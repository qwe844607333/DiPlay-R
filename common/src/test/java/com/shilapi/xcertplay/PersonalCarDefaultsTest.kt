package com.shilapi.xcertplay

import android.content.Context
import android.graphics.BitmapFactory
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class PersonalCarDefaultsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Test fun aFreshPersonalInstallUsesShadowRAndRequestsSixtyFps() {
        assertEquals("影豹R", AirPlayPersistence.loadOemLabel(context))
        assertEquals(60, AirPlayPersistence.loadFps(context))
        assertTrue(AirPlayPersistence.loadForceSquareCorners(context))
    }

    @Test fun preservesAnExplicitSquareCornerChoice() {
        AirPlayPersistence.saveForceSquareCorners(context, false)
        assertFalse(AirPlayPersistence.loadForceSquareCorners(context))

        AirPlayPersistence.saveForceSquareCorners(context, true)
        assertTrue(AirPlayPersistence.loadForceSquareCorners(context))
    }

    @Test fun upgradesTheOldDefaultOnceAndKeepsLaterExplicitChoices() {
        prefs.edit().putString("oem_label", "BYD").apply()
        assertEquals("影豹R", AirPlayPersistence.loadOemLabel(context))
        AirPlayPersistence.saveOemLabel(context, "BYD")
        assertEquals("BYD", AirPlayPersistence.loadOemLabel(context))
    }

    @Test fun preservesExistingCustomNameAndSavedFrameRate() {
        prefs.edit().putString("oem_label", "My car").apply()
        AirPlayPersistence.saveFps(context, 30)
        assertEquals("My car", AirPlayPersistence.loadOemLabel(context))
        assertEquals(30, AirPlayPersistence.loadFps(context))
    }

    @Test fun preservesAnExplicitNameBeforeTheFirstLoad() {
        AirPlayPersistence.saveOemLabel(context, "BYD")
        assertEquals("BYD", AirPlayPersistence.loadOemLabel(context))
    }

    @Test fun doesNotRenameAnExistingCustomIconPreset() {
        prefs.edit().putString("oem_label", "BYD").apply()
        File(context.filesDir, "airplay-icon.png").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals("BYD", AirPlayPersistence.loadOemLabel(context))
    }

    @Test fun packagedRedRStaysWithinTheCarPlayHandshakeIconLimits() {
        val bytes = context.resources.openRawResource(R.raw.ic_car_home).use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(bitmap)
        assertFalse(AirPlayPersistence.iconExceedsLimits(bytes.size, bitmap.width, bitmap.height))
        val background = bitmap.getPixel(bitmap.width / 2, bitmap.height / 5)
        assertTrue("personal R tile has a dark background", android.graphics.Color.red(background) < 64)
        val center = bitmap.getPixel(bitmap.width / 2, (bitmap.height * 0.54).toInt())
        assertTrue("R center stroke is red", android.graphics.Color.red(center) > android.graphics.Color.green(center) * 2)
    }
}
