package com.shilapi.xcertplay.media

import android.view.Surface
import com.shilapi.xcertplay.airplay.VideoCodec
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

internal sealed interface VideoJob {
    data class Config(val codec: VideoCodec, val codecData: ByteArray) : VideoJob
    data class Frame(
        val nalus: ByteArray,
        val receivedNs: Long = System.nanoTime(),
        /** The iPhone's frame time and when the frame arrived (System.nanoTime), 0 when unknown. */
        val senderNanos: Long = 0L,
        val arrivalNanos: Long = 0L,
    ) : VideoJob
    data class SurfaceChanged(val surface: Surface?) : VideoJob
    /** Stop rendering to a surface that is going away; completes [request] once the codec has let go. */
    data class DetachSurface(val request: SurfaceDetachRequest) : VideoJob
    /** Ask the iPhone for a keyframe, so a surface that just came back gets a picture without waiting for motion. */
    data object RefreshPicture : VideoJob
    /** Issue #315: legacy reverse strategy — release the codec and stop the input pump. */
    data object ReleaseForReverse : VideoJob
    /** Issue #315: legacy reverse strategy — cold-start the codec after the exit delay. */
    data object ResumeFromReverse : VideoJob
    data object Resync : VideoJob
    /** A frame was lost before it reached this queue, so the reference chain is broken (issue #288). */
    data object ReferenceLost : VideoJob
}

/** Do not resume dependent pictures after losing a reference frame. */
internal class VideoReferenceChain {
    var needsKeyFrame = true
        private set
    fun reset() { needsKeyFrame = true }
    /** A frame dropped upstream (e.g. the decrypt queue) breaks the chain just the same. */
    fun onReferenceLost() { needsKeyFrame = true }
    fun accepts(bytes: ByteArray, codec: VideoCodec): Boolean =
        !needsKeyFrame || MediaCodecSupport.isRandomAccess(bytes, codec)
    fun onQueued() { needsKeyFrame = false }
}

/** Limit latency and memory without ever dropping a reference frame silently. */
internal class VideoDecodeQueue(
    // Recovery observes sustained backlog; short delayed bursts keep their reference chain.
    private val maxFrames: Int = 60,
    private val maxBytes: Int = 8 * 1024 * 1024,
) {
    private val lock = Object()
    private val jobs = ArrayDeque<VideoJob>()
    private var pendingFrames = 0
    private var pendingBytes = 0L

    data class Backlog(val pendingFrames: Int, val newestPendingReceivedNs: Long?)

    /** Called by the sole consumer; control work ends the current reference chain. */
    fun backlogAfterCurrent(): Backlog = synchronized(lock) {
        var frames = 0
        var newest: Long? = null
        for (job in jobs) {
            if (job !is VideoJob.Frame) break
            frames++
            newest = newest?.let { maxOf(it, job.receivedNs) } ?: job.receivedNs
        }
        Backlog(frames, newest)
    }

    /** Preserve future config/surface work and the frames belonging to it. */
    fun discardCurrentChain() = synchronized(lock) {
        val iterator = jobs.iterator()
        while (iterator.hasNext()) {
            when (val item = iterator.next()) {
                is VideoJob.Frame -> {
                    iterator.remove()
                    pendingFrames--
                    pendingBytes -= item.nalus.size
                }
                VideoJob.Resync -> iterator.remove()
                else -> return@synchronized
            }
        }
    }

    /** Skip only to an independent picture in this config/surface generation. -1 means none. */
    fun advanceToLatestKeyFrame(codec: VideoCodec): Int = synchronized(lock) {
        var latest = -1
        var position = 0
        for (job in jobs) {
            if (job !is VideoJob.Frame) break
            if (MediaCodecSupport.isRandomAccessFrame(job.nalus, codec)) latest = position
            position++
        }
        if (latest < 0) return@synchronized -1
        repeat(latest) {
            val frame = jobs.removeFirst() as VideoJob.Frame
            pendingFrames--
            pendingBytes -= frame.nalus.size
        }
        latest
    }

    fun offer(job: VideoJob) = synchronized(lock) {
        if (job is VideoJob.Frame) {
            if (pendingFrames >= maxFrames || pendingBytes + job.nalus.size > maxBytes) {
                discardFrames()
                jobs.offer(VideoJob.Resync)
            }
            // An oversized frame is an unusable reference chain, not retained in the queue.
            if (job.nalus.size > maxBytes) {
                lock.notifyAll()
                return@synchronized
            }
            pendingFrames++
            pendingBytes += job.nalus.size
        }
        jobs.offer(job)
        lock.notifyAll()
    }

    fun discardFrames() = synchronized(lock) {
        val iterator = jobs.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item is VideoJob.Frame || item is VideoJob.Resync) iterator.remove()
        }
        pendingFrames = 0
        pendingBytes = 0L
    }

    // Removal and accounting are atomic. timedWait releases the lock so producers can wake us.
    fun poll(timeoutMillis: Long): VideoJob? = synchronized(lock) {
        if (timeoutMillis > 0L && Thread.interrupted()) throw InterruptedException()
        val timeoutNs = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val started = System.nanoTime()
        var remainingNs = timeoutNs
        while (jobs.isEmpty()) {
            if (remainingNs <= 0L) return@synchronized null
            TimeUnit.NANOSECONDS.timedWait(lock, remainingNs)
            remainingNs = timeoutNs - (System.nanoTime() - started)
        }
        val job = jobs.removeFirst()
        if (job is VideoJob.Frame) {
            pendingFrames--
            pendingBytes -= job.nalus.size
        }
        job
    }

    fun drain(): List<VideoJob> = synchronized(lock) {
        val remaining = ArrayList(jobs)
        jobs.clear()
        pendingFrames = 0
        pendingBytes = 0L
        remaining
    }
}

/** Drain output while waiting for input: full output buffers can otherwise starve input forever. */
internal object VideoInputPump {
    fun acquire(
        running: () -> Boolean,
        drain: () -> Unit,
        dequeue: () -> Int,
        nanoTime: () -> Long = System::nanoTime,
        timeoutNs: Long = TimeUnit.MILLISECONDS.toNanos(500),
    ): Int = acquire(running, drain, dequeue, nanoTime, timeoutNs) { attempt ->
        if (attempt > 0) {
            try {
                Thread.sleep(1)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    fun acquire(
        running: () -> Boolean,
        drain: () -> Unit,
        dequeue: () -> Int,
        nanoTime: () -> Long,
        timeoutNs: Long,
        backoff: (attempt: Int) -> Unit,
    ): Int {
        val start = nanoTime()
        var attempt = 0
        while (running()) {
            drain()
            val index = dequeue()
            if (index >= 0) return index
            if (nanoTime() - start >= timeoutNs) break
            backoff(attempt++)
        }
        return -1
    }
}
