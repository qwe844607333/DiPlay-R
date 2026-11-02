package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class VideoCatchUpStatsTest {
    @Test fun skippedOutputsAreReportedWithoutInflatingShownFpsAndResetEachWindow() {
        var now = 1L
        val stats = VideoStats(nanoTime = { now })
        repeat(2) { stats.onSkippedOutput() }
        stats.onCatchUp(4)
        now += 5_000_000_000L
        val line = stats.logIfDue()!!
        assertTrue(line, line.contains("shown=0.0fps"))
        assertTrue(line, line.contains("skippedOutputs=2 catchUpFrames=4"))
        stats.onRendered()
        now += 5_000_000_000L
        assertTrue(stats.logIfDue()!!.contains("skippedOutputs=0 catchUpFrames=0"))
    }
}
