import * as admin from "firebase-admin";

if (!admin.apps.length) {
  admin.initializeApp({
    projectId: process.env.FIREBASE_PROJECT_ID || "tawazun-pk"
  });
}

const db = admin.firestore();
const THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000;
const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;

export async function seedDatabase() {
  console.log("🌱 Starting Tawazun production seed script...");

  // 1. Family 1 (Ahmed Family - Karachi, Pakistan)
  const fam1Id = "fam_pk_001";
  await db.doc(`families/${fam1Id}`).set({
    id: fam1Id,
    familyName: "Ahmed Family",
    city: "Karachi, Pakistan",
    primaryParentEmail: "parent.ahmed@gmail.com",
    consentAt: "2026-11-01T10:00:00Z",
    consentVersion: "v1.2-PK",
    createdAt: Date.now() - 10 * 24 * 3600 * 1000
  });

  // Family 1 Parent User
  await db.doc("users/user_parent_ahmed").set({
    uid: "user_parent_ahmed",
    email: "parent.ahmed@gmail.com",
    role: "PARENT",
    familyId: fam1Id,
    emailVerified: true,
    consentAccepted: true,
    consentAt: "2026-11-01T10:00:00Z",
    consentVersion: "v1.2-PK"
  });

  // Child 1: Zain (Grade 9 Matric)
  const child1Id = "child_zain";
  await db.doc(`families/${fam1Id}/children/${child1Id}`).set({
    id: child1Id,
    familyId: fam1Id,
    name: "Zain Ali",
    grade: "Grade 9 (Matric)",
    avatarEmoji: "👦",
    deviceModel: "Samsung Galaxy A15",
    studentEmail: "student.zain@gmail.com",
    homeworkUnlocked: false,
    bedtimeStart: "22:00",
    bedtimeEnd: "06:30",
    bedtimeActiveDays: [1, 2, 3, 4, 5, 6, 7],
    pairingCode: "TWZ-9104",
    isRevoked: false
  });

  // Child 2: Ayla (Grade 7 Pre-O-Levels)
  const child2Id = "child_ayla";
  await db.doc(`families/${fam1Id}/children/${child2Id}`).set({
    id: child2Id,
    familyId: fam1Id,
    name: "Ayla Khan",
    grade: "Grade 7 (Pre-O-Levels)",
    avatarEmoji: "👧",
    deviceModel: "Redmi Note 12",
    studentEmail: "student.ayla@gmail.com",
    homeworkUnlocked: true,
    bedtimeStart: "21:30",
    bedtimeEnd: "07:00",
    bedtimeActiveDays: [1, 2, 3, 4, 5, 6, 7],
    pairingCode: "TWZ-3829",
    isRevoked: false
  });

  // 2. Family 2 (Tariq Family - Lahore, Pakistan)
  const fam2Id = "fam_pk_002";
  await db.doc(`families/${fam2Id}`).set({
    id: fam2Id,
    familyName: "Tariq Family",
    city: "Lahore, Pakistan",
    primaryParentEmail: "parent.tariq@gmail.com",
    consentAt: "2026-11-03T14:30:00Z",
    consentVersion: "v1.2-PK",
    createdAt: Date.now() - 7 * 24 * 3600 * 1000
  });

  await db.doc("users/user_parent_tariq").set({
    uid: "user_parent_tariq",
    email: "parent.tariq@gmail.com",
    role: "PARENT",
    familyId: fam2Id,
    emailVerified: true,
    consentAccepted: true,
    consentAt: "2026-11-03T14:30:00Z",
    consentVersion: "v1.2-PK"
  });

  // Child 3: Hamza (Grade 10 Matric)
  const child3Id = "child_hamza";
  await db.doc(`families/${fam2Id}/children/${child3Id}`).set({
    id: child3Id,
    familyId: fam2Id,
    name: "Hamza Tariq",
    grade: "Grade 10 (Matric Science)",
    avatarEmoji: "🧑‍🎓",
    deviceModel: "Infinix Note 30",
    studentEmail: "student.hamza@gmail.com",
    homeworkUnlocked: false,
    bedtimeStart: "22:30",
    bedtimeEnd: "06:00",
    bedtimeActiveDays: [1, 2, 3, 4, 5, 6, 7],
    pairingCode: "TWZ-5521",
    isRevoked: false
  });

  // 3. 6 Apps Policies configured for Child 1 (Zain)
  const appsConfig = [
    { key: "whatsapp", name: "WhatsApp (School & Family)", cat: "COMMUNICATION", limit: 60, group: "Chat Apps" },
    { key: "khan_academy", name: "Khan Academy Urdu", cat: "EDUCATION", limit: 180, group: null },
    { key: "youtube", name: "YouTube", cat: "ENTERTAINMENT", limit: 45, group: "Social & Gaming" },
    { key: "free_fire", name: "Free Fire MAX", cat: "GAMING", limit: 30, group: "Social & Gaming" },
    { key: "tiktok", name: "TikTok", cat: "ENTERTAINMENT", limit: 0, group: "Social & Gaming" },
    { key: "duolingo", name: "Duolingo", cat: "EDUCATION", limit: 60, group: null }
  ];

  for (const app of appsConfig) {
    await db.doc(`families/${fam1Id}/children/${child1Id}/policies/${app.key}`).set({
      appKey: app.key,
      appName: app.name,
      category: app.cat,
      dailyLimitMin: app.limit,
      groupName: app.group,
      updatedAt: Date.now()
    });
  }

  // 4. Homework tasks for Zain
  await db.doc(`families/${fam1Id}/children/${child1Id}/homework/task_math_01`).set({
    taskId: "task_math_01",
    childId: child1Id,
    title: "Quadratic Equations Exercise 2.3",
    subject: "Mathematics",
    isCompletedByStudent: true,
    completedAt: Date.now() - 7200000,
    isVerifiedByParent: true,
    verifiedAt: Date.now() - 3600000,
    expireAt: admin.firestore.Timestamp.fromMillis(Date.now() + THREE_DAYS_MS),
    notesSummary: "Worksheet sent on Class WhatsApp Group"
  });

  await db.doc(`families/${fam1Id}/children/${child1Id}/homework/task_phys_02`).set({
    taskId: "task_phys_02",
    childId: child1Id,
    title: "Motion Numericals & Speed-Time Graph",
    subject: "Physics",
    isCompletedByStudent: true,
    completedAt: Date.now() - 1800000,
    isVerifiedByParent: false,
    expireAt: admin.firestore.Timestamp.fromMillis(Date.now() + THREE_DAYS_MS),
    notesSummary: "Numericals 3.1 to 3.5 from Matric Physics Textbook"
  });

  // 5. 4 Days of Usage Events & Daily Aggregates
  const now = Date.now();
  for (let dayOffset = 0; dayOffset < 4; dayOffset++) {
    const dayDate = new Date(now - dayOffset * 86400000).toISOString().slice(0, 10);
    await db.doc(`families/${fam1Id}/children/${child1Id}/usageDaily/${dayDate}`).set({
      date: dayDate,
      totalMinutes: 120 - dayOffset * 15,
      expireAt: admin.firestore.Timestamp.fromMillis(now + THIRTY_DAYS_MS)
    });

    // Only events in the last 3 days carry valid TTL; Day 4 is placed for TTL verification
    const eventTime = now - dayOffset * 86400000;
    await db.collection("usageEvents").add({
      childId: child1Id,
      familyId: fam1Id,
      appKey: "whatsapp",
      appName: "WhatsApp (School & Family)",
      durationMin: 30,
      startedAt: eventTime,
      expireAt: admin.firestore.Timestamp.fromMillis(eventTime + THREE_DAYS_MS)
    });
  }

  // 6. 2 Mirror Sessions for Zain
  await db.collection("mirrorSessions").doc("mirror_session_01").set({
    id: "mirror_session_01",
    childId: child1Id,
    familyId: fam1Id,
    startedAt: now - 15 * 60 * 1000,
    frameCount: 78,
    appsUsed: ["WhatsApp (Class Group)", "Chrome (Notes)"],
    blockedCount: 1,
    status: "ACTIVE",
    expireAt: admin.firestore.Timestamp.fromMillis(now + THREE_DAYS_MS)
  });

  await db.collection("mirrorSessions").doc("mirror_session_02").set({
    id: "mirror_session_02",
    childId: child1Id,
    familyId: fam1Id,
    startedAt: now - 36 * 3600 * 1000, // 1.5 days ago
    endedAt: now - 35 * 3600 * 1000,
    frameCount: 240,
    appsUsed: ["Khan Academy Urdu", "YouTube"],
    blockedCount: 0,
    status: "COMPLETED",
    expireAt: admin.firestore.Timestamp.fromMillis(now + THREE_DAYS_MS)
  });

  // 7. 1 Active Google Play Subscription (PKR 299/mo)
  await db.collection("subscriptions").doc("sub_fam_pk_001").set({
    familyId: fam1Id,
    planName: "Tawazun Family Shield",
    price: "PKR 299/month",
    status: "ACTIVE_SUBSCRIPTION",
    trialDaysRemaining: 5,
    purchaseToken: "play_token_enc_91028301928",
    productId: "pk.tawazun.monthly.299",
    verifiedViaWebhook: true,
    createdAt: now - 2 * 86400000,
    nextBillingAt: now + 28 * 86400000
  });

  console.log("✅ Tawazun production seed complete: 2 families, 3 children, 6 apps, 4 days of usage, 2 mirror sessions, 1 active subscription.");
}

if (require.main === module) {
  seedDatabase().then(() => process.exit(0)).catch(err => {
    console.error("Seed error:", err);
    process.exit(1);
  });
}
