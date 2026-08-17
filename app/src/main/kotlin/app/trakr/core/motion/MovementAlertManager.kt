package app.trakr.core.motion

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import app.trakr.model.Tool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MovementAlertManager {
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _movementAlert = MutableStateFlow<String?>(null)
    val movementAlert: StateFlow<String?> = _movementAlert.asStateFlow()

    private var lastLocation: Location? = null
    private var lastAlertTs = 0L

    private val locationListener =
        object : LocationListener {
            override fun onLocationChanged(location: Location) {
                checkMovement(location)
            }

            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: Bundle?,
            ) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }

    private var currentToolsProvider: (() -> List<Tool>)? = null

    @SuppressLint("MissingPermission")
    fun startMonitoring(
        context: Context,
        toolsProvider: () -> List<Tool>,
    ) {
        currentToolsProvider = toolsProvider
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 10f, locationListener)
                _isMonitoring.value = true
            } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 10f, locationListener)
                _isMonitoring.value = true
            }
        } catch (_: Exception) {
            _isMonitoring.value = false
        }
    }

    fun stopMonitoring(context: Context) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            lm.removeUpdates(locationListener)
        } catch (_: Exception) {
            // Ignorado
        }
        _isMonitoring.value = false
    }

    fun clearAlert() {
        _movementAlert.value = null
    }

    private fun checkMovement(location: Location) {
        val speedKmH = if (location.hasSpeed()) location.speed * 3.6f else 0f
        val prev = lastLocation
        lastLocation = location

        val hasMovedFast =
            speedKmH > 15f ||
                (prev != null && prev.distanceTo(location) > 150f && (location.time - prev.time) < 30000L)

        if (hasMovedFast) {
            val now = System.currentTimeMillis()
            if (now - lastAlertTs > 60000L) { // Cooldown de 1 minuto
                val tools = currentToolsProvider?.invoke() ?: emptyList()
                val missingTools = tools.filter { !it.present }
                if (missingTools.isNotEmpty()) {
                    lastAlertTs = now
                    val names = missingTools.take(3).joinToString(", ") { it.name }
                    val extra = if (missingTools.size > 3) " e mais ${missingTools.size - 3}" else ""
                    _movementAlert.value = "Atenção: Em deslocamento com ferramentas ausentes ($names$extra)!"
                }
            }
        }
    }
}
