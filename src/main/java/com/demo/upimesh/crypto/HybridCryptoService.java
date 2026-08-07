package com.demo.upimesh.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.demo.upimesh.model.PaymentInstruction;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * Hybrid RSA-OAEP + AES-256-GCM encryption, the same pattern TLS uses.
 *
 * Wire format of the ciphertext blob (all concatenated, then base64'd):
 *   [256 bytes RSA-OAEP-encrypted AES key][12 bytes GCM IV][AES-GCM ciphertext + 16-byte tag]
 *
 * RSA can only encrypt small payloads (~245 bytes for a 2048-bit key), so we
 * generate a fresh AES-256 key per packet, encrypt the JSON payload with it,
 * then wrap only that small AES key with RSA. GCM gives us authenticated
 * encryption for free: flip one bit anywhere in the ciphertext and decryption
 * throws instead of silently returning garbage.
 */
@Service
public class HybridCryptoService {

    private static final String RSA_TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_TRANSFORM = "AES/GCM/NoPadding";
    private static final int RSA_KEY_BYTES = 256; // 2048-bit key -> 256-byte ciphertext
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    /** Serializes a PaymentInstruction to the exact bytes that get encrypted - callers
     *  needing to sign the payload (see SignatureService) must sign these same bytes. */
    public byte[] serializePlain(PaymentInstruction instruction) {
        try {
            return objectMapper.writeValueAsBytes(instruction);
        } catch (Exception e) {
            throw new CryptoException("Failed to serialize payment instruction", e);
        }
    }

    /** Encrypts a PaymentInstruction for transport, returning base64 ciphertext. */
    public String encrypt(PaymentInstruction instruction, PublicKey recipientPublicKey) {
        try {
            byte[] plaintext = serializePlain(instruction);

            // 1. Fresh AES-256 key for this packet only.
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, secureRandom);
            SecretKey aesKey = keyGen.generateKey();

            // 2. AES-256-GCM encrypt the payload.
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher aesCipher = Cipher.getInstance(AES_TRANSFORM);
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] aesCiphertext = aesCipher.doFinal(plaintext);

            // 3. RSA-OAEP wrap just the AES key.
            Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORM);
            OAEPParameterSpec oaepSpec = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
            rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey, oaepSpec);
            byte[] wrappedKey = rsaCipher.doFinal(aesKey.getEncoded());

            // 4. Concatenate: wrappedKey | iv | aesCiphertext
            ByteBuffer buffer = ByteBuffer.allocate(wrappedKey.length + iv.length + aesCiphertext.length);
            buffer.put(wrappedKey).put(iv).put(aesCiphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new CryptoException("Failed to encrypt payment instruction", e);
        }
    }

    /** Decrypted payload plus the raw plaintext bytes, needed by callers that verify a signature over it. */
    public static class DecryptedPayload {
        public final PaymentInstruction instruction;
        public final byte[] rawBytes;

        public DecryptedPayload(PaymentInstruction instruction, byte[] rawBytes) {
            this.instruction = instruction;
            this.rawBytes = rawBytes;
        }
    }

    /**
     * Decrypts a base64 ciphertext blob back into a PaymentInstruction.
     * Throws CryptoException on any tampering (GCM tag mismatch) or malformed input -
     * callers must treat that as "reject the packet", never as a crash.
     */
    public DecryptedPayload decrypt(String base64Ciphertext, PrivateKey recipientPrivateKey) {
        try {
            byte[] blob = Base64.getDecoder().decode(base64Ciphertext);
            if (blob.length < RSA_KEY_BYTES + GCM_IV_BYTES) {
                throw new CryptoException("Ciphertext too short to be valid", null);
            }

            byte[] wrappedKey = new byte[RSA_KEY_BYTES];
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] aesCiphertext = new byte[blob.length - RSA_KEY_BYTES - GCM_IV_BYTES];

            ByteBuffer buffer = ByteBuffer.wrap(blob);
            buffer.get(wrappedKey);
            buffer.get(iv);
            buffer.get(aesCiphertext);

            Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORM);
            OAEPParameterSpec oaepSpec = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
            rsaCipher.init(Cipher.DECRYPT_MODE, recipientPrivateKey, oaepSpec);
            byte[] aesKeyBytes = rsaCipher.doFinal(wrappedKey);
            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

            Cipher aesCipher = Cipher.getInstance(AES_TRANSFORM);
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = aesCipher.doFinal(aesCiphertext); // throws AEADBadTagException on tamper

            PaymentInstruction instruction = objectMapper.readValue(plaintext, PaymentInstruction.class);
            return new DecryptedPayload(instruction, plaintext);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            // Covers AEADBadTagException (tamper), IllegalBlockSizeException (malformed),
            // and JSON parse failures - all mean "reject", never "crash the server".
            throw new CryptoException("Failed to decrypt or verify packet integrity", e);
        }
    }

    /** SHA-256 hash of the raw ciphertext bytes, used as the idempotency key. */
    public String hashCiphertext(String base64Ciphertext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base64Ciphertext.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("SHA-256 not available", e);
        }
    }

    public static class CryptoException extends RuntimeException {
        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
