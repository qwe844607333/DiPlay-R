package com.shilapi.xcertplay.media

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.view.Surface
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaCodec
import org.robolectric.shadow.api.Shadow
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList

/** Exercises the real decoder drain; fabricated outputs stand in for a burst from the vendor codec. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, shadows = [VideoOutputCatchUpTest.RecordingCodec::class])
class VideoOutputCatchUpTest {
    @Implements(MediaCodec::class)
    class RecordingCodec : ShadowMediaCodec() {
        private val ready = ArrayDeque<Pair<Int, Long>>()
        val released = CopyOnWriteArrayList<Pair<Int, Boolean>>()
        @Synchronized fun enqueue(outputs: List<Pair<Int, Long>>) { ready.addAll(outputs) }
        @Implementation override fun native_dequeueOutputBuffer(info: MediaCodec.BufferInfo, timeoutUs: Long): Int = synchronized(this) {
            val output = ready.pollFirst() ?: return@synchronized super.native_dequeueOutputBuffer(info, timeoutUs)
            info.set(0, 1, output.second, 0)
            output.first
        }
        @Implementation override fun releaseOutputBuffer(index: Int, render: Boolean) {
            if (index >= 100) released += index to render else super.releaseOutputBuffer(index, render)
        }
        @Implementation override fun releaseOutputBuffer(index: Int, timestampNs: Long) {
            if (index >= 100) released += index to true else super.releaseOutputBuffer(index, timestampNs)
        }
    }

    private var sink: AndroidMediaSink? = null
    private val surfaces = mutableListOf<Pair<Surface, SurfaceTexture>>()

    @After fun cleanup() {
        sink?.close()
        sink?.let { assertTrue(it.awaitVideoReleased(5_000)) }
        surfaces.forEach { (s, t) -> s.release(); t.release() }
    }

    @Test fun overdueDecodedBurstPresentsNewestWithoutRenderingEveryOldPicture() {
        val codec = enqueueBurst(ageMs = 400, paced = false)
        assertEquals(listOf(100 to false, 101 to false, 102 to true), codec.released.toList())
    }

    @Test fun normalLatencyBurstKeepsEveryPicture() {
        val codec = enqueueBurst(ageMs = 10, paced = false)
        assertEquals(listOf(100 to true, 101 to true, 102 to true), codec.released.toList())
    }

    @Test fun pacedOutputKeepsItsExistingDisplaySchedule() {
        val codec = enqueueBurst(ageMs = 400, paced = true)
        assertEquals(listOf(100 to true, 101 to true, 102 to true), codec.released.toList())
    }

    @Test fun lastOverduePictureIsStillShownOnAStaticScreen() {
        val codec = enqueueBurst(ageMs = 400, paced = false, count = 1)
        assertEquals(listOf(100 to true), codec.released.toList())
    }

    private fun enqueueBurst(ageMs: Long, paced: Boolean, count: Int = 3): RecordingCodec {
        val host = AndroidMediaSink(surface = null, videoPacingDelayMillis = if (paced) 90 else 0)
        sink = host
        host.setSurface(110, newSurface())
        host.onVideoConfig(110, byteArrayOf(1, 0x42, 0, 0x1e, -1, -31, 0, 2, 0x67, 1, 1, 0, 2, 0x68, 1))
        assertTrue(host.beginSurfaceDetach(newSurface(), parkMain = false).await())
        val decoder = host.field<Map<Int, Any>>("videoDecoders").getValue(110)
        val codec = Shadow.extract<RecordingCodec>(decoder.field<MediaCodec>("decoder"))
        val now = System.nanoTime()
        val pts = decoder.field<LongArray>("queuedPresentationUs")
        val queued = decoder.field<LongArray>("queuedAtNanos")
        repeat(count) { i -> pts[i] = 1_000L + i; queued[i] = now - ageMs * 1_000_000 }
        codec.enqueue(List(count) { (100 + it) to (1_000L + it) })
        val deadline = System.nanoTime() + 5_000_000_000L
        while (codec.released.size < count && System.nanoTime() < deadline) Thread.sleep(1)
        assertTrue(host.beginSurfaceDetach(newSurface(), parkMain = false).await())
        assertEquals(count, codec.released.size)
        return codec
    }

    private fun newSurface(): Surface = SurfaceTexture(0).let { texture ->
        Surface(texture).also { surfaces += it to texture }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.field(name: String): T = javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T
}
