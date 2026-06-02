package chat.sphinx.feature_service_restore_android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import chat.sphinx.resources.R as R_common

internal class RestoreNotification(
    private val context: Context
) {

    companion object {
        internal const val NOTIFICATION_ID = 2025
        private const val CHANNEL_ID = "SphinxRestoreService"
        private const val CHANNEL_DESCRIPTION = "Shows account restore progress for Sphinx Chat"

        internal const val ACTION_CANCEL_RESTORE = "chat.sphinx.feature_service_restore_android.ACTION_CANCEL_RESTORE"
        private const val ACTION_CANCEL_CODE = 101
    }

    private val notificationManager: NotificationManager?
        get() = context.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        setupNotificationChannel()
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_ID,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setSound(null, null)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun buildInitialNotification(): android.app.Notification {
        return buildNotificationBuilder(0).build()
    }

    fun updateNotification(progress: Int) {
        val notification = buildNotificationBuilder(progress).build()
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotificationBuilder(progress: Int): NotificationCompat.Builder {
        val contentText = when {
            progress <= 10 -> "Restoring contacts… $progress%"
            else -> "Restoring messages… $progress%"
        }

        val cancelIntent = Intent(ACTION_CANCEL_RESTORE).apply {
            setPackage(context.packageName)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            ACTION_CANCEL_CODE,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context.applicationContext, CHANNEL_ID)
            .setContentTitle("Restoring Account")
            .setContentText(contentText)
            .setSmallIcon(R_common.drawable.sphinx_white_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(null)
            .setProgress(100, progress, false)
            .addAction(0, "Cancel", cancelPendingIntent)
    }

    fun clear() {
        notificationManager?.cancel(NOTIFICATION_ID)
    }
}
