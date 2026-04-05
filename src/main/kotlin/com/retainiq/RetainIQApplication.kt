/**
 * RetainIQ — Real-time VAS offer decisioning engine for telecom operators.
 *
 * This module provides a stateless API that sits between customer-facing channels
 * (Agentforce, Genesys, operator apps) and BSS/VAS backends. It scores churn risk,
 * selects compliant VAS offers that balance retention probability and margin, and
 * returns ranked results in under 200 ms — fast enough for live voice and chat.
 *
 * @see com.retainiq.service.DecisionService for the core 5-stage decisioning pipeline
 * @see com.retainiq.api.DecisionController for the REST API surface
 */
package com.retainiq

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot entry point for the RetainIQ decisioning engine.
 *
 * Enables auto-configuration for WebFlux, R2DBC (PostgreSQL), reactive Redis,
 * Spring Security, and Micrometer metrics.
 */
@SpringBootApplication
class RetainIQApplication

/**
 * Bootstraps the RetainIQ application on Netty.
 *
 * @param args command-line arguments forwarded to Spring Boot
 */
fun main(args: Array<String>) {
    runApplication<RetainIQApplication>(*args)
}
