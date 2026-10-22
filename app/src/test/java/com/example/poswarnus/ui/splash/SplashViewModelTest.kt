package com.example.poswarnus.ui.splash

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    @Before
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `emits NavigateToLogin once the delay provider completes`() = runTest(UnconfinedTestDispatcher()) {
        var delayInvoked = false
        val viewModel = SplashViewModel(delayProvider = { delayInvoked = true })

        var emitted: SplashEvent? = null
        val job = launch { emitted = viewModel.events.first() }
        viewModel.start()
        job.join()

        assertEquals(true, delayInvoked)
        assertEquals(SplashEvent.NavigateToLogin, emitted)
    }
}
