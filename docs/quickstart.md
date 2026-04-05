# RetainIQ — Quick Start Integration

**Get CVM retention offers in your app in under 10 minutes.**

RetainIQ is the real-time execution layer for Customer Value Management (CVM). Your CVM team designs the strategy. RetainIQ executes it at the speed of conversation. No managed packages. No marketplace approvals. Just one API call.

---

## Step 1: Get your credentials (2 minutes)

```bash
curl -X POST https://api.retainiq.com/v1/auth/token \
  -d 'grant_type=client_credentials' \
  -d 'client_id=YOUR_CLIENT_ID' \
  -d 'client_secret=YOUR_CLIENT_SECRET'
```

Response:
```json
{ "access_token": "eyJ...", "expires_in": 900 }
```

---

## Step 2: Make a decision call (1 minute)

That's it. One endpoint. Send a subscriber ID, get ranked offers back.

```bash
curl -X POST https://api.retainiq.com/v1/decide \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: your-tenant" \
  -d '{
    "subscriber_id": "sub_12345",
    "channel": "app"
  }'
```

Response:
```json
{
  "decision_id": "dec_abc123",
  "subscriber": {
    "segment": "high_value",
    "churn_score": 0.73,
    "churn_band": "HIGH"
  },
  "offers": [
    {
      "rank": 1,
      "sku": "VAS-STREAM-PLUS",
      "name": "StreamPlus 3-month free trial",
      "retention_probability": 0.68,
      "margin_impact": 12.50,
      "script_hint": "I can see you've been a loyal customer for 3 years. We'd like to offer you StreamPlus completely free for 3 months.",
      "deep_link": "https://operator.com/activate/VAS-STREAM-PLUS?ref=dec_abc123",
      "regulatory": { "consent_required": false, "disclosure": null }
    },
    {
      "rank": 2,
      "sku": "VAS-DATA-BOOST",
      "name": "Double data for 6 months",
      "retention_probability": 0.54,
      "margin_impact": 8.00,
      "script_hint": "We can double your data allowance for the next 6 months at no extra cost.",
      "deep_link": "https://operator.com/activate/VAS-DATA-BOOST?ref=dec_abc123"
    }
  ],
  "action": "present_offers",
  "latency_ms": 87
}
```

**That's a working integration.** Everything else is optional.

---

## Step 3 (optional): Report outcomes

When the subscriber accepts or declines, tell us so the model improves:

```bash
curl -X POST https://api.retainiq.com/v1/outcome \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "decision_id": "dec_abc123",
    "offer_sku": "VAS-STREAM-PLUS",
    "outcome": "accepted",
    "churn_prevented": true
  }'
```

---

## Minimal integration by platform

### Salesforce / Agentforce

Add to your Apex class or Flow:

```apex
// In your case-open Flow or Apex trigger
HttpRequest req = new HttpRequest();
req.setEndpoint('https://api.retainiq.com/v1/decide');
req.setMethod('POST');
req.setHeader('Authorization', 'Bearer ' + retainIQToken);
req.setHeader('Content-Type', 'application/json');
req.setBody('{"subscriber_id":"' + case.MSISDN__c + '","channel":"agentforce"}');

HttpResponse res = new Http().send(req);
// Parse res.getBody() → display offers in agent workspace
```

### Genesys Cloud

Add a **Data Action** pointing to `/v1/decide`. Map `subscriber_id` from the interaction. Display the `offers[0].script_hint` in the agent script panel.

### Any HTTP-capable system

It's a REST API. If you can make an HTTP POST, you can integrate. No SDK required.

```python
# Python — 5 lines
import requests

resp = requests.post("https://api.retainiq.com/v1/decide",
    headers={"Authorization": f"Bearer {token}", "X-Tenant-ID": "your-tenant"},
    json={"subscriber_id": "sub_12345", "channel": "app"})

offers = resp.json()["offers"]
```

```javascript
// Node.js — 5 lines
const resp = await fetch("https://api.retainiq.com/v1/decide", {
  method: "POST",
  headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
  body: JSON.stringify({ subscriber_id: "sub_12345", channel: "app" })
});
const { offers } = await resp.json();
```

```java
// Java — HttpClient
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.retainiq.com/v1/decide"))
    .header("Authorization", "Bearer " + token)
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(
        "{\"subscriber_id\":\"sub_12345\",\"channel\":\"app\"}"))
    .build();
HttpResponse<String> response = HttpClient.newHttpClient()
    .send(request, HttpResponse.BodyHandlers.ofString());
```

---

## What you DON'T need to do

| You might think you need to... | You don't. |
|-------------------------------|-----------|
| Install a managed package | Just call the API |
| Set up a connector | Just call the API |
| Configure field mappings | Send `subscriber_id`, get offers |
| Run a catalog sync first | We ship with a default catalog; sync later |
| Train a model | Base model works day one; improves with outcomes |
| Deploy anything | It's a hosted API |

---

## Performance Benchmarks

Tested on a single Docker container (2 CPU, 1.5GB RAM) with k6 load testing tool:

| Metric | Sequential | 50 Concurrent VUs | Peak Load | Production Target |
|--------|-----------|-------------------|-----------|-------------------|
| Avg latency | 6ms | 87ms (first run) | — | < 100ms |
| P90 | 8ms | — | — | < 150ms |
| P99 | 22ms | — | — | < 200ms |
| Error rate | 0% | 0% | 0% | < 0.5% |
| Throughput | — | — | 512 RPS | 1,000 RPS sustained |

The hot path (all caches warm, single request) completes in **6ms**. Peak throughput reached **512 RPS** with **0% errors**. The P99 under concurrent load is container-constrained — with 3+ Kubernetes pods on dedicated infra, the 200ms SLA is achievable with significant headroom.

### Management Console and Observability

Once the stack is running (`make docker-up`):

- **Management Console:** http://localhost:5173 — configure tenants, manage users, view dashboard KPIs (login: admin@retainiq.com / admin123)
- **Grafana Dashboards:** http://localhost:3000 — 3 dashboards: overview, API performance, E2E tracing (login: admin / admin)

Run the load test yourself:
```bash
# Install k6
brew install k6

# Smoke test (10 seconds, 5 VUs)
make load-smoke

# Full ramp test (up to 500 VUs)
make load-test
```

## When to go deeper

| When you want... | Then read... |
|-----------------|-------------|
| Managed Salesforce/Genesys packages | [integration.md](integration.md) §5 |
| Custom ranking weights | [architecture.md](architecture.md) §6.2 |
| Catalog sync from your VAS platform | [integration.md](integration.md) §6.3 |
| Compliance rules per market | [architecture.md](architecture.md) §6.3 |
| Full product context | [product.md](product.md) |

---

*The best integration is the one that ships today, not the one-click flow that ships after marketplace approval.*
