package chat.sphinx.wrapper_common.lsat

import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.lightning.LightningPaymentRequest

data class Lsat(
    val id: LsatIdentifier,
    val macaroon: Macaroon,
    val paymentRequest: LightningPaymentRequest?,
    val issuer: LsatIssuer?,
    val metaData: LsatMetaData?,
    val paths: LsatPaths?,
    val preimage: LsatPreImage?,
    val status: LsatStatus,
    val createdAt: DateTime
)