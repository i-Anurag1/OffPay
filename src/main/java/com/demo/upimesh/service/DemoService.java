package com.demo.upimesh.service;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.crypto.SignatureService;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DemoService {

    private static final int DEFAULT_TTL = 5;

    private final AccountRepository accountRepository;
    private final HybridCryptoService cryptoService;
    private final SignatureService signatureService;
    private final ServerKeyHolder serverKeyHolder;
    private final MeshSimulatorService meshSimulatorService;

    // Simulates each user's device already having registered its public key
    // with the bank at account-linking time (like how a real UPI app pins a
    // device cert). The backend trusts THIS registry, never a public key
    // that merely rides along inside an incoming packet.
    private final Map<String, KeyPair> deviceKeyRegistry = new ConcurrentHashMap<>();

    public DemoService(AccountRepository accountRepository, HybridCryptoService cryptoService,
                        SignatureService signatureService, ServerKeyHolder serverKeyHolder,
                        MeshSimulatorService meshSimulatorService) {
        this.accountRepository = accountRepository;
        this.cryptoService = cryptoService;
        this.signatureService = signatureService;
        this.serverKeyHolder = serverKeyHolder;
        this.meshSimulatorService = meshSimulatorService;
    }

    @PostConstruct
    public void seedAccounts() {
        seedAccount("alice@upi", "Alice", new BigDecimal("2500.00"));
        seedAccount("bob@upi", "Bob", new BigDecimal("800.00"));
        seedAccount("carol@upi", "Carol", new BigDecimal("1200.00"));
        seedAccount("dave@upi", "Dave", new BigDecimal("50.00"));
    }

    private void seedAccount(String vpa, String name, BigDecimal balance) {
        if (accountRepository.existsById(vpa)) {
            return;
        }
        accountRepository.save(new Account(vpa, name, balance));
        deviceKeyRegistry.put(vpa, signatureService.generateKeyPair());
    }

    public PublicKey getTrustedPublicKey(String vpa) {
        KeyPair pair = deviceKeyRegistry.get(vpa);
        return pair == null ? null : pair.getPublic();
    }

    /**
     * Simulates the sender's phone: builds the payment, signs it with the
     * sender's own device key, encrypts it with the backend's public key,
     * and hands the resulting MeshPacket to the origin device in the mesh
     * (defaulting to phone-alice, as in the original demo flow).
     */
    public MeshPacket composeAndInject(String senderVpa, String receiverVpa, BigDecimal amount, String pin,
                                        String originDeviceId) {
        KeyPair senderKeys = deviceKeyRegistry.get(senderVpa);
        if (senderKeys == null) {
            throw new IllegalArgumentException("No registered device key for sender: " + senderVpa);
        }

        PaymentInstruction instruction = new PaymentInstruction(
            senderVpa, receiverVpa, amount, hashPin(pin), UUID.randomUUID().toString(),
            System.currentTimeMillis());

        byte[] plainBytes = cryptoService.serializePlain(instruction);
        String signature = signatureService.sign(plainBytes, senderKeys.getPrivate());
        String ciphertext = cryptoService.encrypt(instruction, serverKeyHolder.getPublicKey());

        String senderPublicKeyBase64 = Base64.getEncoder().encodeToString(senderKeys.getPublic().getEncoded());

        MeshPacket packet = new MeshPacket(
            UUID.randomUUID().toString(), DEFAULT_TTL, System.currentTimeMillis(),
            ciphertext, senderPublicKeyBase64, signature);

        meshSimulatorService.injectPacket(originDeviceId == null ? "phone-alice" : originDeviceId, packet);
        return packet;
    }

    private String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("upi-demo-salt:" + pin).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
