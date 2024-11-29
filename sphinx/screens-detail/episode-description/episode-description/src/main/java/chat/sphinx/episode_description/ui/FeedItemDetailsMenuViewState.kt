package chat.sphinx.episode_description.ui

import androidx.constraintlayout.motion.widget.MotionLayout
import chat.sphinx.create_description.R
import chat.sphinx.resources.R as R_common
import io.matthewnelson.android_concept_views.MotionLayoutViewState


sealed class FeedItemDetailsMenuViewState: MotionLayoutViewState<FeedItemDetailsMenuViewState>() {

    object Closed: FeedItemDetailsMenuViewState() {
        override val startSetId: Int
            get() = R_common.id.motion_scene_feed_item_details_open
        override val endSetId: Int
            get() = R_common.id.motion_scene_feed_item_details_closed

        override fun restoreMotionScene(motionLayout: MotionLayout) {}
    }

    object Open : FeedItemDetailsMenuViewState() {
        override val startSetId: Int
            get() = R_common.id.motion_scene_feed_item_details_closed
        override val endSetId: Int
            get() = R_common.id.motion_scene_feed_item_details_open

        override fun restoreMotionScene(motionLayout: MotionLayout) {
            motionLayout.setTransition(R_common.id.transition_feed_item_details_closed_to_open)
            motionLayout.setProgress(1F, 1F)
        }
    }
}