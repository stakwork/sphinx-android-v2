package chat.sphinx.dashboard.ui

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import chat.sphinx.concept_repository_dashboard.model.HivePoolStatus
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.FragmentWorkspacePodsBinding
import chat.sphinx.dashboard.ui.adapter.HivePodAdapter
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.android_feature_screens.ui.sideeffect.SideEffectFragment
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.visible
import kotlin.math.abs
import kotlinx.coroutines.launch

@AndroidEntryPoint
internal class WorkspacePodsFragment : SideEffectFragment<
        Context,
        ChatListSideEffect,
        WorkspacePodsViewState,
        WorkspacePodsViewModel,
        FragmentWorkspacePodsBinding
        >(R.layout.fragment_workspace_pods) {

    override val viewModel: WorkspacePodsViewModel by viewModels()
    override val binding: FragmentWorkspacePodsBinding by viewBinding(FragmentWorkspacePodsBinding::bind)

    private var hivePodAdapter: HivePodAdapter? = null
    private var gestureStartX = 0f
    private var gestureStartY = 0f

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        subscribeToPods()

        val workspaceId = arguments?.getString(ARG_WORKSPACE_ID).orEmpty()
        val workspaceSlug = arguments?.getString(ARG_WORKSPACE_SLUG)
        viewModel.load(workspaceId, workspaceSlug)
    }

    private fun setupRecyclerView() {
        hivePodAdapter = HivePodAdapter()

        binding.recyclerViewHivePods.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = hivePodAdapter
            setHasFixedSize(false)
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    dispatchPagerDisallow(e)
                    return false
                }
            })
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayoutHivePods.apply {
            setOnRefreshListener { viewModel.refresh() }
            setOnChildScrollUpCallback { _, _ ->
                binding.recyclerViewHivePods.canScrollVertically(-1)
            }
            setOnTouchListener { _, event ->
                dispatchPagerDisallow(event)
                false
            }
        }
    }

    /**
     * Nested ViewPager2 is horizontal; SwipeRefreshLayout is vertical. Disallow the
     * pager from intercepting while the gesture is clearly vertical so PTR can own it.
     */
    private fun dispatchPagerDisallow(event: MotionEvent) {
        val pagerParent = view?.parent ?: return
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStartX = event.x
                gestureStartY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - gestureStartX)
                val dy = abs(event.y - gestureStartY)
                if (dy > touchSlop && dy > dx) {
                    pagerParent.requestDisallowInterceptTouchEvent(true)
                } else if (dx > touchSlop && dx > dy) {
                    pagerParent.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pagerParent.requestDisallowInterceptTouchEvent(false)
            }
        }
    }

    private fun subscribeToPods() {
        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.loading.collect { isLoading ->
                if (isLoading && viewModel.pods.value.isEmpty()) {
                    binding.progressBarHivePods.visible
                } else {
                    binding.progressBarHivePods.gone
                }
                renderEmptyAndError()
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.refreshing.collect { isRefreshing ->
                binding.swipeRefreshLayoutHivePods.isRefreshing = isRefreshing
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.pods.collect { pods ->
                hivePodAdapter?.submitList(pods)
                renderEmptyAndError()
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.poolStatus.collect { status ->
                renderPoolStatus(status)
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.error.collect {
                renderEmptyAndError()
            }
        }

        binding.buttonHivePodsRetry.setOnClickListener {
            viewModel.dismissError()
            viewModel.refresh()
        }
    }

    private fun renderPoolStatus(status: HivePoolStatus?) {
        if (status == null) {
            binding.textViewHivePoolQueued.text = getString(R.string.hive_pods_pool_unavailable)
            binding.textViewHivePoolIdle.text = ""
        } else {
            binding.textViewHivePoolQueued.text = getString(
                R.string.hive_pods_pool_queued,
                status.queuedCount
            )
            binding.textViewHivePoolIdle.text = getString(
                R.string.hive_pods_pool_idle,
                status.unusedVms
            )
        }
    }

    private fun renderEmptyAndError() {
        val hasError = viewModel.error.value
        val isEmpty = viewModel.pods.value.isEmpty()
        val isLoading = viewModel.loading.value

        if (hasError && isEmpty && !isLoading) {
            binding.layoutHivePodsError.visible
            binding.textViewHivePodsEmpty.gone
            binding.recyclerViewHivePods.gone
        } else if (isEmpty && !isLoading) {
            binding.layoutHivePodsError.gone
            binding.textViewHivePodsEmpty.visible
            binding.recyclerViewHivePods.gone
        } else {
            binding.layoutHivePodsError.gone
            binding.textViewHivePodsEmpty.gone
            binding.recyclerViewHivePods.visible
        }
    }

    override suspend fun onViewStateFlowCollect(viewState: WorkspacePodsViewState) {}

    override suspend fun onSideEffectCollect(sideEffect: ChatListSideEffect) {
        sideEffect.execute(binding.root.context)
    }

    companion object {
        const val ARG_WORKSPACE_ID = "arg_workspace_id"
        const val ARG_WORKSPACE_SLUG = "arg_workspace_slug"

        fun newInstance(workspaceId: String, workspaceSlug: String?): WorkspacePodsFragment {
            return WorkspacePodsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORKSPACE_ID, workspaceId)
                    putString(ARG_WORKSPACE_SLUG, workspaceSlug)
                }
            }
        }
    }
}
