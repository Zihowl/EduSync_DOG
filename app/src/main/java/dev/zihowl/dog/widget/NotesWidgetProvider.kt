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

class NotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val sessionManager = SessionManager(context)
        val db = AppDatabase.getInstance(context, sessionManager.getDbPassphrase())

        CoroutineScope(Dispatchers.IO).launch {
            val notes = db.noteDao().getAllList()
                .takeLast(5)
                .reversed()

            val displayText = if (notes.isEmpty()) {
                "Sin notas registradas"
            } else {
                notes.joinToString("\n") { "• ${it.title}" }
            }

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_notes)
                views.setTextViewText(R.id.widget_notes_title, "Últimas notas")
                views.setTextViewText(R.id.widget_notes_content, displayText)

                val intent = Intent(context, dev.zihowl.dog.ui.main.MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_notes_container, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
