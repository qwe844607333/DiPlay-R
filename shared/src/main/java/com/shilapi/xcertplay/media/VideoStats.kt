package com.shilapi.xcertplay.media

import android.util.Log

/** Five-second video counters that separate network/iPhone gaps from decoder throughput. */
internal class VideoStats(
    private val label: String = "",
    private val nanoTime: () -> Long = System::nanoTime,
    private val processCpuMillis: (() -> Long)? = null,
    private val processBlockingGcMillis: (() -> Long?)? = null,
) {
    private var windowStartNs = nanoTime()
    private var lastArrivalNs = 0L
    private var received = 0
    private var rendered = 0
    private var recoveries = 0
    private var skippedOutputs = 0
    private var catchUpFrames = 0
    private var bytes = 0L
    private var maxArrivalGapNs = 0L
    private var touchSamples = 0
    private var touchLatencySumNs = 0L
    private var maxTouchLatencyNs = 0L
    private val decode = LatencySamples()
    private val queueWait = LatencySamples()
    private val inputWait = LatencySamples()
    private val submitCall = LatencySamples()
    private val releaseCall = LatencySamples()
    private var windowCpuMs = processCpuMillis?.invoke()
    private var windowBlockingGcMs = processBlockingGcMillis?.invoke()
    private var late = 0
    private var pacingDelayNanos = 0L
    private var lastDegenerateRecoveryNs = 0L

    @Synchronized fun reset() {
        windowStartNs = nanoTime()
        lastArrivalNs = 0L
        maxArrivalGapNs = 0L
        windowCpuMs = processCpuMillis?.invoke()
        windowBlockingGcMs = processBlockingGcMillis?.invoke()
        received = 0
        rendered = 0
        recoveries = 0
        skippedOutputs = 0
        catchUpFrames = 0
        bytes = 0L
        touchSamples = 0
        touchLatencySumNs = 0L
        maxTouchLatencyNs = 0L
        resetLatencySamples()
        late = 0
        lastDegenerateRecoveryNs = 0L
    }

    @Synchronized fun onReceived(size: Int) {
        val now = nanoTime()
        val gap = now - lastArrivalNs
        if (lastArrivalNs != 0L && gap < IDLE_GAP_NS) maxArrivalGapNs = maxOf(maxArrivalGapNs, gap)
        lastArrivalNs = now
        // Touches only change the main screen; a second stream must not consume their samples.
        val touchLatency = if (label.isEmpty()) TouchLatencyProbe.onFrame(now) else -1L
        if (touchLatency >= 0) {
            touchSamples++
            touchLatencySumNs += touchLatency
            maxTouchLatencyNs = maxOf(maxTouchLatencyNs, touchLatency)
        }
        received++
        bytes += size
    }

    @Synchronized fun onRendered() { rendered++ }

    @Synchronized fun onRecovery() { recoveries++ }
    @Synchronized fun onSkippedOutput() { skippedOutputs++ }
    @Synchronized fun onCatchUp(frames: Int) { catchUpFrames += frames }

    /** Time after queueInputBuffer returned until its output is dequeued; excludes software/input waits. */
    @Synchronized fun onDecodeLatency(ns: Long) { decode.add(ns) }
    @Synchronized fun onQueueWait(ns: Long) { queueWait.add(ns) }
    /** Includes draining output while retrying an unavailable input buffer. */
    @Synchronized fun onInputWait(ns: Long) { inputWait.add(ns) }
    @Synchronized fun onSubmitCall(ns: Long) { submitCall.add(ns) }
    @Synchronized fun onReleaseCall(ns: Long) { releaseCall.add(ns) }

    /** A paced frame left the decoder after its display time and was shown at once. */
    @Synchronized fun onLate() { late++ }

    /** Smooth video's current display delay ([PacingDelay]). */
    @Synchronized fun onPacingDelay(ns: Long) { pacingDelayNanos = ns }

    /** True if the pipeline is receiving a steady stream of frames but none are being rendered. */
    @Synchronized fun shouldRecoverDegeneratePipeline(): Boolean {
        val now = nanoTime()
        // Debounce: do not trigger consecutive degenerate recoveries within 3 seconds
        if (lastDegenerateRecoveryNs != 0L && now - lastDegenerateRecoveryNs < 3_000_000_000L) return false
        val elapsedNs = now - windowStartNs
        // If within the last 2+ seconds we received at least 30 frames but rendered 0, or within 3+ seconds
        // we received at least 5 frames but rendered 0 (static/low-fps stream after reverse camera),
        // the pipeline is stalled.
        val degenerate = (elapsedNs >= 2_000_000_000L && received >= 30 && rendered == 0) ||
            (elapsedNs >= 3_000_000_000L && received >= 5 && rendered == 0)
        if (degenerate) {
            reset()
            lastDegenerateRecoveryNs = now
        }
        return degenerate
    }

    private fun decodeSummary(): String {
        if (decode.isEmpty) return ""
        val delay = if (pacingDelayNanos > 0) " delay=%dms".format(pacingDelayNanos / 1_000_000) else ""
        return decode.summary("decode", includeCount = false) + " late=$late" + delay + " decodeN=${decode.size}"
    }

    private fun resetLatencySamples() {
        decode.reset(); queueWait.reset(); inputWait.reset(); submitCall.reset(); releaseCall.reset()
    }

    @Synchronized fun logIfDue(): String? {
        val now = nanoTime()
        val elapsedNs = now - windowStartNs
        if (elapsedNs < WINDOW_NS) return null
        val seconds = elapsedNs / 1e9
        val gcNow = processBlockingGcMillis?.invoke()
        val gcBefore = windowBlockingGcMs
        windowBlockingGcMs = gcNow
        val gc = if (gcNow != null && gcBefore != null && gcNow >= gcBefore) " appBlockingGcMs=${gcNow - gcBefore}" else ""
        val cpuNow = processCpuMillis?.invoke()
        val cpuBefore = windowCpuMs
        windowCpuMs = cpuNow
        val touchSend = if (label.isEmpty()) TouchLatencyProbe.takeSendTimes() else TouchLatencyProbe.SendTimes()
        if (received == 0 && rendered == 0 && skippedOutputs == 0 && catchUpFrames == 0 && touchSamples == 0 && touchSend.maxSendNs == 0L && touchSend.maxQueueNs == 0L) {
            windowStartNs = now
            resetLatencySamples()
            return null
        }
        val cpu = if (cpuNow != null && cpuBefore != null && cpuNow >= cpuBefore) {
            " appCpu=%.1f%%".format(java.util.Locale.US, (cpuNow - cpuBefore) * 100.0 / (elapsedNs / 1_000_000.0))
        } else ""
        val touchAvgMs = if (touchSamples == 0) -1 else touchLatencySumNs / touchSamples / 1_000_000
        val line = ("video stats$label rx=%.1ffps shown=%.1ffps maxGap=%dms kbps=%d recoveries=%d " +
            "touch2frame avg=%dms max=%dms n=%d touchSendMax=%dms").format(
            received / seconds, rendered / seconds, maxArrivalGapNs / 1_000_000,
            (bytes * 8 / 1000 / seconds).toLong(), recoveries,
            touchAvgMs, maxTouchLatencyNs / 1_000_000, touchSamples, touchSend.maxSendNs / 1_000_000,
        ) + " touchQueueMax=${touchSend.maxQueueNs / 1_000_000}ms" + decodeSummary() + queueWait.summary("queue") + inputWait.summary("inputWait") +
            submitCall.summary("submit") + releaseCall.summary("release") +
            " skippedOutputs=$skippedOutputs catchUpFrames=$catchUpFrames" + cpu + gc
        Log.i(TAG, line)
        windowStartNs = now
        received = 0; rendered = 0; recoveries = 0; bytes = 0; maxArrivalGapNs = 0
        skippedOutputs = 0; catchUpFrames = 0
        touchSamples = 0; touchLatencySumNs = 0; maxTouchLatencyNs = 0
        resetLatencySamples(); late = 0
        return line
    }

    private companion object {
        const val TAG = "DiPlay-VideoStats"
        const val WINDOW_NS = 5_000_000_000L
        const val IDLE_GAP_NS = 2_000_000_000L // longer gaps are a static screen, not lag
    }
}

/** Fixed storage; sorting and text allocation happen only once per reporting window. */
private class LatencySamples {
    private val micros = IntArray(1024)
    private var count = 0
    val isEmpty: Boolean get() = count == 0
    val size: Int get() = count
    fun add(ns: Long) {
        if (count < micros.size) micros[count++] = (ns / 1000).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
    fun reset() { count = 0 }
    fun summary(label: String, includeCount: Boolean = true): String {
        if (count == 0) return ""
        val sorted = micros.copyOf(count).also { it.sort() }
        return " $label p50=${sorted[(count - 1) * 50 / 100] / 1000}ms" +
            " p90=${sorted[(count - 1) * 90 / 100] / 1000}ms" + if (includeCount) " n=$count" else ""
    }
}

