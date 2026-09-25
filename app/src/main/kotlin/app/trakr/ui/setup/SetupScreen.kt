package app.trakr.ui.setup

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trakr.core.ble.BleManager
import app.trakr.ui.settings.SettingsPrefs
import app.trakr.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val discoveredDevices by BleManager.discoveredDevices.collectAsStateWithLifecycle()

    var showPinDialog by remember { mutableStateOf<BluetoothDevice?>(null) }
    var pinInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        BleManager.discoverMode = true
        BleManager.rescan()
    }

    DisposableEffect(Unit) {
        onDispose {
            BleManager.discoverMode = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Adicionar Maleta", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Icon(
                imageVector = Icons.Filled.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Buscando rastreadores próximos...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ligue a sua maleta TRK-Finder e aguarde.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (discoveredDevices.isEmpty()) {
                CircularProgressIndicator(color = NeonGreen)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredDevices, key = { it.address }) { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPinDialog = device },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = NeonGreen)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = device.name ?: "Dispositivo Desconhecido",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog != null) {
        AlertDialog(
            onDismissRequest = { showPinDialog = null },
            title = { Text("Parear com Rastreador") },
            text = {
                Column {
                    Text("Insira o PIN de 6 dígitos mostrado no visor OLED da maleta para autorizar a conexão.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6) pinInput = it },
                        label = { Text("PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val device = showPinDialog!!
                        SettingsPrefs.setTrackerMac(context, device.address)
                        SettingsPrefs.setTrackerPin(context, pinInput)
                        
                        BleManager.discoverMode = false
                        BleManager.connectSpecificDevice(device)
                        
                        onSetupComplete()
                    },
                    enabled = pinInput.length >= 4 // Permite de 4 a 6 digitos p/ testes
                ) {
                    Text("Conectar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
