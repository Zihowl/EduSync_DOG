package dev.zihowl.dog.ui.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.ManualEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ManualEventsAdapter(
    private val onItemClick: (ManualEvent) -> Unit,
    private val onItemLongClick: (ManualEvent) -> Unit,
    private val onHeaderClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_EVENT = 1
    }

    private var displayList: List<Any> = emptyList()
    private var selectedItems: Set<ManualEvent> = emptySet()
    private var expandedHeaders: Set<String> = emptySet()

    fun submitList(newList: List<Any>) {
        this.displayList = newList
        notifyDataSetChanged()
    }

    fun setSelectedItems(selected: Set<ManualEvent>?) {
        this.selectedItems = selected ?: emptySet()
        notifyDataSetChanged()
    }

    fun setExpandedHeaders(headers: Set<String>) {
        this.expandedHeaders = headers
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (displayList[position] is String) VIEW_TYPE_HEADER else VIEW_TYPE_EVENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_manual_event_header, parent, false)
            HeaderViewHolder(view, onHeaderClick)
        } else {
            val view = inflater.inflate(R.layout.item_manual_event, parent, false)
            EventViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder.itemViewType == VIEW_TYPE_HEADER) {
            val header = displayList[position] as String
            (holder as HeaderViewHolder).bind(header, expandedHeaders.contains(header))
        } else {
            val event = displayList[position] as ManualEvent
            val eventHolder = holder as EventViewHolder
            eventHolder.bind(event, onItemClick, onItemLongClick)

            val cardView = holder.itemView as CardView
            if (selectedItems.contains(event)) {
                cardView.setCardBackgroundColor(MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimaryContainer))
            } else {
                cardView.setCardBackgroundColor(eventHolder.defaultCardBackgroundColor.defaultColor)
            }
        }
    }

    override fun getItemCount(): Int = displayList.size

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.textEventTitle)
        val time: TextView = itemView.findViewById(R.id.textEventTime)
        val location: TextView = itemView.findViewById(R.id.textEventLocation)
        val frequency: TextView = itemView.findViewById(R.id.textEventFrequency)
        val defaultCardBackgroundColor = (itemView as CardView).cardBackgroundColor

        private fun formatTime(time24: String?): String {
            if (time24.isNullOrEmpty()) return "--:--"
            return try {
                val sdf24 = SimpleDateFormat("HH:mm", Locale.getDefault())
                val sdf12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
                sdf12.format(sdf24.parse(time24)!!)
            } catch (_: Exception) {
                time24
            }
        }

        fun bind(
            event: ManualEvent,
            clickListener: (ManualEvent) -> Unit,
            longClickListener: (ManualEvent) -> Unit
        ) {
            title.text = event.title
            time.text = String.format(Locale.getDefault(), "%s - %s", formatTime(event.startTime), formatTime(event.endTime))

            if (!event.location.isNullOrBlank()) {
                location.text = event.location
                location.visibility = View.VISIBLE
            } else {
                location.visibility = View.GONE
            }

            frequency.text = when (event.frequencyType) {
                ManualEvent.FREQUENCY_RECURRENT -> {
                    when (event.dayOfWeek) {
                        Calendar.SUNDAY -> "Domingo"
                        Calendar.MONDAY -> "Lunes"
                        Calendar.TUESDAY -> "Martes"
                        Calendar.WEDNESDAY -> "Miércoles"
                        Calendar.THURSDAY -> "Jueves"
                        Calendar.FRIDAY -> "Viernes"
                        Calendar.SATURDAY -> "Sábado"
                        else -> "Desconocido"
                    }
                }
                ManualEvent.FREQUENCY_UNIQUE -> {
                    event.date?.let {
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
                    } ?: "Fecha desconocida"
                }
                else -> ""
            }

            itemView.setOnClickListener { clickListener(event) }
            itemView.setOnLongClickListener { longClickListener(event); true }
        }
    }

    class HeaderViewHolder(
        itemView: View,
        private val listener: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val headerTitle: TextView = itemView.findViewById(R.id.header_title)
        private val expandIcon: ImageView = itemView.findViewById(R.id.expand_icon)

        fun bind(title: String, isExpanded: Boolean) {
            headerTitle.text = title
            expandIcon.setImageResource(
                if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
            itemView.setOnClickListener { listener(title) }
        }
    }
}
