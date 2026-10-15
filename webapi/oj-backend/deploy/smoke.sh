#!/usr/bin/env bash
# 线上/预发冒烟：登录、令牌校验、就绪与若干只读接口（对齐旧后端 deploy/smoke.py）。
#
# 用法: API_BASE=https://ojapi.example.com SMOKE_USER=smoke SMOKE_PASS=*** ./smoke.sh
set -euo pipefail

API_BASE="${API_BASE:-http://127.0.0.1:6173}"
SMOKE_USER="${SMOKE_USER:-smoke}"
SMOKE_PASS="${SMOKE_PASS:-}"

code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

echo "[smoke] 就绪检查 $API_BASE/readyz"
[ "$(code "$API_BASE/readyz")" = "200" ] || { echo "readyz 失败" >&2; exit 1; }

echo "[smoke] 判题 worker 可用性"
code "$API_BASE/healthz/judge?pool=contest" | grep -qE '200|503' || { echo "judge 探针异常" >&2; exit 1; }

echo "[smoke] 题库列表"
[ "$(code "$API_BASE/problems?page=1&per_page=1")" = "200" ] || { echo "problems 失败" >&2; exit 1; }

if [ -n "$SMOKE_PASS" ]; then
  echo "[smoke] 密码登录并校验令牌"
  token="$(curl -s -X POST "$API_BASE/auth/login/password" -H 'Content-Type: application/json' \
    -H 'X-CSRF-Protection: 1' \
    -d "{\"identifier\":\"$SMOKE_USER\",\"password\":\"$SMOKE_PASS\",\"remember\":true}" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"
  [ -n "$token" ] || { echo "登录未取到 access_token" >&2; exit 1; }
  [ "$(code -H "Authorization: Bearer $token" "$API_BASE/auth/verify")" = "200" ] \
    || { echo "verify 失败" >&2; exit 1; }
fi

echo "[smoke] 通过"
