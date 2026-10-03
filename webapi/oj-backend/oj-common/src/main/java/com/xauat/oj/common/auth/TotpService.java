package com.xauat.oj.common.auth;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Optional;

/** RFC 6238 TOTP（HMAC-SHA1，30 秒步长，6 位），兼容旧后端的 ±1 时间窗。 */
@Component
public class TotpService {
    public Optional<Long> match(String base32Secret, String code, long epochSeconds) {
        if (code == null || code.length() != 6 || !code.chars().allMatch(Character::isDigit)) return Optional.empty();
        byte[] key;
        try { key = base32Decode(base32Secret); }
        catch (RuntimeException exception) { return Optional.empty(); }
        if (key.length < 20) return Optional.empty();
        long current = epochSeconds / 30;
        for (long counter : new long[]{current, current - 1, current + 1}) {
            if (counter < 0) continue;
            String expected = hotp(key, counter);
            if (MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    code.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                return Optional.of(counter);
            }
        }
        return Optional.empty();
    }

    private String hotp(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = digest[digest.length - 1] & 0x0F;
            int value = ((digest[offset] & 0x7F) << 24) | ((digest[offset + 1] & 0xFF) << 16)
                    | ((digest[offset + 2] & 0xFF) << 8) | (digest[offset + 3] & 0xFF);
            return String.format("%06d", value % 1_000_000);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算 TOTP", exception);
        }
    }

    /** RFC 4648 Base32 解码（忽略空格与大小写）。 */
    static byte[] base32Decode(String input) {
        if (input == null) throw new IllegalArgumentException("empty secret");
        String normalized = input.replace(" ", "").replace("=", "").toUpperCase(java.util.Locale.ROOT);
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        int buffer = 0, bits = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char character : normalized.toCharArray()) {
            int value = alphabet.indexOf(character);
            if (value < 0) throw new IllegalArgumentException("invalid base32");
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
