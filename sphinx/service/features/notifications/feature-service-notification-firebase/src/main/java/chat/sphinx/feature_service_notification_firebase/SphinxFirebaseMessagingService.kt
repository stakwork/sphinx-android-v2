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
import chat.sphinx.logger.e
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
        val message = "You have new messages %s"
        val child: String? = p0.data["child"]

        if (child == null || child == "null") {
            LOG.d(TAG, "Network not connected or null child value. Skipping notification.")
            return
        }

        if (p0.notification != null) {
            // Old notification, handled by system
            return
        }

        if (MainActivity.isActive) {
            // App in foreground, don't show notification
            return
        }

        // Launch async work off the main thread
        CoroutineScope(Dispatchers.IO).launch {
            var messageBody = "You have new messages"

            if (!MainActivity.isAppCompletelyClosed) {
                try {
                    val chatId = connectManagerRepository
                        .getChatIdByEncryptedChild(child)
                        .firstOrNull()

                    val chat = chatId?.let {
                        chatRepository.getChatById(it).firstOrNull()
                    }

                    val name = chat?.name?.value

                    messageBody = when {
                        chat?.isTribe() == true && !name.isNullOrEmpty() -> {
                            String.format(message, "in $name Tribe")
                        }
                        chat != null -> {
                            String.format(message, "from $name")
                        }
                        else -> {
                            String.format(message, "")
                        }
                    }
                } catch (e: Exception) {
                    LOG.e(TAG, "Failed to fetch chat info", e)
                }
            }

            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("child", child)
            }

            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationBuilder = NotificationCompat.Builder(applicationContext, "channel_id")
                .setSmallIcon(R_common.drawable.sphinx_white_logo)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "channel_id",
                    "Channel Name",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }

            notificationManager.notify(0, notificationBuilder.build())
        }
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