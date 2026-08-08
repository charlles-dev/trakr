package app.trakr.data

import app.trakr.model.AlertEvent
import app.trakr.model.EventRecord
import app.trakr.model.MAIN_TOOLBOX_ID
import app.trakr.model.Tool
import app.trakr.model.Toolbox
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Parse dos payloads JSON recebidos do firmware via BLE. */
object InventoryParser {

    /** Converte o inventário (mesmo formato do inventory_<id>.json) em entidades Room. */
    fun parseInventory(json: String): Pair<Toolbox, List<Tool>> {
        val root = JSONObject(json)
        val toolboxId = root.optString("id", MAIN_TOOLBOX_ID)
        val toolbox = Toolbox(
            id = toolboxId,
            name = root.optString("toolbox", root.optString("name", "Trakr")),
            lastSyncAt = System.currentTimeMillis(),
        )

        val array: JSONArray = root.optJSONArray("tools") ?: JSONArray()
        val tools = mutableListOf<Tool>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            tools += Tool(
                id = o.optString("id", "t$i"),
                toolboxId = toolboxId,
                name = o.optString("name", "Ferramenta ${i + 1}"),
                icon = "wrench",
                present = o.optBoolean("present", true),
                epc = o.optString("tag", o.optString("epc", "")),
            )
        }
        return toolbox to tools
    }

    /** Converte o histórico (array JSON do History) em entidades Room. */
    fun parseHistory(json: String, toolboxId: String): List<EventRecord> {
        val events = mutableListOf<EventRecord>()
        if (json.isBlank()) return events
        return try {
            val root = JSONObject("""{"events":$json}""")
            val array: JSONArray = root.getJSONArray("events")
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                events += EventRecord(
                    toolboxId = toolboxId,
                    type = o.optString("type", "?"),
                    toolId = o.optString("tool_id", ""),
                    toolName = o.optString("name", ""),
                    ts = o.optLong("ts", 0L),
                )
            }
            events
        } catch (e: JSONException) {
            emptyList()
        }
    }

    data class ToolEvent(val type: String, val toolId: String, val toolName: String)

    /** Evento single: {"type":"tool_missing","tool_id":"01","name":"..."} */
    fun parseEvent(json: String): ToolEvent? {
        return try {
            val root = JSONObject(json)
            val type = root.optString("type")
            if (type.isEmpty()) return null
            ToolEvent(
                type = type,
                toolId = root.optString("tool_id", "?"),
                toolName = root.optString("name", "Ferramenta"),
            )
        } catch (e: JSONException) {
            null
        }
    }
}