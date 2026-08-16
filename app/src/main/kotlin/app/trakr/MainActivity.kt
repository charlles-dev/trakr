package app.trakr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import app.trakr.core.ble.BleForegroundService
import app.trakr.core.nfc.NfcPairingManager
import app.trakr.ui.TrakrApp
import app.trakr.ui.settings.SettingsPrefs
import app.trakr.ui.theme.ThemePrefs
import app.trakr.ui.theme.TrakrTheme
import android.widget.Toast

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TARGET_ID = "target_id"
        const val EXTRA_NFC_BLE = "nfc_ble_address"
    }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { granted ->
            if (granted.values.any { it }) startBleService()
        }

    private val pendingTargetId = mutableStateOf<String?>(null)
    private val pendingNfcBle = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTargetId.value = intent.getStringExtra(EXTRA_TARGET_ID)
        handleNfcIntent(intent)
        requestRuntimePermissions()
        setContent {
            var darkTheme by remember { mutableStateOf(ThemePrefs.isDark(this)) }
            var absenceAlerts by remember { mutableStateOf(SettingsPrefs.absenceAlertsEnabled(this)) }
            TrakrTheme(darkTheme = darkTheme) {
                TrakrApp(
                    darkTheme = darkTheme,
                    onToggleTheme = {
                        darkTheme = !darkTheme
                        ThemePrefs.setDark(this, darkTheme)
                    },
                    absenceAlerts = absenceAlerts,
                    onAbsenceAlertsChange = { enabled ->
                        absenceAlerts = enabled
                        SettingsPrefs.setAbsenceAlertsEnabled(this, enabled)
                    },
                    initialTargetId = pendingTargetId.value,
                    onTargetConsumed = { pendingTargetId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTargetId.value = intent.getStringExtra(EXTRA_TARGET_ID)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent) {
        if (intent.action == android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent.action == android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent.action == android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED) {
            val nfcData = NfcPairingManager.parseIntent(intent)
            if (nfcData != null) {
                pendingNfcBle.value = nfcData.bleAddress ?: nfcData.raw
                Toast.makeText(this, "NFC: ${nfcData.raw.take(30)}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startBleService() {
        val intent = Intent(this, BleForegroundService::class.java)
        startForegroundService(intent)
    }

    private fun requestRuntimePermissions() {
        val needed =
            buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.BLUETOOTH_SCAN)
                    }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        if (needed.isEmpty()) {
            startBleService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
