package chat.sphinx.dashboard.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.*
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.cash.exhaustive.Exhaustive
import chat.sphinx.concept_image_loader.Disposable
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_image_loader.ImageLoaderOptions
import chat.sphinx.concept_user_colors_helper.UserColorsHelper
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.LayoutChatListChatHolderBinding
import chat.sphinx.dashboard.ui.ChatListViewModel
import chat.sphinx.dashboard.ui.collectChatViewState
import chat.sphinx.resources.*
import chat.sphinx.wrapper_chat.*
import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.invite.*
import chat.sphinx.wrapper_common.lightning.asFormattedString
import chat.sphinx.wrapper_common.util.getInitials
import chat.sphinx.resources.R as R_common
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.goneIfFalse
import io.matthewnelson.android_feature_screens.util.goneIfTrue
import io.matthewnelson.android_feature_screens.util.invisible
import io.matthewnelson.android_feature_screens.util.invisibleIfFalse
import io.matthewnelson.android_feature_screens.util.visible
import io.matthewnelson.android_feature_viewmodel.util.OnStopSupervisor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ChatListAdapter(
    private val recyclerView: RecyclerView,
    private val layoutManager: LinearLayoutManager,
    private val imageLoader: ImageLoader<ImageView>,
    private val lifecycleOwner: LifecycleOwner,
    private val onStopSupervisor: OnStopSupervisor,
    private val viewModel: ChatListViewModel,
    private val userColorsHelper: UserColorsHelper
): RecyclerView.Adapter<ChatListAdapter.ChatViewHolder>(), DefaultLifecycleObserver {

    // AsyncListDiffer setup
    private val diffCallback = object : DiffUtil.ItemCallback<DashboardChat>() {
        override fun areItemsTheSame(oldItem: DashboardChat, newItem: DashboardChat): Boolean {
            return when {
                oldItem is DashboardChat.Active && newItem is DashboardChat.Active -> {
                    oldItem.chat.id == newItem.chat.id
                }
                oldItem is DashboardChat.Inactive.Invite && newItem is DashboardChat.Inactive.Invite -> {
                    oldItem.invite?.id == newItem.invite?.id &&
                            oldItem.contact.id == newItem.contact.id
                }
                oldItem is DashboardChat.Inactive.Conversation && newItem is DashboardChat.Inactive.Conversation -> {
                    oldItem.chatName == newItem.chatName &&
                            oldItem.contact.id == newItem.contact.id
                }
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: DashboardChat, newItem: DashboardChat): Boolean {
            return when {
                oldItem is DashboardChat.Active && newItem is DashboardChat.Active -> {
                    oldItem.chat.type == newItem.chat.type &&
                            oldItem.chatName == newItem.chatName &&
                            oldItem.chat.notify == newItem.chat.notify &&
                            oldItem.chat.seen == newItem.chat.seen &&
                            oldItem.chat.photoUrl == newItem.chat.photoUrl &&
                            oldItem.chat.latestMessageId == newItem.chat.latestMessageId &&
                            oldItem.message?.seen == newItem.message?.seen
                }
                oldItem is DashboardChat.Inactive.Invite && newItem is DashboardChat.Inactive.Invite -> {
                    oldItem.invite?.status == newItem.invite?.status &&
                            oldItem.contact.status == newItem.contact.status
                }
                oldItem is DashboardChat.Inactive.Conversation && newItem is DashboardChat.Inactive.Conversation -> {
                    oldItem.chatName == newItem.chatName
                }
                else -> false
            }
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.collectChatViewState { viewState ->
                if (viewModel.isFirstLoad() && differ.currentList.isEmpty() && viewState.list.isNotEmpty()) {
                    viewModel.markAsLoaded()
                    differ.submitList(viewState.list) {
                        recyclerView.scrollToPosition(0)
                    }
                } else {
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (
                        firstVisibleItemPosition == 0                               &&
                        recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE
                    ) {
                        differ.submitList(viewState.list) {
                            recyclerView.scrollToPosition(0)
                        }
                    } else {
                        differ.submitList(viewState.list)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatListAdapter.ChatViewHolder {
        val binding = LayoutChatListChatHolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatListAdapter.ChatViewHolder, position: Int) {
        holder.bind(position)
    }

    private val today00: DateTime by lazy {
        DateTime.getToday00()
    }

    private val imageLoaderOptions: ImageLoaderOptions by lazy {
        ImageLoaderOptions.Builder()
            .placeholderResId(R_common.drawable.ic_profile_avatar_circle)
            .build()
    }

    inner class ChatViewHolder(
        private val binding: LayoutChatListChatHolderBinding
    ): RecyclerView.ViewHolder(binding.root), DefaultLifecycleObserver {

        private var disposable: Disposable? = null
        private var dChat: DashboardChat? = null
        private var badgeJob: Job? = null
        private var mentionsJob: Job? = null

        init {
            binding.layoutConstraintChatHolder.setOnClickListener {
                dChat?.let { dashboardChat ->
                    @Exhaustive
                    when (dashboardChat) {
                        is DashboardChat.Active.Conversation -> {
                            lifecycleOwner.lifecycleScope.launch {
                                viewModel.dashboardNavigator.toChatContact(
                                    dashboardChat.chat.id,
                                    dashboardChat.contact.id
                                )
                            }
                        }
                        is DashboardChat.Active.GroupOrTribe -> {
                            lifecycleOwner.lifecycleScope.launch {
                                if (dashboardChat.chat.type.isTribe()) {
                                    viewModel.dashboardNavigator.toChatTribe(dashboardChat.chat.id)
                                }
                            }
                        }
                        is DashboardChat.Inactive.Conversation -> {
                            lifecycleOwner.lifecycleScope.launch {
                                viewModel.dashboardNavigator.toChatContact(
                                    null,
                                    dashboardChat.contact.id
                                )
                            }
                        }
                        is DashboardChat.Inactive.Invite -> {
                            dashboardChat.invite?.let { invite ->
                                lifecycleOwner.lifecycleScope.launch {
                                    viewModel.dashboardNavigator.toQRCodeDetail(
                                        invite.inviteString.value,
                                        binding.root.context.getString(
                                            R.string.invite_qr_code_header_name
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        @OptIn(ExperimentalStdlibApi::class)
        fun bind(position: Int) {
            binding.apply {
                val dashboardChat: DashboardChat = differ.currentList.getOrNull(position) ?: let {
                    dChat = null
                    return
                }
                dChat = dashboardChat
                disposable?.dispose()
                badgeJob?.cancel()
                mentionsJob?.cancel()

                val isPending = dashboardChat is DashboardChat.Inactive.Conversation

                // Set Defaults
                layoutConstraintChatHolderBorder.goneIfFalse(position != differ.currentList.lastIndex)
                textViewDashboardChatHolderName.setTextColorExt(android.R.color.white)
                textViewChatHolderMessage.setTextColorExt(R_common.color.placeholderText)
                textViewChatHolderMessage.setTextFont(R_common.font.roboto_regular)
                textViewDashboardChatHolderBadgeCount.invisibleIfFalse(false)

                includeDashboardChatHolderInitial.root.visible
                initialsDashboardPendingChatHolder.root.gone
                imageViewDashboardChatHolderPicture.gone

                // Image
                dashboardChat.photoUrl.let { url ->
                    if (isPending) {
                        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
                            includeDashboardChatHolderInitial.root.gone
                            initialsDashboardPendingChatHolder.apply {
                                root.visible
                                iconClock.gone
                                textViewInitials.apply {
                                    visible
                                    text = dashboardChat.chatName?.getInitials() ?: ""
                                    setBackgroundRandomColor(
                                        R_common.drawable.chat_initials_circle,
                                        Color.parseColor(
                                            dashboardChat.getColorKey()?.let { colorKey ->
                                                userColorsHelper.getHexCodeForKey(
                                                    colorKey,
                                                    root.context.getRandomHexCode(),
                                                )
                                            }
                                        ),
                                    )
                                }
                            }
                        }
                    } else {
                        includeDashboardChatHolderInitial.apply {
                            root.visible
                            imageViewChatPicture.goneIfFalse(url != null)
                            textViewInitials.goneIfFalse(url == null)
                        }

                        if (url != null) {
                            onStopSupervisor.scope.launch(viewModel.dispatchers.default) {
                                imageLoader.load(
                                    includeDashboardChatHolderInitial.imageViewChatPicture,
                                    url.value,
                                    imageLoaderOptions
                                )
                            }
                        } else {
                            includeDashboardChatHolderInitial.textViewInitials.text =
                                dashboardChat.chatName?.getInitials() ?: ""

                            onStopSupervisor.scope.launch(viewModel.mainImmediate) {
                                includeDashboardChatHolderInitial.textViewInitials
                                    .setInitialsColor(
                                        dashboardChat.getColorKey()?.let { colorKey ->
                                            Color.parseColor(
                                                userColorsHelper.getHexCodeForKey(
                                                    colorKey,
                                                    root.context.getRandomHexCode()
                                                )
                                            )
                                        },
                                        R_common.drawable.chat_initials_circle
                                    )
                            }
                        }
                    }
                }

                // Name
                val chatName = if (dashboardChat is DashboardChat.Inactive.Invite) {
                    dashboardChat.getChatName(root.context)
                } else if (dashboardChat.chatName != null) {
                    dashboardChat.chatName
                } else {
                    // Should never make it here, but just in case...
                    textViewDashboardChatHolderName.setTextColorExt(R_common.color.primaryRed)
                    textViewChatHolderCenteredName.setTextColorExt(R_common.color.primaryRed)
                    root.context.getString(R_common.string.null_name_error)
                }

                textViewDashboardChatHolderName.text = chatName
                textViewChatHolderCenteredName.text = chatName

                val chatHasMessages = (dashboardChat as? DashboardChat.Active)?.message != null
                val activeChatOrInvite = ((dashboardChat is DashboardChat.Active && chatHasMessages)
                        || dashboardChat is DashboardChat.Inactive.Invite
                        || isPending)

                textViewDashboardChatHolderName.invisibleIfFalse(activeChatOrInvite)
                imageViewChatHolderLock.invisibleIfFalse(activeChatOrInvite)
                textViewChatHolderMessageIcon.invisibleIfFalse(activeChatOrInvite)
                startIconClock.invisibleIfFalse(activeChatOrInvite)
                textViewChatHolderMessage.invisibleIfFalse(activeChatOrInvite)
                textViewChatHolderTime.invisibleIfFalse(activeChatOrInvite)

                textViewChatHolderCenteredName.invisibleIfFalse(!activeChatOrInvite)
                imageViewChatHolderCenteredLock.invisibleIfFalse(!activeChatOrInvite)

                if (dashboardChat is DashboardChat.Active.Conversation) {
                    imageViewChatHolderLock.text = getString(R_common.string.material_icon_name_lock)
                    imageViewChatHolderCenteredLock.text = getString(R_common.string.material_icon_name_lock)
                }

                if (dashboardChat is DashboardChat.Inactive.Invite) {
                    imageViewChatHolderLock.gone
                    imageViewChatHolderCenteredLock.gone
                }

                if (dashboardChat is DashboardChat.Inactive.Conversation) {
                    imageViewChatHolderLock.text = getString(R_common.string.material_icon_name_lock_open)
                    imageViewChatHolderCenteredLock.text = getString(R_common.string.material_icon_name_lock_open)
                }
                if (dashboardChat is DashboardChat.Active.GroupOrTribe) {
                    imageViewChatHolderLock.text = getString(R_common.string.material_icon_name_lock)
                    imageViewChatHolderCenteredLock.text = getString(R_common.string.material_icon_name_lock)
                }

                // Time
                textViewChatHolderTime.text = dashboardChat.getDisplayTime(today00)

                // Message
                val messageText = if (isPending) getString(R_common.string.waiting_for_approval) else dashboardChat.getMessageText(root.context)
                val hasUnseenMessages = dashboardChat.hasUnseenMessages()
                val isChatMuted = (dChat as? DashboardChat.Active)?.chat?.isMuted() == true

                if (messageText == root.context.getString(R_common.string.decryption_error)) {
                    textViewChatHolderMessage.setTextColorExt(R_common.color.primaryRed)
                } else {
                    textViewChatHolderMessage.setTextColorExt(if (hasUnseenMessages && !isChatMuted) R_common.color.text else R_common.color.placeholderText)
                }
                textViewChatHolderMessage.setTextFont(if (hasUnseenMessages && !isChatMuted) R_common.font.roboto_bold else R_common.font.roboto_regular)

                textViewChatHolderMessage.text = messageText
                textViewChatHolderMessage.goneIfTrue(messageText.isEmpty())

                startIconClock.goneIfFalse(isPending)
                iconReferenceView.goneIfFalse(isPending)

                handleInviteLayouts()

                handleUnseenMessageCount()
                handleUnseenMentionsCount()

                // Notification
                if (dashboardChat is DashboardChat.Active) {
                    imageViewChatHolderNotification.invisibleIfFalse(dashboardChat.chat.isMuted())
                } else {
                    imageViewChatHolderNotification.invisibleIfFalse(false)
                }
            }
        }

        private fun handleInviteLayouts() {
            dChat?.let { nnDashboardChat ->
                binding.apply {
                    textViewChatHolderTime.visible
                    textViewChatHolderMessageIcon.gone

                    if (nnDashboardChat is DashboardChat.Inactive.Invite) {
                        textViewChatHolderTime.gone
                        textViewChatHolderMessage.setTextFont(R_common.font.roboto_bold)
                        textViewChatHolderMessage.setTextColorExt(R_common.color.text)

                        includeDashboardChatHolderInitial.root.gone
                        initialsDashboardPendingChatHolder.root.gone
                        imageViewDashboardChatHolderPicture.gone
                        textViewDashboardChatHolderInvitePrice.gone
                        imageViewDashboardChatHolderPicture.visible

                        nnDashboardChat.getInviteIconAndColor()?.let { iconAndColor ->
                            iconReferenceView.invisible
                            textViewChatHolderMessageIcon.visible
                            textViewChatHolderMessageIcon.text = getString(iconAndColor.first)
                            textViewChatHolderMessageIcon.setTextColor(getColor(iconAndColor.second))
                        }

                        nnDashboardChat.getInvitePrice()?.let { price ->
                            val paymentPending = nnDashboardChat.invite?.status?.isPaymentPending() == true
                            textViewDashboardChatHolderInvitePrice.goneIfFalse(paymentPending)
                            textViewDashboardChatHolderInvitePrice.text = price.asFormattedString()
                        }
                    }
                }
            }
        }

        private fun handleUnseenMessageCount() {
            dChat?.let { nnDashboardChat ->
                badgeJob = onStopSupervisor.scope.launch(viewModel.mainImmediate) {
                    nnDashboardChat.unseenMessageFlow?.collect { unseen ->

                        binding.textViewDashboardChatHolderBadgeCount.apply {
                            if (unseen != null && unseen > 0) {
                                text = unseen.toString()
                            }

                            if (nnDashboardChat is DashboardChat.Active) {
                                val chatIsMutedOrOnlyMentions = (nnDashboardChat.chat.isMuted() || nnDashboardChat.chat.isOnlyMentions())

                                alpha = if (chatIsMutedOrOnlyMentions) 0.2f else 1.0f

                                backgroundTintList = binding.getColorStateList(if (chatIsMutedOrOnlyMentions) {
                                    R_common.color.washedOutReceivedText
                                } else {
                                    R_common.color.primaryBlue
                                }
                                )
                            }

                            goneIfFalse(nnDashboardChat.hasUnseenMessages())
                        }
                    }
                }
            }
        }

        private fun handleUnseenMentionsCount() {
            dChat?.let { nnDashboardChat ->
                mentionsJob = onStopSupervisor.scope.launch(viewModel.mainImmediate) {
                    nnDashboardChat.unseenMentionsFlow?.collect { unseenMentions ->

                        binding.textViewDashboardChatHolderMentionsCount.apply {
                            if (unseenMentions != null && unseenMentions > 0) {
                                text = "@ $unseenMentions"
                            }
                            goneIfFalse((unseenMentions ?: 0) > 0)
                        }
                    }
                }
            }
        }

        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)

            badgeJob?.let {
                if (!it.isActive) {
                    handleUnseenMessageCount()
                }
            }

            mentionsJob?.let {
                if (!it.isActive) {
                    handleUnseenMentionsCount()
                }
            }
        }

        init {
            lifecycleOwner.lifecycle.addObserver(this)
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)

        recyclerView.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(10)

            recycledViewPool.setMaxRecycledViews(0, 20)

            (layoutManager as? LinearLayoutManager)?.apply {
                isItemPrefetchEnabled = true
                initialPrefetchItemCount = 4
            }
        }
    }
}