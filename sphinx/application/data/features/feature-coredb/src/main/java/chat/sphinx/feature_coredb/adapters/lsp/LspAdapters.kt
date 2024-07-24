package chat.sphinx.feature_coredb.adapters.lsp

import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.lsp.LspIdentifier
import chat.sphinx.wrapper_common.lsp.LspIssuer
import chat.sphinx.wrapper_common.lsp.LspMetaData
import chat.sphinx.wrapper_common.lsp.LspPaths
import chat.sphinx.wrapper_common.lsp.LspPaymentRequest
import chat.sphinx.wrapper_common.lsp.LspPreImage
import chat.sphinx.wrapper_common.lsp.LspStatus
import chat.sphinx.wrapper_common.lsp.Macaroon
import chat.sphinx.wrapper_common.lsp.toLspStatus
import com.squareup.sqldelight.ColumnAdapter

internal class LspIdentifierAdapter: ColumnAdapter<LspIdentifier, String> {
    override fun decode(databaseValue: String): LspIdentifier {
        return LspIdentifier(databaseValue)
    }

    override fun encode(value: LspIdentifier): String {
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

internal class LspPaymentRequestAdapter: ColumnAdapter<LspPaymentRequest, String> {
    override fun decode(databaseValue: String): LspPaymentRequest {
        return LspPaymentRequest(databaseValue)
    }

    override fun encode(value: LspPaymentRequest): String {
        return value.value
    }
}

internal class LspIssuerAdapter: ColumnAdapter<LspIssuer, String> {
    override fun decode(databaseValue: String): LspIssuer {
        return LspIssuer(databaseValue)
    }

    override fun encode(value: LspIssuer): String {
        return value.value
    }
}

internal class LspMetaDataAdapter: ColumnAdapter<LspMetaData, String> {
    override fun decode(databaseValue: String): LspMetaData {
        return LspMetaData(databaseValue)
    }

    override fun encode(value: LspMetaData): String {
        return value.value
    }
}

internal class LspPathsAdapter: ColumnAdapter<LspPaths, String> {
    override fun decode(databaseValue: String): LspPaths {
        return LspPaths(databaseValue)
    }

    override fun encode(value: LspPaths): String {
        return value.value
    }
}

internal class LspPreImageAdapter: ColumnAdapter<LspPreImage, String> {
    override fun decode(databaseValue: String): LspPreImage {
        return LspPreImage(databaseValue)
    }

    override fun encode(value: LspPreImage): String {
        return value.value
    }
}

internal class LspStatusAdapter: ColumnAdapter<LspStatus, Long> {
    override fun decode(databaseValue: Long): LspStatus {
        return databaseValue.toInt().toLspStatus()
    }

    override fun encode(value: LspStatus): Long {
        return value.value.toLong()
    }
}

