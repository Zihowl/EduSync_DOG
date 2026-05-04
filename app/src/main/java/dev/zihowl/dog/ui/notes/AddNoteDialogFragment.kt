package dev.zihowl.dog.ui.notes

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.Note

class AddNoteDialogFragment : DialogFragment() {

    companion object {
        private const val KEY_NOTE = "note"

        fun newInstance(note: Note): AddNoteDialogFragment {
            return AddNoteDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(KEY_NOTE, note)
                }
            }
        }
    }

    private lateinit var viewModel: NotesViewModel
    private lateinit var subjectsViewModel: dev.zihowl.dog.ui.subjects.SubjectsViewModel
    private lateinit var editTextTitle: TextInputEditText
    private lateinit var editTextContent: TextInputEditText
    private lateinit var spinnerSubject: AutoCompleteTextView
    private var isEditing = false
    private var originalNote: Note? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[NotesViewModel::class.java]
        subjectsViewModel = ViewModelProvider(requireActivity())[dev.zihowl.dog.ui.subjects.SubjectsViewModel::class.java]
        arguments?.let {
            originalNote = it.getSerializable(KEY_NOTE) as? Note
            isEditing = originalNote != null
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_note, null)

        editTextTitle = view.findViewById(R.id.editTextNoteTitle)
        editTextContent = view.findViewById(R.id.editTextNoteContent)
        spinnerSubject = view.findViewById(R.id.spinnerSubject)

        val subjectNames = mutableListOf("Ninguna")
        subjectsViewModel.subjects.observe(this) { subjects ->
            subjectNames.clear()
            subjectNames.add("Ninguna")
            subjects?.let { subjectNames.addAll(it.map { s -> s.name }) }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, subjectNames)
            spinnerSubject.setAdapter(adapter)
        }

        originalNote?.let { note ->
            editTextTitle.setText(note.title)
            editTextContent.setText(note.content)
            if (!note.subjectName.isNullOrEmpty()) {
                spinnerSubject.setText(note.subjectName, false)
            }
        }

        builder.setView(view)
            .setTitle(if (isEditing) "Editar Nota" else "Nueva Nota")
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar") { _, _ -> dismiss() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { saveNote() }
        }
        return dialog
    }

    private fun saveNote() {
        val title = editTextTitle.text?.toString()?.trim() ?: ""
        if (TextUtils.isEmpty(title)) {
            editTextTitle.error = "El título no puede estar vacío"
            return
        }

        val content = editTextContent.text?.toString()?.trim()
        val subjectName = spinnerSubject.text.toString().let {
            if (it == "Ninguna") null else it
        }

        if (isEditing && originalNote != null) {
            val updated = originalNote!!.copy(
                title = title,
                content = content,
                subjectName = subjectName
            )
            viewModel.updateNote(updated, requireContext())
            viewModel.finishSelectionMode()
        } else {
            val note = Note(
                title = title,
                content = content,
                subjectName = subjectName
            )
            viewModel.addNote(note, requireContext())
        }
        dismiss()
    }
}
