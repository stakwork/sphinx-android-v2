package chat.sphinx.threads.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import chat.sphinx.chat_common.ui.viewstate.messageholder.LayoutState
import chat.sphinx.highlighting_tool.SphinxUrlSpan
import chat.sphinx.concept_image_loader.Disposable
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_user_colors_helper.UserColorsHelper
import chat.sphinx.threads.databinding.ThreadsListItemHolderBinding
import chat.sphinx.threads.model.ThreadItemViewState
import chat.sphinx.threads.ui.ThreadsViewModel
import chat.sphinx.threads.viewstate.ThreadsViewState
import io.matthewnelson.android_feature_viewmodel.util.OnStopSupervisor
import io.matthewnelson.concept_views.viewstate.collect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ThreadsAdapter(
    private val recyclerView: RecyclerView,
    private val layoutManager: LinearLayoutManager,
    private val imageLoader: ImageLoader<ImageView>,
    private val lifecycleOwner: LifecycleOwner,
    private val onStopSupervisor: OnStopSupervisor,
    private val viewModel: ThreadsViewModel,
    private val userColorsHelper: UserColorsHelper,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
    DefaultLifecycleObserver,
    View.OnLayoutChangeListener
{

    private val diffCallback = object : DiffUtil.ItemCallback<ThreadItemViewState>() {

        override fun areItemsTheSame(oldItem: ThreadItemViewState, newItem: ThreadItemViewState): Boolean {
            return oldItem.uuid == newItem.uuid
        }

        override fun areContentsTheSame(oldItem: ThreadItemViewState, newItem: ThreadItemViewState): Boolean {
            return  oldItem.message  == newItem.message &&
                    oldItem.uuid == newItem.uuid &&
                    oldItem.usersCount == newItem.usersCount &&
                    oldItem.repliesAmount == newItem.repliesAmount &&
                    oldItem.lastReplyDate == newItem.lastReplyDate
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.viewStateContainer.collect { viewState ->
                (viewState as? ThreadsViewState.ThreadList)?.let {
                    if (differ.currentList.isEmpty()) {
                        differ.submitList(viewState.threads) {
                            forceScrollToTop()
                        }
                    } else {
                        scrollToPreviousPositionWithCallback(viewState.threads.size) {
                            differ.submitList(viewState.threads)
                        }
                    }
                }
            }
        }
    }

    private suspend fun scrollToPreviousPositionWithCallback(
        newListSize: Int,
        callback: (() -> Unit)? = null,
    ) {
        val firstItemBeforeUpdate = differ.currentList.first()
        val lastVisibleItemPositionBeforeDispatch = layoutManager.findLastVisibleItemPosition()
        val listSizeBeforeDispatch = differ.currentList.size
        val diffToBottom = listSizeBeforeDispatch - lastVisibleItemPositionBeforeDispatch

        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val currentFirstVisible = layoutManager.findFirstVisibleItemPosition()
        val currentFirstView = layoutManager.findViewByPosition(currentFirstVisible)
        val currentOffset = currentFirstView?.top ?: 0
        val currentListSize = itemCount

        if (callback != null) {
            callback()
        }

        val firstItemAfterUpdate = differ.currentList.first()
        val isLoadingMore = !diffCallback.areItemsTheSame(firstItemBeforeUpdate, firstItemAfterUpdate)
        val newItemsAdded = newListSize - currentListSize
        val newTargetPosition = currentFirstVisible + (if (isLoadingMore) newItemsAdded else 0)


        if (diffToBottom <= 1) {
            delay(250L)

            recyclerView.post {
                recyclerView.smoothScrollToPosition(
                    newListSize - 1
                )
            }
        } else {
            if (!isLoadingMore) {
                return
            }
            recyclerView.post {
                (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                    newTargetPosition,
                    currentOffset
                )
            }
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadsAdapter.ThreadsListViewHolder {
        val binding = ThreadsListItemHolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ThreadsListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ThreadsListViewHolder -> {
                holder.bind(position)
            }
        }
    }

    interface OnRowLayoutListener {
        fun onRowHeightChanged()
    }

    private val onRowLayoutListener: OnRowLayoutListener = object: OnRowLayoutListener {
        override fun onRowHeightChanged() {
            val firstVisibleItemPosition = (recyclerView.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()
            val isScrolledAtFirstRow = firstVisibleItemPosition == 0

            if (isScrolledAtFirstRow) {
                forceScrollToTop()
            }
        }
    }

    fun forceScrollToTop() {
        recyclerView.layoutManager?.scrollToPosition(0)
    }

    inner class ThreadsListViewHolder(
        private val binding: ThreadsListItemHolderBinding
    ): RecyclerView.ViewHolder(binding.root), DefaultLifecycleObserver {

        private val holderJobs: ArrayList<Job> = ArrayList(2)
        private val disposables: ArrayList<Disposable> = ArrayList(2)
        private var currentViewState: ThreadItemViewState? = null

        private val holderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        init {
            binding.root.setOnClickListener {
                currentViewState?.let { threadItem ->
                    viewModel.navigateToThreadDetail(threadItem.uuid)
                }
            }

            binding.textViewThreadMessageContent.setOnClickListener {
                currentViewState?.let { threadItem ->
                    viewModel.navigateToThreadDetail(threadItem.uuid)
                }
            }
        }

        fun bind(position: Int) {
            cleanup()

            val viewState = differ.currentList.elementAtOrNull(position).also { currentViewState = it } ?: return

            binding.setView(
                holderScope,
                holderJobs,
                disposables,
                viewModel.dispatchers,
                viewModel.audioPlayerController,
                imageLoader,
                viewModel.memeServerTokenHandler,
                viewState,
                userColorsHelper,
                null,
                onRowLayoutListener,
            )

            observeAudioAttachmentState()
        }

        fun cleanup() {
            holderJobs.forEach { it.cancel() }
            holderJobs.clear()

            disposables.forEach { it.dispose() }
            disposables.clear()

            audioAttachmentJob?.cancel()

            holderScope.coroutineContext.cancelChildren()
        }

        private var audioAttachmentJob: Job? = null
        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)

            audioAttachmentJob?.let { job ->
                if (!job.isActive) {
                    observeAudioAttachmentState()
                }
            }
        }

        private fun observeAudioAttachmentState() {
            currentViewState?.bubbleAudioAttachment?.let { audioAttachment ->
                if (audioAttachment is LayoutState.Bubble.ContainerSecond.AudioAttachment.FileAvailable) {
                    audioAttachmentJob?.cancel()
                    audioAttachmentJob = holderScope.launch(viewModel.mainImmediate) {
                        viewModel.audioPlayerController.getAudioState(audioAttachment)?.collect { audioState ->
                            binding.setAudioAttachmentLayoutForState(audioState)
                        }
                    }
                }
            }
        }

        init {
            lifecycleOwner.lifecycle.addObserver(this)
        }
    }

    override fun onLayoutChange(
        v: View?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        if (bottom != oldBottom) {
            val lastPosition = differ.currentList.size - 1
            if (
                recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE  &&
                layoutManager.findLastVisibleItemPosition() == lastPosition
            ) {
                recyclerView.scrollToPosition(lastPosition)
            }
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)

        when (holder) {
            is ThreadsAdapter.ThreadsListViewHolder -> holder.cleanup()
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)

        for (i in 0 until itemCount) {
            when (val holder = recyclerView.findViewHolderForAdapterPosition(i)) {
                is ThreadsAdapter.ThreadsListViewHolder -> holder.cleanup()
            }
        }
    }
}