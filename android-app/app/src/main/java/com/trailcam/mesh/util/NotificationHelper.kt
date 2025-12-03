package com.trailcam.mesh.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.trailcam.mesh.MainActivity
import com.trailcam.mesh.data.MotionAlert
import com.trailcam.mesh.data.NodeNameManager

object NotificationHelper {
    
    private const val CHANNEL_ID = "trail_cam_alerts"
    private const val CHANNEL_NAME = "Motion Alerts"
    private var isChannelCreated = false
    
    fun initialize(context: Context) {
        if (isChannelCreated) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when trail cameras detect motion"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                enableLights(true)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        isChannelCreated = true
    }
    
    fun showMotionAlert(context: Context, alert: MotionAlert) {
        initialize(context)
        
        // Get custom name if available
        val nodeNameManager = NodeNameManager(context)
        val nodeName = nodeNameManager.getNodeName(alert.nodeId)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            // Use SINGLE_TOP to bring existing activity to front without recreating
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("tab", 1) // Navigate to Alerts tab
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.nodeId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🦌 Motion Detected!")
            .setContentText("$nodeName detected movement")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$nodeName detected movement" +
                    if (alert.hasImage) "\n📷 Image captured - tap to view" else ""))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use unique ID per alert so multiple alerts don't replace each other
        val notificationId = (alert.nodeId * 1000 + (alert.receivedAt % 1000)).toInt()
        notificationManager.notify(notificationId, notification)
    }
    
    fun cancelAll(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }
}
