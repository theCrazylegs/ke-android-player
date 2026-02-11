package com.thecrazylegs.keplayer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.thecrazylegs.keplayer.data.storage.TokenStorage
import com.thecrazylegs.keplayer.ui.login.LoginScreen
import com.thecrazylegs.keplayer.ui.login.LoginViewModel
import com.thecrazylegs.keplayer.ui.player.PlayerScreen
import com.thecrazylegs.keplayer.ui.player.PlayerViewModel
import com.thecrazylegs.keplayer.ui.welcome.WelcomeScreen
import com.thecrazylegs.keplayer.ui.welcome.WelcomeViewModel

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
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
        Screen.Welcome.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Welcome.route) {
            val context = LocalContext.current
            val viewModel = remember { WelcomeViewModel(tokenStorage, context.applicationContext) }
            WelcomeScreen(
                viewModel = viewModel,
                onPaired = {
                    navController.navigate(Screen.Player.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onManualLogin = {
                    navController.navigate(Screen.Login.route)
                }
            )
        }

        composable(Screen.Login.route) {
            val viewModel = remember { LoginViewModel(tokenStorage) }
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Player.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Player.route) {
            val viewModel = remember { PlayerViewModel(tokenStorage) }
            PlayerScreen(
                viewModel = viewModel,
                onLogout = {
                    tokenStorage.clearAuth()
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Player.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
