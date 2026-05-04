package dev.zihowl.dog.ui.schedule

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dev.zihowl.dog.data.model.Subject
import dev.zihowl.dog.data.repository.DogRepository

class ScheduleViewModel(repository: DogRepository) : ViewModel() {
    val subjects: LiveData<List<Subject>> = repository.allSubjects
}
