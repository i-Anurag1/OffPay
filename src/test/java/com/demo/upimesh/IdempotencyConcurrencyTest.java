package com.demo.upimesh;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.service.BridgeIngestionService;
import com.demo.upimesh.service.DemoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IdempotencyConcurrencyTest {

    @Autowired
    private DemoService demoService;

    @Autowired
    private BridgeIngestionService bridgeIngestionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private HybridCryptoService cryptoService;

    @Autowired
    private com.demo.upimesh.crypto.ServerKeyHolder serverKeyHolder;

    @BeforeEach
    void resetBalances() {
        accountRepository.findById("alice@upi").ifPresent(a -> {
            a.setBalance(new BigDecimal("2500.00"));
            accountRepository.save(a);
        });
        accountRepository.findById("bob@upi").ifPresent(a -> {
            a.setBalance(new BigDecimal("800.00"));
            accountRepository.save(a);
        });
    }

    @Test
    void encryptDecryptRoundTrip() {
        var instruction = new com.demo.upimesh.model.PaymentInstruction(
            "alice@upi", "bob@upi", new BigDecimal("100.00"), "hash", "nonce-1",
            System.currentTimeMillis());
        String ciphertext = cryptoService.encrypt(instruction, serverKeyHolder.getPublicKey());
        var decrypted = cryptoService.decrypt(ciphertext, serverKeyHolder.getPrivateKey());

        assertEquals("alice@upi", decrypted.instruction.getSenderVpa());
        assertEquals("bob@upi", decrypted.instruction.getReceiverVpa());
        assertEquals(0, new BigDecimal("100.00").compareTo(decrypted.instruction.getAmount()));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        var instruction = new com.demo.upimesh.model.PaymentInstruction(
            "alice@upi", "bob@upi", new BigDecimal("100.00"), "hash", "nonce-2",
            System.currentTimeMillis());
        String ciphertext = cryptoService.encrypt(instruction, serverKeyHolder.getPublicKey());

        // Flip one character in the middle of the base64 ciphertext.
        char[] chars = ciphertext.toCharArray();
        int mid = chars.length / 2;
        chars[mid] = chars[mid] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        assertThrows(HybridCryptoService.CryptoException.class,
            () -> cryptoService.decrypt(tampered, serverKeyHolder.getPrivateKey()));
    }

    @Test
    void singlePacketDeliveredByThreeBridgesSettlesExactlyOnce() throws InterruptedException {
        MeshPacket packet = demoService.composeAndInject(
            "alice@upi", "bob@upi", new BigDecimal("500.00"), "1234", "phone-alice");

        int concurrency = 3;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);
        AtomicInteger settledCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();

        List<Runnable> tasks = List.of(
            () -> deliverOnce(packet, "bridge-1", startGate, doneLatch, settledCount, duplicateCount),
            () -> deliverOnce(packet, "bridge-2", startGate, doneLatch, settledCount, duplicateCount),
            () -> deliverOnce(packet, "bridge-3", startGate, doneLatch, settledCount, duplicateCount)
        );
        tasks.forEach(pool::submit);

        startGate.countDown(); // release all three threads at once
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "ingestion threads did not finish in time");
        pool.shutdown();

        assertEquals(1, settledCount.get(), "exactly one delivery should settle");
        assertEquals(2, duplicateCount.get(), "the other two should be dropped as duplicates");

        Account alice = accountRepository.findById("alice@upi").orElseThrow();
        assertEquals(0, new BigDecimal("2000.00").compareTo(alice.getBalance()),
            "alice should be debited exactly once, not three times");
    }

    private void deliverOnce(MeshPacket packet, String bridgeId, CountDownLatch startGate,
                              CountDownLatch doneLatch, AtomicInteger settledCount, AtomicInteger duplicateCount) {
        try {
            startGate.await();
            BridgeIngestionService.IngestResult result = bridgeIngestionService.ingest(packet, bridgeId);
            if (result.outcome == BridgeIngestionService.Outcome.SETTLED) {
                settledCount.incrementAndGet();
            } else if (result.outcome == BridgeIngestionService.Outcome.DUPLICATE_DROPPED) {
                duplicateCount.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            doneLatch.countDown();
        }
    }
}
