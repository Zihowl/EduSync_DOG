package dev.zihowl.dog.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.zihowl.dog.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClassAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val subject = intent.getStringExtra("subjectName") ?: "Clase"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Próxima clase")
                    .setContentText("En 5 minutos comienza $subject")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)

                NotificationManagerCompat.from(context).notify(1001, builder.build())
                ClassNotificationScheduler.scheduleNextClass(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
