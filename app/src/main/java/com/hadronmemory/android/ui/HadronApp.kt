package com.hadronmemory.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.hadronmemory.android.AppViewModel
import com.hadronmemory.android.AuthState
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class MemoryDetailRoute(val memoryId: String)

/**
 * Node detail routes by id alone — `node(ref:)` returns everything the screen
 * renders, so the route survives process death without carrying a payload.
 */
@Serializable
data class NodeDetailRoute(val nodeId: String)

@Serializable
object AboutRoute

/** Switches between the signed-out and signed-in worlds. */
@Composable
fun HadronApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (state.authState) {
        AuthState.SignedIn -> SignedInNavHost(viewModel)
        else -> SignInScreen(
            state = state,
            onSignIn = viewModel::signIn,
            onCancel = viewModel::cancelSignIn,
        )
    }
}

@Composable
private fun SignedInNavHost(viewModel: AppViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                viewModel = viewModel,
                onOpenMemory = { navController.navigate(MemoryDetailRoute(it.id)) },
                onOpenNode = { navController.navigate(NodeDetailRoute(it.id)) },
                onOpenAbout = { navController.navigate(AboutRoute) },
            )
        }
        composable<MemoryDetailRoute> { entry ->
            MemoryDetailScreen(
                viewModel = viewModel,
                memoryId = entry.toRoute<MemoryDetailRoute>().memoryId,
                onBack = navController::popBackStack,
                onOpenNode = { navController.navigate(NodeDetailRoute(it.id)) },
            )
        }
        composable<NodeDetailRoute> { entry ->
            NodeDetailScreen(
                viewModel = viewModel,
                nodeId = entry.toRoute<NodeDetailRoute>().nodeId,
                onBack = navController::popBackStack,
            )
        }
        composable<AboutRoute> {
            AboutScreen(onBack = navController::popBackStack)
        }
    }
}
