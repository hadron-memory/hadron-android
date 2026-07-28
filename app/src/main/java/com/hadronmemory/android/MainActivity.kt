package com.hadronmemory.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.hadronmemory.android.ui.HadronApp
import com.hadronmemory.android.ui.theme.HadronTheme

/**
 * The single Activity. Declared `singleTask` in the manifest so the OAuth
 * redirect re-enters this instance — which also clears the Custom Tab off the
 * task stack, the way `ASWebAuthenticationSession` dismisses its sheet on iOS.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HadronTheme {
                HadronApp(viewModel)
            }
        }
        consumeRedirect(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeRedirect(intent)
    }

    override fun onPause() {
        super.onPause()
        viewModel.onHostPaused()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onHostResumed()
    }

    /**
     * Take the redirect URI off the Intent as we hand it over: the Activity
     * keeps the same Intent across a recreate, and an authorization code is
     * single-use.
     */
    private fun consumeRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        intent.data = null
        viewModel.onRedirect(uri)
    }
}
