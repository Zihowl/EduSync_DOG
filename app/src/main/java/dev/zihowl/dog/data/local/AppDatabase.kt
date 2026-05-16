package dev.zihowl.dog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.zihowl.dog.data.model.ManualEvent
import dev.zihowl.dog.data.model.Note
import dev.zihowl.dog.data.model.Notification
import dev.zihowl.dog.data.model.Subject
import dev.zihowl.dog.data.model.SyncQueueItem
import dev.zihowl.dog.data.model.Task
import net.sqlcipher.database.SupportFactory

@Database(entities = [Subject::class, Task::class, Note::class, ManualEvent::class, SyncQueueItem::class, Notification::class], version = 9, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun manualEventDao(): ManualEventDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v8: materias oficiales (RQF-APP-36). Preserva tareas/notas locales. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subjects ADD COLUMN isOfficial INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subjects ADD COLUMN sourceGroupId INTEGER")
            }
        }

        /**
         * v9: bandeja de notificaciones (RQF-APP-27/28/29) y salón de las
         * materias oficiales. Preserva materias, tareas y notas locales.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subjects ADD COLUMN classroom TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS notifications (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "owner TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "body TEXT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "subjectName TEXT NOT NULL, " +
                        "newValue TEXT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "isRead INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        fun getInstance(context: Context, passphrase: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = SupportFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dog_database.db"
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
