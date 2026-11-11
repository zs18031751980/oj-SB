import http from 'k6/http';
import { check, sleep } from 'k6';

// 登录压测：BASE_URL/SMOKE_USER/SMOKE_PASS/VUS/DURATION 通过环境变量传入。
export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
  },
};

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:6173';
const USER = __ENV.SMOKE_USER || 'smoke';
const PASS = __ENV.SMOKE_PASS || '';

export default function () {
  const response = http.post(
    `${BASE}/auth/login/password`,
    JSON.stringify({ identifier: USER, password: PASS, remember: true }),
    { headers: { 'Content-Type': 'application/json', 'X-CSRF-Protection': '1' } },
  );
  check(response, {
    'login 2xx': (r) => r.status >= 200 && r.status < 300,
    'has access token': (r) => {
      try {
        return Boolean(r.json('tokens.access_token'));
      } catch (e) {
        return false;
      }
    },
  });
  sleep(1);
}
