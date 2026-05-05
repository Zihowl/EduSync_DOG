package dev.zihowl.dog.ui.schedule

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zihowl.dog.data.model.ManualEvent
import dev.zihowl.dog.data.model.Subject
import dev.zihowl.dog.data.repository.DogRepository
import kotlinx.coroutines.launch
import java.util.Date

class ScheduleViewModel(private val repository: DogRepository) : ViewModel() {
    val subjects: LiveData<List<Subject>> = repository.allSubjects
    val manualEvents: LiveData<List<ManualEvent>> = repository.allManualEvents

    fun addManualEvent(event: ManualEvent, owner: String = "") {
        viewModelScope.launch {
            repository.addManualEvent(event.copy(owner = owner))
        }
    }

    fun updateManualEvent(event: ManualEvent, owner: String = "") {
        viewModelScope.launch {
            repository.updateManualEvent(event.copy(owner = owner))
        }
    }

    fun deleteManualEvents(events: List<ManualEvent>) {
        viewModelScope.launch {
            repository.deleteManualEvents(events)
        }
    }

    suspend fun getManualEventsByDayOfWeek(dayOfWeek: Int): List<ManualEvent> {
        return repository.getManualEventsByDayOfWeek(dayOfWeek)
    }

    suspend fun getManualEventsByDate(date: Date): List<ManualEvent> {
        return repository.getManualEventsByDate(date)
    }
}
