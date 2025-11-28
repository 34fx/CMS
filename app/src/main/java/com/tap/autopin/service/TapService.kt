package com.tap.autopin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tap.autopin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

class TapService : Service() {

    private val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restart if the system kills the service
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Restart the service if the task is removed
        start(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        // Attempt to restart to remain resident
        start(applicationContext)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startMonitoring() {
        monitorJob = serviceScope.launch {
            var targetWasRunning = false
            while (isActive) {
                val isRunning = isTargetAppRunning()
                if (isRunning && !targetWasRunning) {
                    Log.i(TAG, "Target detected. Preparing to simulate tap in $TRIGGER_DELAY_MS ms")
                    delay(TRIGGER_DELAY_MS)
                    if (isTargetAppRunning()) {
                        simulateCenterTap()
                    }
                    targetWasRunning = true
                } else if (!isRunning) {
                    targetWasRunning = false
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun isTargetAppRunning(): Boolean {
        val command = "pidof $TARGET_PACKAGE"
        return runShellCommand(command).second.isNotBlank()
    }

    private fun simulateCenterTap() {
        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val x = displayMetrics.widthPixels / 2
        val y = displayMetrics.heightPixels / 2
        val tapCommand = "input tap $x $y"
        val (exitCode, output) = runShellCommand(tapCommand)
        if (exitCode == 0) {
            Log.i(TAG, "Simulated tap at ($x,$y)")
        } else {
            Log.w(TAG, "Failed to simulate tap. Exit code=$exitCode Output=$output")
        }
    }

    private fun runShellCommand(command: String): Pair<Int, String> {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(process.outputStream).use { outputStream ->
                outputStream.writeBytes(command + "\n")
                outputStream.writeBytes("exit\n")
                outputStream.flush()
            }

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                buildString {
                    var line = reader.readLine()
                    while (line != null) {
                        appendLine(line)
                        line = reader.readLine()
                    }
                }
            }.trim()

            val exitCode = process.waitFor()
            Pair(exitCode, output)
        } catch (e: Exception) {
            Log.e(TAG, "Shell command failed", e)
            Pair(-1, e.message ?: "")
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = getString(R.string.notification_channel_description)
                    setShowBadge(false)
                    enableVibration(false)
                    enableLights(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "tap_automation_channel"
        private const val NOTIFICATION_ID = 101
        private const val TAG = "TapService"
        private const val TARGET_PACKAGE = "com.bat.vuseapp"
        private const val TRIGGER_DELAY_MS = 10_000L
        private const val CHECK_INTERVAL_MS = 3_000L

        fun start(context: Context) {
            val intent = Intent(context, TapService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
