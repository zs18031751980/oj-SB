package com.xauat.oj.api.auth;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.generators.SCrypt;
import org.bouncycastle.crypto.params.KeyParameter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 兼容旧后端 Werkzeug 3.x 生成的密码哈希。
 *
 * <p>Werkzeug 3.1 默认使用 {@code scrypt:32768:8:1$salt$hash}，历史上也使用过
 * {@code pbkdf2:sha256:iterations$salt$hash}。新密码统一用 BCrypt 存储，登录校验
 * 旧哈希成功后会由调用方触发重新编码，逐步迁移到 BCrypt。</p>
 */
public class WerkzeugCompatiblePasswordEncoder implements PasswordEncoder {
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) return false;
        if (isBcrypt(encodedPassword)) return bcrypt.matches(rawPassword, encodedPassword);
        return matchesWerkzeug(rawPassword.toString(), encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword == null || !isBcrypt(encodedPassword);
    }

    private boolean isBcrypt(String encoded) {
        return encoded.startsWith("$2a$") || encoded.startsWith("$2b$") || encoded.startsWith("$2y$");
    }

    private boolean matchesWerkzeug(String rawPassword, String storedHash) {
        String[] parts = storedHash.split("\\$", 3);
        if (parts.length != 3) return false;
        String method = parts[0];
        String salt = parts[1];
        String expected = parts[2];
        try {
            String computed = switch (method.split(":")[0]) {
                case "scrypt" -> scrypt(method, salt, rawPassword);
                case "pbkdf2" -> pbkdf2(method, salt, rawPassword);
                default -> null;
            };
            return computed != null && MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.US_ASCII), expected.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            return false;
        }
    }

    private String scrypt(String method, String salt, String rawPassword) {
        String[] args = method.split(":");
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 32768;
        int r = args.length > 2 ? Integer.parseInt(args[2]) : 8;
        int p = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        byte[] derived = SCrypt.generate(rawPassword.getBytes(StandardCharsets.UTF_8),
                salt.getBytes(StandardCharsets.UTF_8), n, r, p, 64);
        return HexFormat.of().formatHex(derived);
    }

    private String pbkdf2(String method, String salt, String rawPassword) {
        String[] args = method.split(":");
        String hashName = args.length > 1 ? args[1].toLowerCase() : "sha256";
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 1_000_000;
        Digest digest = switch (hashName) {
            case "sha1" -> new SHA1Digest();
            case "sha512" -> new SHA512Digest();
            default -> new SHA256Digest();
        };
        int keyLengthBits = digest.getDigestSize() * 8;
        PKCS5S2ParametersGenerator generator = new PKCS5S2ParametersGenerator(digest);
        generator.init(rawPassword.getBytes(StandardCharsets.UTF_8), salt.getBytes(StandardCharsets.UTF_8), iterations);
        byte[] derived = ((KeyParameter) generator.generateDerivedParameters(keyLengthBits)).getKey();
        return HexFormat.of().formatHex(derived);
    }
}
