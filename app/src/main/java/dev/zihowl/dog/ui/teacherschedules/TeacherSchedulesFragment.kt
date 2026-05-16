package dev.zihowl.dog.ui.teacherschedules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import dev.zihowl.dog.R
import dev.zihowl.dog.data.remote.CatalogClient

/**
 * Pantalla de consulta de horarios de profesores (RQF-APP-54/55). Se muestra
 * en el contenedor de fragmentos de `MainActivity`, abierta desde la sidebar.
 */
class TeacherSchedulesFragment : Fragment() {

    private val stateHolder = mutableStateOf<TeacherSchedulesViewModel.UiState>(
        TeacherSchedulesViewModel.UiState.Loading
    )
    private val onlyMineHolder = mutableStateOf(false)
    private lateinit var viewModel: TeacherSchedulesViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this)[TeacherSchedulesViewModel::class.java]

        viewModel.state.observe(viewLifecycleOwner) { stateHolder.value = it }
        viewModel.onlyMine.observe(viewLifecycleOwner) { onlyMineHolder.value = it }

        val weekDays = resources.getStringArray(R.array.week_days)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isDark = (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                androidx.compose.material3.MaterialTheme(
                    colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
                ) {
                    TeacherSchedulesScreen(
                        state = stateHolder.value,
                        onlyMine = onlyMineHolder.value,
                        weekDays = weekDays,
                        onToggleOnlyMine = { viewModel.setOnlyMine(it) },
                        onRetry = { viewModel.load() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TeacherSchedulesScreen(
    state: TeacherSchedulesViewModel.UiState,
    onlyMine: Boolean,
    weekDays: Array<String>,
    onToggleOnlyMine: (Boolean) -> Unit,
    onRetry: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar profesor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = onlyMine, onCheckedChange = onToggleOnlyMine)
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Solo mis profesores",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(Modifier.height(8.dp))

        when (state) {
            is TeacherSchedulesViewModel.UiState.Loading ->
                CenterBox { CircularProgressIndicator() }

            is TeacherSchedulesViewModel.UiState.Error ->
                CenterBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Reintentar",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onRetry() }
                        )
                    }
                }

            is TeacherSchedulesViewModel.UiState.Ready -> {
                val filtered = state.teachers.filter {
                    it.teacherName.contains(query.trim(), ignoreCase = true)
                }
                if (filtered.isEmpty()) {
                    CenterBox {
                        Text(
                            text = if (onlyMine)
                                "No se encontraron profesores de tus materias inscritas."
                            else
                                "No hay horarios de profesores para mostrar.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered) { teacher ->
                            TeacherCard(teacher, weekDays)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherCard(
    teacher: TeacherSchedulesViewModel.TeacherSchedule,
    weekDays: Array<String>
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = teacher.teacherName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${teacher.slots.size} clase(s) publicada(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    painter = androidx.compose.ui.res.painterResource(
                        if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    teacher.slots.forEach { slot -> SlotRow(slot, weekDays) }
                }
            }
        }
    }
}

@Composable
private fun SlotRow(slot: CatalogClient.RemoteSlot, weekDays: Array<String>) {
    val day = weekDays.getOrNull(slot.dayOfWeek - 1) ?: "—"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = slot.subjectName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = buildString {
                    append("$day · ${slot.startTime.take(5)} - ${slot.endTime.take(5)}")
                    slot.classroomName?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) { content() }
}
