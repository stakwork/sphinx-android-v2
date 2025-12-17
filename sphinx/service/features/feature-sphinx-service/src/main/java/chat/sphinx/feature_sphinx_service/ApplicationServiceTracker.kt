package chat.sphinx.feature_sphinx_service

import androidx.annotation.MainThread
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplicationServiceTracker @Inject constructor() {

    @Volatile
    var mustCompleteServicesCounter: Int = 0
        private set

    @MainThread
    fun onServiceCreated(mustComplete: Boolean) {
        if (mustComplete) {
            mustCompleteServicesCounter++
        }
    }

    @MainThread
    fun onServiceDestroyed(mustComplete: Boolean) {
        if (mustComplete) {
            mustCompleteServicesCounter--
        }
    }

    @Volatile
    var taskIsRemoved: Boolean = false
        private set

    @MainThread
    fun taskReturned() {
        taskIsRemoved = false
    }

    @MainThread
    fun onTaskRemoved() {
        if (!taskIsRemoved) {
            taskIsRemoved = true
        }
    }
}
