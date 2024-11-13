package chat.sphinx.chat_common.ui.activity.call_activity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class StressTest : Parcelable {

    @Parcelize
    data class SwitchRoom(
        val firstToken: String,
        val secondToken: String,
    ) : StressTest()

    @Parcelize
    object None : StressTest()
}