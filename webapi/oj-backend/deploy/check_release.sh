#!/usr/bin/env bash
# 校验发布制品引用为不可变摘要（对齐旧后端 deploy/check_release.py）。
# 用法: ./check_release.sh <image-ref>...
# 通过条件: 每个引用包含 @sha256:<64hex>，或形如 name:1.2.3 的语义化标签。
set -euo pipefail

fail=0
for ref in "$@"; do
  if [[ "$ref" =~ @sha256:[0-9a-f]{64}$ ]]; then
    continue
  fi
  if [[ "$ref" =~ :[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    continue
  fi
  echo "[check_release] 引用不可变校验失败: $ref" >&2
  fail=1
done

[ "$fail" -eq 0 ] && echo "[check_release] 通过"
exit "$fail"
