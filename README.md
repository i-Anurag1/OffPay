<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=12,20,24&height=260&section=header&text=OFFPAY&fontSize=88&fontColor=ffffff&animation=fadeIn&fontAlignY=38&desc=Offline%20UPI%20Mesh%20Payment%20System&descAlignY=60&descSize=22" alt="OffPay banner" width="100%"/>

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=600&size=22&duration=2800&pause=900&color=22C55E&center=true&vCenter=true&width=900&lines=Pay+Without+the+Internet;Sign+%2B+Encrypt+%2B+Gossip+%2B+Settle;One+Packet+%E2%86%92+Exactly+One+Settlement;RSA-PSS+%C2%B7+AES-256-GCM+%C2%B7+RSA-OAEP;Offline+First+%C2%B7+Verified+on+Arrival" alt="OffPay typing animation"/>

<br/>

**An offline payment network that enables UPI-style transactions without direct internet connectivity.**

<br/>

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Production-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-Swagger_UI-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)](https://swagger.io/)

[![CI](https://github.com/i-Anurag1/OffPay/actions/workflows/ci.yml/badge.svg)](https://github.com/i-Anurag1/OffPay/actions/workflows/ci.yml)

<br/>

[![Live Demo](https://img.shields.io/badge/%E2%9C%A6%20LIVE%20DEMO-Open%20App-22C55E?style=for-the-badge&labelColor=0B1220)](https://off-pay-five.vercel.app/)
[![Source Code](https://img.shields.io/badge/%E2%9C%A6%20SOURCE-GitHub-8B5CF6?style=for-the-badge&labelColor=0B1220&logo=github)](https://github.com/i-Anurag1/OffPay)

<br/>

<a href="#-overview">Overview</a> &nbsp;·&nbsp;
<a href="#-features">Features</a> &nbsp;·&nbsp;
<a href="#-architecture">Architecture</a> &nbsp;·&nbsp;
<a href="#-how-it-works">How It Works</a> &nbsp;·&nbsp;
<a href="#-security-design">Security</a> &nbsp;·&nbsp;
<a href="#-api-reference">API</a> &nbsp;·&nbsp;
<a href="#-quick-start">Quick Start</a> &nbsp;·&nbsp;
<a href="#-roadmap">Roadmap</a>

</div>

<br/>

```text
 ██████╗ ███████╗███████╗██████╗  █████╗ ██╗   ██╗
██╔═══██╗██╔════╝██╔════╝██╔══██╗██╔══██╗╚██╗ ██╔╝
██║   ██║█████╗  █████╗  ██████╔╝███████║ ╚████╔╝ 
██║   ██║██╔══╝  ██╔══╝  ██╔═══╝ ██╔══██║  ╚██╔╝  
╚██████╔╝██║     ██║     ██║     ██║  ██║   ██║   
 ╚═════╝ ╚═╝     ╚═╝     ╚═╝     ╚═╝  ╚═╝   ╚═╝   
```

> **OffPay** lets a phone with no connectivity create a signed, encrypted payment. Nearby devices carry it hop by hop until one of them reaches the internet. The backend then verifies, deduplicates and settles it.

<br/>

## ✦ At a Glance

<div align="center">

| 🔐 **3** | 🔑 **256-bit** | 🧾 **7** | 🌐 **10** | 🎯 **1** |
|:---:|:---:|:---:|:---:|:---:|
| Cryptographic layers | AES-GCM encryption | Ingestion checks | API endpoints | Settlement per packet |

</div>

<br/>

## ✦ Overview

The backend verifies authenticity, prevents duplicate payments, detects replay attacks and maintains a transaction ledger.

> **Intermediate devices carry packets they cannot read, cannot modify and cannot forge.**

```mermaid
flowchart LR
    S(["📱 Sender<br/>Offline"]) -->|"Sign + Encrypt"| P["🔒 Encrypted<br/>Packet"]
    P --> M["🕸️ Mesh<br/>Devices"]
    M --> B["📡 Bridge<br/>Device"]
    B -->|"HTTPS"| API{{"☕ Spring Boot<br/>Backend"}}
    API --> L[("📒 Ledger")]
    API --> A[("💰 Balances")]

    classDef offline fill:#FEF3C7,stroke:#F59E0B,color:#0B1220
    classDef mesh fill:#DBEAFE,stroke:#3B82F6,color:#0B1220
    classDef api fill:#22C55E,stroke:#166534,color:#fff,stroke-width:2px
    classDef store fill:#0F172A,stroke:#38BDF8,color:#E2E8F0
    class S,P offline
    class M,B mesh
    class API api
    class L,A store
```

<br/>

## ✦ Features

<div align="center">

| 🕸️ Offline Mesh Flow | 🛡️ Security | ⚙️ Backend Reliability |
|:---|:---|:---|
| Sender creates payment while offline | RSA-PSS digital signatures | Atomic idempotency handling |
| Packet is encrypted and signed | AES-256-GCM encryption | Rate limiting for bridge nodes |
| Packets propagate through mesh routing | RSA-OAEP key exchange | Transaction ledger |
| Bridge uploads when internet returns | SHA-256 packet hashing | Database locking during settlement |
| | Replay attack protection | Automated CI testing |
| | Duplicate transaction prevention | Docker deployment support |

</div>

<br/>

## ✦ Architecture

```mermaid
flowchart LR
    A(["📱 Sender Phone<br/>Offline"]) -->|"Sign + Encrypt"| B["🔒 Encrypted Payment Packet"]
    B --> C["🕸️ Nearby Devices<br/>Mesh Network"]
    C --> D["📡 Bridge Device<br/>Internet Available"]
    D -->|"HTTPS Upload"| E{{"☕ Spring Boot Backend"}}

    E --> F["🚦 Rate Limiter"]
    F --> G["#️⃣ SHA-256 Hash"]
    G --> H["♻️ Idempotency Check"]
    H --> I["🔓 Decrypt Payload"]
    I --> J["✍️ Verify Signature"]
    J --> K["🏦 Settlement Service"]

    K --> L[("📒 Transaction Ledger")]
    K --> M[("💰 Account Balance")]

    classDef edge fill:#FEF3C7,stroke:#F59E0B,color:#0B1220
    classDef net fill:#DBEAFE,stroke:#3B82F6,color:#0B1220
    classDef core fill:#22C55E,stroke:#166534,color:#fff,stroke-width:2px
    classDef guard fill:#EDE9FE,stroke:#8B5CF6,color:#0B1220
    classDef store fill:#0F172A,stroke:#38BDF8,color:#E2E8F0
    class A,B edge
    class C,D net
    class E,K core
    class F,G,H,I,J guard
    class L,M store
```

<br/>

## ✦ System Flow

```mermaid
sequenceDiagram
    autonumber
    actor S as 📱 Sender Phone
    participant M as 🕸️ Mesh Devices
    participant B as 📡 Bridge Node
    participant API as ☕ Backend
    participant DB as 🗄️ Database

    S->>S: Create payment instruction
    S->>S: Sign using RSA-PSS
    S->>S: Encrypt using AES-GCM
    S->>M: Broadcast packet
    M->>M: Gossip propagation
    M->>B: Forward packet
    B->>API: Upload encrypted packet
    API->>API: Rate limit check
    API->>API: Hash ciphertext
    API->>API: Idempotency verification
    API->>API: Decrypt and verify signature
    API->>DB: Debit sender and credit receiver
    DB-->>API: Settlement complete
```

<br/>

## ✦ How It Works

### ① Create Payment

The sender creates a payment instruction containing:

<div align="center">

| 👤 Sender VPA | 🎯 Receiver VPA | 💵 Amount | 🕒 Timestamp | 🎲 Unique Nonce |
|:---:|:---:|:---:|:---:|:---:|

</div>

The payload is **signed** with the sender device key, then **encrypted** before it enters the mesh.

### ② Mesh Propagation

Nearby devices store and forward the encrypted packet. Intermediate devices:

<div align="center">

| ❌ Cannot read | ❌ Cannot modify | ❌ Cannot forge |
|:---:|:---:|:---:|
| Transaction data | Payment details | Valid payments |

</div>

The packet keeps moving until it reaches a bridge device.

### ③ Bridge Upload

A bridge device with internet connectivity sends the packet to:

```http
POST /api/bridge/ingest
```

```mermaid
flowchart LR
    R(["📥 Packet"]) --> C1["1 · Rate limit<br/>validation"]
    C1 --> C2["2 · Packet<br/>hashing"]
    C2 --> C3["3 · Duplicate<br/>detection"]
    C3 --> C4["4 · Decryption"]
    C4 --> C5["5 · Signature<br/>verification"]
    C5 --> C6["6 · Timestamp<br/>validation"]
    C6 --> C7["7 · Settlement"]
    C7 --> OK(["✅ Settled"])

    classDef step fill:#DBEAFE,stroke:#3B82F6,color:#0B1220
    classDef io fill:#22C55E,stroke:#166534,color:#fff
    class C1,C2,C3,C4,C5,C6,C7 step
    class R,OK io
```

<br/>

## ✦ Duplicate Payment Protection

OffPay prevents duplicate settlements using **atomic idempotency**.

```mermaid
flowchart TD
    RX(["📥 Receive Packet"]) --> H["#️⃣ Generate SHA-256 Hash"]
    H --> Q{"Hash already<br/>exists?"}
    Q -->|"Exists"| REJ(["🚫 Reject Duplicate"])
    Q -->|"New"| CONT(["✅ Continue Settlement"])

    classDef bad fill:#EF4444,stroke:#7F1D1D,color:#fff
    classDef good fill:#22C55E,stroke:#166534,color:#fff
    classDef dec fill:#FEF3C7,stroke:#F59E0B,color:#0B1220
    class REJ bad
    class CONT good
    class Q dec
```

Multiple bridge devices uploading the same packet result in **exactly one** successful settlement.

```mermaid
flowchart LR
    B1["📡 Bridge 1"] --> API{{"☕ Backend"}}
    B2["📡 Bridge 2"] --> API
    B3["📡 Bridge 3"] --> API
    API --> R1(["✅ 1 × SETTLED"])
    API --> R2(["♻️ 2 × DUPLICATE_DROPPED"])

    classDef good fill:#22C55E,stroke:#166534,color:#fff
    classDef dup fill:#F59E0B,stroke:#92400E,color:#fff
    classDef core fill:#8B5CF6,stroke:#4C1D95,color:#fff
    class R1 good
    class R2 dup
    class API core
```

<br/>

## ✦ Security Design

### Hybrid Encryption

AES provides fast encryption. RSA protects the encryption key.

```mermaid
flowchart LR
    PD["📄 Payment Data"] --> AES["🔐 AES-256-GCM<br/>Encryption"]
    AES --> CT(["🧾 Ciphertext"])
    K["🗝️ AES Key"] --> OAEP["🔏 Protected using<br/>RSA-OAEP"]
    OAEP --> EK(["📦 Encrypted Key"])

    classDef c fill:#DBEAFE,stroke:#3B82F6,color:#0B1220
    classDef k fill:#EDE9FE,stroke:#8B5CF6,color:#0B1220
    class AES,CT c
    class OAEP,EK k
```

### Digital Signature

The sender signs the transaction **before** encryption. The backend verifies it against the trusted device public key, which prevents forged payments.

```mermaid
flowchart LR
    PD["📄 Payment Data"] --> SIG["✍️ RSA-PSS<br/>Signature"]
    SIG --> VER{"Verify against<br/>Trusted Device<br/>Public Key"}
    VER -->|"Valid"| OK(["✅ Accepted"])
    VER -->|"Invalid"| NO(["🚫 Rejected"])

    classDef good fill:#22C55E,stroke:#166534,color:#fff
    classDef bad fill:#EF4444,stroke:#7F1D1D,color:#fff
    classDef dec fill:#FEF3C7,stroke:#F59E0B,color:#0B1220
    class OK good
    class NO bad
    class VER dec
```

### Threat Coverage

<div align="center">

| Threat | Defense |
|:---|:---|
| Reading data in transit | AES-256-GCM encryption |
| Tampering with a packet | GCM authentication and RSA-PSS signature |
| Forged payments | RSA-PSS signature checked against the device public key |
| Duplicate delivery | SHA-256 hash with atomic idempotency |
| Replay attacks | Unique nonce and timestamp validation |
| Abusive bridge nodes | Rate limiting |
| Race conditions at settlement | Database locking during settlement |

</div>

<br/>

## ✦ Tech Stack

<div align="center">

| Layer | Technologies |
|:---|:---|
| **Backend** | ![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=flat-square&logo=spring&logoColor=white) ![Hibernate](https://img.shields.io/badge/Hibernate-59666C?style=flat-square&logo=hibernate&logoColor=white) ![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white) |
| **Security** | RSA-OAEP · RSA-PSS · AES-256-GCM · SHA-256 hashing |
| **Database** | ![H2](https://img.shields.io/badge/H2-1021FF?style=flat-square) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white) H2 for demo, PostgreSQL production profile |
| **DevOps** | ![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white) Docker Compose ![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white) |
| **Documentation** | ![OpenAPI](https://img.shields.io/badge/OpenAPI-85EA2D?style=flat-square&logo=swagger&logoColor=black) Swagger UI |

</div>

<br/>

## ✦ Project Structure

```text
OffPay/
│
├── src/main/java/
│   │
│   ├── controller/
│   │   ├── ApiController
│   │   └── DashboardController
│   │
│   ├── service/
│   │   ├── SettlementService
│   │   ├── MeshSimulatorService
│   │   ├── BridgeIngestionService
│   │   ├── IdempotencyService
│   │   └── RateLimiterService
│   │
│   ├── crypto/
│   │   ├── HybridCryptoService
│   │   ├── SignatureService
│   │   └── ServerKeyHolder
│   │
│   └── model/
│       ├── Account
│       ├── Transaction
│       ├── MeshPacket
│       └── PaymentInstruction
│
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

```mermaid
flowchart TD
    C["🎛️ controller"] --> S["⚙️ service"]
    S --> CR["🔐 crypto"]
    S --> M["🧩 model"]
    CR --> M

    classDef a fill:#DBEAFE,stroke:#3B82F6,color:#0B1220
    classDef b fill:#22C55E,stroke:#166534,color:#fff
    classDef c fill:#EDE9FE,stroke:#8B5CF6,color:#0B1220
    classDef d fill:#FEF3C7,stroke:#F59E0B,color:#0B1220
    class C a
    class S b
    class CR c
    class M d
```

<br/>

## ✦ API Reference

<div align="center">

| Method | Endpoint | Description |
|:---:|:---|:---|
| ![GET](https://img.shields.io/badge/GET-22C55E?style=flat-square) | `/` | Dashboard |
| ![GET](https://img.shields.io/badge/GET-22C55E?style=flat-square) | `/api/accounts` | View balances |
| ![GET](https://img.shields.io/badge/GET-22C55E?style=flat-square) | `/api/transactions` | View ledger |
| ![GET](https://img.shields.io/badge/GET-22C55E?style=flat-square) | `/api/mesh/state` | Mesh status |
| ![POST](https://img.shields.io/badge/POST-3B82F6?style=flat-square) | `/api/demo/send` | Create demo payment |
| ![POST](https://img.shields.io/badge/POST-3B82F6?style=flat-square) | `/api/mesh/gossip` | Run mesh propagation |
| ![POST](https://img.shields.io/badge/POST-3B82F6?style=flat-square) | `/api/mesh/flush` | Upload from bridge |
| ![POST](https://img.shields.io/badge/POST-F59E0B?style=flat-square) | `/api/bridge/ingest` | Production ingestion endpoint |
| ![POST](https://img.shields.io/badge/POST-3B82F6?style=flat-square) | `/api/mesh/reset` | Reset demo state |
| ![GET](https://img.shields.io/badge/GET-22C55E?style=flat-square) | `/swagger-ui.html` | API documentation |

</div>

<br/>

## ✦ Quick Start

<details open>
<summary><b>1 · Clone the repository</b></summary>

<br/>

```bash
git clone https://github.com/i-Anurag1/OffPay.git
cd OffPay
```

</details>

<details open>
<summary><b>2 · Run the application</b></summary>

<br/>

Windows:

```bash
.\mvnw.cmd spring-boot:run
```

Linux / macOS:

```bash
./mvnw spring-boot:run
```

Then open `http://localhost:8080`.

</details>

<details>
<summary><b>3 · Run with Docker</b></summary>

<br/>

Build the image:

```bash
docker build -t offpay .
```

Run the container:

```bash
docker run -p 8080:8080 offpay
```

Or use Docker Compose:

```bash
docker compose up --build
```

</details>

<details>
<summary><b>4 · Run the tests</b></summary>

<br/>

```bash
./mvnw test
```

</details>

<br/>

## ✦ Testing

<div align="center">

| 🔐 Cryptography | 🧨 Tamper Resistance | 🏁 Concurrency |
|:---:|:---:|:---:|
| Encryption and decryption validation | Tampered packet rejection | Concurrent duplicate delivery handling |

</div>

```mermaid
flowchart LR
    DEV(["👨‍💻 Developer"]) --> GIT["Git Push"] --> GH["GitHub"] --> CI{{"GitHub Actions"}}
    CI --> T["🧪 Maven Tests"]
    T --> ST["✅ Validation"]

    classDef ci fill:#2088FF,stroke:#0B3D91,color:#fff
    classDef ok fill:#22C55E,stroke:#166534,color:#fff
    class CI ci
    class ST ok
```

<br/>

## ✦ Current Limitations

This project demonstrates **offline payment routing and backend settlement logic**. Production deployment would require:

<div align="center">

| Area | Requirement |
|:---|:---|
| 📶 Connectivity | Real Android BLE communication |
| 🏛️ Payments | Real UPI / NPCI integration |
| 🔑 Device trust | Hardware-backed device keys |
| ⚡ Idempotency | Redis replacing in-memory idempotency storage |
| 🗄️ Data | Production database replication |

</div>

<br/>

## ✦ Roadmap

```mermaid
timeline
    title OffPay Roadmap
    section Current
        Core : Signed and encrypted packets : Mesh simulation : Atomic idempotency
        Platform : Spring Boot backend : Docker : GitHub Actions CI
    section Next
        Mobile : Android Kotlin BLE app : Real device-to-device mesh
        Scale : Redis distributed idempotency : Kafka event sourcing
    section Later
        Platform : Kubernetes deployment
        Integration : Bank API integration
```

<br/>

## ✦ Author

<div align="center">

**Anurag Thakur**

[![GitHub](https://img.shields.io/badge/GitHub-i--Anurag1-181717?style=for-the-badge&logo=github)](https://github.com/i-Anurag1)

</div>

<br/>

<div align="center">

```text
SIGN  →  ENCRYPT  →  GOSSIP  →  BRIDGE  →  VERIFY  →  SETTLE
```

### Offline-first payments, verified on arrival

**Built with Java 17 · Spring Boot 3 · JPA · Docker · GitHub Actions**

<br/>

[Live Demo](https://off-pay-five.vercel.app/) &nbsp;·&nbsp; [Source Code](https://github.com/i-Anurag1/OffPay)

<img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=12,20,24&height=120&section=footer" alt="footer" width="100%"/>

</div>
