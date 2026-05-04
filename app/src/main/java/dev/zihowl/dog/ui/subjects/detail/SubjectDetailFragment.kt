package dev.zihowl.dog.ui.subjects.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.Note
import dev.zihowl.dog.data.model.Subject
import dev.zihowl.dog.data.model.Task
import dev.zihowl.dog.ui.notes.NotesAdapter
import dev.zihowl.dog.ui.notes.NotesViewModel
import dev.zihowl.dog.ui.subjects.SubjectsViewModel
import dev.zihowl.dog.ui.tasks.TasksAdapter
import dev.zihowl.dog.ui.tasks.TasksViewModel

class SubjectDetailFragment : Fragment() {

    companion object {
        private const val ARG_SUBJECT_NAME = "subject_name"
        private const val HEADER_PENDING = "Pendientes"
        private const val HEADER_COMPLETED = "Completadas"

        fun newInstance(subjectName: String): SubjectDetailFragment {
            return SubjectDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SUBJECT_NAME, subjectName)
                }
            }
        }
    }

    private lateinit var subjectsViewModel: SubjectsViewModel
    private lateinit var tasksViewModel: TasksViewModel
    private lateinit var notesViewModel: NotesViewModel
    private var subjectName: String? = null
    private lateinit var tasksAdapter: TasksAdapter
    private lateinit var notesAdapter: NotesAdapter
    private var isPendingTasksExpanded = true
    private var isCompletedTasksExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { subjectName = it.getString(ARG_SUBJECT_NAME) }
        subjectsViewModel = ViewModelProvider(requireActivity())[SubjectsViewModel::class.java]
        tasksViewModel = ViewModelProvider(requireActivity())[TasksViewModel::class.java]
        notesViewModel = ViewModelProvider(requireActivity())[NotesViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_subject_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.title = "Detalles"

        setupAdapters()
        setupRecyclerViews(view)
        updateAllUI(view)
        setupObservers()
    }

    private fun setupAdapters() {
        tasksAdapter = TasksAdapter(
            onTaskStateChanged = { task -> tasksViewModel.toggleTaskCompletion(task) },
            onItemClick = { _ -> },
            onItemLongClick = { _ -> },
            onHeaderClick = { headerTitle ->
                when (headerTitle) {
                    HEADER_PENDING -> isPendingTasksExpanded = !isPendingTasksExpanded
                    HEADER_COMPLETED -> isCompletedTasksExpanded = !isCompletedTasksExpanded
                }
                updateAllUI(view)
            }
        )
        notesAdapter = NotesAdapter(
            onItemClick = { _, _ -> },
            onItemLongClick = { _, _ -> }
        )
    }

    private fun setupRecyclerViews(view: View) {
        val tasksRv = view.findViewById<RecyclerView>(R.id.recycler_view_tasks)
        tasksRv.layoutManager = LinearLayoutManager(context)
        tasksRv.adapter = tasksAdapter

        val notesRv = view.findViewById<RecyclerView>(R.id.recycler_view_notes)
        notesRv.layoutManager = LinearLayoutManager(context)
        notesRv.adapter = notesAdapter
    }

    private fun setupObservers() {
        subjectsViewModel.subjects.observe(viewLifecycleOwner) { updateAllUI(view) }
        tasksViewModel.pendingTasks.observe(viewLifecycleOwner) { updateAllUI(view) }
        tasksViewModel.completedTasks.observe(viewLifecycleOwner) { updateAllUI(view) }
        notesViewModel.notes.observe(viewLifecycleOwner) { updateAllUI(view) }
    }

    private fun updateAllUI(view: View?) {
        if (view == null) return
        val currentSubject = findCurrentSubject() ?: return
        updateSubjectInfo(view, currentSubject)
        updateTasksList(currentSubject)
        updateNotesList(currentSubject)
    }

    private fun findCurrentSubject(): Subject? {
        val subjects = subjectsViewModel.subjects.value
        val name = subjectName ?: return null
        return subjects?.find { it.name == name }
    }

    private fun updateSubjectInfo(view: View, subject: Subject) {
        view.findViewById<TextView>(R.id.detail_subject_name).text = subject.name
        view.findViewById<TextView>(R.id.detail_schedule).text = subject.schedule
        val professorNameTv = view.findViewById<TextView>(R.id.detail_professor_name)
        if (!subject.professorName.isNullOrEmpty()) {
            professorNameTv.text = subject.professorName
            professorNameTv.visibility = View.VISIBLE
        } else {
            professorNameTv.visibility = View.GONE
        }
    }

    private fun updateTasksList(subject: Subject) {
        val pending = filterTasks(tasksViewModel.pendingTasks.value, subject.name)
        val completed = filterTasks(tasksViewModel.completedTasks.value, subject.name)

        val displayList = mutableListOf<Any>()
        if (pending.isNotEmpty()) {
            displayList.add(HEADER_PENDING)
            if (isPendingTasksExpanded) displayList.addAll(pending)
        }
        if (completed.isNotEmpty()) {
            displayList.add(HEADER_COMPLETED)
            if (isCompletedTasksExpanded) displayList.addAll(completed)
        }
        tasksAdapter.submitList(displayList)
    }

    private fun updateNotesList(subject: Subject) {
        val allNotes = notesViewModel.notes.value
        val filtered = allNotes?.filter { it.subjectName == subject.name } ?: emptyList()
        notesAdapter.submitList(filtered)
    }

    private fun filterTasks(tasks: List<Task>?, subjectName: String): List<Task> {
        return tasks?.filter { it.subjectName == subjectName } ?: emptyList()
    }
}
