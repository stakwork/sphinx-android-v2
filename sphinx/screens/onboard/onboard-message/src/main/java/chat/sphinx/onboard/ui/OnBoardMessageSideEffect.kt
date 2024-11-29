package chat.sphinx.onboard.ui

import android.app.AlertDialog
import android.content.Context
import chat.sphinx.onboard.R
import chat.sphinx.wrapper_relay.RelayUrl
import chat.sphinx.resources.R as R_common
import io.matthewnelson.concept_views.sideeffect.SideEffect

internal sealed class OnBoardMessageSideEffect: SideEffect<Context>() {

    class RelayUrlHttpConfirmation(
        private val relayUrl: RelayUrl,
        private val callback: (RelayUrl?) -> Unit,
    ): OnBoardMessageSideEffect() {

        override suspend fun execute(value: Context) {
            val builder = AlertDialog.Builder(value, R_common.style.AlertDialogTheme)
            builder.setTitle(relayUrl.value)
            builder.setMessage(value.getString(R_common.string.relay_url_http_message))
            builder.setPositiveButton(R_common.string.relay_url_http_positive_change_to_https) { _, _ ->
                callback.invoke(RelayUrl(relayUrl.value.replaceFirst("http://", "https://")))
            }
            builder.setNegativeButton(R_common.string.relay_url_http_negative_keep_http) { _, _ ->
                callback.invoke(relayUrl)
            }
            builder.setOnCancelListener {
                callback.invoke(null)
            }

            builder.show()
        }
    }
}
