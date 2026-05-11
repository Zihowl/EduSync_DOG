package dev.zihowl.dog.ui.tasks

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.Task
import dev.zihowl.dog.data.session.SessionManager
import java.util.Calendar
import java.util.Date

class AddTaskDialogFragment : DialogFragment() {

    companion object {
        private const val KEY_TASK = "task"

        fun newInstance(): AddTaskDialogFragment = AddTaskDialogFragment()

        fun newInstance(task: Task): AddTaskDialogFragment {
            return AddTaskDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(KEY_TASK, task)
                }
            }
        }
    }

    private lateinit var viewModel: TasksViewModel
    private lateinit var subjectsViewModel: dev.zihowl.dog.ui.subjects.SubjectsViewModel
    private lateinit var editTextTitle: TextInputEditText
    private lateinit var editTextDescription: TextInputEditText
    private lateinit var spinnerSubject: Spinner
    private lateinit var spinnerPriority: Spinner
    private lateinit var textViewTaskDueDate: TextView
    private var selectedDueDate: Date? = null
    private var isEditing = false
    private var originalTask: Task? = null

    private val priorityOptions = listOf("Alta", "Media", "Baja")
    private val priorityValues = listOf(Task.PRIORITY_HIGH, Task.PRIORITY_MEDIUM, Task.PRIORITY_LOW)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[TasksViewModel::class.java]
        subjectsViewModel = ViewModelProvider(requireActivity())[dev.zihowl.dog.ui.subjects.SubjectsViewModel::class.java]
        arguments?.let {
            originalTask = it.getSerializable(KEY_TASK) as? Task
            isEditing = originalTask != null
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_task, null)

        editTextTitle = view.findViewById(R.id.editTextTaskTitle)
        editTextDescription = view.findViewById(R.id.editTextTaskDescription)
        spinnerSubject = view.findViewById(R.id.spinnerSubjectForTask)
        spinnerPriority = view.findViewById(R.id.spinnerPriority)
        textViewTaskDueDate = view.findViewById(R.id.textViewTaskDueDate)

        val priorityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, priorityOptions)
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPriority.adapter = priorityAdapter
        spinnerPriority.setSelection(priorityValues.indexOf(Task.PRIORITY_MEDIUM))

        view.findViewById<TextView>(R.id.dialog_task_title)?.text = if (isEditing) "Editar Tarea" else "Nueva Tarea"

        val subjectNames = mutableListOf("Ninguna")
        subjectsViewModel.subjects.observe(this) { subjects ->
            subjectNames.clear()
            subjectNames.add("Ninguna")
            subjects?.let { subjectNames.addAll(it.map { s -> s.name }) }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, subjectNames)
            spinnerSubject.setAdapter(adapter)

            originalTask?.let { task ->
                if (!task.subjectName.isNullOrEmpty()) {
                    val index = subjectNames.indexOf(task.subjectName)
                    if (index >= 0) spinnerSubject.setSelection(index)
                }
            }
        }

        originalTask?.let { task ->
            editTextTitle.setText(task.title)
            editTextDescription.setText(task.description)
            selectedDueDate = task.dueDate
            textViewTaskDueDate.text = task.dueDate?.let { formatDate(it) } ?: "Fecha de vencimiento"
            val pIndex = priorityValues.indexOf(task.priority)
            if (pIndex >= 0) spinnerPriority.setSelection(pIndex)
        }

        textViewTaskDueDate.setOnClickListener { showDatePicker() }

        builder.setView(view)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar") { _, _ -> dismiss() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { saveTask() }
        }
        return dialog
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        selectedDueDate?.let { calendar.time = it }
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedDueDate = calendar.time
                textViewTaskDueDate.text = formatDate(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveTask() {
        val title = editTextTitle.text?.toString()?.trim() ?: ""
        if (TextUtils.isEmpty(title)) {
            editTextTitle.error = "El título no puede estar vacío"
            return
        }
        if (title.length > 100) {
            editTextTitle.error = "El título no puede exceder 100 caracteres"
            return
        }
        if (selectedDueDate == null) {
            Toast.makeText(context, "La fecha de vencimiento es obligatoria", Toast.LENGTH_SHORT).show()
            return
        }

        val description = editTextDescription.text?.toString()?.trim()
        val subjectName = spinnerSubject.selectedItem?.toString()?.let {
            if (it == "Ninguna") null else it
        }
        val priority = priorityValues.getOrElse(spinnerPriority.selectedItemPosition) { Task.PRIORITY_MEDIUM }
        val sessionManager = SessionManager(requireContext())
        val owner = sessionManager.username

        if (isEditing && originalTask != null) {
            val updated = originalTask!!.copy(
                title = title,
                description = description,
                dueDate = selectedDueDate,
                subjectName = subjectName,
                priority = priority,
                status = originalTask!!.status,
                owner = owner
            )
            viewModel.updateTask(updated, requireContext())
            viewModel.finishSelectionMode()
        } else {
            val task = Task(
                title = title,
                description = description,
                dueDate = selectedDueDate,
                subjectName = subjectName,
                priority = priority,
                owner = owner
            )
            viewModel.addTask(task, requireContext(), owner)
        }
        dismiss()
    }

    private fun formatDate(date: Date): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        return sdf.format(date)
    }
}
