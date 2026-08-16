package app.trakr.core.notifications

import android.content.Context
import app.trakr.data.AppContainer
import app.trakr.model.AlertEvent
import app.trakr.repository.ToolboxRepository
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
 * Vigia o inventário local e dispara a notificação de ausência: quando o
 * rastreador viu a tag ao menos uma vez ([Tool.lastSeenAt]) e deixa de vê-la
 * por [ABSENCE_MS], o app emite push local + registra um [AlertEvent].
 *
 * Roda dentro do [app.trakr.core.ble.BleForegroundService] para funcionar com
 * o celular bloqueado. O dedupe é por ferramenta: só dispara de novo depois
 * que a tag voltar a ser vista (present = true).
 */
class AbsenceWatcher(context: Context) {
    companion object {
        /** Tempo sem ver a tag para considerar "ausente" (modo radar). */
        const val ABSENCE_MS = 60_000L

        /** Ticker de reavaliação caso o Flow do Room não emita (ex: radar parado). */
        private const val TICK_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository = ToolboxRepository(AppContainer.database.toolboxDao())
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

    private suspend fun check(tools: List<app.trakr.model.Tool>) {
        val now = System.currentTimeMillis()
        tools.forEach { tool ->
            val lastSeen = tool.lastSeenAt
            if (tool.present) {
                // Tag voltou a ser vista: libera o próximo alerta.
                alerted.remove(tool.id)
            } else if (lastSeen != null &&
                now - lastSeen >= ABSENCE_MS &&
                alerted.add(tool.id)
            ) {
                notifier.showMissingToolAlert(tool.name)
                repository.insertAlert(
                    AlertEvent(toolId = tool.id, toolName = tool.name),
                )
            }
        }
    }
}
