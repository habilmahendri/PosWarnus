package com.example.poswarnus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.poswarnus.ui.dashboard.DashboardScreen
import com.example.poswarnus.ui.login.LoginScreen
import com.example.poswarnus.ui.splash.SplashScreen

object PosDestinations {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
}

@Composable
fun PosNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = PosDestinations.SPLASH) {
        composable(PosDestinations.SPLASH) {
            SplashScreen(onNavigateToLogin = {
                navController.navigate(PosDestinations.LOGIN) {
                    popUpTo(PosDestinations.SPLASH) { inclusive = true }
                }
            })
        }
        composable(PosDestinations.LOGIN) {
            LoginScreen(onNavigateToDashboard = {
                navController.navigate(PosDestinations.DASHBOARD) {
                    popUpTo(PosDestinations.LOGIN) { inclusive = true }
                }
            })
        }
        composable(PosDestinations.DASHBOARD) {
            DashboardScreen(onLoggedOut = {
                navController.navigate(PosDestinations.LOGIN) {
                    popUpTo(PosDestinations.DASHBOARD) { inclusive = true }
                }
            })
        }
    }
}
