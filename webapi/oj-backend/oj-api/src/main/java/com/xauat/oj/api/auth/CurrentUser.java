package com.xauat.oj.api.auth;

import com.xauat.oj.common.auth.JwtService;
import com.xauat.oj.core.user.domain.User;
import com.xauat.oj.core.user.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final JwtService jwtService;
    private final UserRepository users;

    public CurrentUser(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    public User require(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new com.xauat.oj.common.exception.OjException("UNAUTHORIZED", "请先登录");
        try {
            Integer id = Integer.valueOf(jwtService.parse(authorization.substring(7)).getSubject());
            return users.findById(id).filter(User::isActive)
                    .orElseThrow(() -> new com.xauat.oj.common.exception.OjException("UNAUTHORIZED", "用户不存在或已停用"));
        } catch (com.xauat.oj.common.exception.OjException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new com.xauat.oj.common.exception.OjException("UNAUTHORIZED", "令牌无效或已过期");
        }
    }

    /** 可选身份：令牌缺失/无效/用户停用都会返回 null，用于公开接口计算 is_liked 之类的个性化字段。 */
    public User optional(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        try {
            Integer id = Integer.valueOf(jwtService.parse(authorization.substring(7)).getSubject());
            return users.findById(id).filter(User::isActive).orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
