package chat.sphinx.chat_common.ui.activity.call_activity

import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi

class ParticipantMetaData(
    val profilePictureUrl: String
)

@JsonClass(generateAdapter = true)
internal data class ParticipantMetaDataMoshi(
    val profilePictureUrl: String
)

@Suppress("NOTHING_TO_INLINE")
inline fun String.toParticipantMetaDataOrNull(moshi: Moshi): ParticipantMetaData? =
    try {
        this.toParticipantMetaData(moshi)
    } catch (e: Exception) {
        null
    }

@Throws(
    IllegalArgumentException::class,
    JsonDataException::class
)
fun String.toParticipantMetaData(moshi: Moshi): ParticipantMetaData =
    moshi.adapter(ParticipantMetaDataMoshi::class.java)
        .fromJson(this)
        ?.let {
            ParticipantMetaData(
                it.profilePictureUrl
            )
        }
        ?: throw IllegalArgumentException("Provided Json was invalid")