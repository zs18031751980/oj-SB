package com.xauat.oj.api.auth;

import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.domain.AuthSession;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

/**
 * 认证入口控制器，提供密码登录、令牌校验、主题更新、登出与刷新等 {@code /auth/**} 端点。
 *
 * <p>在安全链路中的位置：本类不直接参与 Spring Security 过滤器链，而是通过
 * {@link CurrentUser} 复用与服务端会话绑定的令牌校验逻辑；访问令牌由
 * {@code JwtAuthenticationFilter} 放入 SecurityContext，刷新令牌则始终以 HttpOnly Cookie 形式
 * 由 {@link AuthSessionService} 签发与轮换。</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthSessionRepository sessions;
    private final AuthSessionService sessionService;
    private final OAuthProviderRegistry providers;
    private final CurrentUser currentUser;
    private final AccountRateLimiter rateLimiter;

    /** 注入用户、密码编码器、JWT、会话、OAuth 供应商与账号维度限流器等依赖。 */
    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                          AuthSessionRepository sessions, AuthSessionService sessionService,
                          OAuthProviderRegistry providers, CurrentUser currentUser, AccountRateLimiter rateLimiter) {
        this.users = users; this.passwordEncoder = passwordEncoder; this.jwtService = jwtService;
        this.sessions = sessions; this.sessionService = sessionService; this.providers = providers;
        this.currentUser = currentUser; this.rateLimiter = rateLimiter;
    }

    /**
     * 账号密码登录：先按账号维度限流，再校验密码并签发会话与刷新 Cookie。
     *
     * @param request  登录请求，identifier 兼容 username 别名
     * @param response 用于写入刷新 Cookie 的响应
     * @return 成功返回含访问令牌与用户信息的主体，失败分别返回 429/401
     */
    @PostMapping("/login/password")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        if (!rateLimiter.allow(request.identifier())) {
            return ResponseEntity.status(429).body(Map.of("error", "请求过于频繁，请稍后再试"));
        }
        // identifier 可填用户名或邮箱，依次回退查找；统一走同一套校验避免暴露账号是否存在
        User user = users.findByUsername(request.identifier()).orElseGet(() -> users.findByEmail(request.identifier()).orElse(null));
        if (user == null || !user.isActive() || user.getPasswordHash() == null ||
                !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // 账号不存在、未激活、无密码哈希与密码错误返回同一提示，防止账号枚举
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        // 旧 Werkzeug 哈希验证通过后按需重新编码为 BCrypt，实现平滑迁移
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.updatePasswordHash(passwordEncoder.encode(request.password()));
        }
        user.markLogin();
        users.save(user);
        var issued = sessionService.issue(user);
        // remember 缺省视为 true；为 false 时签发会话级 Cookie（关闭浏览器即失效）
        sessionService.setRefreshCookie(response, issued.refreshToken(), request.remember() == null || request.remember());
        return ResponseEntity.ok(loginBody(user, issued));
    }

    /**
     * API 客户端 OAuth 登录：返回授权地址，而不是执行密码登录。
     *
     * @param request 包含 provider 与可选 redirectUri
     * @return 授权跳转地址；provider 为空或不支持时返回 400
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginAlias(@RequestBody LoginAliasRequest request) {
        if (request.provider() == null || request.provider().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "请输入登录方式"));
        }
        if (!providers.configured(request.provider())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "不支持的登录方式：" + request.provider()));
        }
        String authorizationUrl = providers.authorizationUrl(request.provider(), request.redirectUri(), UUID.randomUUID().toString());
        if (authorizationUrl == null) return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "创建授权地址失败"));
        return ResponseEntity.ok(Map.of("success", true, "authorization_url", authorizationUrl));
    }

    /**
     * 校验 Authorization 头中的访问令牌是否有效。
     *
     * @param authorization Bearer 令牌
     * @return 有效返回 {@code valid=true} 与用户信息，否则 401
     */
    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = currentUser.optional(authorization);
        if (user == null) return ResponseEntity.status(401).body(Map.of("valid", false, "error", "令牌无效或已过期"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("valid", true);
        body.put("user_info", sessionService.userInfo(user));
        return ResponseEntity.ok(body);
    }

    /**
     * 返回当前已配置可用的 OAuth 登录方式列表。
     *
     * @return 含 providers 数组的响应
     */
    @GetMapping("/providers")
    public Map<String, Object> providers() {
        String configured = System.getenv().getOrDefault("OJ_OAUTH_PROVIDERS", "github,google,gitee,oidc");
        var result = java.util.Arrays.stream(configured.split(",")).map(String::trim)
                .filter(name -> !name.isBlank()).filter(providers::configured)
                .map(name -> Map.of("id", name, "name", name)).toList();
        return Map.of("providers", result);
    }

    /**
     * 更新当前用户主题偏好。
     *
     * @param authorization Bearer 令牌
     * @param request       主题值，仅允许 light/dark/system
     * @return 成功返回主题，非法值返回 400
     */
    @PatchMapping("/theme")
    public ResponseEntity<?> theme(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @Valid @RequestBody ThemeRequest request) {
        if (!Set.of("light", "dark", "system").contains(request.theme())) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid theme_preference, must be light, dark, or system"));
        }
        User user = currentUser.require(authorization);
        user.updateProfile(null, null, request.theme());
        users.save(user);
        return ResponseEntity.ok(Map.of("success", true, "theme_preference", request.theme()));
    }

    /**
     * 登出：尽力撤销刷新令牌对应会话与访问令牌对应会话，并清除刷新 Cookie。
     *
     * @param refreshToken  刷新 Cookie（可缺省）
     * @param authorization Bearer 访问令牌（可缺省）
     * @param response      用于清除 Cookie 的响应
     * @return 固定成功消息
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = AuthSessionService.REFRESH_COOKIE, required = false) String refreshToken,
                                    @RequestHeader(value = "Authorization", required = false) String authorization,
                                    HttpServletResponse response) {
        if (refreshToken != null) revokeByRefreshToken(refreshToken);
        if (authorization != null && authorization.startsWith("Bearer ")) revokeByAccessToken(authorization.substring(7));
        sessionService.clearRefreshCookie(response);
        return ResponseEntity.ok(Map.of("message", "logout successful"));
    }

    /**
     * 刷新访问令牌：解析刷新令牌定位会话，校验会话状态后续签访问令牌并轮换刷新令牌。
     *
     * <p>刷新令牌轮换（rotation）配合 {@code Idempotency-Key}：服务器在 30 秒窗口内保留上一次
     * 签发结果，客户端因响应丢失而重试同一请求时可恢复原令牌，避免被误判为令牌重放而登出。</p>
     *
     * @param refreshToken 刷新 Cookie
     * @param requestId    幂等键，用于识别重放
     * @param body         可选的 remember 标记
     * @param response     用于写入新刷新 Cookie 的响应
     * @return 新令牌主体；无效/过期/重放异常返回 400
     */
    @PostMapping("/refresh")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> refresh(@CookieValue(value = AuthSessionService.REFRESH_COOKIE, required = false) String refreshToken,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String requestId,
                                     @RequestBody(required = false) RememberRequest body,
                                     HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "refresh_token is required"));
        String sid;
        // 先取出会话 id；解析失败直接按无效令牌处理，不泄露具体失败原因
        try { sid = jwtService.parseRefresh(refreshToken).get("sid", String.class); }
        catch (RuntimeException exception) { return ResponseEntity.badRequest().body(Map.of("error", "refresh_token is invalid or expired")); }
        // 行级锁读取，防止并发刷新同一会话造成竞态
        AuthSession session = sessions.findForUpdateById(sid).orElse(null);
        if (session == null || session.isRevoked() || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(LocalDateTime.now()) || session.getUserId() == null) {
            sessionService.clearRefreshCookie(response);
            return ResponseEntity.badRequest().body(Map.of("error", "refresh_token is invalid or expired"));
        }
        User user = users.findById(session.getUserId()).filter(User::isActive).orElse(null);
        if (user == null) {
            // 会话存在但用户缺失/停用：撤销会话并清 Cookie，避免留下孤儿会话
            session.revoke(); sessions.save(session);
            sessionService.clearRefreshCookie(response);
            return ResponseEntity.badRequest().body(Map.of("error", "refresh_token is invalid or expired"));
        }
        String digest = JwtService.digest(refreshToken);
        // 优先尝试幂等恢复：同一 Idempotency-Key 在恢复窗口内直接返回上次结果
        var recovered = sessionService.recover(session, digest, requestId);
        if (recovered.isPresent()) {
            var issued = recovered.get();
            return ResponseEntity.ok(tokenBody(user, issued));
        }
        // 摘要不匹配说明这不是当前会话的有效刷新令牌（可能已轮换），按重放处理并撤销
        if (!digest.equals(session.getRefreshHash())) {
            session.revoke(); sessions.save(session);
            sessionService.clearRefreshCookie(response);
            return ResponseEntity.badRequest().body(Map.of("error", "refresh_token is invalid or expired"));
        }
        var issued = sessionService.rotate(session, requestId);
        boolean remember = body == null || body.remember() == null || body.remember();
        sessionService.setRefreshCookie(response, issued.refreshToken(), remember);
        return ResponseEntity.ok(tokenBody(user, issued));
    }

    /** 按刷新令牌中的会话 id 撤销会话；令牌不可解析时静默忽略（登出应幂等）。 */
    private void revokeByRefreshToken(String refreshToken) {
        try {
            String sid = jwtService.parseRefresh(refreshToken).get("sid", String.class);
            sessions.findById(sid).ifPresent(session -> { session.revoke(); sessions.save(session); });
        } catch (RuntimeException ignored) { }
    }

    /** 按访问令牌中的会话 id 撤销会话；令牌不可解析时静默忽略。 */
    private void revokeByAccessToken(String accessToken) {
        try {
            String sid = jwtService.parseAccess(accessToken).get("sid", String.class);
            sessions.findById(sid).ifPresent(session -> { session.revoke(); sessions.save(session); });
        } catch (RuntimeException ignored) { }
    }

    /** 组装密码登录响应：同时提供扁平字段与嵌套 tokens，兼容新旧前端。 */
    private Map<String, Object> loginBody(User user, AuthSessionService.IssuedTokens issued) {
        Map<String, Object> userInfo = sessionService.userInfo(user);
        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("access_token", issued.accessToken());
        tokens.put("expires_in", issued.expiresIn());
        tokens.put("token_type", "Bearer");
        tokens.put("user_info", userInfo);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("user_info", userInfo);
        body.put("tokens", tokens);
        return body;
    }

    /** 组装刷新响应：仅返回扁平令牌字段与用户信息。 */
    private Map<String, Object> tokenBody(User user, AuthSessionService.IssuedTokens issued) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", issued.accessToken());
        body.put("expires_in", issued.expiresIn());
        body.put("token_type", "Bearer");
        body.put("user_info", sessionService.userInfo(user));
        return body;
    }

    /** 密码登录请求；identifier 兼容旧字段名 username。 */
    public record LoginRequest(@JsonAlias({"username"}) @NotBlank String identifier, @NotBlank String password, Boolean remember) {}
    /** API 客户端发起 OAuth 登录的请求。 */
    public record LoginAliasRequest(String provider, String redirectUri) {}
    /** 刷新时是否保持长期 Cookie 的请求体。 */
    public record RememberRequest(Boolean remember) {}
    /** 主题更新请求；theme 兼容旧字段名 theme_preference。 */
    public record ThemeRequest(@JsonAlias({"theme", "theme_preference"}) @NotBlank String theme) {}
}
