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

    /**
     * 从 JWT 密钥派生独立的 AES 密钥，避免直接复用签名密钥做加密。
     *
     * @param secret 应用 JWT 密钥
     * @throws IllegalStateException 密钥派生失败时抛出
     */
    public RefreshRecoveryCipher(@Value("${oj.auth.jwt-secret}")
    String secret) {
        try {
            // 加入固定用途前缀做域分离：即使 JWT 密钥泄露，也不能直接解出恢复材料
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(("browser-refresh-recovery:" + secret).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derived, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("无法初始化刷新恢复加密密钥", exception);
        }
    }

    /**
     * 使用 AES-GCM 加密明文，随机 IV 前置拼接后 Base64 编码。
     *
     * @param plaintext 明文
     * @return Base64(iv || ciphertext+tag)
     * @throws IllegalStateException 加密失败时抛出
     */
    public String encrypt(String plaintext) {
        try {
            // 每次都使用新的随机 IV，保证相同明文产生不同密文（GCM 的安全性前提）
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

    /**
     * 解密 {@link #encrypt(String)} 的输出。
     *
     * @param ciphertext Base64(iv || ciphertext+tag)
     * @return 原始明文
     * @throws IllegalStateException 解密或认证失败时抛出
     */
    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            // 前 12 字节为 IV，其余为密文与认证标签
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
