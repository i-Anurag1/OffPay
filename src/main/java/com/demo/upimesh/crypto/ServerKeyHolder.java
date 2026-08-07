package com.demo.upimesh.crypto;

import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Holds the backend's RSA-2048 keypair. Regenerated fresh on every startup
 * for this demo. In production this key never leaves an HSM / KMS - see
 * README "What's NOT real" table.
 */
@Component
public class ServerKeyHolder {

    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    public ServerKeyHolder() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            this.publicKey = pair.getPublic();
            this.privateKey = pair.getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not available", e);
        }
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
}
