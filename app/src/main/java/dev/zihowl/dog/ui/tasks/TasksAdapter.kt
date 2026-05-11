package dev.zihowl.dog.ui.tasks

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import androidx.recyclerview.widget.RecyclerView
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.Task
import java.text.SimpleDateFormat
import java.util.Locale

class TasksAdapter(
    private val onTaskStateChanged: (Task) -> Unit,
    private val onItemClick: (Task) -> Unit,
    private val onItemLongClick: (Task) -> Unit,
    private val onHeaderClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_TASK = 1
    }

    private var displayList: List<Any> = emptyList()
    private var selectedItems: Set<Task> = emptySet()

    fun submitList(newList: List<Any>) {
        this.displayList = newList
        notifyDataSetChanged()
    }

    fun setSelectedItems(selected: Set<Task>) {
        this.selectedItems = selected
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (displayList[position] is String) VIEW_TYPE_HEADER else VIEW_TYPE_TASK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_task_header, parent, false)
            HeaderViewHolder(view, onHeaderClick)
        } else {
            val view = inflater.inflate(R.layout.item_task, parent, false)
            TaskViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder.itemViewType == VIEW_TYPE_HEADER) {
            (holder as HeaderViewHolder).bind(displayList[position] as String)
        } else {
            val task = displayList[position] as Task
            val taskHolder = holder as TaskViewHolder
            taskHolder.bind(task, onTaskStateChanged, onItemClick, onItemLongClick)

            val cardView = holder.itemView as CardView
            if (selectedItems.contains(task)) {
                cardView.setCardBackgroundColor(MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimaryContainer))
            } else {
                cardView.setCardBackgroundColor(taskHolder.defaultCardBackgroundColor)
            }
        }
    }

    override fun getItemCount(): Int = displayList.size

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.textViewTaskTitle)
        val description: TextView = itemView.findViewById(R.id.textViewTaskDescription)
        val dueDate: TextView = itemView.findViewById(R.id.textViewTaskDueDate)
        val subjectName: TextView = itemView.findViewById(R.id.textViewTaskSubject)
        val priority: TextView = itemView.findViewById(R.id.textViewTaskPriority)
        val completedCheckBox: CheckBox = itemView.findViewById(R.id.checkBoxTaskCompleted)
        val defaultCardBackgroundColor: Int = (itemView as CardView).cardBackgroundColor.defaultColor

        fun bind(
            task: Task,
            listener: (Task) -> Unit,
            clickListener: (Task) -> Unit,
            longClickListener: (Task) -> Unit
        ) {
            title.text = task.title
            description.text = task.description
            description.visibility = if (!task.description.isNullOrEmpty()) View.VISIBLE else View.GONE

            if (task.dueDate != null) {
                dueDate.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(task.dueDate)
                dueDate.visibility = View.VISIBLE
            } else {
                dueDate.visibility = View.GONE
            }

            if (!task.subjectName.isNullOrEmpty()) {
                subjectName.text = task.subjectName
                subjectName.visibility = View.VISIBLE
            } else {
                subjectName.visibility = View.GONE
            }

            priority.text = "Prioridad: " + when (task.priority) {
                Task.PRIORITY_HIGH -> "Alta"
                Task.PRIORITY_LOW -> "Baja"
                else -> "Media"
            }

            completedCheckBox.isChecked = task.status == Task.STATUS_COMPLETED || task.status == Task.STATUS_NOT_COMPLETED
            title.paintFlags = if (task.status != Task.STATUS_PENDING) {
                title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            itemView.alpha = if (task.status != Task.STATUS_PENDING) 0.6f else 1.0f

            val cardView = itemView as androidx.cardview.widget.CardView
            if (task.status == Task.STATUS_NOT_COMPLETED) {
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.task_not_completed_background))
            } else {
                cardView.setCardBackgroundColor(defaultCardBackgroundColor)
            }

            itemView.setOnClickListener { clickListener(task) }
            itemView.setOnLongClickListener { longClickListener(task); true }
            completedCheckBox.setOnClickListener { listener(task) }
        }
    }

    class HeaderViewHolder(
        itemView: View,
        private val listener: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val headerTitle: TextView = itemView.findViewById(R.id.header_title)
        private val expandIcon: ImageView = itemView.findViewById(R.id.expand_icon)

        fun bind(title: String) {
            headerTitle.text = title
            itemView.setOnClickListener { listener(title) }
        }
    }
}
