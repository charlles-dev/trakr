package app.trakr.data

import app.trakr.model.RadarReport
import app.trakr.model.Tool
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Parse dos payloads JSON recebidos do firmware via BLE. */
object InventoryParser {
    /**
     * Converte o inventário do rastreador (mesmo formato do inventory.json)
     * em entidades Room.
     */
    fun parseInventory(json: String): List<Tool> {
        return try {
            val root = JSONObject(json)
            val array: JSONArray = root.optJSONArray("tools") ?: JSONArray()
            val tools = mutableListOf<Tool>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                tools +=
                    Tool(
                        id = o.optString("id", "t$i"),
                        name = o.optString("name", "Ferramenta ${i + 1}"),
                        icon = "wrench",
                        present = o.optBoolean("present", false),
                        epc = o.optString("tag", o.optString("epc", "")),
                    )
            }
            tools
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** Relatório do modo radar: {"type":"radar_report","tag":...,"rssi":...,"present":...} */
    fun parseRadarReport(json: String): RadarReport? {
        return try {
            val root = JSONObject(json)
            if (root.optString("type") != "radar_report") return null
            RadarReport(
                tag = root.optString("tag", ""),
                rssi = root.optInt("rssi", -100),
                present = root.optBoolean("present", false),
            )
        } catch (e: JSONException) {
            null
        }
    }

    /** ACK de comando: {"type":"cmd_reply","cmd":...,"status":...,"reason":...} */
    data class CmdReply(
        val cmd: String,
        val status: String,
        val reason: String?,
        val payload: JSONObject? = null,
    )

    fun parseCmdReply(json: String): CmdReply? {
        return try {
            val root = JSONObject(json)
            if (root.optString("type") != "cmd_reply") return null
            CmdReply(
                cmd = root.optString("cmd", "?"),
                status = root.optString("status", "?"),
                reason = root.optString("reason").ifBlank { null },
                payload = root,
            )
        } catch (e: JSONException) {
            null
        }
    }

    /** Configurações do TRK-Finder: {"listen_ms":...,"radar_ms":...,"beep":...} */
    data class TrackerConfig(
        val listenMs: Int,
        val radarMs: Int,
        val beep: Boolean,
    )

    /** Extrai a config do payload de um cmd_reply get_config/set_config. */
    fun trackerConfig(reply: CmdReply): TrackerConfig? {
        if (reply.status != "ok") return null
        if (reply.cmd != "get_config" && reply.cmd != "set_config") return null
        val payload = reply.payload ?: return null
        return TrackerConfig(
            listenMs = payload.optInt("listen_ms", 30000),
            radarMs = payload.optInt("radar_ms", 120000),
            beep = payload.optBoolean("beep", true),
        )
    }

    fun parseTrackerConfig(json: String): TrackerConfig? = parseCmdReply(json)?.let { trackerConfig(it) }
}
