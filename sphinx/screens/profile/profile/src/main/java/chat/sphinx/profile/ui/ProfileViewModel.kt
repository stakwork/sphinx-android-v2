package chat.sphinx.profile.ui

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.webkit.URLUtil
import androidx.lifecycle.viewModelScope
import app.cash.exhaustive.Exhaustive
import chat.sphinx.camera_view_model_coordinator.request.CameraRequest
import chat.sphinx.camera_view_model_coordinator.response.CameraResponse
import chat.sphinx.concept_background_login.BackgroundLoginHandler
import chat.sphinx.concept_network_tor.TorManager
import chat.sphinx.concept_relay.RelayDataHandler
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_repository_dashboard_android.RepositoryDashboardAndroid
import chat.sphinx.concept_repository_feed.FeedRepository
import chat.sphinx.concept_repository_lightning.LightningRepository
import chat.sphinx.concept_repository_media.RepositoryMedia
import chat.sphinx.concept_view_model_coordinator.ViewModelCoordinator
import chat.sphinx.concept_wallet.WalletDataHandler
import chat.sphinx.key_restore.KeyRestore
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.menu_bottom_profile_pic.PictureMenuHandler
import chat.sphinx.menu_bottom_profile_pic.PictureMenuViewModel
import chat.sphinx.menu_bottom_profile_pic.UpdatingImageViewState
import chat.sphinx.profile.R
import chat.sphinx.profile.navigation.ProfileNavigator
import chat.sphinx.wrapper_common.*
import chat.sphinx.wrapper_common.lightning.Sat
import chat.sphinx.wrapper_common.message.SphinxCallLink
import chat.sphinx.wrapper_contact.Contact
import chat.sphinx.wrapper_contact.PrivatePhoto
import chat.sphinx.wrapper_lightning.NodeBalance
import com.squareup.moshi.Moshi
import chat.sphinx.resources.R as R_common
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_viewmodel.SideEffectViewModel
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.android_feature_viewmodel.updateViewState
import io.matthewnelson.concept_authentication.coordinator.AuthenticationCoordinator
import io.matthewnelson.concept_authentication.coordinator.AuthenticationRequest
import io.matthewnelson.concept_authentication.coordinator.AuthenticationResponse
import io.matthewnelson.concept_authentication.coordinator.ConfirmedPinListener
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_views.sideeffect.SideEffectContainer
import io.matthewnelson.concept_views.viewstate.ViewStateContainer
import io.matthewnelson.crypto_common.clazzes.Password
import io.matthewnelson.crypto_common.clazzes.clear
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject


@Serializable
data class SampleClass(
    val elements: List<SampleClassElement>
)

@Serializable
data class SampleClassElement(
    val key: String,
    val value: SampleClassSubElement
)

@Serializable
data class SampleClassSubElement(
    val key: Int,
    val value: List<SampleClassSubSubElement>
)

@Serializable
data class SampleClassSubSubElement(
    val key1: UInt,
    val key2: UInt,
    val key3: UInt
)

@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    private val app: Application,
    private val authenticationCoordinator: AuthenticationCoordinator,
    private val backgroundLoginHandler: BackgroundLoginHandler,
    private val cameraCoordinator: ViewModelCoordinator<CameraRequest, CameraResponse>,
    private val repositoryDashboard: RepositoryDashboardAndroid<Any>,
    private val contactRepository: ContactRepository,
    private val lightningRepository: LightningRepository,
    private val feedRepository: FeedRepository,
    private val repositoryMedia: RepositoryMedia,
    private val relayDataHandler: RelayDataHandler,
    private val walletDataHandler: WalletDataHandler,
    private val torManager: TorManager,
    private val keyRestore: KeyRestore,
    private val navigator: ProfileNavigator,
    private val LOG: SphinxLogger,
    val moshi: Moshi
): SideEffectViewModel<
        Context,
        ProfileSideEffect,
        ProfileViewState>(dispatchers, ProfileViewState.Basic),
    PictureMenuViewModel
{

    val storageBarViewStateContainer: ViewStateContainer<StorageBarViewState> by lazy {
        ViewStateContainer(StorageBarViewState.Loading)
    }

    val updatingImageViewStateContainer: ViewStateContainer<UpdatingImageViewState> by lazy {
        ViewStateContainer(UpdatingImageViewState.Idle)
    }

    override val pictureMenuHandler: PictureMenuHandler by lazy {
        PictureMenuHandler(
            app = app,
            cameraCoordinator = cameraCoordinator,
            dispatchers = this,
            viewModel = this,
            callback = { streamProvider, mediaType, fileName, contentLength, file ->

                updatingImageViewStateContainer.updateViewState(
                    UpdatingImageViewState.UpdatingImage
                )

                viewModelScope.launch(mainImmediate) {
                    val response = contactRepository.updateProfilePic(
                        stream = streamProvider,
                        mediaType = mediaType,
                        fileName = fileName,
                        contentLength = contentLength,
                    )

                    @Exhaustive
                    when (response) {
                        is Response.Error -> {
                            updatingImageViewStateContainer.updateViewState(
                                UpdatingImageViewState.UpdatingImageFailed
                            )
                        }
                        is Response.Success -> {
                            updatingImageViewStateContainer.updateViewState(
                                UpdatingImageViewState.UpdatingImageSucceed
                            )
                        }
                    }

                    try {
                        file?.delete()
                    } catch (e: Exception) {}
                }
            }
        )
    }

    private fun setUpManageStorage(){
        viewModelScope.launch(mainImmediate) {
            repositoryMedia.getStorageDataInfo().collect { storageData ->
                val totalStorage = getTotalStorage()
                val usedStorage = storageData.usedStorage
                val freeStorage = (totalStorage - usedStorage.value).toFileSize()
                val modifiedStorageDataInfo = storageData.copy(freeStorage = freeStorage)
                val storagePercentage = calculateStoragePercentage(modifiedStorageDataInfo)

                storageBarViewStateContainer.updateViewState(
                    StorageBarViewState.StorageData(
                        storagePercentage,
                        usedStorage.calculateSize(),
                        totalStorage.toFileSize()?.calculateSize() ?: "0 Bytes"
                    )
                )
            }
        }
    }

    private fun getTotalStorage(): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        return stat.blockSizeLong * stat.availableBlocksLong
    }

    private var resetPINJob: Job? = null
    fun resetPIN() {
        if (resetPINJob?.isActive == true) return

        resetPINJob = viewModelScope.launch(mainImmediate) {
            authenticationCoordinator.submitAuthenticationRequest(
                AuthenticationRequest.ResetPassword()
            ).firstOrNull()?.let { response ->
                @Exhaustive
                when (response) {
                    is AuthenticationResponse.Failure -> {
                        // handle
                    }
                    is AuthenticationResponse.Success.Authenticated -> {
                        // handle
                    }
                    is AuthenticationResponse.Success.Key -> {}
                }
            }
        }
    }

    private var setGithubPATJob: Job? = null
    fun setGithubPAT() {
        if (setGithubPATJob?.isActive == true) return

        setGithubPATJob = viewModelScope.launch(mainImmediate) {
            submitSideEffect(ProfileSideEffect.GithubPATSet { pat ->
                pat?.let {
                    viewModelScope.launch(mainImmediate) {
                        when (contactRepository.setGithubPat(pat)) {
                            is Response.Error -> {
                                submitSideEffect(ProfileSideEffect.FailedToSetGithubPat)
                            }
                            is Response.Success -> {
                                submitSideEffect(ProfileSideEffect.GithubPATSuccessfullySet)
                            }
                        }
                    }
                }
            })
        }
    }

    private var deleteAccountJob: Job? = null
    fun deleteAccount() {
        if (deleteAccountJob?.isActive == true) return

//        deleteAccountJob = viewModelScope.launch(mainImmediate) {
//            submitSideEffect(ProfileSideEffect.DeleteAccountConfirmation {
//                deleteRelayAccount()
//            })
//        }
    }

//    private fun deleteRelayAccount() {
//        viewModelScope.launch(mainImmediate) {
//            networkQueryContact.deleteAccount().collect{ loadResponse ->
//                @javax.annotation.meta.Exhaustive
//                when (loadResponse) {
//                    is LoadResponse.Loading -> {}
//                    is Response.Error -> {
//                        submitSideEffect(ProfileSideEffect.DeleteAccountError)
//                    }
//                    is Response.Success -> {
//                        deleteData()
//                    }
//                }
//            }
//        }
//    }

    private fun deleteData() {
        val appContext: Context = app.applicationContext

        val sharedPreferences: List<String> = listOf(
            "sphinx_colors",
            "signer_settings",
            "storage_limit",
            "general_settings",
            "server_urls"
        )

        for (sp in sharedPreferences) {
            appContext.getSharedPreferences(sp, Context.MODE_PRIVATE).edit().clear().let { editor ->
                if (!editor.commit()) {
                    editor.apply()
                }
            }
        }

        viewModelScope.launch(mainImmediate) {
            keyRestore.clearAll()
            repositoryDashboard.clearDatabase()

            navigator.toOnBoardWelcomeScreen()
        }
    }

    fun switchTabTo(basicTab: Boolean) {
        if (basicTab) {
            updateViewState(ProfileViewState.Basic)
        } else {
            updateViewState(
                ProfileViewState.Advanced(
                    app.getString(R_common.string.setup_signing_device)
                )
            )
        }
    }

    suspend fun getAccountBalance(): StateFlow<NodeBalance?> =
        lightningRepository.getAccountBalance()

    suspend fun updateOwner(
        alias: String?,
        privatePhoto: PrivatePhoto?,
        tipAmount: Sat?
    ): Response<Any, ResponseError> =
        contactRepository.updateOwner(alias, privatePhoto, tipAmount)

    suspend fun updateMeetingServer(url: String?) {
        _meetingServerUrlStateFlow.value = url

        delay(50L)

        if (url == null || url.isEmpty() || !URLUtil.isValidUrl(url)) {
            submitSideEffect(ProfileSideEffect.InvalidMeetingServerUrl)
            setServerUrls()
            return
        }

        val appContext: Context = app.applicationContext
        val serverUrlsSharedPreferences = appContext.getSharedPreferences("server_urls", Context.MODE_PRIVATE)

        withContext(dispatchers.io) {
            serverUrlsSharedPreferences.edit().putString(SphinxCallLink.CALL_SERVER_URL_KEY, url)
                .let { editor ->
                    if (!editor.commit()) {
                        editor.apply()
                    }
                }
        }
    }

    suspend fun updateLinkPreviewsEnabled(enabled: Boolean) {
        _linkPreviewsEnabledStateFlow.value = enabled

        delay(50L)

        val appContext: Context = app.applicationContext
        val generalSettingsSharedPreferences = appContext.getSharedPreferences(PreviewsEnabled.LINK_PREVIEWS_SHARED_PREFERENCES, Context.MODE_PRIVATE)

        withContext(dispatchers.io) {
            generalSettingsSharedPreferences.edit().putBoolean(PreviewsEnabled.LINK_PREVIEWS_ENABLED_KEY, enabled)
                .let { editor ->
                    if (!editor.commit()) {
                        editor.apply()
                    }
                }
        }
    }

    suspend fun updateFeedRecommendationsToggle(enabled: Boolean) {
        _feedRecommendationsStateFlow.value = enabled
        feedRepository.setRecommendationsToggle(enabled)

        delay(50L)

        val appContext: Context = app.applicationContext
        val generalSettingsSharedPreferences = appContext.getSharedPreferences(FeedRecommendationsToggle.FEED_RECOMMENDATIONS_SHARED_PREFERENCES, Context.MODE_PRIVATE)

        withContext(dispatchers.io) {
            generalSettingsSharedPreferences.edit().putBoolean(FeedRecommendationsToggle.FEED_RECOMMENDATIONS_ENABLED_KEY, enabled)
                .let { editor ->
                    if (!editor.commit()) {
                        editor.apply()
                    }
                }
        }
    }

    fun persistPINTimeout() {
        _pinTimeoutStateFlow.value?.let { timeout ->
            viewModelScope.launch(mainImmediate) {
                if (!backgroundLoginHandler.updateSetting(timeout)) {
                    _pinTimeoutStateFlow.value = backgroundLoginHandler.getTimeOutSetting()
                    // TODO: Side effect, failed to persist setting
                }
            }
        }
    }

    fun updatePINTimeOutStateFlow(progress: Int) {
        _pinTimeoutStateFlow.value = progress
    }

    fun backupKeys() {
        viewModelScope.launch(mainImmediate) {
            submitSideEffect(ProfileSideEffect.BackupKeysPinNeeded)

            var passwordPin: Password? = null

            authenticationCoordinator.submitAuthenticationRequest(
                AuthenticationRequest.ConfirmPin(object : ConfirmedPinListener() {
                    override suspend fun doWithConfirmedPassword(password: Password) {
                        passwordPin = password
                    }
                })
            ).firstOrNull().let { response ->
                @Exhaustive
                when (response) {
                    null,
                    is AuthenticationResponse.Failure -> {
                        submitSideEffect(ProfileSideEffect.WrongPIN)
                    }
                    is AuthenticationResponse.Success.Authenticated -> {
                        authenticationCoordinator.submitAuthenticationRequest(
                            AuthenticationRequest.GetEncryptionKey()
                        ).firstOrNull().let { keyResponse ->
                            @Exhaustive
                            when (keyResponse) {
                                null,
                                is AuthenticationResponse.Failure -> {
                                    submitSideEffect(ProfileSideEffect.BackupKeysFailed)
                                }
                                is AuthenticationResponse.Success.Authenticated -> {

                                }
                                is AuthenticationResponse.Success.Key -> {
                                    walletDataHandler.retrieveWalletMnemonic()?.let { mnemonic ->
                                        submitSideEffect(ProfileSideEffect.CopyBackupToClipboard(mnemonic.value))
                                    }
                                }
                            }
                        }
                    }
                    is AuthenticationResponse.Success.Key -> { }
                }

                passwordPin?.clear()
            }
        }
    }

    private val _pinTimeoutStateFlow: MutableStateFlow<Int?> by lazy {
        MutableStateFlow(null)
    }
    private val _meetingServerUrlStateFlow: MutableStateFlow<String?> by lazy {
        MutableStateFlow(null)
    }
    private val _linkPreviewsEnabledStateFlow: MutableStateFlow<Boolean> by lazy {
        MutableStateFlow(true)
    }

    private val _feedRecommendationsStateFlow: MutableStateFlow<Boolean> by lazy {
        MutableStateFlow(false)
    }

    val pinTimeoutStateFlow: StateFlow<Int?>
        get() = _pinTimeoutStateFlow.asStateFlow()
    val meetingServerUrlStateFlow: StateFlow<String?>
        get() = _meetingServerUrlStateFlow.asStateFlow()
    val accountOwnerStateFlow: StateFlow<Contact?>
        get() = contactRepository.accountOwner
    val linkPreviewsEnabledStateFlow: StateFlow<Boolean>
        get() = _linkPreviewsEnabledStateFlow.asStateFlow()
    val feedRecommendationsStateFlow: StateFlow<Boolean>
        get() = _feedRecommendationsStateFlow.asStateFlow()

    init {
        viewModelScope.launch(mainImmediate) {
            _pinTimeoutStateFlow.value = backgroundLoginHandler.getTimeOutSetting()

            setServerUrls()
            setLinkPreviewsEnabled()
            setFeedRecommendationsToggle()
            setUpManageStorage()
        }
    }

    private fun setServerUrls() {
        val appContext: Context = app.applicationContext
        val serverUrlsSharedPreferences = appContext.getSharedPreferences("server_urls", Context.MODE_PRIVATE)

        val meetingServerUrl = serverUrlsSharedPreferences.getString(
            SphinxCallLink.CALL_SERVER_URL_KEY,
            SphinxCallLink.DEFAULT_CALL_SERVER_URL
        ) ?: SphinxCallLink.DEFAULT_CALL_SERVER_URL

        _meetingServerUrlStateFlow.value = meetingServerUrl
    }

    private fun setLinkPreviewsEnabled() {
        val appContext: Context = app.applicationContext
        val serverUrlsSharedPreferences = appContext.getSharedPreferences(PreviewsEnabled.LINK_PREVIEWS_SHARED_PREFERENCES, Context.MODE_PRIVATE)

        val linkPreviewsEnabled = serverUrlsSharedPreferences.getBoolean(
            PreviewsEnabled.LINK_PREVIEWS_ENABLED_KEY,
            PreviewsEnabled.True.isTrue()
        )

        _linkPreviewsEnabledStateFlow.value = linkPreviewsEnabled
    }

    private fun setFeedRecommendationsToggle() {
        val appContext: Context = app.applicationContext
        val sharedPreferences = appContext.getSharedPreferences(FeedRecommendationsToggle.FEED_RECOMMENDATIONS_SHARED_PREFERENCES, Context.MODE_PRIVATE)

        val feedRecommendationsToggle = sharedPreferences.getBoolean(
            FeedRecommendationsToggle.FEED_RECOMMENDATIONS_ENABLED_KEY, false
        )
        feedRepository.setRecommendationsToggle(feedRecommendationsToggle)
        _feedRecommendationsStateFlow.value = feedRecommendationsToggle
    }

    override val sideEffectContainer: SideEffectContainer<Context, ProfileSideEffect>
        get() = super.sideEffectContainer
}
