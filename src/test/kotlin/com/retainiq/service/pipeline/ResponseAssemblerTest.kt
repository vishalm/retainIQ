package com.retainiq.service.pipeline

import com.retainiq.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class ResponseAssemblerTest {

    private val assembler = ResponseAssembler()

    @Test
    fun `assembles present_offers action when offers exist`() {
        val response = assembler.assemble(
            decisionId = UUID.randomUUID(),
            profile = SubscriberProfile("h", "postpaid", 365, 100.0, null, null, 0.0, 0.0, false, 0, 0, 0, false, false, false),
            churnResult = ChurnResult(0.5, ChurnBand.MEDIUM, emptyList()),
            rankedOffers = listOf(
                RankedOffer(1, "SKU-1", "Test", 0.6, 10.0, 0.8, "hint", "link", null)
            ),
            degraded = false,
            confidence = Confidence.HIGH,
            latencyMs = 50
        )
        assertEquals("present_offers", response.action)
        assertEquals(1, response.offers.size)
        assertFalse(response.degraded)
        assertEquals("HIGH", response.confidence)
    }

    @Test
    fun `assembles no_action when no offers`() {
        val response = assembler.assemble(
            decisionId = UUID.randomUUID(),
            profile = SubscriberProfile("h", "postpaid", 365, 100.0, null, null, 0.0, 0.0, false, 0, 0, 0, false, false, false),
            churnResult = ChurnResult(0.5, ChurnBand.MEDIUM, emptyList()),
            rankedOffers = emptyList(),
            degraded = false,
            confidence = Confidence.HIGH,
            latencyMs = 50
        )
        assertEquals("no_action", response.action)
    }

    @Test
    fun `escalates on CRITICAL churn with few offers`() {
        val response = assembler.assemble(
            decisionId = UUID.randomUUID(),
            profile = SubscriberProfile("h", "postpaid", 365, 100.0, null, null, 0.0, 0.0, false, 0, 0, 0, false, false, false),
            churnResult = ChurnResult(0.9, ChurnBand.CRITICAL, emptyList()),
            rankedOffers = listOf(
                RankedOffer(1, "SKU-1", "Test", 0.3, 10.0, 0.5, null, null, null)
            ),
            degraded = true,
            confidence = Confidence.LOW,
            latencyMs = 180
        )
        assertEquals("escalate", response.action)
        assertTrue(response.degraded)
        assertTrue(response.fallback)
    }
}
