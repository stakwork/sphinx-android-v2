package chat.sphinx.example.wrapper_mqtt

import chat.sphinx.wrapper_common.lightning.Bolt11
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_common.lightning.Sat
import chat.sphinx.wrapper_common.lightning.toLightningNodePubKey
import chat.sphinx.wrapper_common.lightning.toSat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class InvoiceBolt11(
    val value: Long?,
    val payment_hash: String?,
    val pubkey: String?,
    val description: String?,
    val expiry: Long?
) {

    companion object {
        @Throws(JsonDataException::class, IllegalArgumentException::class)
        fun String.toInvoiceBolt11(moshi: Moshi): InvoiceBolt11 {
            val adapter = moshi.adapter(InvoiceBolt11::class.java)
            return adapter.fromJson(this) ?: throw IllegalArgumentException("Invalid JSON for InvoiceBolt11")
        }
    }

    fun isExpired(currentTime: Long): Boolean {
        return expiry?.let { it < currentTime } == true
    }

    fun getMilliSatsAmount(): Sat? {
        value?.let { amount ->
            return amount.toSat()
        }
        return null
    }

    fun getSatsAmount(): Sat? {
        value?.let { amount ->
            return amount.div(1000).toSat()
        }
        return null
    }

    fun getMemo(): String {
        description?.let {
            return it
        }
        return ""
    }

    fun getExpiryTime(): Long? {
        expiry?.let {
            return it
        }
        return null
    }

    fun getPubKey(): LightningNodePubKey? {
        pubkey?.toLightningNodePubKey()?.let { nnPubKey ->
            return nnPubKey
        }
        return null
    }
}
