# iOS Club 代码综合平台

iOS Club 代码综合平台集**在线编程、代码评测、学习资源与公告管理**于一体，为社团成员提供编程学习与实践环境。
后端已从 Flask/FastAPI 迁移为 **Spring Boot 3.5 + Java 25 多模块工程**，判题统一走远程执行服务（Judge0）或 Docker 沙箱。

## 核心功能

| 模块 | 说明 |
|------|------|
| **在线编程** | Monaco Editor 驱动的在线代码编辑器，支持 C/C++/Java/Python/Go/JavaScript |
| **代码评测** | 题目浏览、代码提交、测试用例验证、ACM/OI 赛制与部分分 |
| **比赛** | 报名、封榜/解封、榜单快照、复判、题包校验、答疑、审计、队伍 |
| **学习资源** | Markdown 驱动的学习文档，支持 Mermaid、代码高亮、任务列表 |
| **用户系统** | OAuth/OIDC（iOSClub、GitHub）、本地密码登录、JWT + 服务端会话 |
| **公告管理** | 草稿/发布、按权限分级可见 |
| **主题切换** | 浅色/深色/跟随系统，偏好持久化 |

## 技术架构

```
┌────────────────────────────────────────────────────────────┐
│                        前端 (LetApp)                       │
│  Vue 3 + TypeScript + Vite + Pinia + Vue Router            │
│  Naive UI / Tailwind CSS / Monaco Editor / ECharts         │
├────────────────────────────────────────────────────────────┤
│                     HTTP / REST（JSON）                    │
├────────────────────────────────────────────────────────────┤
│                 后端 (Spring Boot 3.5 / Java 25)           │
│  oj-common          JWT、TOTP、异常/响应模型、队列常量     │
│  oj-core            JPA 实体与 Repository                  │
│  oj-infrastructure  判题执行客户端、Redis 队列、编译缓存   │
│  oj-api             REST 控制器、安全过滤器链、投影/派发   │
│  oj-worker          判题/参考解校验 Worker                 │
│                                                            │
│  安全链：RequestId → CSRF → Origin → 安全头 → Metrics 认证 │
│          → Spring Security（JWT + 服务端会话双重校验）     │
├────────────────────────────────────────────────────────────┤
│                        执行与数据                          │
│  PostgreSQL（Flyway 迁移）  Redis（队列/会话/限流/缓存）   │
│  Judge0 CE（远程隔离执行）或 Docker 沙箱执行器             │
└────────────────────────────────────────────────────────────┘
```

### 认证流程

```
浏览器 ──→ GET /auth/login/{provider}（state，可选 PKCE）
        ──→ 供应商授权 ──→ GET /auth/callback/{provider}
        ──→ 一次性 exchange code（不把令牌放 URL）
        ──→ POST /auth/exchange ──→ 访问令牌 + HttpOnly 刷新 Cookie
```

- 访问令牌为 JWT（`user_id/sid/jti/iss/aud`），同时校验服务端 `auth_sessions`，支持刷新令牌轮换与 30 秒重放恢复。
- 角色归一化为 `manager` / `staff` / `member`；比赛内另有 `director` / `jury` / `setter` / `operator` 能力。
- OAuth 授权码流程默认可用；第三方密码模式（`/auth/login/{provider}/password`）可通过 `OJ_OAUTH_PASSWORD_GRANT_ENABLED` 关闭。

### 判题执行

| 执行器 | 配置 | 用途 |
|--------|------|------|
| `judge0` | `OJ_JUDGE_EXECUTOR=judge0` + `OJ_JUDGE0_ENABLED=true` | 生产默认，远程隔离执行 |
| `sandbox` | `OJ_JUDGE_EXECUTOR=sandbox` | Docker 沙箱（网络隔离、只读根、cap-drop、资源限制） |
| `local` | `OJ_JUDGE_EXECUTOR=local` + `OJ_ALLOW_LOCAL_JUDGE=true` | **仅本地开发**，无沙箱隔离 |

> 生产环境禁止 `local`：启动脚本与应用启动自检都会直接拒绝。

## 技术栈

### 后端
- **框架**：Spring Boot 3.5、Spring Security、Spring Data JPA、Flyway
- **语言/构建**：Java 25、Maven 多模块（`oj-common/oj-core/oj-infrastructure/oj-api/oj-worker`）
- **认证**：OAuth2/OIDC、JWT（jjwt）、TOTP（RFC6238）、BCrypt + 旧 Werkzeug 哈希兼容
- **缓存/队列**：Redis（可靠队列、租约、并发槽、限流、缓存）
- **判题**：Judge0 CE / Docker 沙箱 / 本地（仅开发）
- **可观测**：Micrometer + Prometheus、结构化 JSON 日志、请求 ID

### 前端
- Vue 3 + TypeScript、Vite 7、Naive UI、Tailwind CSS 4
- Monaco Editor、ECharts、Iconify、Pinia 3、Vue Router 4

## 项目结构

```
.
├── webapi/oj-backend/               # Spring Boot 多模块后端
│   ├── oj-common/                   # 通用能力（JWT/TOTP/异常）
│   ├── oj-core/                     # JPA 实体与仓储
│   ├── oj-infrastructure/           # 执行客户端/队列/编译缓存
│   ├── oj-api/                      # REST API 与安全过滤器
│   ├── oj-worker/                   # 判题 Worker
│   ├── deploy/                      # 运维脚本与告警/代理示例
│   ├── docker-compose.yml           # 生产参考编排（凭据全部走环境变量）
│   └── Dockerfile / Dockerfile.worker / Dockerfile.sandbox
├── webapp/letapp/                   # Vue 前端
│   └── src/{pages,components,layouts,stores,services,composables,types}
├── start.sh                         # 本地一键启动（含环境激活）
├── .env.example                     # 后端环境变量模板
└── README.md / LICENSE
```

## API 概览

| 模块 | 关键端点 |
|------|----------|
| 认证 | `POST /auth/login/password`、`GET /auth/login/{provider}`、`GET /auth/callback/{provider}`、`POST /auth/exchange`、`POST /auth/refresh`、`POST /auth/logout`、`GET /auth/verify`、`GET /auth/providers` |
| 用户 | `GET/PATCH /users/me`、`GET /users/me/stats`、`POST /users/me/avatar` |
| 题库/提交 | `GET /problems`、`GET /problems/{id}`、`POST /submissions`、`GET /submissions`、`POST /code/run`、`POST /code/run/public` |
| 比赛 | `GET/POST /contests`、`POST /contests/{id}/publish|cancel|join`、`POST /contests/{id}/problems/{pid}/submit`、`GET /contests/{id}/rankings`、`GET/POST /contests/{id}/clarifications`、复判/题包/队伍/角色/审计 |
| 讨论/收藏/学习 | `/discussions`、`/favorites`、`/learn-favorites`、`/learn-history`、`/learn-resources` |
| 管理 | `/admin/stats`、`/admin/users`、`PATCH /admin/users/{id}/role|status`、`/admin/contests`、`/admin/judge/dead-letters`、`POST /admin/audit/export` |
| 运维 | `GET /healthz`、`GET /readyz`、`GET /healthz/db`、`GET /healthz/judge?pool=`、`GET /metrics`、`GET /actuator/prometheus` |

在线文档：启动后访问 `/swagger-ui/index.html` 或 `/v3/api-docs`。

## 环境变量

完整模板见 [`.env.example`](.env.example)。核心项：

```bash
APP_ENV=production                  # development|production

# 数据存储
OJ_DB_URL=jdbc:postgresql://127.0.0.1:5432/oj
OJ_DB_USERNAME=oj
OJ_DB_PASSWORD=change-me
OJ_REDIS_HOST=127.0.0.1
OJ_REDIS_PORT=6379
OJ_REDIS_PASSWORD=

# 认证
OJ_JWT_SECRET=<至少 32 位随机串>
OJ_FRONTEND_URL=https://oj.example.com
OJ_COOKIE_SECURE=true
# OAuth（按需）
OJ_OAUTH_PROVIDERS=iOSClub,github
OJ_OAUTH_IOSCLUB_CLIENT_ID=...
OJ_OAUTH_IOSCLUB_CLIENT_SECRET=...
OJ_OAUTH_GITHUB_CLIENT_ID=...
OJ_OAUTH_GITHUB_CLIENT_SECRET=...

# 判题（生产）
OJ_JUDGE_ENABLED=true
OJ_JUDGE_EXECUTOR=judge0
OJ_JUDGE0_ENABLED=true
OJ_JUDGE0_URL=http://judge0-server:2358
OJ_ALLOW_LOCAL_JUDGE=false

# 监控
METRICS_TOKEN=<随机令牌>
OJ_METRICS_ALLOW_ANONYMOUS=false
# 仅可信代理后开启
OJ_TRUST_FORWARDED_FOR=false
```

## 本地开发

```bash
./start.sh up        # 启动 PostgreSQL + Redis + API + Worker + 前端
./start.sh status
./start.sh logs api
./start.sh down
```

`start.sh` 会自动激活 JDK 25、Node/npm、Maven 与 UTF-8 locale；启动前复制 `.env.example` 为 `.env` 按需修改。

本地默认**不启用**无沙箱判题。若需在无 Judge0/Docker 的机器上调试判题，显式开启：

```bash
OJ_ALLOW_LOCAL_JUDGE=true OJ_JUDGE_EXECUTOR=local ./start.sh up
```

## 生产部署与安全要求

1. **所有密钥通过 Secret/环境变量注入**，禁止提交真实凭据；占位 JWT/指标令牌会在启动时被拒绝。
2. **禁止本机执行**：`APP_ENV=production` 且 `OJ_ALLOW_LOCAL_JUDGE=true`（或执行器为 `local`）时 API/Worker 直接拒绝启动。
3. **Judge0 端口不对外**：默认仅绑定 `127.0.0.1:2358`；Judge0 部署在专用执行节点，按版本要求尽量降低权限。
4. **监控端点统一鉴权**：`/metrics`、`/actuator/metrics/**`、`/actuator/prometheus` 均需 `METRICS_TOKEN`；生产未配置令牌时启动失败（除非显式允许匿名）。
5. **可信代理**：仅当反向代理会覆盖 `X-Forwarded-For` 时设置 `OJ_TRUST_FORWARDED_FOR=true`，否则限流使用真实远端地址。
6. **路径安全**：学习资源 id 在规范化后必须位于资源根目录内，已对 `..`、绝对路径与编码斜杠做回归测试。
7. **运维脚本**：`webapi/oj-backend/deploy/` 提供 `preflight.sh`、`smoke.sh`、`check_release.sh`、`promote_release.sh`、`set-role.sh`、`archive_wal.sh`、`audit_database.sh`、`recovery.sh`、`restored_smoke.sh`、`calibrate_judge.sh`，以及 `alerts.yaml`、`nginx.conf.example`、`postgresql-recovery.conf.example`。

### 数据库与迁移

Flyway 在 API 启动时自动执行 `oj-api/src/main/resources/db/migration` 下的迁移；Worker 关闭 Flyway。生产建议先运行 API 完成迁移，再滚动启动 Worker。

## 测试

```bash
# 后端单元/集成测试（Testcontainers 在无 Docker 时自动跳过）
cd webapi/oj-backend
JAVA_HOME=~/.jdk/jdk-25.0.2 mvn test

# 前端 E2E（Playwright，需先启动本地栈）
cd webapp/letapp
CI=1 npx playwright test
```

安全相关测试覆盖：公告可见性、监控鉴权、可信代理 IP、学习资源路径穿越、本机执行开关、启动密钥校验。

## 许可证

MIT License - 详见 [LICENSE](LICENSE)
