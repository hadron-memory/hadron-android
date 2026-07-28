package com.hadronmemory.android

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hadronmemory.hadronkit.HadronClient
import com.hadronmemory.hadronkit.HadronClientConfig
import com.hadronmemory.hadronkit.HadronException
import com.hadronmemory.hadronkit.HadronNode
import com.hadronmemory.hadronkit.MeUser
import com.hadronmemory.hadronkit.Memory
import com.hadronmemory.hadronkit.OAuthService
import com.hadronmemory.hadronkit.SecureStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthState {
    SignedOut,
    SigningIn,

    /**
     * The magic-link leg: the browser handed off to the user's mail app and
     * the redirect will arrive from wherever they tap the link, so there is
     * nothing to time out on — only a Cancel button.
     */
    AwaitingEmailLink,
    SignedIn,
}

data class AppUiState(
    val authState: AuthState = AuthState.SignedOut,
    val me: MeUser? = null,
    val memories: List<Memory> = emptyList(),
    /** Ids of memories the user reaches via a MemoryShare grant (badged in the list). */
    val sharedMemoryIds: Set<String> = emptySet(),
    val searchResults: List<HadronNode> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
) {
    /** True when the current query is long enough to be an active search. */
    val isSearchActive: Boolean get() = searchQuery.trim().length >= MIN_SEARCH_LENGTH
}

private const val MIN_SEARCH_LENGTH = 2
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Owns the auth lifecycle and the loaded data (memories, search results).
 * Ported from hadron-ios' `AppState`; deliberately per-app, since the state
 * drifts with each UI.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SecureStore(application, config.storeName)
    private val oauth = OAuthService(config, store)

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Set for as long as a sign-in is out at the browser. A resume with this
     * still set — and no redirect having arrived in between — is how the user
     * backing out of the Custom Tab reaches us; the browser has no result
     * callback to report it.
     *
     * These three are written synchronously in [signIn], before anything can
     * suspend. Setting them from the coroutine after the launch would race the
     * Activity's own `onPause`/`onResume`, and losing that race strands the UI
     * on a spinner with no way back.
     */
    private var awaitingBrowser = false
    private var pausedSinceHandoff = false
    private var browserProvider: OAuthService.LoginProvider? = null

    init {
        when {
            store.get(SecureStore.Key.ACCESS_TOKEN) != null -> {
                _state.update { it.copy(authState = AuthState.SignedIn) }
                refresh()
            }
            // A pending magic-link sign-in outlives the process on purpose: the
            // user is off in their mail app and the link may land minutes from
            // now. Any other provider's leg happens entirely inside the Custom
            // Tab, so a leftover record just means it was abandoned.
            oauth.pendingProvider() == OAuthService.LoginProvider.EMAIL ->
                _state.update { it.copy(authState = AuthState.AwaitingEmailLink) }

            else -> oauth.cancelPendingAuthorization()
        }
    }

    /** Null when signed out. Detail screens fetch through this directly. */
    fun client(): HadronClient? =
        store.get(SecureStore.Key.ACCESS_TOKEN)?.let { HadronClient(config, it) }

    // MARK: - Auth

    fun signIn(activity: Activity, provider: OAuthService.LoginProvider) {
        if (_state.value.authState == AuthState.SigningIn) return
        _state.update { it.copy(authState = AuthState.SigningIn, errorMessage = null) }
        awaitingBrowser = true
        pausedSinceHandoff = false
        browserProvider = provider
        viewModelScope.launch {
            try {
                oauth.beginAuthorization(activity, provider)
                if (provider == OAuthService.LoginProvider.EMAIL) {
                    _state.update { it.copy(authState = AuthState.AwaitingEmailLink) }
                }
            } catch (e: Exception) {
                awaitingBrowser = false
                browserProvider = null
                _state.update {
                    it.copy(authState = AuthState.SignedOut, errorMessage = e.message)
                }
            }
        }
    }

    /**
     * Handle a custom-scheme URL the OS delivered (`onCreate` / `onNewIntent`)
     * — the Android counterpart of hadron-ios' `.onOpenURL`.
     */
    fun onRedirect(uri: Uri) {
        if (!oauth.isCallback(uri)) return
        // Replay guards: the same Intent is re-delivered on an Activity
        // recreate, and the authorization behind it is single-use.
        if (_state.value.authState == AuthState.SignedIn) return
        if (!oauth.hasPendingAuthorization()) return

        awaitingBrowser = false
        browserProvider = null
        _state.update { it.copy(authState = AuthState.SigningIn, errorMessage = null) }
        viewModelScope.launch {
            try {
                val token = oauth.completeAuthorization(uri)
                store.set(token, SecureStore.Key.ACCESS_TOKEN)
                _state.update { it.copy(authState = AuthState.SignedIn) }
                loadAll()
            } catch (e: Exception) {
                _state.update {
                    it.copy(authState = AuthState.SignedOut, errorMessage = e.message)
                }
            }
        }
    }

    fun onHostPaused() {
        pausedSinceHandoff = true
    }

    fun onHostResumed() {
        if (!awaitingBrowser || !pausedSinceHandoff) return
        // The magic-link leg legitimately leaves the app — the user is off in
        // their mail client — so coming back without a redirect proves nothing
        // there. Only its explicit Cancel button ends that one.
        if (browserProvider == OAuthService.LoginProvider.EMAIL) {
            pausedSinceHandoff = false
            return
        }
        // Silent, like hadron-ios treats ASWebAuthenticationSession's
        // canceledLogin: the user dismissed it, they know why.
        cancelSignIn()
    }

    fun cancelSignIn() {
        awaitingBrowser = false
        pausedSinceHandoff = false
        browserProvider = null
        oauth.cancelPendingAuthorization()
        _state.update { it.copy(authState = AuthState.SignedOut) }
    }

    /**
     * Deletes the access token but never the cached DCR client id — losing it
     * would re-register on the next sign-in and burn the 10/min rate limit.
     */
    fun signOut() {
        store.delete(SecureStore.Key.ACCESS_TOKEN)
        oauth.cancelPendingAuthorization()
        searchJob?.cancel()
        _state.value = AppUiState(authState = AuthState.SignedOut)
    }

    // MARK: - Data loading

    suspend fun loadAll() {
        val client = client() ?: return
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            coroutineScope {
                val meResult = async { client.me() }
                val ownResult = async { client.myMemories() }
                val sharedResult = async { client.sharedMemories() }

                val me = meResult.await()
                // Merge the default surface with shared-with-me grants (the
                // server keeps those behind an explicit filter); dedupe by id.
                val own = ownResult.await()
                val shared = sharedResult.await()
                val ownIds = own.map { it.id }.toSet()
                val merged = (own + shared.filterNot { it.id in ownIds })
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                _state.update {
                    it.copy(
                        me = me,
                        memories = merged,
                        sharedMemoryIds = shared.map { m -> m.id }.toSet() - ownIds,
                    )
                }
            }
        } catch (e: Exception) {
            handle(e)
        } finally {
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadAll() }
    }

    // MARK: - Search (debounced)

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_SEARCH_LENGTH) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        _state.update { it.copy(isSearching = true) }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val client = client() ?: return@launch
            try {
                val results = client.findNodes(trimmed)
                _state.update { it.copy(searchResults = results, isSearching = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isSearching = false) }
                handle(e)
            }
        }
    }

    // MARK: - Errors

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun handle(error: Throwable) {
        if (error is HadronException.Unauthorized) {
            // signOut() resets the whole state, so set the message afterwards.
            signOut()
            _state.update { it.copy(errorMessage = HadronException.Unauthorized.message) }
        } else {
            _state.update { it.copy(errorMessage = error.message) }
        }
    }

    companion object {
        /**
         * This app's identity against the Hadron platform. The redirect scheme
         * and client name are distinct from the iOS and macOS apps', so DCR
         * registers (and the secure store caches) a separate client id per app.
         */
        val config = HadronClientConfig(
            callbackScheme = "com.hadronmemory.android",
            redirectUri = "com.hadronmemory.android://oauth-callback",
            clientName = "Hadron for Android",
            storeName = "com.hadronmemory.android",
        )
    }
}
