# RetainIQ

**Real-time VAS offer decisioning for telecom operators.** RetainIQ is a thin, stateless decisioning layer that sits between customer-facing channels (Agentforce, Genesys, operator apps) and BSS/VAS backends. It returns ranked, margin-aware, compliance-checked retention offers in a single low-latency API call—without replacing your conversational AI or billing stack.

| | |
|---|---|
| **Document** | Technical source: `docs/RetainIQ_Technical_Design.docx` (v1.0.0, draft) |
| **Status** | Running locally — all systems operational |
| **Stack** | Kotlin · Spring Boot WebFlux · PostgreSQL · Redis · Kafka · LightGBM · AWS me-south-1 |

## What RetainIQ Does — Animated

<p align="center">
<img src="docs/assets/product-flow.svg" alt="RetainIQ Product Flow" width="100%" />
</p>

## ROI Impact for Telcos

<p align="center">
<img src="docs/assets/roi-impact.svg" alt="RetainIQ ROI Impact" width="100%" />
</p>

## Important URLs

| URL | What | Credentials |
|-----|------|-------------|
| http://localhost:8080/health | API health check | — |
| http://localhost:8080/v1/decide | Decision API | JWT (see quickstart) |
| http://localhost:8080/swagger-ui.html | Swagger UI (API docs) | — |
| http://localhost:8080/v3/api-docs | OpenAPI JSON spec | — |
| http://localhost:5173 | **Management Console** (React) | admin@retainiq.com / admin123 |
| http://localhost:3000 | Grafana (dashboards) | admin / admin |
| http://localhost:9090 | Prometheus (metrics) | — |
| http://localhost:8080/actuator/prometheus | App metrics endpoint | — |

### Management Console Pages

| Page | URL | Purpose |
|------|-----|---------|
| Login | http://localhost:5173/login | Admin authentication |
| Dashboard | http://localhost:5173/ | Platform KPIs, channel distribution, top offers |
| Telco Config | http://localhost:5173/tenants | **Configure telecom operators** — BSS connection, compliance, ranking weights, API credentials |
| User Management | http://localhost:5173/users | Create/manage admin, analyst, viewer users |

### Management API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/v1/manage/login` | Admin login (email/password) |
| GET | `/v1/manage/tenants` | List all configured telcos |
| POST | `/v1/manage/tenants` | Onboard a new telco |
| GET | `/v1/manage/tenants/:id` | Get telco config |
| PUT | `/v1/manage/tenants/:id` | Update telco config (BSS, compliance, ranking) |
| POST | `/v1/manage/tenants/:id/activate` | Activate a telco |
| POST | `/v1/manage/tenants/:id/suspend` | Suspend a telco |
| POST | `/v1/manage/tenants/:id/test-bss` | Test BSS connectivity |
| POST | `/v1/manage/tenants/:id/regenerate-credentials` | Rotate API credentials |
| GET | `/v1/manage/users` | List users |
| POST | `/v1/manage/users` | Create a user |
| PUT | `/v1/manage/users/:id` | Update user role/status |
| DELETE | `/v1/manage/users/:id` | Delete a user |
| GET | `/v1/manage/dashboard/stats` | Platform dashboard statistics |

### Default Accounts

| Email | Password | Role | Access |
|-------|----------|------|--------|
| admin@retainiq.com | admin123 | SUPER_ADMIN | Full platform access |
| admin@demo-operator.com | demo123 | TENANT_ADMIN | Demo Operator tenant only |

## Where RetainIQ Fits in the Telco Stack

```mermaid
flowchart TB
    subgraph subscriber [Subscriber Touchpoints]
        IVR[📞 IVR / Voice]
        CHAT[💬 Chat / WhatsApp]
        APP[📱 Operator App]
        WEB[🌐 Self-Service Portal]
    end

    subgraph channels [Channel Platforms — owned by operator]
        AF[Salesforce Agentforce]
        GEN[Genesys Cloud CX]
        CUSTOM[Custom CRM / Agent Desktop]
    end

    subgraph retainiq [RetainIQ — Decisioning Layer]
        direction TB
        API[API Gateway\nOAuth2 · Rate Limits · TLS 1.3]
        DECIDE[Decisioning Service\n5-stage pipeline · under 200ms p99]
        ML[Churn Scorer\nLightGBM · SHAP explanations]
        RANK[Offer Ranker\nα·retention + β·margin − γ·spend + δ·context]
        RULES[Rule Engine\nJSON DSL · hot-deploy · per-market]
        CATALOG[VAS Catalog Graph\nincompatibility · upgrades · bundles]
        AUDIT[Audit & Analytics\n24-month retention · A/B tests]
        API --> DECIDE
        DECIDE --> ML
        DECIDE --> RANK
        DECIDE --> RULES
        RANK --> CATALOG
        DECIDE --> AUDIT
    end

    subgraph operator [Operator Backend Systems — not replaced]
        BSS[BSS / Billing\nAmdocs · Netcracker · Huawei]
        VAS[VAS Platform\nProvisioning · Activation]
        CRM[CRM / CDP\nSubscriber 360]
        DWH[Data Warehouse\nRevenue · Usage]
    end

    subgraph infra [Data Infrastructure]
        PG[(PostgreSQL 15\nDecisions · Outcomes · Rules)]
        REDIS[(Redis 7\nProfiles · Catalog · Usage)]
        KAFKA[Kafka\nOutcomes · Events · Retraining]
    end

    subscriber --> channels
    channels -- "POST /v1/decide" --> API
    channels -- "POST /v1/outcome" --> API
    VAS -- "POST /v1/catalog/sync" --> API
    DECIDE <--> REDIS
    DECIDE <--> BSS
    DECIDE <--> CRM
    AUDIT --> PG
    AUDIT --> KAFKA
    KAFKA --> DWH
    RANK -.-> VAS

    style retainiq fill:#0ea5e9,stroke:#0284c7,color:#fff
    style channels fill:#334155,stroke:#64748b,color:#f8fafc
    style operator fill:#334155,stroke:#64748b,color:#f8fafc
    style subscriber fill:#1e293b,stroke:#475569,color:#f8fafc
    style infra fill:#1e293b,stroke:#475569,color:#f8fafc
```

## How a Decision Flows (Real-Time Path)

```mermaid
sequenceDiagram
    participant S as Subscriber
    participant C as Channel (Agentforce / Genesys)
    participant R as RetainIQ API
    participant E as Enricher (< 20ms)
    participant M as Churn Scorer (< 30ms)
    participant O as Offer Candidacy (< 40ms)
    participant K as Ranker (< 50ms)
    participant A as Assembler (< 20ms)
    participant D as Cache (Redis)
    participant B as BSS

    S->>C: "I want to cancel my plan"
    C->>R: POST /v1/decide {subscriber_id, channel, signals}

    rect rgb(14, 165, 233)
        Note over R,A: Decision Pipeline — total budget < 200ms
        R->>E: Stage 1: Enrich
        E->>D: Get subscriber profile
        D-->>E: Profile (cache hit) or fallback
        E->>M: Stage 2: Score churn
        M-->>E: churn_score: 0.73, band: HIGH
        E->>O: Stage 3: Find candidates
        O->>D: Get VAS catalog
        D-->>O: 6 eligible products
        O->>K: Stage 4: Rank offers
        K-->>R: Top 3 ranked offers
        R->>A: Stage 5: Assemble response
    end

    R-->>C: 200 OK {offers, script_hints, deep_links}
    C-->>S: "I can offer you StreamPlus free for 3 months..."

    Note over C,R: Later...
    S->>C: "Yes, I'll take it"
    C->>R: POST /v1/outcome {accepted, churn_prevented: true}
    R--)R: Kafka → model retraining
```

## The Product in One Picture

```mermaid
mindmap
    root((RetainIQ))
        What it does
            Scores churn risk in real time
            Ranks VAS offers by retention × margin
            Enforces compliance rules per market
            Closes the feedback loop for ROI
        What it replaces
            Static offer scripts
            Agent improvisation
            Spreadsheet-based eligibility
            Months of SI per channel
        What it does NOT replace
            Your chatbot / IVR (Agentforce, Genesys)
            Your BSS / billing (Amdocs, Netcracker)
            Your CRM / CDP (Salesforce, Adobe)
            Your data warehouse / BI
        Who uses it
            Retention & VAS leadership → ROI dashboards
            Contact centre ops → agent guidance
            IT architecture → one API pattern
            Compliance → audit trail & market rules
            Analysts → rule tuning & A/B tests
        Integration speed
            Tier 1: Managed package < 1 hour
            Tier 2: Webhook + mapper < 4 hours
            Tier 3: REST + OpenAPI < 1 day
            Tier 4: SDK < 2 days
```

## Documentation

| Doc | Purpose |
|-----|---------|
| [quickstart.md](docs/quickstart.md) | **Start here** — integrate in under 10 minutes with code samples |
| [product.md](docs/product.md) | Product vision, personas, outcomes, and roadmap narrative (product-owner lens) |
| [architecture.md](docs/architecture.md) | System design: context, components, data, security, deployment |
| [integration.md](docs/integration.md) | One-click connectors, APIs, BSS adapters, and channel integration |
| [plan.md](docs/plan.md) | Phased implementation roadmap and exit criteria |
| [openapi.yaml](docs/openapi.yaml) | OpenAPI 3.1 spec — full schemas, error codes, examples |
| [ml-design.md](docs/ml-design.md) | ML pipeline: feature store, training, inference, A/B testing |
| [multi-tenant-design.md](docs/multi-tenant-design.md) | Schema-per-tenant DDL, provisioning, migrations, Redis namespacing |
| [mena-compliance.md](docs/mena-compliance.md) | TDRA/NCA rules, Arabic disclosures, data residency, JSON DSL examples |
| [adr/001-stack.md](docs/adr/001-stack.md) | ADR: Kotlin, AWS me-south-1, LightGBM, PostgreSQL+Redis |

## What RetainIQ Is (and Is Not)

- **Is:** A decisioning engine—churn scoring, eligibility, ranking, audit, and outcomes feedback.
- **Is not:** A conversational AI/NLU platform, a BSS, or a full analytics/BI product (it exposes APIs and embedded analytics, not a generic BI suite).

## Core API

- `POST /v1/decide` — primary real-time decision (target **under 200 ms p99** end-to-end).
- `POST /v1/outcome` — accept/decline feedback for attribution and model improvement.
- `POST /v1/catalog/sync` — VAS catalog push from the operator platform (HMAC-signed).

## Non-Functional Targets (from design)

| Area | Target |
|------|--------|
| Latency | Under 200 ms p99 |
| Availability | 99.95% monthly |
| Throughput | 5,000 RPS burst, 1,000 RPS sustained |
| Data residency | In-country UAE/KSA where required (TDRA / NCA) |
| Audit | 24 months decision retention |

## Quick Start (Developer)

```bash
# 1. Start everything
make docker-up

# 2. Get a token
curl -s -X POST http://localhost:8080/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"grant_type":"client_credentials","client_id":"00000000-0000-0000-0000-000000000001","client_secret":"demo"}'

# 3. Make a decision
curl -s -X POST http://localhost:8080/v1/decide \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: 00000000-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" \
  -d '{"subscriber_id":"sub_123","channel":"app","signals":{"intent":"cancel","frustration_score":0.8}}'
```

See [quickstart.md](docs/quickstart.md) for full integration guide with code samples in Python, Node, Java, and Apex.

## Repository Layout

```
retainiq/
├── README.md
├── build.gradle.kts                     # Kotlin/Spring Boot 3 + WebFlux
├── Dockerfile                           # Multi-stage, Temurin 21, ZGC
├── docker-compose.yml                   # Full local stack (PG + Redis + Kafka)
├── Makefile                             # build, test, run, curl-* helpers
├── k8s/base/                            # Kubernetes manifests (deploy, HPA, ingress)
├── src/main/kotlin/com/retainiq/
│   ├── RetainIQApplication.kt
│   ├── api/                             # Controllers (decide, outcome, catalog, auth)
│   ├── service/                         # DecisionService, repositories, catalog, tenants
│   │   └── pipeline/                    # 5-stage pipeline (enrich → score → candidacy → rank → assemble)
│   ├── cache/                           # Redis cache (subscriber profiles, VAS catalog)
│   ├── security/                        # JWT auth, SecurityConfig, token service
│   ├── config/                          # Redis, Jackson configuration
│   ├── domain/                          # Domain models (Tenant, Subscriber, VasProduct, Decision)
│   └── exception/                       # Custom exceptions + global error handler
├── src/main/resources/
│   ├── application.yml                  # Config with profiles (local, prod)
│   └── db/migration/platform/           # Flyway migrations (partitioned tables)
├── src/test/kotlin/                     # Unit + integration tests
└── docs/
    ├── RetainIQ_Technical_Design.docx   # authoritative technical design (ingested)
    ├── quickstart.md                    # developer quick start (10 min)
    ├── product.md                       # product vision & personas
    ├── architecture.md                  # system design & components
    ├── integration.md                   # connectors, APIs, BSS adapters
    ├── plan.md                          # phased roadmap & exit criteria
    ├── openapi.yaml                     # OpenAPI 3.1 spec
    ├── ml-design.md                     # ML pipeline LLD
    ├── multi-tenant-design.md           # schema isolation & provisioning
    ├── mena-compliance.md               # TDRA/NCA regulatory design
    └── adr/
        └── 001-stack.md                 # tech stack decision record
```

## Confidentiality

Design materials may be marked confidential. Do not distribute outside authorised stakeholders.

---

*Generated from RetainIQ Technical Design Documentation (April 2026).*
