package dev.zihowl.dog.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import dev.zihowl.dog.data.local.ManualEventDao
import dev.zihowl.dog.data.local.NoteDao
import dev.zihowl.dog.data.local.NotificationDao
import dev.zihowl.dog.data.local.SharedTaskDao
import dev.zihowl.dog.data.local.SubjectDao
import dev.zihowl.dog.data.local.SyncQueueDao
import dev.zihowl.dog.data.local.TaskDao
import dev.zihowl.dog.data.model.ManualEvent
import dev.zihowl.dog.data.model.Note
import dev.zihowl.dog.data.model.Notification
import dev.zihowl.dog.data.model.SharedTaskInbox
import dev.zihowl.dog.data.model.Subject
import dev.zihowl.dog.data.model.SyncQueueItem
import dev.zihowl.dog.data.model.Task
import dev.zihowl.dog.utils.Aes256Crypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DogRepository(
    private val subjectDao: SubjectDao,
    private val taskDao: TaskDao,
    private val noteDao: NoteDao,
    private val manualEventDao: ManualEventDao,
    private val syncQueueDao: SyncQueueDao,
    private val notificationDao: NotificationDao,
    private val sharedTaskDao: SharedTaskDao,
    private val syncKeyProvider: (() -> ByteArray)? = null,
    initialOwner: String = ""
) {
    /**
     * Propietario activo de los datos. Los LiveData expuestos a la UI se
     * recalculan automáticamente cuando cambia (cambio de cuenta / logout).
     */
    private val activeOwner = MutableLiveData(initialOwner)

    /**
     * Serializa las re-sincronizaciones del horario oficial. [syncOfficialSubjects]
     * borra y reinserta las materias oficiales en pasos separados (no es una
     * transacción atómica); si dos sincronizaciones se solapan —p. ej. el guardado
     * de "Mi grupo y materias" y el re-sync que dispara MainActivity al pasar el
     * estado a "Sincronizado"— sus borrados e inserciones se intercalan y las
     * materias quedan duplicadas. Este mutex obliga a que se ejecuten una tras otra.
     */
    private val officialSyncMutex = Mutex()

    /** Fija qué cuenta ve la UI. Debe llamarse al iniciar sesión o en MainActivity. */
    fun setActiveOwner(owner: String) {
        if (activeOwner.value != owner) activeOwner.postValue(owner)
    }

    fun currentActiveOwner(): String = activeOwner.value ?: ""

    private fun enc(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        val key = syncKeyProvider?.invoke() ?: return value
        return Aes256Crypto.encrypt(value, key) ?: value
    }

    private fun enqueue(
        entityType: String,
        entityId: Int,
        operation: String,
        owner: String,
        build: JSONObject.() -> Unit
    ) {
        val payload = JSONObject().apply {
            put("id", entityId)
            put("encrypted", syncKeyProvider != null)
            build()
        }
        syncQueueDao.insert(
            SyncQueueItem(
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                payloadJson = payload.toString(),
                owner = owner
            )
        )
    }

    val allSubjects: LiveData<List<Subject>> =
        activeOwner.switchMap { subjectDao.getAllForOwnerLive(it) }
    val allTasks: LiveData<List<Task>> =
        activeOwner.switchMap { taskDao.getAllForOwnerLive(it) }
    val allNotes: LiveData<List<Note>> =
        activeOwner.switchMap { noteDao.getAllForOwnerLive(it) }

    suspend fun subjectExists(name: String): Boolean {
        return withContext(Dispatchers.IO) {
            subjectDao.exists(name)
        }
    }

    suspend fun addSubject(name: String, professorName: String, schedule: String, owner: String = ""): Boolean {
        return withContext(Dispatchers.IO) {
            if (subjectDao.exists(name)) {
                false
            } else {
                subjectDao.insert(
                    Subject(
                        name = name,
                        professorName = professorName,
                        schedule = schedule,
                        owner = owner
                    )
                )
                true
            }
        }
    }

    suspend fun updateSubject(subject: Subject, newName: String, newProfessorName: String, newSchedule: String) {
        withContext(Dispatchers.IO) {
            val oldName = subject.name
            val updated = subject.copy(
                name = newName,
                professorName = newProfessorName,
                schedule = newSchedule
            )
            subjectDao.update(updated)
            if (oldName != newName) {
                taskDao.getBySubjectName(oldName).forEach { task ->
                    taskDao.update(task.copy(subjectName = newName))
                }
                noteDao.getBySubjectName(oldName).forEach { note ->
                    noteDao.update(note.copy(subjectName = newName))
                }
            }
        }
    }

    suspend fun deleteSubjects(subjects: List<Subject>) {
        withContext(Dispatchers.IO) {
            subjects.forEach { subjectDao.delete(it) }
        }
    }

    suspend fun disassociateAndDeleteSubject(subjectId: Int) {
        withContext(Dispatchers.IO) {
            val subject = subjectDao.getById(subjectId) ?: return@withContext
            taskDao.getBySubjectName(subject.name).forEach { task ->
                taskDao.update(task.copy(subjectName = null))
            }
            noteDao.getBySubjectName(subject.name).forEach { note ->
                noteDao.update(note.copy(subjectName = null))
            }
            subjectDao.delete(subject)
        }
    }

    suspend fun deleteSubjectWithContent(subjectId: Int) {
        withContext(Dispatchers.IO) {
            val subject = subjectDao.getById(subjectId) ?: return@withContext
            val tasks = taskDao.getBySubjectName(subject.name)
            if (tasks.isNotEmpty()) taskDao.deleteAll(tasks)
            val notes = noteDao.getBySubjectName(subject.name)
            if (notes.isNotEmpty()) noteDao.deleteAll(notes)
            subjectDao.delete(subject)
        }
    }

    suspend fun disassociateAndDeleteSubjects(subjectIds: List<Int>) {
        withContext(Dispatchers.IO) {
            subjectIds.forEach { subjectId ->
                val subject = subjectDao.getById(subjectId) ?: return@forEach
                taskDao.getBySubjectName(subject.name).forEach { task ->
                    taskDao.update(task.copy(subjectName = null))
                }
                noteDao.getBySubjectName(subject.name).forEach { note ->
                    noteDao.update(note.copy(subjectName = null))
                }
                subjectDao.delete(subject)
            }
        }
    }

    suspend fun deleteSubjectsWithContent(subjectIds: List<Int>) {
        withContext(Dispatchers.IO) {
            subjectIds.forEach { subjectId ->
                val subject = subjectDao.getById(subjectId) ?: return@forEach
                val tasks = taskDao.getBySubjectName(subject.name)
                if (tasks.isNotEmpty()) taskDao.deleteAll(tasks)
                val notes = noteDao.getBySubjectName(subject.name)
                if (notes.isNotEmpty()) noteDao.deleteAll(notes)
                subjectDao.delete(subject)
            }
        }
    }

    /**
     * Reemplaza las materias oficiales locales con las derivadas de los
     * bloques de horario publicados [slots]. Las materias cuyo nombre esté en
     * [discardedNames] (adelantadas por el alumno) se omiten. Si ya existe una
     * materia manual con el mismo nombre, se conserva la manual y se omite la
     * oficial para no pisar el contenido del usuario.
     *
     * @return nombres de materias oficiales que colisionaron con materias
     *   manuales (no se materializaron).
     */
    suspend fun syncOfficialSubjects(
        slots: List<dev.zihowl.dog.data.remote.CatalogClient.RemoteSlot>,
        discardedNames: Set<String>,
        owner: String,
        weekDays: List<String>
    ): List<String> {
        return officialSyncMutex.withLock {
            withContext(Dispatchers.IO) {
            val collisions = mutableListOf<String>()
            subjectDao.deleteAllOfficialForOwner(owner)

            val bySubject = slots
                .filter { it.subjectName !in discardedNames }
                .groupBy { it.subjectName }

            for ((name, subjectSlots) in bySubject) {
                val existing = subjectDao.getByNameForOwner(name, owner)
                if (existing != null && !existing.isOfficial) {
                    collisions.add(name)
                    continue
                }
                val schedule = subjectSlots
                    .distinctBy { listOf(it.dayOfWeek, it.startTime, it.endTime) }
                    .sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
                    .joinToString("\n") { slot ->
                        val day = weekDays.getOrNull(slot.dayOfWeek - 1) ?: ""
                        val start = slot.startTime.take(5)
                        val end = slot.endTime.take(5)
                        "$day $start - $end"
                    }
                val professor = subjectSlots
                    .firstNotNullOfOrNull { it.teacherName }
                val classroom = subjectSlots
                    .firstNotNullOfOrNull { it.classroomName }
                subjectDao.insert(
                    Subject(
                        name = name,
                        professorName = professor,
                        schedule = schedule,
                        owner = owner,
                        isOfficial = true,
                        sourceGroupId = subjectSlots.first().groupId,
                        classroom = classroom
                    )
                )
            }
            collisions
            }
        }
    }

    suspend fun getTasksForSubject(subjectName: String): List<Task> {
        return withContext(Dispatchers.IO) {
            taskDao.getBySubjectName(subjectName)
        }
    }

    suspend fun getNotesForSubject(subjectName: String): List<Note> {
        return withContext(Dispatchers.IO) {
            noteDao.getBySubjectName(subjectName)
        }
    }

    private fun enqueueTask(task: Task, op: String) {
        enqueue(SyncQueueItem.TYPE_TASK, task.id, op, task.owner) {
            put("title", task.title)
            put("description", enc(task.description))
            put("dueDate", task.dueDate?.time)
            put("status", task.status)
            put("subjectName", task.subjectName)
            put("priority", task.priority)
        }
    }

    suspend fun addTask(task: Task) {
        withContext(Dispatchers.IO) {
            val rowId = taskDao.insert(task)
            val saved = if (task.id == 0) task.copy(id = rowId.toInt()) else task
            enqueueTask(saved, SyncQueueItem.OP_INSERT)
        }
    }

    suspend fun updateTask(task: Task) {
        withContext(Dispatchers.IO) {
            taskDao.update(task)
            enqueueTask(task, SyncQueueItem.OP_UPDATE)
        }
    }

    suspend fun deleteTasks(tasks: List<Task>) {
        withContext(Dispatchers.IO) {
            taskDao.deleteAll(tasks)
            tasks.forEach { enqueueTask(it, SyncQueueItem.OP_DELETE) }
        }
    }

    private fun enqueueNote(note: Note, op: String) {
        enqueue(SyncQueueItem.TYPE_NOTE, note.id, op, note.owner) {
            put("title", note.title)
            put("content", enc(note.content))
            put("subjectName", note.subjectName)
            put("attachmentName", enc(note.attachmentName))
            put("attachmentSize", note.attachmentSize)
        }
    }

    suspend fun addNote(note: Note) {
        withContext(Dispatchers.IO) {
            val rowId = noteDao.insert(note)
            val saved = if (note.id == 0) note.copy(id = rowId.toInt()) else note
            enqueueNote(saved, SyncQueueItem.OP_INSERT)
        }
    }

    suspend fun updateNote(note: Note) {
        withContext(Dispatchers.IO) {
            noteDao.update(note)
            enqueueNote(note, SyncQueueItem.OP_UPDATE)
        }
    }

    suspend fun deleteNotes(notes: List<Note>) {
        withContext(Dispatchers.IO) {
            noteDao.deleteAll(notes)
            notes.forEach { enqueueNote(it, SyncQueueItem.OP_DELETE) }
        }
    }

    suspend fun getAllSubjectsList(owner: String = ""): List<Subject> {
        return withContext(Dispatchers.IO) {
            if (owner.isBlank()) subjectDao.getAllList() else subjectDao.getAllForOwner(owner)
        }
    }

    suspend fun getAllTasksList(owner: String = ""): List<Task> {
        return withContext(Dispatchers.IO) {
            if (owner.isBlank()) taskDao.getAllList() else taskDao.getAllForOwner(owner)
        }
    }

    suspend fun getAllNotesList(owner: String = ""): List<Note> {
        return withContext(Dispatchers.IO) {
            if (owner.isBlank()) noteDao.getAllList() else noteDao.getAllForOwner(owner)
        }
    }

    suspend fun recalculateSubjectCounters(subjectName: String) {
        withContext(Dispatchers.IO) {
            val subject = subjectDao.getByName(subjectName) ?: return@withContext
            val pendingCount = taskDao.getBySubjectName(subjectName).count { it.status == Task.STATUS_PENDING }
            val notesCount = noteDao.getBySubjectName(subjectName).size
            subjectDao.update(subject.copy(tasksPending = pendingCount, notesCount = notesCount))
        }
    }

    suspend fun recalculateAllSubjectCounters() {
        withContext(Dispatchers.IO) {
            val subjects = subjectDao.getAllList()
            subjects.forEach { subject ->
                val pendingCount = taskDao.getBySubjectName(subject.name).count { it.status == Task.STATUS_PENDING }
                val notesCount = noteDao.getBySubjectName(subject.name).size
                subjectDao.update(subject.copy(tasksPending = pendingCount, notesCount = notesCount))
            }
        }
    }

    val allManualEvents: LiveData<List<ManualEvent>> =
        activeOwner.switchMap { manualEventDao.getAllForOwnerLive(it) }

    private fun enqueueManualEvent(event: ManualEvent, op: String) {
        enqueue(SyncQueueItem.TYPE_MANUAL_EVENT, event.id, op, event.owner) {
            put("title", enc(event.title))
            put("location", enc(event.location))
            put("startTime", event.startTime)
            put("endTime", event.endTime)
            put("frequencyType", event.frequencyType)
            put("dayOfWeek", event.dayOfWeek)
            put("date", event.date?.time)
        }
    }

    suspend fun addManualEvent(event: ManualEvent) {
        withContext(Dispatchers.IO) {
            val rowId = manualEventDao.insert(event)
            val saved = if (event.id == 0) event.copy(id = rowId.toInt()) else event
            enqueueManualEvent(saved, SyncQueueItem.OP_INSERT)
        }
    }

    suspend fun updateManualEvent(event: ManualEvent) {
        withContext(Dispatchers.IO) {
            manualEventDao.update(event)
            enqueueManualEvent(event, SyncQueueItem.OP_UPDATE)
        }
    }

    suspend fun deleteManualEvents(events: List<ManualEvent>) {
        withContext(Dispatchers.IO) {
            manualEventDao.deleteAll(events)
            events.forEach { enqueueManualEvent(it, SyncQueueItem.OP_DELETE) }
        }
    }

    suspend fun getManualEventsByDayOfWeek(dayOfWeek: Int): List<ManualEvent> {
        return withContext(Dispatchers.IO) {
            manualEventDao.getByDayOfWeek(dayOfWeek)
        }
    }

    suspend fun getManualEventsByDate(date: java.util.Date): List<ManualEvent> {
        return withContext(Dispatchers.IO) {
            manualEventDao.getByDate(date.time)
        }
    }

    suspend fun getAllManualEventsList(owner: String = ""): List<ManualEvent> {
        return withContext(Dispatchers.IO) {
            if (owner.isBlank()) manualEventDao.getAllList() else manualEventDao.getAllForOwner(owner)
        }
    }

    suspend fun getPendingSyncCount(owner: String): Int {
        return withContext(Dispatchers.IO) {
            syncQueueDao.getPendingCountForOwner(owner)
        }
    }

    suspend fun getPendingSyncItems(owner: String): List<SyncQueueItem> {
        return withContext(Dispatchers.IO) {
            syncQueueDao.getAllListForOwner(owner)
        }
    }

    suspend fun enqueueSyncItem(item: SyncQueueItem) {
        withContext(Dispatchers.IO) {
            syncQueueDao.insert(item)
        }
    }

    suspend fun clearSyncQueue(owner: String) {
        withContext(Dispatchers.IO) {
            syncQueueDao.clearForOwner(owner)
        }
    }

    /** Número total de registros locales (materias/tareas/notas/eventos) de [owner]. */
    suspend fun countRecordsForOwner(owner: String): Int {
        return withContext(Dispatchers.IO) {
            subjectDao.countForOwner(owner) +
                taskDao.countForOwner(owner) +
                noteDao.countForOwner(owner) +
                manualEventDao.countForOwner(owner)
        }
    }

    /** Reasigna todos los registros de [from] a [to] (migración invitado→cuenta). */
    suspend fun reassignOwner(from: String, to: String) {
        withContext(Dispatchers.IO) {
            subjectDao.reassignOwner(from, to)
            taskDao.reassignOwner(from, to)
            noteDao.reassignOwner(from, to)
            manualEventDao.reassignOwner(from, to)
            syncQueueDao.reassignOwner(from, to)
            notificationDao.reassignOwner(from, to)
            sharedTaskDao.reassignOwner(from, to)
        }
    }

    /** Borra todos los datos locales de [owner] (logout tras respaldo confirmado). */
    suspend fun deleteByOwner(owner: String) {
        withContext(Dispatchers.IO) {
            subjectDao.deleteByOwner(owner)
            taskDao.deleteByOwner(owner)
            noteDao.deleteByOwner(owner)
            manualEventDao.deleteByOwner(owner)
            syncQueueDao.clearForOwner(owner)
            notificationDao.deleteByOwner(owner)
            sharedTaskDao.deleteByOwner(owner)
        }
    }

    /**
     * Borra solo las materias oficiales (sincronizadas del servidor) de [owner].
     * Se usa al invalidar la sesión, para eliminar las materias "fantasma" sin
     * tocar las materias manuales, tareas ni notas del usuario.
     */
    suspend fun clearOfficialSubjects(owner: String) {
        withContext(Dispatchers.IO) {
            subjectDao.deleteAllOfficialForOwner(owner)
        }
    }

    /** Materias oficiales (sincronizadas del servidor) de [owner]. */
    suspend fun getOfficialSubjects(owner: String): List<Subject> {
        return withContext(Dispatchers.IO) {
            subjectDao.getAllForOwner(owner).filter { it.isOfficial }
        }
    }

    // --- Bandeja de notificaciones (RQF-APP-27/28/29) ---

    val notifications: LiveData<List<Notification>> =
        activeOwner.switchMap { notificationDao.getAllForOwnerLive(it) }

    val unreadNotificationCount: LiveData<Int> =
        activeOwner.switchMap { notificationDao.unreadCountForOwnerLive(it) }

    /**
     * Registra una notificación si representa un cambio nuevo. Devuelve `true`
     * solo si se insertó (deduplica contra el último valor conocido para esa
     * materia y tipo de cambio, evitando avisos repetidos al re-sincronizar).
     */
    suspend fun recordNotificationIfNew(notification: Notification): Boolean {
        return withContext(Dispatchers.IO) {
            val last = notificationDao.latestValueFor(
                notification.owner, notification.type, notification.subjectName
            )
            if (last == notification.newValue) {
                false
            } else {
                notificationDao.insert(notification)
                true
            }
        }
    }

    suspend fun markNotificationsRead(owner: String) {
        withContext(Dispatchers.IO) { notificationDao.markAllReadForOwner(owner) }
    }

    /** Descarta una notificación individual de la bandeja in-app. */
    suspend fun deleteNotification(id: Int) {
        withContext(Dispatchers.IO) { notificationDao.deleteById(id) }
    }

    /** Borra todas las notificaciones de [owner]. */
    suspend fun deleteAllNotifications(owner: String) {
        withContext(Dispatchers.IO) { notificationDao.deleteByOwner(owner) }
    }

    /** Inserta una notificación in-app sin deduplicar (toques, tareas compartidas). */
    suspend fun addNotification(notification: Notification) {
        withContext(Dispatchers.IO) { notificationDao.insert(notification) }
    }

    // --- Colaboración de tareas (RQF-APP-45/46/47) ---

    /** Bandeja local de tareas compartidas conmigo, reactiva por cuenta activa. */
    val sharedTaskInbox: LiveData<List<SharedTaskInbox>> =
        activeOwner.switchMap { sharedTaskDao.getInboxForOwnerLive(it) }

    /** Ids de tareas compartidas ya conocidas localmente (para detectar nuevas). */
    suspend fun knownSharedTaskIds(owner: String): Set<String> {
        return withContext(Dispatchers.IO) { sharedTaskDao.getKnownIds(owner).toSet() }
    }

    suspend fun upsertSharedTask(item: SharedTaskInbox) {
        withContext(Dispatchers.IO) { sharedTaskDao.upsert(item) }
    }

    suspend fun updateSharedTaskStatus(sharedTaskId: String, status: String) {
        withContext(Dispatchers.IO) { sharedTaskDao.updateStatus(sharedTaskId, status) }
    }

    /** Inserta registros restaurados desde un respaldo, sin encolar sync. */
    suspend fun restoreRecords(
        subjects: List<Subject>,
        tasks: List<Task>,
        notes: List<Note>,
        events: List<ManualEvent>
    ) {
        withContext(Dispatchers.IO) {
            subjects.forEach { subjectDao.insert(it.copy(id = 0)) }
            tasks.forEach { taskDao.insert(it.copy(id = 0)) }
            notes.forEach { noteDao.insert(it.copy(id = 0)) }
            events.forEach { manualEventDao.insert(it.copy(id = 0)) }
        }
    }
}
