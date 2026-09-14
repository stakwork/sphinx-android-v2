package chat.sphinx.dashboard.graphchat

import chat.sphinx.concept_network_client.NetworkClient
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.logger.d
import chat.sphinx.logger.e
import com.squareup.moshi.Moshi
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated Hive SSE transport for Graph Chat.
 *
 * Built from [NetworkClient.getClient].newBuilder() with unlimited read/call
 * timeouts so tool-call silence is not killed by the base 15s readTimeout.
 * Inherited [okhttp3.logging.HttpLoggingInterceptor] instances are stripped so
 * request bodies and streamed tokens are never logged.
 */
@Singleton
class GraphChatSseClient @Inject constructor(
    private val networkClient: NetworkClient,
    private val hiveTokenSource: HiveTokenSource,
    private val dispatchers: CoroutineDispatchers,
    private val logger: SphinxLogger,
) {

    internal var endpointUrl: String = ASK_QUICK_URL

    private val parser = GraphChatSseParser()
    private val moshi: Moshi = Moshi.Builder().build()

    fun stream(
        workspaceSlug: String?,
        messages: List<GraphChatMessage>,
    ): Flow<GraphChatSseEvent> = flow {
        if (workspaceSlug.isNullOrBlank()) {
            logger.e(TAG, "error", null)
            emit(GraphChatSseEvent.Error(MISSING_SLUG_MESSAGE))
            return@flow
        }

        logger.d(TAG, "connect")

        var call: Call? = null
        val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                call?.cancel()
            }
        }

        try {
            val token = obtainToken()
            if (token == null) {
                logger.e(TAG, "error", null)
                emit(GraphChatSseEvent.Error(AUTH_FAILED_MESSAGE))
                return@flow
            }

            val client = newStreamingClient(networkClient.getClient())
            when (
                val outcome = openStream(client, workspaceSlug, messages, token) { call = it }
            ) {
                is OpenOutcome.Failed -> {
                    emit(GraphChatSseEvent.Error(outcome.message))
                }
                is OpenOutcome.Opened -> {
                    outcome.response.use { response ->
                        val body = response.body
                        if (body == null) {
                            logger.e(TAG, "error", null)
                            emit(GraphChatSseEvent.Error("Empty stream body"))
                            return@flow
                        }
                        emitParsedEvents(body.source())
                    }
                }
            }
        } catch (e: CancellationException) {
            logger.d(TAG, "cancel")
            throw e
        } catch (e: Exception) {
            if (call?.isCanceled() == true || !currentCoroutineContext().isActive) {
                logger.d(TAG, "cancel")
                throw CancellationException("Graph chat SSE cancelled", e)
            }
            logger.e(TAG, "error", e)
            emit(GraphChatSseEvent.Error(e.message))
        } finally {
            cancelHandle.dispose()
        }
    }.flowOn(dispatchers.io)

    private suspend fun FlowCollector<GraphChatSseEvent>.emitParsedEvents(
        source: BufferedSource,
    ) {
        val session = parser.newSession()
        while (currentCoroutineContext().isActive && !session.isFinished) {
            val line = source.readUtf8Line() ?: break
            val event = session.acceptLine(line) ?: continue
            emitAndLog(event)
            if (event is GraphChatSseEvent.Finished || event is GraphChatSseEvent.Error) {
                return
            }
        }
        if (!session.isFinished) {
            for (event in session.finish()) {
                emitAndLog(event)
            }
        }
    }

    private suspend fun FlowCollector<GraphChatSseEvent>.emitAndLog(event: GraphChatSseEvent) {
        when (event) {
            GraphChatSseEvent.Finished -> logger.d(TAG, "finish")
            is GraphChatSseEvent.Error -> logger.e(TAG, "error", null)
            is GraphChatSseEvent.Token,
            is GraphChatSseEvent.ToolStatus -> Unit
        }
        emit(event)
    }

    internal fun newStreamingClient(base: OkHttpClient): OkHttpClient {
        val builder = base.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
        builder.interceptors().removeAll { isHttpLoggingInterceptor(it) }
        builder.networkInterceptors().removeAll { isHttpLoggingInterceptor(it) }
        return builder.build()
    }

    private suspend fun obtainToken(): String? {
        hiveTokenSource.retrieveHiveToken()?.let { return it }
        if (!hiveTokenSource.authenticateWithHive()) return null
        return hiveTokenSource.retrieveHiveToken()
    }

    private suspend fun openStream(
        client: OkHttpClient,
        workspaceSlug: String,
        messages: List<GraphChatMessage>,
        initialToken: String,
        onCall: (Call) -> Unit,
    ): OpenOutcome {
        var token = initialToken
        var retried = false
        while (true) {
            val request = buildRequest(workspaceSlug, messages, token)
            val call = client.newCall(request)
            onCall(call)
            val response = call.execute()
            if (response.code == 401) {
                response.close()
                if (retried) {
                    logger.e(TAG, "HTTP non-2xx statusCode=401", null)
                    return OpenOutcome.Failed(UNAUTHORIZED_MESSAGE)
                }
                logger.d(TAG, "401 re-auth-and-retry statusCode=401")
                retried = true
                if (!hiveTokenSource.reauthenticateWithHive()) {
                    logger.e(TAG, "error", null)
                    return OpenOutcome.Failed(AUTH_FAILED_MESSAGE)
                }
                token = hiveTokenSource.retrieveHiveToken()
                    ?: run {
                        logger.e(TAG, "error", null)
                        return OpenOutcome.Failed(AUTH_FAILED_MESSAGE)
                    }
                continue
            }
            if (!response.isSuccessful) {
                logger.e(TAG, "HTTP non-2xx statusCode=${response.code}", null)
                response.close()
                return OpenOutcome.Failed("HTTP ${response.code}")
            }
            return OpenOutcome.Opened(response)
        }
    }

    private fun buildRequest(
        workspaceSlug: String,
        messages: List<GraphChatMessage>,
        token: String,
    ): Request {
        val body = encodeBody(workspaceSlug, messages)
            .toRequestBody(JSON_MEDIA_TYPE)
        return Request.Builder()
            .url(endpointUrl)
            .post(body)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()
    }

    private fun encodeBody(
        workspaceSlug: String,
        messages: List<GraphChatMessage>,
    ): String {
        val payload = LinkedHashMap<String, Any>(2)
        payload["workspaceSlug"] = workspaceSlug
        payload["messages"] = messages.map { message ->
            linkedMapOf(
                "role" to message.role,
                "content" to message.content,
            )
        }
        return moshi.adapter(Any::class.java).toJson(payload)
    }

    private sealed class OpenOutcome {
        data class Opened(val response: Response) : OpenOutcome()
        data class Failed(val message: String) : OpenOutcome()
    }

    companion object {
        const val TAG = "GraphChatSseClient"
        const val ASK_QUICK_URL = "https://hive.sphinx.chat/api/ask/quick"
        const val MISSING_SLUG_MESSAGE = "Missing workspace slug"
        const val AUTH_FAILED_MESSAGE = "Failed to authenticate with Hive"
        const val UNAUTHORIZED_MESSAGE = "Unauthorized"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val HTTP_LOGGING_INTERCEPTOR =
            "okhttp3.logging.HttpLoggingInterceptor"

        private fun isHttpLoggingInterceptor(interceptor: Interceptor): Boolean {
            var cls: Class<*>? = interceptor.javaClass
            while (cls != null) {
                if (cls.name == HTTP_LOGGING_INTERCEPTOR) return true
                cls = cls.superclass
            }
            return false
        }
    }
}
