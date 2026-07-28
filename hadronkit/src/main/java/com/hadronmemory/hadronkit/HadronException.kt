package com.hadronmemory.hadronkit

/**
 * Errors surfaced to the UI. [Unauthorized] means the token was rejected and
 * the app should drop back to the signed-out state (there is no refresh in v1).
 */
sealed class HadronException(override val message: String) : Exception(message) {

    data object NotAuthenticated : HadronException("Not signed in.") {
        private fun readResolve(): Any = NotAuthenticated
    }

    data object Unauthorized : HadronException("Your session expired. Please sign in again.") {
        private fun readResolve(): Any = Unauthorized
    }

    /**
     * The server rejected the request as malformed (GraphQL BAD_USER_INPUT),
     * e.g. findNodes' boolean-query parser on unbalanced syntax. Split out from
     * [GraphQL] so callers can retry with sanitized input.
     */
    class BadUserInput(message: String) : HadronException(message)

    class Transport(message: String) : HadronException(message)

    class GraphQL(message: String) : HadronException(message)
}
