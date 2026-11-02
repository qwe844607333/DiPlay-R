package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

internal class BluetoothSystemCollector(private val context: Context, private val emit: (String) -> Unit) {
    private var adb: LocalAdb? = null
    private var access: LocalAdb.Access? = null

    fun prepare(allowApproval: Boolean) {
        bounded("ADB access", if (allowApproval) 65 else 8) {
            val client = LocalAdb(AdbKeys.load(context))
            adb = client
            access = client.connect(mayAsk = allowApproval)
            emit("System log access via local ADB=$access; endpoint=loopback:5555; Android wireless-debugging TLS is not supported")
            if (access != LocalAdb.Access.READY) emit("系统日志采集未获授权或本机 ADB 不可达：需网络 ADB 5555 和车机授权；Android 11 无线调试配对模式不受此客户端支持。普通 App 日志不能替代系统蓝牙日志。")
        }
    }

    fun snapshot(label: String, since: String) {
        emit("--- System evidence $label ---")
        emit("Evidence scope=Bluetooth logcat and dumpsys; HCI packet capture is not enabled or collected by this App")
        if (access == LocalAdb.Access.READY) {
            bounded("Bluetooth system state", 12) {
                show(adb?.shell("timeout 8 dumpsys bluetooth_manager"), false)
            }
            bounded("System Bluetooth logcat", 12) {
                show(adb?.shell("timeout 8 logcat -d -b main -b system -b crash -v threadtime -T '$since' -t 600"), true)
            }
        } else {
            emit("Logcat scope=app UID only or denied; system evidence unavailable")
            bounded("App logcat", 10) {
                val process = ProcessBuilder("/system/bin/logcat", "-d", "-b", "main", "-v", "threadtime", "-t", "300").redirectErrorStream(true).start()
                try {
                    val text = process.inputStream.bufferedReader().use { it.readText().take(200000) }
                    show(text, true)
                } finally { process.destroy() }
            }
        }
    }

    private fun show(text: String?, log: Boolean) {
        if (text == null) { emit("Evidence command failed or timed out; no usable output"); return }
        val relevant = Regex("(?i)bluetooth|bt_stack|btif|bta_|rfcomm|sdp|l2cap|hci|avrcp|a2dp|permission denial|permission denied|not found|timed out")
        val privateData = Regex("(?i)link.?key|\\bltk\\b|\\birk\\b|pin.?code|packet|payload|eir.?data")
        val values = text.lineSequence().filter { relevant.containsMatchIn(it) && !privateData.containsMatchIn(it) }.take(180).toList()
        emit("Filtered ${if (log) "logcat" else "dumpsys"} lines=${values.size}; absence of lines is inconclusive")
        values.forEach { emit(it) }
    }

    private fun bounded(label: String, seconds: Long, action: () -> Unit) {
        val task = FutureTask { action() }
        Thread(task, "bluetooth-evidence").apply { isDaemon = true; start() }
        try { task.get(seconds, TimeUnit.SECONDS) }
        catch (error: Exception) {
            task.cancel(true)
            access = null
            emit("$label unavailable: ${error.javaClass.simpleName}; collection deadline=${seconds}s")
        }
    }

    fun close() {
        Thread({ runCatching { adb?.close() } }, "bluetooth-evidence-close").apply { isDaemon = true; start() }
    }
}
