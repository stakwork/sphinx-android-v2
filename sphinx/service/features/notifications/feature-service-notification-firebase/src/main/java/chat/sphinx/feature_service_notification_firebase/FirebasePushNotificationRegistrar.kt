package chat.sphinx.feature_service_notification_firebase

import android.content.Context
import app.cash.exhaustive.Exhaustive
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_service_notification.PushNotificationRegistrar
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.logger.d
import chat.sphinx.logger.w
import chat.sphinx.wrapper_contact.DeviceId
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow

internal class FirebasePushNotificationRegistrar(
    private val contactRepository: ContactRepository,
    private val LOG: SphinxLogger,
    private val app: Context
): PushNotificationRegistrar() {

    companion object {
        const val TAG = "FirebasePushNotificationRegistrar"
    }

    init {
        // Remove all manual Firebase initialization code
        LOG.d(TAG, "Firebase Push Notification Registrar initialized")

        // Optional: Add debug logging to verify Firebase is available
        debugFirebaseAvailability()
    }

    private fun debugFirebaseAvailability() {
        try {
            val firebaseApps = FirebaseApp.getApps(app.applicationContext)
            LOG.d(TAG, "Firebase apps available: ${firebaseApps.size}")

            if (firebaseApps.isEmpty()) {
                LOG.w(TAG, "No Firebase apps found - Firebase may not be initialized yet")
            } else {
                firebaseApps.forEach { app ->
                    LOG.d(TAG, "Firebase app available: ${app.name}")
                }
            }
        } catch (e: Exception) {
            LOG.w(TAG, "Error checking Firebase availability", e)
        }
    }

    override suspend fun register(): Response<Any, ResponseError> {
        val tokenFetchResponse: MutableStateFlow<Response<String, ResponseError>?> =
            MutableStateFlow(null)

        try {
            // No need to check Firebase initialization - let it fail naturally if not ready
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    val msg = "Fetching FCM registration token failed"
                    LOG.w(TAG, msg, task.exception)
                    tokenFetchResponse.value = Response.Error(ResponseError(msg, task.exception))
                    return@OnCompleteListener
                }

                val token = task.result
                tokenFetchResponse.value = if (token != null) {
                    LOG.d(TAG, "FCM token retrieved successfully: $token")
                    Response.Success(token)
                } else {
                    val msg = "FCM registration token was null"
                    LOG.w(TAG, msg)
                    Response.Error(ResponseError(msg))
                }
            })
        } catch (e: Exception) {
            val msg = "Unexpected error occurred while fetching FCM registration token"
            LOG.w(TAG, msg, e)
            return Response.Error(ResponseError(msg, e))
        }

        try {
            // Wait for the listener to complete
            tokenFetchResponse.collect { response ->
                @Exhaustive
                when (response) {
                    null -> {}
                    is Response.Error,
                    is Response.Success -> {
                        throw Exception() // Break out of collect loop
                    }
                }
            }
        } catch (e: Exception) {
            // Expected exception to break out of collect
        }

        return tokenFetchResponse.value?.let { response ->
            @Exhaustive
            when (response) {
                is Response.Error -> {
                    response
                }
                is Response.Success -> {
                    contactRepository.updateOwnerDeviceId(DeviceId(response.value))
                    Response.Success(response.value)
                }
            }
        } ?: Response.Error(ResponseError("FCM token fetch completed but no response was received"))
    }
}
