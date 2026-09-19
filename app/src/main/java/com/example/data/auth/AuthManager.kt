package com.example.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Authentication manager for Smart OSM.
 *
 * Implements the system User Identity model:
 * User Identity = Firebase Authentication user.uid
 *
 * Supports Google Sign-In via Android Credential Manager,
 * Email/Password authentication, and Anonymous guest authentication
 * while preserving Room local-first functionality.
 */
open class AuthManager(
    private val authProvider: () -> FirebaseAuth? = {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w("AuthManager", "FirebaseAuth not available: ${e.message}")
            null
        }
    }
) {
    companion object {
        private const val TAG = "AuthManager"
    }

    private val firebaseAuth: FirebaseAuth?
        get() = authProvider()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    open val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    val currentUid: String?
        get() = _currentUser.value?.uid

    private var cachedVillageNo: String = "8"
    private var cachedVillageName: String = "หมู่ 8 บ้านกร่างประดู่วัง"
    private var cachedSubdistrict: String = "ต.ป่าขะ"
    private var cachedDistrict: String = "อ.บ้านนา"
    private var cachedProvince: String = "จ.นครนายก"
    private var cachedPhoneNumber: String? = null
    private var cachedRoleTitle: String = "อสม. ประจำหมู่บ้าน"

    open fun loadSurveyorProfile(context: Context) {
        try {
            val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            cachedVillageNo = prefs.getString("surveyor_village_no", "8") ?: "8"
            cachedVillageName = prefs.getString("surveyor_village_name", "หมู่ 8 บ้านกร่างประดู่วัง") ?: "หมู่ 8 บ้านกร่างประดู่วัง"
            cachedSubdistrict = prefs.getString("surveyor_subdistrict", "ต.ป่าขะ") ?: "ต.ป่าขะ"
            cachedDistrict = prefs.getString("surveyor_district", "อ.บ้านนา") ?: "อ.บ้านนา"
            cachedProvince = prefs.getString("surveyor_province", "จ.นครนายก") ?: "จ.นครนายก"
            cachedPhoneNumber = prefs.getString("surveyor_phone", null)
            cachedRoleTitle = prefs.getString("surveyor_role", "อสม. ประจำหมู่บ้าน") ?: "อสม. ประจำหมู่บ้าน"
            updateUser(_currentUser.value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load surveyor profile: ${e.message}")
        }
    }

    open fun saveSurveyorProfile(
        context: Context,
        villageNo: String,
        villageName: String,
        subdistrict: String = "ต.ป่าขะ",
        district: String = "อ.บ้านนา",
        province: String = "จ.นครนายก",
        phone: String? = null,
        role: String? = "อสม. ประจำหมู่บ้าน"
    ) {
        try {
            val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            cachedVillageNo = villageNo.trim().ifBlank { "8" }
            cachedVillageName = villageName.trim().ifBlank { "หมู่ 8 บ้านกร่างประดู่วัง" }
            cachedSubdistrict = subdistrict.trim().ifBlank { "ต.ป่าขะ" }
            cachedDistrict = district.trim().ifBlank { "อ.บ้านนา" }
            cachedProvince = province.trim().ifBlank { "จ.นครนายก" }
            cachedPhoneNumber = phone?.trim()?.takeIf { it.isNotBlank() }
            cachedRoleTitle = role?.trim()?.takeIf { it.isNotBlank() } ?: "อสม. ประจำหมู่บ้าน"

            prefs.edit()
                .putString("surveyor_village_no", cachedVillageNo)
                .putString("surveyor_village_name", cachedVillageName)
                .putString("surveyor_subdistrict", cachedSubdistrict)
                .putString("surveyor_district", cachedDistrict)
                .putString("surveyor_province", cachedProvince)
                .putString("surveyor_phone", cachedPhoneNumber)
                .putString("surveyor_role", cachedRoleTitle)
                .putBoolean("surveyor_setup_completed", true)
                .apply()

            updateUser(_currentUser.value)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save surveyor profile", e)
        }
    }

    private fun updateUser(user: FirebaseUser?) {
        _currentUser.value = user
        _userProfile.value = user?.let {
            UserProfile.fromFirebaseUser(
                user = it,
                villageNo = cachedVillageNo,
                villageName = cachedVillageName,
                subdistrict = cachedSubdistrict,
                district = cachedDistrict,
                province = cachedProvince,
                phoneNumberOverride = cachedPhoneNumber,
                roleTitle = cachedRoleTitle
            )
        }
    }

    init {
        try {
            val auth = firebaseAuth
            if (auth != null) {
                updateUser(auth.currentUser)
                auth.addAuthStateListener { updatedAuth ->
                    updateUser(updatedAuth.currentUser)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize AuthStateListener: ${e.message}")
        }
    }

    open fun isAuthAvailable(): Boolean {
        return firebaseAuth != null
    }

    /**
     * Triggers Google Sign-In via Android Credential Manager and exchanges
     * the Google ID Token with Firebase Authentication.
     */
    open suspend fun signInWithGoogle(
        context: Context,
        customWebClientId: String? = null
    ): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val auth = firebaseAuth ?: return@withContext Result.failure(
                IllegalStateException("Firebase Auth ยังไม่ได้ตั้งค่าหรือพร้อมใช้งานในระบบนี้")
            )

            // Resolve Web Client ID from params, saved preferences, or generated strings.xml
            val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            if (!customWebClientId.isNullOrBlank()) {
                prefs.edit().putString("web_client_id", customWebClientId.trim()).apply()
            }
            val savedClientId = prefs.getString("web_client_id", null)

            val webClientIdResId = context.resources.getIdentifier(
                "default_web_client_id", "string", context.packageName
            )
            val defaultWebClientId = if (webClientIdResId != 0) {
                try { context.getString(webClientIdResId) } catch (e: Exception) { "" }
            } else {
                ""
            }

            val clientId = customWebClientId?.trim()?.takeIf { it.isNotBlank() }
                ?: savedClientId?.trim()?.takeIf { it.isNotBlank() }
                ?: defaultWebClientId.trim().takeIf { it.isNotBlank() }

            if (clientId.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "MISSING_WEB_CLIENT_ID: ยังไม่ได้กำหนดค่า Web Client ID สำหรับ Google Sign-In"
                    )
                )
            }

            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user
                    ?: throw IllegalStateException("Firebase Authentication ไม่สามารถสร้างหรือคืนค่า User ได้")

                updateUser(user)
                Log.i(TAG, "Google Sign-In successful. User UID: ${user.uid}")
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("ประเภทข้อมูล Credential ไม่ถูกต้อง: ${credential::class.java.name}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "User cancelled Google Sign-In prompt")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager failed: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
            Result.failure(e)
        }
    }

    /**
     * Signs in an existing user with Email and Password using Firebase Auth.
     */
    open suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val auth = firebaseAuth ?: return@withContext Result.failure(
                IllegalStateException("Firebase Auth ยังไม่ได้ตั้งค่าในระบบนี้")
            )
            val result = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw IllegalStateException("User is null after sign in")
            updateUser(user)
            Log.i(TAG, "Email Sign-In successful. User UID: ${user.uid}")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in with email failed", e)
            Result.failure(e)
        }
    }

    /**
     * Registers a new user with Email and Password using Firebase Auth.
     */
    open suspend fun signUpWithEmail(email: String, pass: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val auth = firebaseAuth ?: return@withContext Result.failure(
                IllegalStateException("Firebase Auth ยังไม่ได้ตั้งค่าในระบบนี้")
            )
            val result = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw IllegalStateException("User is null after registration")
            updateUser(user)
            Log.i(TAG, "Email Registration successful. User UID: ${user.uid}")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Registration with email failed", e)
            Result.failure(e)
        }
    }

    /**
     * Signs in anonymously to obtain a valid Firebase Authentication user.uid
     * without requiring an external OAuth provider setup.
     */
    open suspend fun signInAnonymously(): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val auth = firebaseAuth ?: return@withContext Result.failure(
                IllegalStateException("Firebase Auth ยังไม่ได้ตั้งค่าในระบบนี้")
            )
            val result = auth.signInAnonymously().await()
            val user = result.user ?: throw IllegalStateException("User is null after anonymous auth")
            updateUser(user)
            Log.i(TAG, "Anonymous Sign-In successful. User UID: ${user.uid}")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in failed", e)
            Result.failure(e)
        }
    }

    /**
     * Signs out the current user from Firebase Authentication.
     */
    open fun signOut() {
        try {
            firebaseAuth?.signOut()
            updateUser(null)
            Log.i(TAG, "User signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
        }
    }
}
