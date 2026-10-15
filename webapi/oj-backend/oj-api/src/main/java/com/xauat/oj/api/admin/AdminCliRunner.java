package com.xauat.oj.api.admin;

import com.xauat.oj.core.auth.repository.AuthSessionRepository;
import com.xauat.oj.core.user.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 一次性管理命令入口，对齐旧后端 {@code python manage.py set-role}。
 *
 * <p>用法：{@code java -jar oj-api.jar --set-role=<userId>:<role>}，
 * 其中 role ∈ {member,staff,manager,provider}。命令执行完直接退出进程，
 * 不进入常驻服务，便于在部署机上进行运维操作。</p>
 */
@Component
public class AdminCliRunner implements ApplicationRunner {
    private static final Set<String> ROLES = Set.of("member", "staff", "manager", "provider");

    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final ConfigurableApplicationContext context;

    public AdminCliRunner(UserRepository users, AuthSessionRepository sessions, ConfigurableApplicationContext context) {
        this.users = users;
        this.sessions = sessions;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        String value = firstValue(args, "set-role");
        if (value == null)
            return;
        int exitCode = 0;
        try {
            String[] parts = value.split(":", 2);
            if (parts.length != 2)
                throw new IllegalArgumentException("用法: --set-role=<userId>:<role>");
            int userId = Integer.parseInt(parts[0].trim());
            String role = parts[1].trim().toLowerCase();
            if (!ROLES.contains(role))
                throw new IllegalArgumentException("无效角色: " + role);
            var user = users.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
            if ("provider".equals(role)) {
                // 回退到供应商角色
                user.setLocalRole(null);
                user.setRole(user.getProviderRole() == null ? "member" : user.getProviderRole());
            } else {
                user.setLocalRole(role);
                user.setRole(role);
            }
            users.save(user);
            sessions.revokeByUserId(userId);
            System.out.println("用户 " + userId + " 角色已更新为 " + user.getRole() + "，旧会话已撤销");
        } catch (RuntimeException exception) {
            System.err.println("set-role 失败: " + exception.getMessage());
            exitCode = 1;
        }
        int finalExitCode = exitCode;
        int code = SpringApplication.exit(context, () -> finalExitCode);
        System.exit(code);
    }

    /** 从命令行参数中读取指定前缀的首个取值（支持 --key=value）。 */
    private String firstValue(ApplicationArguments args, String key) {
        for (String argument : args.getSourceArgs()) {
            if (argument.startsWith("--" + key + "="))
                return argument.substring(("--" + key + "=").length());
        }
        return null;
    }
}
