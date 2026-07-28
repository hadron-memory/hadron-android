package com.hadronmemory.hadronkit

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Android counterpart of hadron-ios' `KeychainStore`: a small
 * encrypted key/value store keyed on the app's store name (from
 * [HadronClientConfig.storeName]).
 *
 * Values are sealed with an AES-256-GCM key that lives in the AndroidKeyStore
 * — hardware-backed where the device offers it, and never extractable — and
 * the ciphertext is parked in SharedPreferences. Together with
 * `allowBackup=false` and the manifest's `dataExtractionRules` (both cloud
 * backup and device-to-device transfer excluded), that reproduces the iOS
 * `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` posture: readable on a
 * cold launch without a user prompt, but it never leaves this device.
 *
 * Deliberately NOT `androidx.security:security-crypto` — that library is
 * deprecated with no drop-in successor, and this is ~60 lines.
 */
class SecureStore(context: Context, storeName: String) {

    enum class Key(val raw: String) {
        ACCESS_TOKEN("access-token"),
        CLIENT_ID("oauth-client-id"),

        /**
         * The in-flight authorization (PKCE verifier + state). Persisted so a
         * redirect that arrives after the app process was killed — routine on
         * Android while the browser is in front, and near-certain on the email
         * magic-link leg — can still complete the exchange.
         */
        PENDING_AUTH("pending-auth"),
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(storeName, Context.MODE_PRIVATE)

    private val keyAlias = "$storeName.secure-store"

    fun set(value: String?, key: Key) {
        if (value.isNullOrEmpty()) {
            delete(key)
            return
        }
        try {
            prefs.edit().putString(key.raw, seal(value)).apply()
        } catch (e: GeneralSecurityException) {
            // Surface the failure — a silently-dropped token loops sign-in.
            Log.e(TAG, "failed to store ${key.raw}", e)
        }
    }

    fun get(key: Key): String? {
        val stored = prefs.getString(key.raw, null) ?: return null
        return try {
            unseal(stored)
        } catch (e: GeneralSecurityException) {
            // The Keystore key is gone or no longer usable — e.g. the user
            // removed the lock screen, or the prefs file was restored onto a
            // device that never held the key. The ciphertext is now junk;
            // drop it so the app falls back to signed-out instead of looping
            // on an undecryptable value.
            Log.w(TAG, "dropping undecryptable ${key.raw}", e)
            delete(key)
            null
        }
    }

    fun delete(key: Key) {
        prefs.edit().remove(key.raw).apply()
    }

    // MARK: - Crypto

    private fun seal(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // "<iv>:<ciphertext>" — GCM generates a fresh 12-byte IV per call and
        // it must be stored alongside the value it sealed.
        return encode(cipher.iv) + ":" + encode(ciphertext)
    }

    private fun unseal(stored: String): String {
        val parts = stored.split(":")
        if (parts.size != 2) throw GeneralSecurityException("malformed stored value")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, decode(parts[0])),
        )
        return String(cipher.doFinal(decode(parts[1])), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // No setUserAuthenticationRequired: the token has to be
                // readable on a cold launch so the app can auto-sign-in,
                // which is exactly what AfterFirstUnlock buys on iOS.
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val TAG = "SecureStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
