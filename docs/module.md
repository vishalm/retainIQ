# Module RetainIQ

Real-time VAS offer decisioning engine for telecom operators.

RetainIQ is a stateless API that sits between customer-facing channels (Agentforce, Genesys, operator apps) 
and BSS/VAS backends. It scores churn risk, selects compliant VAS offers that balance retention probability 
and margin, and returns ranked results in under 200ms — fast enough for live voice and chat.

## Architecture

The decisioning pipeline runs in 5 budgeted stages:

| Stage | Budget | Responsibility |
|-------|--------|----------------|
| Signal Enrichment | < 20ms | Load subscriber profile from cache/BSS |
| Churn Scoring | < 30ms | LightGBM gradient boosted trees |
| Offer Candidacy | < 40ms | Catalog graph + eligibility + regulatory rules |
| Offer Ranking | < 50ms | Multi-objective: α·retention + β·margin − γ·spend + δ·context |
| Response Assembly | < 20ms | Scripts, audit, JSON response |

## Packages

### com.retainiq.api
REST controllers for the three core endpoints: `/v1/decide`, `/v1/outcome`, `/v1/catalog/sync`, 
plus OAuth2 token endpoint and global error handling.

### com.retainiq.service
Orchestration layer. [DecisionService] wires the 5-stage pipeline and handles all business operations.

### com.retainiq.service.pipeline
The decisioning pipeline components — each stage is a separate class with a clear latency budget.

### com.retainiq.domain
Pure domain models with no framework dependencies. Entities: Tenant, SubscriberProfile, VasProduct, 
Decision, Outcome, ChurnResult, RankedOffer.

### com.retainiq.cache
Redis-backed caching for subscriber profiles and VAS catalog, with TTL-based expiry and graceful 
fallback on cache failure.

### com.retainiq.security
JWT-based authentication (OAuth2 client credentials), WebFlux security filter chain, and token service.

### com.retainiq.config
Spring configuration for Redis, Jackson, and application properties.

### com.retainiq.exception
Custom exception hierarchy mapped to HTTP error codes via the global exception handler.
