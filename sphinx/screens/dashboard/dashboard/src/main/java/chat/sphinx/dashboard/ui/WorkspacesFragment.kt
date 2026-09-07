package chat.sphinx.dashboard.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.FragmentWorkspacesBinding
import chat.sphinx.dashboard.ui.adapter.WorkspaceAdapter
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.android_feature_screens.ui.sideeffect.SideEffectFragment
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.visible
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
internal class WorkspacesFragment : SideEffectFragment<
        Context,
        ChatListSideEffect,
        WorkspacesViewState,
        WorkspacesViewModel,
        FragmentWorkspacesBinding
        >(R.layout.fragment_workspaces) {

    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var imageLoader: ImageLoader<ImageView>

    override val viewModel: WorkspacesViewModel by viewModels()
    override val binding: FragmentWorkspacesBinding by viewBinding(FragmentWorkspacesBinding::bind)

    private var workspaceAdapter: WorkspaceAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        subscribeToWorkspaces()
        viewModel.loadWorkspaces()
    }

    private fun setupRecyclerView() {
        workspaceAdapter = WorkspaceAdapter(
            imageLoader = imageLoader,
            onStopSupervisor = onStopSupervisor,
        )

        binding.recyclerViewWorkspaces.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = workspaceAdapter
            setHasFixedSize(false)
        }
    }

    private fun subscribeToWorkspaces() {
        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.loading.collect { isLoading ->
                if (isLoading) {
                    binding.progressBarWorkspaces.visible
                } else {
                    binding.progressBarWorkspaces.gone
                }
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.workspaces.collect { workspaces ->
                workspaceAdapter?.submitList(workspaces)
            }
        }
    }

    override suspend fun onViewStateFlowCollect(viewState: WorkspacesViewState) {
        // No motion states needed
    }

    override suspend fun onSideEffectCollect(sideEffect: ChatListSideEffect) {
        sideEffect.execute(binding.root.context)
    }
}
