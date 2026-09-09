package chat.sphinx.dashboard.navigation

import androidx.annotation.IdRes
import androidx.navigation.NavController
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.ui.FeaturePlanStubFragmentArgs
import io.matthewnelson.android_feature_navigation.DefaultNavOptions
import io.matthewnelson.concept_navigation.NavigationRequest

class ToFeaturePlanScreen(
    private val featureId: String,
    private val featureTitle: String,
    @IdRes private val popUpToId: Int? = null,
    private val popUpToInclusive: Boolean = false,
): NavigationRequest<NavController>() {
    override fun navigate(controller: NavController) {
        controller.navigate(
            R.id.feature_plan_nav_graph,

            FeaturePlanStubFragmentArgs.Builder(featureId, featureTitle)
                .build()
                .toBundle(),

            DefaultNavOptions.defaultAnims.let { builder ->
                popUpToId?.let { id ->
                    builder.setPopUpTo(id, popUpToInclusive)
                }
                builder.build()
            }
        )
    }
}
