package chat.sphinx.threads.model

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_ONLY
import chat.sphinx.chat_common.ui.viewstate.messageholder.LayoutState
import chat.sphinx.chat_common.ui.viewstate.messageholder.ReplyUserHolder
import chat.sphinx.chat_common.ui.viewstate.messageholder.ThreadHeaderHolder
import chat.sphinx.chat_common.ui.viewstate.messageholder.ThreadRepliesHolder
import chat.sphinx.highlighting_tool.boldTexts
import chat.sphinx.highlighting_tool.highlightedTexts
import chat.sphinx.highlighting_tool.markDownLinkTexts
import chat.sphinx.highlighting_tool.replacingMarkdown
import chat.sphinx.wrapper_chat.Chat
import chat.sphinx.wrapper_chat.isTribe
import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.FileSize
import chat.sphinx.wrapper_common.chatTimeFormat
import chat.sphinx.wrapper_common.timeAgo
import chat.sphinx.wrapper_contact.Contact
import chat.sphinx.wrapper_contact.getColorKey
import chat.sphinx.wrapper_contact.toContactAlias
import chat.sphinx.wrapper_message.Message
import chat.sphinx.wrapper_message.getColorKey
import chat.sphinx.wrapper_message.isPaidPendingMessage
import chat.sphinx.wrapper_message.retrieveImageUrlAndMessageMedia
import chat.sphinx.wrapper_message.retrieveTextToShow
import chat.sphinx.wrapper_message_media.isAudio
import chat.sphinx.wrapper_message_media.isPdf
import chat.sphinx.wrapper_message_media.isUnknown
import chat.sphinx.wrapper_message_media.isVideo

class ThreadItemViewState(
    val message: Message?,
    val threadMessages: List<Message>,
    val chat: Chat,
    val sent: Boolean,
    val owner: Contact?,
    val usersCount: Int,
    val repliesAmount: String,
    val lastReplyDate: String?,
    val uuid: String,
    private val memberTimezoneIdentifier: String?,
    val onBindDownloadMedia: () -> Unit,
) {
    val threadHeader: ThreadHeaderHolder? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (message == null) {
            null
        } else {
            var messageTimestamp = message.date.chatTimeFormat()

            if (chat.isTribe()) {
                if (!((message.remoteTimezoneIdentifier?.value ?: memberTimezoneIdentifier).isNullOrEmpty())) {
                    (message.remoteTimezoneIdentifier?.value ?: memberTimezoneIdentifier)?.let {
                        messageTimestamp = "$messageTimestamp / ${DateTime.getLocalTimeFor(it, message.date)}"
                    }
                }
            }

            val ownerAlias = message.senderAlias?.value ?: owner?.alias?.value
            val ownerPhotoUrl = message.senderPic?.value ?: owner?.photoUrl?.value

            ThreadHeaderHolder(
                if (sent) ownerAlias else message.senderAlias?.value,
                message.getColorKey(),
                if (sent) ownerPhotoUrl else message.senderPic?.value,
                messageTimestamp
            )
        }
    }

    val bubbleMessage: LayoutState.Bubble.ContainerThird.Message? by  lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (message == null) {
            null
        } else {
            val isThread = message.thread?.isNotEmpty() == true

            message.retrieveTextToShow()?.let { text ->
                if (text.isNotEmpty()) {
                    LayoutState.Bubble.ContainerThird.Message(
                        text = text.replacingMarkdown(),
                        highlightedTexts = text.highlightedTexts(),
                        boldTexts = text.boldTexts(),
                        markdownLinkTexts = text.markDownLinkTexts(),
                        decryptionError = false,
                        isThread = isThread
                    )
                } else {
                    null
                }
            } ?: message.messageDecryptionError?.let { decryptionError ->
                if (decryptionError) {
                    LayoutState.Bubble.ContainerThird.Message(
                        text = null,
                        highlightedTexts = emptyList(),
                        boldTexts = emptyList(),
                        markdownLinkTexts = emptyList(),
                        decryptionError = true,
                        isThread = isThread
                    )
                } else {
                    null
                }
            }
        }
    }

    val bubbleAudioAttachment: LayoutState.Bubble.ContainerSecond.AudioAttachment? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (message == null) {
            null
        } else {
            getAudioAttachment(message)
        }
    }

    val bubbleImageAttachment: LayoutState.Bubble.ContainerSecond.ImageAttachment? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (message == null) {
            null
        } else {
            getImageAttachment(message)
        }
    }

    val bubbleVideoAttachment: LayoutState.Bubble.ContainerSecond.VideoAttachment? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (message == null) {
            null
        } else {
            getVideoAttachment(message)
        }
    }


    val bubbleFileAttachment: LayoutState.Bubble.ContainerSecond.FileAttachment? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (message == null){
            null
        } else {
            getFileAttachment(message)
        }
    }

    val userReplies: ThreadRepliesHolder? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (message == null){
            null
        } else {
            val repliesList = threadMessages.drop(1).distinctBy { it.senderAlias }

            ThreadRepliesHolder(
                repliesList.size,
                threadMessages.drop(1).size.toString(),
                threadMessages.first().date.timeAgo(),
                createReplyUserHolders(repliesList, chat, owner) ?: emptyList()
            )
        }
    }

    private fun getImageAttachment(
        message: Message
    ): LayoutState.Bubble.ContainerSecond.ImageAttachment? {
        val pendingPayment = !sent && message.isPaidPendingMessage

        if (!pendingPayment) {
            onBindDownloadMedia.invoke()
        }

        val isThread = message.thread?.isNotEmpty() ?: false

        return message.retrieveImageUrlAndMessageMedia()?.let { mediaData ->
            LayoutState.Bubble.ContainerSecond.ImageAttachment(
                mediaData.first,
                mediaData.second,
                pendingPayment,
                isThread
            )
        }
    }

    private fun getVideoAttachment(
        message: Message
    ): LayoutState.Bubble.ContainerSecond.VideoAttachment? {
        return message.messageMedia?.let { nnMessageMedia ->
            if (nnMessageMedia.mediaType.isVideo) {
                nnMessageMedia.localFile?.let { nnFile ->
                    LayoutState.Bubble.ContainerSecond.VideoAttachment.FileAvailable(nnFile)
                } ?: run {
                    val pendingPayment = !sent && message.isPaidPendingMessage

                    if (!pendingPayment) {
                        onBindDownloadMedia.invoke()
                    }

                    LayoutState.Bubble.ContainerSecond.VideoAttachment.FileUnavailable(
                        pendingPayment
                    )
                }
            } else {
                null
            }
        }
    }

    private fun getFileAttachment(
        message: Message
    ): LayoutState.Bubble.ContainerSecond.FileAttachment? {
        return message.messageMedia?.let { nnMessageMedia ->
            if (nnMessageMedia.mediaType.isPdf || nnMessageMedia.mediaType.isUnknown) {

                nnMessageMedia.localFile?.let { nnFile ->

                    val pageCount = if (nnMessageMedia.mediaType.isPdf) {
                        val fileDescriptor = ParcelFileDescriptor.open(nnFile, MODE_READ_ONLY)
                        val renderer = PdfRenderer(fileDescriptor)
                        renderer.pageCount
                    } else {
                        0
                    }

                    LayoutState.Bubble.ContainerSecond.FileAttachment.FileAvailable(
                        nnMessageMedia.fileName,
                        FileSize(nnFile.length()),
                        nnMessageMedia.mediaType.isPdf,
                        pageCount
                    )
                } ?: run {
                    val pendingPayment = !sent && message.isPaidPendingMessage

                    if (!pendingPayment) {
                        onBindDownloadMedia.invoke()
                    }

                    LayoutState.Bubble.ContainerSecond.FileAttachment.FileUnavailable(
                        pendingPayment
                    )

                }
            } else {
                null
            }
        }
    }

    private fun getAudioAttachment(
        message: Message,
    ): LayoutState.Bubble.ContainerSecond.AudioAttachment? {
        return message.messageMedia?.let { nnMessageMedia ->
            if (nnMessageMedia.mediaType.isAudio) {

                nnMessageMedia.localFile?.let { nnFile ->

                    LayoutState.Bubble.ContainerSecond.AudioAttachment.FileAvailable(
                        message.id,
                        nnFile
                    )

                } ?: run {
                    val pendingPayment = !sent && message.isPaidPendingMessage

                    if (!pendingPayment) {
                        onBindDownloadMedia.invoke()
                    }

                    LayoutState.Bubble.ContainerSecond.AudioAttachment.FileUnavailable(
                        message.id,
                        pendingPayment
                    )
                }
            } else {
                null
            }
        }
    }

    private fun createReplyUserHolders(
        repliesList: List<Message>?,
        chat: Chat?,
        owner: Contact?
    ): List<ReplyUserHolder>? {
        return repliesList?.take(6)?.map {
            val isSenderOwner: Boolean = it.sender == chat?.contactIds?.firstOrNull()

            ReplyUserHolder(
                photoUrl = if (isSenderOwner) owner?.photoUrl else it.senderPic,
                alias = if (isSenderOwner) owner?.alias else it.senderAlias?.value?.toContactAlias(),
                colorKey = if (isSenderOwner) owner?.getColorKey() ?: "" else it.getColorKey()
            )
        }
    }
}
