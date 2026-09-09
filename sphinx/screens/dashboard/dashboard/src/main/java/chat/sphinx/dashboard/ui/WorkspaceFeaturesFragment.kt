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
import chat.sphinx.dashboard.databinding.FragmentWorkspaceFeaturesBinding
import chat.sphinx.dashboard.ui.adapter.HiveFeatureAdapter
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.android_feature_screens.ui.sideeffect.SideEffectFragment
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.visible
import kotlin.math.abs
import kotlinx.coroutines.launch

@AndroidEntryPoint
internal class WorkspaceFeaturesFragment : SideEffectFragment<
        Context,
        ChatListSideEffect,
        WorkspaceFeaturesViewState,
        WorkspaceFeaturesViewModel,
        FragmentWorkspaceFeaturesBinding
        >(R.layout.fragment_workspace_features) {

    override val viewModel: WorkspaceFeaturesViewModel by viewModels()
    override val binding: FragmentWorkspaceFeaturesBinding by viewBinding(FragmentWorkspaceFeaturesBinding::bind)

    private var hiveFeatureAdapter: HiveFeatureAdapter? = null
    private var gestureStartX = 0f
    private var gestureStartY = 0f

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        subscribeToFeatures()

        val workspaceId = arguments?.getString(ARG_WORKSPACE_ID).orEmpty()
        if (workspaceId.isNotEmpty()) {
            viewModel.load(workspaceId)
        }
    }

    private fun setupRecyclerView() {
        hiveFeatureAdapter = HiveFeatureAdapter(
            onFeatureClicked = { feature -> viewModel.onFeatureClicked(feature) },
            onEditClicked = { feature -> viewModel.onEditClicked(feature) },
            onDeleteClicked = { feature -> viewModel.onDeleteClicked(feature) },
        )

        binding.recyclerViewHiveFeatures.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = hiveFeatureAdapter
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
        binding.swipeRefreshLayoutHiveFeatures.apply {
            setOnRefreshListener { viewModel.refresh() }
            setOnChildScrollUpCallback { _, _ ->
                binding.recyclerViewHiveFeatures.canScrollVertically(-1)
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

    private fun subscribeToFeatures() {
        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.loading.collect { isLoading ->
                if (isLoading && viewModel.features.value.isEmpty()) {
                    binding.progressBarHiveFeatures.visible
                } else {
                    binding.progressBarHiveFeatures.gone
                }
                renderEmptyAndError()
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.refreshing.collect { isRefreshing ->
                binding.swipeRefreshLayoutHiveFeatures.isRefreshing = isRefreshing
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.features.collect { features ->
                hiveFeatureAdapter?.submitList(features)
                renderEmptyAndError()
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.error.collect {
                renderEmptyAndError()
            }
        }

        binding.buttonHiveFeaturesRetry.setOnClickListener {
            viewModel.dismissError()
            viewModel.refresh()
        }
    }

    private fun renderEmptyAndError() {
        val hasError = viewModel.error.value
        val isEmpty = viewModel.features.value.isEmpty()
        val isLoading = viewModel.loading.value

        if (hasError && isEmpty && !isLoading) {
            binding.layoutHiveFeaturesError.visible
            binding.textViewHiveFeaturesEmpty.gone
        } else if (isEmpty && !isLoading) {
            binding.layoutHiveFeaturesError.gone
            binding.textViewHiveFeaturesEmpty.visible
        } else {
            binding.layoutHiveFeaturesError.gone
            binding.textViewHiveFeaturesEmpty.gone
        }
    }

    override suspend fun onViewStateFlowCollect(viewState: WorkspaceFeaturesViewState) {}

    override suspend fun onSideEffectCollect(sideEffect: ChatListSideEffect) {
        sideEffect.execute(binding.root.context)
    }

    companion object {
        const val ARG_WORKSPACE_ID = "arg_workspace_id"

        fun newInstance(workspaceId: String): WorkspaceFeaturesFragment {
            return WorkspaceFeaturesFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORKSPACE_ID, workspaceId)
                }
            }
        }
    }
}
