package dev.zihowl.dog

import android.app.Application
import dev.zihowl.dog.data.local.AppDatabase
import dev.zihowl.dog.data.repository.DogRepository
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.notifications.ClassNotificationScheduler
import dev.zihowl.dog.notifications.NotificationHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DogApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repositoryDeferred = CompletableDeferred<DogRepository>()

    suspend fun repository(): DogRepository = repositoryDeferred.await()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            val sessionManager = SessionManager(this@DogApplication)
            if (sessionManager.role.isBlank()) {
                sessionManager.role = SessionManager.ROLE_ALUMNO
            }

            val db = AppDatabase.getInstance(this@DogApplication, sessionManager.getDbPassphrase())
            val repo = DogRepository(
                db.subjectDao(),
                db.taskDao(),
                db.noteDao(),
                db.manualEventDao(),
                db.syncQueueDao(),
                syncKeyProvider = { sessionManager.getSyncAesKey() }
            )
            repositoryDeferred.complete(repo)

            NotificationHelper.createNotificationChannel(this@DogApplication)
            ClassNotificationScheduler.scheduleNextClass(this@DogApplication)
        }
    }
}
