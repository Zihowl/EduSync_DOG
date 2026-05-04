package dev.zihowl.dog.ui.subjects

import com.google.android.material.color.MaterialColors
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.Subject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Objects

class SubjectsAdapter(
    private val onItemClick: (Subject, Int) -> Unit,
    private val onItemLongClick: (Subject, Int) -> Unit
) : ListAdapter<Subject, SubjectsAdapter.SubjectViewHolder>(DIFF_CALLBACK) {

    private var selectedItems: Set<Subject> = emptySet()

    fun setSelectedItems(selected: Set<Subject>?) {
        selectedItems = selected ?: emptySet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_subject, parent, false)
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        val subject = getItem(position)
        holder.bind(subject, onItemClick, onItemLongClick)

        val cardView = holder.itemView as CardView
        if (selectedItems.contains(subject)) {
            cardView.setCardBackgroundColor(MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimaryContainer))
        } else {
            cardView.setCardBackgroundColor(holder.defaultCardBackgroundColor.defaultColor)
        }
    }

    class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.textViewSubjectName)
        val professorName: TextView = itemView.findViewById(R.id.textViewProfessorName)
        val schedule: TextView = itemView.findViewById(R.id.textViewSubjectSchedule)
        val tasksPending: TextView = itemView.findViewById(R.id.textViewTasksPending)
        val notesCount: TextView = itemView.findViewById(R.id.textViewNotesCount)
        val defaultCardBackgroundColor = (itemView as CardView).cardBackgroundColor

        fun bind(
            subject: Subject,
            clickListener: (Subject, Int) -> Unit,
            longClickListener: (Subject, Int) -> Unit
        ) {
            name.text = subject.name
            schedule.text = formatSchedule(subject.schedule)

            if (!subject.professorName.isNullOrEmpty()) {
                professorName.text = subject.professorName
                professorName.visibility = View.VISIBLE
            } else {
                professorName.visibility = View.GONE
            }

            val res = itemView.resources
            tasksPending.text = res.getString(R.string.subject_tasks_pending, subject.tasksPending)
            notesCount.text = res.getString(R.string.subject_notes_count, subject.notesCount)

            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    clickListener(subject, position)
                }
            }
            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    longClickListener(subject, position)
                }
                true
            }
        }

        private fun formatSchedule(schedule24h: String?): String {
            if (schedule24h.isNullOrEmpty()) return "Sin horario"
            val lines = schedule24h.split("\n")
            val formattedSchedule = StringBuilder()
            for (line in lines) {
                try {
                    val parts = line.split(" ")
                    val day = parts[0]
                    val times = line.substring(day.length).trim().split(" - ")
                    formattedSchedule.append("$day ${formatTo12Hour(times[0])} - ${formatTo12Hour(times[1])}\n")
                } catch (e: Exception) {
                    formattedSchedule.append("$line\n")
                }
            }
            return formattedSchedule.toString().trim()
        }

        private fun formatTo12Hour(time24h: String?): String {
            if (time24h.isNullOrEmpty()) return ""
            return try {
                val sdf24 = SimpleDateFormat("HH:mm", Locale.getDefault())
                val sdf12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
                sdf12.format(Objects.requireNonNull(sdf24.parse(time24h)))
            } catch (e: Exception) {
                time24h
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Subject>() {
            override fun areItemsTheSame(oldItem: Subject, newItem: Subject): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Subject, newItem: Subject): Boolean {
                return oldItem.name == newItem.name
                        && oldItem.professorName == newItem.professorName
                        && oldItem.schedule == newItem.schedule
                        && oldItem.tasksPending == newItem.tasksPending
                        && oldItem.notesCount == newItem.notesCount
            }
        }
    }
}
