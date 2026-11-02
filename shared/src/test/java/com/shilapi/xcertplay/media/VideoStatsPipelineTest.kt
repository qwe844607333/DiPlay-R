package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class VideoStatsPipelineTest {
    @Test fun outputOnlyWindowIsStillReported() {
        var now = 1L
        val stats = VideoStats(nanoTime = { now })
        stats.onRendered()
        now += 5_000_000_000L
        val line = stats.logIfDue()
        assertNotNull("queued output after an input pause must remain visible", line)
        assertTrue(line!!.contains("rx=0.0fps shown=0.2fps"))
    }

    @Test fun aNewSurfaceDoesNotInheritAnOldArrivalGap() {
        var now = 1L
        val stats = VideoStats(nanoTime = { now })
        stats.onReceived(10)
        now += 100_000_000L
        stats.onReceived(10)
        stats.reset()
        now += 10_000_000L
        stats.onReceived(10)
        now += 5_000_000_000L
        assertTrue(stats.logIfDue()!!.contains("maxGap=0ms"))
    }
    @Test fun separateStagesAndProcessCpuAreNotCollapsedIntoDecodeTime() {
        var now = 1L
        var cpu = 1000L
        val stats = VideoStats(nanoTime = { now }, processCpuMillis = { cpu })
        stats.onReceived(100)
        stats.onQueueWait(25_000_000)
        stats.onInputWait(6_000_000)
        stats.onSubmitCall(4_000_000)
        stats.onDecodeLatency(130_000_000)
        stats.onReleaseCall(3_000_000)
        stats.onRendered()
        now += 5_000_000_000L
        cpu += 500
        val line = stats.logIfDue()!!
        assertTrue(line.contains("decode p50=130ms p90=130ms"))
        assertTrue(line.contains("queue p50=25ms p90=25ms"))
        assertTrue(line.contains("inputWait p50=6ms p90=6ms"))
        assertTrue(line.contains("submit p50=4ms p90=4ms"))
        assertTrue(line.contains("release p50=3ms p90=3ms"))
        assertTrue(line.contains("appCpu=10.0%"))
        now += 5_000_000_000L
        stats.onReceived(100)
        val next = stats.logIfDue()!!
        assertFalse(next.contains("queue p50="))
        assertFalse(next.contains("decode p50="))
    }

    @Test fun idleWindowsAdvanceTheCpuBaseline() {
        var now = 1L
        var cpu = 0L
        val stats = VideoStats(nanoTime = { now }, processCpuMillis = { cpu })
        now += 5_000_000_000L
        cpu = 1000
        assertNull(stats.logIfDue())
        stats.onReceived(10)
        now += 5_000_000_000L
        cpu = 1250
        assertTrue(stats.logIfDue()!!.contains("appCpu=5.0%"))
    }

    @Test fun blockingGcCounterIsWindowedAndCanBeUnavailable() {
        var now = 1L
        var gc: Long? = 100L
        val stats = VideoStats(nanoTime = { now }, processBlockingGcMillis = { gc })
        stats.onReceived(10)
        now += 5_000_000_000L
        gc = 140
        assertTrue(stats.logIfDue()!!.contains("appBlockingGcMs=40"))
        stats.onReceived(10)
        now += 5_000_000_000L
        gc = null
        assertFalse(stats.logIfDue()!!.contains("appBlockingGcMs="))
    }

    @Test fun degenerateRecoveryStartsAConsistentNewMeasurementWindow() {
        var now = 1L
        var cpu = 1000L
        var gc: Long? = 100L
        val stats = VideoStats(nanoTime = { now }, processCpuMillis = { cpu }, processBlockingGcMillis = { gc })
        repeat(30) { stats.onReceived(1000) }
        now += 2_000_000_000L
        cpu = 1300; gc = 110
        assertTrue(stats.shouldRecoverDegeneratePipeline())
        stats.onReceived(5000)
        stats.onRendered()
        now += 5_000_000_000L
        cpu = 1550; gc = 130
        val line = stats.logIfDue()!!
        assertTrue(line, line.contains("appCpu=5.0%"))
        assertTrue(line, line.contains("appBlockingGcMs=20"))
        assertTrue(line, line.contains("kbps=8"))
    }

    @Test fun clusterStatsDoNotConsumeMainTouchQueueMeasurements() {
        TouchLatencyProbe.onFrame(Long.MAX_VALUE)
        TouchLatencyProbe.takeSendTimes()
        var now = 1L
        val main = VideoStats(nanoTime = { now })
        val cluster = VideoStats(" stream=111", nanoTime = { now })
        try {
            TouchLatencyProbe.onTouchSent(1, 7_000_000, 50_000_000)
            cluster.onRendered()
            now += 5_000_000_000L
            assertTrue(cluster.logIfDue()!!.contains("touchQueueMax=0ms"))
            val line = main.logIfDue()!!
            assertTrue(line.contains("touchSendMax=7ms"))
            assertTrue(line.contains("touchQueueMax=50ms"))
        } finally {
            TouchLatencyProbe.onFrame(Long.MAX_VALUE)
            TouchLatencyProbe.takeSendTimes()
        }
    }

}
