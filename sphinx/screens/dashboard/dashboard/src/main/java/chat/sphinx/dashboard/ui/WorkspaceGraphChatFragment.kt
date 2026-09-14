package chat.sphinx.dashboard.ui

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.FragmentWorkspaceGraphChatBinding
import chat.sphinx.dashboard.ui.adapter.GraphChatMessageAdapter
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.android_feature_screens.ui.sideeffect.SideEffectFragment
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.visible
import kotlin.math.abs
import kotlinx.coroutines.launch

@AndroidEntryPoint
internal class WorkspaceGraphChatFragment : SideEffectFragment<
        Context,
        ChatListSideEffect,
        WorkspaceGraphChatViewState,
        WorkspaceGraphChatViewModel,
        FragmentWorkspaceGraphChatBinding
        >(R.layout.fragment_workspace_graph_chat) {

    override val viewModel: WorkspaceGraphChatViewModel by viewModels()
    override val binding: FragmentWorkspaceGraphChatBinding by viewBinding(
        FragmentWorkspaceGraphChatBinding::bind
    )

    private var messageAdapter: GraphChatMessageAdapter? = null
    private var gestureStartX = 0f
    private var gestureStartY = 0f

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupComposer()
        subscribeToState()

        val workspaceId = arguments?.getString(ARG_WORKSPACE_ID).orEmpty()
        val workspaceSlug = arguments?.getString(ARG_WORKSPACE_SLUG)
        viewModel.load(workspaceId, workspaceSlug)
    }

    override fun onPause() {
        viewModel.cancelInFlight()
        super.onPause()
    }

    fun cancelInFlight() {
        viewModel.cancelInFlight()
    }

    private fun setupRecyclerView() {
        messageAdapter = GraphChatMessageAdapter()
        binding.recyclerViewGraphChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
            setHasFixedSize(false)
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    dispatchPagerDisallow(e)
                    return false
                }
            })
        }
    }

    private fun setupComposer() {
        binding.editTextGraphChatComposer.setOnTouchListener { _, event ->
            dispatchPagerDisallow(event)
            false
        }
        binding.layoutGraphChatComposer.setOnTouchListener { _, event ->
            dispatchPagerDisallow(event)
            false
        }
        binding.buttonGraphChatSend.setOnClickListener { submitComposer() }
        binding.editTextGraphChatComposer.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitComposer()
                true
            } else {
                false
            }
        }
        binding.buttonGraphChatRetry.setOnClickListener {
            viewModel.dismissError()
            viewModel.retry()
        }
    }

    private fun submitComposer() {
        val text = binding.editTextGraphChatComposer.text?.toString().orEmpty()
        viewModel.send(text)
        if (viewModel.inFlight.value) {
            binding.editTextGraphChatComposer.text = null
        }
    }

    /**
     * Nested ViewPager2 is horizontal. Disallow the pager from intercepting while
     * the gesture is clearly vertical so the list and composer can own it.
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

    private fun subscribeToState() {
        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.messages.collect { messages ->
                messageAdapter?.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        binding.recyclerViewGraphChat.scrollToPosition(messages.lastIndex)
                    }
                }
                render()
            }
        }
        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.inFlight.collect { render() }
        }
        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.statusDetail.collect { render() }
        }
        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.error.collect { render() }
        }
    }

    private fun render() {
        val messages = viewModel.messages.value
        val inFlight = viewModel.inFlight.value
        val hasError = viewModel.error.value
        val statusDetail = viewModel.statusDetail.value

        if (messages.isEmpty() && !inFlight) {
            binding.textViewGraphChatEmpty.visible
            binding.recyclerViewGraphChat.gone
        } else {
            binding.textViewGraphChatEmpty.gone
            binding.recyclerViewGraphChat.visible
        }

        if (inFlight) {
            binding.layoutGraphChatStatus.visible
            binding.textViewGraphChatStatus.text = if (statusDetail.isNullOrBlank()) {
                getString(R.string.graph_chat_in_progress)
            } else {
                statusDetail
            }
        } else {
            binding.layoutGraphChatStatus.gone
        }

        if (hasError && !inFlight) {
            binding.layoutGraphChatError.visible
        } else {
            binding.layoutGraphChatError.gone
        }

        binding.editTextGraphChatComposer.isEnabled = !inFlight
        binding.buttonGraphChatSend.isEnabled = !inFlight
    }

    override suspend fun onViewStateFlowCollect(viewState: WorkspaceGraphChatViewState) {}

    override suspend fun onSideEffectCollect(sideEffect: ChatListSideEffect) {
        sideEffect.execute(binding.root.context)
    }

    companion object {
        const val ARG_WORKSPACE_ID = "arg_workspace_id"
        const val ARG_WORKSPACE_SLUG = "arg_workspace_slug"

        fun newInstance(
            workspaceId: String,
            workspaceSlug: String?,
        ): WorkspaceGraphChatFragment {
            return WorkspaceGraphChatFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORKSPACE_ID, workspaceId)
                    putString(ARG_WORKSPACE_SLUG, workspaceSlug)
                }
            }
        }
    }
}
