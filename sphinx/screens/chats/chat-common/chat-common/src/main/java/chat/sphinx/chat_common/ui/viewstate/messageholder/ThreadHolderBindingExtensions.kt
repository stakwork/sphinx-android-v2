package chat.sphinx.chat_common.ui.viewstate.messageholder

import android.annotation.SuppressLint
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import app.cash.exhaustive.Exhaustive
import chat.sphinx.chat_common.R
import chat.sphinx.chat_common.databinding.LayoutMessageHolderBinding
import chat.sphinx.chat_common.databinding.LayoutMessageTypeAttachmentAudioBinding
import chat.sphinx.chat_common.databinding.LayoutThreadMessageHeaderBinding
import chat.sphinx.chat_common.ui.viewstate.audio.AudioMessageState
import chat.sphinx.chat_common.ui.viewstate.audio.AudioPlayState
import chat.sphinx.chat_common.util.AudioPlayerController
import chat.sphinx.chat_common.util.VideoThumbnailUtil
import chat.sphinx.concept_image_loader.Disposable
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_image_loader.ImageLoaderOptions
import chat.sphinx.concept_image_loader.OnImageLoadListener
import chat.sphinx.concept_image_loader.Transformation
import chat.sphinx.concept_meme_server.MemeServerTokenHandler
import chat.sphinx.concept_network_client_crypto.CryptoHeader
import chat.sphinx.concept_network_client_crypto.CryptoScheme
import chat.sphinx.concept_user_colors_helper.UserColorsHelper
import chat.sphinx.highlighting_tool.SphinxHighlightingTool
import chat.sphinx.highlighting_tool.SphinxLinkify
import chat.sphinx.highlighting_tool.SphinxUrlSpan
import chat.sphinx.resources.getRandomHexCode
import chat.sphinx.resources.getString
import chat.sphinx.resources.setBackgroundRandomColor
import chat.sphinx.wrapper_common.PhotoUrl
import chat.sphinx.wrapper_common.asFormattedString
import chat.sphinx.wrapper_common.util.getHHMMString
import chat.sphinx.wrapper_common.util.getInitials
import chat.sphinx.wrapper_contact.ContactAlias
import chat.sphinx.wrapper_meme_server.headerKey
import chat.sphinx.wrapper_meme_server.headerValue
import chat.sphinx.wrapper_message_media.MessageMedia
import chat.sphinx.wrapper_message_media.isGif
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.goneIfFalse
import io.matthewnelson.android_feature_screens.util.visible
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import chat.sphinx.resources.R as R_common


internal fun LayoutThreadMessageHeaderBinding.setView(
    holderScope: CoroutineScope,
    holderJobs: ArrayList<Job>,
    disposables: ArrayList<Disposable>,
    dispatchers: CoroutineDispatchers,
    imageLoader: ImageLoader<ImageView>,
    memeServerTokenHandler: MemeServerTokenHandler,
    audioPlayerController: AudioPlayerController,
    threadHeader: MessageHolderViewState.ThreadHeader,
    userColorsHelper: UserColorsHelper,
    onSphinxInteractionListener: SphinxUrlSpan.OnInteractionListener? = null
) {
    fun loadImageAttachment(
        imageView: ImageView,
        loadingContainer: ConstraintLayout,
        url: String,
        media: MessageMedia?
    ) {
        holderScope.launch(dispatchers.mainImmediate) {
            val file: File? = media?.localFile

            val options: ImageLoaderOptions? = if (media != null) {
                val builder = ImageLoaderOptions.Builder()

                if (file == null) {
                    media.host?.let { host ->
                        memeServerTokenHandler.retrieveAuthenticationToken(host)
                            ?.let { token ->
                                builder.addHeader(token.headerKey, token.headerValue)

                                media.mediaKeyDecrypted?.value?.let { key ->
                                    val header = CryptoHeader.Decrypt.Builder()
                                        .setScheme(CryptoScheme.Decrypt.JNCryptor)
                                        .setPassword(key)
                                        .build()

                                    builder.addHeader(header.key, header.value)
                                }
                            }
                    }
                }

                builder.build()
            } else {
                null
            }

            if (file != null) {
                imageLoader.load(imageView, file, options, object : OnImageLoadListener {
                    override fun onSuccess() {
                        super.onSuccess()

                        loadingContainer.gone
//                        onRowLayoutListener?.onRowHeightChanged()
                    }

                    override fun onError() {
                        super.onError()

                        imageView.setImageDrawable(
                            ContextCompat.getDrawable(root.context, R.drawable.received_image_not_available)
                        )
                    }
                }, media.mediaType.isGif).also { disposables.add(it) }
            } else {
                imageLoader.load(imageView, url, options, object : OnImageLoadListener {
                    override fun onSuccess() {
                        super.onSuccess()

                        loadingContainer.gone
//                        onRowLayoutListener?.onRowHeightChanged()
                    }

                    override fun onError() {
                        super.onError()

                        imageView.setImageDrawable(
                            ContextCompat.getDrawable(root.context, R.drawable.received_image_not_available)
                        )
                    }
                }, media?.mediaType?.isGif == true || url.contains("gif", ignoreCase = true)).also { disposables.add(it) }
            }
        }
    }
    apply {
        setHeader(
            threadHeader.threadHeader,
            holderScope,
            holderJobs,
            disposables,
            dispatchers,
            imageLoader,
            userColorsHelper
        )

        setBubbleMessageLayout(
            threadHeader.bubbleMessage,
            onSphinxInteractionListener
        )

        constraintMediaThreadContainer.gone

        val job1 = holderScope.launch {
            setBubbleImageAttachment(threadHeader.bubbleImageAttachment) { imageView, loadingContainer, url, media ->
                loadImageAttachment(imageView, loadingContainer, url, media)
            }
        }
        holderJobs.add(job1)

        setBubbleVideoAttachment(threadHeader.bubbleVideoAttachment)

        setBubbleFileAttachment(threadHeader.bubbleFileAttachment)

        setBubbleAudioAttachment(
            threadHeader.bubbleAudioAttachment,
            audioPlayerController,
            dispatchers,
            holderJobs,
            holderScope
        )
    }
}

@SuppressLint("SetTextI18n")
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutThreadMessageHeaderBinding.setHeader(
    threadHeader: ThreadHeaderHolder?,
    holderScope: CoroutineScope,
    holderJobs: ArrayList<Job>,
    disposables: ArrayList<Disposable>,
    dispatchers: CoroutineDispatchers,
    imageLoader: ImageLoader<ImageView>,
    userColorsHelper: UserColorsHelper
) {
    if (threadHeader != null) {
        textViewContactMessageHeaderName.text = threadHeader.senderName
        textViewThreadDate.text = threadHeader.messageTimestamp

        // User Profile Picture
        layoutContactInitialHolder.apply {
            textViewInitialsName.visible
            textViewInitialsName.text = (threadHeader.senderName ?: root.context.getString(chat.sphinx.resources.R.string.unknown)).getInitials()
            imageViewChatPicture.gone

            holderScope.launch(dispatchers.mainImmediate) {
                textViewInitialsName.setBackgroundRandomColor(
                    chat.sphinx.resources.R.drawable.chat_initials_circle,
                    Color.parseColor(
                        threadHeader.colorKey.let {
                            userColorsHelper.getHexCodeForKey(
                                it,
                                root.context.getRandomHexCode()
                            )
                        }
                    )
                )
            }.let { job ->
                holderJobs.add(job)

                threadHeader.photoUrl?.let { photoUrl ->
                    textViewInitialsName.gone
                    imageViewChatPicture.visible

                    holderScope.launch(dispatchers.mainImmediate) {
                        imageLoader.load(
                            imageViewChatPicture,
                            photoUrl,
                            ImageLoaderOptions.Builder()
                                .placeholderResId(chat.sphinx.resources.R.drawable.ic_profile_avatar_circle)
                                .transformation(Transformation.CircleCrop)
                                .build()
                        ).also {
                            disposables.add(it)
                        }
                    }
                }
            }
        }
    }
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutThreadMessageHeaderBinding.setBubbleMessageLayout(
    message: LayoutState.Bubble.ContainerThird.Message?,
    onSphinxInteractionListener: SphinxUrlSpan.OnInteractionListener?
) {
    textViewThreadMessageContent.apply {
        textViewShowMore.gone

        if (message == null) {
            gone
        } else {
            visible
            text = message.text ?: getString(R_common.string.decryption_error)
            textViewThreadMessageContent.maxLines = Int.MAX_VALUE

//            textViewThreadMessageContent.post {
//                val linesCount: Int = lineCount
//
//                if (linesCount <= 12) {
//                    textViewShowMore.gone
//                } else {
//                    if (isHeaderExpended) {
//                        textViewThreadMessageContent.maxLines = Int.MAX_VALUE
//                        textViewShowMore.text =
//                            getString(R_common.string.episode_description_show_less)
//                    } else {
//                        textViewThreadMessageContent.maxLines = 12
//                        textViewShowMore.text =
//                            getString(R_common.string.episode_description_show_more)
//                    }
//                }
//            }

            val textColor = ContextCompat.getColor(
                root.context,
                if (message.decryptionError) R_common.color.primaryRed else R_common.color.textMessages
            )
            setTextColor(textColor)

            SphinxLinkify.addLinks(
                this,
                SphinxLinkify.ALL,
                root.context,
                onSphinxInteractionListener
            )

            SphinxHighlightingTool.addMarkdowns(
                this,
                message.highlightedTexts,
                emptyList(),
                message.boldTexts,
                message.markdownLinkTexts,
                onSphinxInteractionListener,
                resources,
                context
            )
        }
    }
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal suspend inline fun LayoutThreadMessageHeaderBinding.setBubbleImageAttachment(
    imageAttachment: LayoutState.Bubble.ContainerSecond.ImageAttachment?,
    loadImage: (ImageView, ConstraintLayout, String, MessageMedia?) -> Unit,
) {
    includeMessageTypeImageAttachment.apply {
        if (imageAttachment == null) {
            root.gone
        } else {
            constraintMediaThreadContainer.visible
            root.visible

            val image = imageViewAttachmentImage

            if (imageAttachment.showPaidOverlay) {
                layoutConstraintPaidImageOverlay.visible

                image.gone
            } else {
                layoutConstraintPaidImageOverlay.gone

                loadingImageProgressContainer.visible
                image.visible

                if (imageAttachment.isThread) {
                    val params = image.layoutParams as FrameLayout.LayoutParams
                    params.height = 460

                    image.layoutParams = params
                }

                loadImage(image, loadingImageProgressContainer, imageAttachment.url, imageAttachment.media)
            }
        }
    }
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutThreadMessageHeaderBinding.setBubbleAudioAttachment(
    audioAttachment: LayoutState.Bubble.ContainerSecond.AudioAttachment?,
    audioPlayerController: AudioPlayerController,
    dispatchers: CoroutineDispatchers,
    holderJobs: ArrayList<Job>,
    holderScope: CoroutineScope
) {
    includeMessageTypeAudioAttachment.apply {
        @Exhaustive
        when (audioAttachment) {
            null -> {
                root.gone
            }
            is LayoutState.Bubble.ContainerSecond.AudioAttachment.FileAvailable -> {
                constraintMediaThreadContainer.visible
                root.visible
                holderScope.launch(dispatchers.io) {
                    audioPlayerController.getAudioState(audioAttachment)?.value?.let { state ->
                        holderScope.launch(dispatchers.mainImmediate) {
                            setAudioAttachmentLayoutForState(state)
                        }
                    } ?: run {
                        holderScope.launch(dispatchers.mainImmediate) {
                            setAudioAttachmentLayoutForState(
                                AudioMessageState(
                                    audioAttachment.messageId,
                                    null,
                                    null,
                                    null,
                                    AudioPlayState.Error,
                                    1L,
                                    0L,
                                )
                            )
                        }
                    }
                }.let { job ->
                    holderJobs.add(job)
                }
            }
            is LayoutState.Bubble.ContainerSecond.AudioAttachment.FileUnavailable -> {
                constraintMediaThreadContainer.visible
                root.visible
                setAudioAttachmentLayoutForState(
                    AudioMessageState(
                        audioAttachment.messageId,
                        null,
                        null,
                        null,
                        AudioPlayState.Loading,
                        1L,
                        0L
                    )
                )
            }
        }

    }
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutThreadMessageHeaderBinding.setBubbleFileAttachment(
    fileAttachment: LayoutState.Bubble.ContainerSecond.FileAttachment?
) {
    includeMessageTypeFileAttachment.apply {

        @Exhaustive
        when (fileAttachment){
            null -> {
                root.gone
            }
            is LayoutState.Bubble.ContainerSecond.FileAttachment.FileAvailable -> {
                constraintMediaThreadContainer.visible
                root.visible

                progressBarAttachmentFileDownload.gone
                buttonAttachmentFileDownload.visible

                textViewAttachmentFileIcon.text = if (fileAttachment.isPdf) {
                    getString(R_common.string.material_icon_name_file_pdf)
                } else {
                    getString(R_common.string.material_icon_name_file_attachment)
                }

                textViewAttachmentFileName.text = fileAttachment.fileName?.value ?: "File.txt"

                textViewAttachmentFileSize.text = if (fileAttachment.isPdf) {
                    if (fileAttachment.pageCount > 1) {
                        "${fileAttachment.pageCount} ${getString(R.string.pdf_pages)}"
                    } else {
                        "${fileAttachment.pageCount} ${getString(R.string.pdf_page)}"
                    }
                } else {
                    fileAttachment.fileSize.asFormattedString()
                }
            }
            is LayoutState.Bubble.ContainerSecond.FileAttachment.FileUnavailable -> {
                constraintMediaThreadContainer.visible
                root.visible

                textViewAttachmentFileIcon.text = getString(R_common.string.material_icon_name_file_attachment)

                textViewAttachmentFileName.text = if (fileAttachment.pendingPayment) {
                    getString(R.string.paid_file_pay_to_unlock)
                } else {
                    getString(R.string.file_name_loading)
                }

                textViewAttachmentFileSize.text = "-"

                buttonAttachmentFileDownload.goneIfFalse(
                    fileAttachment.pendingPayment
                )
                progressBarAttachmentFileDownload.goneIfFalse(
                    !fileAttachment.pendingPayment
                )
            }
        }
    }
}

@MainThread
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutThreadMessageHeaderBinding.setBubbleVideoAttachment(
    videoAttachment: LayoutState.Bubble.ContainerSecond.VideoAttachment?
) {
    includeMessageTypeVideoAttachment.apply {
        imageViewAttachmentThumbnail.gone
        layoutConstraintVideoPlayButton.gone

        @Exhaustive
        when (videoAttachment) {
            null -> {
                root.gone
            }
            is LayoutState.Bubble.ContainerSecond.VideoAttachment.FileAvailable -> {
                constraintMediaThreadContainer.visible
                root.visible

                val thumbnail = VideoThumbnailUtil.loadThumbnail(videoAttachment.file)

                if (thumbnail != null) {
                    imageViewAttachmentThumbnail.setImageBitmap(thumbnail)
                    layoutConstraintVideoPlayButton.visible
                }

                imageViewAttachmentThumbnail.visible
            }
            is LayoutState.Bubble.ContainerSecond.VideoAttachment.FileUnavailable -> {
                constraintMediaThreadContainer.visible
                if  (videoAttachment.showPaidOverlay) {
                    layoutConstraintPaidVideoOverlay.visible

                    imageViewAttachmentThumbnail.gone
                } else {
                    layoutConstraintPaidVideoOverlay.gone

                    imageViewAttachmentThumbnail.visible
                }
                root.visible
            }
        }
    }
}
