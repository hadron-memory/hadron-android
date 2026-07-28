package com.hadronmemory.android.ui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hadronmemory.android.AppUiState
import com.hadronmemory.android.AuthState
import com.hadronmemory.android.R
import com.hadronmemory.hadronkit.OAuthService.LoginProvider

/**
 * The signed-out screen: app mark, one-line pitch, and one button per identity
 * provider. The explanatory line and the legal footnote matter for store
 * review optics — the auth wall shouldn't look arbitrary, and signing in can
 * create the account.
 */
@Composable
fun SignInScreen(
    state: AppUiState,
    onSignIn: (Activity, LoginProvider) -> Unit,
    onCancel: () -> Unit,
) {
    val activity = LocalActivity.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The round mark ships with its own circular alpha edge — no clip.
            Image(
                painter = painterResource(R.drawable.hadron_mark),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            when (state.authState) {
                AuthState.SigningIn -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.signing_in),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AuthState.AwaitingEmailLink -> AwaitingEmailLink(onCancel = onCancel)

                else -> if (activity != null) {
                    ProviderButtons { provider -> onSignIn(activity, provider) }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.sign_in_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            LegalFootnote()

            state.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * One button per identity provider. The server's authorize page routes its
 * unauthenticated leg by `login_provider`, and omitting it lands everyone on
 * GitHub — so the choice has to happen here, before the browser opens.
 */
@Composable
private fun ProviderButtons(onSignIn: (LoginProvider) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProviderButton(
            label = stringResource(R.string.sign_in_apple),
            iconRes = R.drawable.ic_provider_apple,
        ) { onSignIn(LoginProvider.APPLE) }

        ProviderButton(
            label = stringResource(R.string.sign_in_github),
            iconRes = R.drawable.ic_provider_github,
        ) { onSignIn(LoginProvider.GITHUB) }

        ProviderButton(
            label = stringResource(R.string.sign_in_google),
            iconRes = R.drawable.ic_provider_google,
        ) { onSignIn(LoginProvider.GOOGLE) }

        ProviderButton(
            label = stringResource(R.string.sign_in_email),
            iconRes = R.drawable.ic_provider_email,
        ) { onSignIn(LoginProvider.EMAIL) }
    }
}

@Composable
private fun ProviderButton(label: String, iconRes: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The provider glyphs are single-colour masks (the same ones
            // hadron-ios ships), so tinting them to the button's content
            // colour keeps them legible in both light and dark.
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(LocalContentColor.current),
                modifier = Modifier.size(18.dp),
            )
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun AwaitingEmailLink(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.awaiting_email_link_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.awaiting_email_link_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel))
        }
    }
}
