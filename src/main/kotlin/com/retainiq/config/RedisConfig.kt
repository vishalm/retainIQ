package com.retainiq.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Spring configuration for reactive Redis.
 *
 * Provides a [ReactiveStringRedisTemplate] bean used by [SubscriberCacheService][com.retainiq.cache.SubscriberCacheService]
 * and [CatalogCacheService][com.retainiq.cache.CatalogCacheService] for subscriber profile and VAS catalog caching.
 */
@Configuration
class RedisConfig {
    /**
     * Creates a reactive Redis template using string serialization for both keys and values.
     *
     * @param factory the auto-configured reactive Redis connection factory
     * @return a [ReactiveStringRedisTemplate]
     */
    @Bean
    fun reactiveStringRedisTemplate(factory: ReactiveRedisConnectionFactory): ReactiveStringRedisTemplate {
        return ReactiveStringRedisTemplate(factory)
    }
}
