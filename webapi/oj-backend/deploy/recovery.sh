#!/usr/bin/env bash
# 逻辑备份/恢复演练辅助（对齐旧后端 deploy/recovery.py）。
#
# 备份: OJ_DB_URL=... ./recovery.sh backup <out.sql>
# 恢复: OJ_DB_URL=... ./recovery.sh restore <in.sql> <target-db>
set -euo pipefail

: "${OJ_DB_URL:?需要 OJ_DB_URL}"
MODE="${1:-}"

case "$MODE" in
  backup)
    OUT="${2:?用法: recovery.sh backup <out.sql>}"
    pg_dump "$OJ_DB_URL" --no-owner --format=custom --file="$OUT"
    echo "[recovery] 已备份到 $OUT"
    ;;
  restore)
    IN="${2:?用法: recovery.sh restore <in.sql> <target-db>}"
    TARGET_DB="${3:?缺少目标数据库名}"
    # 目标库须已存在且为空，避免覆盖线上数据
    pg_restore --no-owner --clean --if-exists --dbname="$TARGET_DB" "$IN"
    echo "[recovery] 已恢复到 $TARGET_DB"
    ;;
  *)
    echo "用法: $0 {backup|restore}" >&2
    exit 2
    ;;
esac
