package chat.sphinx.concept_network_query_chat.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

@JsonClass(generateAdapter = true)
data class NewTribeDto(
    val pubkey: String,
    val route_hint: String,
    val name: String,
    val description: String?,
    val tags: Array<String> = arrayOf(),
    val img: String?,
    val owner_alias: String?,
    val price_per_message: Long = 0,
    val price_to_join: Long = 0,
    val escrow_amount: Long = 0,
    val escrow_millis: Long = 0,
    val unlisted: Boolean?,
    val private: Boolean?,
    val created: Long?,
    val updated: Long?,
    val member_count: Int?,
    val last_active: Int?,
    val unique_name: String?,
    val pin: String?,
    val app_url: String?,
    val feed_url: String?,
) {

    var amount: Long? = null
    var host: String? = null
    var uuid: String? = null

    var joined: Boolean? = null

    @Json(name = "my_alias")
    var myAlias: String? = null

    @Transient
    var profileImgFile: File? = null

    fun setProfileImageFile(img: File?) {
        this.profileImgFile?.let {
            try {
                it.delete()
            } catch (e: Exception) {
            }
        }
        this.profileImgFile = img
    }


    fun set(
        host: String?,
        tribePubKey: String,
    ) {
        this.host = host
        this.uuid = tribePubKey
    }

    fun toJsonString(): String {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val jsonAdapter = moshi.adapter(NewTribeDto::class.java)
        return jsonAdapter.toJson(this)
    }

}

fun Long.escrowMillisToHours(): Long {
    if (this == 0L) {
        return 0L
    }
    return this / (1000 * 60 * 60)
}