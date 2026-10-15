#!/usr/bin/env bash
# 针对恢复后的数据库执行冒烟（对齐旧后端 deploy/restored_smoke.py）。
# 复用 smoke.sh，但要求显式指定被恢复实例的 API 地址，避免误打到线上。
set -euo pipefail

: "${RESTORED_API_BASE:?需要 RESTORED_API_BASE，例如 http://127.0.0.1:6173}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

API_BASE="$RESTORED_API_BASE" SMOKE_USER="${SMOKE_USER:-smoke}" SMOKE_PASS="${SMOKE_PASS:-}" \
  "$DIR/smoke.sh"
