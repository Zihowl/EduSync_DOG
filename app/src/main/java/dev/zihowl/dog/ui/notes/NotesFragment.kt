package dev.zihowl.dog.ui.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.Note
import dev.zihowl.dog.ui.main.MainActivity
import dev.zihowl.dog.utils.AttachmentUtils

class NotesFragment : Fragment() {

    private lateinit var viewModel: NotesViewModel
    private lateinit var adapter: NotesAdapter
    private lateinit var backPressedCallback: OnBackPressedCallback
    private var emptyText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[NotesViewModel::class.java]

        backPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                viewModel.finishSelectionMode()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_notes, container, false)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewNotes)
        recyclerView.layoutManager = LinearLayoutManager(context)
        emptyText = view.findViewById(R.id.text_empty_notes)
        setupAdapter()
        recyclerView.adapter = adapter
        setupObservers()
    }

    private fun setupAdapter() {
        adapter = NotesAdapter(
            onItemClick = { note, _ ->
                if (viewModel.isSelectionMode.value == true) {
                    viewModel.toggleSelection(note)
                } else {
                    handleEditAction(note)
                }
            },
            onItemLongClick = { note, _ ->
                viewModel.toggleSelection(note)
            },
            onAttachmentClick = { note ->
                AttachmentUtils.openAttachment(requireContext(), note.attachmentPath, note.attachmentName)
            }
        )
    }

    private fun setupObservers() {
        viewModel.notes.observe(viewLifecycleOwner) { notes ->
            adapter.submitList(notes ?: emptyList())
            val empty = notes.isNullOrEmpty()
            emptyText?.visibility = if (empty) View.VISIBLE else View.GONE
        }

        viewModel.isSelectionMode.observe(viewLifecycleOwner) { isSelection ->
            backPressedCallback.isEnabled = isSelection
            if (!isSelection) updateActionBarTitle("Notas")
            requireActivity().invalidateOptionsMenu()
        }

        viewModel.selectedNotes.observe(viewLifecycleOwner) { selectedNotes ->
            if (viewModel.isSelectionMode.value == true) {
                val count = selectedNotes.size
                if (count == 0) {
                    viewModel.finishSelectionMode()
                } else {
                    updateActionBarTitle("$count seleccionadas")
                }
            }
            adapter.setSelectedItems(selectedNotes)
            requireActivity().invalidateOptionsMenu()
        }
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onPrepareMenu(menu: Menu) {
                if (!isResumed || isNotCurrentFragment()) return
                val isSelection = viewModel.isSelectionMode.value == true
                val selectedCount = viewModel.selectedNotes.value?.size ?: 0
                menu.findItem(R.id.action_add)?.isVisible = !isSelection
                menu.findItem(R.id.action_delete)?.isVisible = isSelection
                menu.findItem(R.id.action_edit)?.isVisible = isSelection && selectedCount == 1
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (!isResumed || isNotCurrentFragment()) return false
                return when (menuItem.itemId) {
                    R.id.action_delete -> {
                        showDeleteConfirmationDialog()
                        true
                    }
                    R.id.action_edit -> {
                        handleEditAction()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun handleEditAction(noteToEdit: Note? = null) {
        val note = noteToEdit ?: run {
            val selected = viewModel.selectedNotes.value
            if (selected.isNullOrEmpty()) return
            selected.first()
        }
        AddNoteDialogFragment.newInstance(note)
            .show(parentFragmentManager, "EditNoteDialog")
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar las notas seleccionadas?")
            .setPositiveButton("Eliminar") { _, _ -> viewModel.deleteSelectedNotes(requireContext()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateActionBarTitle(title: String) {
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = title
    }

    private fun isNotCurrentFragment(): Boolean {
        return (activity as? MainActivity)?.isCurrentTab(2) != true
    }
}
