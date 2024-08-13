package chat.sphinx.feature_service_notification_firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import chat.sphinx.activitymain.MainActivity
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_connect_manager.model.NetworkStatus
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.logger.d
import chat.sphinx.wrapper_chat.isTribe
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import chat.sphinx.resources.R as R_common


/*
 * https://firebase.google.com/docs/cloud-messaging/android/first-message
 * */
@Suppress("PropertyName")
@AndroidEntryPoint
internal class SphinxFirebaseMessagingService: FirebaseMessagingService() {

    companion object {
        const val TAG = "SphinxFirebaseMessagingService"
    }

    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var dispatchers: CoroutineDispatchers

    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var connectManagerRepository: ConnectManagerRepository

    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var chatRepository: ChatRepository

    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var LOG: SphinxLogger

    private val supervisor: Job by lazy {
        SupervisorJob()
    }
    private val serviceScope: CoroutineScope by lazy {
        CoroutineScope(supervisor + dispatchers.mainImmediate)
    }
    val networkStatusStateFlow: StateFlow<NetworkStatus>
        get() = connectManagerRepository.networkStatus.asStateFlow()

    override fun onMessageReceived(p0: RemoteMessage) {
        super.onMessageReceived(p0)

        val title: String = p0.data["title"] ?: ""
        var messageBody: String = "You have new messages"
        val child: String? = p0.data["child"]
        val message = "You have new messages %s"

        if (child == null || child == "null") {
            LOG.d(TAG, "Network not connected or null child value. Skipping notification.")
            return
        }

        if (p0.notification != null) {
            ///Old notification, no need to create notification from code
            return
        }

        if (MainActivity.isActive) {
            ///If app is in foreground, then do not show notification
            return
        }

        // Get Contact/Tribe name from the child
        if (!MainActivity.isAppCompletelyClosed) {
            runBlocking {
                child.let { nnChild ->
                    val chatId =
                        connectManagerRepository.getChatIdByEncryptedChild(nnChild).firstOrNull()
                    val chat = chatId?.let { chatRepository.getChatById(it).firstOrNull() }
                    val name = chat?.name?.value

                    messageBody = if (chat?.isTribe() == true && !name.isNullOrEmpty()) {
                        String.format(message, "in $name Tribe")
                    } else {
                        String.format(message, "from $name")
                    }
                }
            }
        }

        // Create an intent to open MainActivity when the notification is clicked
        val intent = Intent(
            this,
            MainActivity::class.java
        )

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("child", child)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, "channel_id")
                .setSmallIcon(R_common.drawable.sphinx_white_logo)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

        // Get the NotificationManager
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Check if the device is running Android Oreo or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "channel_id",
                "Channel Name",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Show the notification
        notificationManager.notify(0, notificationBuilder.build())
    }

    override fun onNewToken(p0: String) {
        super.onNewToken(p0)
        LOG.d(TAG, "onNewToken: $p0")
    }

    override fun onCreate() {
        super.onCreate()
        LOG.d(TAG, "onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        supervisor.cancel()
        LOG.d(TAG, "onDestroy")
    }
}