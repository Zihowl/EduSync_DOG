package dev.zihowl.dog.ui.performance

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import dev.zihowl.dog.data.model.Task
import dev.zihowl.dog.ui.tasks.TasksViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PerformanceFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val viewModel = ViewModelProvider(requireActivity())[TasksViewModel::class.java]
        val tasksState = mutableStateOf(emptyList<Task>())

        viewModel.allTasks.observe(viewLifecycleOwner) { tasks ->
            tasksState.value = tasks ?: emptyList()
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
                MaterialTheme(colorScheme = colorScheme) {
                    PerformanceScreen(tasksState.value)
                }
            }
        }
    }
}

private fun startOfDay(time: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun endOfDay(time: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
    return cal.timeInMillis
}

@Composable
fun PerformanceScreen(tasks: List<Task>) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val defaultEnd = remember { System.currentTimeMillis() }
    val defaultStart = remember {
        Calendar.getInstance().apply {
            timeInMillis = defaultEnd
            add(Calendar.DAY_OF_YEAR, -29)
        }.timeInMillis
    }

    var startDate by remember { mutableStateOf(startOfDay(defaultStart)) }
    var endDate by remember { mutableStateOf(endOfDay(defaultEnd)) }

    fun showDatePicker(initial: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onPicked(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val filtered = tasks.filter { task ->
        val due = task.dueDate?.time ?: return@filter false
        due in startDate..endDate
    }

    val total = filtered.size
    val pending = filtered.count { it.status == Task.STATUS_PENDING }
    val completed = filtered.count { it.status == Task.STATUS_COMPLETED }
    val notCompleted = filtered.count { it.status == Task.STATUS_NOT_COMPLETED }

    val completedPct = if (total > 0) completed.toFloat() / total else 0f
    val notCompletedPct = if (total > 0) notCompleted.toFloat() / total else 0f
    val pendingPct = if (total > 0) pending.toFloat() / total else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date range selector (RQF-APP-52)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Rango de fechas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateField(
                        label = "Inicio",
                        value = dateFormat.format(Date(startDate)),
                        modifier = Modifier.weight(1f)
                    ) {
                        showDatePicker(startDate) { picked ->
                            val newStart = startOfDay(picked)
                            // RQNF-APP-49: la fecha de inicio no debe ser posterior a la fecha de fin
                            if (newStart > endDate) {
                                Toast.makeText(
                                    context,
                                    "La fecha de inicio no puede ser posterior a la fecha de fin.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                startDate = newStart
                            }
                        }
                    }
                    DateField(
                        label = "Fin",
                        value = dateFormat.format(Date(endDate)),
                        modifier = Modifier.weight(1f)
                    ) {
                        showDatePicker(endDate) { picked ->
                            val newEnd = endOfDay(picked)
                            if (newEnd < startDate) {
                                Toast.makeText(
                                    context,
                                    "La fecha de fin no puede ser anterior a la fecha de inicio.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                endDate = newEnd
                            }
                        }
                    }
                }
            }
        }

        if (total == 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay tareas en el rango de fechas seleccionado.",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
            return@Column
        }

        // Pie Chart
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Distribución de Tareas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Tareas totales: $total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                PieChart(
                    slices = listOf(
                        PieSlice(pendingPct, Color(0xFFFFB020), "Pendientes"),
                        PieSlice(completedPct, Color(0xFF4CAF50), "Completadas"),
                        PieSlice(notCompletedPct, Color(0xFFE53935), "No Completadas")
                    ),
                    modifier = Modifier.size(200.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendItem(color = Color(0xFFFFB020), label = "Pendientes", count = pending)
                    LegendItem(color = Color(0xFF4CAF50), label = "Completadas", count = completed)
                    LegendItem(color = Color(0xFFE53935), label = "No Complet.", count = notCompleted)
                }
            }
        }

        // Cumplimiento - RQNF-APP-48
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cumplimiento del rango",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$completed de $total tareas completadas (${(completedPct * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ProgressBarRow(
                    label = "Cumplimiento",
                    progress = completedPct,
                    color = Color(0xFF4CAF50),
                    count = completed,
                    total = total
                )
                ProgressBarRow(
                    label = "Incumplimiento",
                    progress = notCompletedPct,
                    color = Color(0xFFE53935),
                    count = notCompleted,
                    total = total
                )
                ProgressBarRow(
                    label = "Pendientes",
                    progress = pendingPct,
                    color = Color(0xFFFFB020),
                    count = pending,
                    total = total
                )
            }
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PieChart(slices: List<PieSlice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    if (total == 0f) return

    Canvas(modifier = modifier) {
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = (slice.value / total) * 360f
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height)
            )
            startAngle += sweep
        }
    }
}

data class PieSlice(val value: Float, val color: Color, val label: String)

@Composable
fun LegendItem(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = color)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontSize = 11.sp)
            Text(text = count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProgressBarRow(label: String, progress: Float, color: Color, count: Int, total: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$count / $total (${(progress * 100).toInt()}%)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
