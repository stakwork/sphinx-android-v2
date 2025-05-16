package chat.sphinx.onboard_desktop.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import chat.sphinx.onboard_common.OnBoardStepHandler
import chat.sphinx.onboard_common.model.OnBoardInviterData
import chat.sphinx.onboard_desktop.navigation.OnBoardDesktopNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_viewmodel.BaseViewModel
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class OnBoardDesktopViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    private val app: Application,
    private val navigator: OnBoardDesktopNavigator,
): BaseViewModel<
        OnBoardDesktopViewState
        >(dispatchers, OnBoardDesktopViewState.Idle)
{

    private var nextScreenJob: Job? = null
    fun nextScreen() {
        if (nextScreenJob?.isActive == true) {
            return
        }

        nextScreenJob = viewModelScope.launch {
            navigator.toOnBoardReadyScreen()
        }
    }

    fun getDesktopApp() {
        val i = Intent(Intent.ACTION_VIEW)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        i.data = Uri.parse("https://sphinx.chat")
        app.startActivity(i)
    }

}