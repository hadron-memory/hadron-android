package com.hadronmemory.hadronkit

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID

/**
 * Drives the OAuth 2.1 authorization-code + PKCE flow against Hadron's AS
 * (spec `cor:aut:030:01`): discovery → dynamic client registration → browser
 * consent in a Custom Tab → token exchange. Yields a long-lived `hdr_user_*`
 * bearer token usable on `/graphql`.
 *
 * Unlike hadron-ios' `OAuthService`, this is **not** one suspending call that
 * spans the browser. `ASWebAuthenticationSession` hands the redirect back to
 * the same in-memory continuation; Android hands it to a fresh Intent, and the
 * app process may well have been killed while the browser was in front. So the
 * flow is split in two around a durable [SecureStore.Key.PENDING_AUTH] record:
 *
 *  1. [beginAuthorization] — discover, register, persist the PKCE verifier and
 *     state, open the Custom Tab.
 *  2. [completeAuthorization] — called when the redirect arrives (from
 *     `onCreate`/`onNewIntent`), verify state, exchange the code.
 *
 * The upside is that the email magic-link leg — where the user leaves for
 * their mail app, possibly for minutes — completes for free.
 */
class OAuthService(
    private val config: HadronClientConfig,
    private val store: SecureStore,
) {

    /**
     * First-party identity provider the server's authorize page routes the
     * unauthenticated leg through (`login_provider` query param, spec 025).
     * Omitting it falls back to the server default (GitHub) — always pass one
     * explicitly in UI with multiple sign-in buttons.
     */
    enum class LoginProvider(val value: String) {
        GITHUB("github"),
        GOOGLE("google"),
        APPLE("apple"),

        /**
         * Magic-link email (hadron-server#804). The link is tapped in the mail
         * app and completes in the system browser, OUTSIDE the Custom Tab this
         * flow opened — the app's custom-scheme intent-filter is what catches
         * the final redirect.
         */
        EMAIL("email"),
        ;

        companion object {
            fun fromValue(value: String?): LoginProvider? =
                entries.firstOrNull { it.value == value }
        }
    }

    sealed class OAuthException(override val message: String) : Exception(message) {
        data object DiscoveryFailed :
            OAuthException("Couldn't reach the Hadron sign-in service.") {
            private fun readResolve(): Any = DiscoveryFailed
        }

        data object UserCancelled : OAuthException("Sign-in was cancelled.") {
            private fun readResolve(): Any = UserCancelled
        }

        data object StateMismatch :
            OAuthException("Sign-in failed a security check (state mismatch).") {
            private fun readResolve(): Any = StateMismatch
        }

        data object MissingCode :
            OAuthException("Sign-in didn't return an authorization code.") {
            private fun readResolve(): Any = MissingCode
        }

        data object NoPendingAuthorization :
            OAuthException("That sign-in link has already been used or has expired.") {
            private fun readResolve(): Any = NoPendingAuthorization
        }

        data object NoBrowser :
            OAuthException("No browser is available to sign in with.") {
            private fun readResolve(): Any = NoBrowser
        }

        class RegistrationFailed(detail: String) :
            OAuthException("Client registration failed: $detail")

        class AuthorizationFailed(detail: String) :
            OAuthException("Authorization failed: $detail")

        class TokenExchangeFailed(detail: String) :
            OAuthException("Token exchange failed: $detail")
    }

    // MARK: - Public entry points

    /** True when [uri] is the redirect this app registered for. */
    fun isCallback(uri: Uri): Boolean =
        uri.scheme?.equals(config.callbackScheme, ignoreCase = true) == true

    /** True while an authorization is waiting for its redirect. */
    fun hasPendingAuthorization(): Boolean = store.get(SecureStore.Key.PENDING_AUTH) != null

    /** The provider the pending authorization was started with, if any. */
    fun pendingProvider(): LoginProvider? =
        LoginProvider.fromValue(loadPending()?.provider)

    fun cancelPendingAuthorization() {
        store.delete(SecureStore.Key.PENDING_AUTH)
    }

    /**
     * Run discovery + registration, then hand off to the browser. Returns once
     * the Custom Tab has been launched; the flow resumes in
     * [completeAuthorization] when the redirect comes back.
     */
    suspend fun beginAuthorization(activity: Activity, provider: LoginProvider) {
        val metadata = fetchMetadata()
        val clientId = ensureClientId(metadata.registration_endpoint)

        val pkce = PkcePair()
        val state = UUID.randomUUID().toString()

        store.set(
            HadronHttp.json.encodeToString(
                PendingAuth.serializer(),
                PendingAuth(
                    state = state,
                    codeVerifier = pkce.codeVerifier,
                    clientId = clientId,
                    tokenEndpoint = metadata.token_endpoint,
                    provider = provider.value,
                ),
            ),
            SecureStore.Key.PENDING_AUTH,
        )

        val authorizeUrl = buildAuthorizeUrl(
            endpoint = metadata.authorization_endpoint,
            clientId = clientId,
            pkce = pkce,
            state = state,
            provider = provider,
        )

        try {
            withContext(Dispatchers.Main) {
                launchBrowser(activity, authorizeUrl)
            }
        } catch (e: Throwable) {
            // Nothing will ever redirect back — don't leave a record that
            // makes the next launch look like a sign-in is in flight.
            cancelPendingAuthorization()
            throw e
        }
    }

    /**
     * Complete an in-flight authorization from the redirect the OS delivered.
     * Returns the issued access token.
     */
    suspend fun completeAuthorization(uri: Uri): String {
        val pending = loadPending() ?: throw OAuthException.NoPendingAuthorization
        try {
            val code = parseCallback(uri, pending.state)
            val token = exchangeToken(
                endpoint = pending.tokenEndpoint,
                code = code,
                clientId = pending.clientId,
                verifier = pending.codeVerifier,
            )
            return token
        } finally {
            // One redirect per authorization, success or failure — an
            // authorization code is single-use and a stale record would let a
            // later, unrelated redirect look legitimate.
            cancelPendingAuthorization()
        }
    }

    // MARK: - Steps

    private suspend fun fetchMetadata(): AsMetadata = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(config.discoveryUrl).get().build()
        val (status, text) = try {
            HadronHttp.client.newCall(request).execute().use { it.code to it.body.string() }
        } catch (e: IOException) {
            throw OAuthException.DiscoveryFailed
        }
        if (status != 200) throw OAuthException.DiscoveryFailed
        try {
            HadronHttp.json.decodeFromString(AsMetadata.serializer(), text)
        } catch (e: Exception) {
            throw OAuthException.DiscoveryFailed
        }
    }

    /**
     * Reuse a cached client id, else dynamically register one and cache it.
     * The cache matters: `/oauth/register` is rate-limited to 10/min/IP, so
     * re-registering on every launch would lock the user out.
     */
    private suspend fun ensureClientId(registrationEndpoint: String?): String {
        store.get(SecureStore.Key.CLIENT_ID)?.let { return it }

        val endpoint = registrationEndpoint ?: config.registrationFallbackUrl
        val body = HadronHttp.json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("redirect_uris", buildJsonArray { add(config.redirectUri) })
                put("client_name", config.clientName)
                put("token_endpoint_auth_method", "none")
                put("grant_types", buildJsonArray { add("authorization_code") })
                put("response_types", buildJsonArray { add("code") })
            },
        )

        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(endpoint)
                .post(body.toRequestBody(HadronHttp.jsonMediaType))
                .build()
            val (status, text) = try {
                HadronHttp.client.newCall(request).execute()
                    .use { it.code to it.body.string() }
            } catch (e: IOException) {
                throw OAuthException.RegistrationFailed(e.message ?: "network error")
            }
            if (status != 200 && status != 201) {
                throw OAuthException.RegistrationFailed(errorText(text, status))
            }
            val clientId = try {
                HadronHttp.json.decodeFromString(RegistrationResponse.serializer(), text).client_id
            } catch (e: Exception) {
                throw OAuthException.RegistrationFailed("malformed registration response")
            }
            store.set(clientId, SecureStore.Key.CLIENT_ID)
            clientId
        }
    }

    private fun buildAuthorizeUrl(
        endpoint: String,
        clientId: String,
        pkce: PkcePair,
        state: String,
        provider: LoginProvider,
    ): Uri = Uri.parse(endpoint).buildUpon()
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("client_id", clientId)
        .appendQueryParameter("redirect_uri", config.redirectUri)
        .appendQueryParameter("scope", config.scope)
        // RFC 8707 — REQUIRED here and on the token endpoint.
        .appendQueryParameter("resource", config.resource)
        .appendQueryParameter("code_challenge", pkce.codeChallenge)
        .appendQueryParameter("code_challenge_method", pkce.method)
        .appendQueryParameter("state", state)
        .appendQueryParameter("login_provider", provider.value)
        .build()

    private fun launchBrowser(activity: Activity, url: Uri) {
        // Launched from the Activity (no NEW_TASK) so the Custom Tab lands on
        // top of our task: the redirect Intent then re-enters the singleTask
        // MainActivity and clears the tab off the stack for us.
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(activity, url)
            return
        } catch (e: ActivityNotFoundException) {
            // Fall through: no Custom Tabs-capable browser installed.
        }
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (e: ActivityNotFoundException) {
            throw OAuthException.NoBrowser
        }
    }

    private fun parseCallback(uri: Uri, expectedState: String): String {
        uri.getQueryParameter("error")?.let { error ->
            throw OAuthException.AuthorizationFailed(
                uri.getQueryParameter("error_description") ?: error
            )
        }
        if (uri.getQueryParameter("state") != expectedState) throw OAuthException.StateMismatch
        return uri.getQueryParameter("code") ?: throw OAuthException.MissingCode
    }

    private suspend fun exchangeToken(
        endpoint: String,
        code: String,
        clientId: String,
        verifier: String,
    ): String = withContext(Dispatchers.IO) {
        val form = formUrlEncoded(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to config.redirectUri,
            "client_id" to clientId,
            "code_verifier" to verifier,
            "resource" to config.resource,
        )
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .post(form.toRequestBody(HadronHttp.formMediaType))
            .build()

        val (status, text) = try {
            HadronHttp.client.newCall(request).execute().use { it.code to it.body.string() }
        } catch (e: IOException) {
            throw OAuthException.TokenExchangeFailed(e.message ?: "network error")
        }
        if (status != 200) throw OAuthException.TokenExchangeFailed(errorText(text, status))
        try {
            HadronHttp.json.decodeFromString(TokenResponse.serializer(), text).access_token
        } catch (e: Exception) {
            throw OAuthException.TokenExchangeFailed("malformed token response")
        }
    }

    // MARK: - Helpers

    private fun loadPending(): PendingAuth? {
        val raw = store.get(SecureStore.Key.PENDING_AUTH) ?: return null
        return try {
            HadronHttp.json.decodeFromString(PendingAuth.serializer(), raw)
        } catch (e: Exception) {
            cancelPendingAuthorization()
            null
        }
    }

    private fun formUrlEncoded(vararg params: Pair<String, String>): String =
        params.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }

    private fun errorText(body: String, status: Int): String {
        return try {
            val json = HadronHttp.json.decodeFromString(OAuthErrorResponse.serializer(), body)
            json.error_description ?: json.error ?: "HTTP $status"
        } catch (e: Exception) {
            "HTTP $status"
        }
    }

    // MARK: - Wire types

    @Serializable
    private data class AsMetadata(
        val authorization_endpoint: String,
        val token_endpoint: String,
        val registration_endpoint: String? = null,
    )

    @Serializable
    private data class RegistrationResponse(val client_id: String)

    @Serializable
    private data class TokenResponse(
        val access_token: String,
        val token_type: String? = null,
        val scope: String? = null,
    )

    @Serializable
    private data class OAuthErrorResponse(
        val error: String? = null,
        val error_description: String? = null,
    )

    @Serializable
    private data class PendingAuth(
        val state: String,
        val codeVerifier: String,
        val clientId: String,
        val tokenEndpoint: String,
        val provider: String,
    )
}
