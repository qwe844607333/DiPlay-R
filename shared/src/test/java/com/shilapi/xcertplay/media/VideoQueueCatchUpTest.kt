package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.VideoCodec
import org.junit.Assert.*
import org.junit.Test

class VideoQueueCatchUpTest {
    private fun frame(header: Int) = VideoJob.Frame(byteArrayOf(0, 0, 0, 1, header.toByte(), 1))

    @Test fun retainsLatestIdrAndItsDependentPictures() {
        val queue = VideoDecodeQueue()
        val newest = frame(0x65)
        val tail = frame(0x41)
        listOf(frame(0x41), frame(0x65), frame(0x41), newest, tail).forEach(queue::offer)
        assertEquals(3, queue.advanceToLatestKeyFrame(VideoCodec.H264))
        assertEquals(listOf(newest, tail), queue.drain())
    }

    @Test fun missingRestartPointKeepsAllReferencesAndControlJobs() {
        val queue = VideoDecodeQueue()
        val before = frame(0x41)
        val later = frame(0x65)
        queue.offer(before)
        queue.offer(VideoJob.Config(VideoCodec.H264, byteArrayOf(1)))
        queue.offer(later)
        assertEquals(-1, queue.advanceToLatestKeyFrame(VideoCodec.H264))
        assertSame(before, queue.poll(0))
        assertTrue(queue.poll(0) is VideoJob.Config)
        assertSame(later, queue.poll(0))
    }

    @Test fun cuttingToIdrReleasesItsFrameAndByteBudget() {
        val queue = VideoDecodeQueue(maxFrames = 3, maxBytes = 18)
        val idr = frame(0x65)
        val tail = frame(0x41)
        repeat(2) { queue.offer(frame(0x41)) }
        queue.offer(idr)
        assertEquals(2, queue.advanceToLatestKeyFrame(VideoCodec.H264))
        queue.offer(tail)
        assertEquals(listOf(idr, tail), queue.drain())
    }

    @Test fun hevcAndLengthPrefixedRestartPointsAreRecognized() {
        val queue = VideoDecodeQueue()
        queue.offer(frame(0x02))
        val idr = VideoJob.Frame(byteArrayOf(0, 0, 0, 2, 0x26, 1))
        queue.offer(idr)
        assertEquals(1, queue.advanceToLatestKeyFrame(VideoCodec.H265))
        assertSame(idr, queue.poll(0))
    }

    @Test fun malformedLengthPrefixedIdrCannotBreakTheCurrentReferenceChain() {
        val queue = VideoDecodeQueue()
        val predicted = frame(0x41)
        queue.offer(predicted)
        queue.offer(VideoJob.Frame(byteArrayOf(0, 0, 0, 3, 0x65, 1)))
        assertEquals(-1, queue.advanceToLatestKeyFrame(VideoCodec.H264))
        assertSame(predicted, queue.poll(0))
    }

    @Test fun reservedHevcIrapTypesAreNotAcceptedAsRestartPoints() {
        for (type in 22..23) {
            assertFalse(MediaCodecSupport.isRandomAccessFrame(byteArrayOf(0, 0, 0, 2, (type shl 1).toByte(), 1), VideoCodec.H265))
        }
    }
}
