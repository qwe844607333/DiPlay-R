package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class VideoOperatingRateTest {
    @Test fun rateHintFollowsSelectedFpsOnlyForEligibleMainOutput() {
        assertEquals(60, videoOperatingRate(110, null, 60, 27))
        assertEquals(30, videoOperatingRate(110, null, 30, 23))
        assertEquals(0, videoOperatingRate(110, null, 60, 22))
        assertEquals(0, videoOperatingRate(111, null, 60, 27))
        assertEquals(0, videoOperatingRate(110, "mirror", 60, 27))
        assertEquals(0, videoOperatingRate(110, null, 0, 27))
        assertEquals(0, videoOperatingRate(110, null, -1, 27))
    }
}
