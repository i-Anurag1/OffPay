package com.demo.upimesh.service;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.crypto.SignatureService;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.time.Instant;

@Service
public class BridgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BridgeIngestionService.class);

    public enum Outcome {
        SETTLED,
        DUPLICATE_DROPPED,
        INVALID,
        RATE_LIMITED
    }

    public static class IngestResult {
        public final Outcome outcome;
        public final String packetHash;
        public final String reason;
        public final Long transactionId;
        public final boolean signatureVerified;

        public IngestResult(Outcome outcome, String packetHash, String reason, Long transactionId,
                             boolean signatureVerified) {
            this.outcome = outcome;
            this.packetHash = packetHash;
            this.reason = reason;
            this.transactionId = transactionId;
            this.signatureVerified = signatureVerified;
        }
    }

    private final HybridCryptoService cryptoService;
    private final SignatureService signatureService;
    private final ServerKeyHolder serverKeyHolder;
    private final IdempotencyService idempotencyService;
    private final RateLimiterService rateLimiterService;
    private final SettlementService settlementService;
    private final DemoService demoService;

    @Value("${upi.mesh.freshness-window-ms:86400000}")
    private long freshnessWindowMs;

    public BridgeIngestionService(HybridCryptoService cryptoService, SignatureService signatureService,
                                   ServerKeyHolder serverKeyHolder, IdempotencyService idempotencyService,
                                   RateLimiterService rateLimiterService, SettlementService settlementService,
                                   DemoService demoService) {
        this.cryptoService = cryptoService;
        this.signatureService = signatureService;
        this.serverKeyHolder = serverKeyHolder;
        this.idempotencyService = idempotencyService;
        this.rateLimiterService = rateLimiterService;
        this.settlementService = settlementService;
        this.demoService = demoService;
    }

    /**
     * THE pipeline. Every real bridge node (and the "Bridges Upload" demo
     * button) POSTs to /api/bridge/ingest, which calls this method.
     *
     * Order matters:
     *   0. rate-limit the bridge node itself (added on top of the original design)
     *   1. hash the ciphertext (cheap, before any crypto work)
     *   2. claim the hash atomically - duplicates short-circuit here
     *   3. decrypt (RSA-OAEP unwrap + AES-GCM decrypt+authenticate)
     *   4. verify the sender's signature against the trusted on-file public key
     *      (added on top of the original design - proves authorship, not just secrecy)
     *   5. freshness check (reject anything older than the configured window)
     *   6. settle (debit/credit/ledger in one DB transaction)
     */
    public IngestResult ingest(MeshPacket packet, String bridgeNodeId) {
        if (bridgeNodeId != null && !rateLimiterService.allow(bridgeNodeId)) {
            log.warn("Bridge node {} rate-limited", bridgeNodeId);
            return new IngestResult(Outcome.RATE_LIMITED, null,
                "Bridge node " + bridgeNodeId + " exceeded ingest rate limit", null, false);
        }

        String packetHash = cryptoService.hashCiphertext(packet.getCiphertext());

        if (!idempotencyService.claim(packetHash)) {
            log.info("Duplicate packet {} dropped (hash {})", packet.getPacketId(), packetHash);
            return new IngestResult(Outcome.DUPLICATE_DROPPED, packetHash, "Already settled or in-flight", null, false);
        }

        HybridCryptoService.DecryptedPayload decrypted;
        try {
            decrypted = cryptoService.decrypt(packet.getCiphertext(), serverKeyHolder.getPrivateKey());
        } catch (HybridCryptoService.CryptoException e) {
            log.warn("Packet {} failed decryption/integrity check: {}", packet.getPacketId(), e.getMessage());
            return new IngestResult(Outcome.INVALID, packetHash, "Decryption or integrity check failed", null, false);
        }

        PaymentInstruction instruction = decrypted.instruction;

        boolean signatureVerified = verifySignature(packet, decrypted, instruction);
        if (!signatureVerified) {
            log.warn("Packet {} failed signature verification for sender {}",
                packet.getPacketId(), instruction.getSenderVpa());
            return new IngestResult(Outcome.INVALID, packetHash,
                "Sender signature did not verify against registered device key", null, false);
        }

        long age = Instant.now().toEpochMilli() - instruction.getSignedAt();
        if (age > freshnessWindowMs || age < -60_000) { // small negative tolerance for clock skew
            log.warn("Packet {} rejected for staleness (age={}ms)", packet.getPacketId(), age);
            return new IngestResult(Outcome.INVALID, packetHash, "Packet outside freshness window", null, false);
        }

        SettlementService.SettlementOutcome settlement =
            settlementService.settle(instruction, packetHash, true);

        return switch (settlement.result) {
            case SETTLED -> new IngestResult(Outcome.SETTLED, packetHash, null, settlement.transactionId, true);
            case INSUFFICIENT_FUNDS, UNKNOWN_ACCOUNT ->
                new IngestResult(Outcome.INVALID, packetHash, settlement.reason, null, true);
            case DUPLICATE_DROPPED ->
                new IngestResult(Outcome.DUPLICATE_DROPPED, packetHash, settlement.reason, null, true);
        };
    }

    private boolean verifySignature(MeshPacket packet, HybridCryptoService.DecryptedPayload decrypted,
                                     PaymentInstruction instruction) {
        PublicKey trustedKey = demoService.getTrustedPublicKey(instruction.getSenderVpa());
        if (trustedKey == null) {
            return false; // unknown sender VPA - never trust a self-declared key from the packet
        }
        return signatureService.verify(decrypted.rawBytes, packet.getSignatureBase64(), trustedKey);
    }
}
