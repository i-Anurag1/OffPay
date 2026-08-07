package com.demo.upimesh.model;

import java.math.BigDecimal;

/**
 * The decrypted payload inside a MeshPacket's ciphertext. Never travels
 * in cleartext across the mesh - only exists in memory on the sender's
 * phone (before encryption) and on the backend (after decryption).
 */
public class PaymentInstruction {

    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String pinHash; // SHA-256(PIN + salt); never the raw PIN
    private String nonce;   // UUID, makes two identical-looking payments distinguishable
    private long signedAt;  // epoch millis, used for the freshness/replay check

    public PaymentInstruction() {
    }

    public PaymentInstruction(String senderVpa, String receiverVpa, BigDecimal amount,
                               String pinHash, String nonce, long signedAt) {
        this.senderVpa = senderVpa;
        this.receiverVpa = receiverVpa;
        this.amount = amount;
        this.pinHash = pinHash;
        this.nonce = nonce;
        this.signedAt = signedAt;
    }

    public String getSenderVpa() {
        return senderVpa;
    }

    public void setSenderVpa(String senderVpa) {
        this.senderVpa = senderVpa;
    }

    public String getReceiverVpa() {
        return receiverVpa;
    }

    public void setReceiverVpa(String receiverVpa) {
        this.receiverVpa = receiverVpa;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public long getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(long signedAt) {
        this.signedAt = signedAt;
    }
}
