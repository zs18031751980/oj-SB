package com.xauat.oj.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.user.repository.UserRepository;
import com.xauat.oj.api.auth.WerkzeugCompatiblePasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.http.HttpMethod;

/**
 * Spring Security 主配置：无状态会话、JWT 过滤器与 URL 授权规则。
 *
 * <p>安全链路中的位置：位于 Servlet 过滤器链的内层，负责鉴权与授权决策；
 * 外层的 CSRF/Origin/安全头/限流等由 {@code WebConfig} 注册的过滤器承担。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtService jwtService;
    private final UserRepository users;
    private final AuthSessionRepository sessions;

    public SecurityConfig(JwtService jwtService, UserRepository users, AuthSessionRepository sessions) {
        this.jwtService = jwtService;
        this.users = users;
        this.sessions = sessions;
    }

    /**
     * 构建安全过滤器链。
     *
     * @param http 由 Spring Boot 注入的安全构建器
     * @return 安全过滤器链
     * @throws Exception 构建失败时抛出
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF 由外层 CsrfProtectionFilter 以显式请求头方式实现，故在此关闭框架内置 CSRF
        return http.csrf(csrf -> csrf.disable())
                // 使用默认 CORS 配置，具体来源由 WebConfig 的 CorsConfigurationSource 提供
                .cors(cors -> {
                })
                // 无状态：不创建 HttpSession，认证完全依赖 JWT + 服务端会话表
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 在用户名密码过滤器之前解析 Bearer 令牌，使控制器与授权规则都能拿到用户
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, users, sessions),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(401))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(403)))
                .authorizeHttpRequests(auth -> auth
                        // 公共读接口与认证入口放行（认证端点自行做令牌/会话校验）
                        .requestMatchers("/", "/healthz/**", "/readyz", "/auth/login/**", "/auth/callback/**",
                                "/auth/exchange", "/auth/providers", "/auth/verify", "/auth/refresh", "/problems",
                                "/problems/**", "/rankings", "/rankings/**", "/code/run/public",
                                "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/metrics",
                                "/actuator/metrics/**", "/actuator/prometheus", "/metrics")
                        .permitAll()
                        // 仅匿名可读的展示类 GET 接口
                        .requestMatchers(HttpMethod.GET, "/announcement", "/announcement/**", "/contests",
                                "/contests/**", "/discussions", "/discussions/**", "/learn-resources/**")
                        .permitAll()
                        // 管理端要求管理员/工作人员/管理者角色
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "STAFF", "MANAGER")
                        .anyRequest().authenticated())
                .build();
    }

    /** 密码编码器：兼容旧 Werkzeug 哈希并逐步迁移到 BCrypt。 */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new WerkzeugCompatiblePasswordEncoder();
    }

    /** 允许 URL 编码斜杠（%2F），使含 `/` 的学习资源 id 能作为路径变量传递。 */
    @Bean
    WebSecurityCustomizer httpFirewallCustomizer() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowUrlEncodedDoubleSlash(true);
        return web -> web.httpFirewall(firewall);
    }
}
