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

    /** Relatório do modo radar: {"type":"radar_report","tag":...,"rssi":...,"present":...,"delta":...,"hint":...} */
    fun parseRadarReport(json: String): RadarReport? {
        return try {
            val root = JSONObject(json)
            if (root.optString("type") != "radar_report") return null
            RadarReport(
                tag = root.optString("tag", ""),
                rssi = root.optInt("rssi", -100),
                present = root.optBoolean("present", false),
                delta = root.optInt("delta", 0),
                hint = root.optString("hint", "search"),
                threshold = root.optInt("threshold", -70),
            )
        } catch (e: JSONException) {
            null
        }
    }

    fun parseLiveReport(json: String): app.trakr.model.LiveReport? {
        return try {
            val root = JSONObject(json)
            if (root.optString("type") != "live_report") return null
            val arr = root.optJSONArray("reads") ?: return null
            val reads = mutableListOf<app.trakr.model.LiveRead>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                reads += app.trakr.model.LiveRead(tag = o.optString("tag", ""), rssi = o.optInt("rssi", -100))
            }
            app.trakr.model.LiveReport(reads = reads)
        } catch (e: JSONException) {
            null
        }
    }

    fun parseMultiReport(json: String): app.trakr.model.MultiRadarReport? {
        return try {
            val root = JSONObject(json)
            if (root.optString("type") != "radar_report_multi") return null
            val arr = root.optJSONArray("ranking") ?: return null
            val ranking = mutableListOf<app.trakr.model.LiveRead>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                ranking += app.trakr.model.LiveRead(tag = o.optString("tag", ""), rssi = o.optInt("rssi", -100))
            }
            app.trakr.model.MultiRadarReport(ranking = ranking)
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

    /** Configurações do TRK-Finder: listen/radar/beep + PIN + RF calibration */
    data class TrackerConfig(
        val listenMs: Int,
        val radarMs: Int,
        val beep: Boolean,
        val hasPin: Boolean = false,
        val authed: Boolean = true,
        val authExpiresMs: Long = 0L,
        val txPowerDbm: Int = 26,
        val rssiOffset: Int = 0,
        val rssiThreshold: Int = -70,
        val envProfile: String = "default",
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
            hasPin = payload.optBoolean("has_pin", false),
            authed = payload.optBoolean("authed", !payload.optBoolean("has_pin", false)),
            authExpiresMs = payload.optLong("auth_expires_ms", 0L),
            txPowerDbm = payload.optInt("tx_power_dbm", 26),
            rssiOffset = payload.optInt("rssi_offset", 0),
            rssiThreshold = payload.optInt("rssi_threshold", -70),
            envProfile = payload.optString("env_profile", "default"),
        )
    }

    fun parseTrackerConfig(json: String): TrackerConfig? = parseCmdReply(json)?.let { trackerConfig(it) }
}
