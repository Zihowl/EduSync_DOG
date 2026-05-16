package dev.zihowl.dog.ui.schedule.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import dev.zihowl.dog.R
import dev.zihowl.dog.data.remote.CatalogClient
import dev.zihowl.dog.ui.schedule.setup.ScheduleSetupViewModel.DraggedRow
import dev.zihowl.dog.ui.schedule.setup.ScheduleSetupViewModel.Stage
import dev.zihowl.dog.ui.schedule.setup.ScheduleSetupViewModel.SubjectRow

class ScheduleSetupFragment : Fragment() {

    private lateinit var viewModel: ScheduleSetupViewModel

    private lateinit var scrollContent: View
    private lateinit var stateContainer: View
    private lateinit var stateProgress: ProgressBar
    private lateinit var stateText: TextView
    private lateinit var retryButton: MaterialButton

    private lateinit var layoutGroup: TextInputLayout
    private lateinit var dropdownGroup: MaterialAutoCompleteTextView
    private lateinit var layoutSubgroup: TextInputLayout
    private lateinit var dropdownSubgroup: MaterialAutoCompleteTextView
    private lateinit var progressSubjects: ProgressBar
    private lateinit var textSubjectsHint: TextView
    private lateinit var containerSubjects: LinearLayout
    private lateinit var containerDragged: LinearLayout
    private lateinit var buttonAddDragged: MaterialButton
    private lateinit var buttonSave: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_schedule_setup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[ScheduleSetupViewModel::class.java]

        scrollContent = view.findViewById(R.id.setup_scroll)
        stateContainer = view.findViewById(R.id.setup_state_container)
        stateProgress = view.findViewById(R.id.setup_progress)
        stateText = view.findViewById(R.id.setup_state_text)
        retryButton = view.findViewById(R.id.setup_retry)
        layoutGroup = view.findViewById(R.id.layoutGroup)
        dropdownGroup = view.findViewById(R.id.dropdownGroup)
        layoutSubgroup = view.findViewById(R.id.layoutSubgroup)
        dropdownSubgroup = view.findViewById(R.id.dropdownSubgroup)
        progressSubjects = view.findViewById(R.id.progressSubjects)
        textSubjectsHint = view.findViewById(R.id.textSubjectsHint)
        containerSubjects = view.findViewById(R.id.containerSubjects)
        containerDragged = view.findViewById(R.id.containerDragged)
        buttonAddDragged = view.findViewById(R.id.buttonAddDragged)
        buttonSave = view.findViewById(R.id.buttonSaveSetup)

        retryButton.setOnClickListener { viewModel.load() }
        buttonAddDragged.setOnClickListener { startDraggedPicker() }
        buttonSave.setOnClickListener { viewModel.save() }

        observeViewModel()
        if (savedInstanceState == null) viewModel.load()
    }

    private fun observeViewModel() {
        viewModel.stage.observe(viewLifecycleOwner) { stage ->
            when (stage) {
                is Stage.Loading -> showState(loading = true, message = "Cargando grupos…")
                is Stage.Error -> showState(loading = false, message = stage.message, retry = true)
                is Stage.Ready -> {
                    stateContainer.visibility = View.GONE
                    scrollContent.visibility = View.VISIBLE
                }
            }
        }
        viewModel.parentGroups.observe(viewLifecycleOwner) { bindGroupDropdown(it) }
        viewModel.subgroups.observe(viewLifecycleOwner) { bindSubgroupDropdown(it) }
        viewModel.subjectsLoading.observe(viewLifecycleOwner) {
            progressSubjects.visibility = if (it) View.VISIBLE else View.GONE
        }
        viewModel.primarySubjects.observe(viewLifecycleOwner) { bindSubjects(it) }
        viewModel.dragged.observe(viewLifecycleOwner) { bindDragged(it) }
        viewModel.saveResult.observe(viewLifecycleOwner) { message ->
            message?.let {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(it)
                    .setPositiveButton("Aceptar", null)
                    .show()
                viewModel.saveResult.value = null
            }
        }
    }

    private fun showState(loading: Boolean, message: String, retry: Boolean = false) {
        scrollContent.visibility = View.GONE
        stateContainer.visibility = View.VISIBLE
        stateProgress.visibility = if (loading) View.VISIBLE else View.GONE
        stateText.text = message
        retryButton.visibility = if (retry) View.VISIBLE else View.GONE
    }

    private fun bindGroupDropdown(groups: List<CatalogClient.RemoteGroup>) {
        dropdownGroup.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1,
                groups.map { it.name })
        )
        groups.firstOrNull { it.id == viewModel.selectedGroupId }?.let {
            dropdownGroup.setText(it.name, false)
        }
        dropdownGroup.setOnItemClickListener { _, _, position, _ ->
            val group = groups.getOrNull(position) ?: return@setOnItemClickListener
            viewModel.onGroupChosen(group.id)
            dropdownSubgroup.setText("", false)
        }
    }

    private fun bindSubgroupDropdown(subgroups: List<CatalogClient.RemoteGroup>) {
        dropdownSubgroup.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1,
                subgroups.map { it.name })
        )
        subgroups.firstOrNull { it.id == viewModel.selectedSubgroupId }?.let {
            dropdownSubgroup.setText(it.name, false)
        }
        dropdownSubgroup.setOnItemClickListener { _, _, position, _ ->
            val subgroup = subgroups.getOrNull(position) ?: return@setOnItemClickListener
            viewModel.onSubgroupChosen(subgroup.id)
        }
    }

    private fun bindSubjects(subjects: List<SubjectRow>) {
        containerSubjects.removeAllViews()
        textSubjectsHint.visibility = if (subjects.isEmpty()) View.VISIBLE else View.GONE
        if (subjects.isEmpty()) {
            textSubjectsHint.text =
                if (viewModel.selectedSubgroupId > 0)
                    "Este grupo aún no tiene horario publicado."
                else "Selecciona grupo y subgrupo para ver las materias."
        }
        subjects.forEach { row ->
            val checkBox = MaterialCheckBox(requireContext()).apply {
                text = row.name
                isChecked = row.included
                setOnClickListener { viewModel.toggleSubject(row.name) }
            }
            containerSubjects.addView(checkBox)
        }
    }

    private fun bindDragged(dragged: List<DraggedRow>) {
        containerDragged.removeAllViews()
        dragged.forEach { row ->
            val item = LayoutInflater.from(requireContext())
                .inflate(android.R.layout.simple_list_item_1, containerDragged, false) as TextView
            item.text = "${row.name}  ·  ${row.groupLabel}"
            item.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage("¿Quitar \"${row.name}\" de tus materias adicionales?")
                    .setPositiveButton("Quitar") { _, _ -> viewModel.removeDragged(row) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            containerDragged.addView(item)
        }
    }

    /** Diálogo en cascada: grupo → subgrupo → materias a arrastrar. */
    private fun startDraggedPicker() {
        val parents = viewModel.parentGroupsList()
        if (parents.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Grupo de origen")
            .setItems(parents.map { it.name }.toTypedArray()) { _, which ->
                pickDraggedSubgroup(parents[which].id)
            }
            .show()
    }

    private fun pickDraggedSubgroup(parentId: Int) {
        val subgroups = viewModel.subgroupsForParent(parentId)
        if (subgroups.isEmpty()) {
            // El grupo puede tener materias publicadas sin subgrupos: pasa directo
            // a elegir las materias del grupo padre.
            pickDraggedSubjects(parentId, isGroup = true)
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Subgrupo de origen")
            .setItems(subgroups.map { it.name }.toTypedArray()) { _, which ->
                pickDraggedSubjects(subgroups[which].id, isGroup = false)
            }
            .show()
    }

    private fun pickDraggedSubjects(groupId: Int, isGroup: Boolean) {
        viewModel.fetchSubjectNames(groupId) { names ->
            if (!isAdded) return@fetchSubjectNames
            if (names.isNullOrEmpty()) {
                val tipo = if (isGroup) "grupo" else "subgrupo"
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage("Ese $tipo no tiene horario publicado.")
                    .setPositiveButton("Aceptar", null)
                    .show()
                return@fetchSubjectNames
            }
            val checked = BooleanArray(names.size)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Materias a agregar")
                .setMultiChoiceItems(names.toTypedArray(), checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton("Agregar") { _, _ ->
                    names.forEachIndexed { index, name ->
                        if (checked[index]) viewModel.addDragged(groupId, name)
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }
}
