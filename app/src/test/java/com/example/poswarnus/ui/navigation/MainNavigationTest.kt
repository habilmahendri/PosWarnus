package com.example.poswarnus.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationTest {

    @Test
    fun `fromRoute maps the three main routes`() {
        assertEquals(MainDestination.KASIR, MainDestination.fromRoute(PosDestinations.DASHBOARD))
        assertEquals(MainDestination.RIWAYAT, MainDestination.fromRoute(PosDestinations.HISTORY))
        assertEquals(MainDestination.PENGATURAN, MainDestination.fromRoute(PosDestinations.SETTINGS))
    }

    @Test
    fun `fromRoute is null for login, splash, null and unknown routes`() {
        assertNull(MainDestination.fromRoute(PosDestinations.LOGIN))
        assertNull(MainDestination.fromRoute(PosDestinations.SPLASH))
        assertNull(MainDestination.fromRoute(null))
        assertNull(MainDestination.fromRoute("unknown"))
    }

    @Test
    fun `showNavRail is true on a tablet for each main route`() {
        assertTrue(showNavRail(1024, PosDestinations.DASHBOARD))
        assertTrue(showNavRail(1024, PosDestinations.HISTORY))
        assertTrue(showNavRail(1024, PosDestinations.SETTINGS))
    }

    @Test
    fun `showNavRail flips exactly at 840dp`() {
        assertFalse(showNavRail(839, PosDestinations.DASHBOARD))
        assertTrue(showNavRail(840, PosDestinations.DASHBOARD))
    }

    @Test
    fun `showNavRail is false for login and splash even on a tablet`() {
        assertFalse(showNavRail(1024, PosDestinations.LOGIN))
        assertFalse(showNavRail(1024, PosDestinations.SPLASH))
    }

    @Test
    fun `showNavRail is false for a null route`() {
        assertFalse(showNavRail(1024, null))
    }
}
