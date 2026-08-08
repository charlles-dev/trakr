package app.trakr.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.trakr.R

class NotificationService(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "trakr_alerts"
        private const val CHANNEL_NAME = "Alertas Trakr"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /** Alerta local quando uma ferramenta sai da maleta. */
    fun showMissingToolAlert(toolName: String) {
        val manager = NotificationManagerCompat.from(context)
        // API 33+ exige permissão runtime POST_NOTIFICATIONS.
        if (!manager.areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_tool_missing, toolName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(toolName.hashCode(), notification)
    }
}