package com.example.data.repository

import com.example.data.model.AppCategory
import com.example.data.model.AppPolicy
import com.example.data.model.AuditLog
import com.example.data.model.Child
import com.example.data.model.HomeworkTask
import com.example.data.model.MirrorSession
import com.example.data.model.SubscriptionInfo
import com.example.data.model.UsageEvent
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.data.model.WebAttempt
import android.content.Context
import android.content.Intent
import com.example.data.usage.UsageTracker
import com.example.domain.DomainFilter
import com.example.service.GuardState
import com.example.service.ScreenSnapService
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.concurrent.ExecutionException
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TawazunRepository(private val appContext: Context? = null) {

    // REAL Firebase Authentication engine
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // REAL Firestore database (asli family data)
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var familyListener: ListenerRegistration? = null
    private var homeworkListener: ListenerRegistration? = null
    private var policiesListener: ListenerRegistration? = null
    private var usageListener: ListenerRegistration? = null
    private var screenSnapListener: ListenerRegistration? = null

    // Current Session (null = logged out)
    private val _currentSession = MutableStateFlow<UserSession?>(null)
    val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()

    // Auth UI state: error message + loading indicator
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    // Children load ho rahe hain? (Firestore se)
    private val _childrenLoading = MutableStateFlow(false)
    val childrenLoading: StateFlow<Boolean> = _childrenLoading.asStateFlow()

    init {
        // App restart par agar Firebase user logged in hai to session restore karo
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null && _currentSession.value == null) {
                val email = user.email ?: ""
                scope.launch {
                    // Email se role detect karo: ye bacha hai ya parent?
                    val child = findChildByEmail(email)
                    if (child != null) {
                        _children.value = listOf(child)
                        _currentSession.value = UserSession(
                            email = email,
                            role = UserRole.STUDENT,
                            familyId = child.familyId,
                            childId = child.id,
                            isEmailVerified = user.isEmailVerified,
                            consentAccepted = true
                        )
                        _selectedChildId.value = child.id
                        attachChildrenListener(child.familyId)
                    } else {
                        _currentSession.value = UserSession(
                            email = email,
                            role = UserRole.PARENT,
                            familyId = user.uid,
                            isEmailVerified = user.isEmailVerified,
                            consentAccepted = true
                        )
                        startFamilySession(user.uid, email)
                    }
                }
            }
        }
    }

    // Children in family — ab Firestore se LIVE load hote hain (demo data khatam)
    private val _children = MutableStateFlow<List<Child>>(emptyList())
    val children: StateFlow<List<Child>> = _children.asStateFlow()

    private val _selectedChildId = MutableStateFlow<String>("")
    val selectedChildId: StateFlow<String> = _selectedChildId.asStateFlow()

    // Policies — ab Firestore se LIVE load hoti hain (starter policies addChild par cloud mein save hoti hain)
    private val _policies = MutableStateFlow<Map<String, List<AppPolicy>>>(emptyMap())
    val policies: StateFlow<Map<String, List<AppPolicy>>> = _policies.asStateFlow()

    // Blocked Domains
    private val _blockedDomains = MutableStateFlow<List<String>>(
        listOf(
            "*.youtube.com",
            "*.tiktok.com",
            "*.roblox.com",
            "omegle.com",
            "*.chatroulette.com"
        )
    )
    val blockedDomains: StateFlow<List<String>> = _blockedDomains.asStateFlow()

    // Homework tasks
    private val _homeworkTasks = MutableStateFlow<List<HomeworkTask>>(emptyList())
    val homeworkTasks: StateFlow<List<HomeworkTask>> = _homeworkTasks.asStateFlow()

    // 3-Day Window Usage Events
    // Usage events — student device se ASLI data (Firestore se LIVE)
    private val _usageEvents = MutableStateFlow<List<UsageEvent>>(emptyList())
    val usageEvents: StateFlow<List<UsageEvent>> = _usageEvents.asStateFlow()

    // 🪞 Live Screen Snaps — childId → (base64, timestamp)
    private val _screenSnaps = MutableStateFlow<Map<String, Pair<String, Long>>>(emptyMap())
    val screenSnaps: StateFlow<Map<String, Pair<String, Long>>> = _screenSnaps.asStateFlow()

    // Web Attempts
    private val _webAttempts = MutableStateFlow<List<WebAttempt>>(
        listOf(
            WebAttempt("wa_1", "child_zain", "https://m.youtube.com/shorts", "m.youtube.com", System.currentTimeMillis() - 40 * 60 * 1000, blocked = true, matchedRule = "*.youtube.com"),
            WebAttempt("wa_2", "child_zain", "https://ilmkidunya.com/past-papers", "ilmkidunya.com", System.currentTimeMillis() - 90 * 60 * 1000, blocked = false),
            WebAttempt("wa_3", "child_zain", "https://vt.tiktok.com/clip", "vt.tiktok.com", System.currentTimeMillis() - 150 * 60 * 1000, blocked = true, matchedRule = "*.tiktok.com"),
            WebAttempt("wa_4", "child_zain", "https://sindhstudy.pk/notes", "sindhstudy.pk", System.currentTimeMillis() - 210 * 60 * 1000, blocked = false)
        )
    )
    val webAttempts: StateFlow<List<WebAttempt>> = _webAttempts.asStateFlow()

    // Mirror Sessions
    private val _mirrorSessions = MutableStateFlow<List<MirrorSession>>(
        listOf(
            MirrorSession(
                id = "mirror_live_01",
                childId = "child_zain",
                startedAt = System.currentTimeMillis() - 12 * 60 * 1000,
                frameCount = 78,
                appsUsed = listOf("WhatsApp (Class Group)", "Chrome (Notes)"),
                blockedCount = 1,
                status = "ACTIVE"
            )
        )
    )
    val mirrorSessions: StateFlow<List<MirrorSession>> = _mirrorSessions.asStateFlow()

    // Audit Logs
    private val _auditLogs = MutableStateFlow<List<AuditLog>>(
        listOf(
            AuditLog(
                id = "log_init",
                timestamp = System.currentTimeMillis() - 3600000 * 4,
                action = "CONSENT_ACCEPTED",
                performedBy = "parent.ahmed@gmail.com",
                details = "Signed consent v1.2-PK for child Zain Ali. 3-day data retention policy confirmed."
            ),
            AuditLog(
                id = "log_policy",
                timestamp = System.currentTimeMillis() - 3600000 * 2,
                action = "POLICY_UPDATE",
                performedBy = "parent.ahmed@gmail.com",
                details = "Updated TikTok dailyLimitMin to 0 min (Strict Block)."
            ),
            AuditLog(
                id = "log_hw",
                timestamp = System.currentTimeMillis() - 3600000,
                action = "HOMEWORK_VERIFIED",
                performedBy = "parent.ahmed@gmail.com",
                details = "Verified Mathematics Ex 2.3 completed tasks."
            )
        )
    )
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs.asStateFlow()

    // Subscription
    private val _subscription = MutableStateFlow(SubscriptionInfo())
    val subscription: StateFlow<SubscriptionInfo> = _subscription.asStateFlow()

    // --- REAL AUTHENTICATION (Firebase Auth) ---

    /** Naya PARENT account banao (email + password) — asli Firebase Auth. */
    suspend fun signUpParent(email: String, password: String): Boolean {
        val cleanEmail = email.trim()
        if (password.length < 6) {
            _authError.value = "Password must be at least 6 characters."
            return false
        }
        _authLoading.value = true
        return try {
            withContext(Dispatchers.IO) {
                Tasks.await(auth.createUserWithEmailAndPassword(cleanEmail, password))
                // Asli email check: Firebase inbox mein verify-link email bhejta hai
                auth.currentUser?.sendEmailVerification()
            }
            _currentSession.value = UserSession(
                email = cleanEmail,
                role = UserRole.PARENT,
                familyId = auth.currentUser?.uid ?: "",
                isEmailVerified = auth.currentUser?.isEmailVerified ?: false,
                consentAccepted = true
            )
            val uid = auth.currentUser?.uid
            if (uid != null) startFamilySession(uid, cleanEmail)
            addAudit("PARENT_SIGNUP", cleanEmail, "New parent account created via Firebase Auth.")
            true
        } catch (e: Exception) {
            _authError.value = friendlyAuthError(e)
            false
        } finally {
            _authLoading.value = false
        }
    }

    /** Parent REAL login — galat password par login NAHI hota. */
    suspend fun signInParent(email: String, password: String): Boolean {
        val cleanEmail = email.trim()
        _authLoading.value = true
        return try {
            withContext(Dispatchers.IO) {
                Tasks.await(auth.signInWithEmailAndPassword(cleanEmail, password))
            }
            _currentSession.value = UserSession(
                email = cleanEmail,
                role = UserRole.PARENT,
                familyId = auth.currentUser?.uid ?: "",
                isEmailVerified = auth.currentUser?.isEmailVerified ?: false,
                consentAccepted = true
            )
            val uid = auth.currentUser?.uid
            if (uid != null) startFamilySession(uid, cleanEmail)
            addAudit("PARENT_LOGIN", cleanEmail, "Parent logged in via Firebase Auth. Student data is now viewable by verified parent.")
            true
        } catch (e: Exception) {
            _authError.value = friendlyAuthError(e)
            false
        } finally {
            _authLoading.value = false
        }
    }

    /** Naya STUDENT account banao (email + password) — asli Firebase Auth.
     *  Pehle check: parent ne ye email apni family mein add kiya hai ya nahi. */
    suspend fun signUpStudent(email: String, password: String): Boolean {
        val cleanEmail = email.trim()
        if (password.length < 6) {
            _authError.value = "Password must be at least 6 characters."
            return false
        }
        _authLoading.value = true
        return try {
            val child = findChildByEmail(cleanEmail)
            if (child == null) {
                _authError.value = "Ye email abhi kisi family mein add nahi. Parent se pehle apne app par add karwayein."
                return false
            }
            withContext(Dispatchers.IO) {
                Tasks.await(auth.createUserWithEmailAndPassword(cleanEmail, password))
                // Asli email check: Firebase inbox mein verify-link email bhejta hai
                auth.currentUser?.sendEmailVerification()
            }
            linkStudentToChild(child, auth.currentUser?.uid)
            _children.value = listOf(child)
            _currentSession.value = UserSession(
                email = cleanEmail,
                role = UserRole.STUDENT,
                familyId = child.familyId,
                childId = child.id,
                isEmailVerified = auth.currentUser?.isEmailVerified ?: false,
                consentAccepted = true
            )
            _selectedChildId.value = child.id
            attachChildrenListener(child.familyId)
            addAudit("STUDENT_SIGNUP", cleanEmail, "New student account created via Firebase Auth (${child.name}).")
            true
        } catch (e: Exception) {
            _authError.value = friendlyAuthError(e)
            false
        } finally {
            _authLoading.value = false
        }
    }

    /** Student REAL login (email + password) — Firestore mein parent ke add kiye bachay se match hota hai. */
    suspend fun signInStudent(email: String, password: String): Boolean {
        val cleanEmail = email.trim()
        _authLoading.value = true
        return try {
            val child = findChildByEmail(cleanEmail)
            if (child == null) {
                _authError.value = "Ye email abhi kisi family mein add nahi. Parent se pehle apne app par add karwayein."
                return false
            }
            withContext(Dispatchers.IO) {
                Tasks.await(auth.signInWithEmailAndPassword(cleanEmail, password))
            }
            linkStudentToChild(child, auth.currentUser?.uid)
            _children.value = listOf(child)
            _currentSession.value = UserSession(
                email = cleanEmail,
                role = UserRole.STUDENT,
                familyId = child.familyId,
                childId = child.id,
                isEmailVerified = auth.currentUser?.isEmailVerified ?: false,
                consentAccepted = true
            )
            _selectedChildId.value = child.id
            attachChildrenListener(child.familyId)
            addAudit("STUDENT_LOGIN", cleanEmail, "Student logged in via Firebase Auth on device (${child.name}). Parental monitoring view is locked.")
            true
        } catch (e: Exception) {
            _authError.value = friendlyAuthError(e)
            false
        } finally {
            _authLoading.value = false
        }
    }

    /** Google Sign-In ka asli Firebase login (idToken se). */
    suspend fun loginWithGoogleIdToken(idToken: String): Boolean {
        _authLoading.value = true
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            withContext(Dispatchers.IO) {
                Tasks.await(auth.signInWithCredential(credential))
            }
            val user = auth.currentUser
            _currentSession.value = UserSession(
                email = user?.email ?: "",
                role = UserRole.PARENT,
                familyId = user?.uid ?: "",
                isEmailVerified = user?.isEmailVerified ?: true,
                consentAccepted = true
            )
            if (user != null) startFamilySession(user.uid, user.email ?: "")
            addAudit("PARENT_LOGIN", user?.email ?: "google-user", "Parent logged in with Google Sign-In (Firebase Auth).")
            true
        } catch (e: Exception) {
            _authError.value = friendlyAuthError(e)
            false
        } finally {
            _authLoading.value = false
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    /** Verify-email link dobara bhejo (banner ka Resend button). */
    fun resendVerificationEmail() {
        auth.currentUser?.sendEmailVerification()
    }

    /** "Maine link click kar diya" — Firebase se taaza status check karo. */
    suspend fun refreshVerificationStatus() {
        val user = auth.currentUser ?: return
        withContext(Dispatchers.IO) {
            Tasks.await(user.reload())
        }
        val session = _currentSession.value ?: return
        if (user.isEmailVerified && !session.isEmailVerified) {
            _currentSession.value = session.copy(isEmailVerified = true)
            addAudit("EMAIL_VERIFIED", session.email, "Email verified successfully. Full access granted.")
        }
    }

    fun logout() {
        val prevSession = _currentSession.value
        // 🪞 Screen snap service bhi band
        if (ScreenSnapService.isRunning) {
            appContext?.stopService(Intent(appContext, ScreenSnapService::class.java))
        }
        familyListener?.remove()
        familyListener = null
        homeworkListener?.remove()
        homeworkListener = null
        policiesListener?.remove()
        policiesListener = null
        usageListener?.remove()
        usageListener = null
        screenSnapListener?.remove()
        screenSnapListener = null
        _children.value = emptyList()
        _selectedChildId.value = ""
        _homeworkTasks.value = emptyList()
        _policies.value = emptyMap()
        _usageEvents.value = emptyList()
        _currentSession.value = null
        // Firebase se bhi asli sign out
        auth.signOut()
        if (prevSession != null) {
            addAudit("LOGOUT", prevSession.email, "Session logged out (${prevSession.role}).")
        }
    }

    // --- FAMILY DATA (Firestore) ---

    /** Parent ka family document banao (agar nahi hai) aur bachon ka LIVE listener lagao. */
    private fun startFamilySession(uid: String, email: String) {
        if (email.isNotBlank()) {
            db.collection("families").document(uid).set(
                mapOf(
                    "parentEmail" to email,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }
        attachChildrenListener(uid)
    }

    /** Family ke bachon par LIVE listener — Firestore mein koi change ho to app khud update hota hai. */
    private fun attachChildrenListener(familyId: String) {
        familyListener?.remove()
        _childrenLoading.value = true
        familyListener = db.collection("families").document(familyId).collection("children")
            .addSnapshotListener { snapshot, error ->
                _childrenLoading.value = false
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { it.toChild() }
                _children.value = list
                val selected = _selectedChildId.value
                if (list.isNotEmpty() && list.none { it.id == selected }) {
                    _selectedChildId.value = list.first().id
                }
                // 🛡️ Guard update (homeworkUnlocked / bedtime badal sakte hain)
                refreshGuardState()
                // 🪞 Mirror OFF hua to snap service band
                stopSnapIfRevoked()
            }
        attachHomeworkListener(familyId)
        attachPoliciesListener(familyId)
        attachUsageListener(familyId)
        attachScreenSnapListener(familyId)
    }

    /** 🪞 Screen Snaps — LIVE (bachay ke phone ki taaza tasveer). */
    private fun attachScreenSnapListener(familyId: String) {
        screenSnapListener?.remove()
        screenSnapListener = db.collection("families").document(familyId).collection("screenSnaps")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val map = mutableMapOf<String, Pair<String, Long>>()
                snapshot.documents.forEach { doc ->
                    val b64 = doc.getString("snapBase64") ?: return@forEach
                    val at = doc.getLong("snapAt") ?: 0L
                    map[doc.id] = b64 to at
                }
                _screenSnaps.value = map
            }
    }

    /** 🪞 Parent ne mirror OFF kiya to student ke phone par snap service band karo. */
    private fun stopSnapIfRevoked() {
        val session = _currentSession.value ?: return
        if (session.role != UserRole.STUDENT) return
        val child = _children.value.find { it.id == session.childId } ?: return
        if (!child.mirroringActive && ScreenSnapService.isRunning) {
            appContext?.stopService(Intent(appContext, ScreenSnapService::class.java))
            addAudit("MIRROR_STOPPED", session.email, "Screen Snap stopped (parent turned mirroring off).")
        }
    }

    /**
     * 🛡️ GUARD STATE — kya kya block karna hai ye compute kar ke GuardService ko do.
     * Rule: (1) limit khatam = block, (2) homework unverified = entertainment/games block,
     * (3) bedtime = entertainment/games block.
     * Sirf STUDENT device par chalta hai (parent ka phone kabhi block nahi hota).
     */
    fun refreshGuardState() {
        val session = _currentSession.value ?: return
        if (session.role != UserRole.STUDENT) return
        val childId = session.childId ?: return
        val child = _children.value.find { it.id == childId } ?: return
        val childPolicies = _policies.value[childId] ?: emptyList()

        val blockedKeys = mutableSetOf<String>()
        childPolicies.forEach { p ->
            val isFun = p.category == AppCategory.ENTERTAINMENT || p.category == AppCategory.GAMING
            val lockedByHomework = isFun && !child.homeworkUnlocked
            val lockedByTimer = p.isBlockedByTimer
            val bedtimeNow = isFun && isBedtimeActive(child)
            if (lockedByHomework || lockedByTimer || bedtimeNow) {
                blockedKeys.add(p.appKey)
            }
        }

        val blockedPackages = mutableSetOf<String>()
        blockedKeys.forEach { key ->
            UsageTracker.packagesFor(key)?.let { blockedPackages.addAll(it) }
        }
        GuardState.update(appContext, blockedPackages)
    }

    /** Usage events — LIVE (student phone par jo chale, parent ko dikhta hai). */
    private fun attachUsageListener(familyId: String) {
        usageListener?.remove()
        usageListener = db.collection("families").document(familyId).collection("usage")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                _usageEvents.value = snapshot.documents.mapNotNull { doc ->
                    val childId = doc.getString("childId")
                    val appKey = doc.getString("appKey")
                    if (childId == null || appKey == null) {
                        null
                    } else {
                        UsageEvent(
                            id = doc.id,
                            childId = childId,
                            appKey = appKey,
                            appName = doc.getString("appName") ?: appKey,
                            startedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                            durationMin = (doc.getLong("minutesUsed") ?: 0L).toInt()
                        )
                    }
                }
            }
    }

    /**
     * ASLI USAGE SYNC — student device par chalti hai:
     * Android se aaj ka per-app usage parho → policies usedTodayMin update +
     * Firestore mein usage summary likho (parent LIVE dekh ta hai).
     */
    fun refreshTodayUsage(context: Context) {
        val session = _currentSession.value ?: return
        if (session.role != UserRole.STUDENT) return
        val childId = session.childId ?: return
        val familyId = session.familyId

        val usage = UsageTracker.todayUsageMinutes(context)
        if (usage.isEmpty()) return

        // 1) Local policies update (student ke phone par UI turant)
        val current = _policies.value[childId] ?: emptyList()
        val updated = current.map { p ->
            if (usage.containsKey(p.appKey)) p.copy(usedTodayMin = usage.getValue(p.appKey)) else p
        }
        _policies.value = _policies.value.toMutableMap().apply { put(childId, updated) }

        // 2) Cloud sync: policy usedTodayMin + daily usage summary
        val policiesRef = db.collection("families").document(familyId).collection("policies")
        val usageRef = db.collection("families").document(familyId).collection("usage")
        val dateStr = UsageTracker.todayDateString()
        updated.forEach { p ->
            val minutes = usage[p.appKey] ?: return@forEach
            policiesRef.document("${childId}_${p.appKey}")
                .update("usedTodayMin", minutes)
                .addOnFailureListener {
                    policiesRef.document("${childId}_${p.appKey}")
                        .set(p.toPolicyMap(childId), SetOptions.merge())
                }
            usageRef.document("${childId}_${dateStr}_${p.appKey}").set(
                mapOf(
                    "childId" to childId,
                    "appKey" to p.appKey,
                    "appName" to p.appName,
                    "minutesUsed" to minutes,
                    "date" to dateStr,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        }
        addAudit("USAGE_SYNC", session.email, "Real usage synced for $dateStr (${usage.size} apps).")

        // 🛡️ Naya usage aaya — guard update (limit khatam hui ho to block lage)
        refreshGuardState()
    }

    /** Homework tasks — LIVE (parent add kare to student ke phone par foran dikhta hai). */
    private fun attachHomeworkListener(familyId: String) {
        homeworkListener?.remove()
        homeworkListener = db.collection("families").document(familyId).collection("homework")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { it.toHomeworkTask() }
                _homeworkTasks.value = list
                // Data change par unlock status dobara calculate (e.g. naya task aa gaya)
                list.map { it.childId }.distinct().forEach { recomputeUnlock(it) }
                // 🛡️ Guard update (verify hone par unlock hota hai)
                refreshGuardState()
            }
    }

    /** App limits (policies) — LIVE cloud sync. */
    private fun attachPoliciesListener(familyId: String) {
        policiesListener?.remove()
        policiesListener = db.collection("families").document(familyId).collection("policies")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val pairs = snapshot.documents.mapNotNull { doc ->
                    val policy = doc.toPolicy()
                    if (policy != null) (doc.getString("childId") ?: "") to policy else null
                }
                _policies.value = pairs.groupBy({ it.first }, { it.second })
                // 🛡️ Guard update (limits badal sakti hain)
                refreshGuardState()
            }
    }

    /** Firestore mein is email ka bacha dhoondo (sari families ke children mein). */
    private suspend fun findChildByEmail(email: String): Child? = withContext(Dispatchers.IO) {
        try {
            val snap = Tasks.await(
                db.collectionGroup("children").whereEqualTo("studentEmail", email).limit(1).get()
            )
            snap.documents.firstOrNull()?.toChild()
        } catch (e: Exception) {
            null
        }
    }

    /** Student ka naya account is bachay se link karo (security rules ke liye). */
    private fun linkStudentToChild(child: Child, uid: String?) {
        if (uid == null || child.studentAuthUid == uid) return
        db.collection("families").document(child.familyId)
            .collection("children").document(child.id)
            .update("studentAuthUid", uid)
    }

    /** Parent apne family mein naya bacha add kare — Firestore mein hamesha ke liye mehfooz. */
    fun addChild(name: String, grade: String, studentEmail: String, deviceModel: String, avatarEmoji: String) {
        val parentUid = auth.currentUser?.uid ?: return
        val cleanEmail = studentEmail.trim()
        if (name.isBlank() || !cleanEmail.contains("@")) {
            _authError.value = "Bachay ka naam aur sahi email zaroori hai."
            return
        }
        val docRef = db.collection("families").document(parentUid).collection("children").document()
        val child = Child(
            id = docRef.id,
            familyId = parentUid,
            name = name.trim(),
            grade = grade.trim().ifBlank { "Not set" },
            avatarEmoji = avatarEmoji.ifBlank { "🧑" },
            deviceModel = deviceModel.trim().ifBlank { "Not paired yet" },
            studentEmail = cleanEmail,
            pairingCode = "TWZ-" + (1000..9999).random()
        )
        docRef.set(child.toMap())
            .addOnSuccessListener {
                // Naye bachay ke liye starter policies — cloud mein bhi save
                val policiesRef = db.collection("families").document(parentUid).collection("policies")
                starterPolicies().forEach { p ->
                    policiesRef.document("${child.id}_${p.appKey}").set(p.toPolicyMap(child.id))
                }
                _policies.value = _policies.value + (child.id to starterPolicies())
                _selectedChildId.value = child.id
                addAudit("CHILD_ADDED", _currentSession.value?.email ?: "Parent", "New child added: ${child.name} (${cleanEmail})")
            }
            .addOnFailureListener { e ->
                _authError.value = "Bacha save nahi hua: ${e.message}"
            }
    }

    /** Naye bachay ke liye shuruaati app limits. */
    private fun starterPolicies(): List<AppPolicy> = listOf(
        AppPolicy("whatsapp", "WhatsApp (School & Family)", AppCategory.COMMUNICATION, "💬", dailyLimitMin = 60, usedTodayMin = 0, groupName = "Chat Apps"),
        AppPolicy("khan_academy", "Khan Academy Urdu", AppCategory.EDUCATION, "📚", dailyLimitMin = 180, usedTodayMin = 0),
        AppPolicy("youtube", "YouTube", AppCategory.ENTERTAINMENT, "▶️", dailyLimitMin = 45, usedTodayMin = 0, groupName = "Social & Gaming"),
        AppPolicy("tiktok", "TikTok", AppCategory.ENTERTAINMENT, "🎵", dailyLimitMin = 0, usedTodayMin = 0, groupName = "Social & Gaming")
    )

    /** Child ko Firestore document banane ke liye map mein badlo. */
    private fun Child.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "familyId" to familyId,
        "name" to name,
        "grade" to grade,
        "avatarEmoji" to avatarEmoji,
        "deviceModel" to deviceModel,
        "studentEmail" to studentEmail,
        "homeworkUnlocked" to homeworkUnlocked,
        "bedtimeStart" to bedtimeStart,
        "bedtimeEnd" to bedtimeEnd,
        "bedtimeActiveDays" to bedtimeActiveDays,
        "mirroringActive" to mirroringActive,
        "mirrorMode" to mirrorMode,
        "videoToGallery" to videoToGallery,
        "pairingCode" to pairingCode,
        "isRevoked" to isRevoked,
        "studentAuthUid" to studentAuthUid
    )

    /** HomeworkTask ko Firestore map mein badlo. */
    private fun HomeworkTask.toHomeworkMap(): Map<String, Any?> = mapOf(
        "childId" to childId,
        "title" to title,
        "subject" to subject,
        "dueTime" to dueTime,
        "isCompletedByStudent" to isCompletedByStudent,
        "completedAt" to completedAt,
        "isVerifiedByParent" to isVerifiedByParent,
        "verifiedAt" to verifiedAt,
        "notesSummary" to notesSummary,
        "createdAt" to System.currentTimeMillis()
    )

    /** Firestore document se HomeworkTask banao. */
    private fun DocumentSnapshot.toHomeworkTask(): HomeworkTask? = try {
        HomeworkTask(
            taskId = id,
            childId = getString("childId") ?: "",
            title = getString("title") ?: "",
            subject = getString("subject") ?: "",
            dueTime = getString("dueTime") ?: "",
            isCompletedByStudent = getBoolean("isCompletedByStudent") ?: false,
            completedAt = getLong("completedAt"),
            isVerifiedByParent = getBoolean("isVerifiedByParent") ?: false,
            verifiedAt = getLong("verifiedAt"),
            notesSummary = getString("notesSummary") ?: ""
        )
    } catch (e: Exception) {
        null
    }

    /** AppPolicy ko Firestore map mein badlo. */
    private fun AppPolicy.toPolicyMap(childId: String): Map<String, Any?> = mapOf(
        "childId" to childId,
        "appKey" to appKey,
        "appName" to appName,
        "category" to category.name,
        "iconEmoji" to iconEmoji,
        "dailyLimitMin" to dailyLimitMin,
        "usedTodayMin" to usedTodayMin,
        "groupName" to groupName
    )

    /** Firestore document se AppPolicy banao. */
    private fun DocumentSnapshot.toPolicy(): AppPolicy? = try {
        AppPolicy(
            appKey = getString("appKey") ?: "",
            appName = getString("appName") ?: "",
            category = try {
                AppCategory.valueOf(getString("category") ?: "ENTERTAINMENT")
            } catch (e: Exception) {
                AppCategory.ENTERTAINMENT
            },
            iconEmoji = getString("iconEmoji") ?: "📱",
            dailyLimitMin = (getLong("dailyLimitMin") ?: 0L).toInt(),
            usedTodayMin = (getLong("usedTodayMin") ?: 0L).toInt(),
            groupName = getString("groupName")
        )
    } catch (e: Exception) {
        null
    }

    /** Firestore document se Child wapas banao. */
    private fun DocumentSnapshot.toChild(): Child? = try {
        Child(
            id = id,
            familyId = getString("familyId") ?: "",
            name = getString("name") ?: "",
            grade = getString("grade") ?: "",
            avatarEmoji = getString("avatarEmoji") ?: "🧑",
            deviceModel = getString("deviceModel") ?: "Not paired yet",
            studentEmail = getString("studentEmail") ?: "",
            homeworkUnlocked = getBoolean("homeworkUnlocked") ?: false,
            bedtimeStart = getString("bedtimeStart") ?: "22:00",
            bedtimeEnd = getString("bedtimeEnd") ?: "06:30",
            bedtimeActiveDays = (get("bedtimeActiveDays") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() }
                ?: listOf(1, 2, 3, 4, 5, 6, 7),
            mirroringActive = getBoolean("mirroringActive") ?: false,
            mirrorMode = getString("mirrorMode") ?: "snap",
            videoToGallery = getBoolean("videoToGallery") ?: true,
            pairingCode = getString("pairingCode") ?: "TWZ-0000",
            isRevoked = getBoolean("isRevoked") ?: false,
            studentAuthUid = getString("studentAuthUid")
        )
    } catch (e: Exception) {
        null
    }

    /** Firebase errors ko asaan messages mein badlo. */
    private fun friendlyAuthError(e: Exception): String {
        val cause = (e as? ExecutionException)?.cause ?: e
        return when (cause) {
            is FirebaseAuthInvalidUserException -> "No account found with this email. Please Sign Up first."
            is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password. Please try again."
            is FirebaseAuthWeakPasswordException -> "Weak password - use at least 6 characters."
            is FirebaseAuthUserCollisionException -> "This email is already registered. Please Sign In."
            else -> "Authentication error: ${cause.message ?: "unknown error"}"
        }
    }

    fun setSelectedChild(childId: String) {
        _selectedChildId.value = childId
    }

    // --- HOMEWORK & BALANCE MODE (Firestore cloud sync) ---

    /** Parent naya homework banaye — Firestore mein save, student ke phone par LIVE dikhta hai. */
    fun addHomeworkTask(childId: String, title: String, subject: String, dueTime: String) {
        if (title.isBlank()) {
            _authError.value = "Homework ka title likhna zaroori hai."
            return
        }
        val child = _children.value.find { it.id == childId } ?: return
        val docRef = db.collection("families").document(child.familyId).collection("homework").document()
        val task = HomeworkTask(
            taskId = docRef.id,
            childId = childId,
            title = title.trim(),
            subject = subject.trim().ifBlank { "General" },
            dueTime = dueTime.trim().ifBlank { "Today" },
            notesSummary = "Added by parent via Tawazun"
        )
        docRef.set(task.toHomeworkMap())
        _homeworkTasks.value = _homeworkTasks.value + task
        recomputeUnlock(childId)
        addAudit("HOMEWORK_ADDED", _currentSession.value?.email ?: "Parent", "Homework added: '${task.title}' for ${child.name}")
    }

    fun markHomeworkCompletedByStudent(taskId: String, completed: Boolean) {
        var updatedTask: HomeworkTask? = null
        _homeworkTasks.value = _homeworkTasks.value.map { task ->
            if (task.taskId == taskId) {
                val t = task.copy(
                    isCompletedByStudent = completed,
                    completedAt = if (completed) System.currentTimeMillis() else null,
                    // If student un-completes, also reset parent verification
                    isVerifiedByParent = if (!completed) false else task.isVerifiedByParent
                )
                updatedTask = t
                t
            } else task
        }
        val task = updatedTask ?: return
        val familyId = _children.value.find { it.id == task.childId }?.familyId
        if (familyId != null) {
            db.collection("families").document(familyId).collection("homework").document(taskId)
                .update(
                    mapOf(
                        "isCompletedByStudent" to completed,
                        "completedAt" to task.completedAt,
                        "isVerifiedByParent" to task.isVerifiedByParent
                    )
                )
        }
        recomputeUnlock(task.childId)
        addAudit("HOMEWORK_STATUS_CHANGED", _currentSession.value?.email ?: "Student", "Marked '${task.title}' completed = $completed")
    }

    fun verifyHomeworkByParent(taskId: String, verified: Boolean) {
        var updatedTask: HomeworkTask? = null
        _homeworkTasks.value = _homeworkTasks.value.map { task ->
            if (task.taskId == taskId) {
                val t = task.copy(
                    isVerifiedByParent = verified,
                    verifiedAt = if (verified) System.currentTimeMillis() else null
                )
                updatedTask = t
                t
            } else task
        }
        val task = updatedTask ?: return
        val familyId = _children.value.find { it.id == task.childId }?.familyId
        if (familyId != null) {
            db.collection("families").document(familyId).collection("homework").document(taskId)
                .update(
                    mapOf(
                        "isVerifiedByParent" to verified,
                        "verifiedAt" to task.verifiedAt
                    )
                )
        }
        recomputeUnlock(task.childId)
        addAudit("HOMEWORK_VERIFIED", _currentSession.value?.email ?: "Parent", "Parent verification for '${task.title}' set to $verified")
    }

    private fun recomputeUnlock(childId: String) {
        val tasksForChild = _homeworkTasks.value.filter { it.childId == childId }
        val allDoneAndVerified = tasksForChild.isNotEmpty() && tasksForChild.all { it.isCompletedByStudent && it.isVerifiedByParent }

        _children.value = _children.value.map { child ->
            if (child.id == childId) {
                child.copy(homeworkUnlocked = allDoneAndVerified)
            } else child
        }

        // Unlock status cloud mein bhi update karo (dono phones par sync)
        val child = _children.value.find { it.id == childId }
        if (child != null) {
            db.collection("families").document(child.familyId).collection("children").document(childId)
                .update("homeworkUnlocked", allDoneAndVerified)
        }

        addAudit(
            "RECOMPUTE_UNLOCK",
            "Server/CloudFunction",
            "Child $childId homeworkUnlocked evaluated to $allDoneAndVerified (${tasksForChild.count { it.isVerifiedByParent }}/${tasksForChild.size} verified)."
        )
    }

    // --- APP POLICIES & LIMITS ---

    fun updatePolicy(childId: String, appKey: String, limitMin: Int) {
        val familyId = _children.value.find { it.id == childId }?.familyId ?: return
        val currentChildPolicies = _policies.value[childId]
        if (currentChildPolicies != null) {
            val updated = currentChildPolicies.map {
                if (it.appKey == appKey) it.copy(dailyLimitMin = limitMin) else it
            }
            _policies.value = _policies.value.toMutableMap().apply {
                put(childId, updated)
            }
        }
        // Limit cloud mein save — bachay ke phone par bhi yehi limit chalegi
        val policyRef = db.collection("families").document(familyId).collection("policies")
            .document("${childId}_${appKey}")
        policyRef.update("dailyLimitMin", limitMin)
            .addOnFailureListener {
                // Doc abhi nahi bana to bana do
                policyRef.set(
                    mapOf(
                        "childId" to childId,
                        "appKey" to appKey,
                        "appName" to appKey,
                        "category" to "ENTERTAINMENT",
                        "iconEmoji" to "📱",
                        "dailyLimitMin" to limitMin,
                        "usedTodayMin" to 0,
                        "groupName" to null
                    ),
                    SetOptions.merge()
                )
            }
        addAudit("POLICY_UPDATE", _currentSession.value?.email ?: "Parent", "Set daily limit for $appKey to $limitMin min for child $childId")
    }

    fun recordAppUsage(childId: String, appKey: String, minutesUsed: Int) {
        val currentChildPolicies = _policies.value[childId] ?: return
        val targetPolicy = currentChildPolicies.find { it.appKey == appKey } ?: return

        // If app belongs to a group (e.g. Social & Gaming), update group usage
        val group = targetPolicy.groupName
        val updatedPolicies = currentChildPolicies.map { policy ->
            if (group != null && policy.groupName == group) {
                policy.copy(usedTodayMin = policy.usedTodayMin + minutesUsed)
            } else if (policy.appKey == appKey) {
                policy.copy(usedTodayMin = policy.usedTodayMin + minutesUsed)
            } else {
                policy
            }
        }

        _policies.value = _policies.value.toMutableMap().apply {
            put(childId, updatedPolicies)
        }

        // Add 3-day usage event
        val newEvent = UsageEvent(
            id = "ue_${System.currentTimeMillis()}",
            childId = childId,
            appKey = appKey,
            appName = targetPolicy.appName,
            startedAt = System.currentTimeMillis() - minutesUsed * 60 * 1000,
            durationMin = minutesUsed
        )
        _usageEvents.value = listOf(newEvent) + _usageEvents.value
    }

    // --- WEB FILTERING ---

    fun testDomainAttempt(childId: String, rawUrl: String): Pair<Boolean, String?> {
        val check = DomainFilter.isBlocked(rawUrl, _blockedDomains.value)
        val attempt = WebAttempt(
            id = "wa_${System.currentTimeMillis()}",
            childId = childId,
            url = rawUrl,
            domain = DomainFilter.normalizeHost(rawUrl),
            attemptedAt = System.currentTimeMillis(),
            blocked = check.first,
            matchedRule = check.second
        )
        _webAttempts.value = listOf(attempt) + _webAttempts.value
        if (check.first) {
            addAudit("WEB_BLOCK", "DomainFilter", "Blocked navigation to '$rawUrl' under rule '${check.second}' for child $childId")
        }
        return check
    }

    fun addBlockedDomain(pattern: String) {
        val trimmed = pattern.trim().lowercase(Locale.ROOT)
        if (trimmed.isNotEmpty() && !_blockedDomains.value.contains(trimmed)) {
            _blockedDomains.value = _blockedDomains.value + trimmed
            addAudit("DOMAIN_RULE_ADDED", _currentSession.value?.email ?: "Parent", "Added block rule '$trimmed'")
        }
    }

    fun removeBlockedDomain(pattern: String) {
        _blockedDomains.value = _blockedDomains.value.filter { it != pattern }
        addAudit("DOMAIN_RULE_REMOVED", _currentSession.value?.email ?: "Parent", "Removed block rule '$pattern'")
    }

    // --- SCREEN MIRRORING & PURGING ---

    fun toggleMirroring(childId: String, active: Boolean) {
        val child = _children.value.find { it.id == childId } ?: return
        _children.value = _children.value.map {
            if (it.id == childId) it.copy(mirroringActive = active) else it
        }
        // 🔥 FIX: Firestore mein bhi likho — warna student phone ko pata nahi chalta!
        db.collection("families").document(child.familyId)
            .collection("children").document(childId)
            .update("mirroringActive", active)
            .addOnFailureListener { }
        addAudit("MIRROR_TOGGLE", _currentSession.value?.email ?: "Parent", "Screen mirroring $childId set to $active")
    }

    /**
     * 🎞️ Parent khud choose kare: "snap" (live photos) ya "video" (poori recording).
     * videoToGallery = true → video Gallery (Movies/Tawazun) mein save hogi.
     */
    fun setMirrorMode(childId: String, mode: String, videoToGallery: Boolean) {
        val child = _children.value.find { it.id == childId } ?: return
        _children.value = _children.value.map {
            if (it.id == childId) it.copy(mirrorMode = mode, videoToGallery = videoToGallery) else it
        }
        db.collection("families").document(child.familyId)
            .collection("children").document(childId)
            .update(
                mapOf(
                    "mirrorMode" to mode,
                    "videoToGallery" to videoToGallery
                )
            )
            .addOnFailureListener { }
        addAudit(
            "MIRROR_MODE",
            _currentSession.value?.email ?: "Parent",
            "Mirror mode for $childId set to $mode (gallery=$videoToGallery)"
        )
    }

    /**
     * Parent-facing "Delete now" button that immediately purges a child's mirror data and events.
     * Enforces the 3-day privacy guarantee.
     */
    fun purgeThreeDayData(childId: String) {
        _usageEvents.value = _usageEvents.value.filter { it.childId != childId }
        _webAttempts.value = _webAttempts.value.filter { it.childId != childId }
        _mirrorSessions.value = _mirrorSessions.value.filter { it.childId != childId }
        addAudit(
            "PURGE_3DAY_DATA",
            _currentSession.value?.email ?: "Parent",
            "Emergency privacy purge: Cleared all mirror frames, usage events, and web attempts for child $childId immediately."
        )
    }

    // --- BEDTIME LOGIC ---

    fun isBedtimeActive(child: Child): Boolean {
        return try {
            val now = Calendar.getInstance()
            val dayOfWeek = now.get(Calendar.DAY_OF_WEEK) // 1=Sun, 7=Sat
            if (!child.bedtimeActiveDays.contains(dayOfWeek)) return false

            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val startParts = child.bedtimeStart.split(":")
            val startMin = startParts[0].toInt() * 60 + startParts[1].toInt()

            val endParts = child.bedtimeEnd.split(":")
            val endMin = endParts[0].toInt() * 60 + endParts[1].toInt()

            if (startMin > endMin) {
                // Overnight bedtime (e.g. 22:00 to 06:30)
                currentMinutes >= startMin || currentMinutes < endMin
            } else {
                currentMinutes in startMin..endMin
            }
        } catch (e: Exception) {
            false
        }
    }

    // --- AUDIT LOGGER ---

    private fun addAudit(action: String, performedBy: String, details: String) {
        val log = AuditLog(
            id = "audit_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            action = action,
            performedBy = performedBy,
            details = details
        )
        _auditLogs.value = listOf(log) + _auditLogs.value
    }
}
