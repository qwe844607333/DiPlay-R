package com.shilapi.xcertplay.airplay

import java.net.Socket
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AirPlayTraceTest {
    @Test fun defaultDiagnosticsDoNotConstructRawProtocolTraceText() {
        var built = 0
        val lines = mutableListOf<String>()
        session(false, lines).use { session ->
            invokeTrace(session) { built++; "expensive raw payload" }
            assertEquals("disabled raw tracing must not execute its text builder", 0, built)
            assertFalse(lines.any { it.startsWith("TRACE ") })
        }
    }

    @Test fun explicitlyEnabledRawTraceStillBuildsAndDeliversItsText() {
        var built = 0
        val lines = mutableListOf<String>()
        session(true, lines).use { session ->
            invokeTrace(session) { built++; "example" }
            assertEquals(1, built)
            assertTrue(lines.contains("TRACE example"))
        }
    }

    @Test fun lookupHexPreservesEveryByteAndTheEmptyPayload() {
        val bytes = ByteArray(256) { it.toByte() }
        val reference = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(reference, bytes.toHexString())
        assertEquals("", ByteArray(0).toHexString())
    }

    private fun invokeTrace(session: AirPlaySession, message: () -> String) {
        session.javaClass.getDeclaredMethod("trace", Function0::class.java)
            .apply { isAccessible = true }.invoke(session, message)
    }

    private fun session(raw: Boolean, lines: MutableList<String>) = AirPlaySession(
        socket = Socket(),
        config = AirPlayConfig(deviceName = "test", deviceId = "02:00:00:00:00:02",
            btMac = "02:00:00:00:00:01", sourceVersion = "test",
            main = AirPlayDisplayConfig(widthPixels = 800, heightPixels = 480)),
        identity = AirPlayIdentity.generate(), pairings = PairingStore(), mfi = null,
        listener = object : AirPlaySessionListener {
            override val rawProtocolTraceEnabled = raw
            override fun onDebugLog(message: String) { lines.add(message) }
        },
        media = object : AirPlayMediaHandler {},
    )
}
