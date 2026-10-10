package com.authen.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Reversible password encoder: stores passwords AES-256-GCM encrypted (not hashed) so support
 * can recover the real password with {@link #decrypt(String)}. Stored format is
 * {@code {aes}base64(iv + ciphertext + tag)}. A value without the prefix is treated as a legacy
 * plain-text password, still matched, and flagged by {@link #upgradeEncoding(String)}.
 */
@Component
public class AesPasswordEncoder implements PasswordEncoder {

    public static final String PREFIX = "{aes}";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH = 32;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesPasswordEncoder(@Value("${password.encryption.key}") String base64Key) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("password.encryption.key must be Base64", e);
        }
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalStateException("password.encryption.key must decode to 32 bytes (openssl rand -base64 32)");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String encode(CharSequence rawPassword) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(rawPassword.toString().getBytes(StandardCharsets.UTF_8));

            byte[] payload = ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password encryption failed", e);
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        String expected;
        try {
            expected = isEncrypted(encodedPassword) ? decrypt(encodedPassword) : encodedPassword;
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(
                rawPassword.toString().getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword != null && !isEncrypted(encodedPassword);
    }

    public boolean isEncrypted(String encodedPassword) {
        return encodedPassword != null && encodedPassword.startsWith(PREFIX);
    }

    /**
     * @throws IllegalArgumentException when the value is not in the {@code {aes}} format, was
     *                                  encrypted with a different key, or has been tampered with
     */
    public String decrypt(String encodedPassword) {
        if (!isEncrypted(encodedPassword)) {
            throw new IllegalArgumentException("Value is not an encrypted password");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encodedPassword.substring(PREFIX.length()));
            if (payload.length < IV_LENGTH) {
                throw new IllegalArgumentException("Encrypted password is too short");
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, payload, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Encrypted password cannot be decrypted with the configured key", e);
        }
    }
}
