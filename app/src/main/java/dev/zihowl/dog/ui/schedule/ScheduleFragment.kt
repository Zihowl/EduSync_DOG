package dev.zihowl.dog.ui.schedule

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.google.android.material.color.MaterialColors
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
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
import com.google.android.material.card.MaterialCardView
import androidx.lifecycle.ViewModelProvider
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.Subject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Objects

class ScheduleFragment : Fragment() {

    private data class Event(
        val subject: Subject,
        val startMinutes: Float,
        val endMinutes: Float,
        var column: Int = 0,
        var totalColumns: Int = 1
    )

    private lateinit var viewModel: ScheduleViewModel
    private lateinit var scheduleContainer: FrameLayout
    private lateinit var hoursColumn: LinearLayout
    private lateinit var daySelectorSpinner: Spinner
    private lateinit var scheduleScrollView: ScrollView
    private lateinit var emptyScheduleText: TextView
    private var timeGrid: View? = null

    companion object {
        private const val HOUR_HEIGHT_DP = 75
        private const val DAY_WIDTH_DP = 300
        private const val START_HOUR = 0
        private const val END_HOUR = 24
    }

    private lateinit var weekDays: List<String>
    private var selectedDay: String? = null

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

        weekDays = resources.getStringArray(R.array.week_days).toList()
        viewModel = ViewModelProvider(requireActivity())[ScheduleViewModel::class.java]

        setupDaySelector()
        setupGrid()
        observeSubjects()
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
                observeSubjects()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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

    private fun observeSubjects() {
        viewModel.subjects.observe(viewLifecycleOwner) { subjects ->
            if (subjects != null && selectedDay != null) {
                drawSubjectsForDay(subjects, selectedDay!!)
            }
        }
    }

    private fun drawSubjectsForDay(subjects: List<Subject>, dayName: String) {
        clearEvents()

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val eventsToday = mutableListOf<Event>()
        var firstEventStart = -1f

        for (subject in subjects) {
            val schedule = subject.schedule ?: continue
            for (line in schedule.split("\n")) {
                if (!line.trim().startsWith(dayName)) continue
                try {
                    val parts = line.split(" ")
                    val times = line.substring(parts[0].length).trim().split(" - ")
                    val startMinutes = parseMinutes(sdf, times[0])
                    val endMinutes = parseMinutes(sdf, times[1])

                    if (startMinutes < endMinutes) {
                        eventsToday.add(Event(subject, startMinutes, endMinutes))
                        if (firstEventStart == -1f || startMinutes < firstEventStart) {
                            firstEventStart = startMinutes
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        if (eventsToday.isEmpty()) {
            emptyScheduleText.visibility = View.VISIBLE
            scheduleScrollView.alpha = 0f
        } else {
            emptyScheduleText.visibility = View.GONE
            scheduleScrollView.alpha = 1f
            renderEvents(eventsToday)

            val scrollPos = if (firstEventStart > 0) (firstEventStart / 60f) * HOUR_HEIGHT_DP else 0f
            scheduleScrollView.post {
                scheduleScrollView.scrollTo(0, dpToPx(scrollPos.toInt()))
            }
        }
    }

    private fun renderEvents(events: List<Event>) {
        if (events.isEmpty()) return

        val sortedEvents = events.sortedBy { it.startMinutes }
        val collisionGroups = mutableListOf<MutableList<Event>>()

        for (event in sortedEvents) {
            var placed = false
            for (group in collisionGroups) {
                if (group.any { doEventsOverlap(it, event) }) {
                    group.add(event)
                    placed = true
                    break
                }
            }
            if (!placed) {
                collisionGroups.add(mutableListOf(event))
            }
        }

        for (group in collisionGroups) {
            processCollisionGroup(group)
        }
    }

    private fun processCollisionGroup(group: List<Event>) {
        if (group.isEmpty()) return

        val columns = mutableListOf<MutableList<Event>>()
        columns.add(mutableListOf())

        for (event in group) {
            var placed = false
            for ((index, column) in columns.withIndex()) {
                if (column.isEmpty() || !doEventsOverlap(column.last(), event)) {
                    column.add(event)
                    event.column = index
                    placed = true
                    break
                }
            }
            if (!placed) {
                val newColumn = mutableListOf(event)
                event.column = columns.size
                columns.add(newColumn)
            }
        }

        val totalColumns = columns.size
        for (event in group) {
            event.totalColumns = totalColumns
            drawEventView(event)
        }
    }

    private fun drawEventView(event: Event) {
        val eventView = LayoutInflater.from(context).inflate(R.layout.item_schedule_event, scheduleContainer, false)
        val eventTextView = eventView.findViewById<TextView>(R.id.event_text)
        val cardView = eventView as? MaterialCardView

        val height = ((event.endMinutes - event.startMinutes) / 60f) * HOUR_HEIGHT_DP
        val topMargin = (event.startMinutes / 60f) * HOUR_HEIGHT_DP
        val colWidth = DAY_WIDTH_DP / event.totalColumns
        val leftMargin = event.column * dpToPx(colWidth)

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, (event.startMinutes / 60).toInt())
            set(Calendar.MINUTE, (event.startMinutes % 60).toInt())
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, (event.endMinutes / 60).toInt())
            set(Calendar.MINUTE, (event.endMinutes % 60).toInt())
        }
        val eventText = if (height < HOUR_HEIGHT_DP * 0.75f) {
            event.subject.name
        } else {
            String.format(
                "%s\n%s - %s",
                event.subject.name,
                timeFormat.format(startCal.time),
                timeFormat.format(endCal.time)
            )
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

    private fun doEventsOverlap(e1: Event, e2: Event): Boolean {
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
