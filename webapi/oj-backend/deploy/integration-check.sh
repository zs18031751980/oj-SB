#!/usr/bin/env bash
# 本地联调检查：干净重启全栈（本地判题）→ 就绪/接口 → 判题 → DB/Redis → 日志 → E2E。
# 仅用于本地开发环境；会使用 smoke/SmokePass123! 账号并产生提交记录。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$ROOT"
API=http://127.0.0.1:6173
FAIL=0
fail() { echo "FAIL $*"; FAIL=$((FAIL + 1)); }
ok() { echo "PASS $*"; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

echo "===== 干净重启全栈（显式本地判题） ====="
./start.sh down >/dev/null 2>&1 || true
sleep 1
OJ_ALLOW_LOCAL_JUDGE=true OJ_JUDGE_EXECUTOR=local timeout 240 ./start.sh up > /tmp/oj-integration-up.log 2>&1
echo "start_exit=$?"

echo "===== 就绪与基础接口 ====="
for p in '/healthz' '/readyz' '/healthz/judge?pool=contest' '/auth/providers' '/problems?page=1&per_page=5' \
         '/rankings' '/v3/api-docs' '/announcement' '/discussions' '/learn-resources/tree'; do
  c=$(code "$API$p"); [ "$c" = 200 ] && ok "$p -> $c" || fail "$p -> $c"
done
for p in '/metrics' '/actuator/metrics' '/actuator/prometheus'; do
  c=$(code "$API$p"); [ "$c" = 503 ] && ok "$p 无令牌被拒 -> $c" || fail "$p 期望 503，实际 $c"
done

echo "===== deploy/smoke.sh ====="
API_BASE="$API" SMOKE_USER=smoke SMOKE_PASS='SmokePass123!' ./webapi/oj-backend/deploy/smoke.sh \
  && ok "smoke.sh" || fail "smoke.sh"

echo "===== 登录并巡检受保护接口 ====="
TOKEN=$(curl -s -X POST "$API/auth/login/password" -H 'Content-Type: application/json' -H 'X-CSRF-Protection: 1' \
  -d '{"identifier":"smoke","password":"SmokePass123!","remember":true}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["tokens"]["access_token"])' 2>/dev/null)
if [ -z "${TOKEN:-}" ]; then fail "登录失败"; else ok "登录成功"; fi
for p in '/users/me' '/users/me/stats' '/submissions?page=1&per_page=5' '/favorites' '/user/code' \
         '/learn-favorites' '/learn-history' '/admin/stats' '/admin/users?page=1&per_page=2'; do
  c=$(code "$API$p" -H "Authorization: Bearer $TOKEN"); [ "$c" = 200 ] && ok "$p -> $c" || fail "$p -> $c"
done

echo "===== 比赛接口（含榜单计分） ====="
CID=$(curl -s "$API/contests" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d[0]["id"] if d else "")' 2>/dev/null)
if [ -n "$CID" ]; then
  for p in "/contests/$CID" "/contests/$CID/problems" "/contests/$CID/rankings" "/contests/$CID/rules" \
           "/contests/$CID/teams" "/contests/$CID/roles" "/contests/$CID/events"; do
    c=$(code "$API$p"); [ "$c" = 200 ] && ok "$p -> $c" || fail "$p -> $c"
  done
  echo "contest_id=$CID"
else
  echo "WARN 无公开比赛，跳过比赛接口"
fi

echo "===== 判题链路（题目 2001） ====="
submit_case() {
  local source="$1" label="$2" body id st
  body=$(python3 -c 'import json,sys;print(json.dumps({"problem_id":2001,"code":sys.argv[1],"language":"python"}))' "$source")
  id=$(curl -s -X POST "$API/submissions" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -H 'X-CSRF-Protection: 1' -d "$body" \
    | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("id") or d.get("submission_id") or "")' 2>/dev/null)
  [ -z "$id" ] && { fail "$label 提交失败"; return; }
  st=""
  for _ in $(seq 1 40); do
    st=$(curl -s "$API/submissions/$id" -H "Authorization: Bearer $TOKEN" \
      | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("status") or d.get("verdict") or "")' 2>/dev/null)
    case "$st" in AC|WA|CE|RE|TLE|MLE|Partial|SystemError|SIGSEGV) break;; esac
    sleep 1
  done
  echo "  $label id=$id status=$st"
  case "$label:$st" in AC:AC|WA:WA|TLE:TLE) ok "判题 $label" ;; *) fail "判题 $label 得到 $st" ;; esac
}
submit_case 'a,b=map(int,input().split())
print(f"max={max(a,b)}")' "AC"
submit_case 'a,b=map(int,input().split())
print(0)' "WA"
submit_case 'while True: pass' "TLE"

echo "===== DB 一致性 ====="
psql -h 127.0.0.1 -p 5432 -U postgres -d oj -tAc \
  "select 'submissions', status, count(*) from submissions group by status order by 2" 2>&1
psql -h 127.0.0.1 -p 5432 -U postgres -d oj -tAc \
  "select 'contest_outbox', state, count(*) from contest_judge_outbox group by state order by 2" 2>&1
psql -h 127.0.0.1 -p 5432 -U postgres -d oj -tAc \
  "select 'submission_outbox', state, count(*) from submission_outbox group by state order by 2" 2>&1
pending=$(psql -h 127.0.0.1 -p 5432 -U postgres -d oj -tAc \
  "select count(*) from submissions where status in ('Pending','Judging')" 2>/dev/null | tr -d '[:space:]')
echo "pending_submissions=${pending:-unknown}"

echo "===== Redis 队列残留 ====="
for q in judge_queue contest_judge_queue practice_judge_queue rejudge_queue testcase_gen_queue; do
  printf '%s len=%s retry=%s inflight=%s\n' "$q" "$(redis-cli llen "$q")" \
    "$(redis-cli zcard "$q:retry")" "$(redis-cli zcard "$q:inflight")"
done

echo "===== 日志异常（最近 20 条） ====="
echo "--- api.log ---"
grep -iE '"level":"ERROR"|Exception|Caused by' .local-run/logs/api.log 2>/dev/null | tail -20
echo "--- worker.log ---"
grep -iE 'ERROR|Exception|Caused by' .local-run/logs/worker.log 2>/dev/null | tail -20

echo "===== E2E ====="
(cd webapp/letapp && CI=1 timeout 500 npx playwright test --reporter=line 2>&1 | tail -6)

echo "===== 汇总 ====="
echo "FAIL_COUNT=$FAIL"
exit "$FAIL"
