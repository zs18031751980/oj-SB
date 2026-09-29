package com.xauat.oj.api.config;

import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.List;

/** 将 JWT 校验结果放入 Spring Security 上下文，统一承担 401/403 边界。 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String value = request.getHeader("Authorization");
        if (value != null && value.startsWith("Bearer ")) {
            try {
                var claims = jwtService.parse(value.substring(7));
                Integer userId = Integer.valueOf(claims.getSubject());
                var user = users.findById(userId).filter(com.xauat.oj.core.user.domain.User::isActive).orElse(null);
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
