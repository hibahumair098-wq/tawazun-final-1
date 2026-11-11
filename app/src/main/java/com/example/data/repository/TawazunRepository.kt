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
import com.example.domain.DomainFilter
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

class TawazunRepository {

    // REAL Firebase Authentication engine
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // REAL Firestore database (asli family data)
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var familyListener: ListenerRegistration? = null

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

    // Policies
    private val _policies = MutableStateFlow<Map<String, List<AppPolicy>>>(
        mapOf(
            "child_zain" to listOf(
                AppPolicy("whatsapp", "WhatsApp (School & Family)", AppCategory.COMMUNICATION, "💬", dailyLimitMin = 60, usedTodayMin = 40, groupName = "Chat Apps"),
                AppPolicy("khan_academy", "Khan Academy Urdu", AppCategory.EDUCATION, "📚", dailyLimitMin = 180, usedTodayMin = 45),
                AppPolicy("youtube", "YouTube", AppCategory.ENTERTAINMENT, "▶️", dailyLimitMin = 45, usedTodayMin = 25, groupName = "Social & Gaming"),
                AppPolicy("free_fire", "Free Fire MAX", AppCategory.GAMING, "🎮", dailyLimitMin = 30, usedTodayMin = 10, groupName = "Social & Gaming"),
                AppPolicy("tiktok", "TikTok", AppCategory.ENTERTAINMENT, "🎵", dailyLimitMin = 0, usedTodayMin = 0, groupName = "Social & Gaming"),
                AppPolicy("duolingo", "Duolingo", AppCategory.EDUCATION, "🦉", dailyLimitMin = 60, usedTodayMin = 20)
            ),
            "child_ayla" to listOf(
                AppPolicy("whatsapp", "WhatsApp (School)", AppCategory.COMMUNICATION, "💬", dailyLimitMin = 60, usedTodayMin = 15, groupName = "Chat Apps"),
                AppPolicy("khan_academy", "Khan Academy", AppCategory.EDUCATION, "📚", dailyLimitMin = 120, usedTodayMin = 30),
                AppPolicy("youtube", "YouTube Kids", AppCategory.ENTERTAINMENT, "▶️", dailyLimitMin = 60, usedTodayMin = 20),
                AppPolicy("roblox", "Roblox", AppCategory.GAMING, "🕹️", dailyLimitMin = 30, usedTodayMin = 0)
            )
        )
    )
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
    private val _homeworkTasks = MutableStateFlow<List<HomeworkTask>>(
        listOf(
            HomeworkTask(
                taskId = "task_math_01",
                childId = "child_zain",
                title = "Quadratic Equations Exercise 2.3",
                subject = "Mathematics",
                dueTime = "Today, 6:00 PM",
                isCompletedByStudent = true,
                completedAt = System.currentTimeMillis() - 7200000,
                isVerifiedByParent = true,
                verifiedAt = System.currentTimeMillis() - 3600000,
                notesSummary = "Worksheet sent on Class WhatsApp Group (Ex 2.3 Q1-Q10)"
            ),
            HomeworkTask(
                taskId = "task_phys_02",
                childId = "child_zain",
                title = "Motion Numericals & Speed-Time Graph",
                subject = "Physics",
                dueTime = "Today, 7:30 PM",
                isCompletedByStudent = true,
                completedAt = System.currentTimeMillis() - 1800000,
                isVerifiedByParent = false,
                notesSummary = "Numericals 3.1 to 3.5 from Matric Physics Textbook"
            ),
            HomeworkTask(
                taskId = "task_urdu_03",
                childId = "child_zain",
                title = "Allama Iqbal Poetry Memorization (Bang-e-Dra)",
                subject = "Urdu Literature",
                dueTime = "Today, 8:30 PM",
                isCompletedByStudent = false,
                isVerifiedByParent = false,
                notesSummary = "Prepare 4 stanzas for morning assembly recitation"
            )
        )
    )
    val homeworkTasks: StateFlow<List<HomeworkTask>> = _homeworkTasks.asStateFlow()

    // 3-Day Window Usage Events
    private val _usageEvents = MutableStateFlow<List<UsageEvent>>(
        listOf(
            UsageEvent("ue_1", "child_zain", "whatsapp", "WhatsApp (School & Family)", System.currentTimeMillis() - 25 * 60 * 1000, 25),
            UsageEvent("ue_2", "child_zain", "khan_academy", "Khan Academy Urdu", System.currentTimeMillis() - 70 * 60 * 1000, 45),
            UsageEvent("ue_3", "child_zain", "free_fire", "Free Fire MAX", System.currentTimeMillis() - 130 * 60 * 1000, 10),
            UsageEvent("ue_4", "child_zain", "youtube", "YouTube", System.currentTimeMillis() - 200 * 60 * 1000, 25)
        )
    )
    val usageEvents: StateFlow<List<UsageEvent>> = _usageEvents.asStateFlow()

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

    fun logout() {
        val prevSession = _currentSession.value
        familyListener?.remove()
        familyListener = null
        _children.value = emptyList()
        _selectedChildId.value = ""
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
                // Naye bachay ke liye starter policies
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
        "pairingCode" to pairingCode,
        "isRevoked" to isRevoked,
        "studentAuthUid" to studentAuthUid
    )

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

    // --- HOMEWORK & BALANCE MODE ---

    fun markHomeworkCompletedByStudent(taskId: String, completed: Boolean) {
        _homeworkTasks.value = _homeworkTasks.value.map { task ->
            if (task.taskId == taskId) {
                task.copy(
                    isCompletedByStudent = completed,
                    completedAt = if (completed) System.currentTimeMillis() else null,
                    // If student un-completes, also reset parent verification
                    isVerifiedByParent = if (!completed) false else task.isVerifiedByParent
                )
            } else task
        }
        val task = _homeworkTasks.value.find { it.taskId == taskId }
        if (task != null) {
            recomputeUnlock(task.childId)
            addAudit("HOMEWORK_STATUS_CHANGED", _currentSession.value?.email ?: "Student", "Marked '${task.title}' completed = $completed")
        }
    }

    fun verifyHomeworkByParent(taskId: String, verified: Boolean) {
        _homeworkTasks.value = _homeworkTasks.value.map { task ->
            if (task.taskId == taskId) {
                task.copy(
                    isVerifiedByParent = verified,
                    verifiedAt = if (verified) System.currentTimeMillis() else null
                )
            } else task
        }
        val task = _homeworkTasks.value.find { it.taskId == taskId }
        if (task != null) {
            recomputeUnlock(task.childId)
            addAudit("HOMEWORK_VERIFIED", _currentSession.value?.email ?: "Parent", "Parent verification for '${task.title}' set to $verified")
        }
    }

    private fun recomputeUnlock(childId: String) {
        val tasksForChild = _homeworkTasks.value.filter { it.childId == childId }
        val allDoneAndVerified = tasksForChild.isNotEmpty() && tasksForChild.all { it.isCompletedByStudent && it.isVerifiedByParent }

        _children.value = _children.value.map { child ->
            if (child.id == childId) {
                child.copy(homeworkUnlocked = allDoneAndVerified)
            } else child
        }

        addAudit(
            "RECOMPUTE_UNLOCK",
            "Server/CloudFunction",
            "Child $childId homeworkUnlocked evaluated to $allDoneAndVerified (${tasksForChild.count { it.isVerifiedByParent }}/${tasksForChild.size} verified)."
        )
    }

    // --- APP POLICIES & LIMITS ---

    fun updatePolicy(childId: String, appKey: String, limitMin: Int) {
        val currentChildPolicies = _policies.value[childId] ?: return
        val updated = currentChildPolicies.map {
            if (it.appKey == appKey) it.copy(dailyLimitMin = limitMin) else it
        }
        _policies.value = _policies.value.toMutableMap().apply {
            put(childId, updated)
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
        _children.value = _children.value.map {
            if (it.id == childId) it.copy(mirroringActive = active) else it
        }
        addAudit("MIRROR_TOGGLE", _currentSession.value?.email ?: "Parent", "Screen mirroring $childId set to $active")
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
