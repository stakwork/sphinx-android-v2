package chat.sphinx.chat_common.ui.viewstate.messageholder

import android.graphics.Color
import android.widget.ImageView
import chat.sphinx.chat_common.databinding.LayoutThreadMessageHeaderBinding
import chat.sphinx.chat_common.ui.viewstate.audio.AudioMessageState
import chat.sphinx.chat_common.ui.viewstate.audio.AudioPlayState
import chat.sphinx.chat_common.util.AudioPlayerController
import chat.sphinx.chat_common.util.VideoThumbnailUtil
import chat.sphinx.concept_image_loader.Disposable
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_image_loader.ImageLoaderOptions
import chat.sphinx.concept_image_loader.Transformation
import chat.sphinx.concept_user_colors_helper.UserColorsHelper
import chat.sphinx.highlighting_tool.SphinxHighlightingTool
import chat.sphinx.highlighting_tool.SphinxUrlSpan
import chat.sphinx.resources.getRandomHexCode
import chat.sphinx.resources.getString
import chat.sphinx.resources.setBackgroundRandomColor
import chat.sphinx.wrapper_common.PhotoUrl
import chat.sphinx.wrapper_common.asFormattedString
import chat.sphinx.wrapper_common.util.getInitials
import chat.sphinx.wrapper_contact.ContactAlias
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.goneIfFalse
import io.matthewnelson.android_feature_screens.util.visible
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import chat.sphinx.resources.R as R_common


internal fun LayoutThreadMessageHeaderBinding.setView(
    holderScope: CoroutineScope,
    holderJobs: ArrayList<Job>,
    disposables: ArrayList<Disposable>,
    dispatchers: CoroutineDispatchers,
    imageLoader: ImageLoader<ImageView>,
    audioPlayerController: AudioPlayerController,
    threadHeader: MessageHolderViewState.ThreadHeader,
    userColorsHelper: UserColorsHelper,
    onSphinxInteractionListener: SphinxUrlSpan.OnInteractionListener? = null
) {
    apply {
        root.visible

        val senderInfo: Triple<PhotoUrl?, ContactAlias?, String>? = if (threadHeader.message != null) {
            threadHeader.messageSenderInfo(threadHeader.message!!)
        } else {
            null
        }

        textViewContactMessageHeaderName.text = senderInfo?.second?.value ?: ""
        textViewThreadDate.text = threadHeader.timestamp

        textViewThreadMessageContent.text = threadHeader.bubbleMessage?.text ?: ""
        textViewThreadMessageContent.goneIfFalse(threadHeader.bubbleMessage?.text?.isNotEmpty() == true)

        SphinxHighlightingTool.addMarkdowns(
            textViewThreadMessageContent,
            threadHeader.bubbleMessage?.highlightedTexts ?: emptyList(),
            threadHeader.bubbleMessage?.boldTexts ?: emptyList(),
            threadHeader.bubbleMessage?.markdownLinkTexts ?: emptyList(),
            onSphinxInteractionListener,
            textViewThreadMessageContent.resources,
            textViewThreadMessageContent.context
        )

        textViewThreadDate.post(Runnable {
            val linesCount: Int = textViewThreadDate.lineCount

            if (linesCount <= 12) {
                textViewShowMore.gone
            } else {
                if (threadHeader.isExpanded) {
                    textViewThreadMessageContent.maxLines = Int.MAX_VALUE
                    textViewShowMore.text =
                        getString(R_common.string.episode_description_show_less)
                } else {
                    textViewThreadMessageContent.maxLines = 12
                    textViewShowMore.text =
                        getString(R_common.string.episode_description_show_more)
                }
            }
        })

        layoutContactInitialHolder.apply {
            senderInfo?.third?.let {
                textViewInitialsName.visible
                imageViewChatPicture.gone

                textViewInitialsName.apply {
                    text = (senderInfo.second?.value ?: "").getInitials()

                    holderScope.launch(dispatchers.mainImmediate) {
                        setBackgroundRandomColor(
                            R_common.drawable.chat_initials_circle,
                            Color.parseColor(
                                userColorsHelper.getHexCodeForKey(
                                    it,
                                    root.context.getRandomHexCode(),
                                )
                            ),
                        )
                    }.let { job ->
                        holderJobs.add(job)
                    }
                }
            }

            constraintMediaThreadContainer.gone
            includeMessageTypeFileAttachment.root.gone
            includeMessageTypeVideoAttachment.root.gone
            includeMessageTypeImageAttachment.root.gone

            includeMessageTypeImageAttachment.apply {
                threadHeader.bubbleImageAttachment?.let { imageAttachment ->
                    constraintMediaThreadContainer.visible
                    root.visible
                    layoutConstraintPaidImageOverlay.gone
                    loadingImageProgressContainer.visible
                    imageViewAttachmentImage.visible
                    imageViewAttachmentImage.scaleType = ImageView.ScaleType.CENTER_CROP

                    holderScope.launch(dispatchers.mainImmediate) {
                        imageAttachment.media?.localFile?.let { localFile ->
                            imageLoader.load(
                                imageViewAttachmentImage,
                                localFile,
                                ImageLoaderOptions.Builder().build()
                            ).also { disposables.add(it) }
                        } ?: run {
                            imageLoader.load(
                                imageViewAttachmentImage,
                                imageAttachment.url,
                                ImageLoaderOptions.Builder().build()
                            ).also { disposables.add(it) }
                        }
                    }.let { job ->
                        holderJobs.add(job)
                    }
                }
            }

            includeMessageTypeVideoAttachment.apply {
                (threadHeader.bubbleVideoAttachment as? LayoutState.Bubble.ContainerSecond.VideoAttachment.FileAvailable)?.let {
                    VideoThumbnailUtil.loadThumbnail(it.file)?.let { thumbnail ->
                        constraintMediaThreadContainer.visible
                        root.visible

                        imageViewAttachmentThumbnail.setImageBitmap(thumbnail)
                        imageViewAttachmentThumbnail.visible
                        layoutConstraintVideoPlayButton.visible
                    }
                }
            }

            includeMessageTypeFileAttachment.apply {
                (threadHeader.bubbleFileAttachment as? LayoutState.Bubble.ContainerSecond.FileAttachment.FileAvailable)?.let { fileAttachment ->
                    constraintMediaThreadContainer.visible
                    root.visible
                    progressBarAttachmentFileDownload.gone
                    buttonAttachmentFileDownload.visible

                    textViewAttachmentFileIcon.text =
                        if (fileAttachment.isPdf) {
                            getString(R_common.string.material_icon_name_file_pdf)
                        } else {
                            getString(R_common.string.material_icon_name_file_attachment)
                        }

                    textViewAttachmentFileName.text =
                        fileAttachment.fileName?.value ?: "File.txt"

                    textViewAttachmentFileSize.text =
                        if (fileAttachment.isPdf) {
                            if (fileAttachment.pageCount > 1) {
                                "${fileAttachment.pageCount} ${getString(
                                    chat.sphinx.chat_common.R.string.pdf_pages
                                )}"
                            } else {
                                "${fileAttachment.pageCount} ${getString(
                                    chat.sphinx.chat_common.R.string.pdf_page
                                )}"
                            }
                        } else {
                            fileAttachment.fileSize.asFormattedString()
                        }
                }
            }

            includeMessageTypeAudioAttachment.apply {
                (threadHeader.bubbleAudioAttachment as? LayoutState.Bubble.ContainerSecond.AudioAttachment.FileAvailable)?.let { audioAttachment ->
                    constraintMediaThreadContainer.visible
                    root.visible
                    includeMessageTypeAudioAttachment.root.setBackgroundResource(R_common.drawable.background_thread_file_attachment)

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
                    }
                }
            }

            senderInfo?.first?.let { photoUrl ->
                textViewInitialsName.gone
                imageViewChatPicture.visible

                holderScope.launch(dispatchers.mainImmediate) {
                    imageLoader.load(
                        layoutContactInitialHolder.imageViewChatPicture,
                        photoUrl.value,
                        ImageLoaderOptions.Builder()
                            .placeholderResId(R_common.drawable.ic_profile_avatar_circle)
                            .transformation(Transformation.CircleCrop)
                            .build()
                    ).also {
                        disposables.add(it)
                    }
                }.let { job ->
                    holderJobs.add(job)
                }
            }
        }
    }
}
