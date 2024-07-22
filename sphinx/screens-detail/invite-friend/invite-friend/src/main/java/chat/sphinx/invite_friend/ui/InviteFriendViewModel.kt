package chat.sphinx.invite_friend.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import app.cash.exhaustive.Exhaustive
import chat.sphinx.concept_network_query_invite.NetworkQueryInvite
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_lightning.LightningRepository
import chat.sphinx.invite_friend.navigation.InviteFriendNavigator
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.wrapper_common.lightning.toSat
import chat.sphinx.wrapper_lightning.NodeBalance
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_viewmodel.SideEffectViewModel
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.android_feature_viewmodel.updateViewState
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class InviteFriendViewModel @Inject constructor(
    val navigator: InviteFriendNavigator,
    private val networkQueryInvite: NetworkQueryInvite,
    private val lightningRepository: LightningRepository,
    private val connectManagerRepository: ConnectManagerRepository,
    private val app: Application,
    dispatchers: CoroutineDispatchers,
): SideEffectViewModel<
        Context,
        InviteFriendSideEffect,
        InviteFriendViewState
        >(dispatchers, InviteFriendViewState.Idle)
{
    companion object {
        const val SERVER_SETTINGS_SHARED_PREFERENCES = "server_ip_settings"
        const val TRIBE_SERVER_IP = "tribe_server_ip"
        const val NETWORK_MIXER_IP = "network_mixer_ip"
        const val DEFAULT_TRIBE_KEY = "default_tribe"
    }
    private suspend fun getAccountBalance(): StateFlow<NodeBalance?> =
        lightningRepository.getAccountBalance()

    private val serverSettingsSharedPreferences =
        app.getSharedPreferences(SERVER_SETTINGS_SHARED_PREFERENCES, Context.MODE_PRIVATE)

    private val tribeServerIp: String? = serverSettingsSharedPreferences
        .getString(TRIBE_SERVER_IP, null)

    private val defaultTribe = serverSettingsSharedPreferences
        .getString(DEFAULT_TRIBE_KEY, null)

    private val mixerIp = serverSettingsSharedPreferences
        .getString(NETWORK_MIXER_IP, null)

    init {
        viewModelScope.launch(mainImmediate) {
            networkQueryInvite.getLowestNodePrice().collect { loadResponse ->
                @Exhaustive
                when (loadResponse) {
                    is LoadResponse.Loading -> {}

                    is Response.Error -> {}

                    is Response.Success -> {

                        loadResponse.value.response?.price?.let { price ->
                            price.toLong().toSat()?.let { sats ->
                                updateViewState(InviteFriendViewState.InviteFriendLowestPrice(sats))
                            }
                        }
                    }
                }
            }
        }
    }

    private var createInviteJob: Job? = null
    fun createNewInvite(
        nickname: String?,
        welcomeMessage: String?,
        sats: Long?
    ) {
        if (createInviteJob?.isActive == true) {
            return
        }

        createInviteJob = viewModelScope.launch(mainImmediate) {
            val balance = getAccountBalance().firstOrNull()?.balance?.value
            val serverDefaultTribe = if (defaultTribe?.isEmpty() == true) null else defaultTribe

            when {
                nickname.isNullOrEmpty() -> {
                    submitSideEffect(InviteFriendSideEffect.EmptyNickname)
                    updateViewState(InviteFriendViewState.InviteCreationFailed)
                }
                balance != null && sats != null && sats != 0L && sats <= balance -> {
                    connectManagerRepository.createInvite(
                        nickname,
                        welcomeMessage ?: "",
                        sats,
                        serverDefaultTribe,
                        tribeServerIp,
                        mixerIp
                    )
                    updateViewState(InviteFriendViewState.InviteCreationSucceed)
                }
                else -> {
                    submitSideEffect(InviteFriendSideEffect.EmptySats)
                    updateViewState(InviteFriendViewState.InviteCreationFailed)
                }
            }
        }
    }
}
