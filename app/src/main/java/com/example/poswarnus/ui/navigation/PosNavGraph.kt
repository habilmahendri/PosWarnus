package com.example.poswarnus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.poswarnus.ui.components.SystemBarIcons
import com.example.poswarnus.ui.login.WideLayoutMinWidth
import androidx.hilt.navigation.compose.hiltViewModel
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
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val compactWidth = LocalConfiguration.current.screenWidthDp < WideLayoutMinWidth.value
    // Splash and compact Login sit on the accent colour; everything else is cream/white.
    SystemBarIcons(
        lightStatusBarIcons = route == PosDestinations.SPLASH || (route == PosDestinations.LOGIN && compactWidth),
        lightNavigationBarIcons = route == PosDestinations.SPLASH,
    )
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
        composable(PosDestinations.DASHBOARD) { backStackEntry ->
            DashboardScreen(
                onLoggedOut = {
                    navController.navigate(PosDestinations.LOGIN) {
                        popUpTo(PosDestinations.DASHBOARD) { inclusive = true }
                    }
                },
                // Phase 4 wires payment; "Bayar" is a no-op until then.
                onCheckout = { },
                // Owned by this entry, so popping the dashboard on logout destroys it along with
                // any open dialog; the shared CartStore is cleared by LogoutViewModel.
                cartViewModel = hiltViewModel(viewModelStoreOwner = backStackEntry),
            )
        }
    }
}
