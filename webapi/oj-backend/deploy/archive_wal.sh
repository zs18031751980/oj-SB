#!/usr/bin/env bash
# PostgreSQL archive_command 的 WAL 归档脚本（对齐旧后端 deploy/archive_wal.py）。
# 在 postgresql.conf 中设置:
#   archive_mode = on
#   archive_command = '/path/deploy/archive_wal.sh %p %f'
set -euo pipefail

SRC="${1:?用法: archive_wal.sh <wal-path> <wal-name>}"
NAME="${2:?缺少 WAL 文件名}"
DEST_DIR="${WAL_ARCHIVE_DIR:-/var/lib/postgresql/wal-archive}"

mkdir -p "$DEST_DIR"
TARGET="$DEST_DIR/$NAME"
# 幂等：已归档则直接成功，避免重试报错
if [ -f "$TARGET" ]; then
  exit 0
fi
# 先复制到临时文件再原子重命名，避免归档中出现半个文件
cp "$SRC" "$TARGET.tmp.$$"
mv "$TARGET.tmp.$$" "$TARGET"
