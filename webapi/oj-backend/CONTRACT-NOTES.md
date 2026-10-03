# 前后端契约要点（对接参考）

来源：`/home/shuai/桌面/code/oj/webapp/letapp/src/services/api.ts` + `stores/auth.ts`，以及参考 Flask 后端
`/home/shuai/桌面/code/oj/webapi/fastapi_of_letcoing/controllers/*`。

## 全局规则
- **无全局响应包裹**：成功返回纯 JSON 对象/数组，前端靠 HTTP 状态码判断错误。
- **字段名混用**：DB 风格字段用 snake_case（`problem_id`、`created_at`、`is_published`、`per_page`、`like_count`…）；
  `ProblemDetailData` 和 `Submission.testcase_results[]` 用 camelCase（`inputFormat`、`outputFormat`、`timeLimit`、
  `testCaseCount`、`testCaseIndex`、`actualOutput`）。
- Spring 不要设置 `/api` context-path，也不要用全局 `property-naming-strategy`。
- 尾斜杠兼容：Controller 统一写 `@GetMapping({"", "/"})`。

## 已修复（本轮，均可编译）
- **ContestController**：`solved()` 返回 int，修复编译。
- **CurrentUser**：新增 `optional(authorization)`，供公开接口计算 `is_liked`。
- **Discussion**：`view`/`replyView` 补全 `author_id/author_name/is_liked/is_pinned/is_closed/created_at`；
  list 支持 `category`/`limit`/`offset`，content 截断 200；`{id}/like` 与 `{id}/replies` 接受 `{liked}` 并返回
  `{liked, like_count}`。
- **Announcement**：list 支持 `include_unpublished`（仅 manager）；DTO 返回 `is_published`；请求体接收 `is_published`。
- **Problem（/problems）**：list 返回 `{data, total}`；summary 补 `category/categoryLabel/tags/sourceNumber/accept_count/submission_count`。
- **User /users/me/stats**：返回 `{solved, submissions, favorites}`。
- **Ranking**：`view` 补 `username/avatar_url`（注入 UserRepository）。
- **UserCode /user/code/{id}**：未找到返回 `{code: null}`（200），而非 404。
- **Learning**：record/clear 返回 `success: true`。

## 本轮补齐（已实现并通过单元测试）
1. **Problem 静态题库 + 比赛库合并**：`StaticProblemCatalog` 加载 `resources/catalog/problems.json`（由旧 `pages/problem_data.py`
   + `c_language_problems.py` 导出，48 题）；`ProblemCatalogService` 合并静态题与已结束比赛题目，library id 仍为 `1_000_000 + contest_problem_id`。
   `detail` 返回 `samples/testCaseCount/isLibrary/contestProblemId/contestId/contestTitle`；`StaticProblemSeeder` 启动时幂等补齐
   `problems`/`testcases` 并重置序列，供普通提交与判题使用。
2. **Submission detail**：`JudgeResultView` 将 `testcase_results` 解析为
   `[{testCaseIndex,passed,stdout,stderr,input,expected,actualOutput}]`，并补 `fail_testcase_index`、`compile_error`。
3. **Auth 契约**：`WerkzeugCompatiblePasswordEncoder`（scrypt/pbkdf2，旧哈希验证成功后自动升级 BCrypt）；
   JWT 补齐 `user_id/sid/jti/iss/aud/type`；access/refresh 分离，服务端按 `auth_sessions` 校验撤销与过期；
   `/auth/refresh` 支持 `{remember}` + `Idempotency-Key` + 悲观锁 + 30 秒重放恢复（密文存储）；
   Cookie 名/路径恢复为 `letcoding_refresh` / `/auth`；`/auth/login` 恢复为返回 `authorization_url`。
4. **Discuss 回复** 分页返回裸数组；点赞 body `{liked}`。
5. **Contest** 题面公开 DTO 补齐 `description/input_desc/output_desc/time_limit/memory_limit/difficulty/testcase_count/samples`。
6. **提交状态字符串**：Worker 产出规范短码 `AC/WA/CE/TLE/MLE/OLE/RE/SIGSEGV/SystemError/Partial`，等待态 `Pending/Judging`。
7. **队列与 Worker**：队列名对齐契约 `judge_queue/contest_judge_queue/practice_judge_queue/rejudge_queue/testcase_gen_queue`；
   Outbox 按复判/正式/练习路由；Worker 轮询全部队列；`testcase_gen_queue` 由 `ConfiguredValidationExecutor` 执行参考代码验证并写回
   `validation_status`；检查器策略 `OutputChecker`（text/exact/tokens/float）与 OI 部分分。
8. **排行榜投影**：`RankingProjection` 周期聚合 AC 记录到 `user_judge_stats`，`/rankings`、`/users/me/stats` 不再为空。

## 第二轮补齐（已实现并通过单元测试）
1. **`POST /learn-resources/rescan`**：管理员重扫目录，补回唯一缺失路由。
2. **比赛榜单后台投影**：`ContestScoreboardService` + `ScoreboardProjection` 按 `scoreboard_requested_version` 重建
   LIVE/PUBLIC_FREEZE/FINAL 快照，`/contests/{id}/rankings` 只读快照；FINAL 快照不可变；判题结束会推进榜单版本。
3. **复判 apply/cancel**：`POST /contests/{id}/rejudges` 立即创建候选提交并入 `rejudge_queue`；
   `GET .../{batchId}` 返回 before/after；`POST .../{batchId}` 按 `{action:apply|cancel}` 应用，校验 attempt 栅栏并归档 `judgements`；
   override 现在推进 `attempt_id`。
4. **部署**：新增 `Dockerfile`（构建 oj-api/oj-worker，`SERVICE` 选择）、`.dockerignore`、根级
   `.github/workflows/backend.yml`（Temurin 25 + Maven test/package）；`oj-worker` 增加 spring-boot repackage。

## 第三轮：真实环境端到端冒烟（PostgreSQL 17 + Redis，JDK 25）
在 nix PostgreSQL 17 + Redis 上启动 `oj-api`/`oj-worker`，发现并修复以下**启动/运行期缺陷**：
1. `ReferenceValidationJobRepository.findByProblemIdOrderByCreatedAtDesc` → 应为 `findByProblem_Id...`（关联属性）。
2. `UserJudgeStats` 用关联作 `@Id` 导致启动失败；改为标量 `user_id`。
3. `claimPending()` 原生 SQL 已含 `FOR UPDATE SKIP LOCKED`，再叠加 `@Lock` 报 “Illegal attempt to set lock mode for a native query”；移除 `@Lock`。
4. `oj-worker` 未关闭 Flyway（无迁移资源）导致校验失败；`spring.flyway.enabled=false`。
5. `oj-worker` 缺 `ObjectMapper` bean（无 spring-web）与 `oj.auth.jwt-secret`；新增 `WorkerConfig` 与配置。
6. Disabled 执行器用 `@ConditionalOnMissingBean` 不可靠；改为 `@ConditionalOnProperty(matchIfMissing=true)`。
7. `/code/run/public`、`GET /learn-resources/**` 被 Spring Security 误拦；加入 permitAll。

验证通过：Flyway 迁移、静态题库 seed（48 题 / 2801 用例）、`/problems` 与详情、密码登录（scrypt 旧哈希）、`/auth/verify|refresh|logout`（sid 会话撤销、重放恢复）、`/users/me`、`/admin/contests` 比赛题目 CRUD、公开题面不含 `correct_answer`、outbox 投递到 `judge_queue`、参考验证投递到 `testcase_gen_queue`、Worker 轮询 claim/NACK/retry、榜单投影写入 `ranking_projection_state`。

## 第四轮：剩余项全部补齐
1. **复判 MFA / 双人复核**：`TotpService`（RFC6238 HMAC-SHA1，Base32，±1 时间窗）+ `JuryMFAState` 时间步持久化防重放；
   `JuryReauthService` 按 `JURY_REQUIRE_MFA`/`JURY_TOTP_SECRETS` 校验密码与动态码；`apply_rejudge` 按 `CONTEST_DUAL_REVIEW_MIN`
   要求另一名裁判独立审核（FINALIZED 一律敏感），override 同样需重新认证。
2. **比赛内权限**：`ContestPermissionService` 实现 User+Contest+Capability（director/jury/setter/operator），
   复判/题包/改判/控制等端点改为能力校验。
3. **题包全链路**：`PackageService` 递归排序键的 canonical JSON + SHA-256 摘要、结构校验、stage(PENDING)→Worker 验证→activate；
   `PackageValidator` 执行参考答案/输入验证器/已知错误程序验证；`PackageValidationDispatcher` 投递 `package:<digest>`；
   `ContestOperationController` 的 packages 路由对齐原契约（stage 202 / get / activate）。
4. **Docker 沙箱**：`DockerSandboxClient` 按原安全边界（network=none、read-only、cap-drop、no-new-privileges、内存/PID/输出限制）
   调用沙箱镜像内 `execution_runtime.py`；`SandboxJudgeExecutor` 一次编译、逐点只读运行，通过 `oj.judge.executor=sandbox` 切换。
5. **custom checker**：`Checker` 通过 Judge0 运行自定义检查器（stdin `{input,actual,expected}`，退出码 0/1 判定），
   不再是 `SystemError`。
6. **集成测试**：新增 Testcontainers PostgreSQL `RepositorySchemaTest`（Flyway + JPA 映射/派生查询校验），
   无 Docker 环境自动跳过；CI `mvn test` 在有 Docker 时执行。

## 第五轮：契约缺陷修复（实测驱动）
1. **请求字段绑定**：为 `SubmissionRequest/ContestRequest/CodeRequest/HistoryRequest/RoleRequest/RulesRequest/RejudgeRequest/ProfileRequest/HistoryRequest` 等补
   `@JsonProperty(snake_case)+@JsonAlias(camelCase)`；`ApiExceptionHandler` 增加 400（校验/不可读/类型）、404（未匹配路由）、409，并记录未处理异常。
2. **UserCode**：`PUT /user/code` 真正落库并执行「每题最多 5 个」淘汰；新增 `GET /user/code` 最近 5 条。
3. **比赛 DTO/列表**：`view` 补 `status/is_frozen/freeze_time/penalty_time/created_at/allowed_languages`；
   `GET /contests` 过滤 non-public 与 DRAFT/CANCELLED，支持 `?status=` 与排序。
4. **比赛生命周期**：detail 可见性（私有/DRAFT 非 manager → 404）；create/update 校验类型与时间区间、冻结区间、罚时并推断 status；
   delete 仅 DRAFT 且级联清理 judgments/outbox/submissions/testcases/problems/participants；join 校验公开与状态并写 entry 事件；
   publish 校验隐藏测试点并生成题包 digest。
5. **结算/榜单**：finalize 使用 `control` 能力、阻断未完成判题、先置 FINALIZED 再生成不可变 FINAL 快照并返回榜单；
   `kindFor` 支持 `FINAL:<final_revision>`；复判结算后生成新 FINAL 快照；rankings 增加可见性校验。
6. **可观测性/保留**：`QueueMetrics` 暴露 `oj_judge_queue_*` 指标并接入 Prometheus；`RetentionJob` 清理已处理死信与过期浏览记录；
   dead-letter 归档接口；事件按 audience/身份过滤；GitHub OAuth scope。
- 未移植：`compile_cache`（跨提交编译缓存，属性能优化；Judge0/沙箱已满足「一次提交只编译一次」）。

## 第六轮：链路自测 + Playwright E2E
- 新增后端链路自测脚本（57 项，覆盖 auth/problems/favorites/usercode/submissions/learning/announcement/discussion/contest/admin/packages/rankings/code），全部通过。
- 实测发现并修复：
  1. **CORS 缺失**：新增 `CorsConfigurationSource`（白名单来源 + 携带 Cookie），否则前端 dev 跨域全失败。
  2. **编码斜杠**：学习资源 id 含 `/` 以 `%2F` 传输被 Tomcat 拒绝；配置 `TomcatConnectorCustomizer`（`encodedSolidusHandling=decode`）、`WebSecurityCustomizer`（允许编码斜杠）并将 `learn-favorites` 路由改为 `{*resourcePath}`。
  3. **讨论创建** 返回 201（原为 200）。
- 新增 Playwright 规格 `webapp/letapp/tests/app-chains.spec.ts`（12 项，真实后端），与既有 10 项合计 **22 项全部通过**。
- 备注：前端唯一登录入口为 `/auth/login/{provider}/password`（provider=iOSClub），本地账号登录 API 存在但 UI 未接；E2E 通过后端 `/auth/login/password` 取 token 后注入 localStorage 验证登录态链路。
- 复跑新增：`tests/app-chains.spec.ts` 扩到 14 项（新增「个人资料界面更新」「管理后台创建公告」），全套 **24 项通过**；链路自测保持 57/57。
- 再次修复：`PATCH /users/me` 改为返回 `{success, user_info}`（旧后端契约，前端据此刷新用户信息），否则资料保存后界面不更新。

## 第七轮：按优先级补齐服务级能力
1. **安全边界**：新增 `OriginValidationFilter`（写请求 Origin 白名单/同源，否则 403）、`SecurityHeadersFilter`（nosniff/Referrer-Policy/DENY/HSTS/敏感路径 no-store）、
   关键请求 DTO 的 `@Size`（code≤128KiB、stdin≤1MiB、title/description 等）；登录增加账号维度限流 `AccountRateLimiter`（20/min，429）。
2. **比赛操作**：`POST /contests/{id}/teams` 支持 `member_ids`（1–3 人、开赛后禁止、批量报名/审计/榜单版本++）；
   答疑按 `jury` 能力、认领冲突 409、`broadcast` 写 public/entry 事件与审计；override 清空判题明细、OI 计分、写 `judgement` 事件。
3. **可观测性**：`/actuator/prometheus` 增加 `METRICS_TOKEN` 校验（`MetricsAuthFilter`）；
   新增 `oj_judge_queue_retry`、`oj_outbox_pending`、`oj_dependency_up{redis/postgres}` 指标；`/contests/{id}/health` 增加 waiting/oldest_wait/projection lag+stale/system errors；
   Worker 写 `judge:worker:<id>` 心跳（`WorkerHeartbeat`）。
4. **保留策略**：`RetentionJob` 清理已终态提交的已派发 outbox（≥7 天）与死信/浏览记录。

## 第八轮：并发/可靠性/可观测补强
1. **Contest 行锁**：`ContestRepository.findForUpdateById`（`SELECT ... FOR UPDATE`），并在 update/delete/publish/cancel/join/submit、thaw/finalize/rejudge/package/rules/team/role/override/答疑 等关键写路径使用，满足文档“锁 Contest”要求。
2. **幂等投递**：`JudgeQueue.publishOnce`（`judge:enqueued:<queue>:<jobId>` 去重），outbox/参考验证/题包验证分发器改用之，避免重试重复入队。
3. **就绪与指标**：`/readyz` 校验 DB/Redis，并按 `OJ_REQUIRE_WORKER=true` 校验存在新鲜 Worker 心跳（否则 503）；
   新增 `oj_judge_workers_alive`、`oj_outbox_oldest_seconds` 指标。
4. **审计导出**：`AuditExporter` + `POST /admin/audit/export`（不可变文件、只补不覆盖，目录由 `OJ_AUDIT_EXPORT_DIR` 指定）。
5. **结构化日志**：`JsonLogEncoder` + `logback-spring.xml`，输出 JSON 并对 password/token/secret/totp/authorization 脱敏，异常回退纯文本。

## 第九轮：正确性 + 外部登录 + 判题指标 + 部署
1. **榜单/状态过滤**：`ContestScoreboardService` 与 `/contests/{id}/statuses`、提交结果接口过滤 `contest_eligible` 与 `rejudge_of is null`，练习/题库提交不再混入比赛排名。
2. **外部登录 claims**：新增 `OidcClaims`（角色字段递归解析 + 归一化 + 账号状态）与 `ProviderUserSync`：新用户按最高角色写 `role/provider_role`，已有用户本地角色优先，供应商停用则停用账号并撤销会话；OAuth 回调/密码登录均接入。
3. **判题阶段列（V4）**：`contest_submissions` 增加 compile/execution/checked 时间戳、`output_size/exit_code/signal/package_digest/request_digest/team_id`；`ConfiguredJudgeExecutor` 与 `SandboxJudgeExecutor` 在成功判题后写入。
4. **沙箱回收**：`SandboxReaper`（`io.letcoding.sandbox` 标签、过期容器清理）。
5. **部署**：新增 `Dockerfile.sandbox`（含 `sandbox/execution_runtime.py`）、`Dockerfile.worker`、`docker-compose.yml`（postgres/redis/api/worker，Worker 挂载 docker.sock）。

## 第十轮：剩余缺口补齐
1. **队伍 entry 分组**：`EntryResolver`（成员→队长），榜单 `ContestScoreboardService` 与 `/contests/{id}/statuses` 按 entry 归并。
2. **执行并发槽**：`JudgeQueue.acquire/releaseExecutionSlot`（Redis ZSET + TTL），在 Judge0/沙箱执行器按用户限制并发。
3. **Redis 提交结果缓存**：`SubmissionCache`（终态结果缓存 30s），通用提交与比赛提交结果接口读取。
4. **Worker 池化与 drain**：`oj.worker.pool`（all/contest/practice/rejudge/validation）控制领取队列；`WorkerHeartbeat` 上报 pool/accepting/draining；`@PreDestroy` 先 drain。
5. **判题阶段直方图**：`JudgeStageRecorder`（Redis hash）+ token 保护的 `GET /metrics` 输出 `oj_judge_stage_seconds`。
6. **learn_scanner**：`LearnScannerService`（递归扫描/ID/元数据/缓存/越界校验）替换直读，`/tree` 返回 `{data:tree}`、`/file` 返回 `{data:{content,title,...}}`。
7. **OIDC 元数据发现 / PKCE**：`OAuthProviderRegistry` 支持 `server-metadata-url`/`issuer` 发现端点；`oj.oauth.pkce` 开启 S256 PKCE。
8. **编码**：Dockerfile 固定 `file.encoding=UTF-8`，避免非 UTF-8 路径失败。

## 未移植（有明确理由）
- **判题公平调度**（原 `contest_judge_queue` 按 contest+entry 轮转）：属于可选优化，文档要求的原子 claim/lease/attempt fencing 已实现；公平调度需更改队列消息格式（现队列消息为 jobId）。
- **跨提交编译缓存**（`compile_cache.py`）：Judge0/沙箱已满足文档“一次提交只编译一次”；跨提交复用为性能优化。
- **队伍 team_id 列**：已加列；entry 分组在读取时按成员→队长归并（未在写入时冗余 team_id，语义等价）。
- **OIDC 元数据发现/claims 合并/PKCE**：文档要求的 OAuth→一次性 grant→exchange 流程已实现；发现与 PKCE 为增强项。
- **结构化 JSON 日志与脱敏**（`logger_service.py`）：属日志基础设施，按部署需要另行配置。

## 仍需注意（环境相关）
- Docker 沙箱与 Testcontainers 均需 Docker 守护进程；本机无 docker，未能实机执行，已在配置层验证装配。
- Judge0 与沙箱为两种可切换执行后端，生产按 `oj.judge.executor` 选择。
