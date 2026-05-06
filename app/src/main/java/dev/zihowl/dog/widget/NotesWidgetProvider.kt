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
import dev.zihowl.dog.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sessionManager = SessionManager(context)

                val displayText = if (!sessionManager.isLoggedIn && !sessionManager.isGuestMode) {
                    "Inicia sesión para ver tus notas"
                } else {
                    val db = AppDatabase.getInstance(context, sessionManager.getDbPassphrase())
                    val notes = db.noteDao().getAllForOwner(sessionManager.username)
                        .takeLast(5)
                        .reversed()

                    if (notes.isEmpty()) {
                        "Sin notas registradas"
                    } else {
                        notes.joinToString("\n") { "• ${it.title}" }
                    }
                }

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_notes)
                    views.setTextViewText(R.id.widget_notes_title, "Últimas notas")
                    views.setTextViewText(R.id.widget_notes_content, displayText)

                    val intent = Intent(context, dev.zihowl.dog.ui.main.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(dev.zihowl.dog.ui.main.MainActivity.EXTRA_OPEN_TAB, dev.zihowl.dog.ui.main.MainActivity.TAB_NOTES)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context, dev.zihowl.dog.ui.main.MainActivity.TAB_NOTES, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_notes_container, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                Log.e("NotesWidget", "Error updating widget", e)
                try {
                    for (appWidgetId in appWidgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.widget_notes)
                        views.setTextViewText(R.id.widget_notes_title, "Últimas notas")
                        views.setTextViewText(R.id.widget_notes_content, "Error al cargar notas")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e2: Exception) {
                    Log.e("NotesWidget", "Error updating widget with error message", e2)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
