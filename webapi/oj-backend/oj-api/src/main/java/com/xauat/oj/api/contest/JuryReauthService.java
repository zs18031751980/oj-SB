package com.xauat.oj.api.contest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xauat.oj.common.auth.TotpService;
import com.xauat.oj.core.auth.domain.JuryMFAState;
import com.xauat.oj.core.auth.repository.JuryMFAStateRepository;
import com.xauat.oj.core.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 裁判敏感操作重新认证：本地账号验证当前密码；配置了 TOTP 或开启强制 MFA 时还必须校验动态验证码。
 * 与旧后端一致，TOTP 密钥由部署 Secret 注入（JURY_TOTP_SECRETS 的 JSON）。
 */
@Service
public class JuryReauthService {
    private final PasswordEncoder encoder;
    private final TotpService totp;
    private final JuryMFAStateRepository states;
    private final Map<Integer, String> secrets = new HashMap<>();
    private final boolean requireMfa;

    public JuryReauthService(PasswordEncoder encoder, TotpService totp, JuryMFAStateRepository states, ObjectMapper mapper,
                             @Value("${oj.mfa.totp-secrets:}") String secretsJson,
                             @Value("${oj.mfa.require:false}") boolean requireMfa) {
        this.encoder = encoder; this.totp = totp; this.states = states; this.requireMfa = requireMfa;
        if (secretsJson != null && !secretsJson.isBlank()) {
            try {
                Map<String, String> parsed = mapper.readValue(secretsJson, new TypeReference<Map<String, String>>() {});
                parsed.forEach((key, value) -> secrets.put(Integer.valueOf(key), value));
            } catch (Exception ignored) {
                // 配置损坏时视为未配置，避免静默放过 MFA。
            }
        }
    }

    @Transactional
    public void reauthenticate(User actor, String password, String code) {
        String secret = secrets.get(actor.getId());
        boolean configured = secret != null && !secret.isBlank();
        if (configured || requireMfa) verifyTotp(actor, code, secret);
        if (actor.getPasswordHash() != null) {
            if (password == null || !encoder.matches(password, actor.getPasswordHash())) {
                throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "裁判密码重新认证失败");
            }
        } else if (!configured) {
            throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "人工改判需要本地密码或预配置的动态验证码");
        }
    }

    public boolean mfaConfigured(Integer userId) { return secrets.containsKey(userId); }
    public boolean requireMfa() { return requireMfa; }

    private void verifyTotp(User actor, String code, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "裁判动态验证码无效或未配置");
        }
        Optional<Long> matched = totp.match(secret, code, Instant.now().getEpochSecond());
        if (matched.isEmpty()) {
            throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "裁判动态验证码无效或未配置");
        }
        JuryMFAState state = states.findForUpdateByUserId(actor.getId())
                .orElseGet(() -> states.save(JuryMFAState.create(actor.getId())));
        if (matched.get() <= state.getLastCounter()) {
            throw new com.xauat.oj.common.exception.OjException("FORBIDDEN", "裁判动态验证码已使用");
        }
        state.consume(matched.get());
        states.save(state);
    }
}
