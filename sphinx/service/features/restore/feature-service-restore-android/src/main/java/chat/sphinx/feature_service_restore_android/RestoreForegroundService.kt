package chat.sphinx.feature_service_restore_android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.feature_sphinx_service.ApplicationServiceTracker
import chat.sphinx.feature_sphinx_service.SphinxService
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.logger.d
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RestoreForegroundService : SphinxService() {

    companion object {
        const val TAG = "RestoreForegroundService"

        /**
         * Broadcast sent by the service when the Cancel action is tapped in the notification.
         * [DashboardFragment] listens for this and calls [viewModel.cancelRestore()].
         */
        const val ACTION_RESTORE_CANCELLED = "chat.sphinx.ACTION_RESTORE_CANCELLED"
    }

    override val mustComplete: Boolean
        get() = true

    @Inject
    @Suppress("PropertyName", "ProtectedInFinal")
    protected lateinit var _applicationServiceTracker: ApplicationServiceTracker

    override val applicationServiceTracker: ApplicationServiceTracker
        get() = _applicationServiceTracker

    @Inject
    @Suppress("PropertyName", "ProtectedInFinal")
    protected lateinit var _dispatchers: CoroutineDispatchers

    override val dispatchers: CoroutineDispatchers
        get() = _dispatchers

    @Inject
    @Suppress("PropertyName", "ProtectedInFinal")
    protected lateinit var _connectManagerRepository: ConnectManagerRepository

    @Inject
    @Suppress("PropertyName", "ProtectedInFinal")
    protected lateinit var _LOG: SphinxLogger

    private val LOG: SphinxLogger
        get() = _LOG

    private val notification: RestoreNotification by lazy {
        RestoreNotification(this)
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var cancelReceiverRegistered = false

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RestoreNotification.ACTION_CANCEL_RESTORE) {
                LOG.d(TAG, "Cancel broadcast received — broadcasting ACTION_RESTORE_CANCELLED")
                // Notify DashboardFragment via a package-scoped broadcast
                sendBroadcast(
                    Intent(ACTION_RESTORE_CANCELLED).apply {
                        setPackage(packageName)
                    }
                )
                stopRestoreService(reason = "cancelled")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LOG.d(TAG, "Service started")

        // Acquire partial wake lock (30-minute safety cap)
        (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.let { pm ->
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "sphinx:RestoreWakeLock"
            ).also { lock ->
                lock.acquire(30 * 60 * 1000L)
                LOG.d(TAG, "WakeLock acquired")
            }
        }

        // Must call startForeground within onStartCommand to avoid ForegroundServiceDidNotStartInTimeException
        val initialNotification = notification.buildInitialNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                RestoreNotification.NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(RestoreNotification.NOTIFICATION_ID, initialNotification)
        }

        // Register receiver for notification Cancel action
        val filter = IntentFilter(RestoreNotification.ACTION_CANCEL_RESTORE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(cancelReceiver, filter)
        }
        cancelReceiverRegistered = true

        // Observe restore progress from the repository
        serviceLifecycleScope.launch {
            _connectManagerRepository.restoreProgress.collect { progress ->
                when {
                    progress == null || progress >= 100 -> {
                        LOG.d(TAG, "Restore finished (progress=$progress) — stopping service")
                        stopRestoreService(reason = if (progress != null) "completed" else "null-progress")
                    }
                    else -> {
                        notification.updateNotification(progress)
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun stopRestoreService(reason: String) {
        releaseWakeLock()
        notification.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        LOG.d(TAG, "Service stopped — reason: $reason")
        stopSelf()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
                LOG.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        if (cancelReceiverRegistered) {
            try {
                unregisterReceiver(cancelReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver was not registered — safe to ignore
            }
            cancelReceiverRegistered = false
        }
        releaseWakeLock()
        super.onDestroy()
    }
}
