package com.hadronmemory.hadronkit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A Hadron memory as returned by `memories` (the uniform #473 read surface). */
@Serializable
data class Memory(
    val id: String,
    val urn: String,
    val name: String,
    val shortDescription: String? = null,
    /** Memory class: system | app | knowledge | personal | group | private. */
    @SerialName("class") val memoryClass: String? = null,
)

/** A graph node as returned by `findNodes` hits. */
@Serializable
data class HadronNode(
    val id: String,
    val loc: String,
    val name: String,
    val description: String? = null,
    val nodeType: String,
    /**
     * Fully-qualified node URN, composed server-side from the node's memory
     * URN + loc (#481) and carried by every Node-returning surface. We consume
     * it as-is rather than re-deriving it so the app stays agnostic to the URN
     * grammar (the server owns composition; cf. hadron-server#691).
     */
    @SerialName("urn") val nodeUrn: String? = null,
    val memory: NodeMemory? = null,
) {
    @Serializable
    data class NodeMemory(val urn: String, val name: String)
}

/**
 * Full node detail as returned by `node(ref:)` — everything [HadronNode]
 * carries plus the long-form fields the detail screen renders.
 */
@Serializable
data class HadronNodeDetail(
    val id: String,
    val loc: String,
    val name: String,
    val description: String? = null,
    /** Retrieval-surface summary (spec 032); may be stale relative to content. */
    val abstract: String? = null,
    /** Markdown body. Null/empty for structural nodes. */
    val content: String? = null,
    val nodeType: String,
    val tags: List<String>? = null,
    val updatedAt: String? = null,
    @SerialName("urn") val nodeUrn: String? = null,
    val memory: HadronNode.NodeMemory? = null,
)

/** The authenticated user (`me`). */
@Serializable
data class MeUser(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val handle: String? = null,
    /** Provider-sourced profile image (GitHub/Google both populate it). */
    val avatarUrl: String? = null,
) {
    /**
     * Handle last: auto-provisioned accounts get an opaque hex handle, which
     * reads like a bug when it leads the account menu.
     */
    val displayName: String get() = name ?: email ?: handle ?: "Signed in"
}

// MARK: - GraphQL envelope

@Serializable
internal data class GraphQLResponse<T>(
    val data: T? = null,
    val errors: List<GraphQLError>? = null,
)

@Serializable
internal data class GraphQLError(
    val message: String,
    /**
     * Apollo-style error metadata; `code` carries machine-readable kinds like
     * BAD_USER_INPUT (used to retry sanitized searches).
     */
    val extensions: Extensions? = null,
) {
    @Serializable
    data class Extensions(val code: String? = null)
}

/**
 * Uniform { items, total } page envelope shared by every find-many query on
 * the #473 read surface (memories, apps, organizations, users, …).
 */
@Serializable
internal data class Page<T>(val items: List<T>, val total: Int)

/**
 * findNodes envelope: a scored-hit list. The app only needs the nodes, so
 * [nodes] flattens `hits[].node` back to the list the callers use.
 */
@Serializable
internal data class FindNodesResult(val hits: List<Hit>) {
    @Serializable
    data class Hit(val node: HadronNode)

    val nodes: List<HadronNode> get() = hits.map { it.node }
}
