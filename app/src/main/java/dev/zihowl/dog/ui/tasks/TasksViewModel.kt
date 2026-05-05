package dev.zihowl.dog.ui.tasks

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zihowl.dog.data.model.Task
import dev.zihowl.dog.data.repository.DogRepository
import kotlinx.coroutines.launch
import java.util.Date

class TasksViewModel(private val repository: DogRepository) : ViewModel() {

    val allTasks: LiveData<List<Task>> = repository.allTasks

    val pendingTasks: LiveData<List<Task>> = MutableLiveData()
    val completedTasks: LiveData<List<Task>> = MutableLiveData()
    val notCompletedTasks: LiveData<List<Task>> = MutableLiveData()

    private val _isSelectionMode = MutableLiveData(false)
    val isSelectionMode: LiveData<Boolean> = _isSelectionMode

    private val _selectedTasks = MutableLiveData<Set<Task>>(emptySet())
    val selectedTasks: LiveData<Set<Task>> = _selectedTasks

    private val _isPendingExpanded = MutableLiveData(true)
    val isPendingExpanded: LiveData<Boolean> = _isPendingExpanded

    private val _isCompletedExpanded = MutableLiveData(true)
    val isCompletedExpanded: LiveData<Boolean> = _isCompletedExpanded

    private val _isNotCompletedExpanded = MutableLiveData(true)
    val isNotCompletedExpanded: LiveData<Boolean> = _isNotCompletedExpanded

    init {
        allTasks.observeForever { tasks ->
            val sorted = tasks?.sortedWith(compareBy(
                { when (it.priority) {
                    Task.PRIORITY_HIGH -> 0
                    Task.PRIORITY_MEDIUM -> 1
                    Task.PRIORITY_LOW -> 2
                    else -> 3
                }},
                { it.dueDate ?: Date(Long.MAX_VALUE) }
            )) ?: emptyList()
            (pendingTasks as MutableLiveData).value = sorted.filter { it.status == Task.STATUS_PENDING }
            (notCompletedTasks as MutableLiveData).value = sorted.filter { it.status == Task.STATUS_NOT_COMPLETED }
            (completedTasks as MutableLiveData).value = sorted.filter { it.status == Task.STATUS_COMPLETED }
        }
    }

    fun addTask(task: Task, context: Context) {
        viewModelScope.launch {
            repository.addTask(task)
            Toast.makeText(context, "Tarea '${task.title}' añadida", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateTask(task: Task, context: Context) {
        viewModelScope.launch {
            repository.updateTask(task)
            Toast.makeText(context, "Tarea actualizada", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val newStatus = when (task.status) {
                Task.STATUS_PENDING -> Task.STATUS_COMPLETED
                Task.STATUS_COMPLETED -> Task.STATUS_PENDING
                else -> Task.STATUS_PENDING
            }
            repository.updateTask(task.copy(status = newStatus))
        }
    }

    fun deleteSelectedTasks(context: Context) {
        val selected = _selectedTasks.value ?: return
        viewModelScope.launch {
            repository.deleteTasks(selected.toList())
            finishSelectionMode()
            val count = selected.size
            Toast.makeText(
                context,
                "$count ${if (count > 1) " tareas eliminadas" else " tarea eliminada"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun toggleSelection(task: Task) {
        val current = _selectedTasks.value?.toMutableSet() ?: mutableSetOf()
        if (current.contains(task)) {
            current.remove(task)
        } else {
            current.add(task)
        }
        _selectedTasks.value = current

        if (current.isEmpty() && _isSelectionMode.value == true) {
            finishSelectionMode()
        } else if (current.isNotEmpty() && _isSelectionMode.value != true) {
            _isSelectionMode.value = true
        }
    }

    fun finishSelectionMode() {
        _selectedTasks.value = emptySet()
        _isSelectionMode.value = false
    }

    fun togglePendingExpansion() {
        _isPendingExpanded.value = _isPendingExpanded.value != true
    }

    fun toggleCompletedExpansion() {
        _isCompletedExpanded.value = _isCompletedExpanded.value != true
    }

    fun toggleNotCompletedExpansion() {
        _isNotCompletedExpanded.value = _isNotCompletedExpanded.value != true
    }

    fun markSelectedAsNotCompleted(context: Context) {
        val selected = _selectedTasks.value ?: return
        viewModelScope.launch {
            selected.forEach { task ->
                repository.updateTask(task.copy(status = Task.STATUS_NOT_COMPLETED))
            }
            finishSelectionMode()
            val count = selected.size
            Toast.makeText(
                context,
                "$count ${if (count > 1) "tareas marcadas como no completadas" else "tarea marcada como no completada"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        allTasks.removeObserver { }
    }
}
