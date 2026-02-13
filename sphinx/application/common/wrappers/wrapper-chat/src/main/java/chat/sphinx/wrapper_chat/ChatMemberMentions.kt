package chat.sphinx.wrapper_chat

import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.after
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.util.Date

fun String.toChatMemberMentionsOrNull(moshi: Moshi): ChatMemberMentions? =
    try {
        moshi.adapter(ChatMemberMentionsMoshi::class.java)
            .fromJson(this)
            ?.let { moshiData ->
                ChatMemberMentions(
                    moshiData.members.map { m -> 
                        ChatMemberMention(
                            alias = m.alias,
                            pictureUrl = m.pictureUrl,
                            colorKey = m.colorKey,
                            lastMessageTimestamp = DateTime(Date(m.lastMessageTimestamp))
                        )
                    }
                )
            }
    } catch (e: Exception) {
        null
    }

@Throws(AssertionError::class)
fun ChatMemberMentions.toJson(moshi: Moshi): String =
    moshi.adapter(ChatMemberMentionsMoshi::class.java)
        .toJson(
            ChatMemberMentionsMoshi(
                members = members.map { m ->
                    ChatMemberMentionMoshi(
                        alias = m.alias,
                        pictureUrl = m.pictureUrl,
                        colorKey = m.colorKey,
                        lastMessageTimestamp = m.lastMessageTimestamp.value.time
                    )
                }
            )
        )

data class ChatMemberMention(
    val alias: String,
    val pictureUrl: String,
    val colorKey: String,
    val lastMessageTimestamp: DateTime
)

data class ChatMemberMentions(
    val members: List<ChatMemberMention>
) {
    /**
     * Add a new member or update existing member's timestamp.
     * Matches primarily by alias to handle profile picture changes.
     * Profile picture can be null or empty.
     */
    fun addOrUpdateMember(
        alias: String,
        pictureUrl: String,
        colorKey: String,
        timestamp: DateTime
    ): ChatMemberMentions {
        // Match by alias only (primary identifier)
        val existingIndex = members.indexOfFirst { it.alias == alias }
        
        return if (existingIndex >= 0) {
            // Update existing member (may have new picture)
            val updatedMembers = members.toMutableList()
            updatedMembers[existingIndex] = ChatMemberMention(
                alias = alias,
                pictureUrl = pictureUrl,
                colorKey = colorKey,
                lastMessageTimestamp = timestamp
            )
            ChatMemberMentions(updatedMembers)
        } else {
            // Add new member
            ChatMemberMentions(members + ChatMemberMention(alias, pictureUrl, colorKey, timestamp))
        }
    }

    /**
     * Remove a member matching by alias only.
     */
    fun removeMember(alias: String, pictureUrl: String): ChatMemberMentions {
        return ChatMemberMentions(
            members.filter { it.alias != alias }
        )
    }

    /**
     * Filter members to only include those active within the last 3 months.
     */
    fun filterByThreeMonths(): ChatMemberMentions {
        val threeMonthsAgo = DateTime.getThreeMonthsAgo()
        return ChatMemberMentions(
            members.filter { it.lastMessageTimestamp.after(threeMonthsAgo) }
        )
    }

    /**
     * Match members whose alias starts with the query (case-insensitive).
     */
    fun matchAlias(query: String): List<ChatMemberMention> {
        val lowerQuery = query.lowercase()
        return members.filter { it.alias.lowercase().startsWith(lowerQuery) }
    }
}

@JsonClass(generateAdapter = true)
internal data class ChatMemberMentionsMoshi(
    val members: List<ChatMemberMentionMoshi>
)

@JsonClass(generateAdapter = true)
internal data class ChatMemberMentionMoshi(
    val alias: String,
    val pictureUrl: String,
    val colorKey: String,
    val lastMessageTimestamp: Long
)
