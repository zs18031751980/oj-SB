package com.xauat.oj.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.domain.OAuthGrant;
import com.xauat.oj.core.auth.repository.OAuthGrantRepository;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * OAuth/OIDC 登录控制器：负责授权跳转、回调换取用户信息、一次性交换码换取本地令牌。
 *
 * <p>安全链路中的位置：授权回调通过 state（可选 PKCE）防 CSRF，回调成功后不直接下发本地令牌，
 * 而是生成短期一次性 exchange code 交由前端调用 {@code /auth/exchange} 换取会话，
 * 避免令牌出现在浏览器重定向 URL 中。交换码同时写入 Redis 与 OAuth Grant 表做双重校验。</p>
 */
@RestController
@RequestMapping("/auth")
public class OAuthController {
    private final StringRedisTemplate redis;
    private final UserRepository users;
    private final OAuthGrantRepository grants;
    private final JwtService jwt;
    private final ObjectMapper mapper;
    private final String frontend;
    private final OAuthProviderRegistry providers;
    private final AuthSessionService sessionService;
    private final ProviderUserSync providerUserSync;
    private final boolean pkceEnabled;
    /** 注入 Redis、用户/Grant 仓库、JWT、供应商注册表、会话服务与用户同步器，并读取前端地址与 PKCE 开关。 */
    public OAuthController(StringRedisTemplate redis, UserRepository users, OAuthGrantRepository grants, JwtService jwt,
            ObjectMapper mapper,
            @Value("${oj.auth.frontend-url:http://localhost:5173}")
            String frontend,
            OAuthProviderRegistry providers, AuthSessionService sessionService, ProviderUserSync providerUserSync,
            @Value("${oj.oauth.pkce:false}")
            boolean pkceEnabled) {
        this.redis = redis;
        this.users = users;
        this.grants = grants;
        this.jwt = jwt;
        this.mapper = mapper;
        this.frontend = frontend;
        this.providers = providers;
        this.sessionService = sessionService;
        this.providerUserSync = providerUserSync;
        this.pkceEnabled = pkceEnabled;
    }

    /**
     * 发起 OAuth 授权：生成 state（可选 PKCE）后重定向到供应商授权地址。
     *
     * @param provider 供应商标识
     * @param next     登录完成后前端跳转路径
     * @param response HTTP 响应
     * @throws Exception 生成随机数或发起重定向失败
     */
    @GetMapping("/login/{provider}")
    public void login(@PathVariable
    String provider, @RequestParam(defaultValue = "/")
    String next,
            HttpServletResponse response) throws Exception {
        String authorization = providers.property(provider, "authorization-uri");
        if (authorization == null) {
            response.sendError(503, "OAuth provider 未配置");
            return;
        }
        // state 同时绑定 provider 与 next，回调时校验来源并恢复跳转目标，5 分钟过期防重放
        String state = UUID.randomUUID().toString();
        redis.opsForValue().set("oj:oauth:state:" + state, provider + "\n" + next, Duration.ofMinutes(5));
        String challenge = null;
        if (pkceEnabled) {
            // PKCE：随机 verifier 只存服务端，把其 SHA-256 挑战值发给供应商，回调时用 verifier 兑换
            String verifier = base64Url(java.security.SecureRandom.getInstanceStrong().generateSeed(48));
            challenge = base64Url(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            redis.opsForValue().set("oj:oauth:pkce:" + state, verifier, Duration.ofMinutes(5));
        }
        String redirect = providers.authorizationUrl(provider, null, state, challenge);
        response.sendRedirect(redirect);
    }

    /**
     * OAuth 回调：校验 state、兑换用户信息、同步本地用户，然后下发一次性 exchange code。
     *
     * @param provider 供应商标识
     * @param code     供应商授权码
     * @param state    授权时下发的 state
     * @param response HTTP 响应
     * @throws Exception 与供应商交互或重定向失败
     */
    @GetMapping("/callback/{provider}")
    public void callback(@PathVariable
    String provider, @RequestParam
    String code, @RequestParam
    String state,
            HttpServletResponse response) throws Exception {
        // getAndDelete 保证 state 一次性；前缀校验确保供应商与发起时一致
        String stored = redis.opsForValue().getAndDelete("oj:oauth:state:" + state);
        if (stored == null || !stored.startsWith(provider + "\n")) {
            response.sendError(400, "OAuth state 无效");
            return;
        }
        String verifier = pkceEnabled ? redis.opsForValue().getAndDelete("oj:oauth:pkce:" + state) : null;
        // 用授权码（及 PKCE verifier）换取 access_token 并拉取用户信息；providerId 兼容 sub/id 两种字段
        JsonNode profile = exchange(provider, code, verifier);
        String providerId = profile.path("sub").asText(profile.path("id").asText());
        String email = profile.path("email").asText("");
        String username = profile.path("preferred_username").asText(profile.path("login").asText(email));
        if (providerId.isBlank() || username.isBlank()) {
            response.sendError(400, "OAuth 用户信息不完整");
            return;
        }
        User user = providerUserSync.sync(provider, providerId, profile, username, email,
                profile.path("picture").asText(""));
        if (!user.isActive()) {
            response.sendError(403, "账号已被停用");
            return;
        }
        // 生成 2 分钟有效的一次性交换码，同时落库（Grant 表）与 Redis，供 exchange 端点双重校验
        String exchangeCode = UUID.randomUUID().toString();
        grants.save(OAuthGrant.create(UUID.randomUUID().toString(), user, JwtService.digest(exchangeCode),
                LocalDateTime.now().plusMinutes(2)));
        redis.opsForValue().set("oj:oauth:exchange:" + exchangeCode, user.getId().toString(), Duration.ofMinutes(2));
        String next = stored.substring(provider.length() + 1);
        response.sendRedirect(frontend + "/auth/callback?code=" + OAuthProviderRegistry.enc(exchangeCode) + "&next="
                + OAuthProviderRegistry.enc(next));
    }

    /**
     * 用一次性 exchange code 换取本地会话令牌。
     *
     * @param request  含 code 与 remember
     * @param response 用于写入刷新 Cookie 的响应
     * @return 会话令牌；交换码无效/被停用/归属不符时返回 401
     */
    @PostMapping("/exchange")
    @Transactional
    public ResponseEntity<?> exchange(@RequestBody
    ExchangeRequest request, HttpServletResponse response) {
        // Redis 侧一次性消费交换码，拿到当时登录的用户 id
        String userId = redis.opsForValue().getAndDelete("oj:oauth:exchange:" + request.code());
        if (userId == null)
            return ResponseEntity.status(401).body(Map.of("error", "exchange code 无效或已过期"));
        var grant = grants.findByBindingHash(JwtService.digest(request.code()))
                .filter(item -> item.usable(JwtService.digest(request.code()))).orElse(null);
        if (grant == null)
            return ResponseEntity.status(401).body(Map.of("error", "OAuth Grant 无效或已过期"));
        var user = users.findById(Integer.valueOf(userId)).orElse(null);
        if (user == null || !user.isActive() || !user.getId().equals(grant.getUser().getId()))
            return ResponseEntity.status(401).build();
        var issued = sessionService.issue(user);
        sessionService.setRefreshCookie(response, issued.refreshToken(),
                request.remember() == null || request.remember());
        return ResponseEntity.ok(sessionBody(user, issued));
    }

    /**
     * 走 OAuth2 密码模式（Resource Owner Password）直接向供应商认证并换取本地会话。
     *
     * @param provider 供应商标识
     * @param request  账号密码与 remember
     * @param response 用于写入刷新 Cookie 的响应
     * @return 会话令牌；供应商未配置 503，认证/通信失败分别 401/502
     */
    @PostMapping("/login/{provider}/password")
    @Transactional
    public ResponseEntity<?> providerPassword(@PathVariable
    String provider, @RequestBody
    PasswordRequest request,
            HttpServletResponse response) {
        String tokenUri = providers.property(provider, "token-uri");
        if (tokenUri == null)
            return ResponseEntity.status(503).body(Map.of("error", "OAuth provider 未配置"));
        try {
            // 以表单方式向供应商 token 端点提交密码模式请求，所有值均做 URL 编码
            String form = "grant_type=password&username=" + OAuthProviderRegistry.enc(request.identifier())
                    + "&password=" + OAuthProviderRegistry.enc(request.password()) + "&client_id="
                    + OAuthProviderRegistry.enc(providers.property(provider, "client-id")) + "&client_secret="
                    + OAuthProviderRegistry.enc(providers.property(provider, "client-secret"));
            HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(tokenUri))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            JsonNode token = mapper.readTree(
                    HttpClient.newHttpClient().send(tokenRequest, HttpResponse.BodyHandlers.ofString()).body());
            String access = token.path("access_token").asText();
            if (access.isBlank())
                return ResponseEntity.status(401).body(Map.of("error", "提供商认证失败"));
            // 再以 access_token 拉取用户信息，后续流程与授权码回调一致
            HttpRequest infoRequest = HttpRequest.newBuilder(URI.create(providers.property(provider, "userinfo-uri")))
                    .header("Authorization", "Bearer " + access).GET().build();
            JsonNode profile = mapper.readTree(
                    HttpClient.newHttpClient().send(infoRequest, HttpResponse.BodyHandlers.ofString()).body());
            String providerId = profile.path("sub").asText(profile.path("id").asText());
            String email = profile.path("email").asText("");
            String username = profile.path("preferred_username").asText(profile.path("login").asText(email));
            if (providerId.isBlank() || username.isBlank())
                return ResponseEntity.status(502).body(Map.of("error", "提供商用户信息不完整"));
            User user = providerUserSync.sync(provider, providerId, profile, username, email,
                    profile.path("picture").asText(""));
            if (!user.isActive())
                return ResponseEntity.status(403).body(Map.of("error", "账号已被停用"));
            var issued = sessionService.issue(user);
            sessionService.setRefreshCookie(response, issued.refreshToken(),
                    request.remember() == null || request.remember());
            return ResponseEntity.ok(sessionBody(user, issued));
        } catch (Exception exception) {
            return ResponseEntity.status(502).body(Map.of("error", "提供商认证请求失败"));
        }
    }

    /** 组装 OAuth 登录响应：同时提供扁平令牌字段与嵌套 tokens，兼容新旧前端。 */
    private Map<String, Object> sessionBody(User user, AuthSessionService.IssuedTokens issued) {
        Map<String, Object> userInfo = sessionService.userInfo(user);
        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("access_token", issued.accessToken());
        tokens.put("expires_in", issued.expiresIn());
        tokens.put("token_type", "Bearer");
        tokens.put("user_info", userInfo);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("access_token", issued.accessToken());
        body.put("expires_in", issued.expiresIn());
        body.put("token_type", "Bearer");
        body.put("user_info", userInfo);
        body.put("tokens", tokens);
        return body;
    }

    /** 用授权码（含可选 PKCE verifier）向供应商 token 端点换取 access_token，再拉取用户信息。 */
    private JsonNode exchange(String provider, String code, String codeVerifier) throws Exception {
        String tokenUri = providers.property(provider, "token-uri"),
                clientId = providers.property(provider, "client-id"),
                secret = providers.property(provider, "client-secret");
        String form = "grant_type=authorization_code&code=" + OAuthProviderRegistry.enc(code) + "&client_id="
                + OAuthProviderRegistry.enc(clientId) + "&client_secret=" + OAuthProviderRegistry.enc(secret)
                + "&redirect_uri=" + OAuthProviderRegistry.enc(providers.property(provider, "redirect-uri"))
                + (codeVerifier == null ? "" : "&code_verifier=" + OAuthProviderRegistry.enc(codeVerifier));
        HttpRequest req = HttpRequest.newBuilder(URI.create(tokenUri))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        JsonNode token = mapper
                .readTree(HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString()).body());
        String access = token.path("access_token").asText();
        HttpRequest info = HttpRequest.newBuilder(URI.create(providers.property(provider, "userinfo-uri")))
                .header("Authorization", "Bearer " + access).GET().build();
        return mapper.readTree(HttpClient.newHttpClient().send(info, HttpResponse.BodyHandlers.ofString()).body());
    }

    /** URL 安全、无填充的 Base64 编码（用于 PKCE 值）。 */
    private static String base64Url(byte[] value) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /** 交换码换取令牌的请求体。 */
    public record ExchangeRequest(String code, Boolean remember) {
    }
    /** 供应商密码模式登录请求体。 */
    public record PasswordRequest(String identifier, String password, Boolean remember) {
    }
}
