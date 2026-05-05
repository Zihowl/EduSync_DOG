package dev.zihowl.dog

import android.app.Application
import dev.zihowl.dog.data.local.AppDatabase
import dev.zihowl.dog.data.repository.DogRepository
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.notifications.ClassNotificationScheduler
import dev.zihowl.dog.notifications.NotificationHelper

class DogApplication : Application() {

    lateinit var repository: DogRepository
        private set

    override fun onCreate() {
        super.onCreate()

        val sessionManager = SessionManager(this)
        sessionManager.role = "alumno"

        val db = AppDatabase.getInstance(this, sessionManager.getDbPassphrase())
        repository = DogRepository(
            db.subjectDao(),
            db.taskDao(),
            db.noteDao(),
            db.manualEventDao()
        )

        NotificationHelper.createNotificationChannel(this)
        ClassNotificationScheduler.scheduleNextClass(this)
    }
}
