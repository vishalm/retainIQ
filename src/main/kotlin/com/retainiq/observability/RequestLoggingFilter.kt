package com.retainiq.observability

import mu.KotlinLogging
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Adds structured logging context to every request for correlation in Loki.
 *
 * MDC fields set:
 * - `traceId`: from W3C traceparent header or generated UUID
 * - `tenantId`: from X-Tenant-ID header
 * - `requestPath`: the URI path
 * - `method`: HTTP method
 *
 * Also logs request start/completion with latency.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val startTime = System.currentTimeMillis()
        val request = exchange.request
        val path = request.path.value()

        // Skip actuator/health noise
        if (path.startsWith("/actuator") || path == "/health") {
            return chain.filter(exchange)
        }

        val traceId = request.headers.getFirst("traceparent")
            ?.split("-")?.getOrNull(1)
            ?: UUID.randomUUID().toString().replace("-", "").take(32)
        val tenantId = request.headers.getFirst("X-Tenant-ID") ?: "unknown"

        return chain.filter(exchange)
            .contextWrite { ctx ->
                MDC.put("traceId", traceId)
                MDC.put("tenantId", tenantId)
                MDC.put("requestPath", path)
                MDC.put("method", request.method.name())
                ctx
            }
            .doOnSuccess {
                val latency = System.currentTimeMillis() - startTime
                val status = exchange.response.statusCode?.value() ?: 0
                logger.info { "$status ${request.method.name()} $path ${latency}ms tenant=$tenantId trace=$traceId" }
            }
            .doOnError { err ->
                val latency = System.currentTimeMillis() - startTime
                logger.error(err) { "ERROR ${request.method.name()} $path ${latency}ms tenant=$tenantId trace=$traceId" }
            }
            .doFinally {
                MDC.clear()
            }
    }
}
