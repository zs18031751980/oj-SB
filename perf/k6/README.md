# k6 压力测试脚本

前置：安装 [k6](https://k6.io/docs/get-started/installation/)，准备一个**非生产**测试环境。

```bash
# 题库列表
BASE_URL=https://test.example.com VUS=50 DURATION=1m k6 run perf/k6/problems.js

# 登录（需要开发/测试账号）
BASE_URL=https://test.example.com SMOKE_USER=smoke SMOKE_PASS='***' \
  VUS=20 DURATION=30s k6 run perf/k6/login.js

# 提交（会真实占用判题队列，谨慎设置 VUs）
BASE_URL=https://test.example.com SMOKE_USER=smoke SMOKE_PASS='***' \
  VUS=5 DURATION=30s PROBLEM_ID=2001 k6 run perf/k6/submit.js
```

注意：

- API 接收压力与真实判题压力是两件事，务必分别评估；不要用 Judge0/硬件不足的环境结果代表生产容量。
- 提交压测前确认已配置限流与队列监控，避免打满共享环境。
- 建议在独立流水线执行，不阻塞日常 PR。
