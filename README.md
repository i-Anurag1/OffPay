# UPI Offline Mesh (OffPay) — Demo

A Spring Boot backend that demonstrates **offline UPI payments routed through a Bluetooth-style mesh network**. You're in a basement with zero connectivity. You send your friend ₹500. Your phone signs and encrypts the payment, broadcasts it to nearby phones, and the packet hops device-to-device until *some* phone walks outside, gets 4G, and silently uploads it to this backend. The backend verifies, decrypts, deduplicates, and settles.

## Demo

Temporary live demo:
https://candy-teens-meters-green.trycloudflare.com/

Note: Demo runs through Cloudflare Tunnel from local deployment. Link is active only while the server is running.

## Run Locally

```bash
git clone https://github.com/i-Anurag1/OffPay.git
cd OffPay

./mvnw spring-boot:run

This repo is the **server side** of that system, plus a software simulator of the mesh so you can demo the whole flow on a single laptop without any real Bluetooth hardware.

---

## Table of Contents

1. [What this demo proves](#what-this-demo-proves)
2. [How to run it](#how-to-run-it)
3. [The demo flow (step by step)](#the-demo-flow-step-by-step)
4. [Architecture](#architecture)
5. [The three hard problems and how they're solved](#the-three-hard-problems-and-how-theyre-solved)
6. [What's added on top of the original design](#whats-added-on-top-of-the-original-design)
7. [File-by-file walkthrough](#file-by-file-walkthrough)
8. [API reference](#api-reference)
9. [Tests](#tests)
10. [Running with Docker](#running-with-docker)
11. [What's NOT real (and what would change for production)](#whats-not-real-and-what-would-change-for-production)
12. [Honest limitations of the concept](#honest-limitations-of-the-concept)
13. [Troubleshooting](#troubleshooting)

---

## What this demo proves

The system shows four things working end to end:

1. **A payment can travel from sender to backend through untrusted intermediaries** without any of them being able to read or tamper with it. (Hybrid RSA + AES-GCM encryption.)
2. **The backend can prove who actually authored a payment**, not just that someone encrypted it correctly. (RSA-PSS sender signatures, verified against an on-file device key — added on top of the original design.)
3. **Even if the same payment reaches the backend simultaneously through multiple bridge nodes, it settles exactly once.** (Idempotency via atomic compare-and-set on the ciphertext hash.)
4. **A tampered, replayed, forged, or flooded packet is rejected** before it touches the ledger.

You'll see all four in the dashboard at `http://localhost:8080`.

---

## How to run it

### Prerequisites

- **JDK 17 or newer** installed and on `PATH` (or `JAVA_HOME` set). Check with `java -version`.
- That's it for the default profile — no database, no Redis. Maven itself is fetched by the wrapper the first time you run it, *if* your machine has internet access. If it doesn't, install Maven 3.9+ yourself and use `mvn` instead of `./mvnw` / `mvnw.cmd` below.

### Run on Windows

```
mvnw.cmd spring-boot:run
```

### Run on Mac/Linux

```
./mvnw spring-boot:run
```

The first run downloads Maven and all dependencies — give it a couple of minutes. Subsequent runs start in a few seconds.

### Open the dashboard

Once you see `Started UpiMeshApplication in X.XXX seconds`, open:

**http://localhost:8080**

You'll get a dark, mesh-themed dashboard with everything you need to drive the demo, plus a live API reference at **http://localhost:8080/swagger-ui.html**.

### Stop the server

`Ctrl+C` in the terminal.

### Run the tests

```
./mvnw test
```

The interesting one is `IdempotencyConcurrencyTest` — it fires three threads delivering the same packet simultaneously and asserts that exactly one settles.

---

## The demo flow (step by step)

The dashboard has three actions that walk through the full pipeline.

### Step 1 — Compose a payment

Choose sender, receiver, amount, PIN. Click **"📤 Inject into Mesh"**.

**What actually happens on the backend:**

- The server pretends to be the sender's phone.
- It builds a `PaymentInstruction` with a unique nonce and current timestamp.
- It **signs** that instruction with the sender's own device key (RSA-PSS/SHA-256).
- It **encrypts** the signed instruction with the server's RSA public key (hybrid encryption — see below).
- It wraps the ciphertext, signature, and sender's public key in a `MeshPacket` with a TTL of 5.
- It hands the packet to `phone-alice`, an offline virtual device.

You'll see `phone-alice` now holds 1 packet, highlighted amber on the mesh diagram.

### Step 2 — Run gossip rounds

Click **"🔄 Run Gossip Round"**. Then click it again.

Each round, every device that holds a packet broadcasts it to every other device within "Bluetooth range" (which, in our simulator, means everyone). TTL decrements per hop. After 1–2 rounds, every device holds the packet, including `phone-bridge`.

In the real system this would happen organically as people walk past each other in the basement.

### Step 3 — Bridge node walks outside

Click **"📡 Bridges Upload to Backend"**.

`phone-bridge` is the only device with `hasInternet=true`. The dashboard simulates that phone walking outside and getting 4G. It POSTs every packet it holds to `/api/bridge/ingest`.

The backend pipeline runs:

1. Rate-limit the calling bridge node.
2. Hash the ciphertext (SHA-256).
3. Try to claim the hash in the idempotency cache.
4. If claimed: decrypt with the server's RSA private key.
5. Verify the sender's signature against the trusted device key on file for that VPA.
6. Verify freshness (signedAt within 24 hours).
7. Run the debit/credit in a single DB transaction.

Watch the **Account Balances** table — money has moved. Watch the **Transaction Ledger** and the **event log** — a new settlement appears.

### Demonstrating idempotency (the killer feature)

Reset the mesh. Inject a single packet, run gossip twice so every device (including the bridge) holds it, then flush. Only one bridge is seeded by default, so to really exercise the *concurrent duplicate* case, run:

```
./mvnw test -Dtest=IdempotencyConcurrencyTest#singlePacketDeliveredByThreeBridgesSettlesExactlyOnce
```

This test creates one packet, fires 3 threads at `BridgeIngestionService.ingest()` simultaneously, and verifies that exactly one settles, two are dropped as duplicates, and the sender is debited exactly once.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         SENDER PHONE (offline)                          │
│  PaymentInstruction { sender, receiver, amount, pinHash, nonce, time }  │
│              │                                                          │
│              ▼ sign with sender's own device key (RSA-PSS/SHA-256)      │
│              ▼ encrypt with server's RSA public key                     │
│   MeshPacket { packetId, ttl, createdAt, ciphertext,                    │
│                senderPublicKey, signature }                             │
└──────────────────────────────────────┬──────────────────────────────────┘
                                        │ Bluetooth gossip
                                        ▼
        ┌─────────┐  hop   ┌─────────┐  hop   ┌─────────┐
        │stranger1│ ─────▶ │stranger2│ ─────▶ │ bridge  │ ◀── walks outside
        └─────────┘        └─────────┘        └────┬────┘     gets 4G
                                                     │
                                                     ▼ HTTPS POST
┌─────────────────────────────────────────────────────────────────────────┐
│                     SPRING BOOT BACKEND (this project)                  │
│                                                                          │
│  /api/bridge/ingest                                                     │
│       │                                                                 │
│       ▼                                                                 │
│  [0] RateLimiterService.allow(bridgeNodeId)  ◀── sliding window,         │
│       │                                          per bridge node        │
│       ▼                                                                 │
│  [1] hash ciphertext (SHA-256)                                          │
│       │                                                                 │
│       ▼                                                                 │
│  [2] IdempotencyService.claim(hash)  ◀── atomic putIfAbsent (≈ Redis     │
│       │                                  SETNX). Duplicates rejected     │
│       │                                  here, before any work.         │
│       ▼                                                                 │
│  [3] HybridCryptoService.decrypt(ciphertext)                            │
│       │       (RSA-OAEP unwraps AES key, AES-GCM decrypts payload       │
│       │        AND verifies the auth tag — tampering = exception)       │
│       ▼                                                                 │
│  [4] SignatureService.verify(payload, signature, trustedSenderKey)      │
│       │       (proves the sender actually authored this, not an         │
│       │        intermediate forging a VPA it doesn't own)               │
│       ▼                                                                 │
│  [5] Freshness check: signedAt within last 24h                          │
│       │                                                                 │
│       ▼                                                                 │
│  [6] SettlementService.settle()                                         │
│       @Transactional: debit sender, credit receiver, write ledger       │
│       @Version + pessimistic row lock on Account = defense in depth     │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## The three hard problems and how they're solved

### Problem 1: Untrusted intermediates

A random stranger's phone is carrying your transaction. How do you stop them from reading the amount or changing it — or from forging a payment that claims to be from someone else entirely?

**Solution: Hybrid encryption (RSA-OAEP + AES-GCM) plus sender signatures.**

The sender encrypts the payload with the server's public key, so intermediates see opaque ciphertext. RSA can only encrypt small data (~245 bytes for a 2048-bit key), and our payload is JSON that could exceed that, so we use the standard hybrid pattern:

1. Generate a fresh AES-256 key for *this packet*.
2. Encrypt the JSON with **AES-256-GCM** (fast + authenticated).
3. Encrypt just the AES key with **RSA-OAEP**.
4. Concatenate: `[256 bytes RSA-encrypted AES key][12 bytes IV][AES ciphertext + 16-byte GCM tag]`.

**Why GCM specifically?** It's authenticated encryption. If an intermediate flips one bit anywhere in the ciphertext, decryption throws an exception — the GCM tag won't verify.

Encryption alone only proves "someone who has the server's public key encrypted this" — which is everyone, since the public key is public. That's why the sender also **signs** the plaintext payload with their own RSA-PSS key before encrypting it, and the backend verifies that signature against a public key it already has on file for that VPA — never against a key that merely rides along inside the packet. See `HybridCryptoService.java` and `SignatureService.java`.

### Problem 2: The duplicate-storm

Three bridge nodes hold the same packet. They all walk outside at the same instant. They all POST to `/api/bridge/ingest` within milliseconds of each other. If you naively process all three, the sender is debited ₹1500 instead of ₹500.

**Solution: Atomic compare-and-set on the ciphertext hash.**

The very first thing the server does on receiving a packet (after a quick rate-limit check) is compute `SHA-256(ciphertext)` and try to "claim" that hash:

```java
// IdempotencyService.java
Instant prev = seen.putIfAbsent(packetHash, now);
return prev == null;  // true = first claimer, false = duplicate
```

`ConcurrentHashMap.putIfAbsent` is atomic. Even if 100 threads call it at the exact same nanosecond, exactly one returns `null` (the first claimer) and the rest return the existing entry. Only the first claimer proceeds to decrypt and settle. The rest are short-circuited as `DUPLICATE_DROPPED`.

**Why hash the ciphertext, not the packetId or the cleartext?**

- `packetId` can be rewritten by a malicious intermediate. Two copies of the same payment could have different packetIds. Bad key.
- The cleartext requires decryption first. We want to dedupe *before* spending CPU on RSA.
- The ciphertext is authenticated by GCM, so any tampering is detectable on decrypt. Two legitimate deliveries of the same payment have byte-identical ciphertexts.

In production this `ConcurrentHashMap` becomes Redis: `SET key NX EX 86400`. Same semantics, distributed across replicas. There's also a defense-in-depth fallback: `transactions.packetHash` has a unique index, so even if the cache layer ever fails, the database rejects a second settlement of the same hash.

### Problem 3: Replay attacks

An attacker who captured a ciphertext weeks ago could replay it whenever convenient.

**Solution: Two layers.**

1. **Inside the encrypted payload**, the sender includes `signedAt` (epoch millis). The server rejects any packet older than 24 hours. The attacker can't change `signedAt` without breaking the GCM tag *and* the signature.
2. **Inside the encrypted payload**, the sender includes a **nonce** (UUID). Even if Alice legitimately sends Bob ₹100 twice, the nonces differ → ciphertexts differ → hashes differ → both settle. But a *replay* of one specific signed packet is byte-identical, so the idempotency cache catches it.

See `BridgeIngestionService.java` for the freshness check.

---

## What's added on top of the original design

The original repo's own README is upfront that a few things were still missing. Rather than change the core proof (encryption + idempotency + replay protection, which was already solid), the following were added to close some of the gaps its own "what would change for production" table called out:

| Addition | Why | Where |
|---|---|---|
| **Sender signatures** (RSA-PSS/SHA-256) | Encryption alone proves secrecy, not authorship — anyone can encrypt to the server's public key. Signing proves the payment really came from the claimed sender's device. | `crypto/SignatureService.java`, verified in `BridgeIngestionService` |
| **Per-bridge-node rate limiting** | The original table explicitly listed "no rate limiting" as a gap. A compromised or buggy bridge node could otherwise hammer `/api/bridge/ingest`. | `service/RateLimiterService.java` |
| **OpenAPI / Swagger UI** | The README's API reference table is now also a live, browsable spec at `/swagger-ui.html`, useful if you're integrating a real Android bridge client against this backend. | `springdoc-openapi` dependency, `config/AppConfig.java` |
| **Dockerfile + docker-compose** | One-command containerized run, and a `prod` Spring profile wired to Postgres to make the "H2 → Postgres" production swap concrete instead of just a table row. | `Dockerfile`, `docker-compose.yml`, `application-prod.properties` |
| **GitHub Actions CI** | Runs the full test suite (including the concurrency test) on every push/PR. | `.github/workflows/ci.yml` |
| **Pessimistic row locking on settlement** | On top of the original's optimistic `@Version` field, `SettlementService` now also takes a pessimistic write lock while debiting/crediting, so two concurrent settlements on the *same* account can't interleave at all, rather than one failing and needing a retry. | `model/AccountRepository.findByVpaForUpdate`, `service/SettlementService` |
| **Structured `IngestResult` with a `RATE_LIMITED` outcome** | Keeps the API contract explicit about *why* a packet was rejected, rather than folding rate-limiting into a generic error. | `service/BridgeIngestionService.java` |

None of these solve the two problems the original README is explicit about being unsolvable offline (funds verification and double-spend prevention) — that's still an inherent limitation of "no internet, anywhere in the chain," discussed below.

---

## File-by-file walkthrough

```
upi-offline-mesh/
├── pom.xml                                  Maven build, Spring Boot 3.3, Java 17
├── mvnw, mvnw.cmd, .mvn/wrapper/             Maven wrapper (no local install needed, if you have internet)
├── Dockerfile, docker-compose.yml           Containerized run + optional Postgres profile
├── .github/workflows/ci.yml                 GitHub Actions: build + test on push/PR
├── README.md                                this file
└── src/main/
    ├── resources/
    │   ├── application.properties           H2 in-memory DB, port 8080, TTLs, rate-limit config
    │   ├── application-prod.properties      Postgres-backed profile (illustrative)
    │   └── templates/dashboard.html         The interactive demo UI
    └── java/com/demo/upimesh/
        ├── UpiMeshApplication.java          Spring Boot main class
        │
        ├── model/                           ── Domain layer
        │   ├── Account.java                 JPA entity. @Version = optimistic lock
        │   ├── AccountRepository.java       Spring Data JPA + pessimistic-lock finder
        │   ├── Transaction.java             Settled-tx ledger. unique idx on packetHash
        │   ├── TransactionRepository.java   Spring Data JPA
        │   ├── MeshPacket.java              Wire format: ciphertext + sender pubkey + signature
        │   └── PaymentInstruction.java      Decrypted payload (sender/receiver/amount/nonce/time)
        │
        ├── crypto/                          ── Cryptography layer
        │   ├── ServerKeyHolder.java         Generates RSA-2048 keypair on startup
        │   ├── HybridCryptoService.java     RSA-OAEP + AES-256-GCM encrypt/decrypt + ciphertext hash
        │   └── SignatureService.java        RSA-PSS sign/verify (added on top of the original design)
        │
        ├── service/                         ── Business logic
        │   ├── DemoService.java             Seeds accounts + device keys, simulates a sender phone
        │   ├── VirtualDevice.java           One simulated phone in the mesh
        │   ├── MeshSimulatorService.java    Gossip protocol across virtual devices
        │   ├── IdempotencyService.java      ConcurrentHashMap = JVM-local Redis SETNX
        │   ├── RateLimiterService.java      Per-bridge-node sliding window (added)
        │   ├── SettlementService.java       @Transactional debit + credit + ledger insert
        │   └── BridgeIngestionService.java  THE pipeline: rate-limit → hash → claim → decrypt → verify → settle
        │
        ├── controller/                      ── HTTP layer
        │   ├── ApiController.java           All REST endpoints
        │   └── DashboardController.java     Serves the dashboard HTML at /
        │
        └── config/
            └── AppConfig.java               @EnableScheduling + OpenAPI bean

src/test/java/com/demo/upimesh/
└── IdempotencyConcurrencyTest.java          Round-trip, tamper, and 3-bridges-at-once tests
```

---

## API reference

| Method | Path                 | What it does                                        |
| ------ | -------------------- | --------------------------------------------------- |
| GET    | `/`                  | Dashboard HTML                                      |
| GET    | `/api/server-key`    | Server's RSA public key (base64)                    |
| GET    | `/api/accounts`      | All accounts and balances                           |
| GET    | `/api/transactions`  | Last 20 transactions                                |
| GET    | `/api/mesh/state`    | Current state of every virtual device               |
| POST   | `/api/demo/send`     | Simulate sender phone — sign, encrypt, inject packet|
| POST   | `/api/mesh/gossip`   | Run one round of gossip across the mesh              |
| POST   | `/api/mesh/flush`    | Bridges with internet upload to backend (parallel)   |
| POST   | `/api/mesh/reset`    | Clear mesh + idempotency + rate-limit caches          |
| POST   | `/api/bridge/ingest` | **The production endpoint.** Real bridges POST here  |
| GET    | `/swagger-ui.html`   | Live, browsable OpenAPI spec (added)                 |
| GET    | `/h2-console`        | Browse the in-memory database                        |

H2 console login: JDBC URL `jdbc:h2:mem:upimesh`, username `sa`, no password.

### Request format for `/api/bridge/ingest`

```
POST /api/bridge/ingest
Content-Type: application/json
X-Bridge-Node-Id: phone-bridge-42
X-Hop-Count: 3

{
  "packetId": "550e8400-e29b-41d4-a716-446655440000",
  "ttl": 2,
  "createdAt": 1730000000000,
  "ciphertext": "base64-encoded-RSA-and-AES-blob",
  "senderPublicKeyBase64": "base64-encoded-sender-RSA-public-key",
  "signatureBase64": "base64-encoded-RSA-PSS-signature"
}
```

Response:

```json
{
  "outcome": "SETTLED",
  "packetHash": "a3f8c9...",
  "reason": null,
  "transactionId": 42,
  "signatureVerified": true
}
```

`outcome` is one of `SETTLED`, `DUPLICATE_DROPPED`, `INVALID`, or `RATE_LIMITED`.

---

## Tests

Run all tests:

```
./mvnw test
```

The included tests:

- **`encryptDecryptRoundTrip`** — sanity-check that hybrid encryption is symmetric.
- **`tamperedCiphertextIsRejected`** — flip a byte in the ciphertext, verify that decryption throws instead of returning garbage.
- **`singlePacketDeliveredByThreeBridgesSettlesExactlyOnce`** — the headline test. Three threads, one packet, simultaneous delivery. Asserts exactly one `SETTLED`, two `DUPLICATE_DROPPED`, and that the sender's balance changed by exactly the amount once.

---

## Running with Docker

```
docker compose up --build
```

This builds the app image and starts it alongside a Postgres container, running on the `prod` Spring profile (see `application-prod.properties`). To run just the app on the default in-memory H2 profile inside Docker:

```
docker build -t upi-offline-mesh .
docker run -p 8080:8080 upi-offline-mesh
```

---

## What's NOT real (and what would change for production)

This is a teaching demo. To make it production-grade you'd swap these things:

| What's in the demo                               | What it would be in production                                               |
| ------------------------------------------------ | ---------------------------------------------------------------------------- |
| H2 in-memory DB (default profile)                | PostgreSQL / MySQL with replicas (`prod` profile is a first step)            |
| `ConcurrentHashMap` for idempotency               | Redis with `SET NX EX`                                                       |
| `ConcurrentHashMap`-based rate limiter             | Redis-backed distributed limiter (e.g. Bucket4j + Redis)                     |
| RSA keypair regenerated on every startup          | Private key in HSM (AWS KMS, HashiCorp Vault). Public key cached on devices. |
| Sender device keys generated in-process           | Real device keys generated on-device, registered once at account linking     |
| Server-side `DemoService.composeAndInject()`      | Same code running on Android, in a Kotlin port                               |
| Software-simulated mesh (`MeshSimulatorService`)  | Real BLE GATT or Wi-Fi Direct between phones                                 |
| One settlement service that owns the ledger       | Integration with NPCI / a real bank core                                     |
| Header-only bridge node identification            | Mutual TLS or signed bridge-node certificates                                |
| In-memory accounts seeded on startup               | Real KYC'd users, real VPAs, real PIN verification against the bank          |
| H2 console exposed                                | Disabled                                                                     |
| Logs to console                                   | Structured logs to a SIEM, alerts on `INVALID`/`RATE_LIMITED` spikes         |

The cryptography, signature verification, and idempotency code is essentially production-shaped. The infrastructure around it is what changes.

---

## Honest limitations of the concept

Let's be straight about what this design **does not** solve. These are not implementation bugs — they're inherent to "no internet, anywhere in the chain," and none of the additions above change that:

1. **The receiver has no way to verify the sender has the funds.** When sender hands receiver a phone showing "₹500 sent," it's an IOU, not a settled payment. If the sender's account is empty when the packet finally reaches the backend, the settlement will be `INVALID` and the receiver is out ₹500 with no recourse. *This is why real offline UPI (UPI Lite) uses a pre-funded hardware-backed wallet* — to give cryptographic proof of available funds offline. The signature added here proves *who* sent it, not that they *could afford* it.
2. **A malicious sender can double-spend offline.** With ₹500 in their account, they could send a packet to Bob in basement A, walk to basement B, and send another ₹500 to Carol. Whichever packet hits the backend first wins; the other gets rejected for insufficient funds. Same root cause as #1.
3. **Bluetooth in real life is hard.** Background BLE on Android is heavily throttled since Android 8. iOS peripheral mode is locked down. Two strangers' phones reliably forming a GATT connection while the apps aren't actively open is genuinely difficult. This demo skips that problem entirely by simulating the mesh.
4. **Privacy / liability.** A stranger carries your encrypted transaction packet on their phone. They can't read it, but its existence is metadata. In a real deployment you'd want to think about regulatory disclosures and what happens if a device is seized.

For a college / portfolio project: name the concept honestly as **"mesh-routed deferred settlement"** rather than "real-time offline UPI," and you'll have a much stronger pitch. The cryptography, signature, and idempotency work here is real engineering and worth showing off.

---

## Troubleshooting

**`java: command not found`** — Install JDK 17+. On Windows, `winget install EclipseAdoptium.Temurin.17.JDK` or download from adoptium.net.

**Port 8080 already in use** — Change `server.port` in `application.properties`.

**`./mvnw` fails to download anything** — Your environment has no internet access for the wrapper bootstrap. Install Maven 3.9+ yourself and run `mvn spring-boot:run` / `mvn test` instead of `./mvnw`.

**`mvnw.cmd : The term 'mvnw.cmd' is not recognized`** — On PowerShell you need to prefix with `.\`: `.\mvnw.cmd spring-boot:run`.

**Tests fail intermittently** — The concurrency test is timing-sensitive. If it ever flakes, run it a few times; if it consistently fails on your hardware, check the actual assertion failure — it's usually informative about which thread "won."

---

## License

Demo code, no license. Use it however you want for learning.
