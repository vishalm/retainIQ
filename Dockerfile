# ============================================================
# RetainIQ — Production Dockerfile
# Multi-stage build: deps → build → runtime
# Image: eclipse-temurin:21, Alpine-based, non-root, ZGC
# ============================================================

# Stage 1: Cache Gradle dependencies (rarely changes)
FROM eclipse-temurin:21-jdk-alpine AS deps
WORKDIR /app

# Install required build tools
RUN apk add --no-cache bash

# Copy only dependency-related files for Docker layer caching
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew

# Download all dependencies (this layer is cached unless build files change)
RUN ./gradlew dependencies --no-daemon --console=plain

# Stage 2: Build the application
FROM deps AS build
WORKDIR /app

# Copy source code
COPY src src

# Build fat JAR, skip tests (tests run in CI separately)
RUN ./gradlew bootJar --no-daemon --console=plain -x test -x dokkaHtml

# Stage 3: Production runtime (minimal image)
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Labels for OCI image spec
LABEL org.opencontainers.image.title="RetainIQ"
LABEL org.opencontainers.image.description="Real-time VAS offer decisioning for telecom operators"
LABEL org.opencontainers.image.vendor="RetainIQ"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.source="https://github.com/retainiq/retainiq"

# Install curl for healthchecks and tini for PID 1 signal handling
RUN apk add --no-cache curl tini

# Create non-root user
RUN addgroup -g 1001 -S retainiq && \
    adduser -u 1001 -S retainiq -G retainiq -h /app

# Copy the fat JAR
COPY --from=build --chown=retainiq:retainiq /app/build/libs/*.jar app.jar

# Create directories for logs and temp files
RUN mkdir -p /app/logs /tmp/retainiq && \
    chown -R retainiq:retainiq /app/logs /tmp/retainiq

USER retainiq

# JVM Configuration
# - ZGC: low-latency GC ideal for the <200ms p99 target
# - MaxRAMPercentage: respect container memory limits
# - ExitOnOutOfMemoryError: let K8s restart on OOM rather than limping
# - UseStringDeduplication: reduce memory for repeated offer/SKU strings
# - +UseContainerSupport: auto-detect cgroup limits
ENV JAVA_OPTS="\
    -XX:+UseZGC \
    -XX:+ZGenerational \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+UseStringDeduplication \
    -XX:+UseContainerSupport \
    -Djava.io.tmpdir=/tmp/retainiq \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=UTC \
    -Dreactor.netty.ioWorkerCount=4"

# OpenTelemetry agent (mounted or baked in for tracing)
ENV OTEL_SERVICE_NAME="retainiq"
ENV OTEL_EXPORTER_OTLP_ENDPOINT="http://tempo:4317"
ENV OTEL_METRICS_EXPORTER="none"
ENV OTEL_LOGS_EXPORTER="none"
ENV OTEL_TRACES_SAMPLER="parentbased_traceidratio"
ENV OTEL_TRACES_SAMPLER_ARG="0.1"

EXPOSE 8080

# Healthcheck: use curl for more reliable checks than wget
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=3 \
    CMD curl -sf http://localhost:8080/health || exit 1

# Use tini as PID 1 for proper signal handling
ENTRYPOINT ["tini", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
