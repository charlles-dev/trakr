@file:Suppress("ktlint:standard:max-line-length", "MaxLineLength")

package app.trakr.ui.kits

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trakr.model.JobKit
import app.trakr.model.Tool
import app.trakr.ui.components.EmptyState
import app.trakr.ui.components.SectionHeader
import app.trakr.ui.components.maxContentWidth
import app.trakr.ui.motion.pressScale
import app.trakr.ui.theme.AlertRed
import app.trakr.ui.theme.MonospaceTypography
import app.trakr.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobKitsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JobKitsViewModel = viewModel(factory = JobKitsViewModel.Factory),
) {
    val kits by viewModel.allKits.collectAsStateWithLifecycle()
    val allTools by viewModel.allTools.collectAsStateWithLifecycle()
    val activeKit by viewModel.activeMissionKit.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Kits & Missões de Serviço") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.pressScale(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Novo Kit")
            }
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .maxContentWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Card de Missão Ativa (se houver)
                activeKit?.let { kit ->
                    item {
                        SectionHeader("MISSÃO ATIVA EM ANDAMENTO")
                    }
                    item {
                        ActiveMissionCard(
                            kit = kit,
                            allTools = allTools,
                            onFinish = viewModel::finishMission,
                        )
                    }
                }

                item {
                    SectionHeader("KITS DE FERRAMENTAS CADASTRADOS")
                }

                if (kits.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.AssignmentTurnedIn,
                            title = "Nenhum kit de trabalho",
                            hint = "Crie kits com as ferramentas específicas para cada tipo de serviço (elétrico, mecânico, etc.) para conferência rápida de entrada e saída.",
                        )
                    }
                } else {
                    items(kits, key = { it.id }) { kit ->
                        KitCard(
                            kit = kit,
                            allTools = allTools,
                            isActive = activeKit?.id == kit.id,
                            onStartMission = { viewModel.startMission(kit) },
                            onDelete = { viewModel.deleteKit(kit.id) },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateKitDialog(
            allTools = allTools,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc, selectedIds ->
                viewModel.createKit(name, desc, selectedIds)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun ActiveMissionCard(
    kit: JobKit,
    allTools: List<Tool>,
    onFinish: () -> Unit,
) {
    val kitToolIds = kit.getToolIdList()
    val kitTools = allTools.filter { it.id in kitToolIds }
    val presentCount = kitTools.count { it.present }
    val total = kitTools.size
    val allPresent = total > 0 && presentCount == total
    val progress = if (total > 0) presentCount.toFloat() / total else 0f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, if (allPresent) NeonGreen else AlertRed),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(kit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (allPresent) "Todos os $total itens conferidos!" else "${total - presentCount} item(ns) pendente(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (allPresent) NeonGreen else AlertRed,
                    )
                }
                Text(
                    "$presentCount / $total",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonospaceTypography,
                    color = if (allPresent) NeonGreen else AlertRed,
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (allPresent) NeonGreen else AlertRed,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )

            // Lista compacta dos itens do kit
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                kitTools.forEach { tool ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tool.name, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (tool.present) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = null,
                                tint = if (tool.present) NeonGreen else AlertRed,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                if (tool.present) "OK" else "AUSENTE",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (tool.present) NeonGreen else AlertRed,
                                fontFamily = MonospaceTypography,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().pressScale(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Finalizar Missão (Check-out)")
            }
        }
    }
}

@Composable
private fun KitCard(
    kit: JobKit,
    allTools: List<Tool>,
    isActive: Boolean,
    onStartMission: () -> Unit,
    onDelete: () -> Unit,
) {
    val kitToolIds = kit.getToolIdList()
    val kitTools = allTools.filter { it.id in kitToolIds }
    val presentCount = kitTools.count { it.present }
    val total = kitTools.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (isActive) NeonGreen else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(kit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (kit.description.isNotBlank()) {
                        Text(
                            kit.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$total ferramentas associadas ($presentCount no local)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MonospaceTypography,
                )

                if (!isActive) {
                    Button(
                        onClick = onStartMission,
                        modifier = Modifier.pressScale(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Iniciar Missão", fontSize = 12.sp)
                    }
                } else {
                    Text("EM EXECUÇÃO", style = MaterialTheme.typography.labelSmall, color = NeonGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CreateKitDialog(
    allTools: List<Tool>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, selectedIds: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Kit de Trabalho") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Kit (ex: Kit Eletricista)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição / Aplicação") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Selecione as ferramentas do kit:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(modifier = Modifier.height(180.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(allTools, key = { it.id }) { tool ->
                        val isChecked = tool.id in selectedIds
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) selectedIds.remove(tool.id) else selectedIds.add(tool.id)
                                    }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { check ->
                                    if (check) selectedIds.add(tool.id) else selectedIds.remove(tool.id)
                                },
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, description, selectedIds.toList()) }) {
                Text("Criar Kit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
