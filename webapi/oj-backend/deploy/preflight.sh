#!/usr/bin/env bash
# 发布前配置自检（对齐旧后端 deploy/preflight.py）。
# 校验必需环境变量与关键约束，任一不满足即非零退出，供 CI/部署门禁调用。
set -euo pipefail

fail=0
require() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "[preflight] 缺少必需环境变量: $name" >&2
    fail=1
  fi
}

require OJ_DB_URL
require OJ_DB_USERNAME
require OJ_DB_PASSWORD
require OJ_REDIS_HOST
require OJ_JWT_SECRET

# JWT 密钥长度下限，避免弱密钥
if [ -n "${OJ_JWT_SECRET:-}" ] && [ "${#OJ_JWT_SECRET}" -lt 32 ]; then
  echo "[preflight] OJ_JWT_SECRET 长度不足 32" >&2
  fail=1
fi

# 生产环境强制安全 Cookie
if [ "${APP_ENV:-}" = "production" ] && [ "${OJ_COOKIE_SECURE:-false}" != "true" ]; then
  echo "[preflight] 生产环境必须设置 OJ_COOKIE_SECURE=true" >&2
  fail=1
fi

# 生产环境不允许本机无隔离判题
if [ "${APP_ENV:-}" = "production" ] && [ "${OJ_ALLOW_LOCAL_JUDGE:-false}" = "true" ]; then
  echo "[preflight] 生产环境禁止 OJ_ALLOW_LOCAL_JUDGE=true" >&2
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  echo "[preflight] 配置检查通过"
fi
exit "$fail"
