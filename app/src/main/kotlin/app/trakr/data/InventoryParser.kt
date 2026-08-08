package app.trakr.data

import app.trakr.model.AlertEvent
import app.trakr.model.MAIN_TOOLBOX_ID
import app.trakr.model.Tool
import app.trakr.model.Toolbox
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Parse dos payloads JSON recebidos do firmware via BLE. */
object InventoryParser {

    /** Converte o inventário (mesmo formato do inventory.json) em entidades Room. */
    fun parseInventory(json: String): Pair<Toolbox, List<Tool>> {
        val root = JSONObject(json)
        val toolbox = Toolbox(
            id = MAIN_TOOLBOX_ID,
            name = root.optString("toolbox", "Trakr"),
            lastSyncAt = System.currentTimeMillis(),
        )

        val array: JSONArray = root.optJSONArray("tools") ?: JSONArray()
        val tools = mutableListOf<Tool>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            tools += Tool(
                id = o.optString("id", "t$i"),
                toolboxId = MAIN_TOOLBOX_ID,
                name = o.optString("name", "Ferramenta ${i + 1}"),
                icon = "wrench",
                present = o.optBoolean("present", true),
            )
        }
        return toolbox to tools
    }

    data class ToolEvent(val toolId: String, val toolName: String)

    /** Evento de retirada: {"type":"tool_missing","tool_id":"01","name":"..."} */
    fun parseEvent(json: String): ToolEvent? {
        return try {
            val root = JSONObject(json)
            if (root.optString("type") != "tool_missing") return null
            ToolEvent(
                toolId = root.optString("tool_id", "?"),
                toolName = root.optString("name", "Ferramenta"),
            )
        } catch (e: JSONException) {
            null
        }
    }
}