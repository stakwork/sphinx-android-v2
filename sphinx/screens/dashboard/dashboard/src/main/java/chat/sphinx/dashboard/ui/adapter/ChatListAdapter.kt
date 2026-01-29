package chat.sphinx.dashboard.ui.adapter

import android.annotation.SuppressLint
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

    // AsyncListDiffer setup - optimized for performance with payload support
    private val diffCallback = object : DiffUtil.ItemCallback<DashboardChat>() {
        override fun areItemsTheSame(oldItem: DashboardChat, newItem: DashboardChat): Boolean {
            // Early return if types don't match
            if (oldItem::class != newItem::class) return false

            return when (oldItem) {
                is DashboardChat.Active -> {
                    oldItem.chat.id == (newItem as DashboardChat.Active).chat.id
                }
                is DashboardChat.Inactive.Invite -> {
                    val new = newItem as DashboardChat.Inactive.Invite
                    oldItem.invite?.id == new.invite?.id && oldItem.contact.id == new.contact.id
                }
                is DashboardChat.Inactive.Conversation -> {
                    val new = newItem as DashboardChat.Inactive.Conversation
                    oldItem.contact.id == new.contact.id
                }
            }
        }

        override fun areContentsTheSame(oldItem: DashboardChat, newItem: DashboardChat): Boolean {
            // Early return if types don't match
            if (oldItem::class != newItem::class) return false

            return when (oldItem) {
                is DashboardChat.Active -> {
                    val new = newItem as DashboardChat.Active
                    // Check most frequently changing fields first for early exit
                    oldItem.unseenMessagesCount == new.unseenMessagesCount &&
                    oldItem.unseenMentionsCount == new.unseenMentionsCount &&
                    oldItem.chat.seen == new.chat.seen &&
                    oldItem.message?.id == new.message?.id &&
                    oldItem.chat.notify == new.chat.notify &&
                    oldItem.chatName == new.chatName &&
                    oldItem.chat.photoUrl == new.chat.photoUrl
                }
                is DashboardChat.Inactive.Invite -> {
                    val new = newItem as DashboardChat.Inactive.Invite
                    oldItem.invite?.status == new.invite?.status &&
                    oldItem.contact.status == new.contact.status
                }
                is DashboardChat.Inactive.Conversation -> {
                    oldItem.chatName == (newItem as DashboardChat.Inactive.Conversation).chatName
                }
            }
        }

        override fun getChangePayload(oldItem: DashboardChat, newItem: DashboardChat): Any? {
            // Return payloads for partial updates when only badge counts change
            if (oldItem is DashboardChat.Active && newItem is DashboardChat.Active) {
                val payloads = mutableListOf<String>()

                // Check if only unseen count changed
                if (oldItem.unseenMessagesCount != newItem.unseenMessagesCount) {
                    payloads.add(PAYLOAD_UNSEEN_COUNT)
                }

                // Check if only mentions count changed
                if (oldItem.unseenMentionsCount != newItem.unseenMentionsCount) {
                    payloads.add(PAYLOAD_UNSEEN_MENTIONS)
                }

                // If only badge counts changed, return payloads for partial update
                if (payloads.isNotEmpty() &&
                    oldItem.chat.seen == newItem.chat.seen &&
                    oldItem.message?.id == newItem.message?.id &&
                    oldItem.chat.notify == newItem.chat.notify &&
                    oldItem.chatName == newItem.chatName &&
                    oldItem.chat.photoUrl == newItem.chat.photoUrl
                ) {
                    return payloads
                }
            }
            // Return null for full rebind
            return null
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

    // Payload constants for partial updates
    companion object {
        const val PAYLOAD_UNSEEN_COUNT = "unseen_count"
        const val PAYLOAD_UNSEEN_MENTIONS = "unseen_mentions"
    }

    override fun onBindViewHolder(
        holder: ChatListAdapter.ChatViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            // Full bind
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Partial update - only update badge counts
            val item = differ.currentList.getOrNull(position) ?: return
            for (payload in payloads) {
                // Payload can be a list of strings from getChangePayload
                @Suppress("UNCHECKED_CAST")
                val payloadList = payload as? List<String> ?: continue
                for (p in payloadList) {
                    when (p) {
                        PAYLOAD_UNSEEN_COUNT -> holder.updateUnseenCount(item)
                        PAYLOAD_UNSEEN_MENTIONS -> holder.updateUnseenMentions(item)
                    }
                }
            }
        }
    }

    // Eager initialization instead of lazy - these are lightweight and always needed
    private val today00: DateTime = DateTime.getToday00()

    private val imageLoaderOptions: ImageLoaderOptions = ImageLoaderOptions.Builder()
        .placeholderResId(R_common.drawable.ic_profile_avatar_circle)
        .build()

    inner class ChatViewHolder(
        private val binding: LayoutChatListChatHolderBinding
    ): RecyclerView.ViewHolder(binding.root), DefaultLifecycleObserver {

        private var disposable: Disposable? = null
        private var dChat: DashboardChat? = null

        init {
            binding.layoutConstraintChatHolder.setOnClickListener {
                dChat?.let { dashboardChat ->
                    @Exhaustive
                    when (dashboardChat) {
                        is DashboardChat.Active.Conversation -> {
                            viewModel.navigateToChatContact(
                                dashboardChat.contact.id,
                                dashboardChat.chat.id
                            )
                        }
                        is DashboardChat.Active.GroupOrTribe -> {
                            if (dashboardChat.chat.type.isTribe()) {
                                viewModel.navigateToChatTribe(
                                    dashboardChat.chat.id
                                )
                            }
                        }
                        is DashboardChat.Inactive.Conversation -> {
                            viewModel.navigateToChatContact(
                                dashboardChat.contact.id,
                                null
                            )
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

                // Image - optimized to reduce coroutine launches where possible
                dashboardChat.photoUrl.let { url ->
                    if (isPending) {
                        // Set up view hierarchy synchronously
                        includeDashboardChatHolderInitial.root.gone
                        initialsDashboardPendingChatHolder.apply {
                            root.visible
                            iconClock.gone
                            textViewInitials.visible
                            textViewInitials.text = dashboardChat.chatName?.getInitials() ?: ""
                        }
                        // Only use coroutine for suspend function getHexCodeForKey
                        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
                            initialsDashboardPendingChatHolder.textViewInitials.setBackgroundRandomColor(
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
                    } else {
                        includeDashboardChatHolderInitial.apply {
                            root.visible
                            imageViewChatPicture.goneIfFalse(url != null)
                            textViewInitials.goneIfFalse(url == null)
                        }

                        if (url != null) {
                            // Image loading needs to be async
                            onStopSupervisor.scope.launch(viewModel.dispatchers.default) {
                                imageLoader.load(
                                    includeDashboardChatHolderInitial.imageViewChatPicture,
                                    url.value,
                                    imageLoaderOptions
                                )
                            }
                        } else {
                            // Set text synchronously
                            includeDashboardChatHolderInitial.textViewInitials.text =
                                dashboardChat.chatName?.getInitials() ?: ""

                            // Only use coroutine for suspend function getHexCodeForKey
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

        @SuppressLint("SetTextI18n")
        private fun handleUnseenMessageCount() {
            dChat?.let { nnDashboardChat ->
                binding.textViewDashboardChatHolderBadgeCount.apply {
                    val unseen = nnDashboardChat.unseenMessagesCount

                    if (unseen != null && unseen > 0) {
                        text = unseen.toString()
                    }

                    if (nnDashboardChat is DashboardChat.Active) {
                        val chatIsMutedOrOnlyMentions = (nnDashboardChat.chat.isMuted() || nnDashboardChat.chat.isOnlyMentions())

                        alpha = if (chatIsMutedOrOnlyMentions) 0.5f else 1.0f

                        backgroundTintList = binding.getColorStateList(
                            if (chatIsMutedOrOnlyMentions) {
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

        private fun handleUnseenMentionsCount() {
            dChat?.let { nnDashboardChat ->
                binding.textViewDashboardChatHolderMentionsCount.apply {
                    val unseenMentions = nnDashboardChat.unseenMentionsCount

                    if (unseenMentions != null && unseenMentions > 0) {
                        text = "@ $unseenMentions"
                    }

                    if (nnDashboardChat is DashboardChat.Active) {
                        val chatIsMuted = nnDashboardChat.chat.isMuted()

                        alpha = if (chatIsMuted) 0.5f else 1.0f

                        backgroundTintList = binding.getColorStateList(
                            if (chatIsMuted) {
                                R_common.color.washedOutReceivedText
                            } else {
                                R_common.color.primaryBlue
                            }
                        )
                    }

                    goneIfFalse(nnDashboardChat.hasUnseenMessages() && (unseenMentions ?: 0) > 0)
                }
            }
        }

        // Partial update methods for payload-based updates (avoid full rebind)
        @SuppressLint("SetTextI18n")
        fun updateUnseenCount(dashboardChat: DashboardChat) {
            dChat = dashboardChat
            binding.textViewDashboardChatHolderBadgeCount.apply {
                val unseen = dashboardChat.unseenMessagesCount

                if (unseen != null && unseen > 0) {
                    text = unseen.toString()
                }

                if (dashboardChat is DashboardChat.Active) {
                    val chatIsMutedOrOnlyMentions = (dashboardChat.chat.isMuted() || dashboardChat.chat.isOnlyMentions())

                    alpha = if (chatIsMutedOrOnlyMentions) 0.5f else 1.0f

                    backgroundTintList = binding.getColorStateList(
                        if (chatIsMutedOrOnlyMentions) {
                            R_common.color.washedOutReceivedText
                        } else {
                            R_common.color.primaryBlue
                        }
                    )
                }

                goneIfFalse(dashboardChat.hasUnseenMessages())
            }
        }

        @SuppressLint("SetTextI18n")
        fun updateUnseenMentions(dashboardChat: DashboardChat) {
            dChat = dashboardChat
            binding.textViewDashboardChatHolderMentionsCount.apply {
                val unseenMentions = dashboardChat.unseenMentionsCount

                if (unseenMentions != null && unseenMentions > 0) {
                    text = "@ $unseenMentions"
                }

                if (dashboardChat is DashboardChat.Active) {
                    val chatIsMuted = dashboardChat.chat.isMuted()

                    alpha = if (chatIsMuted) 0.5f else 1.0f

                    backgroundTintList = binding.getColorStateList(
                        if (chatIsMuted) {
                            R_common.color.washedOutReceivedText
                        } else {
                            R_common.color.primaryBlue
                        }
                    )
                }

                goneIfFalse(dashboardChat.hasUnseenMessages() && (unseenMentions ?: 0) > 0)
            }
        }

        init {
            lifecycleOwner.lifecycle.addObserver(this)
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)

        recyclerView.apply {
            // Note: setHasFixedSize is set to false in ChatListFragment since we use ConcatAdapter
            // with potentially dynamic content. Don't override it here.
            setItemViewCacheSize(10)

            recycledViewPool.setMaxRecycledViews(0, 20)

            (layoutManager as? LinearLayoutManager)?.apply {
                isItemPrefetchEnabled = true
                initialPrefetchItemCount = 4
            }
        }
    }
}