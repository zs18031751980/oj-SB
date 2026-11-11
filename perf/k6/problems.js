import http from 'k6/http';
import { check, sleep } from 'k6';

// 题库列表压测：验证分页查询与数据库读性能。
export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:6173';
const PAGE = __ENV.PAGE || '1';
const PER_PAGE = __ENV.PER_PAGE || '20';

export default function () {
  const response = http.get(`${BASE}/problems?page=${PAGE}&per_page=${PER_PAGE}`);
  check(response, {
    'problems 200': (r) => r.status === 200,
    'has data array': (r) => {
      try {
        return Array.isArray(r.json('data'));
      } catch (e) {
        return false;
      }
    },
  });
  sleep(1);
}
