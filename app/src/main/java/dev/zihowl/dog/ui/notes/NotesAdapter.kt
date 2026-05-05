package dev.zihowl.dog.ui.notes

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
import dev.zihowl.dog.data.model.Note

class NotesAdapter(
    private val onItemClick: (Note, Int) -> Unit,
    private val onItemLongClick: (Note, Int) -> Unit,
    private val onAttachmentClick: (Note) -> Unit = {}
) : ListAdapter<Note, NotesAdapter.NoteViewHolder>(DIFF_CALLBACK) {

    private var selectedItems: Set<Note> = emptySet()

    fun setSelectedItems(selected: Set<Note>?) {
        selectedItems = selected ?: emptySet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = getItem(position)
        holder.bind(note, onItemClick, onItemLongClick, onAttachmentClick)

        val cardView = holder.itemView as CardView
        if (selectedItems.contains(note)) {
            cardView.setCardBackgroundColor(MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimaryContainer))
        } else {
            cardView.setCardBackgroundColor(holder.defaultCardBackgroundColor.defaultColor)
        }
    }

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.textViewNoteTitle)
        val content: TextView = itemView.findViewById(R.id.textViewNoteContent)
        val subjectName: TextView = itemView.findViewById(R.id.textViewNoteSubject)
        val openAttachmentButton: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.buttonOpenAttachment)
        val defaultCardBackgroundColor = (itemView as CardView).cardBackgroundColor

        fun bind(
            note: Note,
            clickListener: (Note, Int) -> Unit,
            longClickListener: (Note, Int) -> Unit,
            attachmentClickListener: (Note) -> Unit
        ) {
            title.text = note.title
            content.text = note.content
            if (!note.subjectName.isNullOrEmpty()) {
                subjectName.text = note.subjectName
                subjectName.visibility = View.VISIBLE
            } else {
                subjectName.visibility = View.GONE
            }

            if (!note.attachmentPath.isNullOrEmpty()) {
                openAttachmentButton.visibility = View.VISIBLE
                openAttachmentButton.text = note.attachmentName ?: "Ver adjunto"
                openAttachmentButton.setOnClickListener { attachmentClickListener(note) }
            } else {
                openAttachmentButton.visibility = View.GONE
                openAttachmentButton.setOnClickListener(null)
            }

            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    clickListener(note, position)
                }
            }
            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    longClickListener(note, position)
                }
                true
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Note>() {
            override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
                return oldItem.title == newItem.title
                        && oldItem.content == newItem.content
                        && oldItem.subjectName == newItem.subjectName
                        && oldItem.attachmentPath == newItem.attachmentPath
                        && oldItem.attachmentName == newItem.attachmentName
            }
        }
    }
}
