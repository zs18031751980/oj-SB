#!/usr/bin/env bash
# 将已验证的提交快进到发布分支（对齐旧后端 deploy/promote_release.py）。
# 用法: REMOTE=origin TARGET=main ./promote_release.sh <commit-sha>
# 仅允许快进（--ff-only），避免发布分支出现分叉历史。
set -euo pipefail

REMOTE="${REMOTE:-origin}"
TARGET="${TARGET:-main}"
COMMIT="${1:-}"

if [ -z "$COMMIT" ]; then
  echo "用法: $0 <commit-sha>" >&2
  exit 2
fi

git fetch "$REMOTE" "$TARGET"
# 校验目标分支确实是该提交的祖先（可快进）
if ! git merge-base --is-ancestor "$REMOTE/$TARGET" "$COMMIT"; then
  echo "[promote] 不能快进：$REMOTE/$TARGET 不是 $COMMIT 的祖先" >&2
  exit 1
fi

git push "$REMOTE" "$COMMIT:$TARGET"
echo "[promote] 已将 $COMMIT 快进到 $REMOTE/$TARGET"
