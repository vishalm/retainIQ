package com.retainiq.observability

import org.springframework.context.annotation.Configuration

/**
 * Tracing configuration for distributed trace propagation.
 *
 * Integrates with OpenTelemetry via Micrometer Tracing bridge.
 * Trace context (W3C traceparent) is propagated across HTTP calls
 * and Kafka messages for end-to-end visibility.
 *
 * Spring Boot auto-configures the Micrometer-to-OTel bridge when
 * `micrometer-tracing-bridge-otel` is on the classpath. Explicit
 * metrics instrumentation is handled by [RetainIQMetrics].
 */
@Configuration
class TracingConfig
