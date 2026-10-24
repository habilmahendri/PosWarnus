package com.example.poswarnus.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.poswarnus.ui.components.SystemBarIcons
import com.example.poswarnus.ui.dashboard.DashboardScreen
import com.example.poswarnus.ui.history.HistoryScreen
import com.example.poswarnus.ui.login.LoginScreen
import com.example.poswarnus.ui.login.WideLayoutMinWidth
import com.example.poswarnus.ui.settings.SettingsScreen
import com.example.poswarnus.ui.splash.SplashScreen
import com.example.poswarnus.ui.theme.SurfaceWhite
import kotlinx.coroutines.launch

object PosDestinations {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
}

/**
 * Switches between the three main screens. Dashboard always stays at the bottom of the stack so its
 * cart and payment view models survive; choosing the active destination does nothing.
 */
fun NavHostController.navigateToMain(destination: MainDestination, current: MainDestination?) {
    if (destination == current) return
    when (destination) {
        MainDestination.KASIR -> popBackStack(PosDestinations.DASHBOARD, inclusive = false)
        MainDestination.RIWAYAT, MainDestination.PENGATURAN -> navigate(destination.route) {
            popUpTo(PosDestinations.DASHBOARD) { inclusive = false }
            launchSingleTop = true
        }
    }
}

@Composable
fun PosNavGraph(navController: NavHostController = rememberNavController()) {
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val compactWidth = screenWidthDp < WideLayoutMinWidth.value
    // Splash and compact Login sit on the accent colour; everything else is cream/white.
    SystemBarIcons(
        lightStatusBarIcons = route == PosDestinations.SPLASH || (route == PosDestinations.LOGIN && compactWidth),
        lightNavigationBarIcons = route == PosDestinations.SPLASH,
    )

    val logoutViewModel: LogoutViewModel = hiltViewModel()
    val confirmingLogout by logoutViewModel.confirming.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val currentDestination = MainDestination.fromRoute(route)
    val showRail = showNavRail(screenWidthDp, route)
    val showDrawer = currentDestination != null && !showRail

    // Moved, not recreated, when the surrounding rail/drawer wrapper changes, so screen state survives.
    val navHost = remember(navController) {
        movableContentOf { modifier: Modifier ->
            NavHost(
                navController = navController,
                startDestination = PosDestinations.SPLASH,
                modifier = modifier,
            ) {
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
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        // Owned by this entry, so popping the dashboard on logout destroys them along with
                        // any open dialog; the shared CartStore is cleared by LogoutViewModel.
                        cartViewModel = hiltViewModel(viewModelStoreOwner = backStackEntry),
                        paymentViewModel = hiltViewModel(viewModelStoreOwner = backStackEntry),
                    )
                }
                composable(PosDestinations.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(PosDestinations.HISTORY) {
                    HistoryScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }

    when {
        showRail -> Row {
            MainNavRail(
                current = currentDestination,
                onSelect = { navController.navigateToMain(it, currentDestination) },
                onLogout = logoutViewModel::requestLogout,
            )
            // The rail already pads the start edge; the screens beside it must not pad it again.
            // Vertical insets stay unconsumed: the rail pads only itself, the screens still need them.
            navHost(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
            )
        }
        showDrawer -> ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = route == PosDestinations.DASHBOARD,
            drawerContent = {
                // The drawer content pads the safe-drawing insets itself.
                ModalDrawerSheet(
                    drawerContainerColor = SurfaceWhite,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    MainNavDrawerContent(
                        current = currentDestination,
                        onSelect = {
                            scope.launch { drawerState.close() }
                            navController.navigateToMain(it, currentDestination)
                        },
                        onLogout = {
                            scope.launch { drawerState.close() }
                            logoutViewModel.requestLogout()
                        },
                    )
                }
            },
        ) {
            navHost(Modifier)
        }
        else -> navHost(Modifier)
    }

    if (confirmingLogout) {
        LogoutConfirmDialog(
            onConfirm = {
                logoutViewModel.logout {
                    navController.navigate(PosDestinations.LOGIN) {
                        popUpTo(PosDestinations.DASHBOARD) { inclusive = true }
                        // logout() can report twice on a double tap; never stack two Logins.
                        launchSingleTop = true
                    }
                }
            },
            onDismiss = logoutViewModel::cancelLogout,
        )
    }
}
