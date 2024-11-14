package chat.sphinx.chat_tribe.ui.viewstate

import androidx.constraintlayout.motion.widget.MotionLayout
import chat.sphinx.resources.R as R_common
import io.matthewnelson.android_concept_views.MotionLayoutViewState


sealed class WebViewLayoutScreenViewState: MotionLayoutViewState<WebViewLayoutScreenViewState>() {

    object Closed: WebViewLayoutScreenViewState() {
        override val startSetId: Int
            get() = R_common.id.motion_scene_tribe_app_open
        override val endSetId: Int?
            get() = R_common.id.motion_scene_tribe_app_closed

        override fun restoreMotionScene(motionLayout: MotionLayout) {}
    }

    object Open: WebViewLayoutScreenViewState() {
        override val startSetId: Int
            get() = R_common.id.motion_scene_tribe_app_closed
        override val endSetId: Int?
            get() = R_common.id.motion_scene_tribe_app_open

        override fun restoreMotionScene(motionLayout: MotionLayout) {
            motionLayout.setTransition(R_common.id.transition_tribe_app_closed_to_open)
            motionLayout.setProgress(1F, 1F)
        }
    }

}