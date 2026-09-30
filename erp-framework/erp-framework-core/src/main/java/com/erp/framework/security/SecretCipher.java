package com.erp.framework.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感配置的加密存储（如 AI API Key）：AES-256-GCM，密钥由配置 {@code erp.security.secret-key}（环境变量 ERP_SECRET_KEY，
 * 未设置时使用 JWT 密钥）经 SHA-256 派生。密文格式 {@code enc:v1:Base64(IV + 密文)}；不带前缀的值视为旧的明文。
 * <p>更换密钥后已加密的值无法解密，需要重新填写。
 */
@Component
public class SecretCipher {

    public static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${erp.security.secret-key:}") String secretKey, @Value("${erp.security.jwt.secret}") String jwtSecret) {
        String secret = secretKey == null || secretKey.isBlank() ? jwtSecret : secretKey;
        try {
            byte[] k = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(k, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** 加密；空值原样返回 */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty() || isEncrypted(plain)) return plain;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 解密；不是密文时原样返回（兼容旧的明文值），密钥不匹配时返回 null */
    public String decrypt(String value) {
        if (!isEncrypted(value)) return value;
        try {
            byte[] in = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return new String(c.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 页面显示：只显示后 4 位 */
    public static String mask(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        return "****" + (plain.length() <= 4 ? "" : plain.substring(plain.length() - 4));
    }
}
