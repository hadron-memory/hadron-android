package com.hadronmemory.hadronkit

import android.net.Uri

/**
 * Per-app configuration for talking to the Hadron platform. Each client app
 * constructs one with its own redirect scheme, client name, and secure-store
 * name; the endpoints and OAuth contract are shared with hadron-ios /
 * hadron-macapp.
 *
 * Endpoints and the OAuth contract are documented in the platform specs
 * (`cor:api:010:01` for GraphQL, `cor:aut:030:01` for the OAuth 2.1 flow).
 */
data class HadronClientConfig(
    /** The Hadron server that hosts the GraphQL + MCP transports and OAuth AS. */
    val baseUrl: String = "https://srv.hadronmemory.com",

    /**
     * Custom-scheme redirect. The app registers an intent-filter for this
     * scheme so the browser hands the final redirect back to us.
     *
     * The server's DCR validator requires scheme + non-empty authority with no
     * fragment — use the double-slash form (`scheme://host`), never the
     * Apple-style single-slash form.
     */
    val callbackScheme: String,
    val redirectUri: String,

    /** Human-readable client name recorded at dynamic registration. */
    val clientName: String,

    /** SharedPreferences file name the app's [SecureStore] is keyed on. */
    val storeName: String,

    /** The web portal — used for "Open in portal" deep links. */
    val portalBaseUrl: String = "https://hadronmemory.com",
) {
    /** GraphQL endpoint — the complete API surface. */
    val graphQLUrl: String get() = "$baseUrl/graphql"

    /**
     * RFC 8707 resource indicator. REQUIRED on `/oauth/authorize` and
     * `/oauth/token`; it is the MCP resource the issued key is scoped to.
     */
    val resource: String get() = "$baseUrl/mcp"

    /** Only scope the AS advertises / accepts. */
    val scope: String get() = "mcp"

    val discoveryUrl: String get() = "$baseUrl/.well-known/oauth-authorization-server"

    /** Used when discovery omits `registration_endpoint`. */
    val registrationFallbackUrl: String get() = "$baseUrl/oauth/register"

    /**
     * Build the shareable portal URL for a fully-qualified entity URN,
     * e.g. `https://hadronmemory.com/app/u/hrn:node:org::memory::loc`.
     *
     * The URN is encoded as a single path segment: "/" must be escaped so a
     * URN that ever contains one doesn't split into extra segments, while the
     * URN grammar's own delimiters (":" above all) are left legible.
     */
    fun portalUrl(forUrn: String): String =
        "$portalBaseUrl/app/u/" + Uri.encode(forUrn, PATH_SEGMENT_SAFE)

    /** The portal's signed-in root. */
    val portalAppUrl: String get() = "$portalBaseUrl/app"

    /** Account management, including the store-required deletion affordance. */
    val portalAccountUrl: String get() = "$portalBaseUrl/app/account"

    private companion object {
        /**
         * Sub-delims plus ":" and "@" — RFC 3986's `pchar` set minus "/",
         * matching what hadron-ios keeps unescaped.
         */
        const val PATH_SEGMENT_SAFE = "!$&'()*+,;=:@"
    }
}
