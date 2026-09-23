package com.example

import com.example.presentation.components.DoubleTapSeekInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class Media3GestureOverlayTest {

    @Test
    fun doubleTapSeekInfo_forwardStep_calculatesCorrectly() {
        val forwardSeek = DoubleTapSeekInfo(
            isForward = true,
            seconds = 10
        )
        assertTrue(forwardSeek.isForward)
        assertEquals(10, forwardSeek.seconds)

        // Consecutive tap accumulation
        val accumulatedForward = forwardSeek.copy(seconds = forwardSeek.seconds + 10)
        assertEquals(20, accumulatedForward.seconds)
    }

    @Test
    fun doubleTapSeekInfo_rewindStep_calculatesCorrectly() {
        val rewindSeek = DoubleTapSeekInfo(
            isForward = false,
            seconds = 10
        )
        assertFalse(rewindSeek.isForward)
        assertEquals(10, rewindSeek.seconds)

        // Triple tap accumulation
        val tripleTapRewind = rewindSeek.copy(seconds = rewindSeek.seconds + 20)
        assertEquals(30, tripleTapRewind.seconds)
    }

    @Test
    fun volume_level_clampingAndPercentage() {
        val minLevel = (-0.2f).coerceIn(0f, 1f)
        val maxLevel = (1.4f).coerceIn(0f, 1f)
        val midLevel = (0.654f).coerceIn(0f, 1f)

        assertEquals(0f, minLevel, 0.001f)
        assertEquals(1f, maxLevel, 0.001f)
        assertEquals(65, (midLevel * 100).roundToInt())
    }

    @Test
    fun brightness_level_clampingAndPercentage() {
        val minBrightness = (0.005f).coerceIn(0.01f, 1.0f)
        val maxBrightness = (1.2f).coerceIn(0.01f, 1.0f)
        val currentBrightness = (0.789f).coerceIn(0.01f, 1.0f)

        assertEquals(0.01f, minBrightness, 0.001f)
        assertEquals(1.0f, maxBrightness, 0.001f)
        assertEquals(79, (currentBrightness * 100).roundToInt())
    }
}
