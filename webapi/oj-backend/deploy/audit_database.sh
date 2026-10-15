#!/usr/bin/env bash
# 只读数据库审计（对齐旧后端 deploy/audit_database.py）。
# 检查应用账号权限是否最小化、连接数与复制槽等，不修改任何数据。
#
# 用法: OJ_DB_URL=postgresql://oj:pass@host:5432/oj ./audit_database.sh
set -euo pipefail

: "${OJ_DB_URL:?需要 OJ_DB_URL（postgresql://user:pass@host:port/db）}"

psql "$OJ_DB_URL" -v ON_ERROR_STOP=1 <<'SQL'
\echo === 超级用户角色（应尽量为空）===
select rolname from pg_roles where rolsuper and rolname not like 'pg_%';

\echo === 应用角色的建库/建角色权限（最小权限应无）===
select rolname, rolcreatedb, rolcreaterole from pg_roles
where rolname = current_user;

\echo === 当前连接数 / 上限 ===
select count(*) as connections,
       current_setting('max_connections') as max_connections
from pg_stat_activity;

\echo === 复制槽（异常遗留会撑大 WAL）===
select slot_name, active, restart_lsn from pg_replication_slots;

\echo === 未使用索引最多的表（可辅助优化）===
select relname from pg_stat_user_tables order by seq_scan desc limit 10;
SQL
