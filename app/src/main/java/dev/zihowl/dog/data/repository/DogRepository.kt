package dev.zihowl.dog.data.repository

import androidx.lifecycle.LiveData
import dev.zihowl.dog.data.local.NoteDao
import dev.zihowl.dog.data.local.SubjectDao
import dev.zihowl.dog.data.local.TaskDao
import dev.zihowl.dog.data.model.Note
import dev.zihowl.dog.data.model.Subject
import dev.zihowl.dog.data.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DogRepository(
    private val subjectDao: SubjectDao,
    private val taskDao: TaskDao,
    private val noteDao: NoteDao
) {
    val allSubjects: LiveData<List<Subject>> = subjectDao.getAll()
    val allTasks: LiveData<List<Task>> = taskDao.getAll()
    val allNotes: LiveData<List<Note>> = noteDao.getAll()

    suspend fun subjectExists(name: String): Boolean {
        return withContext(Dispatchers.IO) {
            subjectDao.exists(name)
        }
    }

    suspend fun addSubject(name: String, professorName: String, schedule: String): Boolean {
        return withContext(Dispatchers.IO) {
            if (subjectDao.exists(name)) {
                false
            } else {
                subjectDao.insert(
                    Subject(
                        name = name,
                        professorName = professorName,
                        schedule = schedule
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

    suspend fun getAllSubjectsList(): List<Subject> {
        return withContext(Dispatchers.IO) {
            subjectDao.getAllList()
        }
    }

    suspend fun getAllTasksList(): List<Task> {
        return withContext(Dispatchers.IO) {
            taskDao.getAllList()
        }
    }

    suspend fun getAllNotesList(): List<Note> {
        return withContext(Dispatchers.IO) {
            noteDao.getAllList()
        }
    }

    suspend fun recalculateSubjectCounters(subjectName: String) {
        withContext(Dispatchers.IO) {
            val subject = subjectDao.getByName(subjectName) ?: return@withContext
            val pendingCount = taskDao.getBySubjectName(subjectName).count { !it.isCompleted }
            val notesCount = noteDao.getBySubjectName(subjectName).size
            subjectDao.update(subject.copy(tasksPending = pendingCount, notesCount = notesCount))
        }
    }

    suspend fun recalculateAllSubjectCounters() {
        withContext(Dispatchers.IO) {
            val subjects = subjectDao.getAllList()
            subjects.forEach { subject ->
                val pendingCount = taskDao.getBySubjectName(subject.name).count { !it.isCompleted }
                val notesCount = noteDao.getBySubjectName(subject.name).size
                subjectDao.update(subject.copy(tasksPending = pendingCount, notesCount = notesCount))
            }
        }
    }
}
