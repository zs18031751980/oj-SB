package com.xauat.oj.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 刷新恢复材料的对称加密。密钥由 JWT 密钥派生，保证数据库中不保存明文刷新令牌。
 */
@Component
public class RefreshRecoveryCipher {
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public RefreshRecoveryCipher(@Value("${oj.auth.jwt-secret}") String secret) {
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(("browser-refresh-recovery:" + secret).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derived, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("无法初始化刷新恢复加密密钥", exception);
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception exception) {
            throw new IllegalStateException("无法加密刷新恢复材料", exception);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("无法解密刷新恢复材料", exception);
        }
    }
}
