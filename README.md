# Multi-Tenant SaaS API Management Platform (MetricPort)

A high-performance, multi-tenant SaaS API management platform built with Spring Boot, Spring Cloud Gateway, PostgreSQL, and Redis.

The platform is architected into two primary components:
1. **Control Plane** (`control-plane`): Modular monolith responsible for tenant administration, API catalog, subscription plans, credentials, access policies, and audit logging.
2. **Data Plane / API Gateway** (`api-gateway`): Reactive gateway handling runtime traffic routing, security validation, distributed rate limiting, and metrics.

---

## Repository Structure

```
MetricPort/
├── pom.xml                     # Maven root aggregator & dependency parent
├── docker-compose.yml          # Infrastructure services (PostgreSQL, Redis)
├── .env.example                # Sample environment variables
├── .gitignore                  # Git ignore rules
├── control-plane/              # Control Plane service
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/platform/controlplane/
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       └── test/
├── api-gateway/                # API Gateway service
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/platform/gateway/
│       │   └── resources/
│       │       └── application.yml
│       └── test/
├── infrastructure/             # Deployment scripts & infra configs
└── docs/                       # Architecture decisions & design docs
```

---

## Prerequisites

- **Java**: 21 LTS
- **Maven**: 3.9+
- **Docker & Docker Compose**: v2+

---

## Quickstart

### 1. Launch Infrastructure
Start PostgreSQL and Redis in the background:
```bash
docker-compose up -d
```

Verify services are healthy:
```bash
docker-compose ps
```

### 2. Build the Entire Monorepo
From the repository root:
```bash
mvn clean compile
```

### 3. Run the Services

**Control Plane (Port 8080):**
```bash
cd control-plane
mvn spring-boot:run
```
Actuator Health: `http://localhost:8080/actuator/health`

**API Gateway (Port 8081):**
```bash
cd api-gateway
mvn spring-boot:run
```
Actuator Health: `http://localhost:8081/actuator/health`

---

## Implementation Milestones

- [x] **Milestone 1 — Project Foundation and Infrastructure**
- [ ] **Milestone 2 — Identity and Multi-Tenancy**
- [ ] **Milestone 3 — API Management**
- [ ] **Milestone 4 — Dynamic API Gateway**
- [ ] **Milestone 5 — API Consumption**
- [ ] **Milestone 6 — Runtime Policies**
- [ ] **Milestone 7 — Analytics and Observability**
- [ ] **Milestone 8 — Testing and Hardening**
