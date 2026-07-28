package com.hadronmemory.hadronkit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Thin GraphQL client for the Hadron API. Stateless apart from the config and
 * bearer token it is constructed with.
 */
class HadronClient(
    val config: HadronClientConfig,
    private val token: String,
) {

    // MARK: Typed operations

    suspend fun me(): MeUser? {
        val query = "query { me { id name email handle avatarUrl } }"
        return execute(query, MePayload.serializer()).me
    }

    suspend fun myMemories(): List<Memory> = memoriesList(sharedWithMe = false)

    /**
     * Memories another user granted to the caller (MemoryShare). NOT part of
     * the default `memories()` surface — the server keeps grants behind the
     * explicit `sharedWithMe` filter, so a complete "everything I can read"
     * list must merge both calls.
     */
    suspend fun sharedMemories(): List<Memory> = memoriesList(sharedWithMe = true)

    private suspend fun memoriesList(sharedWithMe: Boolean): List<Memory> {
        // Uniform read surface (hadron-server #473): memories() replaces
        // myMemories and returns an { items, total } page (limit cap 200).
        // The old query returned the complete list, so offset-page until the
        // envelope's total is reached — the apps show all memories.
        val query = """
            query(${'$'}filter: MemoryFilter, ${'$'}limit: Int, ${'$'}offset: Int) {
              memories(filter: ${'$'}filter, limit: ${'$'}limit, offset: ${'$'}offset) {
                total
                items { id urn name shortDescription class }
              }
            }
        """.trimIndent()
        return fetchAllPages { limit, offset ->
            val variables = buildJsonObject {
                put("limit", limit)
                put("offset", offset)
                // Omitted (not null/false) when unset — the default surface.
                if (sharedWithMe) {
                    put("filter", buildJsonObject { put("sharedWithMe", true) })
                }
            }
            execute(query, MemoriesPayload.serializer(), variables).memories
        }
    }

    /**
     * Browse a single memory's nodes (no search query — a deterministic
     * filtered list, sorted by loc so the tree order is stable).
     */
    suspend fun memoryNodes(memoryId: String, limit: Int = 200): List<HadronNode> {
        val query = """
            query(${'$'}filter: NodeFilter, ${'$'}limit: Int) {
              findNodes(filter: ${'$'}filter, sort: loc, limit: ${'$'}limit) {
                hits { node { $NODE_FIELDS } }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("filter", buildJsonObject {
                put("memoryIds", buildJsonArray { add(memoryId) })
            })
            put("limit", limit)
        }
        return execute(query, FindNodesPayload.serializer(), variables).findNodes.nodes
    }

    /**
     * Fetch one node's full detail. [ref] accepts a node PK or a
     * fully-qualified URN; denied and missing both come back null (the server
     * deliberately doesn't distinguish them on this surface).
     */
    suspend fun node(ref: String): HadronNodeDetail? {
        val query = """
            query(${'$'}ref: ID!) {
              node(ref: ${'$'}ref) {
                id loc name description abstract content nodeType tags updatedAt urn
                memory { urn name }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("ref", ref) }
        return execute(query, NodePayload.serializer(), variables).node
    }

    suspend fun findNodes(search: String, limit: Int = 50): List<HadronNode> {
        // The search box is free-form text, but findNodes' keyword mode parses
        // a boolean query grammar — send a literal-safe form so no user input
        // is rejected as malformed syntax.
        val safe = literalSafeQuery(search)
        return try {
            keywordSearch(safe, limit)
        } catch (e: HadronException.BadUserInput) {
            // Defensive backstop: if the sanitizer missed a parser edge case,
            // retry once with the whole input as a quoted phrase (the
            // grammar's literal form) so user input never surfaces as an
            // error dialog.
            val quoted = quotedPhrase(search)
            if (quoted == safe) throw e
            keywordSearch(quoted, limit)
        }
    }

    private suspend fun keywordSearch(search: String, limit: Int): List<HadronNode> {
        // mode:keyword keeps the old substring-search intent (now stemmed,
        // relevance-ranked Postgres FTS) without pulling in the vector index.
        val query = """
            query(${'$'}query: String, ${'$'}limit: Int) {
              findNodes(query: ${'$'}query, mode: keyword, limit: ${'$'}limit) {
                hits { node { $NODE_FIELDS } }
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("query", search)
            put("limit", limit)
        }
        return execute(query, FindNodesPayload.serializer(), variables).findNodes.nodes
    }

    // MARK: Paging

    /**
     * Accumulate offset pages from a #473 find-many query until the envelope's
     * `total` is reached (or a short/empty page says the server has no more).
     * [maxPages] is a defensive cap on round-trips so a misbehaving `total` can
     * never loop forever: 25 × 200 = 5,000 items, far beyond what the apps can
     * usefully show.
     */
    private suspend fun <T> fetchAllPages(
        pageSize: Int = 200,
        maxPages: Int = 25,
        fetch: suspend (limit: Int, offset: Int) -> Page<T>,
    ): List<T> {
        val items = mutableListOf<T>()
        repeat(maxPages) {
            val page = fetch(pageSize, items.size)
            items += page.items
            if (page.items.size < pageSize || items.size >= page.total) return items
        }
        return items
    }

    // MARK: Transport

    private suspend fun <T> execute(
        query: String,
        serializer: KSerializer<T>,
        variables: JsonObject? = null,
    ): T = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("query", query)
            // Optional GraphQL variables must be OMITTED, not null — an
            // ID-typed optional sent as null is a type error server-side. The
            // JsonObject builders make that structural: a variable that isn't
            // put simply isn't in the map.
            if (variables != null && variables.isNotEmpty()) put("variables", variables)
        }

        val request = Request.Builder()
            .url(config.graphQLUrl)
            .header("Authorization", "Bearer $token")
            .post(
                HadronHttp.json.encodeToString(JsonObject.serializer(), body)
                    .toRequestBody(HadronHttp.jsonMediaType)
            )
            .build()

        val (status, text) = try {
            HadronHttp.client.newCall(request).execute().use { response ->
                response.code to response.body.string()
            }
        } catch (e: IOException) {
            throw HadronException.Transport(e.message ?: "Couldn't reach the Hadron server.")
        }

        if (status == 401 || status == 403) throw HadronException.Unauthorized
        if (status !in 200..299) throw HadronException.Transport("HTTP $status")

        val decoded = try {
            HadronHttp.json.decodeFromString(GraphQLResponse.serializer(serializer), text)
        } catch (e: Exception) {
            throw HadronException.GraphQL("Couldn't parse the server response.")
        }

        val errors = decoded.errors
        if (!errors.isNullOrEmpty()) {
            val message = errors.joinToString("; ") { it.message }
            val lowered = message.lowercase()
            if (lowered.contains("unauth") || lowered.contains("forbidden")) {
                throw HadronException.Unauthorized
            }
            if (errors.any { it.extensions?.code == "BAD_USER_INPUT" }) {
                throw HadronException.BadUserInput(message)
            }
            throw HadronException.GraphQL(message)
        }

        decoded.data ?: throw HadronException.GraphQL("The server returned no data.")
    }

    // MARK: Payload envelopes

    @Serializable
    private data class MePayload(val me: MeUser? = null)

    @Serializable
    private data class MemoriesPayload(val memories: Page<Memory>)

    @Serializable
    private data class NodePayload(val node: HadronNodeDetail? = null)

    @Serializable
    private data class FindNodesPayload(val findNodes: FindNodesResult)

    companion object {
        private const val NODE_FIELDS = "id loc name description nodeType urn memory { urn name }"

        /**
         * findNodes' keyword mode parses a boolean grammar (hadron-server
         * src/lib/retrieval/tsquery.ts): parentheses group, double quotes
         * delimit phrases, bare AND / OR / NOT words are operators (matched
         * case-insensitively), and a leading `-` negates. Malformed input — an
         * unmatched parenthesis or quote, a dangling operator — is rejected
         * with BAD_USER_INPUT. The old `nodes(search:)` did literal substring
         * matching, so when free-form input contains any of that syntax, send
         * the whole thing as one quoted phrase instead — the parser's literal
         * form, closest to the old semantics. Plain input passes through
         * unchanged (implicit AND of stemmed terms).
         */
        fun literalSafeQuery(raw: String): String =
            if (hasBooleanSyntax(raw)) quotedPhrase(raw) else raw

        private fun hasBooleanSyntax(s: String): Boolean {
            if (s.contains('(') || s.contains(')') || s.contains('"')) return true
            // The server tokenizer splits bare runs on whitespace (parens and
            // quotes are handled above) and uppercases words before operator
            // matching.
            return s.split(Regex("\\s+")).any { word ->
                when (word.uppercase()) {
                    "AND", "OR", "NOT" -> true
                    else -> word.length > 1 && word.first() == '-'
                }
            }
        }

        /**
         * Wrap the input in double quotes, dropping embedded quotes (replaced
         * with spaces — the grammar has no escape sequence for them).
         */
        private fun quotedPhrase(raw: String): String = "\"" + raw.replace('"', ' ') + "\""
    }
}
