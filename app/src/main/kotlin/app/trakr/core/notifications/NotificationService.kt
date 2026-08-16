package app.trakr.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.trakr.MainActivity
import app.trakr.R

class NotificationService(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "trakr_alerts"
        private const val CHANNEL_SILENT = "trakr_alerts_silent"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val defaultChannel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_alerts),
                    NotificationManager.IMPORTANCE_HIGH,
                )
            val silentChannel =
                NotificationChannel(
                    CHANNEL_SILENT,
                    context.getString(R.string.notification_channel_alerts) + " (Silencioso)",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
            nm.createNotificationChannel(defaultChannel)
            nm.createNotificationChannel(silentChannel)
        }
    }

    /** Alerta local quando uma ferramenta não é encontrada (toque abre o detalhe). */
    fun showMissingToolAlert(
        toolName: String,
        toolId: String,
        setting: app.trakr.model.ToolAlertSetting? = null,
    ) {
        if (setting?.muted == true) return
        val manager = NotificationManagerCompat.from(context)
        // API 33+ exige permissão runtime POST_NOTIFICATIONS.
        if (!manager.areNotificationsEnabled()) return

        val openTool =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_TARGET_ID, toolId)
        val pending =
            PendingIntent.getActivity(
                context,
                toolId.hashCode(),
                openTool,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val useSilentChannel = setting?.sound == "silent" || setting?.importance?.let { it <= 2 } == true
        val channelId = if (useSilentChannel) CHANNEL_SILENT else CHANNEL_ID

        val builder =
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notification_tool_missing, toolName))
                .setPriority(if (useSilentChannel) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending)

        // Vibração configurável por ferramenta
        if (setting != null && !setting.vibration) {
            builder.setVibrate(null)
        } else {
            // Padrão: vibração curta para ausência
            builder.setVibrate(longArrayOf(0, 300, 200, 300))
        }

        // Sons diferentes por tipo (placeholder: default vs silent já tratado por canal)
        if (setting?.sound == "beep_long") {
            builder.setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))
        }

        val notification = builder.build()

        manager.notify(toolId.hashCode(), notification)
    }
}
