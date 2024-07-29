package chat.sphinx.feature_coredb.adapters.lsp

import chat.sphinx.wrapper_common.lsp.LsatIdentifier
import chat.sphinx.wrapper_common.lsp.LsatIssuer
import chat.sphinx.wrapper_common.lsp.LsatMetaData
import chat.sphinx.wrapper_common.lsp.LsatPaths
import chat.sphinx.wrapper_common.lsp.LspPaymentRequest
import chat.sphinx.wrapper_common.lsp.LsatPreImage
import chat.sphinx.wrapper_common.lsp.LsatStatus
import chat.sphinx.wrapper_common.lsp.Macaroon
import chat.sphinx.wrapper_common.lsp.toLsatStatus
import com.squareup.sqldelight.ColumnAdapter

internal class LsatIdentifierAdapter: ColumnAdapter<LsatIdentifier, String> {
    override fun decode(databaseValue: String): LsatIdentifier {
        return LsatIdentifier(databaseValue)
    }

    override fun encode(value: LsatIdentifier): String {
        return value.value
    }
}

internal class MacaroonAdapter: ColumnAdapter<Macaroon, String> {
    override fun decode(databaseValue: String): Macaroon {
        return Macaroon(databaseValue)
    }

    override fun encode(value: Macaroon): String {
        return value.value
    }
}

internal class LsatPaymentRequestAdapter: ColumnAdapter<LspPaymentRequest, String> {
    override fun decode(databaseValue: String): LspPaymentRequest {
        return LspPaymentRequest(databaseValue)
    }

    override fun encode(value: LspPaymentRequest): String {
        return value.value
    }
}

internal class LsatIssuerAdapter: ColumnAdapter<LsatIssuer, String> {
    override fun decode(databaseValue: String): LsatIssuer {
        return LsatIssuer(databaseValue)
    }

    override fun encode(value: LsatIssuer): String {
        return value.value
    }
}

internal class LsatMetaDataAdapter: ColumnAdapter<LsatMetaData, String> {
    override fun decode(databaseValue: String): LsatMetaData {
        return LsatMetaData(databaseValue)
    }

    override fun encode(value: LsatMetaData): String {
        return value.value
    }
}

internal class LsatPathsAdapter: ColumnAdapter<LsatPaths, String> {
    override fun decode(databaseValue: String): LsatPaths {
        return LsatPaths(databaseValue)
    }

    override fun encode(value: LsatPaths): String {
        return value.value
    }
}

internal class LsatPreImageAdapter: ColumnAdapter<LsatPreImage, String> {
    override fun decode(databaseValue: String): LsatPreImage {
        return LsatPreImage(databaseValue)
    }

    override fun encode(value: LsatPreImage): String {
        return value.value
    }
}

internal class LsatStatusAdapter: ColumnAdapter<LsatStatus, Long> {
    override fun decode(databaseValue: Long): LsatStatus {
        return databaseValue.toInt().toLsatStatus()
    }

    override fun encode(value: LsatStatus): Long {
        return value.value.toLong()
    }
}

