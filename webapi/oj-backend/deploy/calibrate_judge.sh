#!/usr/bin/env bash
# 判题节点能力标定（对齐旧后端 deploy/calibrate_judge.py 的轻量版）。
# 用固定样例程序测量本机编译+运行吞吐，输出建议的单机并发上限。
#
# 用法: ./calibrate_judge.sh [rounds]
set -euo pipefail

ROUNDS="${1:-5}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/main.cpp" <<'CPP'
#include <bits/stdc++.h>
int main(){ long long s=0; for(int i=0;i<1000000;i++) s+=i; std::cout<<s<<"\n"; }
CPP

echo "[calibrate] 编译耗时测量（$ROUNDS 轮，g++ 可用时）"
if command -v g++ >/dev/null 2>&1; then
  for i in $(seq 1 "$ROUNDS"); do
    /usr/bin/time -f "compile %e s" g++ -O2 -o "$TMP/main" "$TMP/main.cpp" >/dev/null 2>>"$TMP/compile.log" || true
  done
  awk '/compile/{sum+=$2; n++} END{if(n>0) printf "[calibrate] 平均编译 %.3f s\n", sum/n}' "$TMP/compile.log" || true
else
  echo "[calibrate] 未找到 g++，跳过；建议在有判题工具链的节点执行"
fi

echo "[calibrate] CPU 核数: $(nproc 2>/dev/null || echo unknown)"
echo "[calibrate] 建议单机执行并发 = min(核数, 8)，并按内存预留调整"
