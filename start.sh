#!/usr/bin/env bash
# 一键启动脚本：激活运行环境并拉起 PostgreSQL + Redis + Spring API + Judge Worker + Vue 前端。
#
# 用法：
#   ./start.sh up        启动全部（默认）
#   ./start.sh down      停止全部
#   ./start.sh restart   重启
#   ./start.sh status    查看状态
#   ./start.sh logs api  跟踪日志（api|worker|web|pg|redis）
#   ./start.sh env       仅激活并打印运行环境（JDK/Node/Maven 等）
#   ./start.sh build     仅构建后端 jar
#
# 配置来自仓库根目录的 .env（OJ_* 变量，见 .env.example），未提供时使用本地开发默认值。
# 本地判题使用无隔离的本机执行（OJ_ALLOW_LOCAL_JUDGE / OJ_JUDGE_EXECUTOR=local），请勿用于生产。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$ROOT/webapi/oj-backend"
FRONTEND="$ROOT/webapp/letapp"
ENV_FILE="$ROOT/.env"
RUNTIME="$ROOT/.local-run"
LOGS="$RUNTIME/logs"
PGSOCK="/tmp/oj-local-pgsock"   # PostgreSQL socket 目录不能含空格，故放 /tmp
PGDATA="$RUNTIME/pg"

PG_PORT=5432
REDIS_PORT=6379
API_PORT=6173
WEB_PORT=5173
DB_NAME=oj
DB_USER=oj
DB_PASS=oj

die() { echo "错误: $*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 环境激活
# ---------------------------------------------------------------------------
find_bin() {
  command -v "$1" 2>/dev/null || { [ -x "$HOME/.nix-profile/bin/$1" ] && echo "$HOME/.nix-profile/bin/$1"; } || true
}

# 取 java 主版本号（如 25）
java_major() { "$1" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1; }

activate_env() {
  # 1) 用户级工具（nix profile）与系统 PATH
  [ -d "$HOME/.nix-profile/bin" ] && export PATH="$HOME/.nix-profile/bin:$PATH"
  # 2) 中文路径需要 UTF-8 locale
  export LC_ALL="${LC_ALL:-C.UTF-8}" LANG="${LANG:-C.UTF-8}"

  # 3) 优先选择 JDK 25（项目 java.version=25）
  local jdk="${JAVA_HOME:-}" candidate
  if [ -n "$jdk" ] && [ -x "$jdk/bin/java" ] && [ "$(java_major "$jdk/bin/java")" = "25" ]; then
    :
  else
    jdk=""
    for candidate in "$HOME"/.jdk/jdk-25* /usr/lib/jvm/*25* /opt/jdk-25* /opt/*/jdk-25*; do
      [ -x "$candidate/bin/java" ] && { jdk="$candidate"; break; }
    done
    if [ -z "$jdk" ]; then
      candidate="$(command -v java || true)"
      [ -n "$candidate" ] && [ "$(java_major "$candidate")" = "25" ] && jdk="$(dirname "$(dirname "$(readlink -f "$candidate")")")"
    fi
  fi
  [ -n "$jdk" ] || die "未找到 JDK 25。请设置 JAVA_HOME 指向 JDK 25，或安装到 ~/.jdk/jdk-25*。"
  export JAVA_HOME="$jdk"
  export PATH="$JAVA_HOME/bin:$PATH"

  # 4) 校验 Node / npm / Maven
  NPM="$(find_bin npm  || true)"
  MVN="$(find_bin mvn  || true)"
  command -v node >/dev/null 2>&1 || die "未找到 node（建议 ≥ 20）。"
  [ -n "$NPM" ] || die "未找到 npm。"
  [ -n "$MVN" ] || die "未找到 maven（mvn 或 mvnw）。后端构建需要它。"
}

print_env() {
  echo "JAVA_HOME = $JAVA_HOME"
  echo "java      = $(java -version 2>&1 | head -1)"
  echo "node      = $(command -v node) $(node -v)"
  echo "npm       = $NPM $(npm -v 2>/dev/null)"
  echo "mvn       = $MVN"
  echo "locale    = $LC_ALL"
}

# ---------------------------------------------------------------------------
# 配置加载（.env 覆盖默认值；未设置项落地为本地默认）
# ---------------------------------------------------------------------------
# 安全解析 dotenv 风格文件：不执行内容，仅按 KEY=VALUE 导出（去除可选引号）
load_env_file() {
  local line key val
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in ''|'#'*) continue;; esac
    line="${line#export }"
    case "$line" in *=*) ;; *) continue;; esac
    key="${line%%=*}"; val="${line#*=}"
    key="${key//[[:space:]]/}"
    [ -n "$key" ] || continue
    case "$val" in
      \"*\") val="${val#\"}"; val="${val%\"}";;
      \'*\') val="${val#\'}"; val="${val%\'}";;
    esac
    # 仅导入本项目相关变量，避免旧版 .env 的同名变量污染本地配置
    case "$key" in
      OJ_*|IOSCLUB_CLIENT_SECRET|GITHUB_CLIENT_ID|GITHUB_CLIENT_SECRET) export "$key=$val";;
    esac
  done < "$1"
}

load_config() {
  if [ -f "$ENV_FILE" ]; then
    echo "[env] 加载 $ENV_FILE"
    load_env_file "$ENV_FILE"
  else
    echo "[env] 未找到 $ENV_FILE，使用本地默认配置（可参考 .env.example）"
  fi

  # 本地基础设施默认值
  : "${OJ_DB_URL:=jdbc:postgresql://127.0.0.1:${PG_PORT}/${DB_NAME}}"
  : "${OJ_DB_USERNAME:=$DB_USER}"
  : "${OJ_DB_PASSWORD:=$DB_PASS}"
  : "${OJ_REDIS_HOST:=127.0.0.1}"
  : "${OJ_REDIS_PORT:=$REDIS_PORT}"
  : "${OJ_REDIS_PASSWORD:=}"
  : "${OJ_API_PORT:=$API_PORT}"
  : "${OJ_FRONTEND_URL:=http://127.0.0.1:${WEB_PORT}}"
  : "${OJ_COOKIE_SECURE:=false}"
  : "${APP_ENV:=development}"
  # 安全默认：不自动启用无沙箱的本机代码执行；本地开发需显式 OJ_ALLOW_LOCAL_JUDGE=true
  : "${OJ_ALLOW_LOCAL_JUDGE:=false}"
  : "${OJ_JUDGE_ENABLED:=true}"
  if [ "$OJ_ALLOW_LOCAL_JUDGE" = "true" ]; then
    OJ_JUDGE_EXECUTOR="${OJ_JUDGE_EXECUTOR:-local}"
    OJ_JUDGE0_ENABLED="${OJ_JUDGE0_ENABLED:-false}"
  else
    OJ_JUDGE_EXECUTOR="${OJ_JUDGE_EXECUTOR:-judge0}"
    OJ_JUDGE0_ENABLED="${OJ_JUDGE0_ENABLED:-true}"
  fi
  : "${OJ_JUDGE0_URL:=http://127.0.0.1:2358}"
  : "${OJ_TRUST_FORWARDED_FOR:=false}"
  : "${OJ_METRICS_ALLOW_ANONYMOUS:=false}"
  : "${OJ_LEARN_ROOT:=$FRONTEND/learn_source}"
  : "${OJ_JWT_SECRET:=0123456789abcdef0123456789abcdef0123456789abcdef}"

  # 生产环境第一道防线：禁止无沙箱的本机执行
  if [ "$APP_ENV" = "production" ] && [ "$OJ_ALLOW_LOCAL_JUDGE" = "true" ]; then
    die "生产环境禁止启用无沙箱的本机代码执行（OJ_ALLOW_LOCAL_JUDGE=true）"
  fi

  # OAuth：兼容旧版 .env 的 IOSCLUB_* / GITHUB_* 命名
  if [ -z "${OJ_OAUTH_IOSCLUB_CLIENT_SECRET:-}" ] && [ -n "${IOSCLUB_CLIENT_SECRET:-}" ]; then
    OJ_OAUTH_IOSCLUB_CLIENT_SECRET="$IOSCLUB_CLIENT_SECRET"
  fi
  if [ -z "${OJ_OAUTH_GITHUB_CLIENT_ID:-}" ] && [ -n "${GITHUB_CLIENT_ID:-}" ]; then
    OJ_OAUTH_GITHUB_CLIENT_ID="$GITHUB_CLIENT_ID"
  fi
  if [ -z "${OJ_OAUTH_GITHUB_CLIENT_SECRET:-}" ] && [ -n "${GITHUB_CLIENT_SECRET:-}" ]; then
    OJ_OAUTH_GITHUB_CLIENT_SECRET="$GITHUB_CLIENT_SECRET"
  fi

  # 本地 OAuth 默认（回调需在提供方白名单登记 127.0.0.1 才能用第三方登录）
  if [ -n "${OJ_OAUTH_IOSCLUB_CLIENT_ID:-}" ]; then
    : "${OJ_OAUTH_IOSCLUB_ISSUER:=https://api.xauat.site}"
    : "${OJ_OAUTH_IOSCLUB_SCOPE:=openid profile role}"
    : "${OJ_OAUTH_IOSCLUB_REDIRECT_URI:=http://127.0.0.1:${OJ_API_PORT}/auth/callback/iOSClub}"
  fi
  if [ -n "${OJ_OAUTH_GITHUB_CLIENT_ID:-}" ]; then
    : "${OJ_OAUTH_GITHUB_AUTHORIZATION_URI:=https://github.com/login/oauth/authorize}"
    : "${OJ_OAUTH_GITHUB_TOKEN_URI:=https://github.com/login/oauth/access_token}"
    : "${OJ_OAUTH_GITHUB_USERINFO_URI:=https://api.github.com/user}"
    : "${OJ_OAUTH_GITHUB_SCOPE:=read:user user:email}"
    : "${OJ_OAUTH_GITHUB_REDIRECT_URI:=http://127.0.0.1:${OJ_API_PORT}/auth/callback/github}"
  fi
  local providers=()
  [ -n "${OJ_OAUTH_IOSCLUB_CLIENT_ID:-}" ] && providers+=("iOSClub")
  [ -n "${OJ_OAUTH_GITHUB_CLIENT_ID:-}" ] && providers+=("github")
  [ ${#providers[@]} -gt 0 ] && OJ_OAUTH_PROVIDERS="${OJ_OAUTH_PROVIDERS:-$(IFS=,; echo "${providers[*]}")}"

  export APP_ENV OJ_DB_URL OJ_DB_USERNAME OJ_DB_PASSWORD OJ_REDIS_HOST OJ_REDIS_PORT OJ_REDIS_PASSWORD \
         OJ_API_PORT OJ_FRONTEND_URL OJ_COOKIE_SECURE OJ_ALLOW_LOCAL_JUDGE OJ_JUDGE_ENABLED \
         OJ_JUDGE_EXECUTOR OJ_JUDGE0_ENABLED OJ_JUDGE0_URL OJ_TRUST_FORWARDED_FOR \
         OJ_METRICS_ALLOW_ANONYMOUS OJ_LEARN_ROOT OJ_JWT_SECRET OJ_OAUTH_PROVIDERS
  export OJ_OAUTH_IOSCLUB_CLIENT_ID OJ_OAUTH_IOSCLUB_CLIENT_SECRET OJ_OAUTH_IOSCLUB_ISSUER \
         OJ_OAUTH_IOSCLUB_SCOPE OJ_OAUTH_IOSCLUB_REDIRECT_URI
  export OJ_OAUTH_GITHUB_CLIENT_ID OJ_OAUTH_GITHUB_CLIENT_SECRET OJ_OAUTH_GITHUB_AUTHORIZATION_URI \
         OJ_OAUTH_GITHUB_TOKEN_URI OJ_OAUTH_GITHUB_USERINFO_URI OJ_OAUTH_GITHUB_SCOPE OJ_OAUTH_GITHUB_REDIRECT_URI
}

# ---------------------------------------------------------------------------
# 依赖进程
# ---------------------------------------------------------------------------
start_pg() {
  local PG_CTL INITDB PSQL
  PG_CTL="$(find_bin pg_ctl)"; INITDB="$(find_bin initdb)"; PSQL="$(find_bin psql)"
  for b in PG_CTL INITDB PSQL; do [ -n "${!b}" ] || die "缺少依赖: $b"; done

  if [ ! -d "$PGDATA" ]; then
    echo "[pg] 初始化数据目录 $PGDATA"
    "$INITDB" -D "$PGDATA" -U postgres --auth=trust --locale=C >/dev/null 2>&1
  fi
  if "$PG_CTL" -D "$PGDATA" status >/dev/null 2>&1; then echo "[pg] 已在运行"; else
    "$PG_CTL" -D "$PGDATA" -o "-p $PG_PORT -k $PGSOCK -c listen_addresses=127.0.0.1" -l "$LOGS/pg.log" start >/dev/null 2>&1
    echo "[pg] 启动 127.0.0.1:$PG_PORT"
  fi
  sleep 1
  "$PSQL" -h 127.0.0.1 -p "$PG_PORT" -U postgres -tAc "select 1 from pg_roles where rolname='$DB_USER'" | grep -q 1 \
    || "$PSQL" -h 127.0.0.1 -p "$PG_PORT" -U postgres -c "create role $DB_USER login password '$DB_PASS' superuser;" >/dev/null
  "$PSQL" -h 127.0.0.1 -p "$PG_PORT" -U postgres -tAc "select 1 from pg_database where datname='$DB_NAME'" | grep -q 1 \
    || "$PSQL" -h 127.0.0.1 -p "$PG_PORT" -U postgres -c "create database $DB_NAME owner $DB_USER;" >/dev/null
  PG_CTL_BIN="$PG_CTL"; PSQL_BIN="$PSQL"
}

start_redis() {
  local REDIS_SERVER REDIS_CLI
  REDIS_SERVER="$(find_bin redis-server)"; REDIS_CLI="$(find_bin redis-cli)"
  for b in REDIS_SERVER REDIS_CLI; do [ -n "${!b}" ] || die "缺少依赖: $b"; done
  if "$REDIS_CLI" -p "$REDIS_PORT" ping >/dev/null 2>&1; then echo "[redis] 已在运行"; else
    LC_ALL=C "$REDIS_SERVER" --daemonize yes --port "$REDIS_PORT" --dir "$RUNTIME/redis" --logfile "$LOGS/redis.log"
    echo "[redis] 启动 127.0.0.1:$REDIS_PORT"
  fi
  REDIS_CLI_BIN="$REDIS_CLI"
}

# ---------------------------------------------------------------------------
# 构建与启动
# ---------------------------------------------------------------------------
build_jars() {
  local api_jar="$BACKEND/oj-api/target/oj-api-0.1.0-SNAPSHOT.jar"
  local worker_jar="$BACKEND/oj-worker/target/oj-worker-0.1.0-SNAPSHOT.jar"
  if [ ! -f "$api_jar" ] || [ ! -f "$worker_jar" ]; then
    echo "[build] 构建 oj-api / oj-worker ..."
    (cd "$BACKEND" && "$MVN" -q -DskipTests -pl oj-api,oj-worker -am package)
  fi
}

wait_http() {
  local url="$1" name="$2"
  for _ in $(seq 1 90); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)" = "200" ] && { echo "[$name] 就绪"; return 0; }
    sleep 1
  done
  echo "[$name] 启动超时，查看 $LOGS" >&2; return 1
}

start_api() {
  if [ -f "$RUNTIME/api.pid" ] && kill -0 "$(cat "$RUNTIME/api.pid")" 2>/dev/null; then echo "[api] 已在运行"; return; fi
  setsid java -jar "$BACKEND/oj-api/target/oj-api-0.1.0-SNAPSHOT.jar" > "$LOGS/api.log" 2>&1 < /dev/null &
  echo $! > "$RUNTIME/api.pid"
  wait_http "http://127.0.0.1:${OJ_API_PORT}/readyz" api
}

seed_default_user() {
  # 首次运行创建本地测试账号 smoke / SmokePass123!（BCrypt）
  local hash='$2a$10$xlJrcAjC664UPvEsAUFdpOerlPmJxNJChZSpvZCwNrP62GqIMWJk2'
  if ! "$PSQL_BIN" -h 127.0.0.1 -p "$PG_PORT" -U "$DB_USER" -d "$DB_NAME" -tAc "select 1 from users limit 1" 2>/dev/null | grep -q 1; then
    "$PSQL_BIN" -h 127.0.0.1 -p "$PG_PORT" -U "$DB_USER" -d "$DB_NAME" -c \
      "insert into users (username,email,name,password_hash,is_active,role,theme_preference,created_at,updated_at) values ('smoke','smoke@example.com','Smoke','$hash',true,'manager','system',now(),now());" >/dev/null
    echo "[seed] 已创建本地账号 smoke / SmokePass123!"
  fi
}

start_worker() {
  if [ -f "$RUNTIME/worker.pid" ] && kill -0 "$(cat "$RUNTIME/worker.pid")" 2>/dev/null; then echo "[worker] 已在运行"; return; fi
  OJ_WORKER_POOL="${OJ_WORKER_POOL:-all}" \
  setsid java -jar "$BACKEND/oj-worker/target/oj-worker-0.1.0-SNAPSHOT.jar" > "$LOGS/worker.log" 2>&1 < /dev/null &
  echo $! > "$RUNTIME/worker.pid"
  sleep 4; echo "[worker] 启动（本机执行）"
}

start_frontend() {
  if [ -f "$RUNTIME/vite.pid" ] && kill -0 "$(cat "$RUNTIME/vite.pid")" 2>/dev/null; then echo "[web] 已在运行"; return; fi
  [ -d "$FRONTEND/node_modules" ] || (cd "$FRONTEND" && "$NPM" install)
  (cd "$FRONTEND" && setsid "$NPM" run dev -- --host 127.0.0.1 --port "$WEB_PORT" > "$LOGS/web.log" 2>&1 < /dev/null & echo $! > "$RUNTIME/vite.pid")
  wait_http "http://127.0.0.1:${WEB_PORT}/" web
}

stop_one() {
  local name="$1" pidfile="$2"
  if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
    kill "$(cat "$pidfile")" 2>/dev/null || true; echo "[$name] 已停止"; rm -f "$pidfile"
  fi
}

down() {
  stop_one web "$RUNTIME/vite.pid"
  stop_one worker "$RUNTIME/worker.pid"
  stop_one api "$RUNTIME/api.pid"
  local REDIS_CLI PG_CTL
  REDIS_CLI="$(find_bin redis-cli || true)"; PG_CTL="$(find_bin pg_ctl || true)"
  [ -n "$REDIS_CLI" ] && "$REDIS_CLI" -p "$REDIS_PORT" shutdown nosave >/dev/null 2>&1 || true; echo "[redis] 已停止"
  [ -n "$PG_CTL" ] && "$PG_CTL" -D "$PGDATA" stop -m fast >/dev/null 2>&1 || true; echo "[pg] 已停止"
}

status() {
  local PG_CTL REDIS_CLI
  PG_CTL="$(find_bin pg_ctl || true)"; REDIS_CLI="$(find_bin redis-cli || true)"
  echo "API:      http://127.0.0.1:${OJ_API_PORT}  ($(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${OJ_API_PORT}/readyz" 2>/dev/null || echo down))"
  echo "Frontend: http://127.0.0.1:${WEB_PORT}  ($(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${WEB_PORT}/" 2>/dev/null || echo down))"
  echo "Postgres: 127.0.0.1:${PG_PORT}  ($([ -n "$PG_CTL" ] && "$PG_CTL" -D "$PGDATA" status >/dev/null 2>&1 && echo up || echo down))"
  echo "Redis:    127.0.0.1:${REDIS_PORT}  ($([ -n "$REDIS_CLI" ] && "$REDIS_CLI" -p "$REDIS_PORT" ping 2>/dev/null || echo down))"
}

up() {
  mkdir -p "$LOGS" "$PGSOCK" "$RUNTIME/redis"
  start_pg; start_redis; build_jars; start_api; seed_default_user; start_worker; start_frontend
  echo
  echo "前端 http://127.0.0.1:${WEB_PORT}    API http://127.0.0.1:${OJ_API_PORT}"
  if [ "$OJ_ALLOW_LOCAL_JUDGE" = "true" ]; then
    echo "判题：本机无隔离执行（仅限本地开发，APP_ENV=$APP_ENV）"
  else
    echo "判题：Judge0（$OJ_JUDGE0_URL）；如需本地无沙箱判题请显式设置 OJ_ALLOW_LOCAL_JUDGE=true"
  fi
  echo "本地登录账号：smoke / SmokePass123!（在登录表单用「账号+密码」登录）"
  if [ -n "${OJ_OAUTH_IOSCLUB_CLIENT_ID:-}" ] || [ -n "${OJ_OAUTH_GITHUB_CLIENT_ID:-}" ]; then
    echo "提示：OAuth 回调默认指向 127.0.0.1，若未在提供方白名单登记，本地点击第三方登录会报「无效的回调地址」。"
  fi
}

activate_env
case "${1:-up}" in
  env)     print_env;;
  up)      load_config; up;;
  down)    load_config; down;;
  build)   load_config; build_jars; echo "[build] 完成";;
  restart) load_config; down; sleep 1; up;;
  status)  load_config; status;;
  logs)    tail -n 50 -f "$LOGS/${2:-api}.log";;
  *)       echo "用法: $0 {up|down|restart|status|logs [api|worker|web|pg|redis]|env|build}"; exit 1;;
esac
