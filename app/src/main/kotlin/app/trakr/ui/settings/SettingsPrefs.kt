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
}
