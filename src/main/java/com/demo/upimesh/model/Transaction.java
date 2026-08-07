package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "transactions",
    uniqueConstraints = @UniqueConstraint(name = "uk_packet_hash", columnNames = "packetHash")
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String senderVpa;

    @Column(nullable = false)
    private String receiverVpa;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Defense-in-depth: even if the in-memory (or Redis, in prod) idempotency
    // cache is ever bypassed, this DB-level unique index rejects a second
    // settlement of the same ciphertext.
    @Column(nullable = false, length = 64)
    private String packetHash;

    @Column(nullable = false)
    private Instant settledAt;

    @Column(nullable = false)
    private boolean senderSignatureVerified;

    protected Transaction() {
        // JPA
    }

    public Transaction(String senderVpa, String receiverVpa, BigDecimal amount,
                        String packetHash, boolean senderSignatureVerified) {
        this.senderVpa = senderVpa;
        this.receiverVpa = receiverVpa;
        this.amount = amount;
        this.packetHash = packetHash;
        this.senderSignatureVerified = senderSignatureVerified;
        this.settledAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSenderVpa() {
        return senderVpa;
    }

    public String getReceiverVpa() {
        return receiverVpa;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getPacketHash() {
        return packetHash;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public boolean isSenderSignatureVerified() {
        return senderSignatureVerified;
    }
}
