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
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.utils.AttachmentUtils

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
    private lateinit var buttonPickAttachment: MaterialButton
    private lateinit var textViewAttachmentInfo: TextView
    private var isEditing = false
    private var originalNote: Note? = null
    private var selectedAttachmentPath: String? = null
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
                selectedAttachmentPath = path
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
            if (!note.subjectName.isNullOrEmpty()) {
                spinnerSubject.setText(note.subjectName, false)
            }
            updateAttachmentInfo()
        }

        buttonPickAttachment.setOnClickListener {
            pickAttachmentLauncher.launch(arrayOf("image/*", "application/pdf"))
        }

        textViewAttachmentInfo.setOnClickListener {
            AttachmentUtils.openAttachment(requireContext(), selectedAttachmentPath, selectedAttachmentName)
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
        val sessionManager = SessionManager(requireContext())
        val owner = sessionManager.username

        if (isEditing && originalNote != null) {
            val updated = originalNote!!.copy(
                title = title,
                content = content,
                subjectName = subjectName,
                attachmentPath = selectedAttachmentPath,
                attachmentName = selectedAttachmentName,
                attachmentSize = selectedAttachmentSize,
                owner = owner
            )
            viewModel.updateNote(updated, requireContext())
            viewModel.finishSelectionMode()
        } else {
            val note = Note(
                title = title,
                content = content,
                subjectName = subjectName,
                attachmentPath = selectedAttachmentPath,
                attachmentName = selectedAttachmentName,
                attachmentSize = selectedAttachmentSize,
                owner = owner
            )
            viewModel.addNote(note, requireContext(), owner)
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

        val copiedFile = AttachmentUtils.copyUriToInternalStorage(requireContext(), uri)
        if (copiedFile == null) {
            Toast.makeText(requireContext(), "No se pudo guardar el archivo adjunto", Toast.LENGTH_LONG).show()
            return
        }

        selectedAttachmentPath = copiedFile.absolutePath
        selectedAttachmentName = name
        selectedAttachmentSize = size
        updateAttachmentInfo()
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
            textViewAttachmentInfo.text = "Adjunto: $selectedAttachmentName (${(selectedAttachmentSize ?: 0) / 1024} KB) — Toca para abrir"
        } else {
            textViewAttachmentInfo.visibility = View.GONE
        }
    }
}
