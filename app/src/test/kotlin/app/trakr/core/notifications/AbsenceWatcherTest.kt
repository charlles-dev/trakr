package app.trakr.core.notifications

import app.trakr.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsenceWatcherTest {
    private fun tool(
        id: String,
        present: Boolean = false,
        lastSeenAt: Long? = System.currentTimeMillis(),
    ) = Tool(id = id, name = "Ferramenta $id", epc = "EPC-$id", present = present, lastSeenAt = lastSeenAt)

    @Test
    fun evaluateAbsent_presentTool_resetsDedupe() {
        val alerted = mutableSetOf("t1")

        val due = AbsenceWatcher.evaluateAbsent(listOf(tool("t1", present = true)), 0L, alerted)

        assertTrue(due.isEmpty())
        assertTrue(alerted.isEmpty())
    }

    @Test
    fun evaluateAbsent_recentlySeen_doesNotNotify() {
        val now = System.currentTimeMillis()

        val due =
            AbsenceWatcher.evaluateAbsent(
                listOf(tool("t1", lastSeenAt = now - 10_000L)),
                now,
                mutableSetOf(),
            )

        assertTrue(due.isEmpty())
    }

    @Test
    fun evaluateAbsent_absentPastThreshold_notifiesOnce() {
        val now = System.currentTimeMillis()
        val alerted = mutableSetOf<String>()

        val first = AbsenceWatcher.evaluateAbsent(listOf(tool("t1", lastSeenAt = now - AbsenceWatcher.ABSENCE_MS)), now, alerted)
        val second = AbsenceWatcher.evaluateAbsent(listOf(tool("t1", lastSeenAt = now - AbsenceWatcher.ABSENCE_MS)), now, alerted)

        assertEquals(listOf("t1"), first.map { it.id })
        assertTrue(second.isEmpty())
        assertEquals(setOf("t1"), alerted)
    }

    @Test
    fun evaluateAbsent_neverSeen_doesNotNotify() {
        val now = System.currentTimeMillis()

        val due =
            AbsenceWatcher.evaluateAbsent(
                listOf(tool("t1", lastSeenAt = null)),
                now,
                mutableSetOf(),
            )

        assertTrue(due.isEmpty())
    }

    @Test
    fun evaluateAbsent_returnsAllDueTools() {
        val now = System.currentTimeMillis()

        val due =
            AbsenceWatcher.evaluateAbsent(
                listOf(
                    tool("t1", lastSeenAt = now - AbsenceWatcher.ABSENCE_MS),
                    tool("t2", lastSeenAt = now - AbsenceWatcher.ABSENCE_MS - 1L),
                    tool("t3", lastSeenAt = now - 1L),
                ),
                now,
                mutableSetOf(),
            )

        assertEquals(listOf("t1", "t2"), due.map { it.id })
    }

    @Test
    fun evaluateAbsent_backToPresent_allowsNewAlert() {
        val now = System.currentTimeMillis()
        val alerted = mutableSetOf("t1")
        val absent = tool("t1", lastSeenAt = now - AbsenceWatcher.ABSENCE_MS)

        AbsenceWatcher.evaluateAbsent(listOf(tool("t1", present = true)), now, alerted)

        val due = AbsenceWatcher.evaluateAbsent(listOf(absent), now, alerted)

        assertEquals(listOf("t1"), due.map { it.id })
    }
}
