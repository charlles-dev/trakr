package app.trakr.core.notifications

import android.content.Context
import android.util.Log
import app.trakr.data.AppContainer
import app.trakr.model.AlertEvent
import app.trakr.model.Tool
import app.trakr.repository.ToolRepository
import app.trakr.ui.settings.SettingsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class AbsenceWatcher(private val context: Context) {
    companion object {
        private const val TAG = "AbsenceWatcher"
        const val ABSENCE_MS = 60_000L
        private const val TICK_MS = 30_000L

        internal fun evaluateAbsent(
            tools: List<Tool>,
            now: Long,
            alerted: MutableSet<String>,
        ): List<Tool> {
            val toNotify = mutableListOf<Tool>()
            tools.forEach { tool ->
                val lastSeen = tool.lastSeenAt
                if (tool.present) {
                    alerted.remove(tool.id)
                } else if (lastSeen != null &&
                    now - lastSeen >= ABSENCE_MS &&
                    alerted.add(tool.id)
                ) {
                    toNotify += tool
                }
            }
            return toNotify
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository = ToolRepository(AppContainer.database.toolDao())
    private val notifier = NotificationService(context)
    private val alerted = mutableSetOf<String>()
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job =
            scope.launch {
                val ticker =
                    flow {
                        while (true) {
                            delay(TICK_MS)
                            emit(Unit)
                        }
                    }
                combine(
                    repository.observeTools(),
                    repository.observeAllToolSettings(),
                    repository.observeTrackerMutes(),
                    ticker,
                ) { tools, settings, mutes, _ ->
                    Triple(tools, settings, mutes)
                }.collectLatest { (tools, settings, mutes) ->
                    check(tools, settings, mutes)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
        alerted.clear()
    }

    private suspend fun check(
        tools: List<Tool>,
        toolSettings: List<app.trakr.model.ToolAlertSetting>,
        trackerMutes: List<app.trakr.model.TrackerMute>,
    ) {
        if (!SettingsPrefs.absenceAlertsEnabled(context)) {
            alerted.clear()
            return
        }
        if (trackerMutes.isNotEmpty() && trackerMutes.all { it.muted }) return
        val now = System.currentTimeMillis()
        evaluateAbsent(tools, now, alerted).forEach { tool ->
            try {
                val setting = toolSettings.find { it.toolId == tool.id }
                if (setting?.muted == true) return@forEach
                notifier.showMissingToolAlert(tool.name, tool.id, setting)
                repository.insertAlert(
                    AlertEvent(toolId = tool.id, toolName = tool.name),
                )
            } catch (e: Exception) {
                alerted.remove(tool.id)
                Log.w(TAG, "Falha ao registrar alerta de ausencia", e)
            }
        }
    }
}
