#!/usr/bin/env bash
# 设置用户角色（对齐旧后端 `python manage.py set-role`）。
#
# 两种模式：
#   1) 直接改库（停机/单机运维）：API_JAR=/path/oj-api.jar ./set-role.sh <userId> <role>
#   2) 调用管理 API（服务在线）：API_BASE=... ADMIN_TOKEN=... ./set-role.sh <userId> <role>
#
# role ∈ {member,staff,manager,provider}
set -euo pipefail

USER_ID="${1:-}"
ROLE="${2:-}"
if [ -z "$USER_ID" ] || [ -z "$ROLE" ]; then
  echo "用法: $0 <userId> <member|staff|manager|provider>" >&2
  exit 2
fi

if [ -n "${API_JAR:-}" ]; then
  exec java -jar "$API_JAR" "--set-role=$USER_ID:$ROLE"
fi

API_BASE="${API_BASE:-http://127.0.0.1:6173}"
: "${ADMIN_TOKEN:?在线模式需要 ADMIN_TOKEN（manager 的访问令牌）}"

curl -sS -X PATCH "$API_BASE/admin/users/$USER_ID/role" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-CSRF-Protection: 1' \
  -d "{\"role\":\"$ROLE\"}"
echo
