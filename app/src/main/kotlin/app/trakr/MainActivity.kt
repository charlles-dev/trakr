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
import app.trakr.ui.TrakrApp
import app.trakr.ui.theme.ThemePrefs
import app.trakr.ui.theme.TrakrTheme

class MainActivity : ComponentActivity() {
    companion object {
        /** Extra com o id da ferramenta aberta por notificação (deep link). */
        const val EXTRA_TARGET_ID = "target_id"
    }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { granted ->
            if (granted.values.any { it }) startBleService()
        }

    private val pendingTargetId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTargetId.value = intent.getStringExtra(EXTRA_TARGET_ID)
        requestRuntimePermissions()
        setContent {
            var darkTheme by remember { mutableStateOf(ThemePrefs.isDark(this)) }
            TrakrTheme(darkTheme = darkTheme) {
                TrakrApp(
                    darkTheme = darkTheme,
                    onToggleTheme = {
                        darkTheme = !darkTheme
                        ThemePrefs.setDark(this, darkTheme)
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
