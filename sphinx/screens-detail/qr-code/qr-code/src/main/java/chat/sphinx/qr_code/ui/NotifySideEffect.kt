package chat.sphinx.qr_code.ui

import android.app.AlertDialog
import android.content.Context
import chat.sphinx.qr_code.R
import chat.sphinx.resources.SphinxToastUtils
import io.matthewnelson.android_feature_toast_utils.show
import io.matthewnelson.concept_views.sideeffect.SideEffect

internal open class NotifySideEffect(val value: String) : SideEffect<Context>() {
    override suspend fun execute(value: Context) {
        SphinxToastUtils().show(value, this.value)
    }

    class AlertConfirmDeleteInvite(
        private val callback: () -> Unit
    ) : NotifySideEffect("") { // Provide an empty string as a default value
        override suspend fun execute(value: Context) {
            val builder = AlertDialog.Builder(value, R.style.AlertDialogTheme)
            builder.setTitle(value.getString(R.string.cancel))
            builder.setMessage(value.getString(R.string.alert_confirm_delete_invite_message))
            builder.setNegativeButton(android.R.string.cancel) { _, _ -> }
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                callback()
            }
            builder.show()
        }
    }
}
