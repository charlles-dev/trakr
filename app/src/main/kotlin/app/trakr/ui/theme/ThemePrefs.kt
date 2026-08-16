package app.trakr.ui.theme

import android.content.Context

/** Preferências de interface do Trakr (SharedPreferences). */
object ThemePrefs {
    private const val PREFS_NAME = "trakr_prefs"
    private const val KEY_DARK_THEME = "dark_theme"

    fun isDark(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_THEME, true)

    fun setDark(
        context: Context,
        dark: Boolean,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_THEME, dark)
            .apply()
    }
}
