package chat.sphinx.edit_contact.ui

import android.app.Application
import android.widget.ImageView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_repository_lightning.LightningRepository
import chat.sphinx.concept_repository_subscription.SubscriptionRepository
import chat.sphinx.concept_view_model_coordinator.ViewModelCoordinator
import chat.sphinx.concept_wallet.WalletDataHandler
import chat.sphinx.contact.ui.ContactSideEffect
import chat.sphinx.contact.ui.ContactViewModel
import chat.sphinx.contact.ui.ContactViewState
import chat.sphinx.edit_contact.navigation.EditContactNavigator
import chat.sphinx.scanner_view_model_coordinator.request.ScannerRequest
import chat.sphinx.scanner_view_model_coordinator.response.ScannerResponse
import chat.sphinx.wrapper_chat.Chat
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_common.lightning.LightningRouteHint
import chat.sphinx.wrapper_contact.ContactAlias
import chat.sphinx.wrapper_contact.getColorKey
import chat.sphinx.wrapper_chat.toTimezoneEnabled
import chat.sphinx.wrapper_chat.toTimezoneIdentifier
import chat.sphinx.wrapper_chat.toTimezoneUpdated
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_navigation.util.navArgs
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_views.viewstate.ViewStateContainer
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class EditContactViewModel @Inject constructor(
    editContactNavigator: EditContactNavigator,
    dispatchers: CoroutineDispatchers,
    savedStateHandle: SavedStateHandle,
    app: Application,
    scannerCoordinator: ViewModelCoordinator<ScannerRequest, ScannerResponse>,
    contactRepository: ContactRepository,
    subscriptionRepository: SubscriptionRepository,
    walletDataHandler: WalletDataHandler,
    connectManagerRepository: ConnectManagerRepository,
    moshi: Moshi,
    lightningRepository: LightningRepository,
    imageLoader: ImageLoader<ImageView>,
    private val chatRepository: ChatRepository,
) : ContactViewModel<EditContactFragmentArgs>(
    editContactNavigator,
    dispatchers,
    app,
    contactRepository,
    subscriptionRepository,
    scannerCoordinator,
    walletDataHandler,
    connectManagerRepository,
    moshi,
    lightningRepository,
    imageLoader
) {
    override val args: EditContactFragmentArgs by savedStateHandle.navArgs()

    override val fromAddFriend: Boolean
        get() = false
    override val contactId: ContactId
        get() = ContactId(args.argContactId)

    private val chatId = ChatId(args.argContactId)

    override val viewStateContainer: ViewStateContainer<ContactViewState> by lazy {
        ContactViewStateContainer()
    }

    override fun initContactDetails() {
        viewModelScope.launch(mainImmediate) {
            contactRepository.getContactById(contactId).firstOrNull().let { contact ->
                if (contact != null) {
                    contact.nodePubKey?.let { lightningNodePubKey ->

                        submitSideEffect(
                            ContactSideEffect.ExistingContact(
                                contact.alias?.value,
                                contact.photoUrl,
                                contact.getColorKey(),
                                lightningNodePubKey,
                                contact.routeHint,
                                false
                            )
                        )
                    }
                }
            }
        }
    }

    fun updateTimezoneStatus(
        isTimezoneEnabled: Boolean,
        timezoneIdentifier: String,
        timezoneUpdated: Boolean
    ) {
        viewModelScope.launch(mainImmediate) {
            chatRepository.updateTimezoneEnabledStatus(
                isTimezoneEnabled = isTimezoneEnabled.toTimezoneEnabled(), chatId = chatId
            )

            chatRepository.updateTimezoneIdentifier(
                timezoneIdentifier = timezoneIdentifier.toTimezoneIdentifier(), chatId = chatId
            )

            chatRepository.updateTimezoneUpdated(
                timezoneUpdated = timezoneUpdated.toTimezoneUpdated(), chatId = chatId
            )
        }
    }

    fun closeFragment() {
        viewModelScope.launch(mainImmediate) {
            navigator.popBackStack()
        }
    }

    private val chatSharedFlow: SharedFlow<Chat?> = flow {
        emitAll(chatRepository.getChatById(chatId))
    }.distinctUntilChanged().shareIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(2_000),
        replay = 1,
    )

    private inner class ContactViewStateContainer :
        ViewStateContainer<ContactViewState>(ContactViewState.Idle) {
        override val viewStateFlow: StateFlow<ContactViewState> by lazy {
            flow {
                chatSharedFlow.collect { chat ->
                    emit(
                        if (chat != null) {
                            ContactViewState.ShareTimezone(
                                chat,
                            )
                        } else {
                            ContactViewState.Idle
                        }
                    )
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ContactViewState.Idle,
            )
        }
    }

    override fun createContact(
        contactAlias: ContactAlias,
        lightningNodePubKey: LightningNodePubKey,
        lightningRouteHint: LightningRouteHint?
    ) {
    }

    /** Sphinx V1 (likely to be removed) **/

    suspend fun toSubscriptionDetailScreen() {
        (navigator as EditContactNavigator).toSubscribeDetailScreen(contactId)
    }
}
