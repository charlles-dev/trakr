package app.trakr.model

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Maleta/perfil ativo no app. O firmware é a fonte da verdade; esta seleção
 * diz apenas qual inventory_<id>.json o app está "olhando" agora.
 */
object ToolboxStore {

    private const val PREFS = "trakr_prefs"
    private const val KEY_ID = "active_toolbox_id"
    private const val KEY_NAME = "active_toolbox_name"

    @Immutable
    data class Selection(val id: String, val name: String)

    private lateinit var prefs: SharedPreferences

    private val _current = MutableStateFlow(Selection(id = MAIN_TOOLBOX_ID, name = "Trakr"))
    val current: StateFlow<Selection> = _current.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _current.value = Selection(
            id = prefs.getString(KEY_ID, MAIN_TOOLBOX_ID) ?: MAIN_TOOLBOX_ID,
            name = prefs.getString(KEY_NAME, "Trakr") ?: "Trakr",
        )
    }

    fun select(selection: Selection) {
        _current.value = selection
        prefs.edit()
            .putString(KEY_ID, selection.id)
            .putString(KEY_NAME, selection.name)
            .apply()
    }
}