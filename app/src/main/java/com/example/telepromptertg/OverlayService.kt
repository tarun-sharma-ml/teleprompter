package com.example.telepromptertg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast

/** Foreground service that keeps the floating teleprompter overlay on screen. */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.example.telepromptertg.START"
        const val ACTION_STOP = "com.example.telepromptertg.STOP"
        const val ACTION_RELOAD = "com.example.telepromptertg.RELOAD"
        private const val CHANNEL = "teleprompter"
        private const val NOTIF_ID = 1

        @Volatile
        var running = false
    }

    private var overlay: Overlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                // The app saved new settings: apply them to the panel that is already on screen.
                val o = overlay
                if (o == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val prefs = Prefs(this)
                o.setScript(prefs.text)
                o.setWpm(prefs.wpm)
                o.setSize(prefs.size)
                return START_STICKY
            }
        }
        startAsForeground()

        if (overlay == null) {
            val prefs = Prefs(this)
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    applicationContext,
                    "Allow \"Display over other apps\" for this app first.",
                    Toast.LENGTH_LONG,
                ).show()
                stopSelf()
                return START_NOT_STICKY
            }
            val o = Overlay(this, prefs)
            o.show()
            overlay = o
            running = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        overlay?.hide()
        overlay = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Teleprompter", NotificationManager.IMPORTANCE_LOW),
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stopPi = PendingIntent.getService(
            this, 0, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), flags,
        )
        val openPi = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), flags)
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Stop",
            stopPi,
        ).build()
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Teleprompter is active")
            .setContentText("Open your camera app — the floating script is on screen.")
            .setContentIntent(openPi)
            .addAction(stopAction)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }
}