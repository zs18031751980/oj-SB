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

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtService jwtService;
    private final UserRepository users;
    private final AuthSessionRepository sessions;

    public SecurityConfig(JwtService jwtService, UserRepository users, AuthSessionRepository sessions) {
        this.jwtService = jwtService; this.users = users; this.sessions = sessions;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, users, sessions), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(401))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(403)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/healthz/**", "/readyz", "/auth/login/**", "/auth/callback/**", "/auth/exchange", "/auth/providers", "/auth/verify", "/auth/refresh", "/problems", "/problems/**", "/rankings", "/rankings/**", "/code/run/public",
                                "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus", "/metrics").permitAll()
                        .requestMatchers(HttpMethod.GET, "/announcement", "/announcement/**", "/contests", "/contests/**", "/discussions", "/discussions/**", "/learn-resources/**").permitAll()
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "STAFF", "MANAGER")
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new WerkzeugCompatiblePasswordEncoder(); }

    /** 允许 URL 编码斜杠（%2F），使含 `/` 的学习资源 id 能作为路径变量传递。 */
    @Bean
    WebSecurityCustomizer httpFirewallCustomizer() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowUrlEncodedDoubleSlash(true);
        return web -> web.httpFirewall(firewall);
    }
}
