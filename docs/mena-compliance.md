# MENA Regulatory Compliance Design

**Scope:** UAE (TDRA) and KSA (NCA/CITC) compliance requirements for RetainIQ
**Last updated:** 2026-04-05

---

## 1. Regulatory Landscape

### UAE

- **Regulator:** TDRA (Telecommunications and Digital Government Regulatory Authority)
- **Data protection law:** Federal Decree-Law No. 45/2021 on the Protection of Personal Data
- **Telecom-specific:** TDRA Consumer Protection Regulations for telecom subscribers
- **Enforcement:** UAE Data Office oversees compliance; TDRA handles telecom-specific subscriber protection

### KSA

- **Regulators:** NCA (National Cybersecurity Authority) for cybersecurity controls, CITC (Communications, Space and Technology Commission) for telecom regulation
- **Data protection law:** PDPL (Personal Data Protection Law), effective September 2023, with implementing regulations by SDAIA
- **Telecom-specific:** CITC regulations on Value Added Services (VAS), subscriber consent, and complaint handling
- **Enforcement:** SDAIA for PDPL, NCA for security controls, CITC for telecom service compliance

### Key Difference

KSA requires stricter data localization — personal data of Saudi nationals must generally remain within KSA. UAE allows processing within approved jurisdictions (GCC is accepted). This distinction drives RetainIQ's deployment topology.

---

## 2. Data Residency Requirements

### UAE (TDRA)

- Subscriber data must be stored within the UAE or within approved jurisdictions
- AWS me-south-1 (Bahrain) is accepted for UAE operators — Bahrain falls within GCC jurisdiction
- Cross-border transfers permitted with adequate safeguards under Decree-Law 45/2021

### KSA (NCA / PDPL)

- Personal data of Saudi nationals must remain within KSA under the strictest interpretation of PDPL and NCA controls
- Cross-border transfer requires explicit regulatory approval and adequacy assessment
- May require a dedicated deployment within KSA (future AWS KSA region or on-premises at operator site)

### RetainIQ Approach

- **Primary deployment:** AWS me-south-1 (Bahrain) — covers UAE operators and KSA operators under standard interpretation
- **Optional sovereign KSA deployment:** separate Kubernetes cluster within KSA for operators requiring strictest NCA compliance
- **Data segregation:** tenant schemas are fully isolated; KSA tenant data never leaves the KSA deployment if sovereign mode is active

### What Counts as Personal Data

| Data Element | Classification | RetainIQ Handling |
|---|---|---|
| MSISDN | Personal data (direct identifier) | Stored as HMAC-SHA256 hash only — raw MSISDN never persisted |
| Usage patterns (data/voice/SMS) | Personal data derivative | Aggregated signals stored in Redis cache, TTL-bound |
| Churn score | Personal data derivative | Computed in-memory, logged in audit trail with subscriber hash |
| Billing data | Personal data | Not stored by RetainIQ — fetched at inference time from operator CDP |
| Offer acceptance/rejection | Behavioral data | Stored in PostgreSQL audit log, linked to subscriber hash |

> **Note:** Even though RetainIQ only stores HMAC-SHA256 hashed MSISDNs, usage signals and churn scores may still be classified as personal data derivatives under PDPL. The system treats all subscriber-linked data as personal data for compliance purposes.

---

## 3. Consent and Disclosure Rules

### UAE (TDRA)

- VAS offers require clear disclosure of pricing, duration, and auto-renewal terms
- Opt-in required for premium-rate services
- Cooling-off period: subscriber can cancel within 24 hours of activation
- Disclosure must be in a language the subscriber understands (English and Arabic)

### KSA (CITC)

- Explicit consent required before any VAS activation (CITC mandate)
- Arabic language disclosure mandatory — all offer text must include an Arabic version
- Auto-renewal requires separate explicit consent (not bundled with initial activation consent)
- CITC complaint reference number must be accessible to subscribers
- Double opt-in required for premium services (SMS confirmation)

### RetainIQ Implementation

Every offer returned in the `/v1/decide` response includes a `regulatory` block:

```json
{
  "offer_id": "offer-uuid",
  "name": "Data Booster 5GB",
  "regulatory": {
    "consent_required": true,
    "consent_type": "explicit",
    "disclosure": {
      "en": "5GB data add-on for 30 AED/month. Auto-renews monthly. Cancel anytime via *123#.",
      "ar": "باقة بيانات 5 جيجابايت بسعر 30 درهم شهريًا. تتجدد تلقائيًا. للإلغاء اتصل على *123#."
    },
    "cooling_off_hours": 24,
    "auto_renewal": true,
    "auto_renewal_separate_consent": false,
    "citc_complaint_ref": null
  }
}
```

The rule engine enforces per-market consent requirements. An offer cannot be included in the ranked response if its required disclosure fields are missing.

---

## 4. JSON DSL Rule Examples

### Rule 1: UAE — Require Disclosure for Premium VAS

```json
{
  "id": "tdra-premium-disclosure",
  "type": "regulatory",
  "market": "AE",
  "version": 1,
  "expression": {
    "if": { "product.category": "premium" },
    "then": {
      "require": ["disclosure_text.ar", "disclosure_text.en"],
      "set": { "consent_required": true, "cooling_off_hours": 24 }
    }
  }
}
```

### Rule 2: KSA — Arabic Disclosure Mandatory for All Offers

```json
{
  "id": "citc-arabic-disclosure",
  "type": "regulatory",
  "market": "SA",
  "version": 1,
  "expression": {
    "if": { "product.category": { "$exists": true } },
    "then": {
      "require": ["disclosure_text.ar", "name_ar"],
      "set": { "consent_required": true }
    }
  }
}
```

### Rule 3: KSA — Auto-Renewal Requires Separate Consent

```json
{
  "id": "citc-auto-renewal-consent",
  "type": "regulatory",
  "market": "SA",
  "version": 1,
  "expression": {
    "if": { "product.auto_renewal": true },
    "then": {
      "require": ["disclosure_text.ar"],
      "set": {
        "auto_renewal_separate_consent": true,
        "consent_type": "double_opt_in"
      }
    }
  }
}
```

### Rule 4: UAE — Frequency Cap on Retention Offers

```json
{
  "id": "tdra-retention-frequency-cap",
  "type": "regulatory",
  "market": "AE",
  "version": 1,
  "expression": {
    "if": { "offer.type": "retention" },
    "then": {
      "frequency_cap": {
        "max_offers": 2,
        "window_days": 30,
        "scope": "subscriber",
        "action_on_exceed": "suppress"
      }
    }
  }
}
```

### Rule 5: KSA — Data Must Not Leave KSA Region

```json
{
  "id": "nca-data-residency-ksa",
  "type": "regulatory",
  "market": "SA",
  "version": 1,
  "expression": {
    "if": { "subscriber.nationality": "SA" },
    "then": {
      "processing_constraint": {
        "allowed_regions": ["sa-east-1", "ksa-on-prem"],
        "deny_cross_border": true,
        "audit_log_region": "ksa-on-prem"
      }
    }
  }
}
```

---

## 5. Arabic Language Requirements

### Storage

- All subscriber-facing text fields (`disclosure_text`, `script_hint`, `offer_name`) are stored with both `en` and `ar` variants
- Offer catalog entries for KSA tenants must include the `name_ar` field — catalog ingestion rejects entries without it

### Serving

- The `/v1/decide` response includes both language versions in the `regulatory.disclosure` object
- The connector layer (Salesforce, Genesys, IVR) selects the appropriate locale based on channel configuration and subscriber language preference
- Right-to-left (RTL) formatting is the connector's responsibility — RetainIQ stores plain text

### Enforcement

- If Arabic text is missing for any required field, the offer is **excluded from KSA ranking** — enforced by the `citc-arabic-disclosure` rule (Rule 2 above)
- This is a hard exclusion, not a soft penalty — missing Arabic text means the offer is non-compliant and cannot be presented

### Fallback Behavior

| Market | Arabic Text Present | Behavior |
|--------|-------------------|----------|
| SA | Yes | Offer ranked normally |
| SA | No | Offer excluded from response |
| AE | Yes | Offer ranked normally |
| AE | No | Offer ranked normally (Arabic not mandatory for UAE, but recommended) |

---

## 6. Audit and Retention

### Regulatory Retention Periods

| Regulation | Record Type | Minimum Retention | RetainIQ Implementation |
|---|---|---|---|
| TDRA | Subscriber interaction records | 2 years | 24 months in PostgreSQL append-only tables |
| NCA | Security and access logs | 1 year | 18 months in PostgreSQL + CloudWatch |
| NCA | Transaction records | 5 years | 24 months hot (PostgreSQL), archived to S3 Parquet for 5-year retention |
| PDPL | Consent records | Duration of processing + 1 year | Retained for full tenant lifecycle |

### Audit Log Schema

Each decision produces an immutable audit record:

```json
{
  "decision_id": "uuid-v7",
  "timestamp": "2026-04-05T14:23:01.123Z",
  "tenant_id": "etisalat-ae",
  "market": "AE",
  "subscriber_hash": "hmac-sha256-of-msisdn",
  "channel": "ivr",
  "offers_shown": ["offer-1", "offer-2"],
  "offers_ranked": ["offer-2", "offer-1"],
  "churn_score": 0.73,
  "rules_applied": ["tdra-premium-disclosure", "tdra-retention-frequency-cap"],
  "rules_blocked": [],
  "consent_status": "not_required",
  "latency_ms": 47,
  "model_version": "churn-v3.2.1"
}
```

### Export

- Format: JSON-lines (`.jsonl`), one record per line
- Available via management API: `GET /v1/admin/audit/export?from=2026-01-01&to=2026-03-31`
- Export can be filtered by market, tenant, and date range
- Intended for regulatory submission to TDRA, NCA, or CITC upon request

---

## 7. Compliance Verification Checklist

| # | Requirement | Regulation | UAE Status | KSA Status |
|---|---|---|---|---|
| 1 | Data stored within approved jurisdiction | TDRA / NCA | Designed | Designed |
| 2 | MSISDN stored as hash only | PDPL / Decree-Law 45 | Designed | Designed |
| 3 | Subscriber consent captured before VAS activation | TDRA / CITC | Designed | Designed |
| 4 | Arabic disclosure text for all offers | CITC | N/A | Designed |
| 5 | Cooling-off period (24h cancellation) | TDRA | Designed | N/A |
| 6 | Auto-renewal requires separate consent | CITC | N/A | Designed |
| 7 | Frequency cap on retention offers | TDRA best practice | Designed | Designed |
| 8 | Audit log retained for 2+ years | TDRA | Designed | Designed |
| 9 | Security logs retained for 1+ year | NCA | N/A | Designed |
| 10 | Transaction records retained for 5 years | NCA | N/A | Designed |
| 11 | Consent records exportable | PDPL | Designed | Designed |
| 12 | CITC complaint reference accessible | CITC | N/A | Designed |
| 13 | Double opt-in for premium services (KSA) | CITC | N/A | Designed |
| 14 | Cross-border transfer controls | NCA / PDPL | Designed | Designed |
| 15 | Row-Level Security for tenant isolation | NCA cybersecurity controls | Designed | Designed |

**Status key:** Designed = architecture supports it, Implemented = code complete, Verified = tested and audited

---

## Appendix: Deployment Topology by Compliance Mode

```
Standard Mode (UAE + KSA standard):
  AWS me-south-1 (Bahrain)
  ├── EKS cluster (RetainIQ services)
  ├── RDS PostgreSQL (all tenant schemas)
  ├── ElastiCache Redis
  ├── MSK Kafka
  └── S3 (audit archive, model artifacts)

Sovereign KSA Mode (NCA strict):
  AWS me-south-1 (Bahrain)          KSA On-Prem / KSA Region
  ├── UAE tenant workloads           ├── KSA tenant workloads
  ├── RDS (UAE schemas only)         ├── PostgreSQL (KSA schemas only)
  ├── ElastiCache (UAE cache)        ├── Redis (KSA cache)
  └── MSK (UAE topics)               └── Kafka (KSA topics)

  Shared (no subscriber data):
  ├── Model registry (S3, no PII)
  ├── Rule definitions (Git, no PII)
  └── Management plane (tenant config, no subscriber data)
```
