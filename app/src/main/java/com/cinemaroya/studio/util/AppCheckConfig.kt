package com.cinemaroya.studio.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import com.cinemaroya.studio.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

private const val TAG = "AppCheckInit"

// App Check contract keys. These strings are defined by the Firebase SDK and
// the runtime harness, so do not rename them.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private const val CI_DEBUG_TOKEN_RES = "ci_appcheck_debug_token"
private const val CI_RECAPTCHA_SITE_KEY_RES = "ci_recaptcha_site_key"
private const val CI_FIRESTORE_DB_ID_RES = "ci_firestore_database_id"

private const val APPCHECK_META_PREFS = "cineroya_appcheck_meta"
private const val KEY_HAS_HARNESS_DEBUG_TOKEN = "has_harness_debug_token"
private const val KEY_MODE_OVERRIDE = "appcheck_mode_override" // "auto", "debug", "recaptcha"
private const val KEY_CUSTOM_DEBUG_TOKEN = "custom_debug_token"
private const val KEY_CUSTOM_RECAPTCHA_SITE_KEY = "custom_recaptcha_site_key"

private const val DEFAULT_AI_STUDIO_PROJECT_ID = "gen-lang-client-0583048643"
private const val EXPECTED_REGISTERED_SHA1 = "DE:65:8D:87:9B:EB:AC:41:FF:25:7D:20:3B:13:D4:06:27:F4:20:1E"

data class AppCheckDiagnostics(
    val isFirebaseInitialized: Boolean,
    val projectId: String,
    val applicationId: String,
    val firebaseAppId: String,
    val signingSha1: String,
    val signingSha256: String,
    val expectedRegisteredSha1: String,
    val hasSigningSha1Mismatch: Boolean,
    val activeProviderMode: String,
    val activeModeLabelAr: String,
    val siteKeyPreview: String,
    val hasProjectMismatchWarning: Boolean,
    val deviceDebugUuid: String,
    val customDebugToken: String,
    val customSiteKey: String,
    val modeOverride: String
)

sealed interface AppCheckTokenVerification {
    data class Valid(
        val expireTimeMillis: Long,
        val tokenPreview: String
    ) : AppCheckTokenVerification

    data class Failed(
        val userMessageAr: String,
        val technicalDetail: String
    ) : AppCheckTokenVerification
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val app = FirebaseApp.getApps(context).firstOrNull()
        if (app == null) {
            Log.w(TAG, "FirebaseApp is not initialized (missing google-services.json); skipping App Check setup.")
            return
        }

        val persistenceKey = app.persistenceKey
        val debugStorePrefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        val metaPrefs = context.getSharedPreferences(APPCHECK_META_PREFS, Context.MODE_PRIVATE)
        val modeOverride = metaPrefs.getString(KEY_MODE_OVERRIDE, "auto") ?: "auto"

        if (BuildConfig.DEBUG) {
            // 1. Runtime harness Intent extra ALWAYS takes priority when present
            val harnessToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!harnessToken.isNullOrEmpty()) {
                debugStorePrefs.edit()
                    .putString(DEBUG_SECRET_KEY, harnessToken)
                    .commit()
                metaPrefs.edit()
                    .putBoolean(KEY_HAS_HARNESS_DEBUG_TOKEN, true)
                    .commit()
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "App Check debug secret configured from launch Intent.")
                return
            }

            // 2. Previously received harness debug token (unless user forced reCAPTCHA)
            if (metaPrefs.getBoolean(KEY_HAS_HARNESS_DEBUG_TOKEN, false) && modeOverride != "recaptcha") {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "Using previously configured App Check harness debug secret.")
                return
            }

            // 3. User-selected Debug Mode OR CI-injected debug token
            val ciDebugToken = resolveStringResource(context, CI_DEBUG_TOKEN_RES)
            val customDebugToken = metaPrefs.getString(KEY_CUSTOM_DEBUG_TOKEN, null)?.trim()
            val shouldUseDebug = modeOverride == "debug" ||
                (modeOverride == "auto" && !ciDebugToken.isNullOrBlank())

            if (shouldUseDebug) {
                val chosenToken = customDebugToken?.takeIf { it.isNotBlank() }
                    ?: ciDebugToken?.takeIf { it.isNotBlank() }

                if (chosenToken != null) {
                    debugStorePrefs.edit()
                        .putString(DEBUG_SECRET_KEY, chosenToken)
                        .commit()
                } else if (debugStorePrefs.getString(DEBUG_SECRET_KEY, null).isNullOrBlank()) {
                    // Generate and persist synchronously so the user can view/copy it on-device
                    val generatedUuid = UUID.randomUUID().toString()
                    debugStorePrefs.edit()
                        .putString(DEBUG_SECRET_KEY, generatedUuid)
                        .commit()
                }
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed for standalone/CI debug token.")
                return
            }
        }

        // 4. Standalone device / Release: use reCAPTCHA Enterprise
        val siteKey = resolveEffectiveRecaptchaSiteKey(context, metaPrefs)
        if (!siteKey.isNullOrBlank()) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance(siteKey)
            )
            Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
        } else if (BuildConfig.DEBUG) {
            if (debugStorePrefs.getString(DEBUG_SECRET_KEY, null).isNullOrBlank()) {
                debugStorePrefs.edit()
                    .putString(DEBUG_SECRET_KEY, UUID.randomUUID().toString())
                    .commit()
            }
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.w(TAG, "No recaptcha_site_key found; DebugAppCheckProviderFactory installed.")
        } else {
            Log.e(TAG, "recaptcha_site_key string resource missing or blank.")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

fun updateUserAppCheckSettings(
    context: Context,
    modeOverride: String,
    customDebugToken: String?,
    customSiteKey: String?
) {
    val cleanMode = if (modeOverride in listOf("auto", "debug", "recaptcha")) modeOverride else "auto"
    val cleanDebug = customDebugToken?.trim().orEmpty()
    val cleanSiteKey = customSiteKey?.trim().orEmpty()

    val metaPrefs = context.getSharedPreferences(APPCHECK_META_PREFS, Context.MODE_PRIVATE)
    metaPrefs.edit()
        .putString(KEY_MODE_OVERRIDE, cleanMode)
        .putString(KEY_CUSTOM_DEBUG_TOKEN, cleanDebug)
        .putString(KEY_CUSTOM_RECAPTCHA_SITE_KEY, cleanSiteKey)
        .apply {
            if (cleanMode != "auto") {
                putBoolean(KEY_HAS_HARNESS_DEBUG_TOKEN, false)
            }
        }
        .commit()

    // Ensure debug store is cleared/updated synchronously if switching back to auto without custom debug token
    val app = FirebaseApp.getApps(context).firstOrNull()
    if (app != null && cleanMode == "debug" && cleanDebug.isNotBlank()) {
        context.getSharedPreferences("$DEBUG_STORE_PREFIX${app.persistenceKey}", Context.MODE_PRIVATE)
            .edit()
            .putString(DEBUG_SECRET_KEY, cleanDebug)
            .commit()
    }

    configureAppCheck(context, null)
}

fun getAppCheckDiagnostics(context: Context): AppCheckDiagnostics {
    val app = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
        FirebaseApp.getApps(context).firstOrNull()
    }.getOrNull()

    val (sha1, sha256) = getAppSigningFingerprints(context)
    val metaPrefs = context.getSharedPreferences(APPCHECK_META_PREFS, Context.MODE_PRIVATE)
    val modeOverride = metaPrefs.getString(KEY_MODE_OVERRIDE, "auto") ?: "auto"
    val hasHarness = metaPrefs.getBoolean(KEY_HAS_HARNESS_DEBUG_TOKEN, false)
    val customDebugToken = metaPrefs.getString(KEY_CUSTOM_DEBUG_TOKEN, "").orEmpty()
    val customSiteKey = metaPrefs.getString(KEY_CUSTOM_RECAPTCHA_SITE_KEY, "").orEmpty()
    val ciDebugToken = resolveStringResource(context, CI_DEBUG_TOKEN_RES)
    val ciSiteKey = resolveStringResource(context, CI_RECAPTCHA_SITE_KEY_RES)
    val defaultSiteKey = resolveStringResource(context, RECAPTCHA_SITE_KEY_RES)

    val storedDebugUuid = if (app != null) {
        val prefs = context.getSharedPreferences("$DEBUG_STORE_PREFIX${app.persistenceKey}", Context.MODE_PRIVATE)
        val existing = prefs.getString(DEBUG_SECRET_KEY, "").orEmpty()
        if (existing.isBlank() && BuildConfig.DEBUG) {
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString(DEBUG_SECRET_KEY, generated).commit()
            generated
        } else {
            existing
        }
    } else {
        ""
    }

    val activeMode = when {
        app == null -> "none"
        BuildConfig.DEBUG && hasHarness && modeOverride != "recaptcha" -> "harness_debug"
        BuildConfig.DEBUG && modeOverride == "auto" && !ciDebugToken.isNullOrBlank() -> "ci_debug"
        BuildConfig.DEBUG && modeOverride == "debug" -> "custom_debug"
        !resolveEffectiveRecaptchaSiteKey(context, metaPrefs).isNullOrBlank() -> "recaptcha"
        BuildConfig.DEBUG -> "debug_fallback"
        else -> "none"
    }

    val activeModeLabelAr = when (activeMode) {
        "harness_debug" -> "Debug Provider (محاكي AI Studio)"
        "ci_debug" -> "Debug Provider (رمز GitHub CI)"
        "custom_debug" -> "Debug Provider (رمز تصحيح الهاتف)"
        "recaptcha" -> "reCAPTCHA Enterprise"
        "debug_fallback" -> "Debug Provider (احتياطي)"
        else -> "غير مهيأ"
    }

    val effectiveKey = resolveEffectiveRecaptchaSiteKey(context, metaPrefs).orEmpty()
    val siteKeyPreview = if (effectiveKey.length > 14) {
        "${effectiveKey.take(10)}...${effectiveKey.takeLast(6)}"
    } else {
        effectiveKey.ifBlank { "غير متوفر" }
    }

    val projectId = app?.options?.projectId ?: "غير متاح"
    val hasMismatch = activeMode == "recaptcha" &&
        projectId != DEFAULT_AI_STUDIO_PROJECT_ID &&
        customSiteKey.isBlank() &&
        ciSiteKey.isNullOrBlank() &&
        !defaultSiteKey.isNullOrBlank()

    val hasSigningSha1Mismatch = sha1 != "غير متاح" &&
        !sha1.equals(EXPECTED_REGISTERED_SHA1, ignoreCase = true)

    return AppCheckDiagnostics(
        isFirebaseInitialized = app != null,
        projectId = projectId,
        applicationId = BuildConfig.APPLICATION_ID,
        firebaseAppId = app?.options?.applicationId ?: "غير متاح",
        signingSha1 = sha1,
        signingSha256 = sha256,
        expectedRegisteredSha1 = EXPECTED_REGISTERED_SHA1,
        hasSigningSha1Mismatch = hasSigningSha1Mismatch,
        activeProviderMode = activeMode,
        activeModeLabelAr = activeModeLabelAr,
        siteKeyPreview = siteKeyPreview,
        hasProjectMismatchWarning = hasMismatch,
        deviceDebugUuid = storedDebugUuid,
        customDebugToken = customDebugToken,
        customSiteKey = customSiteKey,
        modeOverride = modeOverride
    )
}

suspend fun verifyAppCheckToken(
    context: Context,
    forceRefresh: Boolean = false
): AppCheckTokenVerification {
    if (FirebaseApp.getApps(context).isEmpty()) {
        FirebaseApp.initializeApp(context)
    }
    if (FirebaseApp.getApps(context).isEmpty()) {
        return AppCheckTokenVerification.Failed(
            userMessageAr = "ملف google-services.json غير مدمج في هذه النسخة.",
            technicalDetail = "FirebaseApp is not initialized"
        )
    }

    val diagnostics = getAppCheckDiagnostics(context)
    return try {
        var result = withTimeout(15_000L) {
            FirebaseAppCheck.getInstance().getAppCheckToken(forceRefresh).await()
        }
        var token = result.token
        var dummyError = if (token.isNotBlank()) extractDummyTokenError(token) else "Empty App Check token"

        // If a cached dummy error token was returned when forceRefresh == false, refresh once from server
        if (dummyError != null && !forceRefresh) {
            result = withTimeout(15_000L) {
                FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
            }
            token = result.token
            dummyError = if (token.isNotBlank()) extractDummyTokenError(token) else "Empty App Check token"
        }

        if (token.isBlank()) {
            return AppCheckTokenVerification.Failed(
                userMessageAr = "لم يرجع Firebase App Check أي رمز تحقق.",
                technicalDetail = "Empty App Check token returned by ${diagnostics.activeProviderMode}"
            )
        }

        // Firebase App Check SDK wraps internal provider/exchange failures into a dummy
        // base64 JSON error token instead of always throwing. Detect & decode it cleanly.
        if (dummyError != null) {
            Log.e(TAG, "App Check returned dummy error token: $dummyError")
            return buildDiagnosticFailure(context, diagnostics, dummyError)
        }

        val preview = if (token.length > 22) "${token.take(14)}...${token.takeLast(6)}" else token
        AppCheckTokenVerification.Valid(
            expireTimeMillis = result.expireTimeMillis,
            tokenPreview = preview
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val chainDetail = generateSequence<Throwable>(e) { it.cause }
            .mapNotNull { t ->
                val msg = t.localizedMessage?.trim().orEmpty()
                if (msg.isNotEmpty()) "${t::class.java.simpleName}: $msg" else t::class.java.simpleName
            }
            .distinct()
            .joinToString(" -> ")
        Log.e(TAG, "App Check token retrieval failed: $chainDetail", e)
        buildDiagnosticFailure(context, diagnostics, chainDetail)
    }
}

private fun buildDiagnosticFailure(
    context: Context,
    diagnostics: AppCheckDiagnostics,
    technicalDetail: String
): AppCheckTokenVerification.Failed {
    val msg = buildString {
        append("تعذر الحصول على رمز تحقق App Check صالح عبر (${diagnostics.activeModeLabelAr}).\n")
        if (diagnostics.hasSigningSha1Mismatch) {
            append("• سبب رئيسي مكتشف: هذا الـ APK موقّع ببصمة مختلفة (${diagnostics.signingSha1}) عن البصمة المسجلة في المشروع (${diagnostics.expectedRegisteredSha1}) لأن ملف debug.keystore.base64 غير مطابق أو لم يُرفع عند البناء.\n")
            append("• الحل الفوري بدون إعادة بناء: افتح «تشخيص وإعداد App Check» أدناه، اختر وضع Debug Token، انسخ رمز UUID وأضفه في Firebase Console > App Check > Apps > Manage debug tokens.\n")
            append("• الحل الدائم في GitHub: تأكد من وجود ملف debug.keystore.base64 (أو سر DEBUG_KEYSTORE_BASE64 / FIREBASE_APPCHECK_DEBUG_TOKEN) ثم أعد بناء APK.\n")
        } else if (diagnostics.hasProjectMismatchWarning) {
            append("• تنبيه: ملف google-services.json يخص المشروع (${diagnostics.projectId}) بينما مفتاح reCAPTCHA المدمج يخص مشروعاً آخر. أدخل مفتاح reCAPTCHA Android الخاص بمشروعك أو فعّل رمز التصحيح (Debug Token) من زر «تشخيص وإعداد App Check».\n")
        } else if (diagnostics.activeProviderMode == "recaptcha") {
            append("• عند تثبيت نسخة Debug خارج متجر Google Play أو خارج محاكي AI Studio، قد يرفض الخادم تحقق reCAPTCHA لنسخ Debug. افتح زر «تشخيص وإعداد App Check» أدناه واختر وضع Debug Token وانسخ الرمز إلى Firebase Console.\n")
        } else {
            append("• تأكد من تسجيل رمز التصحيح (Debug Token) لهذا الجهاز في Firebase Console > App Check > Apps > Manage debug tokens.\n")
        }
        append("• البصمة الحالية للتطبيق (SHA-1): ${diagnostics.signingSha1}\n")
        append("السبب التقني: $technicalDetail")
    }
    return AppCheckTokenVerification.Failed(
        userMessageAr = msg,
        technicalDetail = technicalDetail
    )
}

private fun extractDummyTokenError(token: String): String? {
    // Valid Firebase App Check tokens are JWTs (3 dot-separated parts).
    if (token.count { it == '.' } == 2 && !token.startsWith("eyJlcnJvciI6")) {
        return null
    }
    val decoded = runCatching {
        val bytes = Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        String(bytes, Charsets.UTF_8)
    }.getOrElse {
        runCatching {
            String(Base64.decode(token, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    } ?: return if (token.count { it == '.' } != 2) "Invalid non-JWT App Check token returned" else null

    return runCatching {
        val json = JSONObject(decoded)
        if (json.has("error")) json.optString("error", decoded) else null
    }.getOrNull()
}

fun resolveFirestoreDatabaseId(context: Context): String {
    val ciDb = resolveStringResource(context, CI_FIRESTORE_DB_ID_RES)
    if (!ciDb.isNullOrBlank()) return ciDb
    val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
    return if (resId != 0) context.getString(resId) else ""
}

private fun resolveEffectiveRecaptchaSiteKey(
    context: Context,
    metaPrefs: android.content.SharedPreferences
): String? {
    val customKey = metaPrefs.getString(KEY_CUSTOM_RECAPTCHA_SITE_KEY, null)?.trim()
    if (!customKey.isNullOrBlank()) return customKey
    val ciKey = resolveStringResource(context, CI_RECAPTCHA_SITE_KEY_RES)
    if (!ciKey.isNullOrBlank()) return ciKey
    return resolveStringResource(context, RECAPTCHA_SITE_KEY_RES)
}

private fun resolveStringResource(context: Context, name: String): String? {
    val resId = context.resources.getIdentifier(name, "string", context.packageName)
    if (resId == 0) return null
    return runCatching { context.getString(resId) }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
}

private fun getAppSigningFingerprints(context: Context): Pair<String, String> {
    return try {
        val pm = context.packageManager
        val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        }
        if (certBytes == null) {
            "غير متاح" to "غير متاح"
        } else {
            val sha1 = digestHex(certBytes, "SHA-1")
            val sha256 = digestHex(certBytes, "SHA-256")
            sha1 to sha256
        }
    } catch (e: Exception) {
        "غير متاح" to "غير متاح"
    }
}

private fun digestHex(bytes: ByteArray, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm).digest(bytes)
    return digest.joinToString(":") { "%02X".format(it) }
}
