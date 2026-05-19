package dev.zihowl.dog.ui.subjects

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zihowl.dog.data.model.Note
import dev.zihowl.dog.data.model.Subject
import dev.zihowl.dog.data.model.Task
import dev.zihowl.dog.data.repository.DogRepository
import kotlinx.coroutines.launch

class SubjectsViewModel(private val repository: DogRepository) : ViewModel() {

    val subjects: LiveData<List<Subject>> = repository.allSubjects

    private val _isSelectionMode = MutableLiveData(false)
    val isSelectionMode: LiveData<Boolean> = _isSelectionMode

    private val _selectedSubjects = MutableLiveData<Set<Subject>>(emptySet())
    val selectedSubjects: LiveData<Set<Subject>> = _selectedSubjects

    private val tasksObserver = Observer<List<Task>> {
        viewModelScope.launch { repository.recalculateAllSubjectCounters() }
    }

    private val notesObserver = Observer<List<Note>> {
        viewModelScope.launch { repository.recalculateAllSubjectCounters() }
    }

    init {
        repository.allTasks.observeForever(tasksObserver)
        repository.allNotes.observeForever(notesObserver)
    }

    fun addSubject(name: String, professorName: String, schedule: String, context: Context, owner: String = "") {
        viewModelScope.launch {
            if (repository.subjectExists(name)) {
                Toast.makeText(context, "Ya existe una materia con ese nombre", Toast.LENGTH_LONG).show()
            } else {
                repository.addSubject(name, professorName, schedule, owner)
                Toast.makeText(context, "Materia '$name' guardada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateSubject(subject: Subject, newName: String, newProfessorName: String, newSchedule: String) {
        viewModelScope.launch {
            repository.updateSubject(subject, newName, newProfessorName, newSchedule)
        }
    }

    fun deleteSelectedSubjects(context: Context) {
        val selected = _selectedSubjects.value ?: return
        viewModelScope.launch {
            repository.deleteSubjects(selected.toList())
            finishSelectionMode()
            val count = selected.size
            Toast.makeText(
                context,
                "$count ${if (count > 1) "materias eliminadas" else "materia eliminada"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun disassociateAndDelete(subject: Subject, context: Context) {
        viewModelScope.launch {
            repository.disassociateAndDeleteSubject(subject.id)
            finishSelectionMode()
            Toast.makeText(context, "Materia '${subject.name}' eliminada", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSubjectAndContent(subject: Subject, context: Context) {
        viewModelScope.launch {
            repository.deleteSubjectWithContent(subject.id)
            finishSelectionMode()
            Toast.makeText(context, "Materia '${subject.name}' y su contenido eliminados", Toast.LENGTH_SHORT).show()
        }
    }

    fun disassociateAndDeleteSelected(context: Context) {
        val selected = _selectedSubjects.value ?: return
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.disassociateAndDeleteSubjects(selected.map { it.id })
            finishSelectionMode()
            val count = selected.size
            Toast.makeText(
                context,
                "$count ${if (count > 1) "materias eliminadas" else "materia eliminada"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun deleteSelectedSubjectsAndContent(context: Context) {
        val selected = _selectedSubjects.value ?: return
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.deleteSubjectsWithContent(selected.map { it.id })
            finishSelectionMode()
            val count = selected.size
            Toast.makeText(
                context,
                "$count ${if (count > 1) "materias" else "materia"} y su contenido eliminados",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun subjectHasContent(subject: Subject): Boolean {
        return (subject.tasksPending > 0) || (subject.notesCount > 0)
    }

    suspend fun getSubjectContentCount(subject: Subject): IntArray {
        val tasks = repository.getTasksForSubject(subject.name)
        val notes = repository.getNotesForSubject(subject.name)
        return intArrayOf(tasks.size, notes.size)
    }

    suspend fun getSelectionContentCount(): IntArray {
        val selected = _selectedSubjects.value ?: return intArrayOf(0, 0)
        var tasks = 0
        var notes = 0
        selected.forEach { subject ->
            tasks += repository.getTasksForSubject(subject.name).size
            notes += repository.getNotesForSubject(subject.name).size
        }
        return intArrayOf(tasks, notes)
    }

    suspend fun selectionHasContent(): Boolean {
        val counts = getSelectionContentCount()
        return counts[0] > 0 || counts[1] > 0
    }

    fun toggleSelection(subject: Subject) {
        // Las materias oficiales son solo lectura: no entran en modo selección.
        if (subject.isOfficial) return
        val current = _selectedSubjects.value?.toMutableSet() ?: mutableSetOf()
        if (current.contains(subject)) {
            current.remove(subject)
        } else {
            current.add(subject)
        }
        _selectedSubjects.value = current

        if (current.isEmpty() && _isSelectionMode.value == true) {
            finishSelectionMode()
        } else if (current.isNotEmpty() && _isSelectionMode.value != true) {
            _isSelectionMode.value = true
        }
    }

    fun finishSelectionMode() {
        _selectedSubjects.value = emptySet()
        _isSelectionMode.value = false
    }

    override fun onCleared() {
        super.onCleared()
        repository.allTasks.removeObserver(tasksObserver)
        repository.allNotes.removeObserver(notesObserver)
    }
}
