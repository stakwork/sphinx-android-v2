package chat.sphinx.chat_tribe.adapters

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.navigation.NavArgs
import chat.sphinx.chat_common.ui.ChatViewModel
import chat.sphinx.chat_tribe.R
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_image_loader.ImageLoaderOptions
import chat.sphinx.concept_image_loader.Transformation
import chat.sphinx.concept_user_colors_helper.UserColorsHelper
import chat.sphinx.resources.getRandomHexCode
import chat.sphinx.resources.setBackgroundRandomColor
import chat.sphinx.wrapper_common.util.getInitials
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.visible
import io.matthewnelson.android_feature_viewmodel.util.OnStopSupervisor
import kotlinx.coroutines.launch

class MessageMentionsAdapter<ARGS : NavArgs>(
    context: Context,
    mentions: MutableList<Triple<String, String, String>>?,
    private val onStopSupervisor: OnStopSupervisor,
    private val viewModel: ChatViewModel<ARGS>,
    private val imageLoader: ImageLoader<ImageView>,
    private val userColorsHelper: UserColorsHelper
) : ArrayAdapter<Triple<String, String, String>>(context, 0, mentions as MutableList<Triple<String, String, String>>) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView: View? = convertView
        val aliasAndPic: Triple<String, String, String>? = getItem(position)

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.layout_message_mention_item, parent, false)
        }

        val mentionTextView: TextView = convertView?.findViewById(R.id.text_view_message_mention) as TextView
        mentionTextView.text = aliasAndPic?.first ?: ""

        val textViewInitialsName: TextView = convertView?.findViewById(chat.sphinx.resources.R.id.text_view_initials_name) as TextView
        val imageViewChatPicture: AppCompatImageView = convertView?.findViewById(chat.sphinx.resources.R.id.image_view_chat_picture) as AppCompatImageView

        textViewInitialsName.visible
        imageViewChatPicture.gone

        textViewInitialsName.apply {
            text = (aliasAndPic?.first ?: "").getInitials()

            aliasAndPic?.third?.let {
                onStopSupervisor.scope.launch(viewModel.main) {
                    setBackgroundRandomColor(
                        chat.sphinx.resources.R.drawable.chat_initials_circle,
                        Color.parseColor(
                            userColorsHelper.getHexCodeForKey(
                                it,
                                convertView.context.getRandomHexCode(),
                            )
                        ),
                    )
                }
            }
        }

        aliasAndPic?.second?.let { photoUrl ->
            if (photoUrl.isNullOrEmpty()) {
                return@let
            }
            textViewInitialsName.gone
            imageViewChatPicture.visible

            onStopSupervisor.scope.launch(viewModel.default) {
                imageLoader.load(
                    imageViewChatPicture,
                    photoUrl,
                    ImageLoaderOptions.Builder()
                        .placeholderResId(chat.sphinx.resources.R.drawable.ic_profile_avatar_circle)
                        .transformation(Transformation.CircleCrop)
                        .build()
                )
            }
        }

        return convertView
    }
}