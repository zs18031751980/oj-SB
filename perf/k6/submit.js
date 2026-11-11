import http from 'k6/http';
import { check, sleep } from 'k6';

// 提交压测：重点观察 API 接收、outbox 落库与 Redis 入队，不等待判题完成。
// 注意：会真实消耗判题队列，不要在共享环境长时间运行；判题容量需单独评估。
export const options = {
  vus: Number(__ENV.VUS || 5),
  duration: __ENV.DURATION || '20s',
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<1500'],
  },
};

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:6173';
const USER = __ENV.SMOKE_USER || 'smoke';
const PASS = __ENV.SMOKE_PASS || '';
const PROBLEM_ID = Number(__ENV.PROBLEM_ID || 2001);
const CODE = __ENV.CODE || 'a,b=map(int,input().split())\nprint(f"max={max(a,b)}")';

function login() {
  const response = http.post(
    `${BASE}/auth/login/password`,
    JSON.stringify({ identifier: USER, password: PASS, remember: false }),
    { headers: { 'Content-Type': 'application/json', 'X-CSRF-Protection': '1' } },
  );
  if (response.status < 200 || response.status >= 300) return null;
  try {
    return response.json('tokens.access_token');
  } catch (e) {
    return null;
  }
}

export default function () {
  const token = login();
  if (!token) {
    check(null, { 'login failed': () => false });
    return;
  }
  const response = http.post(
    `${BASE}/submissions`,
    JSON.stringify({ problem_id: PROBLEM_ID, code: CODE, language: 'python' }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        'X-CSRF-Protection': '1',
      },
    },
  );
  check(response, { 'submit 2xx': (r) => r.status >= 200 && r.status < 300 });
  sleep(2);
}
