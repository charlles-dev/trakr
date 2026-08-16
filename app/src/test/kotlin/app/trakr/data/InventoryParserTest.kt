package app.trakr.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryParserTest {
    // ---------------- parseInventory ----------------

    @Test
    fun parseInventory_mapsTools() {
        val json =
            """
            {
              "tools": [
                {"id": "01", "name": "Parafusadeira", "tag": "E28011606000020400000001", "present": true},
                {"id": "02", "name": "Furadeira", "tag": "E28011606000020400000002", "present": false}
              ]
            }
            """.trimIndent()

        val tools = InventoryParser.parseInventory(json)

        assertEquals(2, tools.size)

        val first = tools[0]
        assertEquals("01", first.id)
        assertEquals("Parafusadeira", first.name)
        assertEquals("E28011606000020400000001", first.epc)
        assertTrue(first.present)

        assertEquals("E28011606000020400000002", tools[1].epc)
        assertTrue(!tools[1].present)
    }

    @Test
    fun parseInventory_missingFieldsFallsBackToDefaults() {
        val json = """{"tools":[{"name":"Só o nome"}]}"""

        val tools = InventoryParser.parseInventory(json)

        assertEquals(1, tools.size)
        // id fallback "t0" e EPC vazio
        assertEquals("t0", tools[0].id)
        assertEquals("", tools[0].epc)
        assertTrue(!tools[0].present)
    }

    @Test
    fun parseInventory_emptyToolsYieldsEmptyList() {
        assertTrue(InventoryParser.parseInventory("""{"id":"x","tools":[]}""").isEmpty())
        assertTrue(InventoryParser.parseInventory("""{"id":"x"}""").isEmpty())
        assertTrue(InventoryParser.parseInventory("not json").isEmpty())
    }

    @Test
    fun parseInventory_fallbackNameAndEpcFieldAlias() {
        val json = """{"tools":[{"id":"07","epc":"E2801160"}]}"""

        val tools = InventoryParser.parseInventory(json)

        assertEquals(1, tools.size)
        assertEquals("Ferramenta 1", tools[0].name)
        assertEquals("E2801160", tools[0].epc)
    }

    // ---------------- parseRadarReport ----------------

    @Test
    fun parseRadarReport_mapsReport() {
        val report =
            InventoryParser.parseRadarReport(
                """{"type":"radar_report","tag":"E28011606000020400000001","rssi":-52,"present":true}""",
            )

        assertEquals("E28011606000020400000001", report?.tag)
        assertEquals(-52, report?.rssi)
        assertTrue(report?.present == true)
    }

    @Test
    fun parseRadarReport_missingFieldsFallBackToDefaults() {
        val report = InventoryParser.parseRadarReport("""{"type":"radar_report"}""")

        assertEquals("", report?.tag)
        assertEquals(-100, report?.rssi)
        assertTrue(report?.present == false)
    }

    @Test
    fun parseRadarReport_otherTypeReturnsNull() {
        assertNull(InventoryParser.parseRadarReport("""{"type":"tool_missing","name":"X"}"""))
        assertNull(InventoryParser.parseRadarReport("not json"))
    }

    // ---------------- parseCmdReply ----------------

    @Test
    fun parseCmdReply_mapsAck() {
        val reply =
            InventoryParser.parseCmdReply(
                """{"type":"cmd_reply","cmd":"start_radar","status":"ok"}""",
            )

        assertEquals("start_radar", reply?.cmd)
        assertEquals("ok", reply?.status)
        assertNull(reply?.reason)
    }

    @Test
    fun parseCmdReply_withReason() {
        val reply =
            InventoryParser.parseCmdReply(
                """{"type":"cmd_reply","cmd":"start_radar","status":"error","reason":"tool_not_found"}""",
            )

        assertEquals("error", reply?.status)
        assertEquals("tool_not_found", reply?.reason)
    }

    @Test
    fun parseCmdReply_blankReasonYieldsNullReason() {
        val reply =
            InventoryParser.parseCmdReply(
                """{"type":"cmd_reply","cmd":"ota_begin","status":"ok","reason":" "}""",
            )

        assertEquals("ota_begin", reply?.cmd)
        assertEquals("ok", reply?.status)
        assertNull(reply?.reason)
    }

    @Test
    fun parseCmdReply_invalidJsonReturnsNull() {
        assertNull(InventoryParser.parseCmdReply("not json"))
        assertNull(InventoryParser.parseCmdReply("""{"type":123}"""))
    }

    @Test
    fun parseCmdReply_otherTypeReturnsNull() {
        assertNull(InventoryParser.parseCmdReply("""{"type":"radar_report","rssi":-50}"""))
    }

    // ---------------- parseTrackerConfig ----------------

    @Test
    fun parseTrackerConfig_mapsConfig() {
        val config =
            InventoryParser.parseTrackerConfig(
                """{"type":"cmd_reply","cmd":"get_config","status":"ok","listen_ms":15000,"radar_ms":60000,"beep":false}""",
            )

        assertEquals(15000, config?.listenMs)
        assertEquals(60000, config?.radarMs)
        assertEquals(false, config?.beep)
    }

    @Test
    fun parseTrackerConfig_setConfigReplyAlsoMaps() {
        val config =
            InventoryParser.parseTrackerConfig(
                """{"type":"cmd_reply","cmd":"set_config","status":"ok","listen_ms":30000,"radar_ms":120000,"beep":true}""",
            )

        assertEquals(30000, config?.listenMs)
        assertEquals(120000, config?.radarMs)
        assertEquals(true, config?.beep)
    }

    @Test
    fun parseTrackerConfig_missingFieldsFallBackToDefaults() {
        val config =
            InventoryParser.parseTrackerConfig(
                """{"type":"cmd_reply","cmd":"get_config","status":"ok"}""",
            )

        assertEquals(30000, config?.listenMs)
        assertEquals(120000, config?.radarMs)
        assertEquals(true, config?.beep)
    }

    @Test
    fun parseTrackerConfig_errorStatusReturnsNull() {
        assertNull(
            InventoryParser.parseTrackerConfig(
                """{"type":"cmd_reply","cmd":"set_config","status":"error","reason":"save_failed"}""",
            ),
        )
    }

    @Test
    fun parseTrackerConfig_otherCommandReturnsNull() {
        assertNull(
            InventoryParser.parseTrackerConfig(
                """{"type":"cmd_reply","cmd":"rescan","status":"ok"}""",
            ),
        )
        assertNull(InventoryParser.parseTrackerConfig("not json"))
    }
}
