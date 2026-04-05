package com.retainiq.service.pipeline

import com.retainiq.domain.ChurnBand
import com.retainiq.domain.SubscriberProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChurnScorerTest {

    private val scorer = ChurnScorer()

    private fun baseProfile() = SubscriberProfile(
        subscriberHash = "test-hash",
        segment = "postpaid",
        tenureDays = 730,
        arpu = 150.0,
        contractDaysRemaining = 180,
        lastUpgradeDays = 90,
        dataUsageDelta7d = 0.0,
        voiceUsageDelta7d = 0.0,
        billShock = false,
        paymentDelayDays = 0,
        disputeCount90d = 0,
        contacts30d = 0,
        priorChurnIntent = false,
        competitorMention = false,
        portInquiry = false
    )

    @Test
    fun `stable subscriber scores LOW churn`() {
        val result = scorer.score(baseProfile())
        assertEquals(ChurnBand.LOW, result.band)
        assertTrue(result.score < 0.3)
        assertEquals(3, result.topRiskFactors.size)
    }

    @Test
    fun `subscriber with churn intent and competitor mention scores HIGH or CRITICAL`() {
        val profile = baseProfile().copy(
            priorChurnIntent = true,
            competitorMention = true,
            portInquiry = true,
            contacts30d = 5,
            dataUsageDelta7d = -40.0
        )
        val result = scorer.score(profile)
        assertTrue(result.band == ChurnBand.HIGH || result.band == ChurnBand.CRITICAL)
        assertTrue(result.score >= 0.6)
    }

    @Test
    fun `bill shock increases churn score`() {
        val normal = scorer.score(baseProfile())
        val shocked = scorer.score(baseProfile().copy(billShock = true, paymentDelayDays = 20))
        assertTrue(shocked.score > normal.score)
    }

    @Test
    fun `new subscriber with short tenure has higher risk`() {
        val veteran = scorer.score(baseProfile().copy(tenureDays = 1500))
        val newbie = scorer.score(baseProfile().copy(tenureDays = 60, contractDaysRemaining = 10))
        assertTrue(newbie.score > veteran.score)
    }

    @Test
    fun `usage decline increases churn score`() {
        val stable = scorer.score(baseProfile())
        val declining = scorer.score(baseProfile().copy(dataUsageDelta7d = -45.0, voiceUsageDelta7d = -30.0))
        assertTrue(declining.score > stable.score)
    }

    @Test
    fun `fallback score returns MEDIUM band`() {
        val result = scorer.fallbackScore(baseProfile())
        assertEquals(ChurnBand.MEDIUM, result.band)
        assertEquals(0.5, result.score)
    }
}
