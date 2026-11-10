# Tawazun Security Architecture & Threat Model

## 1. Threat Model & Security Invariants

### Invariant 1: Cross-Family Data Isolation
Every query to Firestore must scope documents by `familyId`. The `familyId` is extracted strictly from `request.auth.token.familyId`, verified by Firebase Auth custom claims, and NEVER trusted from client payload bodies.

### Invariant 2: Client Write Restrictions
`devices`, `usageEvents`, `webAttempts`, `mirrorSessions`, and `auditLogs` cannot be written by clients.
- Usage events are posted to the Cloud Function `/ingestUsage` authenticated with an HMAC signature computed with `deviceKey`.
- `homeworkUnlocked` is computed exclusively on the server in Cloud Function `recomputeUnlock`.

### Invariant 3: Device Authentication & Rate Limiting
- Devices do not hold user tokens; they hold a unique 256-bit `deviceKey`.
- Every request includes a timestamp within 5 minutes and an HMAC-SHA256 signature.
- Rate limiting enforces a strict cap of 60 requests/minute per device tracked via Firestore timestamp counters.

---

## 2. Three-Day Retention Proof (Privacy-First)

Tawazun enforces a non-negotiable **3-day retention guarantee** for sensitive children's surveillance data (screen mirror frames, usage events, and web attempts):

1. **Firestore TTL Policy**:
   Every document in `usageEvents`, `webAttempts`, `mirrorSessions`, and `homework` carries an `expireAt` timestamp set to `now + 3 days`. Firestore Native TTL deletes expired documents automatically in the background.

2. **Cloud Storage Lifecycle Rule**:
   All objects stored under `mirror/{familyId}/**` are configured with a lifecycle rule:
   ```json
   {
     "rule": [
       {
         "action": { "type": "Delete" },
         "condition": { "age": 3 }
       }
     ]
   }
   ```

3. **Scheduled Cloud Function (`cleanupExpired`)**:
   Runs daily at 22:00 UTC via Cloud Scheduler. It queries any documents where `expireAt <= now`, compiles an anonymous summary (`frameCount`, `appsUsed`, `blockedCount`) into `mirrorSummaries`, and then purges raw frame objects from Storage.

4. **Parent Emergency "Delete Now" Button**:
   Parents can immediately invoke `purgeThreeDayData(childId)` from the dashboard, wiping all stored mirror sessions, events, and web attempts immediately.

---

## 3. Parental Consent & Child Identity Protection

- Parents must review and sign the electronic consent agreement (`consentVersion: "v1.2-PK"`) before any child profile or device can be paired.
- Consent timestamp `consentAt` is permanently recorded and immutable in security rules.
- Children NEVER get personal email accounts or independent logins; a child is strictly a managed sub-profile.

---

## 4. Google Play Billing Security & Idempotency

- Subscriptions are purchased through the official Google Play Billing Library (PKR 299/mo).
- Webhook notifications from Google Real-time Developer Notifications (RTDN) are handled in `playBillingWebhook`.
- **Idempotency**: Webhook deliveries are deduplicated using `processedBillingEvents/{eventId}` so duplicate network retries result in exactly one update.
- No card numbers, bank accounts, or billing addresses are ever handled or stored by Tawazun.

---

## 5. What is Simulated vs What is Real

> **CRITICAL ARCHITECTURAL BOUNDARY**:
> 
> - **Real Systems in Tawazun**:
>   - Production-grade zero-trust Firestore Security Rules with cross-family access denial.
>   - Cloud Functions v2 verifying HMAC device signatures and computing `homeworkUnlocked`.
>   - Live wildcard domain matcher (`*.youtube.com` matches `m.youtube.com` but not `notyoutube.com`).
>   - 3-day data retention engine with Firestore TTL, Storage lifecycle, and scheduled daily cleanup.
>   - Dual-email account authentication and data isolation.
>   - Reactive StateFlow Jetpack Compose UI architecture.
> 
> - **What is Simulated vs Hardware Native OS Modules**:
>   - True system-wide OS app blocking across third-party APKs requires the Android native **AccessibilityService** and **DevicePolicyManager** (Device Owner / Device Admin).
>   - Live device-wide screen capture outside this application requires **MediaProjection** system permissions.
>   - Device-wide browser URL inspection outside this app requires a local VPN service (`VpnService`) or AccessibilityService event hooking.
>   - Inside this application, the screen-time timer countdowns, app blocking dialogs, Smart Chat read-only enforcement, and domain filter simulations demonstrate the identical logic, API contracts, and user experience that the native background services bind to.
