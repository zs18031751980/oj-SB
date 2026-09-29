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

## 仍需完成（接续 TODO）
1. **Problem 静态题库 + 比赛库合并**（`ProblemCatalogService`：StaticProvider + ContestLibraryProvider，`pages/problem_data.py`、`c_language_problems.py`），
   `detail` 需返回 `samples`、`testCaseCount`、`isLibrary`、`contestProblemId`、`contestId`、`contestTitle`。
2. **Submission detail**：`testcase_results` 需解析为 `[{testCaseIndex,passed,stdout,stderr,input,expected,actualOutput}]`，
   并补 `fail_testcase_index`、`compile_error`。
3. **Auth 契约**：`/auth/refresh` 请求体 `{remember}`，Header `X-CSRF-Protection`；`/auth/exchange` 请求 `{code,remember}`；
   login 响应 `tokens:{access_token,refresh_token,expires_in,token_type,user_info}`。
4. **Discuss 回复** 分页返回裸数组（已支持）；点赞 body `{liked}`（已支持）。
5. **Contest** 各路由、`/admin/contests/{id}/regenerate-testcases` 返回 `testcase_generation==='queued'` 语义。
6. **提交状态字符串** 必须保留：`Pending/Queued/Judging/Claimed/Compiling/Compiled/Running/Checking/…`。
7. Worker 判题执行、Redis 队列 dispatch（`ContestOutboxDispatcher`/`SubmissionOutboxDispatcher`）接通。
8. 本地环境（nix PostgreSQL 17 + redis）启动应用，跑 Flyway + 冒烟测试端到端。
