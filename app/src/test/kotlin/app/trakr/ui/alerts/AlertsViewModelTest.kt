package app.trakr.ui.alerts

import app.trakr.R
import app.trakr.model.AlertEvent
import app.trakr.repository.ToolRepository
import app.trakr.testutil.FakeToolDao
import app.trakr.testutil.MainDispatcherRule
import app.trakr.ui.UiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao = FakeToolDao()
    private val repository = ToolRepository(dao)

    @Test
    fun alerts_streamFromDao() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.insertAlert(AlertEvent(toolId = "t1", toolName = "Chave"))
            val vm = AlertsViewModel(repository)
            advanceUntilIdle()

            val alerts = vm.alerts.first()

            assertEquals(1, alerts.size)
            assertEquals("Chave", alerts.first().toolName)
        }

    @Test
    fun markRead_updatesAlertFlag() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.insertAlert(AlertEvent(id = 1, toolId = "t1", toolName = "Chave"))
            val vm = AlertsViewModel(repository)
            advanceUntilIdle()

            vm.markRead(dao.observeAlerts().first().first())
            advanceUntilIdle()

            assertTrue(dao.observeAlerts().first().first().read)
        }

    @Test
    fun clearAll_removesAlertsAndShowsMessage() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.insertAlert(AlertEvent(toolId = "t1", toolName = "Chave"))
            val vm = AlertsViewModel(repository)
            advanceUntilIdle()

            vm.clearAll()
            advanceUntilIdle()

            assertTrue(dao.observeAlerts().first().isEmpty())
            assertEquals(UiMessage(R.string.alerts_cleared), vm.message.value)
        }
}
