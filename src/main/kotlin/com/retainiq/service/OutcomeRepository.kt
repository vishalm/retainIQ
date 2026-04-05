package com.retainiq.service

import com.retainiq.domain.Outcome
import mu.KotlinLogging
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Persistence layer for [Outcome] feedback records.
 *
 * Outcomes are inserted into `platform.outcomes` using R2DBC. These records
 * close the attribution loop and feed the churn model retraining pipeline.
 * Retained for 24 months alongside the originating [Decision].
 *
 * @property databaseClient R2DBC database client
 */
@Repository
class OutcomeRepository(
    private val databaseClient: DatabaseClient
) {
    /**
     * Persists an outcome record to PostgreSQL.
     *
     * Errors are logged but do not propagate to the caller.
     *
     * @param tenantId owning tenant (for future partitioning)
     * @param outcome the outcome to persist
     */
    suspend fun save(tenantId: UUID, outcome: Outcome) {
        try {
            databaseClient.sql("""
                INSERT INTO platform.outcomes (id, decision_id, offer_sku, outcome, revenue_delta, churn_prevented, created_at)
                VALUES (:id, :decisionId, :offerSku, :outcome, :revenueDelta, :churnPrevented, :createdAt)
            """.trimIndent())
                .bind("id", outcome.id)
                .bind("decisionId", outcome.decisionId)
                .bind("offerSku", outcome.offerSku)
                .bind("outcome", outcome.result.name)
                .bind("revenueDelta", outcome.revenueDelta ?: 0.0)
                .bind("churnPrevented", outcome.churnPrevented ?: false)
                .bind("createdAt", outcome.createdAt)
                .fetch()
                .rowsUpdated()
                .subscribe()

            logger.debug { "Outcome ${outcome.id} persisted for decision ${outcome.decisionId}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to persist outcome ${outcome.id}" }
        }
    }
}
