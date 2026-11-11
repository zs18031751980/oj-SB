#!/usr/bin/env bash
# 沙箱隔离自检（需 Docker）。在具备 Docker 的测试机/CI 上运行。
# 校验：network=none、只读根、cap-drop ALL、no-new-privileges、pids/memory 限制，
#       以及容器内无法写根目录、无法访问外网。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${SANDBOX_IMAGE:-letcoding-sandbox:check}"
NAME="oj-sandbox-check-$$"
cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "[sandbox-check] 构建镜像 $IMAGE"
docker build -q -f "$ROOT/Dockerfile.sandbox" -t "$IMAGE" "$ROOT" >/dev/null

echo "[sandbox-check] 以判题参数启动探针容器"
docker run -d --name "$NAME" --network=none --read-only --cap-drop=ALL \
  --security-opt=no-new-privileges --pids-limit=128 \
  --memory=256m --memory-swap=256m --cpus=1 \
  --user 0:0 --tmpfs /tmp:rw,nosuid,nodev,noexec,size=64m \
  --entrypoint sleep "$IMAGE" 300 >/dev/null

echo "[sandbox-check] 校验容器配置"
docker inspect "$NAME" | python3 -c '
import json, sys
h = json.load(sys.stdin)[0]["HostConfig"]
checks = {
  "network=none": h.get("NetworkMode") == "none",
  "read-only root": h.get("ReadonlyRootfs") is True,
  "cap-drop ALL": "ALL" in (h.get("CapDrop") or []),
  "no-new-privileges": any("no-new-privileges" in s for s in (h.get("SecurityOpt") or [])),
  "pids-limit=128": h.get("PidsLimit") == 128,
  "memory=256m": h.get("Memory") == 256 * 1024 * 1024,
}
for name, ok in checks.items():
    print(("PASS " if ok else "FAIL ") + name)
if not all(checks.values()):
    sys.exit(1)
'

echo "[sandbox-check] 校验只读根文件系统"
if docker exec "$NAME" sh -c 'touch /probe' >/dev/null 2>&1; then
  echo "FAIL 根文件系统可写" >&2
  exit 1
fi
echo "PASS 根文件系统只读"

echo "[sandbox-check] 校验网络隔离"
if docker exec "$NAME" python3 -c "import socket; socket.create_connection(('1.1.1.1', 53), 2)" >/dev/null 2>&1; then
  echo "FAIL 网络未隔离" >&2
  exit 1
fi
echo "PASS 网络隔离"

echo "[sandbox-check] 全部通过"
