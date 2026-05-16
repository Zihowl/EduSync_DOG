package dev.zihowl.dog.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.Notification
import dev.zihowl.dog.ui.main.MainActivity

object NotificationHelper {
    const val CHANNEL_ID = "class_channel"
    const val CHANNEL_CHANGES = "schedule_changes_channel"

    /** Base de IDs para las notificaciones de cambios de horario. */
    private const val CHANGE_NOTIFICATION_BASE = 2000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val classChannel = NotificationChannel(
                CHANNEL_ID,
                "Recordatorios de clases",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifica 5 minutos antes de cada clase"
            }
            manager.createNotificationChannel(classChannel)

            val changesChannel = NotificationChannel(
                CHANNEL_CHANGES,
                "Cambios en tu horario",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisa cuando cambia el salón, horario o docente de tus materias"
            }
            manager.createNotificationChannel(changesChannel)
        }
    }

    /**
     * Muestra una notificación del sistema para un cambio detectado en el
     * horario oficial (RQF-APP-27/28/29). Al tocarla abre la bandeja in-app.
     */
    fun notifyScheduleChange(context: Context, notification: Notification) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOTIFICATIONS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            CHANGE_NOTIFICATION_BASE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_CHANGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val id = CHANGE_NOTIFICATION_BASE +
            ((notification.subjectName + notification.type).hashCode() and 0xFFFF)
        runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
    }
}
