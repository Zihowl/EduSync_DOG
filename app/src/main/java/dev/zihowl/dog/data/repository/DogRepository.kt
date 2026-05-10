package dev.zihowl.dog.data.repository

import androidx.lifecycle.LiveData
import dev.zihowl.dog.data.local.ManualEventDao
import dev.zihowl.dog.data.local.NoteDao
import dev.zihowl.dog.data.local.SubjectDao
import dev.zihowl.dog.data.local.SyncQueueDao
import dev.zihowl.dog.data.local.TaskDao
import dev.zihowl.dog.data.model.ManualEvent
import dev.zihowl.dog.data.model.Note
import dev.zihowl.dog.data.model.Subject
import dev.zihowl.dog.data.model.SyncQueueItem
import dev.zihowl.dog.data.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DogRepository(
    private val subjectDao: SubjectDao,
    private val taskDao: TaskDao,
    private val noteDao: NoteDao,
    private val manualEventDao: ManualEventDao,
    private val syncQueueDao: SyncQueueDao
) {
    val allSubjects: LiveData<List<Subject>> = subjectDao.getAll()
    val allTasks: LiveData<List<Task>> = taskDao.getAll()
    val allNotes: LiveData<List<Note>> = noteDao.getAll()

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

    suspend fun addTask(task: Task) {
        withContext(Dispatchers.IO) {
            taskDao.insert(task)
        }
    }

    suspend fun updateTask(task: Task) {
        withContext(Dispatchers.IO) {
            taskDao.update(task)
        }
    }

    suspend fun deleteTasks(tasks: List<Task>) {
        withContext(Dispatchers.IO) {
            taskDao.deleteAll(tasks)
        }
    }

    suspend fun addNote(note: Note) {
        withContext(Dispatchers.IO) {
            noteDao.insert(note)
        }
    }

    suspend fun updateNote(note: Note) {
        withContext(Dispatchers.IO) {
            noteDao.update(note)
        }
    }

    suspend fun deleteNotes(notes: List<Note>) {
        withContext(Dispatchers.IO) {
            noteDao.deleteAll(notes)
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

    val allManualEvents: LiveData<List<ManualEvent>> = manualEventDao.getAll()

    suspend fun addManualEvent(event: ManualEvent) {
        withContext(Dispatchers.IO) {
            manualEventDao.insert(event)
        }
    }

    suspend fun updateManualEvent(event: ManualEvent) {
        withContext(Dispatchers.IO) {
            manualEventDao.update(event)
        }
    }

    suspend fun deleteManualEvents(events: List<ManualEvent>) {
        withContext(Dispatchers.IO) {
            manualEventDao.deleteAll(events)
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
}
