package com.retainiq.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Lightweight health-check controller for load-balancer and Kubernetes liveness probes.
 *
 * Exposed at `/health` without authentication.
 */
@RestController
class HealthController {

    /**
     * Returns a simple JSON status object with the service name and current timestamp.
     *
     * @return map with keys `status`, `service`, and `timestamp`
     */
    @GetMapping("/health")
    fun health() = mapOf(
        "status" to "UP",
        "service" to "retainiq",
        "timestamp" to Instant.now().toString()
    )
}
