package com.trailcam.mesh.ble

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.trailcam.mesh.MainActivity
import com.trailcam.mesh.R
import com.trailcam.mesh.data.MotionAlert

/**
 * Foreground service for maintaining BLE connection
 */
class BleService : Service() {
    
    companion object {
        const val CHANNEL_ID = "trail_cam_channel"
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
    }
    
    private val binder = LocalBinder()
    lateinit var bleManager: BleManager
        private set
    
    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }
    
    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(this)
        createNotificationChannel()
        
        // Set up motion alert callback
        bleManager.onMotionAlert = { alert ->
            showMotionAlertNotification(alert)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createServiceNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onDestroy() {
        bleManager.disconnect()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createServiceNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Trail Cam Mesh")
            .setContentText("Monitoring for motion alerts...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun showMotionAlertNotification(alert: MotionAlert) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Motion Detected!")
            .setContentText("Camera ${alert.nodeId} detected movement" + 
                if (alert.hasImage) " - Image captured" else "")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(ALERT_NOTIFICATION_ID + alert.nodeId, notification)
    }
}


