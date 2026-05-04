package dev.zihowl.dog.ui.subjects

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.Subject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddSubjectDialogFragment : DialogFragment() {

    companion object {
        private const val KEY_SUBJECT = "subject"

        fun newInstance(subject: Subject): AddSubjectDialogFragment {
            return AddSubjectDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(KEY_SUBJECT, subject)
                }
            }
        }
    }

    private lateinit var viewModel: SubjectsViewModel
    private lateinit var editTextName: TextInputEditText
    private lateinit var editTextProfessorName: TextInputEditText
    private lateinit var containerScheduleBlocks: LinearLayout
    private var isEditing = false
    private var originalSubject: Subject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[SubjectsViewModel::class.java]
        arguments?.let {
            originalSubject = it.getSerializable(KEY_SUBJECT) as? Subject
            isEditing = originalSubject != null
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_add_subject, null)

        editTextName = view.findViewById(R.id.editTextSubjectName)
        editTextProfessorName = view.findViewById(R.id.editTextProfessorName)
        containerScheduleBlocks = view.findViewById(R.id.containerScheduleBlocks)
        val buttonAddBlock = view.findViewById<Button>(R.id.buttonAddBlock)
        buttonAddBlock.setOnClickListener { addScheduleBlock(null, null, null) }

        val dialogTitle = if (isEditing) "Editar Materia" else "Nueva Materia"

        originalSubject?.let { subject ->
            editTextName.setText(subject.name)
            editTextProfessorName.setText(subject.professorName)
            populateScheduleBlocksFromString(subject.schedule)
        }

        builder.setView(view)
            .setTitle(dialogTitle)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar") { _, _ -> dismiss() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener { saveSubject() }
        }

        return dialog
    }

    private fun addScheduleBlock(day: String?, startTime24h: String?, endTime24h: String?) {
        val blockView = layoutInflater.inflate(R.layout.item_schedule_block, containerScheduleBlocks, false)

        val spinnerDay = blockView.findViewById<Spinner>(R.id.spinnerDay)
        val textViewStartTime = blockView.findViewById<TextView>(R.id.textViewStartTime)
        val textViewEndTime = blockView.findViewById<TextView>(R.id.textViewEndTime)
        val buttonRemoveBlock = blockView.findViewById<ImageButton>(R.id.buttonRemoveBlock)

        textViewStartTime.setOnClickListener { showTimePicker(textViewStartTime) }
        textViewEndTime.setOnClickListener { showTimePicker(textViewEndTime) }
        buttonRemoveBlock.setOnClickListener { containerScheduleBlocks.removeView(blockView) }

        if (day != null) {
            val daysArray = resources.getStringArray(R.array.week_days)
            for (i in daysArray.indices) {
                if (daysArray[i].equals(day, ignoreCase = true)) {
                    spinnerDay.setSelection(i)
                    break
                }
            }
        }

        if (startTime24h != null) {
            textViewStartTime.text = formatTo12Hour(startTime24h)
            textViewStartTime.tag = startTime24h
        }
        if (endTime24h != null) {
            textViewEndTime.text = formatTo12Hour(endTime24h)
            textViewEndTime.tag = endTime24h
        }

        containerScheduleBlocks.addView(blockView)
    }

    private fun showTimePicker(timeTextView: TextView) {
        val existingTime24h = (timeTextView.tag as? String) ?: "12:00"
        val timeParts = existingTime24h.split(":")
        val hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText("Seleccionar hora")
            .build()

        picker.addOnPositiveButtonClickListener {
            val time24h = String.format(Locale.getDefault(), "%02d:%02d", picker.hour, picker.minute)
            timeTextView.text = formatTo12Hour(time24h)
            timeTextView.tag = time24h
        }

        picker.show(parentFragmentManager, "TimePicker")
    }

    private fun saveSubject() {
        val name = editTextName.text?.toString()?.trim() ?: ""
        if (TextUtils.isEmpty(name)) {
            editTextName.error = "El nombre no puede estar vacío"
            return
        }

        val professorName = editTextProfessorName.text?.toString()?.trim() ?: ""
        val scheduleString = buildScheduleString()

        if (scheduleString == null) return

        if (isEditing && originalSubject != null) {
            viewModel.updateSubject(originalSubject!!, name, professorName, scheduleString)
            Toast.makeText(context, "Materia '$name' actualizada", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addSubject(name, professorName, scheduleString, requireContext())
        }

        viewModel.finishSelectionMode()
        dismiss()
    }

    private fun buildScheduleString(): String? {
        val scheduleBuilder = StringBuilder()
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

        for (i in 0 until containerScheduleBlocks.childCount) {
            val blockView = containerScheduleBlocks.getChildAt(i)
            val tvStart = blockView.findViewById<TextView>(R.id.textViewStartTime)
            val tvEnd = blockView.findViewById<TextView>(R.id.textViewEndTime)
            val startTimeStr = tvStart.tag as? String
            val endTimeStr = tvEnd.tag as? String

            if (startTimeStr == null || endTimeStr == null) {
                Toast.makeText(context, "Define hora de inicio y fin para todos los bloques.", Toast.LENGTH_SHORT).show()
                return null
            }

            try {
                val startTime = sdf.parse(startTimeStr)
                val endTime = sdf.parse(endTimeStr)
                if (startTime != null && endTime != null && startTime.after(endTime)) {
                    Toast.makeText(context, "La hora de fin no puede ser anterior a la de inicio.", Toast.LENGTH_LONG).show()
                    return null
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Formato de hora inválido.", Toast.LENGTH_SHORT).show()
                return null
            }

            val spinnerDay = blockView.findViewById<Spinner>(R.id.spinnerDay)
            scheduleBuilder.append(spinnerDay.selectedItem.toString()).append(" ")
                .append(startTimeStr).append(" - ").append(endTimeStr)
            if (i < containerScheduleBlocks.childCount - 1) {
                scheduleBuilder.append("\n")
            }
        }
        return scheduleBuilder.toString()
    }

    private fun populateScheduleBlocksFromString(schedule: String?) {
        if (schedule.isNullOrEmpty()) return
        containerScheduleBlocks.removeAllViews()
        for (line in schedule.split("\n")) {
            try {
                val parts = line.split(" ")
                val day = parts[0]
                val times = line.substring(day.length).trim().split(" - ")
                addScheduleBlock(day, times[0], times[1])
            } catch (_: Exception) {
            }
        }
    }

    private fun formatTo12Hour(time24h: String?): String {
        if (time24h.isNullOrEmpty()) return "Hora"
        return try {
            val sdf24 = SimpleDateFormat("HH:mm", Locale.getDefault())
            val sdf12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf12.format(sdf24.parse(time24h)!!)
        } catch (_: Exception) {
            "Hora"
        }
    }
}
