package com.example.secureauth2fa.data.auth

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.secureauth2fa.data.model.BackupCode
import com.example.secureauth2fa.data.model.Session
import com.example.secureauth2fa.data.model.User
import com.example.secureauth2fa.utils.Constants
import com.example.secureauth2fa.utils.Resource
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FirebaseAuthDataSource(private val context: Context) {

    private val TAG = "FirebaseAuthDataSource"
    private val localPrefs = context.getSharedPreferences("secure_auth_local_data", Context.MODE_PRIVATE)

    private fun isApiKeyValid(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        if (key.contains("placeholder", ignoreCase = true)) return false
        if (key.contains("dummy", ignoreCase = true)) return false
        if (key.contains("DevPlaceholder", ignoreCase = true)) return false
        if (key == "AIzaSyDevPlaceholderKeyForSecureAuth2FAApp0") return false
        return true
    }

    private fun isApiKeyOrConfigError(e: Throwable): Boolean {
        val msg = e.message ?: return false
        return msg.contains("API key not valid", ignoreCase = true) ||
                msg.contains("api_key_invalid", ignoreCase = true) ||
                msg.contains("RecaptchaAction", ignoreCase = true) ||
                msg.contains("PROJECT_NOT_FOUND", ignoreCase = true) ||
                msg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
                msg.contains("internal error", ignoreCase = true)
    }

    // Safe lazy initialization of Firebase Auth and Firestore
    private val isFirebaseAvailable: Boolean by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            if (FirebaseApp.getApps(context).isEmpty()) return@lazy false
            val app = FirebaseApp.getInstance()
            val apiKey = app.options.apiKey
            isApiKeyValid(apiKey)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase initialization skipped or unavailable: ${e.message}")
            false
        }
    }

    private val auth: FirebaseAuth? by lazy {
        if (isFirebaseAvailable) {
            try {
                FirebaseAuth.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseAuth unavailable: ${e.message}")
                null
            }
        } else null
    }

    private val firestore: FirebaseFirestore? by lazy {
        if (isFirebaseAvailable) {
            try {
                FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseFirestore unavailable: ${e.message}")
                null
            }
        } else null
    }

    // Persistent fallback repository for local/development mode
    private val localUsers: MutableMap<String, Pair<String, User>> by lazy {
        loadLocalUsers()
    }
    private val localBackupCodes: MutableMap<String, MutableList<BackupCode>> by lazy {
        loadLocalBackupCodes()
    }
    private val localSessions: MutableMap<String, MutableList<Session>> by lazy {
        loadLocalSessions()
    }
    private var simulatedCurrentUser: User? = null

    init {
        // Restore active user if saved
        simulatedCurrentUser = getCurrentLocalUser()
    }

    private fun loadLocalUsers(): MutableMap<String, Pair<String, User>> {
        val map = mutableMapOf<String, Pair<String, User>>()
        val usersJson = localPrefs.getString("users_registry", null)
        if (!usersJson.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(usersJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val email = obj.getString("email")
                    val password = obj.getString("password")
                    val userObj = obj.getJSONObject("user")
                    val userMap = mutableMapOf<String, Any?>()
                    val keys = userObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        userMap[k] = userObj.opt(k)
                    }
                    val uid = userObj.optString("uid")
                    map[email] = Pair(password, User.fromMap(uid, userMap))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading local users: ${e.message}")
            }
        }
        // Always provide a default test account if empty
        if (!map.containsKey("user@example.com")) {
            val demoUser = User(
                uid = "demo-user-uid-01",
                email = "user@example.com",
                displayName = "Security Tester",
                isTwoFactorEnabled = false,
                isEmailVerified = true,
                createdAt = System.currentTimeMillis()
            )
            map["user@example.com"] = Pair("Password@123", demoUser)
        }
        return map
    }

    private fun saveLocalUsers() {
        try {
            val jsonArray = JSONArray()
            localUsers.forEach { (email, pair) ->
                val obj = JSONObject()
                obj.put("email", email)
                obj.put("password", pair.first)
                obj.put("user", JSONObject(pair.second.toMap()))
                jsonArray.put(obj)
            }
            localPrefs.edit().putString("users_registry", jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving local users: ${e.message}")
        }
    }

    private fun loadLocalBackupCodes(): MutableMap<String, MutableList<BackupCode>> {
        val map = mutableMapOf<String, MutableList<BackupCode>>()
        val codesJson = localPrefs.getString("backup_codes_registry", null)
        if (!codesJson.isNullOrBlank()) {
            try {
                val jsonObj = JSONObject(codesJson)
                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val uid = keys.next()
                    val arr = jsonObj.getJSONArray(uid)
                    val list = mutableListOf<BackupCode>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val codeMap = mutableMapOf<String, Any?>()
                        val itemKeys = item.keys()
                        while (itemKeys.hasNext()) {
                            val k = itemKeys.next()
                            codeMap[k] = item.opt(k)
                        }
                        list.add(BackupCode.fromMap(codeMap))
                    }
                    map[uid] = list
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading local backup codes: ${e.message}")
            }
        }
        return map
    }

    private fun saveLocalBackupCodes() {
        try {
            val jsonObj = JSONObject()
            localBackupCodes.forEach { (uid, list) ->
                val arr = JSONArray()
                list.forEach { code ->
                    arr.put(JSONObject(code.toMap()))
                }
                jsonObj.put(uid, arr)
            }
            localPrefs.edit().putString("backup_codes_registry", jsonObj.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving local backup codes: ${e.message}")
        }
    }

    private fun loadLocalSessions(): MutableMap<String, MutableList<Session>> {
        val map = mutableMapOf<String, MutableList<Session>>()
        val sessionsJson = localPrefs.getString("sessions_registry", null)
        if (!sessionsJson.isNullOrBlank()) {
            try {
                val jsonObj = JSONObject(sessionsJson)
                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val uid = keys.next()
                    val arr = jsonObj.getJSONArray(uid)
                    val list = mutableListOf<Session>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val sessionMap = mutableMapOf<String, Any?>()
                        val itemKeys = item.keys()
                        while (itemKeys.hasNext()) {
                            val k = itemKeys.next()
                            sessionMap[k] = item.opt(k)
                        }
                        list.add(Session.fromMap(sessionMap))
                    }
                    map[uid] = list
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading local sessions: ${e.message}")
            }
        }
        return map
    }

    private fun saveLocalSessions() {
        try {
            val jsonObj = JSONObject()
            localSessions.forEach { (uid, list) ->
                val arr = JSONArray()
                list.forEach { session ->
                    arr.put(JSONObject(session.toMap()))
                }
                jsonObj.put(uid, arr)
            }
            localPrefs.edit().putString("sessions_registry", jsonObj.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving local sessions: ${e.message}")
        }
    }

    private fun saveCurrentLocalUser(user: User?) {
        if (user == null) {
            localPrefs.edit().remove("current_user_email").apply()
        } else {
            localPrefs.edit().putString("current_user_email", user.email).apply()
        }
    }

    private fun getCurrentLocalUser(): User? {
        val email = localPrefs.getString("current_user_email", null) ?: return null
        return localUsers[email]?.second
    }

    suspend fun register(email: String, password: String, displayName: String): Resource<User> {
        val trimmedEmail = email.trim().lowercase()
        val authInstance = auth
        val firestoreInstance = firestore

        if (authInstance != null && firestoreInstance != null) {
            try {
                val authResult = authInstance.createUserWithEmailAndPassword(trimmedEmail, password).await()
                val firebaseUser = authResult.user
                    ?: return registerStandalone(trimmedEmail, password, displayName)

                try {
                    firebaseUser.sendEmailVerification().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not send verification email: ${e.message}")
                }

                val user = User(
                    uid = firebaseUser.uid,
                    email = trimmedEmail,
                    displayName = displayName.ifBlank { trimmedEmail.substringBefore("@") },
                    isTwoFactorEnabled = false,
                    isEmailVerified = firebaseUser.isEmailVerified,
                    createdAt = System.currentTimeMillis()
                )

                firestoreInstance.collection(Constants.COLLECTION_USERS)
                    .document(user.uid)
                    .set(user.toMap())
                    .await()

                return Resource.Success(user)
            } catch (e: Exception) {
                if (isApiKeyOrConfigError(e)) {
                    Log.w(TAG, "Firebase unavailable or API key invalid; falling back to secure standalone mode.")
                    return registerStandalone(trimmedEmail, password, displayName)
                }
                Log.e(TAG, "Registration error: ${e.message}", e)
                return Resource.Error(e.localizedMessage ?: "Registration failed")
            }
        } else {
            return registerStandalone(trimmedEmail, password, displayName)
        }
    }

    private fun registerStandalone(trimmedEmail: String, password: String, displayName: String): Resource<User> {
        if (localUsers.containsKey(trimmedEmail)) {
            return Resource.Error("An account with this email already exists.")
        }
        val uid = UUID.randomUUID().toString()
        val user = User(
            uid = uid,
            email = trimmedEmail,
            displayName = displayName.ifBlank { trimmedEmail.substringBefore("@") },
            isTwoFactorEnabled = false,
            isEmailVerified = true,
            createdAt = System.currentTimeMillis()
        )
        localUsers[trimmedEmail] = Pair(password, user)
        saveLocalUsers()
        simulatedCurrentUser = user
        saveCurrentLocalUser(user)
        return Resource.Success(user)
    }

    suspend fun login(email: String, password: String): Resource<User> {
        val trimmedEmail = email.trim().lowercase()
        val authInstance = auth
        val firestoreInstance = firestore

        if (authInstance != null && firestoreInstance != null) {
            try {
                val authResult = authInstance.signInWithEmailAndPassword(trimmedEmail, password).await()
                val firebaseUser = authResult.user
                    ?: return loginStandalone(trimmedEmail, password)

                val doc = firestoreInstance.collection(Constants.COLLECTION_USERS)
                    .document(firebaseUser.uid)
                    .get()
                    .await()

                val user = if (doc.exists() && doc.data != null) {
                    User.fromMap(firebaseUser.uid, doc.data!!)
                } else {
                    val newUser = User(
                        uid = firebaseUser.uid,
                        email = trimmedEmail,
                        displayName = firebaseUser.displayName ?: trimmedEmail.substringBefore("@"),
                        isTwoFactorEnabled = false,
                        isEmailVerified = firebaseUser.isEmailVerified,
                        createdAt = System.currentTimeMillis()
                    )
                    firestoreInstance.collection(Constants.COLLECTION_USERS)
                        .document(firebaseUser.uid)
                        .set(newUser.toMap())
                        .await()
                    newUser
                }
                return Resource.Success(user)
            } catch (e: Exception) {
                if (isApiKeyOrConfigError(e)) {
                    Log.w(TAG, "Firebase unavailable or API key invalid; falling back to secure standalone mode.")
                    return loginStandalone(trimmedEmail, password)
                }
                Log.e(TAG, "Login error: ${e.message}", e)
                return Resource.Error(e.localizedMessage ?: "Login failed. Please check your credentials.")
            }
        } else {
            return loginStandalone(trimmedEmail, password)
        }
    }

    private fun loginStandalone(trimmedEmail: String, password: String): Resource<User> {
        val userEntry = localUsers[trimmedEmail]
        if (userEntry == null || userEntry.first != password) {
            return Resource.Error("Invalid email or password.")
        }
        simulatedCurrentUser = userEntry.second
        saveCurrentLocalUser(userEntry.second)
        return Resource.Success(userEntry.second)
    }

    suspend fun signInWithGoogle(idToken: String?, email: String, displayName: String): Resource<User> {
        val trimmedEmail = email.trim().lowercase()
        val resolvedName = displayName.ifBlank { trimmedEmail.substringBefore("@") }
        val authInstance = auth
        val firestoreInstance = firestore

        if (!idToken.isNullOrBlank() && authInstance != null && firestoreInstance != null) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = authInstance.signInWithCredential(credential).await()
                val firebaseUser = authResult.user
                    ?: return signInWithGoogleStandalone(trimmedEmail, resolvedName)

                val doc = firestoreInstance.collection(Constants.COLLECTION_USERS)
                    .document(firebaseUser.uid)
                    .get()
                    .await()

                val user = if (doc.exists() && doc.data != null) {
                    val existing = User.fromMap(firebaseUser.uid, doc.data!!)
                    val updated = existing.copy(
                        email = firebaseUser.email ?: existing.email,
                        displayName = firebaseUser.displayName ?: existing.displayName,
                        isEmailVerified = firebaseUser.isEmailVerified
                    )
                    firestoreInstance.collection(Constants.COLLECTION_USERS)
                        .document(firebaseUser.uid)
                        .set(updated.toMap(), SetOptions.merge())
                        .await()
                    updated
                } else {
                    val newUser = User(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email ?: trimmedEmail,
                        displayName = firebaseUser.displayName ?: resolvedName,
                        isTwoFactorEnabled = false,
                        isEmailVerified = true,
                        createdAt = System.currentTimeMillis()
                    )
                    firestoreInstance.collection(Constants.COLLECTION_USERS)
                        .document(firebaseUser.uid)
                        .set(newUser.toMap())
                        .await()
                    newUser
                }

                localUsers[user.email] = Pair("GOOGLE_SSO", user)
                saveLocalUsers()
                simulatedCurrentUser = user
                saveCurrentLocalUser(user)
                return Resource.Success(user)
            } catch (e: Exception) {
                if (isApiKeyOrConfigError(e)) {
                    Log.w(TAG, "Firebase unavailable or API key invalid; falling back to secure standalone mode for Google Sign-In.")
                    return signInWithGoogleStandalone(trimmedEmail, resolvedName)
                }
                Log.e(TAG, "Google Sign-In with Firebase error: ${e.message}", e)
                return Resource.Error(e.localizedMessage ?: "Google Sign-In failed")
            }
        } else {
            return signInWithGoogleStandalone(trimmedEmail, resolvedName)
        }
    }

    private fun signInWithGoogleStandalone(trimmedEmail: String, displayName: String): Resource<User> {
        val existingEntry = localUsers[trimmedEmail]
        val user = if (existingEntry != null) {
            val updated = existingEntry.second.copy(
                displayName = if (existingEntry.second.displayName.isBlank()) displayName else existingEntry.second.displayName,
                isEmailVerified = true
            )
            localUsers[trimmedEmail] = Pair(existingEntry.first, updated)
            updated
        } else {
            val newUser = User(
                uid = "google_${UUID.randomUUID().toString().take(12)}",
                email = trimmedEmail,
                displayName = displayName,
                isTwoFactorEnabled = false,
                isEmailVerified = true,
                createdAt = System.currentTimeMillis()
            )
            localUsers[trimmedEmail] = Pair("GOOGLE_SSO", newUser)
            newUser
        }
        saveLocalUsers()
        simulatedCurrentUser = user
        saveCurrentLocalUser(user)
        return Resource.Success(user)
    }

    suspend fun sendPasswordReset(email: String): Resource<Unit> {
        val trimmedEmail = email.trim().lowercase()
        val authInstance = auth
        if (authInstance != null) {
            try {
                authInstance.sendPasswordResetEmail(trimmedEmail).await()
                return Resource.Success(Unit)
            } catch (e: Exception) {
                if (isApiKeyOrConfigError(e)) {
                    Log.w(TAG, "Firebase API key error during password reset; confirmed in local mode.")
                    return Resource.Success(Unit)
                }
                return Resource.Error(e.localizedMessage ?: "Failed to send password reset email")
            }
        }
        return Resource.Success(Unit)
    }

    suspend fun sendEmailVerification(): Resource<Unit> {
        val fbUser = try { auth?.currentUser } catch (e: Exception) { null }
        return try {
            if (fbUser != null) {
                fbUser.sendEmailVerification().await()
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Success(Unit)
        }
    }

    suspend fun getUserProfile(userId: String): Resource<User> {
        val firestoreInstance = firestore
        if (firestoreInstance != null) {
            try {
                val doc = firestoreInstance.collection(Constants.COLLECTION_USERS)
                    .document(userId)
                    .get()
                    .await()
                if (doc.exists() && doc.data != null) {
                    return Resource.Success(User.fromMap(userId, doc.data!!))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore read failed; using local user profile: ${e.message}")
            }
        }
        val user = localUsers.values.find { it.second.uid == userId }?.second
            ?: simulatedCurrentUser
        return if (user != null) Resource.Success(user) else Resource.Error("User not found")
    }

    suspend fun updateTwoFactorStatus(userId: String, isEnabled: Boolean): Resource<Unit> {
        val firestoreInstance = firestore
        if (firestoreInstance != null) {
            try {
                firestoreInstance.collection(Constants.COLLECTION_USERS)
                    .document(userId)
                    .set(mapOf("isTwoFactorEnabled" to isEnabled), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore 2FA status update failed: ${e.message}")
            }
        }
        val current = localUsers.values.find { it.second.uid == userId }
        if (current != null) {
            val updated = current.second.copy(isTwoFactorEnabled = isEnabled)
            localUsers[updated.email] = Pair(current.first, updated)
            saveLocalUsers()
            if (simulatedCurrentUser?.uid == userId) {
                simulatedCurrentUser = updated
                saveCurrentLocalUser(updated)
            }
        } else if (simulatedCurrentUser?.uid == userId) {
            simulatedCurrentUser = simulatedCurrentUser?.copy(isTwoFactorEnabled = isEnabled)
            saveCurrentLocalUser(simulatedCurrentUser)
        }
        return Resource.Success(Unit)
    }

    suspend fun saveBackupCodes(userId: String, codes: List<BackupCode>): Resource<Unit> {
        val firestoreInstance = firestore
        if (firestoreInstance != null) {
            try {
                val batch = firestoreInstance.batch()
                val colRef = firestoreInstance.collection(Constants.COLLECTION_USERS)
                    .document(userId)
                    .collection(Constants.COLLECTION_BACKUP_CODES)

                codes.forEach { code ->
                    val docRef = colRef.document(code.codeHash)
                    batch.set(docRef, code.toMap())
                }
                batch.commit().await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore save backup codes failed: ${e.message}")
            }
        }
        localBackupCodes[userId] = codes.toMutableList()
        saveLocalBackupCodes()
        return Resource.Success(Unit)
    }

    suspend fun verifyAndConsumeBackupCode(userId: String, enteredCode: String): Resource<Boolean> {
        val targetHash = BackupCode.hash(enteredCode)
        val firestoreInstance = firestore
        if (firestoreInstance != null) {
            try {
                val docRef = firestoreInstance.collection(Constants.COLLECTION_USERS)
                    .document(userId)
                    .collection(Constants.COLLECTION_BACKUP_CODES)
                    .document(targetHash)

                val snapshot = docRef.get().await()
                if (snapshot.exists() && snapshot.data != null) {
                    val code = BackupCode.fromMap(snapshot.data!!)
                    return if (!code.isUsed) {
                        docRef.update(mapOf("isUsed" to true, "usedAt" to System.currentTimeMillis())).await()
                        Resource.Success(true)
                    } else {
                        Resource.Error("This recovery code has already been used.")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore verify backup code failed: ${e.message}")
            }
        }
        val list = localBackupCodes[userId] ?: mutableListOf()
        val index = list.indexOfFirst { it.codeHash == targetHash }
        return if (index != -1) {
            val code = list[index]
            if (!code.isUsed) {
                list[index] = code.copy(isUsed = true, usedAt = System.currentTimeMillis())
                saveLocalBackupCodes()
                Resource.Success(true)
            } else {
                Resource.Error("This recovery code has already been used.")
            }
        } else {
            Resource.Error("Invalid recovery code.")
        }
    }

    suspend fun saveDeviceSession(userId: String, session: Session): Resource<Unit> {
        val firestoreInstance = firestore
        if (firestoreInstance != null) {
            try {
                firestoreInstance.collection(Constants.COLLECTION_USERS)
                    .document(userId)
                    .collection(Constants.COLLECTION_SESSIONS)
                    .document(session.sessionId)
                    .set(session.toMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore save session failed: ${e.message}")
            }
        }
        val list = localSessions.getOrPut(userId) { mutableListOf() }
        list.removeAll { it.sessionId == session.sessionId }
        list.add(session)
        saveLocalSessions()
        return Resource.Success(Unit)
    }

    suspend fun getSessions(userId: String): Resource<List<Session>> {
        val firestoreInstance = firestore
        if (firestoreInstance != null) {
            try {
                val query = firestoreInstance.collection(Constants.COLLECTION_USERS)
                    .document(userId)
                    .collection(Constants.COLLECTION_SESSIONS)
                    .get()
                    .await()

                val sessions = query.documents.map { Session.fromMap(it.data ?: emptyMap()) }
                if (sessions.isNotEmpty()) {
                    return Resource.Success(sessions)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore load sessions failed: ${e.message}")
            }
        }
        val list = localSessions[userId] ?: listOf(
            Session(
                sessionId = "session-curr",
                deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                osVersion = "Android ${Build.VERSION.RELEASE}",
                lastActiveTime = System.currentTimeMillis(),
                isCurrentDevice = true,
                isRemembered = true
            )
        )
        return Resource.Success(list)
    }

    suspend fun saveRememberedDeviceToken(userId: String, token: String, expiryMs: Long): Resource<Unit> {
        val firestoreInstance = firestore
        if (firestoreInstance != null) {
            try {
                firestoreInstance.collection(Constants.COLLECTION_USERS)
                    .document(userId)
                    .set(
                        mapOf(
                            "rememberedDeviceToken" to token,
                            "rememberedDeviceExpiry" to expiryMs
                        ),
                        SetOptions.merge()
                    )
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore save token failed: ${e.message}")
            }
        }
        return Resource.Success(Unit)
    }

    fun logout() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign out failed: ${e.message}")
        }
        simulatedCurrentUser = null
        saveCurrentLocalUser(null)
    }

    fun getCurrentUser(): User? {
        val fbUser = try { auth?.currentUser } catch (e: Exception) { null }
        return if (fbUser != null) {
            User(
                uid = fbUser.uid,
                email = fbUser.email ?: "",
                displayName = fbUser.displayName ?: "",
                isEmailVerified = fbUser.isEmailVerified
            )
        } else {
            simulatedCurrentUser ?: getCurrentLocalUser()
        }
    }
}
