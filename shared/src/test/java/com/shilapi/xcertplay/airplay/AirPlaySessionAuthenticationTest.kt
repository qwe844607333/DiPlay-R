package com.shilapi.xcertplay.airplay

import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class AirPlaySessionAuthenticationTest {
    private class Fixture(socket: Socket = Socket()) : AutoCloseable {
        val client = PairVerifyTestClient()
        val calls = mutableListOf<String>()
        val active = CountDownLatch(1)
        val session = AirPlaySession(
            socket, AirPlayConfig(deviceName = "test", deviceId = "02:00:00:00:00:02",
                btMac = "02:00:00:00:00:01", sourceVersion = "1",
                main = AirPlayDisplayConfig(800, 480), mainBufferedAudio = true, videoInCar = true),
            AirPlayIdentity.generate(), client.pairings, null,
            object : AirPlaySessionListener {
                override fun onSessionActive(session: AirPlaySession) { calls += "record"; active.countDown() }
                override fun onCommand(session: AirPlaySession, type: String, params: Map<String, Any?>) { calls += "command" }
                override fun onModesChanged(session: AirPlaySession, appStates: List<Map<String, Any?>>, resources: List<Map<String, Any?>>) { calls += "modes" }
            },
            object : AirPlayMediaHandler {
                override fun onScreen(session: AirPlaySession, type: Int, stream: Map<String, Any?>): Int { calls += "screen"; return 1234 }
                override fun onAudio(session: AirPlaySession, type: Int, stream: Map<String, Any?>): Map<String, Any?> { calls += "audio"; return mapOf("type" to type) }
                override fun onDataStream(session: AirPlaySession, stream: Map<String, Any?>): Map<String, Any?> { calls += "iap"; return mapOf("type" to 130) }
                override fun onBufferedAudio(session: AirPlaySession, stream: Map<String, Any?>): Map<String, Any?> { calls += "buffered"; return mapOf("type" to 103) }
                override fun onBufferedAudioControl(session: AirPlaySession, method: String, body: Map<String, Any?>): Map<String, Any?>? { calls += "buffered-control"; return null }
                override fun onFeedback(session: AirPlaySession): Map<String, Any?>? { calls += "feedback"; return null }
                override fun onSetupResponseSent(session: AirPlaySession) { calls += "setup-response" }
            },
        )
        fun handle(method: String, path: String = "/", body: ByteArray = ByteArray(0)): RtspMessage.Response =
            (AirPlaySession::class.java.getDeclaredMethod("handle", RtspMessage.Request::class.java)
                .apply { isAccessible = true }
                .invoke(session, RtspMessage.Request(method, path, "RTSP/1.0", emptyMap(), body)) as RtspMessage.Response)
                .let { it.copy(status = it.status ?: 200) }
        override fun close() { session.close() }
    }

    private val setup = BplistCodec.encode(mapOf("streams" to listOf(
        mapOf("type" to 110), mapOf("type" to 100), mapOf("type" to 130),
        mapOf("type" to 103, "shk" to ByteArray(32)),
    )))

    @Test fun storedPairingAndTemporaryExchangeDoNotAuthorizeOperations() {
        for (pending in listOf(false, true)) {
            val operations = listOf(
                "SETUP" to "/", "RECORD" to "/", "TEARDOWN" to "/",
                "SETRATE" to "/", "SETRATEANCHORTIME" to "/", "GETANCHOR" to "/", "FLUSHBUFFERED" to "/",
                "POST" to "/command", "POST" to "/modeschanged", "POST" to "/changemodes",
                "POST" to "/feedback", "POST" to "rtsp://127.0.0.1/COMMAND",
            )
            for ((method, path) in operations) Fixture().use { f ->
                if (pending) f.session.pairVerify.handle(f.client.m1())
                val response = f.handle(method, path, if (method == "SETUP") setup else BplistCodec.encode(emptyMap<String, Any>()))
                assertEquals("$pending $method $path", 401, response.status)
                assertEquals("Unauthorized", response.statusText)
                assertTrue(f.calls.toString(), f.calls.isEmpty())
                assertTrue(f.session.activeStreams.isEmpty())
            }
        }
    }

    @Test fun sensitiveMethodsCannotHideBehindPublicEndpointNames() {
        for ((method, path) in listOf("SETUP" to "/info", "RECORD" to "/pair-verify",
            "TEARDOWN" to "/pair-setup", "GETANCHOR" to "/auth-setup")) Fixture().use { f ->
            assertEquals("$method $path", 401, f.handle(method, path, byteArrayOf(1)).status)
            assertTrue(f.calls.isEmpty())
        }
    }

    @Test fun unauthenticatedSetupIsRejectedBeforeParsingItsBody() {
        Fixture().use { f ->
            assertEquals(401, f.handle("SETUP", "/", byteArrayOf(1, 2, 3)).status)
            assertTrue(f.calls.isEmpty())
        }
    }

    @Test fun discoveryAndHarmlessCompatibilityResponsesRemainAvailable() {
        Fixture().use { f ->
            assertEquals(200, f.handle("GET", "/info").status)
            assertEquals(200, f.handle("OPTIONS").status)
            assertEquals(200, f.handle("GET_PARAMETER").status)
            assertEquals(200, f.handle("POST", "/fp-setup").status)
            assertEquals(200, f.handle("GET", "/command").status)
            assertEquals(400, f.handle("POST", "/auth-setup").status) // No MFi provider in fixture.
            val m2 = f.handle("POST", "/pair-verify", f.client.m1())
            assertEquals(200, m2.status)
            assertNull(Tlv8Codec.decode(m2.body)[7])
        }
    }

    @Test fun verifiedControllerRetainsMediaCommandsAndBufferedControls() {
        Fixture().use { f ->
            f.client.authenticate(f.session.pairVerify)
            assertArrayEquals(f.client.shared, f.session.sharedSecret)
            assertEquals(200, f.handle("SETUP", "/", setup).status)
            assertEquals(setOf("screen", "audio", "iap", "buffered"), f.calls.toSet())
            assertEquals(200, f.handle("RECORD").status)
            assertEquals(200, f.handle("POST", "/command", BplistCodec.encode(mapOf("type" to "test"))).status)
            assertEquals(200, f.handle("POST", "/feedback").status)
            assertEquals(200, f.handle("GETANCHOR").status)
            assertTrue(f.calls.containsAll(listOf("record", "command", "feedback", "buffered-control")))
        }
    }

    @Test fun verificationIsPerConnectionAndClosedSessionCannotPublishKeys() {
        Fixture().use { verified -> Fixture().use { other ->
            verified.client.authenticate(verified.session.pairVerify)
            assertNotNull(verified.session.sharedSecret)
            assertEquals(401, other.handle("RECORD").status)
            verified.session.close()
            assertNull(verified.session.sharedSecret)
            assertEquals(401, verified.handle("SETUP", "/", setup).status)
        } }
    }

    @Test fun clearM4ResponseStillTransitionsToEncryptedControlOnTheWire() {
        val loopback = InetAddress.getLoopbackAddress()
        ServerSocket(0, 1, loopback).use { listener ->
            Socket(loopback, listener.localPort).use { peer ->
                peer.soTimeout = 5_000
                Fixture(listener.accept()).use { f ->
                    f.session.start()
                    val input = DataInputStream(peer.getInputStream())
                    val output = peer.getOutputStream()
                    fun exchange(path: String, body: ByteArray): ByteArray {
                        output.write(request("POST", path, body)); output.flush()
                        return readClear(input).also { assertTrue(it.toString(Charsets.ISO_8859_1).startsWith("RTSP/1.0 200")) }
                    }
                    val m2wire = exchange("/pair-verify", f.client.m1())
                    val m2 = m2wire.copyOfRange(headerEnd(m2wire), m2wire.size)
                    val m4wire = exchange("/pair-verify", f.client.m3(m2))
                    assertNull(Tlv8Codec.decode(m4wire.copyOfRange(headerEnd(m4wire), m4wire.size))[7])
                    val cipher = f.client.controlCipher()
                    output.write(cipher.encrypt(request("RECORD", "/", ByteArray(0)))); output.flush()
                    val low = input.readUnsignedByte()
                    val high = input.readUnsignedByte()
                    val body = ByteArray((low or (high shl 8)) + 16).also(input::readFully)
                    val clear = cipher.decrypt(byteArrayOf(low.toByte(), high.toByte()) + body).data
                    assertTrue(clear.toString(Charsets.ISO_8859_1).startsWith("RTSP/1.0 200"))
                    assertTrue(f.active.await(5, TimeUnit.SECONDS))
                }
            }
        }
    }

    @Test fun wireDenialsDoNotInvokeCallbacksOrPreventALaterValidHandshake() {
        val loopback = InetAddress.getByName("127.0.0.1")
        ServerSocket(0, 1, loopback).use { listener ->
            Socket(loopback, listener.localPort).use { peer ->
                peer.soTimeout = 5_000
                Fixture(listener.accept()).use { f ->
                    f.session.start()
                    val input = DataInputStream(peer.getInputStream())
                    val output = peer.getOutputStream()
                    for ((method, path) in listOf("RECORD" to "/pair-verify", "SETUP" to "/info")) {
                        output.write(request(method, path, byteArrayOf(1))); output.flush()
                        assertTrue(readClear(input).toString(Charsets.ISO_8859_1)
                            .startsWith("RTSP/1.0 401 Unauthorized"))
                    }
                    assertTrue(f.calls.isEmpty())
                    output.write(request("POST", "/pair-verify", f.client.m1())); output.flush()
                    val m2 = readClear(input)
                    output.write(request("POST", "/pair-verify",
                        f.client.m3(m2.copyOfRange(headerEnd(m2), m2.size)))); output.flush()
                    val m4 = readClear(input)
                    assertNull(Tlv8Codec.decode(m4.copyOfRange(headerEnd(m4), m4.size))[7])
                    assertTrue(f.session.pairVerify.isVerified)
                }
            }
        }
    }

    @Test fun provisionalKeysCannotActivateTheEventChannel() {
        Fixture().use { f ->
            f.session.pairVerify.handle(f.client.m1())
            val port = openEvent(f.session)
            Socket("127.0.0.1", port).use { peer ->
                peer.soTimeout = 5_000
                // The server uses SO_LINGER(0), so a rejected channel may reset rather than EOF.
                val firstByte = try { peer.getInputStream().read() } catch (_: SocketException) { -1 }
                assertEquals(-1, firstByte)
            }
            assertNull(f.session.sharedSecret)
            assertFalse(f.session.sendCommand(mapOf("type" to "testEvent")))
        }
    }

    @Test fun verifiedControllerCanReceiveEncryptedEventCommands() {
        Fixture().use { f ->
            val shared = f.client.authenticate(f.session.pairVerify)
            Socket("127.0.0.1", openEvent(f.session)).use { peer ->
                peer.soTimeout = 5_000
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                var sent = false
                while (!sent && System.nanoTime() < deadline) {
                    sent = f.session.sendCommand(mapOf("type" to "testEvent"))
                    if (!sent) Thread.sleep(1)
                }
                assertTrue("verified event channel must become usable", sent)
                val cipher = ControlCipher(
                    AirPlayCrypto.hkdfSha512(shared, "Events-Salt".asciiBytes(),
                        "Events-Write-Encryption-Key".asciiBytes()),
                    AirPlayCrypto.hkdfSha512(shared, "Events-Salt".asciiBytes(),
                        "Events-Read-Encryption-Key".asciiBytes()),
                )
                val input = DataInputStream(peer.getInputStream())
                val low = input.readUnsignedByte()
                val high = input.readUnsignedByte()
                val body = ByteArray((low or (high shl 8)) + 16).also(input::readFully)
                val clear = cipher.decrypt(byteArrayOf(low.toByte(), high.toByte()) + body).data
                val command = BplistCodec.decode(clear.copyOfRange(headerEnd(clear), clear.size)) as Map<*, *>
                assertEquals("testEvent", command["type"])
            }
        }
    }

    private fun openEvent(session: AirPlaySession): Int =
        AirPlaySession::class.java.getDeclaredMethod("openEvent").apply { isAccessible = true }
            .invoke(session) as Int

    private fun request(method: String, path: String, body: ByteArray): ByteArray =
        "$method $path RTSP/1.0\r\nCSeq: 1\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray() + body

    private fun headerEnd(bytes: ByteArray): Int =
        bytes.toString(Charsets.ISO_8859_1).indexOf("\r\n\r\n").also { check(it >= 0) } + 4

    private fun readClear(input: DataInputStream): ByteArray {
        val head = java.io.ByteArrayOutputStream()
        while (!head.toString("ISO-8859-1").endsWith("\r\n\r\n")) {
            check(head.size() < 8192)
            head.write(input.readUnsignedByte())
        }
        val length = Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE)
            .find(head.toString("ISO-8859-1"))!!.groupValues[1].toInt()
        val body = ByteArray(length).also(input::readFully)
        return head.toByteArray() + body
    }
}
