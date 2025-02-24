package chat.sphinx.splash.ui

import android.util.Log
import androidx.lifecycle.viewModelScope
import app.cash.exhaustive.Exhaustive
import chat.sphinx.concept_background_login.BackgroundLoginHandler
import chat.sphinx.concept_repository_actions.ActionsRepository
import chat.sphinx.concept_repository_lightning.LightningRepository
import chat.sphinx.onboard_common.OnBoardStepHandler
import chat.sphinx.onboard_common.model.OnBoardStep
import chat.sphinx.splash.navigation.SplashNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_viewmodel.BaseViewModel
import io.matthewnelson.concept_authentication.coordinator.AuthenticationCoordinator
import io.matthewnelson.concept_authentication.coordinator.AuthenticationRequest
import io.matthewnelson.concept_authentication.coordinator.AuthenticationResponse
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
internal class SplashViewModel @Inject constructor(
    private val authenticationCoordinator: AuthenticationCoordinator,
    private val backgroundLoginHandler: BackgroundLoginHandler,
    dispatchers: CoroutineDispatchers,
    private val lightningRepository: LightningRepository,
    private val actionsRepository: ActionsRepository,
    private val navigator: SplashNavigator,
    private val onBoardStepHandler: OnBoardStepHandler,
): BaseViewModel<
        SplashViewState
        >(dispatchers, SplashViewState.Idle)
{
    private val timeTrackerStart = System.currentTimeMillis()

    private var screenInit: Boolean = false
    fun screenInit() {
        if (screenInit) {
            return
        } else {
            screenInit = true
        }

        // prime the account balance retrieval from SharePrefs
        viewModelScope.launch(mainImmediate) {
            lightningRepository.getAccountBalance().firstOrNull()
        }

        viewModelScope.launch(mainImmediate) {
            val isAccountSetup: Boolean = onBoardStepHandler.isAccountSetup()

            backgroundLoginHandler.attemptBackgroundLogin(
                updateLastLoginTimeOnSuccess = true
            )?.let {

                if (isAccountSetup) {
                    navigator.toDashboardScreen(
                        // No need as it was already updated
                        updateBackgroundLoginTime = false
                    )
                } else {
                    navigator.toOnBoardWelcomeScreen()
                }

            } ?: let {
                if (authenticationCoordinator.isAnEncryptionKeySet()) {
                    authenticationCoordinator.submitAuthenticationRequest(
                        AuthenticationRequest.LogIn()
                    ).firstOrNull().let { response ->
                        @Exhaustive
                        when (response) {
                            null,
                            is AuthenticationResponse.Failure -> {
                                // will not be returned as back press for handling
                                // a LogIn request minimizes the application until
                                // User has authenticated
                            }
                            is AuthenticationResponse.Success.Authenticated -> {

                                if (isAccountSetup) {
                                    navigator.toDashboardScreen(
                                        // No need as it was already updated
                                        updateBackgroundLoginTime = false
                                    )
                                } else {
                                    navigator.toOnBoardWelcomeScreen()
                                }
                            }
                            is AuthenticationResponse.Success.Key -> {
                                // will never be returned
                            }
                        }
                    }

                } else {
                    navigator.toOnBoardWelcomeScreen()
                }
            }
        }
    }

}

