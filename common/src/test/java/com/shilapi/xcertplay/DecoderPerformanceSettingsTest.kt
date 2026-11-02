package com.shilapi.xcertplay

import android.app.ActivityManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DecoderPerformanceSettingsTest {
    @Test fun resourceHintIsOptInAndDoesNotChangeTheSelectedFrameRate() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        assertFalse(AirPlayPersistence.loadDecoderRateHint(context))
        AirPlayPersistence.saveFps(context, 60)
        AirPlayPersistence.saveDecoderRateHint(context, true)
        assertTrue(AirPlayPersistence.loadDecoderRateHint(context))
        assertEquals(60, AirPlayPersistence.loadFps(context))
        AirPlayPersistence.saveDecoderRateHint(context, false)
        assertFalse(AirPlayPersistence.loadDecoderRateHint(context))
    }

    @Test fun exportedResourceSnapshotReportsOsMemoryPressureExplicitly() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        Shadows.shadowOf(manager).setMemoryInfo(ActivityManager.MemoryInfo().apply {
            totalMem = 1024L * 1024 * 1024
            availMem = 128L * 1024 * 1024
            threshold = 256L * 1024 * 1024
            lowMemory = true
        })
        val report = PerformanceResourceSnapshot.report(context)
        assertTrue(report.contains("sampled at diagnostic export"))
        assertTrue(report.contains("Memory totalMB=1024 availableMB=128 lowMemory=true thresholdMB=256"))
    }
}
