package com.shilapi.xcertplay.airplay

import org.junit.Assert.*
import org.junit.Test

class StreamReceiveStatsTest {
    @Test fun linksSequenceGapsToTheSameLongReadAndResetsOnlyWindowCounters() {
        var clock = 1L
        val output = mutableListOf<String>()
        val stats = StreamReceiveStats("audio", output::add) { clock }
        fun packet(sequence: Int, readMs: Long) {
            stats.reading()
            clock += readMs * 1_000_000L
            stats.received(100, sequence)
            stats.processed()
        }
        packet(1, 10)
        packet(4, 300) // Two missing packets follow this long socket wait.
        packet(7, 10) // Two more missing packets follow a short socket wait.
        packet(8, 400) // A long read alone does not imply packet loss.
        stats.flush(ended = true)
        assertEquals(1, output.size)
        assertTrue(output.single().contains("windowMs=720 readsOver250Ms=2 seqGapAfterReadOver250Ms=1 seqMissingAfterReadOver250Ms=2"))
        packet(9, 10)
        stats.flush(ended = true)
        assertTrue(output.last().contains("windowMs=10 readsOver250Ms=0 seqGapAfterReadOver250Ms=0 seqMissingAfterReadOver250Ms=0"))
        assertTrue(output.last().contains("seqForwardGaps=0"))
    }

    @Test fun separatesSocketWaitFromLocalProcessingAndTracksSequenceWrap() {
        var clock = 0L
        val output = mutableListOf<String>()
        val stats = StreamReceiveStats("audio", output::add) { clock }
        fun packet(sequence: Int) {
            stats.reading()
            clock += 400_000_000L
            stats.received(100, sequence)
            clock += 200_000L
            stats.processed()
        }
        packet(65535)
        packet(0)
        packet(3) // two missing, then one late packet arrives
        packet(2)
        packet(4)
        stats.flush(ended = true)
        assertEquals(1, output.size)
        assertTrue(output.single().contains("readMaxMs=400 processMaxUs=200 seqForwardGaps=2 lateOrDuplicate=1"))
        assertTrue(output.single().contains("packets=5 bytes=500"))
    }

    @Test fun reportResetsWindowButRetainsSequenceContinuity() {
        var clock = 0L
        val output = mutableListOf<String>()
        val stats = StreamReceiveStats("audio", output::add) { clock }
        stats.reading()
        clock = 5_000_000_000L
        stats.received(10, 1)
        stats.processed()
        stats.reading()
        stats.received(20, 3)
        stats.processed()
        stats.flush(true)
        assertEquals(2, output.size)
        assertTrue(output.last().contains("packets=1 bytes=20 readMaxMs=0"))
        assertTrue(output.last().contains("seqForwardGaps=1"))
    }

    @Test fun decryptTimingIsReportedOnlyWhenRecordedAndThenReset() {
        var clock = 0L
        val output = mutableListOf<String>()
        val stats = StreamReceiveStats("video", output::add) { clock }
        stats.received(1_000)
        stats.decrypted(2_000_000, 500_000)
        stats.decrypted(500_000, 250_000)
        stats.processed()
        clock = 5_000_000_000L
        stats.flush()
        assertTrue(output.last().endsWith(" decryptAvgUs=1250 decryptMaxUs=2000 decryptMBps=300.00"))
        clock = 10_000_000_000L
        stats.flush()
        assertTrue(!output.last().contains("decrypt"))
    }

    @Test fun decryptWorkArrivingDuringAReportBelongsToTheNextWindow() {
        var clock = 0L
        val output = mutableListOf<String>()
        lateinit var stats: StreamReceiveStats
        stats = StreamReceiveStats("video", { line ->
            output.add(line)
            if (output.size == 1) stats.decrypted(2_000_000L, 1_000)
        }) { clock }
        stats.decrypted(1_000_000L, 1_000)
        clock = 5_000_000_000L
        stats.flush()
        clock = 10_000_000_000L
        stats.flush()
        assertTrue("concurrent decrypt work must not be reset after the report callback",
            output.last().contains("decryptAvgUs=2000"))
    }

    @Test fun decryptThroughputBelowOneMBpsIsNotTruncatedToZero() {
        var clock = 0L
        val output = mutableListOf<String>()
        val stats = StreamReceiveStats("video", output::add) { clock }
        stats.received(1_000)
        stats.decrypted(2_000_000, 500)
        stats.processed()
        clock = 5_000_000_000L
        stats.flush()
        assertTrue(output.last().endsWith(" decryptAvgUs=2000 decryptMaxUs=2000 decryptMBps=0.25"))
    }
    @Test fun senderCadenceIsSeparatedFromBurstArrivalAndMissingSequenceData() {
        val output = mutableListOf<String>()
        val stats = StreamReceiveStats("video", output::add)
        stats.videoFrameTiming(1_000_000_000L, 1_000_000_000L)
        stats.videoFrameTiming(1_016_000_000L, 1_316_000_000L)
        stats.videoFrameTiming(1_032_000_000L, 1_317_000_000L)
        stats.flush(true)
        val line = output.single()
        assertTrue(line.contains("sequenceTracking=not_provided"))
        assertTrue(line.contains("senderSamples=2 senderAvgUs=16000 senderMaxGapMs=16 frameArrivalMaxGapMs=316"))
    }

    @Test fun decryptQueueWaitIsNotReportedAsCryptographicExecutionTime() {
        val output = mutableListOf<String>()
        val stats = StreamReceiveStats("video", output::add)
        stats.decrypted(1_000_000, 1000, 20_000_000)
        stats.flush(true)
        assertTrue(output.single().contains("decryptQueueAvgUs=20000 decryptQueueMaxUs=20000"))
        assertTrue(output.single().contains("decryptAvgUs=1000"))
    }

    @Test fun missingAndBackwardSenderClocksDoNotProduceFakeCadence() {
        val output = mutableListOf<String>()
        val stats = StreamReceiveStats("video", output::add)
        stats.videoFrameTiming(0, 100)
        stats.videoFrameTiming(1000, 200)
        stats.videoFrameTiming(900, 300)
        stats.flush(true)
        assertTrue(output.single().contains("senderSamples=0"))
        assertFalse(output.single().contains("senderAvgUs="))
    }

    @Test fun slowestDecryptSampleKeepsItsOwnPayloadSize() {
        val output = mutableListOf<String>()
        val stats = StreamReceiveStats("video", output::add)
        stats.decrypted(1_000_000, 500_000)
        stats.decrypted(10_000_000, 1_000)
        stats.flush(true)
        assertTrue(output.single().contains("decryptMaxSampleBytes=1000"))
        assertTrue(output.single().contains("decryptMaxUs=10000"))
    }

}
