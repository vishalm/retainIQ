package com.retainiq.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.retainiq.domain.SubscriberProfile
import com.retainiq.observability.RetainIQMetrics
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import kotlinx.coroutines.reactive.awaitFirstOrNull
import java.time.Duration
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Redis cache for subscriber profiles.
 *
 * Key pattern: `tenant:{tenantId}:subscriber:{subscriberHash}`.
 * Default TTL: 5 minutes (configurable via `retainiq.cache.subscriber-ttl-seconds`).
 *
 * All Redis operations are wrapped in try/catch to ensure graceful degradation on cache
 * failure -- the pipeline continues with a cache miss rather than throwing.
 *
 * @property redisTemplate reactive Redis string template
 * @property objectMapper Jackson mapper for JSON serialization
 * @property ttlSeconds cache entry time-to-live in seconds
 */
@Service
class SubscriberCacheService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${retainiq.cache.subscriber-ttl-seconds:300}") private val ttlSeconds: Long,
    private val metrics: RetainIQMetrics
) {
    private fun key(tenantId: UUID, subscriberHash: String) = "tenant:$tenantId:subscriber:$subscriberHash"

    // L1 in-process cache for hot subscribers (repeated calls during a session)
    private data class L1Entry(val profile: SubscriberProfile, val expiresAt: Long)
    private val l1 = java.util.concurrent.ConcurrentHashMap<String, L1Entry>()
    private val l1TtlMs = 30_000L // 30 seconds

    suspend fun get(tenantId: UUID, subscriberHash: String): SubscriberProfile? {
        val l1Key = "$tenantId:$subscriberHash"
        val cached = l1[l1Key]
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            metrics.cacheHit("subscriber_l1", tenantId.toString())
            return cached.profile
        }

        return try {
            val json = redisTemplate.opsForValue().get(key(tenantId, subscriberHash)).awaitFirstOrNull()
            if (json != null) {
                metrics.cacheHit("subscriber", tenantId.toString())
                val profile = objectMapper.readValue(json, SubscriberProfile::class.java)
                l1[l1Key] = L1Entry(profile, System.currentTimeMillis() + l1TtlMs)
                profile
            } else {
                metrics.cacheMiss("subscriber", tenantId.toString())
                null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Redis read failed for subscriber cache" }
            null
        }
    }

    /**
     * Stores a subscriber profile in Redis with the configured TTL.
     *
     * Silently logs and swallows Redis write failures.
     *
     * @param tenantId owning tenant
     * @param subscriberHash HMAC hash of the subscriber's MSISDN
     * @param profile the profile to cache
     */
    suspend fun put(tenantId: UUID, subscriberHash: String, profile: SubscriberProfile) {
        try {
            val json = objectMapper.writeValueAsString(profile)
            redisTemplate.opsForValue()
                .set(key(tenantId, subscriberHash), json, Duration.ofSeconds(ttlSeconds))
                .awaitFirstOrNull()
        } catch (e: Exception) {
            logger.warn(e) { "Redis write failed for subscriber cache" }
        }
    }
}
