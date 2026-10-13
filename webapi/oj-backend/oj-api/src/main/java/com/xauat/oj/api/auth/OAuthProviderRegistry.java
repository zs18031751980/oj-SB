package com.xauat.oj.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 读取 OJ_OAUTH_&lt;PROVIDER&gt;_* 环境变量；支持 OIDC 元数据发现与授权地址拼接。 */
@Component
public class OAuthProviderRegistry {
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, JsonNode> discoveryCache = new ConcurrentHashMap<>();

    public OAuthProviderRegistry(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 直接环境变量优先；缺失时回退到发现的 OIDC 元数据字段。 */
    public String property(String provider, String key) {
        String direct = System.getenv(("OJ_OAUTH_" + provider + "_" + key).toUpperCase(Locale.ROOT).replace('-', '_'));
        if (direct != null && !direct.isBlank())
            return direct;
        String field = switch (key) {
            case "authorization-uri" -> "authorization_endpoint";
            case "token-uri" -> "token_endpoint";
            case "userinfo-uri" -> "userinfo_endpoint";
            default -> null;
        };
        if (field == null)
            return null;
        JsonNode metadata = discovery(provider);
        String value = metadata == null ? null : metadata.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 判断某 provider 是否已配置到可用程度（至少具备授权端点和 client-id）。
     *
     * @param provider 供应商标识
     * @return 已配置返回 {@code true}
     */
    public boolean configured(String provider) {
        return provider != null && !provider.isBlank()
                && property(provider, "authorization-uri") != null && property(provider, "client-id") != null;
    }

    /**
     * 拼接 OAuth 授权地址。
     *
     * @param provider      供应商标识
     * @param redirectUri   自定义回调地址，为空时回退到配置
     * @param state         防 CSRF 的 state
     * @param codeChallenge PKCE 挑战值，非空时附加 S256 参数
     * @return 授权跳转 URL；授权端点未配置返回 {@code null}
     */
    public String authorizationUrl(String provider, String redirectUri, String state, String codeChallenge) {
        String authorizationUri = property(provider, "authorization-uri");
        if (authorizationUri == null)
            return null;
        String scope = property(provider, "scope");
        // 未显式配置 scope 时按 GitHub 与通用 OIDC 的差异给出合理默认值
        if (scope == null || scope.isBlank()) {
            scope = "github".equalsIgnoreCase(provider) ? "read:user user:email" : "openid profile email";
        }
        String resolvedRedirect = redirectUri != null && !redirectUri.isBlank()
                ? redirectUri
                : property(provider, "redirect-uri");
        StringBuilder url = new StringBuilder(authorizationUri).append("?response_type=code")
                .append("&client_id=").append(enc(property(provider, "client-id")))
                .append("&redirect_uri=").append(enc(resolvedRedirect))
                .append("&scope=").append(enc(scope))
                .append("&state=").append(enc(state));
        if (codeChallenge != null && !codeChallenge.isBlank()) {
            url.append("&code_challenge=").append(enc(codeChallenge)).append("&code_challenge_method=S256");
        }
        return url.toString();
    }

    /**
     * 不使用 PKCE 的授权地址重载。
     *
     * @param provider    供应商标识
     * @param redirectUri 回调地址
     * @param state       防 CSRF 的 state
     * @return 授权跳转 URL
     */
    public String authorizationUrl(String provider, String redirectUri, String state) {
        return authorizationUrl(provider, redirectUri, state, null);
    }

    /** 获取并缓存 OIDC 发现文档；结果按 provider 缓存，失败时返回 null。 */
    private JsonNode discovery(String provider) {
        return discoveryCache.computeIfAbsent(provider, key -> {
            String metadataUrl = System.getenv(
                    ("OJ_OAUTH_" + provider + "_SERVER_METADATA_URL").toUpperCase(Locale.ROOT).replace('-', '_'));
            if (metadataUrl == null || metadataUrl.isBlank()) {
                // 没有显式的元数据地址时，按 OIDC 规范从 issuer 推导标准发现地址
                String issuer = System
                        .getenv(("OJ_OAUTH_" + provider + "_ISSUER").toUpperCase(Locale.ROOT).replace('-', '_'));
                if (issuer == null || issuer.isBlank())
                    return null;
                metadataUrl = issuer.replaceAll("/$", "") + "/.well-known/openid-configuration";
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(metadataUrl)).timeout(Duration.ofSeconds(5))
                        .GET().build();
                return mapper.readTree(client.send(request, HttpResponse.BodyHandlers.ofString()).body());
            } catch (Exception exception) {
                return null;
            }
        });
    }

    /**
     * URL 编码工具，null 视为空串。
     *
     * @param value 原始值
     * @return UTF-8 百分号编码后的字符串
     */
    public static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
