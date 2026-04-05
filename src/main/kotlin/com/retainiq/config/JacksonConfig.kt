package com.retainiq.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Jackson [ObjectMapper] configuration for the RetainIQ API.
 *
 * Configures:
 * - Kotlin module for data class support
 * - JavaTime module for `Instant` / `LocalDate` serialization as ISO-8601 strings
 * - Lenient deserialization (unknown properties are ignored)
 */
@Configuration
class JacksonConfig {
    /**
     * Creates and configures the application-wide [ObjectMapper].
     *
     * @return configured [ObjectMapper]
     */
    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(kotlinModule())
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }
    }
}
