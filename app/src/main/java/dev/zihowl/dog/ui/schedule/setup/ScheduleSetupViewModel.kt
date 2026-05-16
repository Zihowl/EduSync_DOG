package dev.zihowl.dog.ui.schedule.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dev.zihowl.dog.DogApplication
import dev.zihowl.dog.R
import dev.zihowl.dog.data.remote.CatalogClient
import dev.zihowl.dog.data.repository.DogRepository
import dev.zihowl.dog.data.repository.OfficialScheduleSyncer
import dev.zihowl.dog.data.repository.ScheduleConfig
import dev.zihowl.dog.data.session.SessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ScheduleSetupViewModel(app: Application) : AndroidViewModel(app) {

    /** Una materia del grupo/subgrupo principal y si el alumno la incluye. */
    data class SubjectRow(val name: String, val included: Boolean)

    /** Una materia arrastrada, con etiqueta de su grupo de origen. */
    data class DraggedRow(val groupId: Int, val groupLabel: String, val name: String)

    sealed class Stage {
        object Loading : Stage()
        object Ready : Stage()
        data class Error(val message: String) : Stage()
    }

    private val session = SessionManager(app)
    private val catalogClient = CatalogClient()
    private val weekDays = app.resources.getStringArray(R.array.week_days).toList()
    private val repository: DogRepository =
        runBlocking { (app as DogApplication).repository() }
    private val syncer = OfficialScheduleSyncer(repository, session, weekDays, catalogClient)

    private var allGroups: List<CatalogClient.RemoteGroup> = emptyList()

    private val _stage = MutableLiveData<Stage>(Stage.Loading)
    val stage: LiveData<Stage> = _stage

    private val _parentGroups = MutableLiveData<List<CatalogClient.RemoteGroup>>(emptyList())
    val parentGroups: LiveData<List<CatalogClient.RemoteGroup>> = _parentGroups

    private val _subgroups = MutableLiveData<List<CatalogClient.RemoteGroup>>(emptyList())
    val subgroups: LiveData<List<CatalogClient.RemoteGroup>> = _subgroups

    private val _primarySubjects = MutableLiveData<List<SubjectRow>>(emptyList())
    val primarySubjects: LiveData<List<SubjectRow>> = _primarySubjects

    private val _dragged = MutableLiveData<List<DraggedRow>>(emptyList())
    val dragged: LiveData<List<DraggedRow>> = _dragged

    private val _subjectsLoading = MutableLiveData(false)
    val subjectsLoading: LiveData<Boolean> = _subjectsLoading

    /** true mientras se guarda; el observador recibe un mensaje al terminar. */
    val saveResult = MutableLiveData<String?>()

    var selectedGroupId: Int = session.selectedGroupId
        private set
    var selectedSubgroupId: Int = session.selectedSubgroupId
        private set

    private var savedConfig: ScheduleConfig = ScheduleConfig.fromJson(session.scheduleConfigJson)

    fun load() {
        _stage.value = Stage.Loading
        viewModelScope.launch {
            val baseUrl = session.serverBaseUrl
            val token = session.accessToken
            if (baseUrl == null || token == null) {
                _stage.value = Stage.Error("Necesitas iniciar sesión y conexión al servidor.")
                return@launch
            }
            when (val r = catalogClient.getGroups(baseUrl, token)) {
                is CatalogClient.GroupsResult.Success -> {
                    allGroups = r.groups
                    _parentGroups.value = r.groups.filter { it.parentId == null }
                    _dragged.value = savedConfig.dragged.map { it.toRow() }
                    _stage.value = Stage.Ready
                    restoreSavedSelection()
                }
                is CatalogClient.GroupsResult.Error ->
                    _stage.value = Stage.Error(
                        r.cause?.message ?: "No se pudo cargar el catálogo de grupos."
                    )
            }
        }
    }

    private fun restoreSavedSelection() {
        if (selectedGroupId > 0) {
            _subgroups.value = allGroups.filter { it.parentId == selectedGroupId }
            if (selectedSubgroupId > 0) loadPrimarySubjects()
        }
    }

    fun onGroupChosen(groupId: Int) {
        selectedGroupId = groupId
        selectedSubgroupId = -1
        _subgroups.value = allGroups.filter { it.parentId == groupId }
        _primarySubjects.value = emptyList()
    }

    fun onSubgroupChosen(subgroupId: Int) {
        selectedSubgroupId = subgroupId
        loadPrimarySubjects()
    }

    private fun loadPrimarySubjects() {
        if (selectedGroupId <= 0 || selectedSubgroupId <= 0) return
        _subjectsLoading.value = true
        viewModelScope.launch {
            val baseUrl = session.serverBaseUrl ?: return@launch
            val token = session.accessToken ?: return@launch
            val ids = listOf(selectedGroupId, selectedSubgroupId)
            when (val r = catalogClient.getPublishedSchedule(baseUrl, token, ids)) {
                is CatalogClient.ScheduleResult.Success -> {
                    val names = r.slots.map { it.subjectName }.distinct().sorted()
                    _primarySubjects.value = names.map {
                        SubjectRow(it, included = it !in savedConfig.discarded)
                    }
                }
                is CatalogClient.ScheduleResult.Error ->
                    saveResult.value = "Error al cargar materias: ${r.cause?.message ?: ""}"
            }
            _subjectsLoading.value = false
        }
    }

    fun toggleSubject(name: String) {
        _primarySubjects.value = _primarySubjects.value?.map {
            if (it.name == name) it.copy(included = !it.included) else it
        }
    }

    fun subgroupsForParent(parentId: Int): List<CatalogClient.RemoteGroup> =
        allGroups.filter { it.parentId == parentId }

    fun parentGroupsList(): List<CatalogClient.RemoteGroup> =
        allGroups.filter { it.parentId == null }

    /** Carga los nombres de materias publicadas de un grupo (para arrastrar). */
    fun fetchSubjectNames(groupId: Int, onResult: (List<String>?) -> Unit) {
        viewModelScope.launch {
            val baseUrl = session.serverBaseUrl
            val token = session.accessToken
            if (baseUrl == null || token == null) {
                onResult(null); return@launch
            }
            when (val r = catalogClient.getPublishedSchedule(baseUrl, token, listOf(groupId))) {
                is CatalogClient.ScheduleResult.Success ->
                    onResult(r.slots.map { it.subjectName }.distinct().sorted())
                is CatalogClient.ScheduleResult.Error -> onResult(null)
            }
        }
    }

    fun addDragged(groupId: Int, name: String) {
        val current = _dragged.value.orEmpty()
        if (current.any { it.groupId == groupId && it.name == name }) return
        val label = allGroups.firstOrNull { it.id == groupId }?.name ?: "Grupo $groupId"
        _dragged.value = current + DraggedRow(groupId, label, name)
    }

    fun removeDragged(row: DraggedRow) {
        _dragged.value = _dragged.value.orEmpty().filterNot {
            it.groupId == row.groupId && it.name == row.name
        }
    }

    fun groupLabel(groupId: Int): String =
        allGroups.firstOrNull { it.id == groupId }?.name ?: ""

    fun canSave(): Boolean = selectedGroupId > 0 && selectedSubgroupId > 0

    fun save() {
        if (!canSave()) {
            saveResult.value = "Selecciona tu grupo y subgrupo."
            return
        }
        viewModelScope.launch {
            val discarded = _primarySubjects.value.orEmpty()
                .filterNot { it.included }
                .map { it.name }
                .toSet()
            val dragged = _dragged.value.orEmpty()
                .map { ScheduleConfig.DraggedSubject(it.groupId, it.name) }
            val config = ScheduleConfig(discarded, dragged)

            session.selectedGroupId = selectedGroupId
            session.selectedSubgroupId = selectedSubgroupId
            session.scheduleConfigJson = config.toJson()
            savedConfig = config

            when (val r = syncer.syncForStudent()) {
                is OfficialScheduleSyncer.Result.Success ->
                    saveResult.value = "Horario actualizado."
                is OfficialScheduleSyncer.Result.NotConfigured ->
                    saveResult.value = "Selecciona tu grupo y subgrupo."
                is OfficialScheduleSyncer.Result.Error ->
                    saveResult.value = "Guardado, pero falló la descarga: ${r.message}"
            }
        }
    }

    private fun ScheduleConfig.DraggedSubject.toRow(): DraggedRow {
        val label = allGroups.firstOrNull { it.id == groupId }?.name ?: "Grupo $groupId"
        return DraggedRow(groupId, label, name)
    }
}
