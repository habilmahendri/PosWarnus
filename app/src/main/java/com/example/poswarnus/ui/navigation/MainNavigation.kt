package com.example.poswarnus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class MainDestination(val route: String, val label: String) {
    KASIR(PosDestinations.DASHBOARD, "Kasir"),
    RIWAYAT(PosDestinations.HISTORY, "Riwayat"),
    PENGATURAN(PosDestinations.SETTINGS, "Pengaturan");

    companion object {
        fun fromRoute(route: String?): MainDestination? = entries.firstOrNull { it.route == route }
    }
}

const val NavRailMinWidthDp: Int = 840

fun showNavRail(windowWidthDp: Int, route: String?): Boolean =
    windowWidthDp >= NavRailMinWidthDp && MainDestination.fromRoute(route) != null

@Composable
fun isWideWindow(): Boolean = LocalConfiguration.current.screenWidthDp >= NavRailMinWidthDp
