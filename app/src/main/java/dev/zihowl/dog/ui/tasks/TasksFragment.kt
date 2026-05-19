package dev.zihowl.dog.ui.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.Task
import dev.zihowl.dog.data.remote.CollaborationClient
import dev.zihowl.dog.data.remote.displayIdentity
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.ui.main.MainActivity
import dev.zihowl.dog.ui.sharedtasks.SharedTaskCodec
import kotlinx.coroutines.launch

class TasksFragment : Fragment() {

    private lateinit var viewModel: TasksViewModel
    private lateinit var adapter: TasksAdapter
    private lateinit var backPressedCallback: OnBackPressedCallback
    private var emptyText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[TasksViewModel::class.java]

        backPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                viewModel.finishSelectionMode()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewTasks)
        recyclerView.layoutManager = LinearLayoutManager(context)
        emptyText = view.findViewById(R.id.text_empty_tasks)

        adapter = TasksAdapter(
            onTaskStateChanged = { task -> viewModel.toggleTaskCompletion(task) },
            onItemClick = { task ->
                if (viewModel.isSelectionMode.value == true) {
                    viewModel.toggleSelection(task)
                } else {
                    handleEditAction(task)
                }
            },
            onItemLongClick = { task -> viewModel.toggleSelection(task) },
            onHeaderClick = { headerTitle ->
                when (headerTitle) {
                    HEADER_PENDING -> viewModel.togglePendingExpansion()
                    HEADER_NOT_COMPLETED -> viewModel.toggleNotCompletedExpansion()
                    HEADER_COMPLETED -> viewModel.toggleCompletedExpansion()
                }
            }
        )
        recyclerView.adapter = adapter
        setupObservers()
    }

    private fun setupObservers() {
        viewModel.pendingTasks.observe(viewLifecycleOwner) { buildDisplayList() }
        viewModel.completedTasks.observe(viewLifecycleOwner) { buildDisplayList() }
        viewModel.notCompletedTasks.observe(viewLifecycleOwner) { buildDisplayList() }
        viewModel.isPendingExpanded.observe(viewLifecycleOwner) { buildDisplayList() }
        viewModel.isCompletedExpanded.observe(viewLifecycleOwner) { buildDisplayList() }
        viewModel.isNotCompletedExpanded.observe(viewLifecycleOwner) { buildDisplayList() }

        viewModel.isSelectionMode.observe(viewLifecycleOwner) { isSelection ->
            backPressedCallback.isEnabled = isSelection
            if (!isSelection) updateActionBarTitle("Tareas")
            requireActivity().invalidateOptionsMenu()
        }

        viewModel.selectedTasks.observe(viewLifecycleOwner) { selectedTasks ->
            adapter.setSelectedItems(selectedTasks)
            if (viewModel.isSelectionMode.value == true) {
                val count = selectedTasks.size
                if (count == 0) {
                    viewModel.finishSelectionMode()
                } else {
                    updateActionBarTitle("$count seleccionadas")
                }
            }
            requireActivity().invalidateOptionsMenu()
        }
    }

    private fun buildDisplayList() {
        val displayList = mutableListOf<Any>()
        val pending = viewModel.pendingTasks.value
        val notCompleted = viewModel.notCompletedTasks.value
        val completed = viewModel.completedTasks.value

        if (!pending.isNullOrEmpty()) {
            displayList.add(HEADER_PENDING)
            if (viewModel.isPendingExpanded.value == true) {
                displayList.addAll(pending)
            }
        }

        if (!completed.isNullOrEmpty()) {
            displayList.add(HEADER_COMPLETED)
            if (viewModel.isCompletedExpanded.value == true) {
                displayList.addAll(completed)
            }
        }

        if (!notCompleted.isNullOrEmpty()) {
            displayList.add(HEADER_NOT_COMPLETED)
            if (viewModel.isNotCompletedExpanded.value == true) {
                displayList.addAll(notCompleted)
            }
        }
        adapter.submitList(displayList)
        val empty = pending.isNullOrEmpty() && notCompleted.isNullOrEmpty() && completed.isNullOrEmpty()
        emptyText?.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun handleEditAction(taskToEdit: Task?) {
        val task = taskToEdit ?: run {
            val selected = viewModel.selectedTasks.value
            if (selected.isNullOrEmpty()) return
            selected.first()
        }
        AddTaskDialogFragment.newInstance(task).show(parentFragmentManager, "EditTaskDialog")
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onPrepareMenu(menu: Menu) {
                if (!isResumed || isNotCurrentFragment()) return
                if ((activity as? dev.zihowl.dog.ui.main.MainActivity)?.isPerformanceVisible() == true) return
                val isSelection = viewModel.isSelectionMode.value == true
                val selectedCount = viewModel.selectedTasks.value?.size ?: 0
                menu.findItem(R.id.action_add)?.isVisible = !isSelection
                menu.findItem(R.id.action_delete)?.isVisible = isSelection
                menu.findItem(R.id.action_edit)?.isVisible = isSelection && selectedCount == 1
                menu.findItem(R.id.action_mark_not_completed)?.isVisible = isSelection
                menu.findItem(R.id.action_share_task)?.isVisible = isSelection && selectedCount == 1
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (!isResumed || isNotCurrentFragment()) return false
                return when (menuItem.itemId) {
                    R.id.action_delete -> {
                        showDeleteConfirmationDialog()
                        true
                    }
                    R.id.action_edit -> {
                        handleEditAction(null)
                        true
                    }
                    R.id.action_mark_not_completed -> {
                        viewModel.markSelectedAsNotCompleted(requireContext())
                        true
                    }
                    R.id.action_share_task -> {
                        shareSelectedTask()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    /**
     * Comparte la tarea seleccionada con compañeros (RQF-APP-45): carga los
     * candidatos del servidor, deja elegir destinatarios y envía la tarea
     * cifrada. `Compartir con todo el grupo` se modela seleccionando a todos.
     */
    private fun shareSelectedTask() {
        val task = viewModel.selectedTasks.value?.firstOrNull() ?: return
        val session = SessionManager(requireContext())
        if (session.isGuestMode || session.serverBaseUrl.isNullOrBlank() ||
            session.accessToken.isNullOrBlank()
        ) {
            Toast.makeText(
                requireContext(),
                "Compartir tareas requiere conexión con el servidor",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val baseUrl = session.serverBaseUrl!!
        val token = session.accessToken!!
        val client = CollaborationClient()

        viewLifecycleOwner.lifecycleScope.launch {
            val candidates = client.shareCandidates(baseUrl, token, null)
            if (candidates.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "No hay compañeros disponibles. Configura tu grupo en 'Mi grupo y materias'.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val labels = candidates
                .map { displayIdentity(it.fullName, it.username) }
                .toTypedArray()
            val checked = BooleanArray(candidates.size)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Compartir \"${task.title}\"")
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton("Compartir") { _, _ ->
                    val recipients = candidates.filterIndexed { i, _ -> checked[i] }
                    if (recipients.isEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            "Selecciona al menos un compañero",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setPositiveButton
                    }
                    val scope = if (recipients.size == candidates.size) "GROUP" else "SELECTED"
                    sendSharedTask(client, baseUrl, token, task, recipients, scope)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun sendSharedTask(
        client: CollaborationClient,
        baseUrl: String,
        token: String,
        task: Task,
        recipients: List<CollaborationClient.ShareCandidate>,
        scope: String
    ) {
        val encrypted = SharedTaskCodec.encrypt(task)
        if (encrypted == null) {
            Toast.makeText(
                requireContext(),
                "No se pudo cifrar la tarea para compartirla",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val result = client.shareTask(
                baseUrl, token,
                encrypted.ciphertext, encrypted.encKey, scope,
                task.title, recipients.map { it.userId }
            )
            val text = when (result) {
                is CollaborationClient.ShareResult.Success ->
                    "Tarea compartida con ${result.recipientCount} compañero(s)"
                is CollaborationClient.ShareResult.Error ->
                    result.message ?: "No se pudo compartir la tarea"
            }
            Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
            if (result is CollaborationClient.ShareResult.Success) {
                viewModel.finishSelectionMode()
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar las tareas seleccionadas?")
            .setPositiveButton("Eliminar") { _, _ -> viewModel.deleteSelectedTasks(requireContext()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateActionBarTitle(title: String) {
        if (isNotCurrentFragment()) return
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = title
    }

    private fun isNotCurrentFragment(): Boolean {
        return (activity as? MainActivity)?.isCurrentTab(1) != true
    }

    companion object {
        private const val HEADER_PENDING = "Pendientes"
        private const val HEADER_NOT_COMPLETED = "No Completadas"
        private const val HEADER_COMPLETED = "Completadas"
    }
}
