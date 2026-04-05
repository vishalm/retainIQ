package com.retainiq.service.pipeline

import com.retainiq.api.dto.SignalsDto
import com.retainiq.domain.SubscriberProfile
import com.retainiq.cache.SubscriberCacheService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

/**
 * Stage 1 of the decisioning pipeline — Signal Enrichment.
 *
 * Enriches a raw decision request with a full [SubscriberProfile] by:
 * 1. Looking up the subscriber in the Redis cache (keyed by HMAC hash of MSISDN)
 * 2. On cache miss, building a profile from the inline signals (production: BSS fetch)
 * 3. Merging any real-time signals (intent, contact count) into the cached profile
 *
 * On failure, [fallbackProfile] returns a synthetic profile with conservative defaults
 * so the pipeline can continue in degraded mode.
 *
 * **Latency budget: < 20 ms**
 *
 * @property subscriberCache Redis-backed subscriber profile cache
 */
@Component
class SignalEnricher(
    private val subscriberCache: SubscriberCacheService
) {
    /**
     * Enriches the subscriber profile from cache, merging real-time signals.
     *
     * @param tenantId owning tenant
     * @param subscriberId raw subscriber identifier (MSISDN or alias)
     * @param signals optional real-time signals from the channel
     * @return enriched [SubscriberProfile]
     */
    suspend fun enrich(tenantId: UUID, subscriberId: String, signals: SignalsDto?): SubscriberProfile {
        val subscriberHash = hmacHash(subscriberId)

        // Try cache first, then BSS (simulated with cache miss → default profile)
        val cached = subscriberCache.get(tenantId, subscriberHash)
        if (cached != null) {
            logger.debug { "Cache hit for subscriber $subscriberHash" }
            return mergeSignals(cached, signals)
        }

        logger.debug { "Cache miss for subscriber $subscriberHash, building from signals" }
        // In production: fetch from BSS adapter here, then cache
        val profile = buildProfileFromSignals(subscriberHash, signals)
        subscriberCache.put(tenantId, subscriberHash, profile)
        return profile
    }

    /**
     * Returns a synthetic profile with conservative defaults when enrichment fails.
     *
     * @param subscriberId raw subscriber identifier
     * @param signals optional real-time signals
     * @return a safe fallback [SubscriberProfile]
     */
    fun fallbackProfile(subscriberId: String, signals: SignalsDto?): SubscriberProfile {
        val hash = hmacHash(subscriberId)
        return buildProfileFromSignals(hash, signals)
    }

    private fun mergeSignals(profile: SubscriberProfile, signals: SignalsDto?): SubscriberProfile {
        if (signals == null) return profile
        return profile.copy(
            contacts30d = signals.priorContacts30d ?: profile.contacts30d,
            priorChurnIntent = when (signals.intent) {
                "cancel" -> true
                else -> profile.priorChurnIntent
            },
            competitorMention = when (signals.intent) {
                "competitor" -> true
                else -> profile.competitorMention
            }
        )
    }

    private fun buildProfileFromSignals(hash: String, signals: SignalsDto?): SubscriberProfile {
        return SubscriberProfile(
            subscriberHash = hash,
            segment = "unknown",
            tenureDays = 365,
            arpu = 100.0,
            contractDaysRemaining = null,
            lastUpgradeDays = null,
            dataUsageDelta7d = 0.0,
            voiceUsageDelta7d = 0.0,
            billShock = false,
            paymentDelayDays = 0,
            disputeCount90d = 0,
            contacts30d = signals?.priorContacts30d ?: 0,
            priorChurnIntent = signals?.intent == "cancel",
            competitorMention = false,
            portInquiry = false
        )
    }

    private fun hmacHash(input: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("retainiq-subscriber-hash".toByteArray(), "HmacSHA256"))
        return mac.doFinal(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
