package com.demo.upimesh.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ADDED ON TOP OF THE ORIGINAL DESIGN.
 *
 * The original README's "what's NOT real" table lists "no rate limiting" as
 * a known gap: "per-bridge-node rate limit, per-sender velocity check."
 * This is a minimal version of that - a sliding-window counter per
 * X-Bridge-Node-Id. It won't stop a determined attacker who rotates node
 * IDs, but it stops one compromised or buggy bridge from hammering
 * /api/bridge/ingest, and it's the natural place to plug in a real
 * distributed limiter (e.g. Bucket4j + Redis) in production.
 */
@Service
public class RateLimiterService {

    @Value("${upi.mesh.rate-limit.max-requests:20}")
    private int maxRequests;

    @Value("${upi.mesh.rate-limit.window-ms:60000}")
    private long windowMs;

    private final Map<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();

    /** Returns true if the request should be allowed, false if the bridge node is over its limit. */
    public synchronized boolean allow(String bridgeNodeId) {
        Instant now = Instant.now();
        Instant cutoff = now.minusMillis(windowMs);
        Deque<Instant> log = requestLog.computeIfAbsent(bridgeNodeId, k -> new ArrayDeque<>());

        while (!log.isEmpty() && log.peekFirst().isBefore(cutoff)) {
            log.pollFirst();
        }

        if (log.size() >= maxRequests) {
            return false;
        }
        log.addLast(now);
        return true;
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupStale() {
        Instant cutoff = Instant.now().minusMillis(windowMs);
        requestLog.values().forEach(log -> {
            synchronized (this) {
                while (!log.isEmpty() && log.peekFirst().isBefore(cutoff)) {
                    log.pollFirst();
                }
            }
        });
        requestLog.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public void reset() {
        requestLog.clear();
    }
}
