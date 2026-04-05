package com.retainiq.api

import com.retainiq.api.dto.*
import com.retainiq.service.DecisionService
import com.retainiq.service.pipeline.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Tests the decision API contract by verifying response structure
 * from the pipeline components directly (without full Spring context).
 */
class DecisionControllerTest {

    private val assembler = ResponseAssembler()

    @Test
    fun `decide response has correct structure with offers`() {
        val decisionId = UUID.randomUUID()
        val response = assembler.assemble(
            decisionId = decisionId,
            profile = com.retainiq.domain.SubscriberProfile(
                "hash", "postpaid", 730, 150.0, 90, 60,
                -10.0, -5.0, false, 0, 0, 2, false, false, false
            ),
            churnResult = com.retainiq.domain.ChurnResult(
                0.73, com.retainiq.domain.ChurnBand.HIGH,
                listOf(com.retainiq.domain.RiskFactor("usage", 0.3))
            ),
            rankedOffers = listOf(
                com.retainiq.domain.RankedOffer(
                    1, "VAS-STREAM-PLUS", "StreamPlus", 0.68, 12.5, 0.8,
                    "Try StreamPlus", "https://activate.retainiq.com/VAS-STREAM-PLUS",
                    com.retainiq.domain.OfferRegulatory(false, null, null)
                )
            ),
            degraded = false,
            confidence = com.retainiq.domain.Confidence.HIGH,
            latencyMs = 87
        )

        assertEquals(decisionId, response.decisionId)
        assertEquals("present_offers", response.action)
        assertEquals("HIGH", response.confidence)
        assertEquals(1, response.offers.size)
        assertEquals("VAS-STREAM-PLUS", response.offers[0].sku)
        assertEquals(0.73, response.subscriber.churnScore)
        assertEquals("HIGH", response.subscriber.churnBand)
        assertFalse(response.degraded)
        assertEquals(87, response.latencyMs)
    }

    @Test
    fun `decide response escalates on CRITICAL churn with single offer`() {
        val response = assembler.assemble(
            decisionId = UUID.randomUUID(),
            profile = com.retainiq.domain.SubscriberProfile(
                "hash", "postpaid", 100, 200.0, 10, 300,
                -45.0, -30.0, true, 15, 2, 6, true, true, true
            ),
            churnResult = com.retainiq.domain.ChurnResult(
                0.92, com.retainiq.domain.ChurnBand.CRITICAL,
                listOf(com.retainiq.domain.RiskFactor("competitive", 0.5))
            ),
            rankedOffers = listOf(
                com.retainiq.domain.RankedOffer(1, "VAS-DATA-BOOST", "Data Boost", 0.35, 8.0, 0.5, null, null, null)
            ),
            degraded = true,
            confidence = com.retainiq.domain.Confidence.LOW,
            latencyMs = 180
        )

        assertEquals("escalate", response.action)
        assertTrue(response.degraded)
        assertTrue(response.fallback)
        assertEquals("LOW", response.confidence)
    }
}
