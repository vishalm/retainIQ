package com.retainiq.service.pipeline

import com.retainiq.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OfferRankerTest {

    private val ranker = OfferRanker(alpha = 0.45, beta = 0.30, gamma = 0.15, delta = 0.10)

    private val testProducts = listOf(
        VasProduct("SKU-A", "Product A", null, "streaming", 15.0,
            listOf(Market.UAE), emptyList(), emptyList(), emptyList(), emptyList(),
            ProductRegulatory(false, mapOf("AE" to "Test disclosure"), null), true),
        VasProduct("SKU-B", "Product B", null, "data", 8.0,
            listOf(Market.UAE), emptyList(), emptyList(), emptyList(), emptyList(),
            ProductRegulatory(false, emptyMap(), null), true),
        VasProduct("SKU-C", "Product C", null, "loyalty", 5.0,
            listOf(Market.UAE), emptyList(), emptyList(), emptyList(), emptyList(),
            ProductRegulatory(true, mapOf("AE" to "Consent required"), 24), true)
    )

    private val highChurn = ChurnResult(0.75, ChurnBand.HIGH, listOf(RiskFactor("usage", 0.3)))

    private val profile = SubscriberProfile(
        "hash", "postpaid", 730, 150.0, 90, 60,
        -10.0, -5.0, false, 0, 0, 2, false, false, false
    )

    @Test
    fun `ranks offers by composite score`() {
        val ranked = ranker.rank(testProducts, highChurn, profile, Channel.APP, 3)
        assertEquals(3, ranked.size)
        assertEquals(1, ranked[0].rank)
        assertEquals(2, ranked[1].rank)
        assertTrue(ranked[0].score >= ranked[1].score)
        assertTrue(ranked[1].score >= ranked[2].score)
    }

    @Test
    fun `respects maxOffers limit`() {
        val ranked = ranker.rank(testProducts, highChurn, profile, Channel.APP, 1)
        assertEquals(1, ranked.size)
    }

    @Test
    fun `empty candidates returns empty list`() {
        val ranked = ranker.rank(emptyList(), highChurn, profile, Channel.APP, 3)
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `generates script hints`() {
        val ranked = ranker.rank(testProducts, highChurn, profile, Channel.APP, 3)
        assertNotNull(ranked[0].scriptHint)
        assertTrue(ranked[0].scriptHint!!.contains("Product A") || ranked[0].scriptHint!!.isNotEmpty())
    }

    @Test
    fun `includes regulatory info in ranked offers`() {
        val ranked = ranker.rank(testProducts, highChurn, profile, Channel.APP, 3)
        val consentOffer = ranked.find { it.sku == "SKU-C" }
        assertNotNull(consentOffer?.regulatory)
        assertTrue(consentOffer!!.regulatory!!.consentRequired)
    }
}
