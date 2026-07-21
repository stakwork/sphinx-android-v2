package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveAuthTokenDto(
    // Nullable so a missing/null JSON field is caught explicitly downstream
    // rather than silently becoming the string "null".
    @Json(name = "token") val token: String?,
)
