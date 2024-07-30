package chat.sphinx.feature_repository.mappers.lsat

import chat.sphinx.conceptcoredb.LsatDbo
import chat.sphinx.feature_repository.mappers.ClassMapper
import chat.sphinx.wrapper_common.lsat.Lsat
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import java.text.ParseException

internal class LsatDboPresenterMapper(
    dispatchers: CoroutineDispatchers
): ClassMapper<LsatDbo, Lsat>(dispatchers) {

    @Throws(
        IllegalArgumentException::class,
        ParseException::class
    )
    override suspend fun mapFrom(value: LsatDbo): Lsat {
        return Lsat(
            id = value.id,
            macaroon = value.macaroon,
            paymentRequest = value.payment_request,
            issuer = value.issuer,
            metaData = value.meta_data,
            paths = value.paths,
            preimage = value.preimage,
            status = value.status,
            createdAt = value.created_at
        )
    }

    override suspend fun mapTo(value: Lsat): LsatDbo {
        return LsatDbo(
            id = value.id,
            macaroon = value.macaroon,
            payment_request = value.paymentRequest,
            issuer = value.issuer,
            meta_data = value.metaData,
            paths = value.paths,
            preimage = value.preimage,
            status = value.status,
            created_at = value.createdAt
        )
    }
}
