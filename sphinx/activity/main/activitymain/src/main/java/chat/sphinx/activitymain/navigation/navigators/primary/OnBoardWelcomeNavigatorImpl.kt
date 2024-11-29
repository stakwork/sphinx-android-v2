package chat.sphinx.activitymain.navigation.navigators.primary

import chat.sphinx.onboard_description.R as R_onboard
import chat.sphinx.activitymain.navigation.drivers.PrimaryNavigationDriver
import chat.sphinx.onboard_description.navigation.ToOnBoardDescriptionScreen
import chat.sphinx.onboard_welcome.navigation.OnBoardWelcomeNavigator
import javax.inject.Inject

internal class OnBoardWelcomeNavigatorImpl @Inject constructor(
    navigationDriver: PrimaryNavigationDriver
): OnBoardWelcomeNavigator(navigationDriver) {

    override suspend fun toOnBoardDescriptionScreen(newUser: Boolean) {
        navigationDriver.submitNavigationRequest(
            ToOnBoardDescriptionScreen(popUpToId = R_onboard.id.on_board_description_nav_graph, newUser)
        )
    }
}