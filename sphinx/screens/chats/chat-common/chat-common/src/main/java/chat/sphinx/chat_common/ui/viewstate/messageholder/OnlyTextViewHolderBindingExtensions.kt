package chat.sphinx.chat_common.ui.viewstate.messageholder

import android.view.View
import android.widget.ImageView
import android.widget.Space
import androidx.annotation.DrawableRes
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import chat.sphinx.chat_common.R
import chat.sphinx.chat_common.databinding.LayoutOnlyTextMessageHolderBubbleBinding
import chat.sphinx.chat_common.databinding.LayoutOnlyTextReceivedMessageHolderBinding
import chat.sphinx.chat_common.databinding.LayoutOnlyTextSentMessageHolderBinding
import chat.sphinx.concept_image_loader.Disposable
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_user_colors_helper.UserColorsHelper
import chat.sphinx.highlighting_tool.SphinxHighlightingTool
import chat.sphinx.highlighting_tool.SphinxLinkify
import chat.sphinx.highlighting_tool.SphinxUrlSpan
import chat.sphinx.resources.getString
import chat.sphinx.wrapper_view.Px
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.goneIfFalse
import io.matthewnelson.android_feature_screens.util.visible
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import chat.sphinx.resources.R as R_common
import chat.sphinx.resources.R as common_R


@MainThread
@Suppress("NOTHING_TO_INLINE")
internal fun LayoutOnlyTextSentMessageHolderBinding.setView(
    holderScope: CoroutineScope,
    holderJobs: ArrayList<Job>,
    dispatchers: CoroutineDispatchers,
    recyclerViewWidth: Px,
    viewState: MessageHolderViewState,
    userColorsHelper: UserColorsHelper,
    colorCache: ColorCache,
    onSphinxInteractionListener: SphinxUrlSpan.OnInteractionListener? = null
) {
    apply {
        setSearchHighlightedStatus(
            viewState.searchHighlightedStatus
        )
        setStatusHeader(
            viewState.statusHeader,
            holderJobs,
            dispatchers,
            holderScope,
            userColorsHelper,
            colorCache
        )
        setBubbleBackground(
            viewState
        )
        if (viewState.background !is BubbleBackground.Gone) {
            setBubbleMessageLayout(
                viewState.bubbleMessage,
                onSphinxInteractionListener
            )
        }
    }
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutOnlyTextSentMessageHolderBinding.setSearchHighlightedStatus(
    searchStatus: LayoutState.SearchHighlightedStatus?
) {
    root.setBackgroundColor(
        if (searchStatus != null) {
            root.context.getColor(R_common.color.lightDivider)
        } else {
            root.context.getColor(android.R.color.transparent)
        }
    )
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutOnlyTextSentMessageHolderBinding.setStatusHeader(
    statusHeader: LayoutState.MessageStatusHeader?,
    holderJobs: ArrayList<Job>,
    dispatchers: CoroutineDispatchers,
    holderScope: CoroutineScope,
    userColorsHelper: UserColorsHelper,
    colorCache: ColorCache
) {
    includeMessageStatusHeader.apply {
        if (statusHeader == null) {
            root.gone
        } else {
            root.visible

            textViewMessageStatusReceivedSenderName.apply {
                statusHeader.senderName?.let { name ->
                    if (name.isEmpty()) {
                        gone
                    } else {
                        visible
                        text = name
                        holderScope.launch(dispatchers.default) {
                            val color = colorCache.getColor(statusHeader.colorKey, root.context, holderScope, userColorsHelper)

                            launch(dispatchers.mainImmediate) {
                                textViewMessageStatusReceivedSenderName.setTextColor(
                                    color
                                )
                            }
                        }.let { job ->
                            holderJobs.add(job)
                        }
                    }
                } ?: gone
            }

            layoutConstraintMessageStatusSentContainer.goneIfFalse(statusHeader.showSent)
            layoutConstraintMessageStatusReceivedContainer.goneIfFalse(statusHeader.showReceived)

            if (statusHeader.showSent) {
                textViewMessageStatusSentTimestamp.text = statusHeader.timestamp
                textViewMessageStatusSentLockIcon.goneIfFalse(statusHeader.showLockIcon)
                progressBarMessageStatusSending.goneIfFalse(statusHeader.showSendingIcon)
                textViewMessageStatusClockIcon.goneIfFalse(statusHeader.showClockIcon)
                textViewMessageStatusSentBoltIcon.goneIfFalse(statusHeader.showBoltIcon)
                layoutConstraintMessageStatusSentFailedContainer.goneIfFalse(statusHeader.showFailedContainer)

                val boltColor = if (statusHeader.showGrayBoltIcon) {
                    ContextCompat.getColor(
                        root.context,
                        R_common.color.secondaryText
                    )
                } else {
                    ContextCompat.getColor(
                        root.context,
                        R_common.color.primaryGreen
                    )
                }

                textViewMessageStatusSentBoltIcon.setTextColor(boltColor)

//                if (statusHeader.errorMessage?.isNotEmpty() == true) {
//                    textViewMessageStatusSentFailedText.text = statusHeader.errorMessage
//                }
            } else {
                textViewMessageStatusReceivedTimestamp.text = statusHeader.timestamp
                textViewMessageStatusReceivedLockIcon.goneIfFalse(statusHeader.showLockIcon)

                if (statusHeader.remoteTimezoneIdentifier != null) {
                    textViewMessageStatusReceivedTimezone.also { timezoneView ->
                        timezoneView.visible
                        timezoneView.text = "/ ${statusHeader.remoteTimezoneIdentifier}"
                    }
                } else {
                    textViewMessageStatusReceivedTimezone.gone
                }
            }

            val currentTime = System.currentTimeMillis()
            val messageAge = currentTime - statusHeader.messageTimestamp

            val showClockIcon = messageAge > 30_000 &&
                    !statusHeader.showFailedContainer &&
                    !statusHeader.showBoltIcon &&
                    !statusHeader.showGrayBoltIcon

            if (showClockIcon) {
                progressBarMessageStatusSending.gone
                textViewMessageStatusClockIcon.visible

            } else {
                textViewMessageStatusClockIcon.gone
            }
        }
    }
}

@MainThread
internal fun LayoutOnlyTextSentMessageHolderBinding.setBubbleBackground(
    viewState: MessageHolderViewState
) {
    if (viewState.background is BubbleBackground.Gone) {
        includeMessageHolderBubble.root.gone
        sentBubbleArrow.gone
    } else {
        sentBubbleArrow.goneIfFalse(viewState.showSentBubbleArrow)

        includeMessageHolderBubble.root.apply {
            visible

            @DrawableRes
            val resId: Int? = when (viewState.background) {
                BubbleBackground.First.Grouped -> {
                    R.drawable.background_message_bubble_sent_first
                }
                BubbleBackground.First.Isolated,
                BubbleBackground.Last -> {
                    R.drawable.background_message_bubble_sent_last
                }
                BubbleBackground.Middle -> {
                    R.drawable.background_message_bubble_sent_middle
                }
                else -> {
                    /* will never make it here as this is already checked for */
                    null
                }
            }

            resId?.let { setBackgroundResource(it) }
        }
    }

    spaceMessageHolderLeft.updateLayoutParams { width = viewState.spaceLeft ?: 0 }
    spaceMessageHolderRight.updateLayoutParams { width = viewState.spaceRight ?: 0 }
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutOnlyTextSentMessageHolderBinding.setBubbleMessageLayout(
    message: LayoutState.Bubble.ContainerThird.Message?,
    onSphinxInteractionListener: SphinxUrlSpan.OnInteractionListener?
) {
    includeMessageHolderBubble.setBubbleMessageLayout(message, onSphinxInteractionListener)
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal fun LayoutOnlyTextReceivedMessageHolderBinding.setView(
    holderScope: CoroutineScope,
    holderJobs: ArrayList<Job>,
    disposables: ArrayList<Disposable>,
    dispatchers: CoroutineDispatchers,
    imageLoader: ImageLoader<ImageView>,
    recyclerViewWidth: Px,
    viewState: MessageHolderViewState,
    userColorsHelper: UserColorsHelper,
    colorCache: ColorCache,
    onSphinxInteractionListener: SphinxUrlSpan.OnInteractionListener? = null
) {
    apply {
        holderScope.launch(dispatchers.default) {
            val initialsColor = viewState.statusHeader?.colorKey?.let { key ->
                colorCache.getColor(key, root.context, holderScope, userColorsHelper)
            }

            launch(dispatchers.mainImmediate) {
                viewState.initialHolder.setInitialHolder(
                    includeMessageHolderChatImageInitialHolder.textViewInitialsName,
                    includeMessageHolderChatImageInitialHolder.imageViewChatPicture,
                    includeMessageStatusHeader,
                    imageLoader,
                    initialsColor
                )?.also {
                    disposables.add(it)
                }
            }
        }.let { job ->
            holderJobs.add(job)
        }
        setSearchHighlightedStatus(
            viewState.searchHighlightedStatus
        )
        setStatusHeader(
            viewState.statusHeader,
            holderJobs,
            dispatchers,
            holderScope,
            userColorsHelper,
            colorCache
        )
        setBubbleBackground(
            viewState
        )
        if (viewState.background !is BubbleBackground.Gone) {
            setBubbleMessageLayout(
                viewState.bubbleMessage,
                onSphinxInteractionListener
            )
        }
    }
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutOnlyTextReceivedMessageHolderBinding.setSearchHighlightedStatus(
    searchStatus: LayoutState.SearchHighlightedStatus?
) {
    root.setBackgroundColor(
        if (searchStatus != null) {
            root.context.getColor(R_common.color.lightDivider)
        } else {
            root.context.getColor(android.R.color.transparent)
        }
    )
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutOnlyTextReceivedMessageHolderBinding.setStatusHeader(
    statusHeader: LayoutState.MessageStatusHeader?,
    holderJobs: ArrayList<Job>,
    dispatchers: CoroutineDispatchers,
    holderScope: CoroutineScope,
    userColorsHelper: UserColorsHelper,
    colorCache: ColorCache
) {
    includeMessageStatusHeader.apply {
        if (statusHeader == null) {
            root.gone

            includeMessageHolderChatImageInitialHolder.root.gone
        } else {
            root.visible

            includeMessageHolderChatImageInitialHolder.root.visible

            textViewMessageStatusReceivedSenderName.apply {
                statusHeader.senderName?.let { name ->
                    if (name.isEmpty()) {
                        gone
                    } else {
                        visible
                        text = name
                        holderScope.launch(dispatchers.default) {
                            val color = colorCache.getColor(statusHeader.colorKey, root.context, holderScope, userColorsHelper)

                            launch(dispatchers.mainImmediate) {
                                textViewMessageStatusReceivedSenderName.setTextColor(
                                    color
                                )
                            }
                        }.let { job ->
                            holderJobs.add(job)
                        }
                    }
                } ?: gone
            }

            layoutConstraintMessageStatusSentContainer.goneIfFalse(statusHeader.showSent)
            layoutConstraintMessageStatusReceivedContainer.goneIfFalse(statusHeader.showReceived)

            if (statusHeader.showSent) {
                textViewMessageStatusSentTimestamp.text = statusHeader.timestamp
                textViewMessageStatusSentLockIcon.goneIfFalse(statusHeader.showLockIcon)
                progressBarMessageStatusSending.goneIfFalse(statusHeader.showSendingIcon)
                textViewMessageStatusClockIcon.goneIfFalse(statusHeader.showClockIcon)
                textViewMessageStatusSentBoltIcon.goneIfFalse(statusHeader.showBoltIcon)
                layoutConstraintMessageStatusSentFailedContainer.goneIfFalse(statusHeader.showFailedContainer)

                val boltColor = if (statusHeader.showGrayBoltIcon) {
                    ContextCompat.getColor(
                        root.context,
                        R_common.color.secondaryText
                    )
                } else {
                    ContextCompat.getColor(
                        root.context,
                        R_common.color.primaryGreen
                    )
                }

                textViewMessageStatusSentBoltIcon.setTextColor(boltColor)

//                if (statusHeader.errorMessage?.isNotEmpty() == true) {
//                    textViewMessageStatusSentFailedText.text = statusHeader.errorMessage
//                }

            } else {
                textViewMessageStatusReceivedTimestamp.text = statusHeader.timestamp
                textViewMessageStatusReceivedLockIcon.goneIfFalse(statusHeader.showLockIcon)

                if (statusHeader.remoteTimezoneIdentifier != null) {
                    textViewMessageStatusReceivedTimezone.also { timezoneView ->
                        timezoneView.visible
                        timezoneView.text = "/ ${statusHeader.remoteTimezoneIdentifier}"
                    }
                } else {
                    textViewMessageStatusReceivedTimezone.gone
                }
            }

            val currentTime = System.currentTimeMillis()
            val messageAge = currentTime - statusHeader.messageTimestamp

            val showClockIcon = messageAge > 30_000 &&
                    !statusHeader.showFailedContainer &&
                    !statusHeader.showBoltIcon &&
                    !statusHeader.showGrayBoltIcon

            if (showClockIcon) {
                progressBarMessageStatusSending.gone
                textViewMessageStatusClockIcon.visible

            } else {
                textViewMessageStatusClockIcon.gone
            }
        }
    }
}

@MainThread
internal fun LayoutOnlyTextReceivedMessageHolderBinding.setBubbleBackground(
    viewState: MessageHolderViewState
) {
    if (viewState.background is BubbleBackground.Gone) {
        includeMessageHolderBubble.root.gone
        receivedBubbleArrow.gone
    } else {
        receivedBubbleArrow.goneIfFalse(viewState.showReceivedBubbleArrow)

        includeMessageHolderBubble.root.apply {
            visible

            @DrawableRes
            val resId: Int? = when (viewState.background) {
                BubbleBackground.First.Grouped -> {
                    R.drawable.background_message_bubble_received_first
                }
                BubbleBackground.First.Isolated,
                BubbleBackground.Last -> {
                    R.drawable.background_message_bubble_received_last
                }
                BubbleBackground.Middle -> {
                    R.drawable.background_message_bubble_received_middle
                }
                else -> {
                    null
                }
            }

            resId?.let { setBackgroundResource(it) }
        }
    }

    spaceMessageHolderLeft.updateLayoutParams { width = viewState.spaceLeft ?: 0 }
    spaceMessageHolderRight.updateLayoutParams { width = viewState.spaceRight ?: 0 }
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutOnlyTextReceivedMessageHolderBinding.setBubbleMessageLayout(
    message: LayoutState.Bubble.ContainerThird.Message?,
    onSphinxInteractionListener: SphinxUrlSpan.OnInteractionListener?
) {
    includeMessageHolderBubble.setBubbleMessageLayout(
        message, onSphinxInteractionListener
    )
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutOnlyTextMessageHolderBubbleBinding.setBubbleMessageLayout(
    message: LayoutState.Bubble.ContainerThird.Message?,
    onSphinxInteractionListener: SphinxUrlSpan.OnInteractionListener?
) {
    textViewMessageText.apply {
        if (message == null) {
            gone
        } else {
            maxLines = if (message.isThread) 2 else Integer.MAX_VALUE
            visible
            text = message.text ?: getString(R_common.string.decryption_error)

            val textColor = ContextCompat.getColor(
                root.context,
                if (message.decryptionError) R_common.color.primaryRed else R_common.color.textMessages
            )
            setTextColor(textColor)

            if (onSphinxInteractionListener != null) {
                SphinxLinkify.addLinks(
                    this,
                    SphinxLinkify.ALL,
                    root.context,
                    onSphinxInteractionListener
                )
            }

            SphinxHighlightingTool.addMarkdowns(
                this,
                message.highlightedTexts,
                message.boldTexts,
                message.markdownLinkTexts,
                onSphinxInteractionListener,
                resources,
                context
            )
        }
    }
}
