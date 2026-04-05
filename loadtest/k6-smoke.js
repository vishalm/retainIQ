import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 5,
  duration: '10s',
  thresholds: {
    http_req_duration: ['p(99)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  const res = http.post(`${BASE_URL}/v1/auth/token`, JSON.stringify({
    grant_type: 'client_credentials',
    client_id: '00000000-0000-0000-0000-000000000001',
    client_secret: 'demo',
  }), { headers: { 'Content-Type': 'application/json' } });
  return { token: JSON.parse(res.body).access_token };
}

export default function (data) {
  const res = http.post(`${BASE_URL}/v1/decide`, JSON.stringify({
    subscriber_id: `sub_${Math.floor(Math.random() * 100)}`,
    channel: 'app',
    signals: { intent: 'cancel', frustration_score: 0.7 },
  }), {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${data.token}`,
      'X-Tenant-ID': '00000000-0000-0000-0000-000000000001',
    },
  });

  check(res, {
    'status 200': (r) => r.status === 200,
    'has offers': (r) => JSON.parse(r.body).offers?.length > 0,
  });
}
