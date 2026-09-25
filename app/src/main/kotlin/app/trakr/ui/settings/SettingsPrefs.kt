package app.trakr.ui.settings

import android.content.Context

/** Preferências de configuração do Trakr (SharedPreferences). */
object SettingsPrefs {
    private const val PREFS_NAME = "trakr_prefs"
    private const val KEY_ABSENCE_ALERTS = "absence_alerts_enabled"

    /** Alertas de ausência: notificação quando uma tag some do radar. */
    fun absenceAlertsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ABSENCE_ALERTS, true)

    fun setAbsenceAlertsEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ABSENCE_ALERTS, enabled)
            .apply()
    }

    fun getTrackerMac(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("tracker_mac", null)

    fun setTrackerMac(context: Context, mac: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("tracker_mac", mac)
            .apply()
    }

    fun getTrackerPin(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("tracker_pin", null)

    fun setTrackerPin(context: Context, pin: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("tracker_pin", pin)
            .apply()
    }
}
