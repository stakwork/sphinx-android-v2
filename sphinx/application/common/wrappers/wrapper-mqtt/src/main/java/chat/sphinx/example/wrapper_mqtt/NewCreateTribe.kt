package chat.sphinx.example.wrapper_mqtt

import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.lang.IllegalArgumentException

@JsonClass(generateAdapter = true)
data class NewCreateTribe(
    val pubkey: String?,
    val route_hint: String?,
    val name: String,
    val description: String,
    val tags: List<String>,
    val img: String?,
    val price_per_message: Long?,
    val price_to_join: Long?,
    val escrow_amount: Long?,
    val escrow_millis: Long?,
    val unlisted: Boolean?,
    val private: Boolean?,
    val app_url: String?,
    val second_brain_url: String?,
    val feed_url: String?,
    val feed_type: Int?,
    val created: Long?,
    val updated: Long?,
    val member_count: Int?,
    val last_active: Long?,
    val owner_alias: String
) {
    fun toJson(): String {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val jsonAdapter = moshi.adapter(NewCreateTribe::class.java)
        return jsonAdapter.toJson(this)
    }

    fun getPricePerMessageInSats(): Long {
        return if (price_per_message == 0L || price_per_message == null ) 0L else price_per_message / 1000
    }

    fun getPriceToJoinInSats(): Long {
        return if (price_to_join == 0L || price_to_join == null) 0L else price_to_join / 1000
    }

    fun getEscrowAmountInSats(): Long {
        return if (escrow_amount == 0L || escrow_amount == null) 0L else escrow_amount / 1000
    }

    companion object {
        @Throws(JsonDataException::class, IllegalArgumentException::class)
        fun String.toNewCreateTribe(moshi: Moshi): NewCreateTribe {
            val adapter = moshi.adapter(NewCreateTribe::class.java)
            return adapter.fromJson(this) ?: throw IllegalArgumentException("Invalid JSON for NewCreateTribe")
        }
    }

}


