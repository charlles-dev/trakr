package app.trakr.ui.settings

import app.trakr.R
import app.trakr.core.ble.BleDeviceInfo
import app.trakr.data.InventoryParser.CmdReply
import app.trakr.data.InventoryParser.TrackerConfig
import app.trakr.repository.ToolRepository
import app.trakr.testutil.FakeBleGateway
import app.trakr.testutil.FakeToolDao
import app.trakr.testutil.MainDispatcherRule
import app.trakr.ui.UiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val ble = FakeBleGateway()
    private val dao = FakeToolDao()
    private val repository = ToolRepository(dao)

    private fun vm() = ConfigViewModel(ble, repository)

    private fun replyWithPayload(
        cmd: String,
        json: String,
    ): CmdReply {
        val payload = JSONObject(json)
        return CmdReply(cmd = cmd, status = "ok", reason = null, payload = payload)
    }

    @Test
    fun loadConfig_sendsGetConfig() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            vm.loadConfig()

            assertEquals(1, ble.getConfigCalls)
        }

    @Test
    fun loadConfig_bleUnavailable_showsNoDevice() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            vm.loadConfig()
            ble.getConfigUnavailable?.invoke()

            assertEquals(UiMessage(R.string.msg_no_device), vm.message.value)
        }

    @Test
    fun setBeep_sendsBeepField() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            vm.setBeep(false)

            assertEquals(mapOf("beep" to false), ble.setConfigCalls.last())
        }

    @Test
    fun setListenMs_sendsListenMsField() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            vm.setListenMs(60000)

            assertEquals(mapOf("listen_ms" to 60000), ble.setConfigCalls.last())
        }

    @Test
    fun setRadarMs_sendsRadarMsField() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            vm.setRadarMs(180000)

            assertEquals(mapOf("radar_ms" to 180000), ble.setConfigCalls.last())
        }

    @Test
    fun setConfig_bleUnavailable_showsNoDevice() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            vm.setBeep(true)
            ble.setConfigUnavailable?.invoke()

            assertEquals(UiMessage(R.string.msg_no_device), vm.message.value)
        }

    @Test
    fun ackGetConfigOk_updatesConfig() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            ble.lastReply.value =
                replyWithPayload(
                    "get_config",
                    """{"listen_ms":15000,"radar_ms":60000,"beep":false}""",
                )
            advanceUntilIdle()

            assertEquals(TrackerConfig(listenMs = 15000, radarMs = 60000, beep = false), vm.config.value)
        }

    @Test
    fun ackSetConfigOk_updatesConfig() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            ble.lastReply.value =
                replyWithPayload(
                    "set_config",
                    """{"listen_ms":30000,"radar_ms":180000,"beep":true}""",
                )
            advanceUntilIdle()

            assertEquals(TrackerConfig(listenMs = 30000, radarMs = 180000, beep = true), vm.config.value)
        }

    @Test
    fun ackConfigError_showsConfigFailed() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            ble.lastReply.value =
                CmdReply(cmd = "set_config", status = "error", reason = "save_failed")
            advanceUntilIdle()

            assertEquals(UiMessage(R.string.msg_config_failed), vm.message.value)
            assertNull(vm.config.value)
        }

    @Test
    fun ackUnrelatedCommand_ignored() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            ble.lastReply.value = CmdReply(cmd = "rescan", status = "ok", reason = null)
            advanceUntilIdle()

            assertNull(vm.config.value)
            assertNull(vm.message.value)
        }

    @Test
    fun deviceConnects_autoLoadsConfigOnce() =
        runTest(mainDispatcherRule.dispatcher) {
            ble.devices.value = listOf(BleDeviceInfo(address = "AA:BB:CC", name = "TRK-01"))
            val vm = vm()

            advanceUntilIdle()

            assertEquals(1, ble.getConfigCalls)
            assertNull(vm.config.value)
        }
}
