# OJ Spring Boot 后端

本工程依据 `oj后端重构.md` 的第一阶段建立模块化单体骨架。

当前范围：Spring Boot、Spring MVC、PostgreSQL、JPA、Flyway、统一异常和 Request ID。

当前明确未接入：Redis、Judge0、Docker Sandbox、业务接口迁移。

## 本地运行

需要 Java 21、Maven 3.9+ 和 PostgreSQL。默认连接参数为 `oj/oj/oj`，可用
`OJ_DB_URL`、`OJ_DB_USERNAME`、`OJ_DB_PASSWORD` 覆盖。

```bash
mvn test
mvn -pl oj-api -am spring-boot:run
```

探针：`GET /healthz`、`GET /readyz`、`GET /healthz/db`。

Spring 不设置 `/api` context-path；生产环境的 `/api` 前缀由 Nginx 去除，符合设计文档的外部契约要求。
