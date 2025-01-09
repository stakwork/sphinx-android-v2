package com.example.call_activity.dialog

import android.R
import android.app.Activity
import android.app.AlertDialog
import android.widget.ArrayAdapter
import com.example.call_activity.CallViewModel

fun Activity.showAudioProcessorSwitchDialog(callViewModel: CallViewModel) {
    var name = callViewModel.audioProcessorOptions?.capturePostProcessor?.getName()
    var enabled = if (callViewModel.enableAudioProcessor.value == true) "On" else "Off"
    val builder = with(AlertDialog.Builder(this)) {
        setTitle("AudioProcessor for mic: \n[$name] is $enabled")

        val arrayAdapter = ArrayAdapter<String>(this@showAudioProcessorSwitchDialog, R.layout.select_dialog_item)
        arrayAdapter.add("On")
        arrayAdapter.add("Off")
        setAdapter(arrayAdapter) { dialog, index ->
            when (index) {
                0 -> callViewModel.toggleEnhancedNs(true)
                1 -> callViewModel.toggleEnhancedNs(false)
            }
            dialog.dismiss()
        }
    }
    builder.show()
}