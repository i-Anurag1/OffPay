package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private String vpa; // virtual payment address, e.g. "alice@upi"

    private String displayName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    // Optimistic locking: defense-in-depth alongside the @Transactional debit/credit
    // in SettlementService. If two settlements somehow race on the same row, one
    // will fail with an ObjectOptimisticLockingFailureException instead of silently
    // corrupting the balance.
    @Version
    private Long version;

    protected Account() {
        // JPA
    }

    public Account(String vpa, String displayName, BigDecimal balance) {
        this.vpa = vpa;
        this.displayName = displayName;
        this.balance = balance;
    }

    public String getVpa() {
        return vpa;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getVersion() {
        return version;
    }
}
