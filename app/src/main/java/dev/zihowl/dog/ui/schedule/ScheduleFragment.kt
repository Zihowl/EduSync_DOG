package dev.zihowl.dog.ui.schedule

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.google.android.material.color.MaterialColors
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.ManualEvent
import dev.zihowl.dog.data.model.Subject
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ScheduleFragment : Fragment() {

    private data class ScheduleItem(
        val name: String,
        val location: String?,
        val startMinutes: Float,
        val endMinutes: Float,
        val color: Int = Color.parseColor("#FF6D00"),
        var column: Int = 0,
        var totalColumns: Int = 1
    )

    private lateinit var viewModel: ScheduleViewModel
    private lateinit var scheduleContainer: FrameLayout
    private lateinit var hoursColumn: LinearLayout
    private lateinit var daySelectorSpinner: Spinner
    private lateinit var scheduleScrollView: ScrollView
    private lateinit var emptyScheduleText: TextView
    private lateinit var buttonSelectDate: MaterialButton
    private var timeGrid: View? = null

    companion object {
        private const val HOUR_HEIGHT_DP = 75
        private const val DAY_WIDTH_DP = 300
        private const val START_HOUR = 0
        private const val END_HOUR = 24
    }

    private lateinit var weekDays: List<String>
    private var selectedDay: String? = null
    private var selectedDate: Date = Date()
    private var allSubjects: List<Subject> = emptyList()
    private var allManualEvents: List<ManualEvent> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleContainer = view.findViewById(R.id.schedule_container)
        hoursColumn = view.findViewById(R.id.hours_column)
        daySelectorSpinner = view.findViewById(R.id.day_selector_spinner)
        scheduleScrollView = view.findViewById(R.id.schedule_scroll_view)
        emptyScheduleText = view.findViewById(R.id.empty_schedule_text)
        buttonSelectDate = view.findViewById(R.id.buttonSelectDate)

        weekDays = resources.getStringArray(R.array.week_days).toList()
        viewModel = ViewModelProvider(requireActivity())[ScheduleViewModel::class.java]

        setupDatePicker()
        setupDaySelector()
        setupGrid()
        observeData()
    }

    private fun setupDaySelector() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, weekDays)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        daySelectorSpinner.adapter = adapter

        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val todayIndex = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2
        daySelectorSpinner.setSelection(todayIndex)
        selectedDay = weekDays.getOrNull(todayIndex)

        daySelectorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDay = weekDays[position]
                refreshSchedule()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDatePicker() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        buttonSelectDate.text = sdf.format(selectedDate)
        buttonSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance().apply { time = selectedDate }
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDate = calendar.time
                    buttonSelectDate.text = sdf.format(selectedDate)
                    val dow = calendar.get(Calendar.DAY_OF_WEEK)
                    val idx = if (dow == Calendar.SUNDAY) 6 else dow - 2
                    daySelectorSpinner.setSelection(idx)
                    selectedDay = weekDays[idx]
                    refreshSchedule()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupGrid() {
        hoursColumn.removeAllViews()
        for (hour in START_HOUR until END_HOUR) {
            hoursColumn.addView(createHourTextView(hour))
        }

        timeGrid = View(context).apply {
            val params = LinearLayout.LayoutParams(dpToPx(DAY_WIDTH_DP), dpToPx(HOUR_HEIGHT_DP * (END_HOUR - START_HOUR)))
            val gridDrawable = GradientDrawable().apply { setStroke(1, MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOutline, Color.LTGRAY)) }
            background = gridDrawable
            layoutParams = params
        }
        scheduleContainer.addView(timeGrid)
    }

    private fun observeData() {
        viewModel.subjects.observe(viewLifecycleOwner) { subjects ->
            allSubjects = subjects ?: emptyList()
            refreshSchedule()
        }
        viewModel.manualEvents.observe(viewLifecycleOwner) { events ->
            allManualEvents = events ?: emptyList()
            refreshSchedule()
        }
    }

    private fun refreshSchedule() {
        val dayName = selectedDay ?: return
        clearEvents()

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val itemsToday = mutableListOf<ScheduleItem>()
        var firstEventStart = -1f

        for (subject in allSubjects) {
            val schedule = subject.schedule ?: continue
            for (line in schedule.split("\n")) {
                if (!line.trim().startsWith(dayName)) continue
                try {
                    val parts = line.split(" ")
                    val times = line.substring(parts[0].length).trim().split(" - ")
                    val startMinutes = parseMinutes(sdf, times[0])
                    val endMinutes = parseMinutes(sdf, times[1])
                    if (startMinutes < endMinutes) {
                        itemsToday.add(ScheduleItem(subject.name, null, startMinutes, endMinutes))
                        if (firstEventStart == -1f || startMinutes < firstEventStart) firstEventStart = startMinutes
                    }
                } catch (_: Exception) { }
            }
        }

        val cal = Calendar.getInstance().apply { time = selectedDate }
        val selectedDow = cal.get(Calendar.DAY_OF_WEEK)
        val selectedDateOnly = cal.time

        for (event in allManualEvents) {
            val include = when (event.frequencyType) {
                ManualEvent.FREQUENCY_RECURRENT -> event.dayOfWeek == selectedDow
                ManualEvent.FREQUENCY_UNIQUE -> {
                    event.date?.let { d ->
                        val eventCal = Calendar.getInstance().apply { time = d }
                        eventCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                        eventCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
                    } ?: false
                }
                else -> false
            }
            if (include) {
                try {
                    val startMinutes = parseMinutes(sdf, event.startTime)
                    val endMinutes = parseMinutes(sdf, event.endTime)
                    if (startMinutes < endMinutes) {
                        itemsToday.add(ScheduleItem(event.title, event.location, startMinutes, endMinutes, Color.parseColor("#4CAF50")))
                        if (firstEventStart == -1f || startMinutes < firstEventStart) firstEventStart = startMinutes
                    }
                } catch (_: Exception) { }
            }
        }

        if (itemsToday.isEmpty()) {
            emptyScheduleText.visibility = View.VISIBLE
            scheduleScrollView.alpha = 0f
        } else {
            emptyScheduleText.visibility = View.GONE
            scheduleScrollView.alpha = 1f
            renderItems(itemsToday)
            val scrollPos = if (firstEventStart > 0) (firstEventStart / 60f) * HOUR_HEIGHT_DP else 0f
            scheduleScrollView.post { scheduleScrollView.scrollTo(0, dpToPx(scrollPos.toInt())) }
        }
    }

    private fun renderItems(items: List<ScheduleItem>) {
        if (items.isEmpty()) return
        val sorted = items.sortedBy { it.startMinutes }
        val collisionGroups = mutableListOf<MutableList<ScheduleItem>>()
        for (item in sorted) {
            var placed = false
            for (group in collisionGroups) {
                if (group.any { doItemsOverlap(it, item) }) {
                    group.add(item)
                    placed = true
                    break
                }
            }
            if (!placed) collisionGroups.add(mutableListOf(item))
        }
        for (group in collisionGroups) {
            processCollisionGroup(group)
        }
    }

    private fun processCollisionGroup(group: List<ScheduleItem>) {
        if (group.isEmpty()) return
        val columns = mutableListOf<MutableList<ScheduleItem>>()
        columns.add(mutableListOf())
        for (item in group) {
            var placed = false
            for ((index, column) in columns.withIndex()) {
                if (column.isEmpty() || !doItemsOverlap(column.last(), item)) {
                    column.add(item)
                    item.column = index
                    placed = true
                    break
                }
            }
            if (!placed) {
                val newColumn = mutableListOf(item)
                item.column = columns.size
                columns.add(newColumn)
            }
        }
        val totalColumns = columns.size
        for (item in group) {
            item.totalColumns = totalColumns
            drawItemView(item)
        }
    }

    private fun drawItemView(item: ScheduleItem) {
        val eventView = LayoutInflater.from(context).inflate(R.layout.item_schedule_event, scheduleContainer, false)
        val eventTextView = eventView.findViewById<TextView>(R.id.event_text)
        val cardView = eventView as? MaterialCardView
        cardView?.setCardBackgroundColor(item.color)

        val height = ((item.endMinutes - item.startMinutes) / 60f) * HOUR_HEIGHT_DP
        val topMargin = (item.startMinutes / 60f) * HOUR_HEIGHT_DP
        val colWidth = DAY_WIDTH_DP / item.totalColumns
        val leftMargin = item.column * dpToPx(colWidth)

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, (item.startMinutes / 60).toInt())
            set(Calendar.MINUTE, (item.startMinutes % 60).toInt())
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, (item.endMinutes / 60).toInt())
            set(Calendar.MINUTE, (item.endMinutes % 60).toInt())
        }
        val locText = item.location?.let { " @$it" } ?: ""
        val eventText = if (height < HOUR_HEIGHT_DP * 0.75f) {
            item.name + locText
        } else {
            String.format("%s%s\n%s - %s", item.name, locText, timeFormat.format(startCal.time), timeFormat.format(endCal.time))
        }
        if (height < HOUR_HEIGHT_DP * 0.75f) {
            eventTextView.setPadding(0, 0, 0, 0)
            eventTextView.maxLines = 1
            eventTextView.ellipsize = TextUtils.TruncateAt.END
            eventTextView.includeFontPadding = false
            cardView?.setContentPadding(0, 0, 0, 0)
        } else {
            eventTextView.maxLines = 2
            eventTextView.ellipsize = null
            eventTextView.includeFontPadding = true
        }
        eventTextView.text = eventText

        val params = FrameLayout.LayoutParams(dpToPx(colWidth), dpToPx(height.toInt()))
        params.topMargin = dpToPx(topMargin.toInt())
        params.leftMargin = leftMargin
        params.gravity = Gravity.TOP or Gravity.START

        scheduleContainer.addView(eventView, params)
    }

    private fun doItemsOverlap(e1: ScheduleItem, e2: ScheduleItem): Boolean {
        return e1.startMinutes < e2.endMinutes && e2.startMinutes < e1.endMinutes
    }

    @Throws(ParseException::class)
    private fun parseMinutes(sdf: SimpleDateFormat, timeStr: String): Float {
        val date = sdf.parse(timeStr) ?: throw ParseException("Invalid time", 0)
        val cal = Calendar.getInstance().also { it.time = date }
        return (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)).toFloat()
    }

    private fun clearEvents() {
        for (i in scheduleContainer.childCount - 1 downTo 1) {
            scheduleContainer.removeViewAt(i)
        }
    }

    private fun createHourTextView(hour: Int): TextView {
        return TextView(requireContext()).apply {
            text = String.format(Locale.getDefault(), "%02d:00", hour)
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(HOUR_HEIGHT_DP))
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
