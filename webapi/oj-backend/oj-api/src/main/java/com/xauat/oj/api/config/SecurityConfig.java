package com.xauat.oj.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.user.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtService jwtService;
    private final UserRepository users;

    public SecurityConfig(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService; this.users = users;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, users), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(401))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(403)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/healthz/**", "/readyz", "/auth/login/**", "/auth/callback/**", "/auth/exchange", "/auth/providers", "/auth/verify", "/auth/refresh", "/problems", "/problems/**", "/rankings", "/rankings/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/announcement", "/announcement/**", "/contests", "/contests/**", "/discussions", "/discussions/**").permitAll()
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "STAFF", "MANAGER")
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
