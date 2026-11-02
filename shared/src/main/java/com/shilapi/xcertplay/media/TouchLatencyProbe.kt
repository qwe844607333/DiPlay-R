package com.shilapi.xcertplay.media

/** Diagnostic touch-to-receive proxy; it does not measure physical presentation. */
internal object TouchLatencyProbe {
    private var pendingTouchNs = 0L
    private var maxSendNs = 0L
    private var maxQueueNs = 0L

    data class SendTimes(val maxSendNs: Long = 0L, val maxQueueNs: Long = 0L)

    @Synchronized fun onTouchSent(sentAtNs: Long, sendDurationNs: Long, queueDelayNs: Long = 0L) {
        if (pendingTouchNs == 0L) pendingTouchNs = sentAtNs
        maxSendNs = maxOf(maxSendNs, sendDurationNs)
        maxQueueNs = maxOf(maxQueueNs, queueDelayNs)
    }

    /** The receive timestamp may predate a touch recorded concurrently; leave that touch pending. */
    @Synchronized fun onFrame(nowNs: Long): Long {
        val touch = pendingTouchNs
        if (touch == 0L || nowNs < touch) return -1L
        pendingTouchNs = 0L
        return nowNs - touch
    }

    /** Only main-screen stats drain this window; mirrors/cluster cannot steal its samples. */
    @Synchronized fun takeSendTimes(): SendTimes = SendTimes(maxSendNs, maxQueueNs).also {
        maxSendNs = 0L
        maxQueueNs = 0L
    }
}
