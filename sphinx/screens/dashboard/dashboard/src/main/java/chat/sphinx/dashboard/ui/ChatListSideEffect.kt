package chat.sphinx.dashboard.ui

import android.app.AlertDialog
import android.content.Context
import chat.sphinx.dashboard.R
import chat.sphinx.resources.SphinxToastUtils
import chat.sphinx.resources.R as R_common
import io.matthewnelson.android_feature_toast_utils.show
import io.matthewnelson.concept_views.sideeffect.SideEffect
import java.util.Locale

sealed class ChatListSideEffect: SideEffect<Context>() {

    class Notify(
        private val msg: String,
        private val notificationLengthLong: Boolean = true
    ): ChatListSideEffect() {
        override suspend fun execute(value: Context) {
            SphinxToastUtils(toastLengthLong = notificationLengthLong).show(value, msg)
        }
    }

    class NotifyError(
        private val msg: String,
        private val notificationLengthLong: Boolean = true
    ): ChatListSideEffect() {
        override suspend fun execute(value: Context) {
            SphinxToastUtils(toastLengthLong = notificationLengthLong, toastBackgroundTint = chat.sphinx.resources.R.color.badgeRed).show(value, msg)
        }
    }

    class AlertConfirmPayInvite(
        private val amount: Long,
        private val callback: () -> Unit
    ): ChatListSideEffect() {
        override suspend fun execute(value: Context) {
            val successMessage = value.getString(R.string.alert_confirm_pay_invite_message, amount)

            val builder = AlertDialog.Builder(value, R_common.style.AlertDialogTheme)
            builder.setTitle(value.getString(R.string.alert_confirm_pay_invite_title))
            builder.setMessage(successMessage)
            builder.setNegativeButton(android.R.string.cancel) { _,_ -> }
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                callback()
            }
            builder.show()
        }
    }

    class AlertConfirmPayLightningPaymentRequest(
        private val amount: Long,
        private val memo: String = "",
        private val callback: () -> Unit
    ): ChatListSideEffect() {
        override suspend fun execute(value: Context) {
            val memo = if (memo.isEmpty()) "-" else memo
            val successMessage = value.getString(R.string.alert_confirm_pay_invoice_message, amount, memo)

            val builder = AlertDialog.Builder(value, R_common.style.AlertDialogTheme)
            builder.setTitle(value.getString(R.string.alert_confirm_pay_invoice_title))
            builder.setMessage(successMessage)
            builder.setNegativeButton(android.R.string.cancel) { _,_ -> }
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                callback()
            }
            builder.show()
        }
    }

    class AlertConfirmDeleteInvite(
        private val callback: () -> Unit
    ): ChatListSideEffect() {
        override suspend fun execute(value: Context) {
            val builder = AlertDialog.Builder(value, R_common.style.AlertDialogTheme)
            builder.setTitle(value.getString(R.string.alert_confirm_delete_invite_title))
            builder.setMessage(value.getString(R.string.alert_confirm_delete_invite_message))
            builder.setNegativeButton(android.R.string.cancel) { _,_ -> }
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                callback()
            }
            builder.show()
        }
    }

    class AlertConfirmDeleteFeature(
        private val onConfirm: () -> Unit,
        private val onDismiss: () -> Unit = {}
    ): ChatListSideEffect() {
        override suspend fun execute(value: Context) {
            val builder = AlertDialog.Builder(value, R_common.style.AlertDialogTheme)
            builder.setTitle(value.getString(R.string.alert_confirm_delete_feature_title))
            builder.setMessage(value.getString(R.string.alert_confirm_delete_feature_message))
            builder.setNegativeButton(android.R.string.cancel) { _, _ ->
                onDismiss()
            }
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                onConfirm()
            }
            builder.show()
        }
    }

    class AlertEditHiveFeature(
        private val currentStatus: String?,
        private val currentPriority: String?,
        private val onConfirm: (status: String?, priority: String?) -> Unit,
        private val onDismiss: () -> Unit = {}
    ): ChatListSideEffect() {
        override suspend fun execute(value: Context) {
            val statuses = value.resources.getStringArray(R.array.hive_feature_status_values)
            val statusIndex = statuses.indexOf(currentStatus).let { if (it >= 0) it else 0 }
            var selectedStatus = statuses.getOrElse(statusIndex) { statuses.first() }

            val statusBuilder = AlertDialog.Builder(value, R_common.style.AlertDialogTheme)
            statusBuilder.setTitle(value.getString(R.string.hive_feature_edit_status_title))
            statusBuilder.setSingleChoiceItems(statuses, statusIndex) { _, which ->
                selectedStatus = statuses[which]
            }
            statusBuilder.setNegativeButton(android.R.string.cancel) { _, _ ->
                onDismiss()
            }
            statusBuilder.setPositiveButton(android.R.string.ok) { _, _ ->
                showPriorityDialog(value, selectedStatus)
            }
            statusBuilder.show()
        }

        private fun showPriorityDialog(value: Context, selectedStatus: String) {
            val priorities = value.resources.getStringArray(R.array.hive_feature_priority_values)
            val priorityIndex = priorities.indexOf(currentPriority).let { if (it >= 0) it else 0 }
            var selectedPriority = priorities.getOrElse(priorityIndex) { priorities.first() }

            val priorityBuilder = AlertDialog.Builder(value, R_common.style.AlertDialogTheme)
            priorityBuilder.setTitle(value.getString(R.string.hive_feature_edit_priority_title))
            priorityBuilder.setSingleChoiceItems(priorities, priorityIndex) { _, which ->
                selectedPriority = priorities[which]
            }
            priorityBuilder.setNegativeButton(android.R.string.cancel) { _, _ ->
                onDismiss()
            }
            priorityBuilder.setPositiveButton(android.R.string.ok) { _, _ ->
                val status = selectedStatus.takeIf { it != currentStatus }
                val priority = selectedPriority.takeIf { it != currentPriority }
                if (status == null && priority == null) {
                    onDismiss()
                } else {
                    onConfirm(status, priority)
                }
            }
            priorityBuilder.show()
        }
    }

    class AlertRetryFetchWorkspaces(
        private val onRetry: () -> Unit,
        private val onDismiss: () -> Unit = {}
    ): ChatListSideEffect() {
        override suspend fun execute(value: Context) {
            val builder = AlertDialog.Builder(value, R_common.style.AlertDialogTheme)
            builder.setTitle(value.getString(R.string.alert_workspaces_load_failed_title))
            builder.setMessage(value.getString(R.string.alert_workspaces_load_failed_message))
            builder.setNegativeButton(value.getString(R.string.alert_workspaces_dismiss)) { _, _ ->
                onDismiss()
            }
            builder.setPositiveButton(value.getString(R.string.alert_workspaces_retry)) { _, _ ->
                onRetry()
            }
            builder.setCancelable(false)
            builder.show()
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun String.toCapitalized(): String {
        return this.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(
                Locale.ROOT
            ) else it.toString()
        }
    }

}