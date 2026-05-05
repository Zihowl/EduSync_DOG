package dev.zihowl.dog.ui.schedule

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.ManualEvent
import dev.zihowl.dog.data.session.SessionManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddManualEventDialogFragment : DialogFragment() {

    companion object {
        private const val KEY_EVENT = "event"

        fun newInstance(event: ManualEvent?): AddManualEventDialogFragment {
            return AddManualEventDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(KEY_EVENT, event)
                }
            }
        }
    }

    private lateinit var viewModel: ScheduleViewModel
    private lateinit var editTextTitle: TextInputEditText
    private lateinit var editTextLocation: TextInputEditText
    private lateinit var spinnerFrequencyType: AutoCompleteTextView
    private lateinit var spinnerDayOfWeek: Spinner
    private lateinit var buttonSelectDate: MaterialButton
    private lateinit var textViewSelectedDate: View
    private lateinit var buttonStartTime: MaterialButton
    private lateinit var buttonEndTime: MaterialButton

    private var isEditing = false
    private var originalEvent: ManualEvent? = null
    private var selectedDate: Date? = null
    private var startTime24h: String? = null
    private var endTime24h: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[ScheduleViewModel::class.java]
        arguments?.let {
            originalEvent = it.getSerializable(KEY_EVENT) as? ManualEvent
            isEditing = originalEvent != null
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_manual_event, null)

        editTextTitle = view.findViewById(R.id.editTextEventTitle)
        editTextLocation = view.findViewById(R.id.editTextEventLocation)
        spinnerFrequencyType = view.findViewById(R.id.spinnerFrequencyType)
        spinnerDayOfWeek = view.findViewById(R.id.spinnerDayOfWeek)
        buttonSelectDate = view.findViewById(R.id.buttonSelectDate)
        textViewSelectedDate = view.findViewById(R.id.textViewSelectedDate)
        buttonStartTime = view.findViewById(R.id.buttonStartTime)
        buttonEndTime = view.findViewById(R.id.buttonEndTime)

        val frequencyOptions = listOf("Recurrente", "Único")
        spinnerFrequencyType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, frequencyOptions))
        spinnerFrequencyType.setText(frequencyOptions[0], false)

        val daysArray = resources.getStringArray(R.array.week_days)
        val dayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, daysArray)
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDayOfWeek.adapter = dayAdapter

        spinnerFrequencyType.setOnItemClickListener { _, _, position, _ ->
            toggleFrequencyInputs(position == 0)
        }

        buttonSelectDate.setOnClickListener { showDatePicker() }
        buttonStartTime.setOnClickListener { showTimePicker(true) }
        buttonEndTime.setOnClickListener { showTimePicker(false) }

        originalEvent?.let { event ->
            editTextTitle.setText(event.title)
            editTextLocation.setText(event.location)
            startTime24h = event.startTime
            endTime24h = event.endTime
            buttonStartTime.text = formatTo12Hour(event.startTime)
            buttonEndTime.text = formatTo12Hour(event.endTime)

            if (event.frequencyType == ManualEvent.FREQUENCY_UNIQUE) {
                spinnerFrequencyType.setText(frequencyOptions[1], false)
                toggleFrequencyInputs(false)
                event.date?.let { date ->
                    selectedDate = date
                    textViewSelectedDate.visibility = View.VISIBLE
                    textViewSelectedDate as android.widget.TextView
                    (textViewSelectedDate as android.widget.TextView).text = formatDate(date)
                }
            } else {
                spinnerFrequencyType.setText(frequencyOptions[0], false)
                toggleFrequencyInputs(true)
                event.dayOfWeek?.let { dow ->
                    val idx = if (dow == Calendar.SUNDAY) 6 else dow - 2
                    spinnerDayOfWeek.setSelection(idx.coerceIn(0, daysArray.size - 1))
                }
            }
        }

        val dialogTitle = if (isEditing) "Editar Evento" else "Nuevo Evento"

        builder.setView(view)
            .setTitle(dialogTitle)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar") { _, _ -> dismiss() }

        val dialog = builder.create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener { saveEvent() }
        }

        return dialog
    }

    private fun toggleFrequencyInputs(isRecurrent: Boolean) {
        if (isRecurrent) {
            spinnerDayOfWeek.visibility = View.VISIBLE
            buttonSelectDate.visibility = View.GONE
            textViewSelectedDate.visibility = View.GONE
        } else {
            spinnerDayOfWeek.visibility = View.GONE
            buttonSelectDate.visibility = View.VISIBLE
            textViewSelectedDate.visibility = View.VISIBLE
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        selectedDate?.let { calendar.time = it }
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedDate = calendar.time
                textViewSelectedDate.visibility = View.VISIBLE
                (textViewSelectedDate as android.widget.TextView).text = formatDate(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(isStart: Boolean) {
        val existing = (if (isStart) startTime24h else endTime24h) ?: "12:00"
        val parts = existing.split(":")
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(parts[0].toInt())
            .setMinute(parts[1].toInt())
            .setTitleText("Seleccionar hora")
            .build()

        picker.addOnPositiveButtonClickListener {
            val time24h = String.format(Locale.getDefault(), "%02d:%02d", picker.hour, picker.minute)
            if (isStart) {
                startTime24h = time24h
                buttonStartTime.text = formatTo12Hour(time24h)
            } else {
                endTime24h = time24h
                buttonEndTime.text = formatTo12Hour(time24h)
            }
        }
        picker.show(parentFragmentManager, "TimePicker")
    }

    private fun saveEvent() {
        val title = editTextTitle.text?.toString()?.trim() ?: ""
        if (TextUtils.isEmpty(title)) {
            editTextTitle.error = "El título no puede estar vacío"
            return
        }

        if (startTime24h == null || endTime24h == null) {
            Toast.makeText(context, "Define hora de inicio y fin.", Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        try {
            val st = sdf.parse(startTime24h!!)
            val et = sdf.parse(endTime24h!!)
            if (st != null && et != null && st.after(et)) {
                Toast.makeText(context, "La hora de fin no puede ser anterior a la de inicio.", Toast.LENGTH_LONG).show()
                return
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Formato de hora inválido.", Toast.LENGTH_SHORT).show()
            return
        }

        val isRecurrent = spinnerFrequencyType.text.toString() == "Recurrente"
        val frequencyType = if (isRecurrent) ManualEvent.FREQUENCY_RECURRENT else ManualEvent.FREQUENCY_UNIQUE
        val dayOfWeek = if (isRecurrent) {
            val idx = spinnerDayOfWeek.selectedItemPosition
            if (idx == 6) Calendar.SUNDAY else idx + 2
        } else null

        if (!isRecurrent && selectedDate == null) {
            Toast.makeText(context, "Selecciona una fecha para el evento único.", Toast.LENGTH_SHORT).show()
            return
        }

        val locationRaw = editTextLocation.text?.toString()?.trim()
        val sessionManager = SessionManager(requireContext())
        val owner = sessionManager.username
        val event = ManualEvent(
            id = originalEvent?.id ?: 0,
            title = title,
            location = if (locationRaw.isNullOrBlank()) null else locationRaw,
            startTime = startTime24h!!,
            endTime = endTime24h!!,
            frequencyType = frequencyType,
            dayOfWeek = dayOfWeek,
            date = if (!isRecurrent) selectedDate else null,
            owner = owner
        )

        if (isEditing) {
            viewModel.updateManualEvent(event, owner)
            Toast.makeText(context, "Evento actualizado", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addManualEvent(event, owner)
            Toast.makeText(context, "Evento creado", Toast.LENGTH_SHORT).show()
        }
        dismiss()
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

    private fun formatDate(date: Date): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
    }
}
