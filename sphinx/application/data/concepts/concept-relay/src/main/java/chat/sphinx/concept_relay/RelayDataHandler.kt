package chat.sphinx.concept_relay

import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_relay.*
import chat.sphinx.wrapper_rsa.RsaPublicKey
import io.matthewnelson.crypto_common.clazzes.Password
import java.io.IOException

/**
 * Persists and retrieves Sphinx Relay data to device storage. Implementation
 * requires User to be logged in to work, otherwise `null` and `false` are always
 * returned.
 * */
abstract class RelayDataHandler {

    /**
     * Send `null` to clear the token from persistent storage
     * */
    abstract suspend fun persistAuthorizationToken(token: AuthorizationToken?): Boolean
    abstract suspend fun retrieveAuthorizationToken(): AuthorizationToken?

    /**
     * Persist the Hive JWT to encrypted storage. Send `null` to clear.
     * Empty string is rejected (not a valid JWT) and returns `false`.
     */
    abstract suspend fun persistHiveToken(token: String?): Boolean
    abstract suspend fun retrieveHiveToken(): String?

}

open class CustomException : IOException {

    var code: Int? = null

    constructor(message: String?, code: Int?): super(message) {
        this.code = code
    }

    companion object {
        const val serialVersionUID = 7818375828146090155L
    }
}
