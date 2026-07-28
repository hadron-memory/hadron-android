package com.hadronmemory.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hadronmemory.android.AppViewModel
import com.hadronmemory.android.R
import com.hadronmemory.hadronkit.HadronNode

/** One memory: its metadata plus a browse list of its nodes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDetailScreen(
    viewModel: AppViewModel,
    memoryId: String,
    onBack: () -> Unit,
    onOpenNode: (HadronNode) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val memory = state.memories.firstOrNull { it.id == memoryId }
    val uriHandler = LocalUriHandler.current

    var nodes by remember { mutableStateOf<List<HadronNode>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(memoryId) {
        val client = viewModel.client() ?: return@LaunchedEffect
        try {
            nodes = client.memoryNodes(memoryId)
        } catch (e: Exception) {
            loadError = e.message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = memory?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    memory?.let {
                        IconButton(onClick = {
                            uriHandler.openUri(AppViewModel.config.portalUrl(it.urn))
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_open_in_new),
                                contentDescription = stringResource(R.string.open_in_portal),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            memory?.let { m ->
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        m.shortDescription?.takeIf { it.isNotEmpty() }?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyMedium)
                        }
                        m.memoryClass?.let {
                            LabeledValue(stringResource(R.string.label_class), it)
                        }
                        LabeledValue(
                            label = stringResource(R.string.label_urn),
                            value = m.urn,
                            monospace = true,
                        )
                    }
                    HorizontalDivider()
                }
            }

            when {
                loadError != null -> item {
                    LoadErrorRow(loadError.orEmpty())
                }

                nodes == null -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                nodes!!.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.nodes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                else -> {
                    item {
                        Text(
                            text = stringResource(R.string.section_nodes, nodes!!.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(nodes!!, key = { it.id }) { node ->
                        NodeRow(node = node, onClick = { onOpenNode(node) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
