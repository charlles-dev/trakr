package app.trakr.ui

import android.content.Context
import androidx.annotation.StringRes

/** Mensagem de status para a UI (Snackbar), resolvida pela tela com o contexto. */
data class UiMessage(
    @StringRes val res: Int,
    val args: List<Any> = emptyList(),
) {
    fun resolve(context: Context): String = context.getString(res, *args.toTypedArray())
}
