package com.hadronmemory.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import com.hadronmemory.android.AppViewModel
import com.hadronmemory.android.R
import com.hadronmemory.hadronkit.HadronNodeDetail

/**
 * Full detail of one node: metadata, description, and the markdown body,
 * fetched screen-locally via `HadronClient.node(ref:)`. That one call returns
 * everything rendered here, so the route only has to carry the node id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailScreen(
    viewModel: AppViewModel,
    nodeId: String,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var detail by remember { mutableStateOf<HadronNodeDetail?>(null) }
    var transportError by remember { mutableStateOf<String?>(null) }
    // The server returns null for both "gone" and "you can't see it" — this
    // surface deliberately doesn't distinguish them, and neither do we.
    var isMissing by remember { mutableStateOf(false) }

    LaunchedEffect(nodeId) {
        val client = viewModel.client() ?: return@LaunchedEffect
        try {
            val result = client.node(nodeId)
            if (result == null) isMissing = true else detail = result
        } catch (e: Exception) {
            transportError = e.message
        }
    }

    val errorText = when {
        isMissing -> stringResource(R.string.node_unavailable)
        else -> transportError
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail?.name.orEmpty(),
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
                    detail?.nodeUrn?.let { urn ->
                        IconButton(onClick = {
                            uriHandler.openUri(AppViewModel.config.portalUrl(urn))
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
        when {
            errorText != null -> LoadErrorRow(
                message = errorText,
                modifier = Modifier.padding(padding),
            )

            detail == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            else -> NodeDetailBody(
                detail = detail!!,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun NodeDetailBody(detail: HadronNodeDetail, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        detail.memory?.name?.let {
            LabeledValue(stringResource(R.string.label_memory), it)
        }
        LabeledValue(stringResource(R.string.label_type), detail.nodeType)
        LabeledValue(stringResource(R.string.label_loc), detail.loc)
        detail.nodeUrn?.let {
            LabeledValue(stringResource(R.string.label_urn), it, monospace = true)
        }
        detail.tags?.takeIf { it.isNotEmpty() }?.let {
            LabeledValue(stringResource(R.string.label_tags), it.joinToString(", "))
        }

        detail.description?.takeIf { it.isNotEmpty() }?.let {
            Section(stringResource(R.string.section_description)) {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        val content = detail.content?.takeIf { it.isNotEmpty() }
        if (content != null) {
            Section(stringResource(R.string.section_content)) {
                SelectionContainer { MarkdownText(content) }
            }
        } else {
            // No body — fall back to the retrieval abstract (spec 032's
            // abstract-fallback semantics, applied client-side).
            detail.abstract?.takeIf { it.isNotEmpty() }?.let {
                Section(stringResource(R.string.section_abstract)) {
                    SelectionContainer { MarkdownText(it) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    HorizontalDivider(modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
    content()
}
