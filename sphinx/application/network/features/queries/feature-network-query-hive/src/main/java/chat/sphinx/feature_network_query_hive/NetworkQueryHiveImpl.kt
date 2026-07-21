package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthTokenDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

open class NetworkQueryHiveImpl(
    private val hiveClient: OkHttpClient,
    private val moshi: Moshi,
) : NetworkQueryHive() {

    companion object {
        private const val HIVE_BASE_URL = "https://hive.sphinx.chat/api"
        private const val DEFAULT_ENDPOINT_AUTH = "$HIVE_BASE_URL/auth/sphinx/token"
    }

    // Open for test subclasses to override with a mock server URL.
    protected open val endpointAuth: String
        get() = DEFAULT_ENDPOINT_AUTH

    override fun getHiveAuthToken(
        requestDto: HiveAuthRequestDto,
    ): Flow<LoadResponse<HiveAuthTokenDto, ResponseError>> = flow {
        emit(LoadResponse.Loading)
        try {
            val body = moshi.adapter(HiveAuthRequestDto::class.java)
                .toJson(requestDto)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(endpointAuth)
                .post(body)
                .build()

            hiveClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val dto = response.body?.source()?.let {
                        moshi.adapter(HiveAuthTokenDto::class.java).fromJson(it)
                    }
                    if (dto != null) {
                        emit(Response.Success(dto))
                    } else {
                        emit(Response.Error(ResponseError("invalid-body")))
                    }
                } else {
                    emit(Response.Error(ResponseError("http-${response.code}")))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Response.Error(ResponseError("exception")))
        }
    }
}
