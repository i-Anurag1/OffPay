package com.demo.upimesh.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deduplicates packets by ciphertext hash before any decryption work happens.
 * ConcurrentHashMap.putIfAbsent is atomic - exactly one caller among any
 * number of concurrent callers gets `true` (first claimer); everyone else
 * gets `false` (duplicate). In production this becomes Redis `SET key NX EX`,
 * with identical semantics but shared across replicas instead of JVM-local.
 */
@Service
public class IdempotencyService {

    private final Map<String, Instant> seen = new ConcurrentHashMap<>();

    @Value("${upi.mesh.idempotency-ttl-ms:86400000}")
    private long ttlMs;

    /** Returns true if this is the first time packetHash has been claimed. */
    public boolean claim(String packetHash) {
        Instant prev = seen.putIfAbsent(packetHash, Instant.now());
        return prev == null;
    }

    public boolean hasSeen(String packetHash) {
        return seen.containsKey(packetHash);
    }

    public void reset() {
        seen.clear();
    }

    public int size() {
        return seen.size();
    }

    /** Evicts expired claims periodically so the map doesn't grow unbounded. */
    @Scheduled(fixedRate = 60_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minusMillis(ttlMs);
        seen.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }
}
