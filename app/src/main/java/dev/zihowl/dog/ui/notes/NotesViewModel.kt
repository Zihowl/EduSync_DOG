package dev.zihowl.dog.ui.notes

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zihowl.dog.data.model.Note
import dev.zihowl.dog.data.repository.DogRepository
import kotlinx.coroutines.launch

class NotesViewModel(private val repository: DogRepository) : ViewModel() {

    val notes: LiveData<List<Note>> = repository.allNotes

    private val _isSelectionMode = MutableLiveData(false)
    val isSelectionMode: LiveData<Boolean> = _isSelectionMode

    private val _selectedNotes = MutableLiveData<Set<Note>>(emptySet())
    val selectedNotes: LiveData<Set<Note>> = _selectedNotes

    fun addNote(note: Note, context: Context, owner: String = "") {
        viewModelScope.launch {
            repository.addNote(note.copy(owner = owner))
            Toast.makeText(context, "Nota '${note.title}' creada", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateNote(note: Note, context: Context) {
        viewModelScope.launch {
            repository.updateNote(note)
            Toast.makeText(context, "Nota actualizada", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSelectedNotes(context: Context) {
        val selected = _selectedNotes.value ?: return
        viewModelScope.launch {
            repository.deleteNotes(selected.toList())
            finishSelectionMode()
            val count = selected.size
            Toast.makeText(
                context,
                "$count ${if (count > 1) " notas eliminadas" else " nota eliminada"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun toggleSelection(note: Note) {
        val current = _selectedNotes.value?.toMutableSet() ?: mutableSetOf()
        if (current.contains(note)) {
            current.remove(note)
        } else {
            current.add(note)
        }
        _selectedNotes.value = current

        if (current.isEmpty() && _isSelectionMode.value == true) {
            finishSelectionMode()
        } else if (current.isNotEmpty() && _isSelectionMode.value != true) {
            _isSelectionMode.value = true
        }
    }

    fun finishSelectionMode() {
        _selectedNotes.value = emptySet()
        _isSelectionMode.value = false
    }
}
