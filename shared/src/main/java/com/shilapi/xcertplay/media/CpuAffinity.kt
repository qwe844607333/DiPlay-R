package com.shilapi.xcertplay.media

/**
 * Best-effort affinity for latency-sensitive app workers. It can only widen within the
 * process cpuset; Android or vendor firmware may keep a narrower effective mask.
 */
object CpuAffinity {
    private val nativeAvailable = runCatching { System.loadLibrary("xcertplay_cpu") }.isSuccess

    @Volatile private var effectiveCores = 0

    fun useAllCores(): Int {
        if (effectiveCores > 0) return effectiveCores
        if (!nativeAvailable) return 0
        return runCatching { nativeUseAllCores() }
            .getOrDefault(-1)
            .takeIf { it > 0 }
            ?.also { effectiveCores = it }
            ?: 0
    }

    fun diagnosticSummary(): String =
        effectiveCores.takeIf { it > 0 }?.let { "all available cores=$it" }
            ?: "not applied (native library or vendor cpuset restriction)"

    private external fun nativeUseAllCores(): Int
}
