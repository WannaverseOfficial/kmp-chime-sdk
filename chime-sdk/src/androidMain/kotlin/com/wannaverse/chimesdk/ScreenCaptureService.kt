package com.wannaverse.chimesdk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

@RequiresApi(Build.VERSION_CODES.Q)
class ScreenCaptureService : Service() {
    private lateinit var notificationManager: NotificationManager

    private val channelId = "ScreenCaptureServiceChannelID"
    private val channelName = "Screen Share"
    private val serviceId = 1

    private val binder = ScreenCaptureBinder()

    class ScreenCaptureBinder : Binder()

    override fun onCreate() {
        super.onCreate()

        notificationManager =
            applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        startForeground(
            serviceId,
            NotificationCompat.Builder(this, channelId)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
