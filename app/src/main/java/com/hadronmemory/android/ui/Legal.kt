package com.hadronmemory.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.hadronmemory.android.R

/** Legal documents, reachable in-app as store review expects. */
object LegalUrls {
    const val TERMS = "https://docs.hadronmemory.com/legal/terms/"
    const val PRIVACY = "https://docs.hadronmemory.com/legal/privacy/"
    const val DOCS = "https://docs.hadronmemory.com"
}

/**
 * The agree-to-terms line, shown before sign-in because signing in can create
 * the account. The two link labels are separate string resources and are
 * located inside the sentence, so a translation stays linkable as long as it
 * uses the same wording for both.
 */
@Composable
fun LegalFootnote(modifier: Modifier = Modifier) {
    val sentence = stringResource(R.string.sign_in_legal)
    val termsLabel = stringResource(R.string.sign_in_legal_terms)
    val privacyLabel = stringResource(R.string.sign_in_legal_privacy)

    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    )

    val text = buildAnnotatedString {
        val links = listOf(termsLabel to LegalUrls.TERMS, privacyLabel to LegalUrls.PRIVACY)
            .mapNotNull { (label, url) ->
                val start = sentence.indexOf(label)
                if (start < 0) null else Triple(start, label, url)
            }
            .sortedBy { it.first }

        var cursor = 0
        for ((start, label, url) in links) {
            if (start < cursor) continue
            append(sentence.substring(cursor, start))
            withLink(LinkAnnotation.Url(url, linkStyles)) { append(label) }
            cursor = start + label.length
        }
        append(sentence.substring(cursor))
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
