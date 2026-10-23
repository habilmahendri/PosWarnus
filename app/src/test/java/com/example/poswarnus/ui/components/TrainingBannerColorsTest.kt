package com.example.poswarnus.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingBannerColorsTest {
    @Test
    fun `banner colours meet 4_5 to 1`() {
        val ratio = contrastRatio(TrainingBannerColors.Background, TrainingBannerColors.Content)
        assertTrue("contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun `contrast ratio of black on white is 21`() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
    }
}
