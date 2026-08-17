@file:Suppress("ktlint:standard:max-line-length", "MaxLineLength")

package app.trakr.ui

import android.content.Context
import androidx.annotation.StringRes

/** Mensagem de status para a UI (Snackbar), resolvida pela tela com o contexto. */
sealed class UiMessage {
    abstract fun resolve(context: Context): String

    data class Resource(
        @StringRes val res: Int,
        val args: List<Any> = emptyList(),
    ) : UiMessage() {
        override fun resolve(context: Context): String = context.getString(res, *args.toTypedArray())
    }

    data class Text(
        val text: String,
    ) : UiMessage() {
        override fun resolve(context: Context): String = text
    }

    companion object {
        operator fun invoke(
            @StringRes res: Int,
            vararg args: Any,
        ): UiMessage = Resource(res, args.toList())

        operator fun invoke(text: String): UiMessage = Text(text)
    }
}
