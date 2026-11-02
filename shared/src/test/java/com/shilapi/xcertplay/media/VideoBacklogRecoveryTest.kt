package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class VideoBacklogRecoveryTest {
    private val ms = 1_000_000L

    @Test fun delayedBurstKeepsItsReferenceChainEvenWhenOld() {
        val recovery = VideoBacklogRecovery()
        val now = 3_000 * ms
        assertFalse(recovery.observe(now, now - 2_000 * ms, 3, now - 1_990 * ms))
        assertFalse(recovery.observe(now + 100 * ms, now - 2_000 * ms, 2, now - 1_990 * ms))
    }

    @Test fun staticTailWithoutQueuedFramesIsNotABacklog() {
        assertFalse(VideoBacklogRecovery().observe(3_000 * ms, 500 * ms, 0, null))
    }

    @Test fun growingBacklogNeedsTheGracePeriodBeforeRecovery() {
        val recovery = VideoBacklogRecovery()
        val now = 2_000 * ms
        assertFalse(recovery.observe(now, now - 400 * ms, 12, now - 100 * ms))
        assertFalse(recovery.observe(now + 700 * ms, now + 150 * ms, 14, now + 550 * ms))
        assertTrue(recovery.observe(now + 800 * ms, now + 150 * ms, 17, now + 700 * ms))
        assertFalse("recovery starts a new observation episode", recovery.observe(now + 810 * ms,
            now + 160 * ms, 17, now + 710 * ms))
    }

    @Test fun stableQueueDoesNotRecoverWithoutGrowthBelowTheHardLimit() {
        val recovery = VideoBacklogRecovery()
        val now = 2_000 * ms
        assertFalse(recovery.observe(now, now - 400 * ms, 10, now - 100 * ms))
        assertFalse(recovery.observe(now + 900 * ms, now + 200 * ms, 10, now + 500 * ms))
    }

    @Test fun hardLimitRecoversAQueueWithMeaningfulSpanImmediately() {
        val now = 2_000 * ms
        assertTrue(VideoBacklogRecovery().observe(now, now - 1_600 * ms, 8, now - 100 * ms))
    }

    @Test fun invalidSnapshotClearsTheEarlierObservationEpisode() {
        val recovery = VideoBacklogRecovery()
        val now = 2_000 * ms
        assertFalse(recovery.observe(now, now - 400 * ms, 12, now - 100 * ms))
        assertFalse(recovery.observe(now - 1, now - 400 * ms, 12, now - 100 * ms))
        assertFalse(recovery.observe(now + 800 * ms, now + 150 * ms, 17, now + 700 * ms))
        assertFalse(recovery.observe(now + 900 * ms, now + 200 * ms, 17, now + 901 * ms))
    }

    @Test fun explicitResetDoesNotCarryOldGrowthIntoANewSurface() {
        val recovery = VideoBacklogRecovery()
        val now = 2_000 * ms
        assertFalse(recovery.observe(now, now - 400 * ms, 12, now - 100 * ms))
        recovery.reset()
        assertFalse(recovery.observe(now + 800 * ms, now + 150 * ms, 17, now + 700 * ms))
    }
}
