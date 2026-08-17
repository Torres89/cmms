package com.grash.service;

import com.grash.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM for secrets we hold on a customer's behalf — currently their AI API
 * key.
 * <p>
 * The key comes from the environment, never from the database, so a database
 * dump on its own does not hand over customers' provider keys. GCM rather than
 * CBC because we want tamper detection, not just confidentiality.
 */
@Service
@Slf4j
public class SecretEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "gcm:";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    private final boolean configured;

    public SecretEncryptionService(@Value("${security.secret-encryption-key:}") String configuredKey,
                                   @Value("${security.jwt.token.secret-key:}") String jwtKey) {
        // Fall back to the JWT secret so a deployment that hasn't set a
        // dedicated key still encrypts rather than storing plaintext.
        String material = (configuredKey == null || configuredKey.isBlank()) ? jwtKey : configuredKey;
        this.configured = material != null && !material.isBlank();
        if (!configured) {
            this.key = null;
            log.warn("No encryption key configured; AI provider keys cannot be stored");
            return;
        }
        try {
            // Normalise whatever length the operator supplied to a 256-bit key.
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialise secret encryption", e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        requireConfigured();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new CustomException("Could not encrypt the secret", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            // Written before encryption was configured; return as-is so the
            // deployment keeps working, and it gets rewritten on next save.
            return stored;
        }
        requireConfigured();
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(combined, IV_BYTES, combined.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Could not decrypt a stored secret; it may have been written with a different key");
            return null;
        }
    }

    /**
     * What the settings screen is allowed to see: enough to recognise the key,
     * not enough to use it.
     */
    public String mask(String plaintext) {
        if (plaintext == null || plaintext.length() < 4) {
            return null;
        }
        return "••••" + plaintext.substring(plaintext.length() - 4);
    }

    public boolean isConfigured() {
        return configured;
    }

    private void requireConfigured() {
        if (!configured) {
            throw new CustomException(
                    "No encryption key is configured. Set SECRET_ENCRYPTION_KEY before storing provider keys.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
