package com.demo.upimesh.controller;

import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import com.demo.upimesh.service.BridgeIngestionService;
import com.demo.upimesh.service.DemoService;
import com.demo.upimesh.service.IdempotencyService;
import com.demo.upimesh.service.MeshSimulatorService;
import com.demo.upimesh.service.RateLimiterService;
import com.demo.upimesh.service.VirtualDevice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "UPI Offline Mesh")
public class ApiController {

    private final ServerKeyHolder serverKeyHolder;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final MeshSimulatorService meshSimulatorService;
    private final DemoService demoService;
    private final BridgeIngestionService bridgeIngestionService;
    private final IdempotencyService idempotencyService;
    private final RateLimiterService rateLimiterService;

    public ApiController(ServerKeyHolder serverKeyHolder, AccountRepository accountRepository,
                          TransactionRepository transactionRepository, MeshSimulatorService meshSimulatorService,
                          DemoService demoService, BridgeIngestionService bridgeIngestionService,
                          IdempotencyService idempotencyService, RateLimiterService rateLimiterService) {
        this.serverKeyHolder = serverKeyHolder;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.meshSimulatorService = meshSimulatorService;
        this.demoService = demoService;
        this.bridgeIngestionService = bridgeIngestionService;
        this.idempotencyService = idempotencyService;
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/server-key")
    @Operation(summary = "Backend's RSA public key (base64)")
    public Map<String, String> serverKey() {
        return Map.of("publicKeyBase64", serverKeyHolder.getPublicKeyBase64());
    }

    @GetMapping("/accounts")
    @Operation(summary = "All accounts and balances")
    public List<Account> accounts() {
        return accountRepository.findAllByOrderByVpaAsc();
    }

    @GetMapping("/transactions")
    @Operation(summary = "Last 20 settled transactions")
    public List<Transaction> transactions() {
        return transactionRepository.findTop20ByOrderBySettledAtDesc();
    }

    @GetMapping("/mesh/state")
    @Operation(summary = "Current state of every virtual device in the mesh")
    public Map<String, Object> meshState() {
        Map<String, Object> state = new LinkedHashMap<>();
        for (VirtualDevice device : meshSimulatorService.getDevices().values()) {
            Map<String, Object> deviceState = new LinkedHashMap<>();
            deviceState.put("hasInternet", device.hasInternet());
            deviceState.put("packetCount", device.packetCount());
            deviceState.put("packetIds", device.getHeldPackets().keySet());
            state.put(device.getDeviceId(), deviceState);
        }
        state.put("idempotencyCacheSize", idempotencyService.size());
        return state;
    }

    public record SendRequest(
        @NotBlank String senderVpa,
        @NotBlank String receiverVpa,
        @Positive BigDecimal amount,
        @NotBlank String pin,
        String originDeviceId
    ) {}

    @PostMapping("/demo/send")
    @Operation(summary = "Simulate the sender's phone: sign, encrypt, and inject a packet into the mesh")
    public ResponseEntity<MeshPacket> demoSend(@Valid @RequestBody SendRequest request) {
        MeshPacket packet = demoService.composeAndInject(
            request.senderVpa(), request.receiverVpa(), request.amount(), request.pin(),
            request.originDeviceId());
        return ResponseEntity.ok(packet);
    }

    @PostMapping("/mesh/gossip")
    @Operation(summary = "Run one round of gossip across the mesh")
    public ResponseEntity<Map<String, Object>> gossip() {
        meshSimulatorService.runGossipRound();
        return ResponseEntity.ok(meshState());
    }

    @PostMapping("/mesh/flush")
    @Operation(summary = "Every device with internet uploads its held packets to /api/bridge/ingest")
    public ResponseEntity<List<Map<String, Object>>> flush() {
        List<Map<String, Object>> results = new ArrayList<>();
        for (VirtualDevice device : meshSimulatorService.getDevices().values()) {
            if (!device.hasInternet()) {
                continue;
            }
            for (MeshPacket packet : device.getHeldPackets().values()) {
                BridgeIngestionService.IngestResult result = bridgeIngestionService.ingest(packet, device.getDeviceId());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("bridgeNodeId", device.getDeviceId());
                row.put("packetId", packet.getPacketId());
                row.put("outcome", result.outcome);
                row.put("packetHash", result.packetHash);
                row.put("reason", result.reason);
                row.put("transactionId", result.transactionId);
                row.put("signatureVerified", result.signatureVerified);
                results.add(row);
            }
            device.clear();
        }
        return ResponseEntity.ok(results);
    }

    @PostMapping("/mesh/reset")
    @Operation(summary = "Clear the mesh and the idempotency + rate-limit caches")
    public ResponseEntity<Void> reset() {
        meshSimulatorService.reset();
        idempotencyService.reset();
        rateLimiterService.reset();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bridge/ingest")
    @Operation(summary = "The production endpoint - real bridge nodes POST captured packets here")
    public ResponseEntity<Map<String, Object>> bridgeIngest(
            @RequestBody MeshPacket packet,
            @RequestHeader(value = "X-Bridge-Node-Id", required = false) String bridgeNodeId,
            @RequestHeader(value = "X-Hop-Count", required = false) Integer hopCount) {
        BridgeIngestionService.IngestResult result = bridgeIngestionService.ingest(packet, bridgeNodeId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("outcome", result.outcome);
        body.put("packetHash", result.packetHash);
        body.put("reason", result.reason);
        body.put("transactionId", result.transactionId);
        body.put("signatureVerified", result.signatureVerified);
        return ResponseEntity.ok(body);
    }
}
