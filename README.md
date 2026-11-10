# Tawazun (توازن) • Parental Screen-Time Balancing Platform

**Tawazun** is a production-grade parental screen-time balancing and monitoring platform designed specifically for Pakistani families.

In Pakistan, students cannot simply have their mobile devices confiscated: schools and academies distribute daily homework worksheets, assignment notices, and timetable updates via **WhatsApp class groups**. Tawazun balances instead of blocking:
1. **Balance Mode**: Entertainment and games (YouTube, Free Fire, TikTok) remain locked until all of today's homework tasks are completed by the student and verified by the parent.
2. **Smart Chat Timer**: Chat apps like WhatsApp have independent timers. When the time expires, WhatsApp switches to `readOnly: true`, allowing students to access class notes and homework PDFs while preventing distracting social banter.
3. **Safe Web Filter with Wildcards**: Real-time domain verification supporting wildcard patterns (e.g., `*.youtube.com` blocks `m.youtube.com` and `www.youtube.com`, but allows `notyoutube.com`).
4. **Strict 3-Day Privacy Retention**: All screen mirror frames, usage events, and web attempts are permanently purged after 3 days.
5. **Dual Email Authentication & Data Isolation**: Student monitoring data is visible ONLY when the student's account is logged out and the parent's account is logged in via email.

---

## 🇵🇰 Pakistani Context & Subscription
- **Pricing**: PKR 299/month, 7-day free trial, 3-day grace period.
- **Billing**: Digital subscriptions are handled strictly through **Google Play Billing** (15% digital fee). Zero credit card data is ever collected or processed by Tawazun.
- **Support**: `support@tawazun.pk` (No phone or SMS support).

---

## Demo Credentials & Switching

| Role | Email Address | Access & Capabilities |
|---|---|---|
| **Parent** | `parent.ahmed@gmail.com` | Overview, Child management (Zain & Ayla), Homework verification, App policy & group limits, Live screen mirror, Billing, Audit logs, Emergency 3-day purge. |
| **Student** | `student.zain@gmail.com` | Student Home (homework checklist), Monitored Apps (with live timer countdowns & balance locks), Smart WhatsApp chat (read-only state simulation), Safe browser with live wildcard testing, Bedtime soft-lock overlay. |

> **Privacy Isolation Rule**: When Zain is logged into the device, parental monitoring metrics and controls are hidden. To view monitoring insights, Zain logs out, and the parent logs in with their email address.

---

## Architecture & Technology Stack
- **Android Application**: Kotlin, Jetpack Compose, Material Design 3, Coroutines, StateFlow, MVVM architecture.
- **Backend**: Firebase Cloud Functions v2 (Node 20, TypeScript), Firestore Native Mode (`us-central1`), Cloud Storage (`us-central1`), Firebase App Check (Play Integrity & reCAPTCHA Enterprise).
- **Security Rules**: Zero-trust Firestore rules scoping queries strictly by `familyId` derived from the decoded auth token.

---

## Local Setup & Deployment

### 1. Prerequisites
- Android Studio Ladybug / Koala or modern Gradle toolchain
- Node.js 20+ and Firebase CLI (`npm install -g firebase-tools`)

### 2. Environment Variables
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```

### 3. Running Emulators & Seeding
```bash
# Start Firebase emulators (Firestore, Storage, Functions, Auth)
firebase emulators:start

# In a separate terminal, run seed script:
npm run seed
```

### 4. Running Unit Tests
```bash
# Android JVM logic tests (wildcard domain tests, balance mode, 3-day retention):
gradle :app:testDebugUnitTest

# Cloud Functions & Security rules tests:
npm run test:domain
npm run test:rules
```
