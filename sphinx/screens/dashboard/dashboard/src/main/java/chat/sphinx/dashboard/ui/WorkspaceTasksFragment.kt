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
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.FragmentWorkspaceTasksBinding
import chat.sphinx.dashboard.ui.adapter.HiveTaskAdapter
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.android_feature_screens.ui.sideeffect.SideEffectFragment
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.visible
import kotlin.math.abs
import kotlinx.coroutines.launch

@AndroidEntryPoint
internal class WorkspaceTasksFragment : SideEffectFragment<
        Context,
        ChatListSideEffect,
        WorkspaceTasksViewState,
        WorkspaceTasksViewModel,
        FragmentWorkspaceTasksBinding
        >(R.layout.fragment_workspace_tasks) {

    override val viewModel: WorkspaceTasksViewModel by viewModels()
    override val binding: FragmentWorkspaceTasksBinding by viewBinding(FragmentWorkspaceTasksBinding::bind)

    private var hiveTaskAdapter: HiveTaskAdapter? = null
    private var gestureStartX = 0f
    private var gestureStartY = 0f

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        setupSegments()
        subscribeToTasks()

        val workspaceId = arguments?.getString(ARG_WORKSPACE_ID).orEmpty()
        if (workspaceId.isNotEmpty()) {
            viewModel.load(workspaceId)
        }
    }

    private fun setupRecyclerView() {
        hiveTaskAdapter = HiveTaskAdapter(
            onStartClicked = { task -> viewModel.onStartClicked(task) },
            onRetryClicked = { task -> viewModel.onRetryClicked(task) },
            onMarkCompleteClicked = { task -> viewModel.onMarkCompleteClicked(task) },
            onArchiveClicked = { task -> viewModel.onArchiveClicked(task) },
            onUnarchiveClicked = { task -> viewModel.onUnarchiveClicked(task) },
            onDuplicateClicked = { task -> viewModel.onDuplicateClicked(task) },
            onFlagsChanged = { task, autoMerge, runBuild, runTestSuite ->
                viewModel.onFlagsChanged(task, autoMerge, runBuild, runTestSuite)
            },
        )

        binding.recyclerViewHiveTasks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = hiveTaskAdapter
            setHasFixedSize(false)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val lastVisible = manager.findLastVisibleItemPosition()
                    val total = manager.itemCount
                    if (lastVisible >= total - 3) {
                        viewModel.loadNextPage()
                    }
                }
            })
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    dispatchPagerDisallow(e)
                    return false
                }
            })
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayoutHiveTasks.apply {
            setOnRefreshListener { viewModel.refresh() }
            setOnChildScrollUpCallback { _, _ ->
                binding.recyclerViewHiveTasks.canScrollVertically(-1)
            }
            setOnTouchListener { _, event ->
                dispatchPagerDisallow(event)
                false
            }
        }
    }

    private fun setupSegments() {
        binding.buttonHiveTasksSegmentActive.setOnClickListener {
            viewModel.setArchivedSegment(false)
        }
        binding.buttonHiveTasksSegmentArchived.setOnClickListener {
            viewModel.setArchivedSegment(true)
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

    private fun subscribeToTasks() {
        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.loading.collect { isLoading ->
                if (isLoading && viewModel.tasks.value.isEmpty()) {
                    binding.progressBarHiveTasks.visible
                } else {
                    binding.progressBarHiveTasks.gone
                }
                renderEmptyAndError()
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.refreshing.collect { isRefreshing ->
                binding.swipeRefreshLayoutHiveTasks.isRefreshing = isRefreshing
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.tasks.collect { tasks ->
                hiveTaskAdapter?.submitList(tasks)
                renderEmptyAndError()
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.error.collect {
                renderEmptyAndError()
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.archivedSegment.collect { archived ->
                hiveTaskAdapter?.archivedSegment = archived
                renderSegmentButtons(archived)
            }
        }

        binding.buttonHiveTasksRetry.setOnClickListener {
            viewModel.dismissError()
            viewModel.refresh()
        }
    }

    private fun renderSegmentButtons(archived: Boolean) {
        binding.buttonHiveTasksSegmentActive.isSelected = !archived
        binding.buttonHiveTasksSegmentArchived.isSelected = archived
    }

    private fun renderEmptyAndError() {
        val hasError = viewModel.error.value
        val isEmpty = viewModel.tasks.value.isEmpty()
        val isLoading = viewModel.loading.value

        if (hasError && isEmpty && !isLoading) {
            binding.layoutHiveTasksError.visible
            binding.textViewHiveTasksEmpty.gone
        } else if (isEmpty && !isLoading) {
            binding.layoutHiveTasksError.gone
            binding.textViewHiveTasksEmpty.visible
        } else {
            binding.layoutHiveTasksError.gone
            binding.textViewHiveTasksEmpty.gone
        }
    }

    override suspend fun onViewStateFlowCollect(viewState: WorkspaceTasksViewState) {}

    override suspend fun onSideEffectCollect(sideEffect: ChatListSideEffect) {
        sideEffect.execute(binding.root.context)
    }

    companion object {
        const val ARG_WORKSPACE_ID = "arg_workspace_id"

        fun newInstance(workspaceId: String): WorkspaceTasksFragment {
            return WorkspaceTasksFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORKSPACE_ID, workspaceId)
                }
            }
        }
    }
}
