package app.trakr.ui.tools

import app.trakr.model.RssiSample
import app.trakr.repository.ToolboxRepository
import app.trakr.testutil.FakeToolboxDao
import app.trakr.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao = FakeToolboxDao()
    private val repository = ToolboxRepository(dao)

    @Test
    fun setEpc_nullEmitsEmptyList() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = ToolDetailViewModel(repository)
            val collected = mutableListOf<List<RssiSample>>()
            val job = launch { vm.samples.collect { collected += it } }

            vm.setEpc("EPC-A")
            advanceUntilIdle()
            job.cancel()

            assertEquals(emptyList<RssiSample>(), collected.last())
        }

    @Test
    fun setEpc_emitsSamplesOnlyForThatEpc() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.insertRssiSample(RssiSample(epc = "EPC-A", rssi = -50))
            dao.insertRssiSample(RssiSample(epc = "EPC-A", rssi = -62))
            dao.insertRssiSample(RssiSample(epc = "EPC-B", rssi = -70))
            val vm = ToolDetailViewModel(repository)
            val collected = mutableListOf<List<RssiSample>>()
            val job = launch { vm.samples.collect { collected += it } }

            vm.setEpc("EPC-A")
            advanceUntilIdle()
            job.cancel()

            assertTrue(collected.last().isNotEmpty())
            assertEquals(listOf("EPC-A", "EPC-A"), collected.last().map { it.epc })
        }

    @Test
    fun setEpc_switchEpc_updatesSamples() =
        runTest(mainDispatcherRule.dispatcher) {
            dao.insertRssiSample(RssiSample(epc = "EPC-A", rssi = -50))
            dao.insertRssiSample(RssiSample(epc = "EPC-B", rssi = -70))
            val vm = ToolDetailViewModel(repository)
            val collected = mutableListOf<List<RssiSample>>()
            val job = launch { vm.samples.collect { collected += it } }

            vm.setEpc("EPC-A")
            advanceUntilIdle()
            vm.setEpc("EPC-B")
            advanceUntilIdle()
            job.cancel()

            assertEquals(listOf("EPC-B"), collected.last().map { it.epc })
        }
}
