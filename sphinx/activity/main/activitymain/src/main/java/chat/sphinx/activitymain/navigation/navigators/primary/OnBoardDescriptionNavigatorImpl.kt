package chat.sphinx.activitymain.navigation.navigators.primary

import chat.sphinx.onboard_description.R as R_onboard
import chat.sphinx.activitymain.navigation.drivers.PrimaryNavigationDriver
import chat.sphinx.onboard_connect.navigation.ToOnBoardConnectScreen
import chat.sphinx.onboard_description.navigation.OnBoardDescriptionNavigator
import javax.inject.Inject

internal class OnBoardDescriptionNavigatorImpl @Inject constructor(
    navigationDriver: PrimaryNavigationDriver
): OnBoardDescriptionNavigator(navigationDriver) {

    override suspend fun toOnBoardConnectScreen(newUser: Boolean) {
        navigationDriver.submitNavigationRequest(
            ToOnBoardConnectScreen(popUpToId = R_onboard.id.on_board_description_nav_graph, newUser, null)
        )
    }

}