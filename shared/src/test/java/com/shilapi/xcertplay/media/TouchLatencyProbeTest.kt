package com.shilapi.xcertplay.media

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TouchLatencyProbeTest {
    @Before @After fun clearProbe() {
        TouchLatencyProbe.onFrame(Long.MAX_VALUE)
        TouchLatencyProbe.takeSendTimes()
    }

    @Test fun queueAndSendDurationsAreKeptSeparateAndDrainedAtomically() {
        TouchLatencyProbe.onTouchSent(100, 5, 20)
        TouchLatencyProbe.onTouchSent(110, 7, 10)
        assertEquals(TouchLatencyProbe.SendTimes(7, 20), TouchLatencyProbe.takeSendTimes())
        assertEquals(TouchLatencyProbe.SendTimes(), TouchLatencyProbe.takeSendTimes())
        assertEquals(100L, TouchLatencyProbe.onFrame(200))
    }

    @Test fun anEarlierFrameCannotConsumeANewerTouch() {
        TouchLatencyProbe.onTouchSent(200, 1)
        assertEquals(-1L, TouchLatencyProbe.onFrame(190))
        assertEquals(50L, TouchLatencyProbe.onFrame(250))
    }
}
