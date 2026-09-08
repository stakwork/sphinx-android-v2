package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_call.NetworkCall
import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.concept_network_query_hive.model.WorkspaceImageDto
import chat.sphinx.concept_network_query_hive.model.WorkspacesListDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow
import java.net.URLEncoder

class NetworkQueryHiveImpl(
    private val networkCall: NetworkCall
) : NetworkQueryHive() {

    companion object {
        const val HIVE_BASE_URL = "https://hive.sphinx.chat/api"
        const val ENDPOINT_AUTH = "/auth/sphinx/token"
        const val ENDPOINT_WORKSPACES = "/workspaces"

        /**
         * Percent-encode [segment] as a URL path segment.
         *
         * [URLEncoder] emits `+` for spaces (form encoding), which is invalid in a
         * path — rewrite those to `%20`.
         */
        fun encodePathSegment(segment: String): String =
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
    }

    override fun authenticateWithHive(
        token: String,
        pubkey: String,
        timestamp: Long
    ): Flow<LoadResponse<HiveAuthenticationTokenDto, ResponseError>> =
        networkCall.post(
            url = HIVE_BASE_URL + ENDPOINT_AUTH,
            responseJsonClass = HiveAuthenticationTokenDto::class.java,
            requestBodyJsonClass = HiveAuthRequestDto::class.java,
            requestBody = HiveAuthRequestDto(token, pubkey, timestamp)
        )

    override fun getWorkspaces(
        authToken: String
    ): Flow<LoadResponse<WorkspacesListDto, ResponseError>> =
        networkCall.get(
            url = HIVE_BASE_URL + ENDPOINT_WORKSPACES,
            responseJsonClass = WorkspacesListDto::class.java,
            headers = mapOf("Authorization" to "Bearer $authToken")
        )

    override fun getWorkspaceImage(
        slug: String,
        authToken: String
    ): Flow<LoadResponse<WorkspaceImageDto, ResponseError>> =
        networkCall.get(
            url = HIVE_BASE_URL + "/workspaces/" + encodePathSegment(slug) + "/image",
            responseJsonClass = WorkspaceImageDto::class.java,
            headers = mapOf("Authorization" to "Bearer $authToken")
        )
}
