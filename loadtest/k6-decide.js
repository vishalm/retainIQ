import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const decideLatency = new Trend('retainiq_decide_latency', true);
const decideErrors = new Rate('retainiq_decide_errors');
const sloBreaches = new Rate('retainiq_slo_breaches');

// Test configuration
export const options = {
  scenarios: {
    // Ramp-up: simulate growing traffic
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },   // Warm up
        { duration: '1m', target: 200 },    // Ramp to sustained load
        { duration: '2m', target: 200 },    // Hold at sustained
        { duration: '30s', target: 500 },   // Burst to 5x
        { duration: '1m', target: 500 },    // Hold burst
        { duration: '30s', target: 0 },     // Cool down
      ],
    },
  },
  thresholds: {
    'retainiq_decide_latency': ['p(99)<200'],  // p99 < 200ms SLA
    'retainiq_decide_errors': ['rate<0.005'],   // Error rate < 0.5%
    'retainiq_slo_breaches': ['rate<0.01'],     // SLO breach < 1%
    'http_req_duration': ['p(95)<300'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TENANT_ID = __ENV.TENANT_ID || '00000000-0000-0000-0000-000000000001';

// Get token once at init
let TOKEN = '';

export function setup() {
  const tokenRes = http.post(`${BASE_URL}/v1/auth/token`, JSON.stringify({
    grant_type: 'client_credentials',
    client_id: TENANT_ID,
    client_secret: __ENV.CLIENT_SECRET || 'demo',
  }), { headers: { 'Content-Type': 'application/json' } });

  if (tokenRes.status !== 200) {
    console.error(`Token fetch failed: ${tokenRes.status} ${tokenRes.body}`);
    return { token: '' };
  }
  return { token: JSON.parse(tokenRes.body).access_token };
}

// Subscriber pool for realistic traffic
const subscribers = Array.from({ length: 1000 }, (_, i) => `sub_${String(i).padStart(5, '0')}`);
const channels = ['agentforce', 'genesys', 'app', 'ivr'];
const intents = ['cancel', 'complaint', 'billing', 'upgrade'];
const markets = ['AE', 'SA', 'KW', 'BH', 'OM'];
const reasons = ['billing', 'network', 'competitor'];

function randomFrom(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.token}`,
    'X-Tenant-ID': TENANT_ID,
  };

  group('POST /v1/decide', () => {
    const payload = JSON.stringify({
      subscriber_id: randomFrom(subscribers),
      channel: randomFrom(channels),
      signals: {
        frustration_score: Math.random(),
        intent: randomFrom(intents),
        session_duration_s: Math.floor(Math.random() * 600),
        prior_contacts_30d: Math.floor(Math.random() * 8),
      },
      context: {
        reason_code: randomFrom(reasons),
        market: randomFrom(markets),
      },
      options: {
        max_offers: 3,
        explain: false,
      },
    });

    const res = http.post(`${BASE_URL}/v1/decide`, payload, { headers, tags: { endpoint: 'decide' } });
    const latency = res.timings.duration;

    decideLatency.add(latency);
    decideErrors.add(res.status !== 200);
    sloBreaches.add(latency > 200);

    check(res, {
      'status is 200': (r) => r.status === 200,
      'has decision_id': (r) => JSON.parse(r.body).decision_id !== undefined,
      'has offers': (r) => JSON.parse(r.body).offers.length > 0,
      'latency < 200ms': (r) => r.timings.duration < 200,
      'latency < 500ms': (r) => r.timings.duration < 500,
    });

    // Simulate ~20% outcome feedback
    if (Math.random() < 0.2 && res.status === 200) {
      const body = JSON.parse(res.body);
      if (body.offers && body.offers.length > 0) {
        const outcome = Math.random() < 0.34 ? 'accepted' : 'declined';
        http.post(`${BASE_URL}/v1/outcome`, JSON.stringify({
          decision_id: body.decision_id,
          offer_sku: body.offers[0].sku,
          outcome: outcome,
          churn_prevented: outcome === 'accepted',
        }), { headers, tags: { endpoint: 'outcome' } });
      }
    }
  });

  sleep(Math.random() * 0.5); // Random think time 0-500ms
}

export function handleSummary(data) {
  const p99 = data.metrics.retainiq_decide_latency?.values?.['p(99)'] || 0;
  const errorRate = data.metrics.retainiq_decide_errors?.values?.rate || 0;
  const total = data.metrics.http_reqs?.values?.count || 0;

  console.log(`\n${'='.repeat(60)}`);
  console.log('RetainIQ Load Test Summary');
  console.log('='.repeat(60));
  console.log(`Total requests:  ${total}`);
  console.log(`P99 latency:     ${p99.toFixed(1)}ms ${p99 < 200 ? '✅ SLA MET' : '❌ SLA BREACH'}`);
  console.log(`Error rate:      ${(errorRate * 100).toFixed(2)}% ${errorRate < 0.005 ? '✅' : '❌'}`);
  console.log('='.repeat(60));

  return {
    'loadtest/results/summary.json': JSON.stringify(data, null, 2),
  };
}
