package app.trakr.ui.radar

import app.trakr.R
import app.trakr.core.ble.BleDeviceInfo
import app.trakr.data.InventoryParser
import app.trakr.model.RadarReport
import app.trakr.model.Tool
import app.trakr.repository.ToolRepository
import app.trakr.testutil.FakeBleGateway
import app.trakr.testutil.FakeToolDao
import app.trakr.testutil.MainDispatcherRule
import app.trakr.ui.UiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao = FakeToolDao()
    private val ble = FakeBleGateway()
    private val repository = ToolRepository(dao)

    private val tool = Tool(id = "t1", name = "Chave", epc = "E2801160")

    private fun vm() = RadarViewModel(repository, ble)

    @Test
    fun start_withoutTarget_showsChooseTargetAndStaysStopped() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            vm.start()

            assertEquals(UiMessage(R.string.msg_choose_target), vm.message.value)
            assertFalse(vm.running.value)
            assertTrue(ble.startRadarCalls.isEmpty())
        }

    @Test
    fun start_withTarget_sendsToolIdAndTag() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.upsertTool(tool)
            val vm = vm()
            advanceUntilIdle()
            vm.selectTarget(tool.id)

            vm.start()

            val (toolId, tag) = ble.startRadarCalls.last()
            assertEquals(tool.id, toolId)
            assertEquals(tool.epc, tag)
            assertTrue(vm.running.value)
        }

    @Test
    fun start_bleUnavailable_revertsRunningAndShowsNoDevice() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.upsertTool(tool)
            val vm = vm()
            advanceUntilIdle()
            vm.selectTarget(tool.id)

            vm.start()
            ble.startRadarUnavailable?.invoke()
            advanceUntilIdle()

            assertFalse(vm.running.value)
            assertEquals(UiMessage(R.string.msg_no_device), vm.message.value)
        }

    @Test
    fun ackStartRadarError_toolNotFound_showsTargetNotFound() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.upsertTool(tool)
            val vm = vm()
            advanceUntilIdle()
            vm.selectTarget(tool.id)
            vm.start()

            ble.lastReply.value =
                InventoryParser.CmdReply("start_radar", "error", "tool_not_found")
            advanceUntilIdle()

            assertFalse(vm.running.value)
            assertEquals(UiMessage(R.string.msg_target_not_found), vm.message.value)
        }

    @Test
    fun ackStartRadarError_unknownCmd_showsRadarUnsupported() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.upsertTool(tool)
            val vm = vm()
            advanceUntilIdle()
            vm.selectTarget(tool.id)
            vm.start()

            ble.lastReply.value = InventoryParser.CmdReply("start_radar", "error", "unknown_cmd")
            advanceUntilIdle()

            assertEquals(UiMessage(R.string.msg_radar_unsupported), vm.message.value)
        }

    @Test
    fun ackStartRadarError_otherReason_showsStartRefused() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.upsertTool(tool)
            val vm = vm()
            advanceUntilIdle()
            vm.selectTarget(tool.id)
            vm.start()

            ble.lastReply.value = InventoryParser.CmdReply("start_radar", "error", null)
            advanceUntilIdle()

            assertEquals(UiMessage(R.string.msg_start_refused), vm.message.value)
        }

    @Test
    fun ackStartRadarOk_keepsRunning() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.upsertTool(tool)
            val vm = vm()
            advanceUntilIdle()
            vm.selectTarget(tool.id)
            vm.start()

            ble.lastReply.value = InventoryParser.CmdReply("start_radar", "ok", null)
            advanceUntilIdle()

            assertTrue(vm.running.value)
        }

    @Test
    fun ackStopRadar_stopsRunning() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.upsertTool(tool)
            val vm = vm()
            advanceUntilIdle()
            vm.selectTarget(tool.id)
            vm.start()

            ble.lastReply.value = InventoryParser.CmdReply("stop_radar", "ok", null)
            advanceUntilIdle()

            assertFalse(vm.running.value)
        }

    @Test
    fun stop_sendsStopCommand() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            vm.stop()

            assertEquals(1, ble.stopRadarCalls)
            assertFalse(vm.running.value)
        }

    @Test
    fun hasRadarDevice_reflectsConnectedDevices() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()
            advanceUntilIdle()
            assertFalse(vm.hasRadarDevice.value)

            ble.devices.value = listOf(BleDeviceInfo(address = "AA:BB", name = "TRK-FINDER"))
            advanceUntilIdle()

            assertTrue(vm.hasRadarDevice.value)
        }

    @Test
    fun radarReport_streamsFromGateway() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()
            val report = RadarReport(tag = "E2801160", rssi = -45, present = true)

            ble.radarReport.value = report

            assertEquals(report, vm.radarReport.value)
        }
}
