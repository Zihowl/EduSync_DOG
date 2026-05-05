package dev.zihowl.dog.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.zihowl.dog.R
import dev.zihowl.dog.data.local.AppDatabase
import dev.zihowl.dog.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val sessionManager = SessionManager(context)
        val db = AppDatabase.getInstance(context, sessionManager.getDbPassphrase())

        CoroutineScope(Dispatchers.IO).launch {
            val subjects = db.subjectDao().getAllList()
            val manualEvents = db.manualEventDao().getAllList()
            val now = Calendar.getInstance()
            val todayDow = now.get(Calendar.DAY_OF_WEEK)
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dayName = when (todayDow) {
                Calendar.MONDAY -> "Lunes"
                Calendar.TUESDAY -> "Martes"
                Calendar.WEDNESDAY -> "Miércoles"
                Calendar.THURSDAY -> "Jueves"
                Calendar.FRIDAY -> "Viernes"
                Calendar.SATURDAY -> "Sábado"
                Calendar.SUNDAY -> "Domingo"
                else -> ""
            }

            val items = mutableListOf<String>()
            for (subject in subjects) {
                val schedule = subject.schedule ?: continue
                for (line in schedule.split("\n")) {
                    if (!line.trim().startsWith(dayName)) continue
                    try {
                        val parts = line.split(" ")
                        val times = line.substring(parts[0].length).trim().split(" - ")
                        items.add("${subject.name}: ${times[0]} - ${times[1]}")
                    } catch (_: Exception) { }
                }
            }
            for (event in manualEvents) {
                val include = when (event.frequencyType) {
                    "RECURRENT" -> event.dayOfWeek == todayDow
                    "UNIQUE" -> {
                        event.date?.let { d ->
                            val c = Calendar.getInstance().apply { time = d }
                            c.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                        } ?: false
                    }
                    else -> false
                }
                if (include) {
                    items.add("${event.title}: ${event.startTime} - ${event.endTime}")
                }
            }

            val displayText = if (items.isEmpty()) {
                "Sin clases hoy"
            } else {
                items.sorted().take(5).joinToString("\n")
            }

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_schedule)
                views.setTextViewText(R.id.widget_schedule_title, "Horario de hoy")
                views.setTextViewText(R.id.widget_schedule_content, displayText)

                val intent = Intent(context, dev.zihowl.dog.ui.main.MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_schedule_container, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
