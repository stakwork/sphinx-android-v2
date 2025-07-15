package chat.sphinx.chat_contact.navigation

import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import chat.sphinx.chat_contact.R
import chat.sphinx.chat_contact.ui.ChatContactFragmentArgs
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import io.matthewnelson.android_feature_navigation.DefaultNavOptions
import io.matthewnelson.concept_navigation.NavigationRequest

class ToChatContactScreen(
    private val chatId: ChatId?,
    private val contactId: ContactId,
    @IdRes private val popUpToId: Int? = null,
    private val popUpToInclusive: Boolean = false,
): NavigationRequest<NavController>() {

    override fun navigate(controller: NavController) {
        val args = ChatContactFragmentArgs.Builder(
            contactId.value,
            chatId?.value ?: ChatId.NULL_CHAT_ID.toLong()
        ).build().toBundle()
        
        // Create optimized navigation options to reduce transition delay
        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)  // Avoid recreating if already on top
            .apply {
                popUpToId?.let { id ->
                    setPopUpTo(id, popUpToInclusive)
                }
            }
            .build()

        controller.navigate(
            R.id.chat_contact_nav_graph,
            args,
            navOptions
        )
    }
}
