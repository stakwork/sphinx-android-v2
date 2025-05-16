package chat.sphinx.onboard_name.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.onboard_name.navigation.OnBoardNameNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_viewmodel.SideEffectViewModel
import io.matthewnelson.concept_authentication.coordinator.AuthenticationCoordinator
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.launch
import chat.sphinx.onboard_common.OnBoardStepHandler
import chat.sphinx.wrapper_contact.toContactAlias
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
internal class OnBoardNameViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    private val navigator: OnBoardNameNavigator,
    private val authenticationCoordinator: AuthenticationCoordinator,
    private val contactRepository: ContactRepository,
    private val onBoardStepHandler: OnBoardStepHandler,
): SideEffectViewModel<
        Context,
        OnBoardNameSideEffect,
        OnBoardNameViewState
        >(dispatchers, OnBoardNameViewState.Idle)
{

    fun updateOwnerAlias(alias: String) {
        viewModelScope.launch(mainImmediate){
            alias.toContactAlias()?.let {
                contactRepository.updateOwnerAlias(it)
                navigator.toOnBoardPictureScreen(null)
            }
        }
    }

}
