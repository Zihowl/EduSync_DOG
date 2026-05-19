package dev.zihowl.dog.ui.sharedtasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import dev.zihowl.dog.data.model.SharedTaskInbox
import dev.zihowl.dog.data.remote.CollaborationClient
import dev.zihowl.dog.data.remote.displayIdentity

/**
 * Pantalla de colaboración de tareas (RQF-APP-45/46/47). Muestra las tareas
 * recibidas (con aceptar/rechazar) y las enviadas (con recordatorios/toques).
 */
class SharedTasksFragment : Fragment() {

    private val inboxHolder = mutableStateOf<List<SharedTaskInbox>>(emptyList())
    private val outboxHolder = mutableStateOf<List<CollaborationClient.OutboxItem>>(emptyList())
    private val loadingHolder = mutableStateOf(false)

    private lateinit var viewModel: SharedTasksViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this)[SharedTasksViewModel::class.java]

        viewModel.inbox.observe(viewLifecycleOwner) { inboxHolder.value = it ?: emptyList() }
        viewModel.outbox.observe(viewLifecycleOwner) { outboxHolder.value = it ?: emptyList() }
        viewModel.loading.observe(viewLifecycleOwner) { loadingHolder.value = it ?: false }
        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                viewModel.consumeMessage()
            }
        }
        viewModel.refresh()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isDark = (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                MaterialTheme(
                    colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
                ) {
                    SharedTasksScreen(
                        inbox = inboxHolder.value,
                        outbox = outboxHolder.value,
                        loading = loadingHolder.value,
                        onRespond = viewModel::respond,
                        onRemind = viewModel::sendReminder,
                        onRefresh = viewModel::refresh
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedTasksScreen(
    inbox: List<SharedTaskInbox>,
    outbox: List<CollaborationClient.OutboxItem>,
    loading: Boolean,
    onRespond: (SharedTaskInbox, Boolean) -> Unit,
    onRemind: (String, CollaborationClient.RecipientStatus) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onRefresh, enabled = !loading) {
                Text(if (loading) "Actualizando…" else "Actualizar")
            }
        }
        if (inbox.isEmpty() && outbox.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Aún no hay tareas compartidas.\nComparte una tarea desde la " +
                        "pestaña Tareas para empezar a colaborar.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionTitle("Recibidas") }
            if (inbox.isEmpty()) {
                item { EmptyHint("No te han compartido tareas.") }
            }
            items(inbox.size) { i -> InboxCard(inbox[i], onRespond) }

            item { Spacer(Modifier.height(8.dp)) }
            item { SectionTitle("Enviadas") }
            if (outbox.isEmpty()) {
                item { EmptyHint("No has compartido tareas.") }
            }
            items(outbox.size) { i -> OutboxCard(outbox[i], onRemind) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun InboxCard(
    item: SharedTaskInbox,
    onRespond: (SharedTaskInbox, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = item.titlePreview.ifBlank { "Tarea compartida" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "De ${displayIdentity(item.ownerFullName, item.ownerUsername)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            when (item.status) {
                SharedTaskInbox.STATUS_PENDING -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { onRespond(item, true) }) { Text("Aceptar") }
                    OutlinedButton(onClick = { onRespond(item, false) }) { Text("Rechazar") }
                }
                SharedTaskInbox.STATUS_ACCEPTED -> Text(
                    text = "Aceptada",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                else -> Text(
                    text = "Rechazada",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun OutboxCard(
    item: CollaborationClient.OutboxItem,
    onRemind: (String, CollaborationClient.RecipientStatus) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = item.titlePreview.ifBlank { "Tarea compartida" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            item.recipients.forEach { recipient ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayIdentity(recipient.fullName, recipient.username),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = statusLabel(recipient.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (recipient.status == "ACCEPTED") {
                        val left = 3 - recipient.remindersSent24h
                        OutlinedButton(
                            onClick = { onRemind(item.sharedTaskId, recipient) },
                            enabled = left > 0
                        ) {
                            Text(if (left > 0) "Recordar ($left)" else "Sin toques")
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "ACCEPTED" -> "Aceptada"
    "REJECTED" -> "Rechazada"
    else -> "Pendiente de respuesta"
}
