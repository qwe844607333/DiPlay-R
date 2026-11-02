package com.shilapi.xcertplay

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Debug
import java.io.File

/** Export-time context, not a measurement taken during the earlier video stall. No extra permissions. */
internal object PerformanceResourceSnapshot {
    fun report(context: Context): String = buildString {
        appendLine("--- Resources sampled at diagnostic export ---")
        appendLine(runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memory = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(memory)
            "Memory totalMB=${memory.totalMem / MIB} availableMB=${memory.availMem / MIB} " +
                "lowMemory=${memory.lowMemory} thresholdMB=${memory.threshold / MIB}"
        }.getOrElse { "Memory unavailable=${it.javaClass.simpleName}" })
        appendLine(runCatching { "App PSS KB=${Debug.getPss()}" }
            .getOrElse { "App PSS unavailable=${it.javaClass.simpleName}" })
        appendLine(runCatching {
            val displays = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).displays
            "Display declared refresh rates (not measured): " + displays.joinToString { "${it.displayId}:${it.refreshRate}Hz" }
        }.getOrElse { "Display refresh unavailable=${it.javaClass.simpleName}" })
        append(runCatching {
            val fields = File("/proc/meminfo").useLines { lines ->
                lines.take(128).mapNotNull { line -> SWAP_FIELD.matchEntire(line)?.let {
                    "${it.groupValues[1]}KB=${it.groupValues[2]}"
                } }.toList()
            }
            if (fields.isEmpty()) "Swap unavailable=no_readable_fields" else "Swap (device-wide) " + fields.joinToString(" ")
        }.getOrElse { "Swap unavailable=${it.javaClass.simpleName}" })
    }
    private const val MIB = 1024L * 1024L
    private val SWAP_FIELD = Regex("""^(SwapTotal|SwapFree):\s+(\d+)\s+kB\s*$""")
}
