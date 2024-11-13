package chat.sphinx.chat_common.ui.activity.call_activity.dialog

import android.R
import android.app.Activity
import android.app.AlertDialog
import android.widget.ArrayAdapter
import chat.sphinx.chat_common.ui.activity.call_activity.CallViewModel

fun Activity.showDebugMenuDialog(callViewModel: CallViewModel) {
    val builder = with(AlertDialog.Builder(this)) {
        setTitle("Debug Menu")

        val arrayAdapter = ArrayAdapter<String>(this@showDebugMenuDialog, R.layout.select_dialog_item)
        arrayAdapter.add("Simulate Migration")
        arrayAdapter.add("Reconnect to Room")
        setAdapter(arrayAdapter) { dialog, index ->
            when (index) {
                0 -> callViewModel.simulateMigration()
                1 -> callViewModel.reconnect()
            }
            dialog.dismiss()
        }
    }
    builder.show()
}