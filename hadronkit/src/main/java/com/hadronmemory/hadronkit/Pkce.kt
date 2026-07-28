package com.hadronmemory.hadronkit

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** A PKCE pair (RFC 7636). The server supports the `S256` method only. */
class PkcePair(
    val codeVerifier: String = randomUrlSafeString(byteCount = 64),
) {
    val codeChallenge: String = base64Url(
        MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
    )

    val method: String get() = "S256"

    companion object {
        /** Cryptographically-random base64url string with no padding. */
        private fun randomUrlSafeString(byteCount: Int): String {
            val bytes = ByteArray(byteCount)
            SecureRandom().nextBytes(bytes)
            return base64Url(bytes)
        }

        /** base64url (RFC 4648 §5), padding stripped and never line-wrapped. */
        private fun base64Url(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
