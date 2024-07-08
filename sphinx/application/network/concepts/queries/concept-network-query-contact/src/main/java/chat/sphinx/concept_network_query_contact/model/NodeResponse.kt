package chat.sphinx.concept_network_query_contact.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Channel(
    val short_channel_id: Long,
    val cltv_expiry_delta: Int,
    val fee_base_msat: Int,
    val fee_proportional_millionths: Int,
    val spendable_msat: Long,
    val receivable_msat: Long
)

@JsonClass(generateAdapter = true)
data class Peer(
    val pubkey: String,
    val channels: List<Channel>
)

@JsonClass(generateAdapter = true)
data class NodeResponse(
    val pubkey: String,
    val peers: List<Peer>
)