package dev.zihowl.dog.ui.teacherschedules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dev.zihowl.dog.DogApplication
import dev.zihowl.dog.data.remote.CatalogClient
import dev.zihowl.dog.data.session.SessionManager
import kotlinx.coroutines.launch

/**
 * Consulta de horarios de los profesores del plantel (RQF-APP-54/55).
 * Descarga del servidor todos los bloques publicados (RQNF-APP-51), los agrupa
 * por docente y permite filtrar solo los docentes de las materias inscritas
 * del alumno (RQNF-APP-52).
 */
class TeacherSchedulesViewModel(application: Application) : AndroidViewModel(application) {

    /** Un docente con todos sus bloques de clase publicados. */
    data class TeacherSchedule(
        val teacherName: String,
        val slots: List<CatalogClient.RemoteSlot>
    )

    sealed class UiState {
        object Loading : UiState()
        data class Ready(val teachers: List<TeacherSchedule>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val session = SessionManager(application)
    private val catalogClient = CatalogClient()

    /** Catálogo completo agrupado por docente (sin filtrar). */
    private var allTeachers: List<TeacherSchedule> = emptyList()
    /** Nombres de docentes de las materias inscritas del alumno. */
    private var myTeacherNames: Set<String> = emptySet()

    private val _onlyMine = MutableLiveData(false)
    val onlyMine: LiveData<Boolean> = _onlyMine

    private val _state = MutableLiveData<UiState>(UiState.Loading)
    val state: LiveData<UiState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val baseUrl = session.serverBaseUrl
            val token = session.accessToken
            if (baseUrl == null || token == null) {
                _state.value = UiState.Error("Necesitas iniciar sesión para consultar horarios.")
                return@launch
            }

            val repo = (getApplication<Application>() as DogApplication).repository()
            myTeacherNames = repo.getOfficialSubjects(session.currentOwner())
                .mapNotNull { it.professorName?.trim()?.takeIf { n -> n.isNotEmpty() } }
                .map { it.lowercase() }
                .toSet()

            when (val r = catalogClient.getTeacherSchedules(baseUrl, token)) {
                is CatalogClient.ScheduleResult.Success -> {
                    allTeachers = r.slots
                        .filter { it.teacherName?.isNotBlank() == true }
                        .groupBy { it.teacherName!!.trim() }
                        .map { (name, slots) ->
                            TeacherSchedule(
                                teacherName = name,
                                slots = slots.sortedWith(
                                    compareBy({ it.dayOfWeek }, { it.startTime })
                                )
                            )
                        }
                        .sortedBy { it.teacherName.lowercase() }
                    emitFiltered()
                }
                is CatalogClient.ScheduleResult.Error ->
                    _state.value = UiState.Error(
                        r.cause?.message ?: "No se pudo obtener la información del servidor."
                    )
                CatalogClient.ScheduleResult.Unauthorized ->
                    _state.value = UiState.Error("Tu sesión expiró. Inicia sesión de nuevo.")
            }
        }
    }

    fun setOnlyMine(value: Boolean) {
        if (_onlyMine.value == value) return
        _onlyMine.value = value
        emitFiltered()
    }

    private fun emitFiltered() {
        val onlyMine = _onlyMine.value == true
        val list = if (onlyMine) {
            allTeachers.filter { it.teacherName.lowercase() in myTeacherNames }
        } else {
            allTeachers
        }
        _state.value = UiState.Ready(list)
    }
}
