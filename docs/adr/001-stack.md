# ADR-001: Technology Stack Selection

**Date:** 2026-04-05
**Status:** Accepted

## Context

RetainIQ is a real-time telecom retention decisioning API targeting MENA operators (UAE, KSA primarily). The system must meet the following key requirements:

- **Latency:** < 200ms p99 end-to-end for the `/v1/decide` hot path
- **Throughput:** 5,000 RPS burst capacity per tenant
- **Multi-tenancy:** schema-isolated tenants sharing infrastructure
- **Deployment:** Kubernetes on AWS, with data residency in MENA
- **ML inference:** real-time churn scoring and offer ranking in the hot path

This ADR records the foundational technology choices for the platform.

---

## Decisions

### 1. Language: Kotlin on JVM

**Decision:** Use Kotlin (targeting JVM 21+) as the primary language for the core decisioning service.

**Rationale:**
- JVM ecosystem maturity for enterprise-grade systems (observability, profiling, connection pooling)
- Kotlin coroutines provide structured concurrency and async I/O without callback hell
- LightGBM JVM bindings (via ONNX Runtime) available for in-process inference
- Strong hiring pool in MENA for JVM developers (Java/Kotlin)
- Spring Boot ecosystem enables rapid development of production-grade services

**Alternatives considered:**
| Language | Pros | Cons |
|----------|------|------|
| Go | Better raw latency, smaller binaries | Weaker ML ecosystem, fewer MENA developers, no coroutine equivalent for structured async |
| Node.js | Good for connector layer, rich SDK ecosystem (Salesforce, Genesys) | GC pauses problematic at high RPS, single-threaded model limits CPU-bound scoring |
| Python | ML-native, rich data science libraries | Latency unsuitable for hot path, GIL limits concurrency |

**Consequence:** The connector layer (Salesforce, Genesys, CRM integrations) could still be implemented in Node.js if vendor SDKs are only available in JavaScript. This would be a separate service behind the event bus.

---

### 2. Cloud Region: AWS me-south-1 (Bahrain) Primary

**Decision:** Deploy primarily to AWS me-south-1 (Bahrain).

**Rationale:**
- Most mature MENA cloud region with broadest managed service availability
- Managed Kafka (MSK), managed PostgreSQL (RDS), managed Redis (ElastiCache) all available
- Meets TDRA (UAE) and NCA (KSA) data residency requirements — Bahrain is within GCC jurisdiction
- Single region covers both UAE and KSA operator requirements under standard interpretation

**Alternatives considered:**
| Region | Pros | Cons |
|--------|------|------|
| GCP me-central1 (Doha) | Google ML tooling | Newer region, fewer managed services, smaller partner ecosystem |
| Azure UAE North | Good enterprise presence in UAE | Weaker managed Kafka story, less mature than AWS me-south-1 |

**Consequence:** For the strictest interpretation of NCA data localization (KSA national data must remain within KSA borders), a separate sovereign KSA deployment may be required — either in a future AWS KSA region or on-premises at the operator site.

---

### 3. Event Streaming: AWS MSK (Managed Kafka)

**Decision:** Use AWS Managed Streaming for Apache Kafka (MSK) for all event streaming.

**Rationale:**
- Operational simplicity — no dedicated Kafka ops team needed for a small engineering team
- Native integration with AWS IAM for authentication and authorization
- Auto-scaling of broker storage
- Handles retention event sourcing, CDC from PostgreSQL, and connector integration events

**Alternatives considered:**
| Option | Pros | Cons |
|--------|------|------|
| Self-hosted Confluent | Schema Registry, ksqlDB, more features | Significant ops overhead, requires Kafka expertise on team |
| Redpanda | Lower latency, simpler operations | Less MENA regional support, smaller ecosystem |

**Consequence:** If advanced stream processing (ksqlDB, Kafka Streams) is needed later, MSK supports Confluent Schema Registry as a sidecar. Migration path exists.

---

### 4. ML Framework: LightGBM

**Decision:** Use LightGBM for churn prediction and offer propensity models, served via ONNX Runtime on JVM.

**Rationale:**
- Sub-millisecond inference latency for individual predictions
- Small model size (< 10MB), easily loaded into JVM heap
- ONNX export enables language-agnostic serving on JVM via ONNX Runtime
- Interpretable predictions via SHAP values (important for regulatory explainability)
- Proven track record in telco churn prediction use cases

**Alternatives considered:**
| Framework | Pros | Cons |
|-----------|------|------|
| XGBoost | Similar accuracy, well-known | Slightly slower inference than LightGBM |
| Neural networks (PyTorch) | Higher potential accuracy on large datasets | Overkill for tabular telco data, latency risk in hot path, harder to explain |
| scikit-learn | Simple, interpretable | Too limited for production-scale feature sets, no ONNX-optimized inference |

**Consequence:** Model training remains in Python (scikit-learn pipeline + LightGBM). Only inference runs on JVM. Training pipeline is offline and decoupled from the serving path.

---

### 5. Database: PostgreSQL 15 + Redis 7

**Decision:** Use PostgreSQL 15 (via RDS) for durable state and Redis 7 (via ElastiCache) for hot caching.

**Rationale:**

**PostgreSQL** handles:
- Decision audit log (append-only)
- Outcome tracking (offer acceptance, churn events)
- Rule definitions and versioning
- Tenant configuration and metadata
- Schema-per-tenant isolation with Flyway migrations

**Redis** handles:
- Subscriber profile cache (usage signals, segment, current plan)
- Offer catalog cache (per-tenant)
- Rate limiting and frequency cap counters
- Feature store hot layer for ML inference

**Multi-tenant isolation strategy:**
- One PostgreSQL database, separate schema per tenant (`tenant_etisalat`, `tenant_stc`, etc.)
- Flyway manages migrations across all tenant schemas
- Row-Level Security (RLS) as defense-in-depth — even if application logic fails, RLS prevents cross-tenant reads
- Connection pooling via PgBouncer in transaction mode

**Alternatives considered:**
| Option | Pros | Cons |
|--------|------|------|
| DynamoDB | Serverless, auto-scaling | Less flexible querying for analytics, vendor lock-in, harder schema evolution |
| MongoDB | Flexible schema | Weaker transactional guarantees, less mature RLS equivalent |

**Consequence:** PostgreSQL append-only audit tables will grow large. Partitioning by month and archival to S3 (Parquet) is planned for data older than 24 months.

---

### 6. API Framework: Spring Boot 3 + WebFlux

**Decision:** Use Spring Boot 3 with WebFlux (Project Reactor) for the HTTP API layer.

**Rationale:**
- Reactive non-blocking I/O for high throughput on fewer threads
- Mature observability integration (Micrometer to Prometheus, distributed tracing via OpenTelemetry)
- Well-understood deployment model on Kubernetes (health checks, graceful shutdown, ConfigMaps)
- Kotlin coroutines integrate cleanly with WebFlux via `kotlinx-coroutines-reactor`
- Large ecosystem of Spring starters for security (OAuth2), caching, and database access (R2DBC)

**Alternatives considered:**
| Framework | Pros | Cons |
|-----------|------|------|
| Ktor | Kotlin-native, lighter weight | Smaller ecosystem, less enterprise adoption, fewer integrations |
| Quarkus | Fast startup, GraalVM native | Less mature reactive story, smaller community than Spring |
| Micronaut | AOT compilation, fast startup | Smaller ecosystem than Spring Boot |

**Consequence:** Spring Boot's memory footprint is higher than alternatives. For the decisioning hot path, JVM warmup (CDS, tiered compilation) and proper heap tuning are required to meet p99 targets.

---

## Summary

| Component | Choice | Key Driver |
|-----------|--------|------------|
| Language | Kotlin / JVM 21+ | Coroutines + ML bindings + MENA talent |
| Cloud | AWS me-south-1 | MENA data residency + managed services |
| Streaming | AWS MSK | Operational simplicity |
| ML | LightGBM via ONNX | Sub-ms inference latency |
| Database | PostgreSQL 15 + Redis 7 | Durable state + hot cache |
| API | Spring Boot 3 + WebFlux | Reactive throughput + observability |
