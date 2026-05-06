package dev.zihowl.dog.ui.subjects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.zihowl.dog.R
import dev.zihowl.dog.data.model.Subject
import dev.zihowl.dog.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubjectsFragment : Fragment() {

    private lateinit var viewModel: SubjectsViewModel
    private lateinit var adapter: SubjectsAdapter
    private lateinit var backPressedCallback: OnBackPressedCallback
    private var emptyText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[SubjectsViewModel::class.java]

        backPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                viewModel.finishSelectionMode()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_subjects, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewSubjects)
        recyclerView.layoutManager = LinearLayoutManager(context)
        emptyText = view.findViewById(R.id.text_empty_subjects)
        setupAdapter()
        recyclerView.adapter = adapter
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun setupAdapter() {
        adapter = SubjectsAdapter(
            onItemClick = { subject, _ ->
                if (viewModel.isSelectionMode.value == true) {
                    viewModel.toggleSelection(subject)
                } else {
                    (activity as? MainActivity)?.showSubjectDetail(subject.name)
                }
            },
            onItemLongClick = { subject, _ ->
                viewModel.toggleSelection(subject)
            }
        )
    }

    private fun setupObservers() {
        viewModel.subjects.observe(viewLifecycleOwner) { subjects ->
            adapter.submitList(subjects ?: emptyList())
            val empty = subjects.isNullOrEmpty()
            emptyText?.visibility = if (empty) View.VISIBLE else View.GONE
        }

        viewModel.isSelectionMode.observe(viewLifecycleOwner) { isSelection ->
            backPressedCallback.isEnabled = isSelection
            if (!isSelection) updateActionBarTitle("Materias")
            requireActivity().invalidateOptionsMenu()
        }

        viewModel.selectedSubjects.observe(viewLifecycleOwner) { selectedSubjects ->
            if (viewModel.isSelectionMode.value == true) {
                val count = selectedSubjects.size
                if (count == 0) {
                    viewModel.finishSelectionMode()
                } else {
                    updateActionBarTitle("$count seleccionados")
                }
            }
            adapter.setSelectedItems(selectedSubjects)
            requireActivity().invalidateOptionsMenu()
        }
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onPrepareMenu(menu: Menu) {
                if (isNotCurrentFragment()) return
                val isSelection = viewModel.isSelectionMode.value == true
                val selectedCount = viewModel.selectedSubjects.value?.size ?: 0
                menu.findItem(R.id.action_add)?.isVisible = !isSelection
                menu.findItem(R.id.action_delete)?.isVisible = isSelection
                menu.findItem(R.id.action_edit)?.isVisible = isSelection && selectedCount == 1
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (isNotCurrentFragment()) return false
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

    private fun handleEditAction() {
        val selected = viewModel.selectedSubjects.value
        if (!selected.isNullOrEmpty()) {
            val subjectToEdit = selected.first()
            AddSubjectDialogFragment.newInstance(subjectToEdit)
                .show(parentFragmentManager, "EditSubjectDialog")
        }
    }

    private fun showDeleteConfirmationDialog() {
        val selectedSubjects = viewModel.selectedSubjects.value ?: return

        if (selectedSubjects.size == 1) {
            val subject = selectedSubjects.first()
            if (viewModel.subjectHasContent(subject)) {
                CoroutineScope(Dispatchers.Main).launch {
                    val counts = viewModel.getSubjectContentCount(subject)
                    val message = String.format(
                        "Esta materia tiene %d tareas y %d notas asociadas.\n\n¿Qué deseas hacer?",
                        counts[0], counts[1]
                    )
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Materia con Contenido")
                        .setMessage(message)
                        .setNeutralButton("Cancelar", null)
                        .setNegativeButton("Desvincular y Eliminar") { _, _ ->
                            viewModel.disassociateAndDelete(subject, requireContext())
                        }
                        .setPositiveButton("Eliminar Todo") { _, _ ->
                            viewModel.deleteSelectedSubjects(requireContext())
                        }
                        .show()
                }
                return
            }
        }

        val message = "¿Estás seguro de que quieres eliminar las ${selectedSubjects.size} materias seleccionadas y todo su contenido asociado?"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage(message)
            .setPositiveButton("Eliminar") { _, _ -> viewModel.deleteSelectedSubjects(requireContext()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateActionBarTitle(title: String) {
        if (isNotCurrentFragment()) return
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
    }

    private fun isNotCurrentFragment(): Boolean {
        return (activity as? MainActivity)?.isCurrentTab(0) != true
    }
}
