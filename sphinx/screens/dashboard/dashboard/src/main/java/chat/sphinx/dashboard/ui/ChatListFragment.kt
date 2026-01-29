package chat.sphinx.dashboard.ui

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.cash.exhaustive.Exhaustive
import by.kirich1409.viewbindingdelegate.viewBinding
import chat.sphinx.concept_grapheneos_manager.GrapheneOsManager
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_user_colors_helper.UserColorsHelper
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.FragmentChatListBinding
import chat.sphinx.dashboard.ui.adapter.ChatListAdapter
import chat.sphinx.dashboard.ui.adapter.ChatListFooterAdapter
import chat.sphinx.dashboard.ui.adapter.DashboardFooterAdapter
import chat.sphinx.dashboard.ui.viewstates.ChatFilter
import chat.sphinx.dashboard.ui.viewstates.ChatListFooterButtonsViewState
import chat.sphinx.dashboard.ui.viewstates.ChatListViewState
import chat.sphinx.resources.SphinxToastUtils
import chat.sphinx.resources.inputMethodManager
import chat.sphinx.wrapper_chat.ChatType
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.android_feature_screens.navigation.CloseAppOnBackPress
import io.matthewnelson.android_feature_screens.ui.sideeffect.SideEffectFragment
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.goneIfFalse
import io.matthewnelson.android_feature_screens.util.visible
import io.matthewnelson.concept_views.viewstate.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Suppress("NOTHING_TO_INLINE")
private inline fun FragmentChatListBinding.searchBarClearFocus() {
    layoutSearchBar.editTextDashboardSearch.clearFocus()
}

@AndroidEntryPoint
internal class ChatListFragment : SideEffectFragment<
        Context,
        ChatListSideEffect,
        ChatListViewState,
        ChatListViewModel,
        FragmentChatListBinding
        >(R.layout.fragment_chat_list)
{
    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var imageLoader: ImageLoader<ImageView>

    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var userColorsHelper: UserColorsHelper

    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var grapheneOsManager: GrapheneOsManager

    override val viewModel: ChatListViewModel by viewModels()
    override val binding: FragmentChatListBinding by viewBinding(FragmentChatListBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backPressHandler = BackPressHandler(binding.root.context)

        view.post {
            setupSearch()
            setupChats()
            grapheneOsManager.optimizeViewContainer(this)
        }
    }

    override fun onResume() {
        super.onResume()

        backPressHandler?.enableDoubleTapToClose(viewLifecycleOwner, SphinxToastUtils())
            ?.addCallback(viewLifecycleOwner, requireActivity())
    }

    private var backPressHandler: BackPressHandler? = null
    private inner class BackPressHandler(context: Context): CloseAppOnBackPress(context) {
        override fun handleOnBackPressed() {
            if (
                (parentFragment as? DashboardFragment)?.closeDrawerIfOpen() == true
            ) {
                return
            } else {
                binding.searchBarClearFocus()
                super.handleOnBackPressed()
            }
        }
    }

    private fun setupChats() {
        binding.layoutChatListChats.recyclerViewChats.apply {

            val linearLayoutManager = LinearLayoutManager(context)
            val chatListAdapter = ChatListAdapter(
                this,
                linearLayoutManager,
                imageLoader,
                viewLifecycleOwner,
                onStopSupervisor,
                viewModel,
                userColorsHelper
            )

            val chatListFooterAdapter = ChatListFooterAdapter(viewLifecycleOwner, onStopSupervisor, viewModel)
            val footerSpaceAdapter = DashboardFooterAdapter()

            layoutManager = linearLayoutManager
            adapter = ConcatAdapter(
                chatListAdapter,
                chatListFooterAdapter,
                footerSpaceAdapter
            )
            this.setHasFixedSize(false)
            itemAnimator = null

            addOnScrollListener(OptimizedScrollListener())
        }
    }

    private inner class OptimizedScrollListener : RecyclerView.OnScrollListener() {
        private var lastScrollTime = 0L

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            // ✅ Throttle scroll calculations
            val now = System.currentTimeMillis()
            if (now - lastScrollTime < 16) return // Skip if less than one frame
            lastScrollTime = now

            // ✅ Cache calculations
            val parentDashboard = parentFragment as? DashboardFragment ?: return

            val bottomOfScroll = !recyclerView.canScrollVertically(1)
            val topOfScroll = !recyclerView.canScrollVertically(-1)
            val scrollNotAvailable = bottomOfScroll && topOfScroll

            parentDashboard.shouldToggleNavBar((dy <= 0 && !bottomOfScroll) || scrollNotAvailable)
        }
    }

    private fun setupSearch() {
        binding.layoutSearchBar.apply {
            var searchJob: Job? = null

            editTextDashboardSearch.addTextChangedListener { editable ->
                buttonDashboardSearchClear.goneIfFalse(editable.toString().isNotEmpty())

                searchJob?.cancel()
                searchJob = onStopSupervisor.scope.launch(viewModel.default) {
                    delay(300)

                    val filter = if (editable.toString().isNotEmpty()) {
                        ChatFilter.FilterBy(editable.toString())
                    } else {
                        ChatFilter.ClearFilter
                    }

                    viewModel.updateChatListFilter(filter)
                }
            }

            includeLayoutButtonAddTribe.root.setOnClickListener {
                onStopSupervisor.scope.launch(viewModel.mainImmediate) {
                    viewModel.toTribesDiscover()
                }
            }

            editTextDashboardSearch.setOnEditorActionListener(object: TextView.OnEditorActionListener {
                override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
                    if (actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                        editTextDashboardSearch.let { editText ->
                            binding.root.context.inputMethodManager?.let { imm ->
                                if (imm.isActive(editText)) {
                                    imm.hideSoftInputFromWindow(editText.windowToken, 0)
                                    editText.clearFocus()
                                }
                            }
                        }
                        return true
                    }
                    return false
                }
            })

            buttonDashboardSearchClear.setOnClickListener {
                editTextDashboardSearch.setText("")
            }
        }
    }

    override fun onPause() {
        super.onPause()

        binding.searchBarClearFocus()
    }

    override suspend fun onSideEffectCollect(sideEffect: ChatListSideEffect) {
        sideEffect.execute(binding.root.context)
    }

    companion object {
        fun newInstance(
            updateBackgroundLoginTime: Boolean = false,
            chatListType: ChatType = ChatType.Conversation,
            deepLink: String? = null,
        ): ChatListFragment {
            return ChatListFragment().apply {
                val args = ChatListFragmentArgs.Builder(updateBackgroundLoginTime, chatListType.value)
                args.argDeepLink = deepLink

                arguments = args.build().toBundle()
            }
        }
    }

    override suspend fun onViewStateFlowCollect(viewState: ChatListViewState) {
        viewModel.chatListFooterButtonsViewStateContainer.collect { viewState ->
            binding.layoutSearchBar.includeLayoutButtonAddTribe.apply {
                @Exhaustive
                when (viewState) {
                    is ChatListFooterButtonsViewState.Idle -> {
                        root.gone
                    }
                    is ChatListFooterButtonsViewState.ButtonsVisibility -> {
                        root.goneIfFalse(viewState.discoverTribesVisible)
                    }
                }
            }
        }
    }

    override fun subscribeToViewStateFlow() {
        onStopSupervisor.scope.launch(viewModel.default) {
            // Use viewStateFlow property directly instead of wrapping in flow { }
            viewModel.chatViewStateContainer.viewStateFlow
                .combine(viewModel.hasSingleContact) { chatViewState, isSingleContact ->
                    Pair(chatViewState, isSingleContact)
                }.collect { (chatViewState, isSingleContact) ->
                    withContext(viewModel.mainImmediate) {
                        when {
                            chatViewState.list.isEmpty() -> {
                                if (chatViewState.showProgressBar) {
                                    binding.progressBarChatList.visible
                                } else {
                                    binding.progressBarChatList.gone
                                }

                                if (isSingleContact == true) {
                                    binding.progressBarChatList.gone
                                }
                            }

                            else -> {
                                binding.progressBarChatList.gone
                                binding.welcomeToSphinx.gone
                            }
                        }
                    }
                }
        }

        super.subscribeToViewStateFlow()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        backPressHandler = null
    }
}
