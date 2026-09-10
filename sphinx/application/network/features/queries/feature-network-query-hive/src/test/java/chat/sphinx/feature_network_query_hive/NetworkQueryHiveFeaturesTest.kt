package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_call.NetworkCall
import chat.sphinx.concept_network_query_hive.model.HiveFeaturePatchDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskDuplicateDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskDependsOnPatchDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskPatchDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            requireSuccessful: Boolean,
        ): Flow<LoadResponse<T, ResponseError>> {
            lastMethod = "POST"
            lastUrl = url
            lastHeaders = headers
            lastRequireSuccessful = requireSuccessful
            lastBody = requestBody
            return flow { emit(Response.Error(ResponseError("unused"))) }
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

    @Test
    fun `getTasks omits includeArchived when false and percent-encodes query values`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.getTasks("ws id/&", 2, includeArchived = false, authToken = "token-4")

        assertEquals("GET", networkCall.lastMethod)
        assertTrue(networkCall.lastRequireSuccessful == true)
        val expectedWorkspace = URLEncoder.encode("ws id/&", Charsets.UTF_8.name())
        val expectedPage = URLEncoder.encode("2", Charsets.UTF_8.name())
        assertEquals(
            "https://hive.sphinx.chat/api/tasks?workspaceId=$expectedWorkspace&limit=20&page=$expectedPage",
            networkCall.lastUrl
        )
        assertFalse(networkCall.lastUrl!!.contains("includeArchived"))
        assertFalse(networkCall.lastUrl!!.contains("includeLatestMessage"))
        assertEquals("Bearer token-4", networkCall.lastHeaders!!["Authorization"])
    }

    @Test
    fun `getTasks appends includeArchived true only when archived`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.getTasks("ws-1", 1, includeArchived = true, authToken = "token-5")

        assertEquals("GET", networkCall.lastMethod)
        assertTrue(networkCall.lastRequireSuccessful == true)
        assertEquals(
            "https://hive.sphinx.chat/api/tasks?workspaceId=ws-1&limit=20&page=1&includeArchived=true",
            networkCall.lastUrl
        )
        assertFalse(networkCall.lastUrl!!.contains("includeArchived=false"))
        assertEquals("Bearer token-5", networkCall.lastHeaders!!["Authorization"])
    }

    @Test
    fun `startTask patches startWorkflow true and never sets status`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.startTask("task/1+x", "token-6")

        assertEquals("PATCH", networkCall.lastMethod)
        assertTrue(networkCall.lastRequireSuccessful == true)
        assertEquals(
            "https://hive.sphinx.chat/api/tasks/${NetworkQueryHiveImpl.encodePathSegment("task/1+x")}",
            networkCall.lastUrl
        )
        val body = networkCall.lastBody as HiveTaskPatchDto
        assertEquals(true, body.startWorkflow)
        assertNull(body.status)
        assertNull(body.retryWorkflow)
        assertEquals("Bearer token-6", networkCall.lastHeaders!!["Authorization"])
        assertEquals("application/json", networkCall.lastHeaders!!["Content-Type"])
    }

    @Test
    fun `retryTask patches retryWorkflow true and never sets status`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.retryTask("task-2", "token-7")

        assertEquals("PATCH", networkCall.lastMethod)
        val body = networkCall.lastBody as HiveTaskPatchDto
        assertEquals(true, body.retryWorkflow)
        assertNull(body.status)
        assertNull(body.startWorkflow)
        assertEquals(
            "https://hive.sphinx.chat/api/tasks/${NetworkQueryHiveImpl.encodePathSegment("task-2")}",
            networkCall.lastUrl
        )
    }

    @Test
    fun `updateTaskStatus patches status only`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.updateTaskStatus("task-3", "DONE", "token-8")

        val body = networkCall.lastBody as HiveTaskPatchDto
        assertEquals("DONE", body.status)
        assertNull(body.startWorkflow)
        assertNull(body.retryWorkflow)
        assertEquals("PATCH", networkCall.lastMethod)
        assertTrue(networkCall.lastRequireSuccessful == true)
    }

    @Test
    fun `setTaskArchived patches archived flag`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.setTaskArchived("task 4", true, "token-9")

        val body = networkCall.lastBody as HiveTaskPatchDto
        assertEquals(true, body.archived)
        assertEquals(
            "https://hive.sphinx.chat/api/tasks/${NetworkQueryHiveImpl.encodePathSegment("task 4")}",
            networkCall.lastUrl
        )
    }

    @Test
    fun `updateTaskFlags patches all three flags`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.updateTaskFlags(
            "task-5",
            autoMerge = true,
            runBuild = false,
            runTestSuite = true,
            authToken = "token-10",
        )

        val body = networkCall.lastBody as HiveTaskPatchDto
        assertEquals(true, body.autoMerge)
        assertEquals(false, body.runBuild)
        assertEquals(true, body.runTestSuite)
        assertNull(body.status)
    }

    @Test
    fun `duplicateTask posts to features tickets with requireSuccessful`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)
        val body = HiveTaskDuplicateDto(title = "Copy", priority = "LOW")

        query.duplicateTask("feat/1+x", body, "token-11")

        assertEquals("POST", networkCall.lastMethod)
        assertTrue(networkCall.lastRequireSuccessful == true)
        assertEquals(
            "https://hive.sphinx.chat/api/features/${NetworkQueryHiveImpl.encodePathSegment("feat/1+x")}/tickets",
            networkCall.lastUrl
        )
        assertEquals(body, networkCall.lastBody)
        assertEquals("Bearer token-11", networkCall.lastHeaders!!["Authorization"])
        assertEquals("application/json", networkCall.lastHeaders!!["Content-Type"])
    }

    @Test
    fun `updateTaskDependsOn patches tickets not tasks`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.updateTaskDependsOn("task/9", listOf("a", "b"), "token-12")

        assertEquals("PATCH", networkCall.lastMethod)
        assertTrue(networkCall.lastRequireSuccessful == true)
        assertEquals(
            "https://hive.sphinx.chat/api/tickets/${NetworkQueryHiveImpl.encodePathSegment("task/9")}",
            networkCall.lastUrl
        )
        val body = networkCall.lastBody as HiveTaskDependsOnPatchDto
        assertEquals(listOf("a", "b"), body.dependsOnTaskIds)
        assertFalse(networkCall.lastUrl!!.contains("/tasks/"))
        assertEquals("Bearer token-12", networkCall.lastHeaders!!["Authorization"])
        assertEquals("application/json", networkCall.lastHeaders!!["Content-Type"])
    }

    @Test
    fun `getPoolStatus encodes slug as path segment and requires success`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.getPoolStatus("ws slug/&+", "token-13")

        assertEquals("GET", networkCall.lastMethod)
        assertTrue(networkCall.lastRequireSuccessful == true)
        assertEquals(
            "https://hive.sphinx.chat/api/w/${NetworkQueryHiveImpl.encodePathSegment("ws slug/&+")}/pool/status",
            networkCall.lastUrl
        )
        assertEquals("Bearer token-13", networkCall.lastHeaders!!["Authorization"])
        assertFalse(networkCall.lastUrl!!.contains("ws slug"))
    }

    @Test
    fun `getBasicPods encodes slug as path segment and requires success`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.getBasicPods("ws slug/&+", "token-14")

        assertEquals("GET", networkCall.lastMethod)
        assertTrue(networkCall.lastRequireSuccessful == true)
        assertEquals(
            "https://hive.sphinx.chat/api/w/${NetworkQueryHiveImpl.encodePathSegment("ws slug/&+")}/pool/basic-workspaces",
            networkCall.lastUrl
        )
        assertEquals("Bearer token-14", networkCall.lastHeaders!!["Authorization"])
    }

    @Test
    fun `getFullPods encodes slug as path segment and requires success`() {
        val networkCall = RecordingNetworkCall()
        val query = NetworkQueryHiveImpl(networkCall)

        query.getFullPods("ws slug/&+", "token-15")

        assertEquals("GET", networkCall.lastMethod)
        assertTrue(networkCall.lastRequireSuccessful == true)
        assertEquals(
            "https://hive.sphinx.chat/api/w/${NetworkQueryHiveImpl.encodePathSegment("ws slug/&+")}/pool/workspaces",
            networkCall.lastUrl
        )
        assertEquals("Bearer token-15", networkCall.lastHeaders!!["Authorization"])
        assertFalse(networkCall.lastUrl!!.contains("basic-workspaces"))
    }
}
