package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_call.NetworkCall
import chat.sphinx.concept_network_query_hive.model.HiveFeaturePatchDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class NetworkQueryHiveFeaturesTest {

    private class RecordingNetworkCall : NetworkCall() {
        var lastUrl: String? = null
        var lastHeaders: Map<String, String>? = null
        var lastRequireSuccessful: Boolean? = null
        var lastMethod: String? = null
        var lastBody: Any? = null

        override fun <T : Any> get(
            url: String,
            responseJsonClass: Class<T>,
            headers: Map<String, String>?,
            useExtendedNetworkCallClient: Boolean,
            requireSuccessful: Boolean,
        ): Flow<LoadResponse<T, ResponseError>> {
            lastMethod = "GET"
            lastUrl = url
            lastHeaders = headers
            lastRequireSuccessful = requireSuccessful
            return flow {
                @Suppress("UNCHECKED_CAST")
                emit(Response.Error(ResponseError("unused")) as LoadResponse<T, ResponseError>)
            }
        }

        override fun <T : Any> getList(
            url: String,
            responseJsonClass: Class<T>,
            headers: Map<String, String>?,
            useExtendedNetworkCallClient: Boolean,
        ): Flow<LoadResponse<List<T>, ResponseError>> = flow {
            emit(Response.Error(ResponseError("unused")))
        }

        override fun getRawJson(
            url: String,
            headers: Map<String, String>?,
            useExtendedNetworkCallClient: Boolean,
        ): Flow<LoadResponse<String, ResponseError>> = flow {
            emit(Response.Error(ResponseError("unused")))
        }

        override suspend fun getWithoutJson(
            url: String,
            headers: Map<String, String>?,
        ): Flow<LoadResponse<String, ResponseError>> = flow {
            emit(Response.Error(ResponseError("unused")))
        }

        override fun <T : Any, RequestBody : Any> put(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>?,
            requestBody: RequestBody?,
            mediaType: String?,
            headers: Map<String, String>?,
        ): Flow<LoadResponse<T, ResponseError>> = flow {
            emit(Response.Error(ResponseError("unused")))
        }

        override fun <T : Any, RequestBody : Any> patch(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>?,
            requestBody: RequestBody?,
            mediaType: String?,
            headers: Map<String, String>?,
            requireSuccessful: Boolean,
        ): Flow<LoadResponse<T, ResponseError>> {
            lastMethod = "PATCH"
            lastUrl = url
            lastHeaders = headers
            lastRequireSuccessful = requireSuccessful
            lastBody = requestBody
            return flow { emit(Response.Error(ResponseError("unused"))) }
        }

        override fun <T : Any, RequestBody : Any> post(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>,
            requestBody: RequestBody,
            mediaType: String?,
            headers: Map<String, String>?,
            accept400AsSuccess: Boolean,
        ): Flow<LoadResponse<T, ResponseError>> = flow {
            emit(Response.Error(ResponseError("unused")))
        }

        override fun <T : Any, RequestBody : Any> postList(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>,
            requestBody: RequestBody,
            mediaType: String?,
            headers: Map<String, String>?,
        ): Flow<LoadResponse<List<T>, ResponseError>> = flow {
            emit(Response.Error(ResponseError("unused")))
        }

        override fun <T : Any, RequestBody : Any> delete(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>?,
            requestBody: RequestBody?,
            mediaType: String?,
            headers: Map<String, String>?,
            requireSuccessful: Boolean,
        ): Flow<LoadResponse<T, ResponseError>> {
            lastMethod = "DELETE"
            lastUrl = url
            lastHeaders = headers
            lastRequireSuccessful = requireSuccessful
            return flow { emit(Response.Error(ResponseError("unused"))) }
        }

        override suspend fun <T : Any> call(
            responseJsonClass: Class<T>,
            request: Request,
            useExtendedNetworkCallClient: Boolean,
            accept400AsSuccess: Boolean,
            requireSuccessful: Boolean,
        ): T = error("unused")

        override suspend fun <T : Any> callList(
            responseJsonClass: Class<T>,
            request: Request,
            useExtendedNetworkCallClient: Boolean,
        ): List<T> = error("unused")
    }

    @Test
    fun `getFeatures percent-encodes query values and requires success`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.getFeatures("ws id/&", 2, "token-1")

        assertEquals("GET", networkCall.lastMethod)
        assertTrue(networkCall.lastRequireSuccessful == true)
        val expectedWorkspace = URLEncoder.encode("ws id/&", Charsets.UTF_8.name())
        val expectedPage = URLEncoder.encode("2", Charsets.UTF_8.name())
        assertEquals(
            "https://hive.sphinx.chat/api/features?workspaceId=$expectedWorkspace&limit=20&page=$expectedPage",
            networkCall.lastUrl
        )
        assertEquals("Bearer token-1", networkCall.lastHeaders!!["Authorization"])
        assertFalse(networkCall.lastUrl!!.contains("ws id"))
    }

    @Test
    fun `updateFeature encodes featureId as path segment`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)
        val patch = HiveFeaturePatchDto(status = "PLANNED")

        query.updateFeature("feat/1+x", patch, "token-2")

        assertEquals("PATCH", networkCall.lastMethod)
        assertTrue(networkCall.lastRequireSuccessful == true)
        assertEquals(
            "https://hive.sphinx.chat/api/features/${NetworkQueryHiveImpl.encodePathSegment("feat/1+x")}",
            networkCall.lastUrl
        )
        assertEquals("Bearer token-2", networkCall.lastHeaders!!["Authorization"])
        assertEquals("application/json", networkCall.lastHeaders!!["Content-Type"])
        assertEquals(patch, networkCall.lastBody)
    }

    @Test
    fun `deleteFeature encodes featureId as path segment`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.deleteFeature("feat 1", "token-3")

        assertEquals("DELETE", networkCall.lastMethod)
        assertTrue(networkCall.lastRequireSuccessful == true)
        assertEquals(
            "https://hive.sphinx.chat/api/features/${NetworkQueryHiveImpl.encodePathSegment("feat 1")}",
            networkCall.lastUrl
        )
        assertEquals("Bearer token-3", networkCall.lastHeaders!!["Authorization"])
    }
}
