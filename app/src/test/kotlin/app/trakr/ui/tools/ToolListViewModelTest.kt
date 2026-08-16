package app.trakr.ui.tools

import app.trakr.R
import app.trakr.data.InventoryParser.CmdReply
import app.trakr.model.Tool
import app.trakr.repository.ToolboxRepository
import app.trakr.testutil.FakeBleGateway
import app.trakr.testutil.FakeToolboxDao
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
class ToolListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao = FakeToolboxDao()
    private val ble = FakeBleGateway()
    private val repository = ToolboxRepository(dao)

    private fun vm() = ToolListViewModel(repository, ble)

    @Test
    fun addTool_validInput_trimsAndUppercasesAndSendsToBle() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            vm.addTool("  chave inglesa  ", " 30ba1234abcd  ")

            val (name, epc) = ble.addToolCalls.last()
            assertEquals("chave inglesa", name)
            assertEquals("30BA1234ABCD", epc)
            assertEquals(UiMessage(R.string.msg_adding, listOf("chave inglesa")), vm.message.value)
        }

    @Test
    fun addTool_invalidInput_showsRequiredFieldsAndDoesNotCallBle() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            vm.addTool("  ", "E2801160")
            vm.addTool("Chave", "  ")

            assertEquals(UiMessage(R.string.msg_required_fields), vm.message.value)
            assertTrue(ble.addToolCalls.isEmpty())
        }

    @Test
    fun addTool_bleUnavailable_savesLocallyAndShowsSavedLocal() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            vm.addTool("Chave", "E2801160")
            ble.addToolUnavailable?.invoke()
            advanceUntilIdle()

            val tools = dao.observeTools().first()
            assertEquals(1, tools.size)
            assertEquals("Chave", tools.first().name)
            assertEquals(UiMessage(R.string.msg_saved_local), vm.message.value)
        }

    @Test
    fun removeTool_sendsCommandWithIdAndEpc() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()
            val tool = Tool(id = "t1", name = "Chave", epc = "E2801160")

            vm.removeTool(tool)

            val (id, epc) = ble.removeToolCalls.last().let { it.first to it.second }
            assertEquals("t1", id)
            assertEquals("E2801160", epc)
            assertEquals(UiMessage(R.string.msg_removing, listOf("Chave")), vm.message.value)
        }

    @Test
    fun removeTool_bleUnavailable_removesLocally() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.upsertTool(Tool(id = "t1", name = "Chave", epc = "E2801160"))
            val vm = vm()
            val tool = dao.observeTools().first().first()

            vm.removeTool(tool)
            ble.removeToolUnavailable?.invoke()
            advanceUntilIdle()

            assertTrue(dao.observeTools().first().isEmpty())
            assertEquals(UiMessage(R.string.msg_removed_local), vm.message.value)
        }

    @Test
    fun tools_sortedPresentFirstThenByName() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.upsertTool(Tool(id = "t1", name = "Furadeira", epc = "E1", present = false))
            dao.upsertTool(Tool(id = "t2", name = "Chave", epc = "E2", present = true))
            dao.upsertTool(Tool(id = "t3", name = "Alicate", epc = "E3", present = true))
            val vm = vm()

            val tools = vm.tools.first()

            assertEquals(listOf("Alicate", "Chave", "Furadeira"), tools.map { it.name })
        }

    @Test
    fun refresh_asksBleRescanAndShowsRefreshing() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            vm.refresh()

            assertEquals(1, ble.rescanCalls)
            assertTrue(vm.refreshing.value)
            advanceUntilIdle()
            assertTrue(!vm.refreshing.value)
        }

    @Test
    fun ackAddToolOk_showsSavedRemote() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            ble.lastReply.value = CmdReply(cmd = "add_tool", status = "ok", reason = null)
            advanceUntilIdle()

            assertEquals(UiMessage(R.string.msg_added_remote), vm.message.value)
        }

    @Test
    fun ackAddToolError_showsGenericError() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            ble.lastReply.value = CmdReply(cmd = "add_tool", status = "error", reason = "tool_exists")
            advanceUntilIdle()

            assertEquals(UiMessage(R.string.msg_generic_error), vm.message.value)
        }

    @Test
    fun ackRemoveToolOk_showsRemovedRemote() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = vm()

            ble.lastReply.value = CmdReply(cmd = "remove_tool", status = "ok", reason = null)
            advanceUntilIdle()

            assertEquals(UiMessage(R.string.msg_removed_remote), vm.message.value)
        }
}
