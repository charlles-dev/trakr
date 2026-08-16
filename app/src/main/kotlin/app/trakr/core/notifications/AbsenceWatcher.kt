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

/**
 * Vigia o inventÃ¡rio local e dispara a notificaÃ§Ã£o de ausÃªncia: quando o
 * rastreador viu a tag ao menos uma vez ([Tool.lastSeenAt]) e deixa de vÃª-la
 * por [ABSENCE_MS], o app emite push local + registra um [AlertEvent].
 *
 * Roda dentro do [app.trakr.core.ble.BleForegroundService] para funcionar com
 * o celular bloqueado. O dedupe Ã© por ferramenta: sÃ³ dispara de novo depois
 * que a tag voltar a ser vista (present = true).
 */
class AbsenceWatcher(private val context: Context) {
    companion object {
        private const val TAG = "AbsenceWatcher"

        /** Tempo sem ver a tag para considerar "ausente" (modo radar). */
        const val ABSENCE_MS = 60_000L

        /** Ticker de reavaliaÃ§Ã£o caso o Flow do Room nÃ£o emita (ex: radar parado). */
        private const val TICK_MS = 30_000L

        /**
         * Regra pura de ausÃªncia: atualiza [alerted] e devolve as tags que
         * devem disparar alerta agora. Tags presentes resetam o dedupe.
         */
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
                combine(repository.observeTools(), ticker) { tools, _ -> tools }
                    .collectLatest { tools -> check(tools) }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
        alerted.clear()
    }

    private suspend fun check(tools: List<Tool>) {
        // Alertas desativados na Config: limpa o dedupe para que a reativaÃ§Ã£o
        // notifique imediatamente as ferramentas que continuam ausentes.
        if (!SettingsPrefs.absenceAlertsEnabled(context)) {
            alerted.clear()
            return
        }
        val now = System.currentTimeMillis()
        evaluateAbsent(tools, now, alerted).forEach { tool ->
            try {
                notifier.showMissingToolAlert(tool.name, tool.id)
                repository.insertAlert(
                    AlertEvent(toolId = tool.id, toolName = tool.name),
                )
            } catch (e: Exception) {
                // NÃ£o deixa uma falha de notificaÃ§Ã£o matar o watcher (collectLatest):
                // libera o dedupe para tentar de novo no prÃ³ximo ciclo.
                alerted.remove(tool.id)
                Log.w(TAG, "Falha ao registrar alerta de ausÃªncia", e)
            }
        }
    }
}
