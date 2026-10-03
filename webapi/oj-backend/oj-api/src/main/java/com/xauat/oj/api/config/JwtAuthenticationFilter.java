package com.xauat.oj.api.config;

import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.auth.domain.AuthSession;
import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;

/** 将 JWT 与会话双向校验的结果放入 Spring Security 上下文，统一承担 401/403 边界。 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository users;
    private final AuthSessionRepository sessions;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users, AuthSessionRepository sessions) {
        this.jwtService = jwtService;
        this.users = users;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String value = request.getHeader("Authorization");
        if (value != null && value.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parseAccess(value.substring(7));
                String sid = claims.get("sid", String.class);
                Integer userId = Integer.valueOf(claims.getSubject());
                AuthSession session = sid == null ? null : sessions.findById(sid).orElse(null);
                boolean sessionValid = session != null && !session.isRevoked() && session.getExpiresAt() != null
                        && session.getExpiresAt().isAfter(LocalDateTime.now()) && userId.equals(session.getUserId());
                var user = sessionValid ? users.findById(userId).filter(com.xauat.oj.core.user.domain.User::isActive).orElse(null) : null;
                if (user != null) {
                    String role = user.getRole() == null ? "MEMBER" : user.getRole().toUpperCase(Locale.ROOT);
                    var authentication = new UsernamePasswordAuthenticationToken(userId.toString(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException ignored) {
                // 由 Spring Security 的入口点统一返回 401，避免泄露 JWT 解析细节。
            }
        }
        chain.doFilter(request, response);
    }
}
