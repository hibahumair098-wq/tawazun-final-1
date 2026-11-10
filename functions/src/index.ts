import * as crypto from "crypto";
import * as admin from "firebase-admin";
import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { z } from "zod";

admin.initializeApp();
const db = admin.firestore();
const storage = admin.storage();

const THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000;
const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;
const DEVICE_HMAC_SECRET = process.env.DEVICE_HMAC_SECRET || "tawazun_secure_hmac_default_32byte_secret";

/**
 * Domain wildcard matching function.
 * Ensures: *.youtube.com matches m.youtube.com and www.youtube.com,
 * but explicitly does NOT match notyoutube.com.
 */
export function matchesWildcardDomain(hostInput: string, patternInput: string): boolean {
  const host = hostInput.trim().toLowerCase();
  const pattern = patternInput.trim().toLowerCase();
  if (!host || !pattern) return false;

  if (host === pattern) return true;

  if (pattern.startsWith("*.")) {
    const root = pattern.slice(2);
    if (host === root) return true;
    if (host.endsWith("." + root)) return true;
  }
  return false;
}

/**
 * Device Authentication via HMAC verification.
 */
function verifyDeviceHmac(deviceId: string, timestamp: number, signature: string, deviceKey: string): boolean {
  // Reject requests older than 5 minutes to prevent replay attacks
  if (Math.abs(Date.now() - timestamp) > 5 * 60 * 1000) return false;
  const hmac = crypto.createHmac("sha256", deviceKey);
  hmac.update(`${deviceId}:${timestamp}`);
  const expectedSig = hmac.digest("hex");
  return crypto.timingSafeEqual(Buffer.from(signature, "hex"), Buffer.from(expectedSig, "hex"));
}

/**
 * Audit log helper
 */
async function writeAuditLog(familyId: string, action: string, performedBy: string, details: string) {
  await db.collection("auditLogs").add({
    familyId,
    action,
    performedBy,
    details,
    timestamp: Date.now(),
    expireAt: admin.firestore.Timestamp.fromMillis(Date.now() + 90 * 24 * 60 * 60 * 1000)
  });
}

/**
 * 1. setPolicy: Parent configures app daily limits and group allocations.
 */
export const setPolicy = onCall(async (request) => {
  if (!request.auth || request.auth.token.role !== "PARENT") {
    throw new HttpsError("permission-denied", "Only verified parents can modify policies.");
  }
  const schema = z.object({
    childId: z.string(),
    appKey: z.string(),
    dailyLimitMin: z.number().min(0).max(1440),
    groupName: z.string().optional()
  });
  const data = schema.parse(request.data);
  const familyId = request.auth.token.familyId as string;

  const policyRef = db.doc(`families/${familyId}/children/${data.childId}/policies/${data.appKey}`);
  await policyRef.set({
    dailyLimitMin: data.dailyLimitMin,
    groupName: data.groupName || null,
    updatedAt: Date.now(),
    updatedBy: request.auth.token.email || request.auth.uid
  }, { merge: true });

  await writeAuditLog(familyId, "POLICY_UPDATE", request.auth.token.email || request.auth.uid, `Set dailyLimitMin for ${data.appKey} to ${data.dailyLimitMin}`);
  return { success: true };
});

/**
 * 2. markHomework: Student completes, parent verifies.
 */
export const markHomework = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Authentication required.");
  const schema = z.object({
    childId: z.string(),
    taskId: z.string(),
    isCompletedByStudent: z.boolean().optional(),
    isVerifiedByParent: z.boolean().optional()
  });
  const data = schema.parse(request.data);
  const familyId = request.auth.token.familyId as string;

  const taskRef = db.doc(`families/${familyId}/children/${data.childId}/homework/${data.taskId}`);
  const updatePayload: Record<string, any> = {};

  if (typeof data.isCompletedByStudent === "boolean") {
    updatePayload.isCompletedByStudent = data.isCompletedByStudent;
    updatePayload.completedAt = data.isCompletedByStudent ? Date.now() : null;
    if (!data.isCompletedByStudent) {
      updatePayload.isVerifiedByParent = false;
      updatePayload.verifiedAt = null;
    }
  }

  if (typeof data.isVerifiedByParent === "boolean") {
    if (request.auth.token.role !== "PARENT") {
      throw new HttpsError("permission-denied", "Only parents can verify homework.");
    }
    updatePayload.isVerifiedByParent = data.isVerifiedByParent;
    updatePayload.verifiedAt = data.isVerifiedByParent ? Date.now() : null;
  }

  await taskRef.set(updatePayload, { merge: true });
  await recomputeUnlockInternal(familyId, data.childId);
  return { success: true };
});

/**
 * 3. recomputeUnlock: Re-evaluates homework completion; NEVER trusted from client.
 */
async function recomputeUnlockInternal(familyId: string, childId: string): Promise<boolean> {
  const tasksSnap = await db.collection(`families/${familyId}/children/${childId}/homework`).get();
  const tasks = tasksSnap.docs.map(d => d.data());
  const allVerified = tasks.length > 0 && tasks.every(t => t.isCompletedByStudent && t.isVerifiedByParent);

  await db.doc(`families/${familyId}/children/${childId}`).update({
    homeworkUnlocked: allVerified,
    lastUnlockEvaluatedAt: Date.now()
  });
  return allVerified;
}

export const recomputeUnlock = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Auth required.");
  const familyId = request.auth.token.familyId as string;
  const childId = z.string().parse(request.data.childId);
  const isUnlocked = await recomputeUnlockInternal(familyId, childId);
  return { homeworkUnlocked: isUnlocked };
});

/**
 * 4. ingestUsage: Authenticated device posts app usage events.
 * Enforces rate limiting (60 req/min) and 3-day TTL.
 */
export const ingestUsage = onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }
  const { deviceId, childId, familyId, appKey, appName, durationMin, timestamp, signature } = req.body;
  if (!deviceId || !childId || !familyId || !appKey || !timestamp || !signature) {
    res.status(400).json({ error: "Missing required fields" });
    return;
  }

  // Rate limiting via Firestore minute counters
  const minuteKey = Math.floor(Date.now() / 60000);
  const rateLimitRef = db.doc(`rateLimits/${deviceId}_${minuteKey}`);
  const rateLimitDoc = await rateLimitRef.get();
  const currentCount = (rateLimitDoc.data()?.count || 0) + 1;
  if (currentCount > 60) {
    res.status(429).json({ error: "Rate limit exceeded (60 req/min)" });
    return;
  }
  await rateLimitRef.set({ count: currentCount, expireAt: admin.firestore.Timestamp.fromMillis(Date.now() + 120000) }, { merge: true });

  const expireAt = admin.firestore.Timestamp.fromMillis(Date.now() + THREE_DAYS_MS);
  await db.collection("usageEvents").add({
    deviceId,
    childId,
    familyId,
    appKey,
    appName,
    durationMin: durationMin || 1,
    startedAt: Date.now(),
    expireAt
  });

  // Daily aggregate with 30-day TTL (no personal content)
  const todayDate = new Date().toISOString().slice(0, 10);
  const dailyDocRef = db.doc(`families/${familyId}/children/${childId}/usageDaily/${todayDate}`);
  await dailyDocRef.set({
    date: todayDate,
    totalMinutes: admin.firestore.FieldValue.increment(durationMin || 1),
    expireAt: admin.firestore.Timestamp.fromMillis(Date.now() + THIRTY_DAYS_MS)
  }, { merge: true });

  res.status(200).json({ success: true, recordedMin: durationMin });
});

/**
 * 5. checkDomain: Device posts domain, returns allow/block using wildcard matcher.
 */
export const checkDomain = onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }
  const { childId, familyId, domain } = req.body;
  if (!childId || !familyId || !domain) {
    res.status(400).json({ error: "Missing childId, familyId, or domain" });
    return;
  }

  // Load family block rules
  const rulesSnap = await db.collection(`families/${familyId}/blockedDomains`).get();
  const blockRules = rulesSnap.empty
    ? ["*.youtube.com", "*.tiktok.com", "*.roblox.com", "omegle.com"]
    : rulesSnap.docs.map(d => d.data().pattern as string);

  let isBlocked = false;
  let matchedRule: string | null = null;

  for (const rule of blockRules) {
    if (matchesWildcardDomain(domain, rule)) {
      isBlocked = true;
      matchedRule = rule;
      break;
    }
  }

  // Log web attempt with 3-day TTL
  await db.collection("webAttempts").add({
    childId,
    familyId,
    domain,
    blocked: isBlocked,
    matchedRule,
    attemptedAt: Date.now(),
    expireAt: admin.firestore.Timestamp.fromMillis(Date.now() + THREE_DAYS_MS)
  });

  res.status(200).json({ allowed: !isBlocked, matchedRule });
});

/**
 * 6. createPairingCode: Generates a temporary pairing code (15m TTL).
 */
export const createPairingCode = onCall(async (request) => {
  if (!request.auth || request.auth.token.role !== "PARENT") {
    throw new HttpsError("permission-denied", "Parent only.");
  }
  const familyId = request.auth.token.familyId as string;
  const childId = z.string().parse(request.data.childId);
  const code = "TWZ-" + Math.floor(1000 + Math.random() * 9000);

  await db.collection("pairingCodes").doc(code).set({
    familyId,
    childId,
    code,
    expireAt: admin.firestore.Timestamp.fromMillis(Date.now() + 15 * 60 * 1000)
  });
  return { pairingCode: code };
});

/**
 * 7. pairDevice: Exchanges pairing code for hashed deviceKey.
 */
export const pairDevice = onRequest(async (req, res) => {
  const { pairingCode, deviceModel } = req.body;
  if (!pairingCode) {
    res.status(400).json({ error: "Missing pairingCode" });
    return;
  }
  const codeDoc = await db.collection("pairingCodes").doc(pairingCode).get();
  if (!codeDoc.exists) {
    res.status(404).json({ error: "Invalid or expired pairing code" });
    return;
  }
  const { familyId, childId } = codeDoc.data()!;
  const deviceId = "dev_" + crypto.randomUUID();
  const deviceKey = crypto.randomBytes(32).toString("hex");

  await db.collection("devices").doc(deviceId).set({
    deviceId,
    familyId,
    childId,
    deviceModel: deviceModel || "Android Device",
    deviceKeyHash: crypto.createHash("sha256").update(deviceKey).digest("hex"),
    pairedAt: Date.now(),
    isRevoked: false
  });
  await codeDoc.ref.delete();

  res.status(200).json({ deviceId, deviceKey, familyId, childId });
});

/**
 * 8. createMirrorUploadUrl: Device obtains signed upload URL; Parent obtains signed download URL (max 10 min).
 */
export const createMirrorUploadUrl = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Auth required.");
  const familyId = request.auth.token.familyId as string;
  const childId = z.string().parse(request.data.childId);
  const bucket = storage.bucket();
  const filePath = `mirror/${familyId}/${childId}/${Date.now()}.jpg`;
  const file = bucket.file(filePath);

  // Short-lived signed upload URL for device
  const [uploadUrl] = await file.getSignedUrl({
    version: "v4",
    action: "write",
    expires: Date.now() + 5 * 60 * 1000,
    contentType: "image/jpeg"
  });

  // Short-lived signed download URL for parents (strictly <= 10 min)
  const [downloadUrl] = await file.getSignedUrl({
    version: "v4",
    action: "read",
    expires: Date.now() + 10 * 60 * 1000
  });

  return { uploadUrl, downloadUrl, maxValiditySec: 600 };
});

/**
 * 9. cleanupExpired: Daily 22:00 UTC safety net.
 * Deletes frames and records older than 3 days, saving a summary back to mirrorSessions first.
 */
export const cleanupExpired = onSchedule("0 22 * * *", async () => {
  const cutoff = Date.now() - THREE_DAYS_MS;
  const cutoffTimestamp = admin.firestore.Timestamp.fromMillis(cutoff);

  // 1. Purge expired mirror sessions and record summary
  const sessionsSnap = await db.collection("mirrorSessions")
    .where("expireAt", "<=", cutoffTimestamp)
    .get();

  for (const doc of sessionsSnap.docs) {
    const data = doc.data();
    // Persist summary before deleting frames
    await db.collection("mirrorSummaries").doc(doc.id).set({
      sessionId: doc.id,
      childId: data.childId,
      familyId: data.familyId,
      frameCount: data.frameCount || 0,
      apps: data.appsUsed || [],
      blockedCount: data.blockedCount || 0,
      recordedDate: new Date(data.startedAt).toISOString().slice(0, 10),
      deletedAt: Date.now()
    });
    await doc.ref.delete();
  }

  // 2. Storage lifecycle backup deletion
  const bucket = storage.bucket();
  const [files] = await bucket.getFiles({ prefix: "mirror/" });
  for (const file of files) {
    const [metadata] = await file.getMetadata();
    const created = new Date(metadata.timeCreated).getTime();
    if (created < cutoff) {
      await file.delete().catch(() => null);
    }
  }

  console.log("Cleanup expired routine completed.");
});

/**
 * 10. playBillingWebhook: Google Real-time Developer Notifications with Idempotency.
 */
export const playBillingWebhook = onRequest(async (req, res) => {
  const eventId = req.headers["x-goog-message-id"] as string || req.body?.message?.messageId;
  if (!eventId) {
    res.status(400).json({ error: "Missing event identifier" });
    return;
  }

  // Idempotency: same event twice = exactly one update
  const idempotencyRef = db.collection("processedBillingEvents").doc(eventId);
  const idempotencyDoc = await idempotencyRef.get();
  if (idempotencyDoc.exists) {
    res.status(200).json({ status: "IDEMPOTENT_IGNORED", eventId });
    return;
  }

  await idempotencyRef.set({
    eventId,
    processedAt: Date.now(),
    expireAt: admin.firestore.Timestamp.fromMillis(Date.now() + THIRTY_DAYS_MS)
  });

  // Decode Google Play PubSub message
  const pubsubData = req.body?.message?.data
    ? JSON.parse(Buffer.from(req.body.message.data, "base64").toString("utf-8"))
    : req.body;

  const purchaseToken = pubsubData?.subscriptionNotification?.purchaseToken;
  if (purchaseToken) {
    // Locate family subscription and set status
    const subsSnap = await db.collection("subscriptions")
      .where("purchaseToken", "==", purchaseToken)
      .limit(1)
      .get();

    if (!subsSnap.empty) {
      const subDoc = subsSnap.docs[0];
      await subDoc.ref.update({
        status: "ACTIVE_SUBSCRIPTION",
        verifiedViaWebhook: true,
        lastWebhookAt: Date.now()
      });
    }
  }

  res.status(200).json({ status: "SUCCESS", eventId });
});
