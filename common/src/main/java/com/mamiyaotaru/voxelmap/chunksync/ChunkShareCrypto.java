package com.mamiyaotaru.voxelmap.chunksync;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * passphrase based authenticated encryption for chunk-share blobs - the key is derived from the shared
 * passphrase with PBKDF2-HMAC-SHA256 (random per-blob salt), then the payload is sealed with AES-256-GCM
 * and the host only ever sees ciphertext and a wrong passphrase fails the GCM tag check,
 * so {@link #decrypt} throws rather than returning garbage
 */
public final class ChunkShareCrypto {
    private static final byte[] MAGIC = {'V', 'M', 'C', 'S'};
    private static final byte VERSION = 0x02;
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;

    public static final int DEFAULT_ITERATIONS = 120_000;
    public static final int CHAT_ITERATIONS = 15_000;
    private static final int MIN_ITERATIONS = 1_000;
    private static final int MAX_ITERATIONS = 400_000;
    private static final int V1_ITERATIONS = 120_000;

    private static final int V2_HEADER_LEN = MAGIC.length + 1 + 4 + SALT_LEN + IV_LEN;
    private static final int V1_HEADER_LEN = MAGIC.length + 1 + SALT_LEN + IV_LEN;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ChunkShareCrypto() {
    }

    public static byte[] encrypt(byte[] plaintext, String passphrase) throws GeneralSecurityException {
        return encrypt(plaintext, passphrase, DEFAULT_ITERATIONS);
    }

    public static byte[] encrypt(byte[] plaintext, String passphrase, int iterations) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[IV_LEN];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, iterations), new GCMParameterSpec(TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        ByteBuffer buf = ByteBuffer.allocate(V2_HEADER_LEN + ciphertext.length);
        buf.put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(iv).put(ciphertext);
        return buf.array();
    }

    public static byte[] decrypt(byte[] blob, String passphrase) throws GeneralSecurityException {
        if (blob.length < V1_HEADER_LEN) {
            throw new GeneralSecurityException("Share data is too short or corrupt.");
        }
        ByteBuffer buf = ByteBuffer.wrap(blob);
        byte[] magic = new byte[MAGIC.length];
        buf.get(magic);
        byte version = buf.get();
        if (!java.util.Arrays.equals(magic, MAGIC) || (version != VERSION && version != 0x01)) {
            throw new GeneralSecurityException("Not a recognised chunk-share blob (wrong magic/version).");
        }
        int iterations = V1_ITERATIONS;
        if (version == VERSION) {
            if (blob.length < V2_HEADER_LEN) {
                throw new GeneralSecurityException("Share data is too short or corrupt.");
            }
            iterations = Math.max(MIN_ITERATIONS, Math.min(MAX_ITERATIONS, buf.getInt()));
        }
        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[IV_LEN];
        buf.get(salt);
        buf.get(iv);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), new GCMParameterSpec(TAG_BITS, iv));
        try {
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new GeneralSecurityException("Wrong passphrase, or the data was corrupted in transit.");
        }
    }

    private static SecretKeySpec deriveKey(String passphrase, byte[] salt, int iterations) throws GeneralSecurityException {
        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }
}
