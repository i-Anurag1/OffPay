package com.demo.upimesh.crypto;

import org.springframework.stereotype.Service;

import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;

/**
 * ADDED ON TOP OF THE ORIGINAL DESIGN.
 *
 * The original README is explicit that this design does not let the receiver
 * verify the sender has funds - that's an inherent limitation of "no internet,
 * anywhere in the chain" and this service does not solve it either. What it
 * *does* solve is a cheaper, adjacent problem: proving the payment instruction
 * really was authored by the claimed sender's device and hasn't been forged
 * by an intermediate.
 *
 * Without this, hybrid encryption alone only proves "someone who has the
 * server's public key encrypted this" - which is everyone, since the public
 * key is public. Anyone relaying a packet could swap in a forged
 * PaymentInstruction claiming to be from a VPA they don't own, and the server
 * would decrypt and settle it as if genuine.
 *
 * Each simulated sender phone has its own RSA-2048 keypair (see
 * DemoService). The sender signs the *plaintext* JSON payload with
 * RSA-PSS/SHA-256 before encrypting it, and the packet carries the
 * signature plus the sender's public key. The backend decrypts, then
 * verifies the signature against a public key it already has on file for
 * that VPA (simulating the bank's on-device-registration record) - NOT
 * against whatever public key rode along in the packet, since trusting a
 * self-declared key would let an attacker just generate a fresh keypair
 * and sign as "anyone."
 */
@Service
public class SignatureService {

    private static final String SIGNATURE_ALGORITHM = "RSASSA-PSS";

    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not available", e);
        }
    }

    public String sign(byte[] payload, PrivateKey signerPrivateKey) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.setParameter(new PSSParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
            signature.initSign(signerPrivateKey);
            signature.update(payload);
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new HybridCryptoService.CryptoException("Failed to sign payload", e);
        }
    }

    /** Returns false (never throws) on any verification failure - callers decide policy. */
    public boolean verify(byte[] payload, String signatureBase64, PublicKey expectedSignerPublicKey) {
        if (signatureBase64 == null || expectedSignerPublicKey == null) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.setParameter(new PSSParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
            signature.initVerify(expectedSignerPublicKey);
            signature.update(payload);
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            return false;
        }
    }
}
