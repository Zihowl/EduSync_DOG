package dev.zihowl.dog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.zihowl.dog.data.repository.DogRepository
import dev.zihowl.dog.ui.notes.NotesViewModel
import dev.zihowl.dog.ui.schedule.ScheduleViewModel
import dev.zihowl.dog.ui.subjects.SubjectsViewModel
import dev.zihowl.dog.ui.tasks.TasksViewModel

class ViewModelFactory(private val repository: DogRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(SubjectsViewModel::class.java) ->
                SubjectsViewModel(repository) as T
            modelClass.isAssignableFrom(TasksViewModel::class.java) ->
                TasksViewModel(repository) as T
            modelClass.isAssignableFrom(NotesViewModel::class.java) ->
                NotesViewModel(repository) as T
            modelClass.isAssignableFrom(ScheduleViewModel::class.java) ->
                ScheduleViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
