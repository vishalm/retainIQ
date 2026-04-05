-- RetainIQ Platform Schema
-- Phase 0: Foundation

CREATE SCHEMA IF NOT EXISTS platform;

-- Tenants
CREATE TABLE platform.tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    market VARCHAR(10) NOT NULL DEFAULT 'AE',
    regulatory_profile JSONB NOT NULL DEFAULT '{}',
    catalog_webhook_url VARCHAR(512),
    api_client_id VARCHAR(255) UNIQUE,
    api_credentials_hash VARCHAR(512),
    status VARCHAR(20) NOT NULL DEFAULT 'PROVISIONING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_status CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED'))
);

-- Decisions (append-only audit log)
CREATE TABLE platform.decisions (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    subscriber_hash VARCHAR(128) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    churn_score DOUBLE PRECISION NOT NULL,
    churn_band VARCHAR(10) NOT NULL,
    offers_ranked JSONB NOT NULL DEFAULT '[]',
    rules_applied TEXT[] NOT NULL DEFAULT '{}',
    degraded BOOLEAN NOT NULL DEFAULT FALSE,
    confidence VARCHAR(10) NOT NULL DEFAULT 'HIGH',
    latency_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Create partitions for the next 12 months (monthly)
CREATE TABLE platform.decisions_2026_01 PARTITION OF platform.decisions
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE platform.decisions_2026_02 PARTITION OF platform.decisions
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE platform.decisions_2026_03 PARTITION OF platform.decisions
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE platform.decisions_2026_04 PARTITION OF platform.decisions
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE platform.decisions_2026_05 PARTITION OF platform.decisions
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE platform.decisions_2026_06 PARTITION OF platform.decisions
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE platform.decisions_2026_07 PARTITION OF platform.decisions
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE platform.decisions_2026_08 PARTITION OF platform.decisions
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE platform.decisions_2026_09 PARTITION OF platform.decisions
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE platform.decisions_2026_10 PARTITION OF platform.decisions
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE platform.decisions_2026_11 PARTITION OF platform.decisions
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE platform.decisions_2026_12 PARTITION OF platform.decisions
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

-- Indexes for decision queries
CREATE INDEX idx_decisions_tenant_created ON platform.decisions (tenant_id, created_at DESC);
CREATE INDEX idx_decisions_subscriber ON platform.decisions (subscriber_hash, created_at DESC);

-- Outcomes
CREATE TABLE platform.outcomes (
    id UUID PRIMARY KEY,
    decision_id UUID NOT NULL,
    offer_sku VARCHAR(100) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    revenue_delta DOUBLE PRECISION DEFAULT 0,
    churn_prevented BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_outcome CHECK (outcome IN ('ACCEPTED', 'DECLINED', 'NO_RESPONSE'))
);

CREATE INDEX idx_outcomes_decision ON platform.outcomes (decision_id);
CREATE INDEX idx_outcomes_sku ON platform.outcomes (offer_sku, created_at DESC);

-- VAS Products (catalog)
CREATE TABLE platform.vas_products (
    sku VARCHAR(100) PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES platform.tenants(id),
    name VARCHAR(255) NOT NULL,
    name_ar VARCHAR(255),
    category VARCHAR(50) NOT NULL,
    margin DOUBLE PRECISION NOT NULL DEFAULT 0,
    markets TEXT[] NOT NULL DEFAULT '{}',
    eligibility_rules JSONB NOT NULL DEFAULT '[]',
    bundle_with TEXT[] NOT NULL DEFAULT '{}',
    incompatible_with TEXT[] NOT NULL DEFAULT '{}',
    upgrade_from TEXT[] NOT NULL DEFAULT '{}',
    regulatory JSONB NOT NULL DEFAULT '{}',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_tenant ON platform.vas_products (tenant_id, active);

-- Rules
CREATE TABLE platform.rules (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES platform.tenants(id),
    type VARCHAR(50) NOT NULL,
    expression_json JSONB NOT NULL,
    market VARCHAR(10),
    version INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rules_tenant ON platform.rules (tenant_id, active, market);

-- A/B Tests
CREATE TABLE platform.ab_tests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES platform.tenants(id),
    name VARCHAR(255) NOT NULL,
    variants JSONB NOT NULL DEFAULT '[]',
    allocation JSONB NOT NULL DEFAULT '{}',
    start_date DATE,
    end_date DATE,
    primary_metric VARCHAR(100) NOT NULL DEFAULT 'offer_attach_rate',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed demo tenant
INSERT INTO platform.tenants (id, name, market, regulatory_profile, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Demo Operator',
    'AE',
    '{"requireArabicDisclosure": false, "consentRequired": false, "coolingOffHours": 24, "auditRetentionMonths": 24}',
    'ACTIVE'
);
