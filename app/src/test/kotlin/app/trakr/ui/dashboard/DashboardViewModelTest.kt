package app.trakr.ui.dashboard

import app.trakr.core.ble.BleDeviceInfo
import app.trakr.core.ble.BleStatus
import app.trakr.model.Tool
import app.trakr.repository.ToolboxRepository
import app.trakr.testutil.FakeBleGateway
import app.trakr.testutil.FakeToolboxDao
import app.trakr.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao = FakeToolboxDao()
    private val ble = FakeBleGateway()
    private val repository = ToolboxRepository(dao)

    @Test
    fun tools_streamFromDao() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.upsertTool(Tool(id = "t1", name = "Chave", epc = "E2801160"))
            val vm = DashboardViewModel(repository, ble)

            assertEquals(1, vm.tools.first().size)
        }

    @Test
    fun devices_streamFromGateway() =
        runTest(mainDispatcherRule.dispatcher) {
            ble.devices.value = listOf(BleDeviceInfo(address = "AA:BB", name = "TRK-FINDER"))
            val vm = DashboardViewModel(repository, ble)

            assertTrue(vm.devices.value.isNotEmpty())
        }

    @Test
    fun consumeMessage_clearsMessage() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = DashboardViewModel(repository, ble)

            assertNull(vm.message.value)
            vm.consumeMessage()
        }

    @Test
    fun status_streamFromGateway() =
        runTest(mainDispatcherRule.dispatcher) {
            ble.status.value = BleStatus.Scanning
            val vm = DashboardViewModel(repository, ble)

            assertTrue(vm.status.value is BleStatus.Scanning)
        }

    @Test
    fun refresh_asksBleRescanAndShowsRefreshing() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = DashboardViewModel(repository, ble)

            vm.refresh()

            assertEquals(1, ble.rescanCalls)
            assertTrue(vm.refreshing.value)
        }

    @Test
    fun tools_sortedPresentFirst() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.upsertTool(Tool(id = "t1", name = "Furadeira", epc = "E1", present = false))
            dao.upsertTool(Tool(id = "t2", name = "Chave", epc = "E2", present = true))
            val vm = DashboardViewModel(repository, ble)

            val tools = vm.tools.first()

            assertEquals(listOf("Chave", "Furadeira"), tools.map { it.name })
        }
}
