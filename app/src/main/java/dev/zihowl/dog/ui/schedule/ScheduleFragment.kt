package dev.zihowl.dog.ui.schedule

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.google.android.material.color.MaterialColors
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.ManualEvent
import dev.zihowl.dog.data.model.Subject
import dev.zihowl.dog.ui.main.MainActivity
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
    private lateinit var scheduleScrollView: ScrollView
    private lateinit var emptyScheduleText: TextView
    private lateinit var buttonSelectDate: MaterialButton
    private lateinit var textSelectedDay: TextView
    private lateinit var recyclerViewManualEvents: RecyclerView
    private lateinit var adapter: ManualEventsAdapter
    private lateinit var backPressedCallback: OnBackPressedCallback
    private var timeGrid: View? = null

    companion object {
        private const val HOUR_HEIGHT_DP = 75
        private const val DAY_WIDTH_DP = 300
        private const val START_HOUR = 0
        private const val END_HOUR = 24
        private const val HEADER_UNIQUE = "Eventos únicos"
        private const val HEADER_RECURRENT = "Eventos recurrentes"
    }

    private lateinit var weekDays: List<String>
    private var selectedDate: Date = Date()
    private var allSubjects: List<Subject> = emptyList()
    private var allManualEvents: List<ManualEvent> = emptyList()
    private var isListView = false
    private var selectedManualEvents = mutableSetOf<ManualEvent>()
    private var isSelectionMode = false
    private var isUniqueExpanded = true
    private var isRecurrentExpanded = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                finishSelectionMode()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleContainer = view.findViewById(R.id.schedule_container)
        hoursColumn = view.findViewById(R.id.hours_column)
        scheduleScrollView = view.findViewById(R.id.schedule_scroll_view)
        emptyScheduleText = view.findViewById(R.id.empty_schedule_text)
        buttonSelectDate = view.findViewById(R.id.buttonSelectDate)
        textSelectedDay = view.findViewById(R.id.textSelectedDay)
        recyclerViewManualEvents = view.findViewById(R.id.recyclerViewManualEvents)

        weekDays = resources.getStringArray(R.array.week_days).toList()
        viewModel = ViewModelProvider(requireActivity())[ScheduleViewModel::class.java]

        setupDatePicker()
        setupGrid()
        setupManualEventsList()
        setupMenu()
        observeData()
        updateSelectedDayText()
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
                    updateSelectedDayText()
                    refreshSchedule()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun updateSelectedDayText() {
        val cal = Calendar.getInstance().apply { time = selectedDate }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val idx = if (dow == Calendar.SUNDAY) 6 else dow - 2
        textSelectedDay.text = weekDays.getOrNull(idx) ?: ""
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

    private fun setupManualEventsList() {
        recyclerViewManualEvents.layoutManager = LinearLayoutManager(context)
        adapter = ManualEventsAdapter(
            onItemClick = { event ->
                if (isSelectionMode) {
                    toggleSelection(event)
                } else {
                    showEditEventDialog(event)
                }
            },
            onItemLongClick = { event ->
                if (!isSelectionMode) startSelectionMode()
                toggleSelection(event)
            },
            onHeaderClick = { headerTitle ->
                when (headerTitle) {
                    HEADER_UNIQUE -> isUniqueExpanded = !isUniqueExpanded
                    HEADER_RECURRENT -> isRecurrentExpanded = !isRecurrentExpanded
                }
                refreshManualEventsList()
            }
        )
        recyclerViewManualEvents.adapter = adapter
    }

    private fun showEditEventDialog(event: ManualEvent) {
        AddManualEventDialogFragment.newInstance(event)
            .show(parentFragmentManager, "EditManualEventDialog")
    }

    private fun showDeleteEventConfirmation(events: List<ManualEvent>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar el evento seleccionado?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteManualEvents(events)
                finishSelectionMode()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startSelectionMode() {
        isSelectionMode = true
        selectedManualEvents.clear()
        adapter.setSelectedItems(selectedManualEvents)
        backPressedCallback.isEnabled = true
        requireActivity().invalidateOptionsMenu()
    }

    private fun finishSelectionMode() {
        isSelectionMode = false
        selectedManualEvents.clear()
        adapter.setSelectedItems(selectedManualEvents)
        backPressedCallback.isEnabled = false
        updateActionBarTitle("Horario")
        requireActivity().invalidateOptionsMenu()
    }

    private fun toggleSelection(event: ManualEvent) {
        if (selectedManualEvents.contains(event)) {
            selectedManualEvents.remove(event)
        } else {
            selectedManualEvents.add(event)
        }
        adapter.setSelectedItems(selectedManualEvents)
        val count = selectedManualEvents.size
        if (count == 0) {
            finishSelectionMode()
        } else {
            updateActionBarTitle("$count seleccionados")
        }
        requireActivity().invalidateOptionsMenu()
    }

    private fun observeData() {
        viewModel.subjects.observe(viewLifecycleOwner) { subjects ->
            allSubjects = subjects ?: emptyList()
            refreshSchedule()
        }
        viewModel.manualEvents.observe(viewLifecycleOwner) { events ->
            allManualEvents = events ?: emptyList()
            refreshSchedule()
            refreshManualEventsList()
        }
    }

    private fun refreshSchedule() {
        if (isListView) return
        val cal = Calendar.getInstance().apply { time = selectedDate }
        val selectedDow = cal.get(Calendar.DAY_OF_WEEK)
        val dayName = weekDays.getOrNull(if (selectedDow == Calendar.SUNDAY) 6 else selectedDow - 2) ?: return
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

    private fun refreshManualEventsList() {
        if (!isListView) return
        val today = Date()

        val uniqueEvents = allManualEvents.filter {
            it.frequencyType == ManualEvent.FREQUENCY_UNIQUE && it.date != null
        }.sortedWith(compareBy { Math.abs(it.date!!.time - today.time) })

        val recurrentEvents = allManualEvents.filter {
            it.frequencyType == ManualEvent.FREQUENCY_RECURRENT
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))

        val displayList = mutableListOf<Any>()
        if (uniqueEvents.isNotEmpty()) {
            displayList.add(HEADER_UNIQUE)
            if (isUniqueExpanded) displayList.addAll(uniqueEvents)
        }
        if (recurrentEvents.isNotEmpty()) {
            displayList.add(HEADER_RECURRENT)
            if (isRecurrentExpanded) displayList.addAll(recurrentEvents)
        }

        adapter.submitList(displayList)
        adapter.setExpandedHeaders(setOf(
            HEADER_UNIQUE.takeIf { isUniqueExpanded },
            HEADER_RECURRENT.takeIf { isRecurrentExpanded }
        ).filterNotNull().toSet())

        emptyScheduleText.visibility = if (uniqueEvents.isEmpty() && recurrentEvents.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleViewMode() {
        isListView = !isListView
        if (isListView) {
            scheduleScrollView.visibility = View.GONE
            hoursColumn.visibility = View.GONE
            recyclerViewManualEvents.visibility = View.VISIBLE
            buttonSelectDate.visibility = View.GONE
            textSelectedDay.visibility = View.GONE
            refreshManualEventsList()
        } else {
            scheduleScrollView.visibility = View.VISIBLE
            hoursColumn.visibility = View.VISIBLE
            recyclerViewManualEvents.visibility = View.GONE
            buttonSelectDate.visibility = View.VISIBLE
            textSelectedDay.visibility = View.VISIBLE
            refreshSchedule()
        }
        finishSelectionMode()
        requireActivity().invalidateOptionsMenu()
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
        val marginPx = dpToPx(2)

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, (item.startMinutes / 60).toInt())
            set(Calendar.MINUTE, (item.startMinutes % 60).toInt())
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, (item.endMinutes / 60).toInt())
            set(Calendar.MINUTE, (item.endMinutes % 60).toInt())
        }
        val locText = if (!item.location.isNullOrBlank()) " @${item.location}" else ""
        val isShort = height < HOUR_HEIGHT_DP * 0.75f
        val isNarrow = colWidth < 120
        val approxLineHeightDp = 14
        val maxLines = if (isShort) 1 else (height / approxLineHeightDp).toInt().coerceAtLeast(2)

        val eventText = when {
            isShort -> item.name + locText
            isNarrow -> String.format(
                "%s%s\n%s\n%s",
                item.name,
                locText,
                timeFormat.format(startCal.time),
                timeFormat.format(endCal.time)
            )
            else -> String.format(
                "%s%s\n%s - %s",
                item.name,
                locText,
                timeFormat.format(startCal.time),
                timeFormat.format(endCal.time)
            )
        }

        cardView?.setContentPadding(0, 0, 0, 0)
        eventTextView.includeFontPadding = false
        if (isShort) {
            eventTextView.maxLines = 1
        } else {
            eventTextView.maxLines = maxLines
        }
        eventTextView.ellipsize = TextUtils.TruncateAt.END
        eventTextView.text = eventText

        val params = FrameLayout.LayoutParams(dpToPx(colWidth) - marginPx * 2, dpToPx(height.toInt()) - marginPx * 2)
        params.topMargin = dpToPx(topMargin.toInt()) + marginPx
        params.leftMargin = leftMargin + marginPx
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

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onPrepareMenu(menu: Menu) {
                if (!isResumed || isNotCurrentFragment()) return
                val isSelection = isSelectionMode
                val selectedCount = selectedManualEvents.size
                menu.findItem(R.id.action_add)?.isVisible = !isSelection && !isListView
                menu.findItem(R.id.action_delete)?.isVisible = isSelection
                menu.findItem(R.id.action_edit)?.isVisible = isSelection && selectedCount == 1
                val toggleItem = menu.findItem(R.id.action_toggle_view)
                toggleItem?.isVisible = !isSelection
                toggleItem?.setIcon(if (isListView) R.drawable.ic_view_calendar else R.drawable.ic_view_list)
                toggleItem?.title = if (isListView) "Ver calendario" else "Ver lista"
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (!isResumed || isNotCurrentFragment()) return false
                return when (menuItem.itemId) {
                    R.id.action_delete -> {
                        if (isSelectionMode && selectedManualEvents.isNotEmpty()) {
                            showDeleteEventConfirmation(selectedManualEvents.toList())
                        }
                        true
                    }
                    R.id.action_edit -> {
                        if (isSelectionMode && selectedManualEvents.size == 1) {
                            showEditEventDialog(selectedManualEvents.first())
                        }
                        true
                    }
                    R.id.action_toggle_view -> {
                        toggleViewMode()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun updateActionBarTitle(title: String) {
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
    }

    private fun isNotCurrentFragment(): Boolean {
        return (activity as? MainActivity)?.isCurrentTab(3) != true
    }
}
