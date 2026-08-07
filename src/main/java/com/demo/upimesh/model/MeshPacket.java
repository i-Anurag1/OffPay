package com.demo.upimesh.model;

/**
 * The wire format that hops device-to-device across the Bluetooth mesh.
 * Outer fields (packetId, ttl, createdAt) are readable by every intermediate
 * hop so they can route/decrement TTL; the payload itself is opaque
 * ciphertext that only the backend can decrypt.
 */
public class MeshPacket {

    private String packetId;
    private int ttl;
    private long createdAt;
    private String ciphertext; // base64: [RSA-encrypted AES key][IV][AES-GCM ciphertext+tag]
    private String senderPublicKeyBase64; // sender's RSA public key, for signature verification
    private String signatureBase64; // signature over the plaintext payload, RSA-PSS/SHA-256

    public MeshPacket() {
    }

    public MeshPacket(String packetId, int ttl, long createdAt, String ciphertext,
                       String senderPublicKeyBase64, String signatureBase64) {
        this.packetId = packetId;
        this.ttl = ttl;
        this.createdAt = createdAt;
        this.ciphertext = ciphertext;
        this.senderPublicKeyBase64 = senderPublicKeyBase64;
        this.signatureBase64 = signatureBase64;
    }

    public String getPacketId() {
        return packetId;
    }

    public void setPacketId(String packetId) {
        this.packetId = packetId;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public void decrementTtl() {
        this.ttl = Math.max(0, this.ttl - 1);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    public String getSenderPublicKeyBase64() {
        return senderPublicKeyBase64;
    }

    public void setSenderPublicKeyBase64(String senderPublicKeyBase64) {
        this.senderPublicKeyBase64 = senderPublicKeyBase64;
    }

    public String getSignatureBase64() {
        return signatureBase64;
    }

    public void setSignatureBase64(String signatureBase64) {
        this.signatureBase64 = signatureBase64;
    }
}
