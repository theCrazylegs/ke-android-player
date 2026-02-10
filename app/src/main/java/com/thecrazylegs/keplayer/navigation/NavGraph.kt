package com.thecrazylegs.keplayer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.thecrazylegs.keplayer.data.storage.TokenStorage
import com.thecrazylegs.keplayer.ui.login.LoginScreen
import com.thecrazylegs.keplayer.ui.login.LoginViewModel
import com.thecrazylegs.keplayer.ui.player.PlayerScreen
import com.thecrazylegs.keplayer.ui.player.PlayerViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Player : Screen("player")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    tokenStorage: TokenStorage
) {
    val startDestination = if (tokenStorage.isLoggedIn()) {
        Screen.Player.route
    } else {
        Screen.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            val viewModel = remember { LoginViewModel(tokenStorage) }
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Player.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Player.route) {
            val viewModel = remember { PlayerViewModel(tokenStorage) }
            PlayerScreen(
                viewModel = viewModel,
                onLogout = {
                    tokenStorage.clear()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Player.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
