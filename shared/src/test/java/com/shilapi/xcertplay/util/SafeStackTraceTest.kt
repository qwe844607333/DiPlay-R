package com.shilapi.xcertplay.util

import org.junit.Assert.*
import org.junit.Test

class SafeStackTraceTest {
    @Test fun ordinaryCauseChainKeepsTheOriginalErrorAndFrames() {
        val inner = IllegalArgumentException("original failure")
        val outer = IllegalStateException("connection failed", inner)
        val trace = outer.safeStackTrace()
        assertTrue(trace.contains("java.lang.IllegalStateException: connection failed"))
        assertTrue(trace.contains("Caused by: java.lang.IllegalArgumentException: original failure"))
        assertTrue(trace.contains("\tat "))
        assertFalse(trace.contains("cause chain truncated"))
    }

    @Test fun cyclicCausesStopWithoutRecursion() {
        val outer = IllegalStateException("outer")
        val inner = IllegalArgumentException("inner")
        outer.initCause(inner)
        inner.initCause(outer)
        val trace = outer.safeStackTrace()
        assertTrue(trace.contains("outer"))
        assertTrue(trace.contains("inner"))
        assertTrue(trace.endsWith("[cause chain truncated: cyclic cause]\n"))
    }

    @Test fun deepCauseChainsRespectTheLimit() {
        var error: Throwable = IllegalArgumentException("leaf")
        repeat(64) { error = IllegalStateException("level $it", error) }
        val trace = error.safeStackTrace(maxCauses = 3, maxFramesPerCause = 1)
        assertEquals(2, Regex("Caused by:").findAll(trace).count())
        assertEquals(3, trace.lineSequence().count { it.startsWith("\tat ") })
        assertTrue(trace.endsWith("[cause chain truncated: too deep]\n"))
        assertFalse(trace.contains("leaf"))
    }

    @Test fun excessiveFramesAreBoundedAndTheirOmittedCountIsVisible() {
        val error = IllegalStateException("failure")
        error.stackTrace = Array(5) { StackTraceElement("TestClass", "frame$it", "Test.kt", it + 1) }
        val trace = error.safeStackTrace(maxFramesPerCause = 2)
        assertTrue(trace.contains("frame0"))
        assertTrue(trace.contains("frame1"))
        assertFalse(trace.contains("frame2"))
        assertTrue(trace.contains("\t... 3 more\n"))
    }

    @Test fun throwingGettersCannotBreakTheCrashReporter() {
        val error = object : RuntimeException() {
            override val message: String get() = throw IllegalStateException("broken message getter")
            override val cause: Throwable get() = throw IllegalStateException("broken cause getter")
        }
        val trace = error.safeStackTrace()
        assertTrue(trace.contains(error.javaClass.name))
        assertFalse(trace.contains("broken message getter"))
        assertFalse(trace.contains("broken cause getter"))
    }
}
