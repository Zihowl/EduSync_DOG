package dev.zihowl.dog.ui.notes

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
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
    private lateinit var editTextRichContent: TextInputEditText
    private lateinit var spinnerSubject: AutoCompleteTextView
    private lateinit var buttonPickAttachment: MaterialButton
    private lateinit var textViewAttachmentInfo: TextView
    private var isEditing = false
    private var originalNote: Note? = null
    private var selectedAttachmentUri: Uri? = null
    private var selectedAttachmentName: String? = null
    private var selectedAttachmentSize: Long? = null

    private val pickAttachmentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            validateAndSetAttachment(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[NotesViewModel::class.java]
        subjectsViewModel = ViewModelProvider(requireActivity())[dev.zihowl.dog.ui.subjects.SubjectsViewModel::class.java]
        arguments?.let {
            originalNote = it.getSerializable(KEY_NOTE) as? Note
            isEditing = originalNote != null
            originalNote?.attachmentPath?.let { path ->
                selectedAttachmentUri = Uri.parse(path)
                selectedAttachmentName = originalNote?.attachmentName
                selectedAttachmentSize = originalNote?.attachmentSize
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_note, null)

        editTextTitle = view.findViewById(R.id.editTextNoteTitle)
        editTextContent = view.findViewById(R.id.editTextNoteContent)
        editTextRichContent = view.findViewById(R.id.editTextNoteRichContent)
        spinnerSubject = view.findViewById(R.id.spinnerSubject)
        buttonPickAttachment = view.findViewById(R.id.buttonPickAttachment)
        textViewAttachmentInfo = view.findViewById(R.id.textViewAttachmentInfo)

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
            editTextRichContent.setText(note.richContent)
            if (!note.subjectName.isNullOrEmpty()) {
                spinnerSubject.setText(note.subjectName, false)
            }
            updateAttachmentInfo()
        }

        buttonPickAttachment.setOnClickListener {
            pickAttachmentLauncher.launch(arrayOf("image/*", "application/pdf"))
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
        val richContent = editTextRichContent.text?.toString()?.trim()
        val subjectName = spinnerSubject.text.toString().let {
            if (it == "Ninguna") null else it
        }

        selectedAttachmentUri?.let { uri ->
            if (!isValidAttachment(uri)) {
                Toast.makeText(requireContext(), "Adjunto inválido. Extensiones permitidas: jpg, jpeg, png, pdf. Máx 10 MB.", Toast.LENGTH_LONG).show()
                return
            }
        }

        if (isEditing && originalNote != null) {
            val updated = originalNote!!.copy(
                title = title,
                content = content,
                richContent = richContent,
                subjectName = subjectName,
                attachmentPath = selectedAttachmentUri?.toString(),
                attachmentName = selectedAttachmentName,
                attachmentSize = selectedAttachmentSize
            )
            viewModel.updateNote(updated, requireContext())
            viewModel.finishSelectionMode()
        } else {
            val note = Note(
                title = title,
                content = content,
                richContent = richContent,
                subjectName = subjectName,
                attachmentPath = selectedAttachmentUri?.toString(),
                attachmentName = selectedAttachmentName,
                attachmentSize = selectedAttachmentSize
            )
            viewModel.addNote(note, requireContext())
        }
        dismiss()
    }

    private fun validateAndSetAttachment(uri: Uri) {
        val name = getFileName(uri)
        val size = getFileSize(uri)
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: ""
        val allowed = listOf("jpg", "jpeg", "png", "pdf")
        if (ext !in allowed || (size != null && size > 10 * 1024 * 1024)) {
            Toast.makeText(requireContext(), "Archivo no permitido o excede 10 MB", Toast.LENGTH_LONG).show()
            return
        }
        selectedAttachmentUri = uri
        selectedAttachmentName = name
        selectedAttachmentSize = size
        updateAttachmentInfo()
    }

    private fun isValidAttachment(uri: Uri): Boolean {
        val name = getFileName(uri) ?: return false
        val ext = name.substringAfterLast('.', "").lowercase()
        val size = getFileSize(uri)
        return ext in listOf("jpg", "jpeg", "png", "pdf") && (size == null || size <= 10 * 1024 * 1024)
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }

    private fun getFileSize(uri: Uri): Long? {
        var size: Long? = null
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !cursor.isNull(idx)) size = cursor.getLong(idx)
            }
        }
        return size
    }

    private fun updateAttachmentInfo() {
        if (selectedAttachmentName != null) {
            textViewAttachmentInfo.visibility = View.VISIBLE
            textViewAttachmentInfo.text = "Adjunto: $selectedAttachmentName (${(selectedAttachmentSize ?: 0) / 1024} KB)"
        } else {
            textViewAttachmentInfo.visibility = View.GONE
        }
    }
}
