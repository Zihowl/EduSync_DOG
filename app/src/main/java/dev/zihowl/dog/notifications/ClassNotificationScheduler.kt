package dev.zihowl.dog.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.zihowl.dog.DogApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ClassNotificationScheduler {

    private val DAYS = mapOf(
        "Lunes" to Calendar.MONDAY,
        "Martes" to Calendar.TUESDAY,
        "Miércoles" to Calendar.WEDNESDAY,
        "Miercoles" to Calendar.WEDNESDAY,
        "Jueves" to Calendar.THURSDAY,
        "Viernes" to Calendar.FRIDAY,
        "Sábado" to Calendar.SATURDAY,
        "Sabado" to Calendar.SATURDAY,
        "Domingo" to Calendar.SUNDAY
    )

    fun scheduleNextClass(context: Context) {
        val app = context.applicationContext as DogApplication
        CoroutineScope(Dispatchers.IO).launch {
            val subjects = app.repository().getAllSubjectsList()
            if (subjects.isEmpty()) return@launch

            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val now = Calendar.getInstance()
            val nowMillis = now.timeInMillis
            var bestTime = Long.MAX_VALUE
            var bestSubject: String? = null

            for (s in subjects) {
                val schedule = s.schedule ?: continue
                for (line in schedule.split("\n")) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    val parts = trimmed.split(" ", limit = 2)
                    if (parts.size < 2) continue
                    val day = DAYS[parts[0]] ?: continue
                    val times = parts[1].trim().split(" - ")
                    if (times.size != 2) continue
                    try {
                        val start = sdf.parse(times[0]) ?: continue
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.DAY_OF_WEEK, day)
                            set(Calendar.HOUR_OF_DAY, start.hours)
                            set(Calendar.MINUTE, start.minutes)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        if (cal.timeInMillis <= nowMillis) {
                            cal.add(Calendar.WEEK_OF_YEAR, 1)
                        }
                        val trigger = cal.timeInMillis - 5 * 60 * 1000
                        if (trigger > nowMillis && trigger < bestTime) {
                            bestTime = trigger
                            bestSubject = s.name
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            if (bestSubject != null) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val i = Intent(context, ClassAlarmReceiver::class.java).apply {
                    putExtra("subjectName", bestSubject)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    1001,
                    i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (am.canScheduleExactAlarms()) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bestTime, pi)
                        } else {
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bestTime, pi)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bestTime, pi)
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, bestTime, pi)
                    }
                } catch (_: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bestTime, pi)
                }
            }
        }
    }
}
