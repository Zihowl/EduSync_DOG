package dev.zihowl.dog.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import dev.zihowl.dog.R
import dev.zihowl.dog.data.local.AppDatabase
import dev.zihowl.dog.data.model.Task
import dev.zihowl.dog.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TasksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sessionManager = SessionManager(context)

                val displayText = if (!sessionManager.isLoggedIn && !sessionManager.isGuestMode) {
                    "Inicia sesión para ver tus tareas"
                } else {
                    val db = AppDatabase.getInstance(context, sessionManager.getDbPassphrase())
                    val tasks = db.taskDao().getAllForOwner(sessionManager.username)
                        .filter { it.status == Task.STATUS_PENDING }
                        .sortedWith(compareBy(
                            { when (it.priority) {
                                Task.PRIORITY_HIGH -> 0
                                Task.PRIORITY_MEDIUM -> 1
                                Task.PRIORITY_LOW -> 2
                                else -> 3
                            }},
                            { it.dueDate ?: java.util.Date(Long.MAX_VALUE) }
                        ))
                        .take(5)

                    if (tasks.isEmpty()) {
                        "Sin tareas pendientes"
                    } else {
                        tasks.joinToString("\n") { "• ${it.title}" }
                    }
                }

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_tasks)
                    views.setTextViewText(R.id.widget_tasks_title, "Tareas pendientes")
                    views.setTextViewText(R.id.widget_tasks_content, displayText)

                    val intent = Intent(context, dev.zihowl.dog.ui.main.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(dev.zihowl.dog.ui.main.MainActivity.EXTRA_OPEN_TAB, dev.zihowl.dog.ui.main.MainActivity.TAB_TASKS)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context, dev.zihowl.dog.ui.main.MainActivity.TAB_TASKS, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_tasks_container, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                Log.e("TasksWidget", "Error updating widget", e)
                try {
                    for (appWidgetId in appWidgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.widget_tasks)
                        views.setTextViewText(R.id.widget_tasks_title, "Tareas pendientes")
                        views.setTextViewText(R.id.widget_tasks_content, "Error al cargar tareas")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e2: Exception) {
                    Log.e("TasksWidget", "Error updating widget with error message", e2)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
