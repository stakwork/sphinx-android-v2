package chat.sphinx.dashboard.navigation

import androidx.annotation.IdRes
import androidx.navigation.NavController
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.ui.WorkspaceDetailFragmentArgs
import io.matthewnelson.android_feature_navigation.DefaultNavOptions
import io.matthewnelson.concept_navigation.NavigationRequest

class ToWorkspaceDetailScreen(
    private val workspaceId: String,
    private val workspaceName: String,
    private val workspaceSlug: String? = null,
    @IdRes private val popUpToId: Int? = null,
    private val popUpToInclusive: Boolean = false,
): NavigationRequest<NavController>() {
    override fun navigate(controller: NavController) {
        controller.navigate(
            R.id.workspace_detail_nav_graph,

            WorkspaceDetailFragmentArgs.Builder(workspaceId, workspaceName)
                .setArgWorkspaceSlug(workspaceSlug)
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
