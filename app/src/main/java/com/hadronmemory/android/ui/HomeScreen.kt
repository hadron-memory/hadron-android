package com.hadronmemory.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.hadronmemory.android.AppViewModel
import com.hadronmemory.android.R
import com.hadronmemory.hadronkit.HadronNode
import com.hadronmemory.hadronkit.Memory

/**
 * The signed-in root: a searchable list. Empty query shows the user's
 * memories; an active query (≥ 2 chars, debounced in the view model) shows
 * node search results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpenMemory: (Memory) -> Unit,
    onOpenNode: (HadronNode) -> Unit,
    onOpenAbout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    ErrorSnackbar(state.errorMessage, snackbarHostState, viewModel::dismissError)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        AccountMenu(
                            avatarUrl = state.me?.avatarUrl,
                            displayName = state.me?.displayName,
                            onRefresh = viewModel::refresh,
                            onAbout = onOpenAbout,
                            onSignOut = { showSignOutConfirmation = true },
                        )
                    },
                )
                SearchField(
                    query = state.searchQuery,
                    onQueryChange = viewModel::search,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            // Only a *re*fresh gets the pull indicator; the first load already
            // owns the screen with a centred spinner.
            isRefreshing = state.isLoading && state.memories.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isSearchActive -> SearchResults(
                    isSearching = state.isSearching,
                    results = state.searchResults,
                    query = state.searchQuery,
                    onOpenNode = onOpenNode,
                )

                else -> MemoriesList(
                    memories = state.memories,
                    sharedMemoryIds = state.sharedMemoryIds,
                    isLoading = state.isLoading,
                    onOpenMemory = onOpenMemory,
                )
            }
        }
    }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text(stringResource(R.string.sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.sign_out_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirmation = false
                    viewModel.signOut()
                }) {
                    Text(
                        text = stringResource(R.string.sign_out),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

// MARK: - Search

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SearchResults(
    isSearching: Boolean,
    results: List<HadronNode>,
    query: String,
    onOpenNode: (HadronNode) -> Unit,
) {
    when {
        isSearching -> CenteredProgress(stringResource(R.string.searching))

        results.isEmpty() -> EmptyState(
            title = stringResource(R.string.no_results_title),
            body = stringResource(R.string.no_results_body, query),
        )

        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results, key = { it.id }) { node ->
                NodeRow(node = node, onClick = { onOpenNode(node) })
                HorizontalDivider()
            }
        }
    }
}

// MARK: - Memories

@Composable
private fun MemoriesList(
    memories: List<Memory>,
    sharedMemoryIds: Set<String>,
    isLoading: Boolean,
    onOpenMemory: (Memory) -> Unit,
) {
    when {
        memories.isEmpty() && isLoading -> CenteredProgress(stringResource(R.string.loading))

        // A fresh account lands here (sign-in auto-creates one) — make the
        // empty state a starting point, not a dead end.
        memories.isEmpty() -> EmptyState(
            title = stringResource(R.string.no_memories_title),
            body = stringResource(R.string.no_memories_body),
            action = {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Button(onClick = { uriHandler.openUri(AppViewModel.config.portalAppUrl) }) {
                    Text(stringResource(R.string.open_the_portal))
                }
            },
        )

        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = stringResource(R.string.memories_header, memories.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(memories, key = { it.id }) { memory ->
                MemoryRow(
                    memory = memory,
                    isShared = memory.id in sharedMemoryIds,
                    onClick = { onOpenMemory(memory) },
                )
                HorizontalDivider()
            }
        }
    }
}

// MARK: - Account menu

@Composable
private fun AccountMenu(
    avatarUrl: String?,
    displayName: String?,
    onRefresh: () -> Unit,
    onAbout: () -> Unit,
    onSignOut: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    IconButton(onClick = { expanded = true }) {
        Avatar(avatarUrl)
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        displayName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.refresh)) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = { expanded = false; onRefresh() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open_portal)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null)
                },
                onClick = {
                    expanded = false
                    uriHandler.openUri(AppViewModel.config.portalAppUrl)
                },
            )
            // Account management — including the store-required deletion
            // affordance — lives in the portal.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_account)) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                onClick = {
                    expanded = false
                    uriHandler.openUri(AppViewModel.config.portalAccountUrl)
                },
            )
            HorizontalDivider()
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.about)) },
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
            onClick = { expanded = false; onAbout() },
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.sign_out),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = { expanded = false; onSignOut() },
        )
    }
}

/** Provider avatar when the account has one, a glyph otherwise. */
@Composable
private fun Avatar(avatarUrl: String?) {
    val placeholder: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = stringResource(R.string.account_menu),
            modifier = Modifier.size(28.dp),
        )
    }
    if (avatarUrl.isNullOrEmpty()) {
        placeholder()
        return
    }
    SubcomposeAsyncImage(
        model = avatarUrl,
        contentDescription = stringResource(R.string.account_menu),
        contentScale = ContentScale.Crop,
        loading = { placeholder() },
        error = { placeholder() },
        modifier = Modifier
            .size(28.dp)
            .clip(androidx.compose.foundation.shape.CircleShape),
    )
}

// MARK: - Shared bits

@Composable
private fun CenteredProgress(label: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.hadron_mark),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .alpha(0.4f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            action?.let {
                Box(modifier = Modifier.padding(top = 20.dp)) { it() }
            }
        }
    }
}

@Composable
fun ErrorSnackbar(
    message: String?,
    hostState: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(message) {
        if (message != null) {
            hostState.showSnackbar(message)
            onDismiss()
        }
    }
}
