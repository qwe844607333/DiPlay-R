package com.shilapi.xcertplay

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.ResultReceiver
import java.util.UUID

/** Runs in the app's dedicated :btprobe process, so a stuck vendor call can be discarded. */
@SuppressLint("MissingPermission")
class BluetoothProbeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        @Suppress("DEPRECATION")
        val receiver = intent?.getParcelableExtra<ResultReceiver>("receiver") ?: return START_NOT_STICKY
        val address = intent.getStringExtra("address") ?: return START_NOT_STICKY
        val secure = intent.getBooleanExtra("secure", true)
        fun send(code: Int, text: String) = receiver.send(code, Bundle().apply {
            putInt("pid", Process.myPid()); putString("text", text)
        })
        send(1, "process-ready")
        Thread({
            var socket: BluetoothSocket? = null
            var result = "no result"
            try {
                // Context.getSystemService(Class) 需要 API 23；4.4 线走字符串常量。
                val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    getSystemService(BluetoothManager::class.java)?.adapter
                } else {
                    @Suppress("DEPRECATION")
                    (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                } ?: error("Android Bluetooth adapter absent")
                val peer = adapter.getRemoteDevice(address)
                send(1, "socket-create")
                val uuid = UUID.fromString("00000000-deca-fade-deca-deafdecacafe")
                val created = if (secure) peer.createRfcommSocketToServiceRecord(uuid)
                    else peer.createInsecureRfcommSocketToServiceRecord(uuid)
                socket = created
                send(1, "socket-connect")
                created.connect()
                result = "connected; no protocol data sent"
                send(3, result)
            } catch (error: Exception) {
                result = "${error.javaClass.simpleName}: ${error.message}"
                send(3, result)
            } finally {
                send(1, "socket-close")
                runCatching { socket?.close() }
                send(2, result)
                stopSelf(startId)
            }
        }, "bluetooth-probe").start()
        return START_NOT_STICKY
    }
}
