how to build multi-tenant saas api management platform project.
explain the architecture, data flow, users etc.

# Building a Multi-Tenant SaaS API Management Platform

A Multi-Tenant SaaS API Management Platform is a platform where multiple companies can register their APIs, control who can access them, issue API keys, define usage limits, monitor traffic, and manage subscriptions — all through a single application.

Think of it as building a simplified combination of Kong Gateway and an API developer portal, rather than merely building another REST API. Kong's architecture and other commercial API management systems separate API configuration from the runtime that processes API traffic.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://docs.konghq.com\&sz=32)

Kong Docs+1



The interesting engineering challenge is that your platform must serve two distinct purposes: managing APIs for customers and securely processing requests sent through those APIs.

## 1. Understand the product through an example

Imagine three companies using your platform.

[Online shopping concept, People buying products in the online store flat vector illustration](https://images.openai.com/static-rsc-4/ewDbLaMQAy7wXBIP81hUD4TRqXUrpZp07ccC6wXWwMHDINAQ680Ty9nSkCZQM4f-cDrysVpmXx7uUIZNs6OSnMq4mE5EQamiOPuBG9b4EVEfdGcg-3vILjjY76zV2A4n-bd7mEMzFyoHuBkM3WkyLm8wqeVveQWYSb6bmlWDzvGZE3qAfux1ckMAQ-sUN1q2?purpose=inline)

Tenant A — ShopSphere

An e-commerce company

ShopSphere registers its Order API and Inventory API. It publishes them with API keys, quotas, and a developer subscription plan.

[SynCraft Labs | Elevating African Businesses Through Digital Excellence](https://images.openai.com/static-rsc-4/odCG8xvOPbLmyiP_sQai1pF9yMY4IEBNihZFzJ5r5LB2B_v3AOAasG4BMf_MORCrUmHg97HYEnQvsebSkS0iAiZIZ0ZGcgqas05hCiFqYUXnTjDHN1vLAbGqYaXFrlO3ZyooxVxHofpJ2Reo34XI4gHEIA8KRvzs3hmzOBsKr5g?purpose=inline)

Tenant B — FinTrack

A financial technology company

FinTrack registers its own APIs and manages its own team, API credentials, and traffic limits.

[Kleinberg Consulting](https://images.openai.com/static-rsc-4/P6IBT45WvnkIUn1hySlSgiohCHum57mKnch1GUx0KetRW-Zzph9unZEDnbpe_LKsS6_t8D6zg-Dmutz-wkyLsFaJhGZAJdsV2nnFduvRv1O8voy0gx29XLNU8DDGW4wHuQcA8H2Zd4EMZztY6HhZw7GNR2MP3wqjqktggfocjm4?purpose=inline)

Tenant C — DataPilot

An API consumer

DataPilot subscribes to ShopSphere's Inventory API and calls it from its own application using an issued credential.

All these organizations use the same SaaS platform, but ShopSphere must not be able to view FinTrack's private configurations, credentials, or analytics. Likewise, DataPilot should only be able to consume the APIs and operations it is authorized to access.

This is the fundamental concept of multi-tenancy: shared platform infrastructure with logically isolated customer data, permissions, and usage.

An important distinction is that an API provider and an API consumer are not necessarily different types of organization. The same organization could publish its own APIs and subscribe to APIs published by another organization.

## 2. The users and their responsibilities

Your platform has four important user categories.

The platform administrator operates the SaaS product itself. This person can manage organizations, suspend abusive accounts, inspect platform-wide health, and manage subscriptions or platform-level configuration. These are global privileges and must not be confused with a tenant administrator's privileges.

The tenant owner or organization administrator manages their company's workspace. They invite team members, assign roles, register upstream services, configure API routes, publish APIs, define access policies, and review their organization's usage.

The tenant's developers and analysts work with the APIs and their operational data. A developer might configure an endpoint or review request failures, while an analyst might only view analytics. Your role-based access control system should prevent users from modifying resources outside their assigned permissions.

The external API consumer is a developer or application from another organization. They discover published APIs in a developer portal, read their documentation, register an application, request a subscription, obtain credentials, and integrate the APIs into their software.

These responsibilities should be reflected in your user interface. A tenant's management dashboard is different from the developer portal used to discover and consume APIs.

## 3. High-level system architecture

The most important architectural decision is to divide the system into a control plane and a data plane.

The control plane manages APIs, users, credentials, subscriptions, plans, and policies. The data plane receives actual API requests, evaluates the applicable policies, and forwards allowed requests to their destination services. This separation is an established pattern in API gateway architectures.&#x20;

The diagram represents logical components, not necessarily separate microservices. For a portfolio project, I would implement the management side as a modular monolith and run the gateway as a separate runtime service. This gives you a meaningful architectural boundary without the complexity of independently deploying ten different services.

### The control plane

The control plane is the brain of your platform. When an organization registers an API, creates a subscription plan, or changes a rate limit, those operations go through the control plane.

It exposes management endpoints such as `POST /apis`, `POST /routes`, `POST /products`, `POST /subscriptions`, and `POST /credentials`. It validates the user’s identity and organization membership, checks permissions, persists the changes in PostgreSQL, and publishes updated gateway configuration.

The control plane is also responsible for the developer portal, API catalog, OpenAPI documentation, team management, billing-related records, and audit logs.

### The data plane

The data plane is the gateway that sits between API consumers and backend services. It receives requests, finds the applicable route, validates the consumer's credentials, checks subscription status and traffic limits, and forwards allowed requests to the correct upstream service.

It is on the critical path for every API call, so it should not depend on the management dashboard being available. The gateway should use locally cached configuration and efficient shared infrastructure such as Redis for distributed rate limiting, instead of making several database queries for every request.

The exact gateway implementation can vary. For example, Kong documents the use of a gateway proxy to manage requests and forward them to upstream services.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://docs.konghq.com\&sz=32)

Kong Docs+1



### The configuration distribution mechanism

Suppose ShopSphere changes the rate limit of its Inventory API from 100 requests per minute to 50. The management API stores the new configuration and increments its configuration version. It then notifies the gateway instances or makes the new version available for them to retrieve.

Each gateway validates the configuration and updates its active configuration snapshot. A periodic version check provides a recovery mechanism if an event notification is missed.

This means administrative operations update configuration, while normal client requests use the latest available configuration directly in the data plane. For a simple first version, a configuration endpoint plus short polling is sufficient; you can add push-based updates later.

## 4. The data model: what should you store?

Your database schema is the foundation of the entire system. If the relationships between tenants, APIs, applications, subscriptions, and credentials are poorly designed, authorization and usage tracking will become complicated later.

A useful way to design the schema is to start with the following domain entities.

Organization / Tenant

Owns resources and has member users

API Service

An upstream backend

API Product

A publishable collection of APIs and operations

Plan

Rate limits, quota and access rules

Developer Application

Owned by a consumer organization

Subscription + API Credential

Connects a consuming application to an API product under a specific plan

### Tenant, user and membership

A `Tenant` represents an organization or workspace. The `User` table stores identities, while `Membership` connects users to organizations and assigns roles such as `OWNER`, `ADMIN`, `DEVELOPER`, `ANALYST`, and `BILLING_ADMIN`.

Do not put a single `tenant_id` directly on the user and assume that user belongs to only one organization. A developer could belong to multiple companies, each with different permissions.

### API service and route

An `ApiService` represents an upstream backend, such as ShopSphere's Inventory service, with a configured target URL like `https://inventory.example.com`.

A `Route` defines how incoming requests map to that service. For example, a request to `/v1/inventory/items` might be routed to `/api/items` on the upstream service. A route should include the HTTP method or method-matching rules, public path, upstream path mapping, environment, and associated policies.

Keep upstream destinations separate from routes because one backend service can expose several routes.

### API product and plan

An `ApiProduct` is the offering that an API provider publishes for other developers. It can group several services, routes, and API versions into one consumable product.

A `Plan` defines the terms of access to that product. For example, a Starter plan might permit 60 requests per minute with a monthly quota of 10,000 requests. A Pro plan might offer a higher throughput limit and a larger quota.

These values are illustrative. The key design concept is that an API product defines what can be accessed, while a plan defines the conditions under which it can be accessed.

### Application, subscription and credentials

A `DeveloperApplication` represents a software application owned by a consumer organization. For example, DataPilot might register an application called `inventory-sync-service`.

A `Subscription` links that application to ShopSphere's API product and the selected plan. It records approval state, effective dates, and whether access is currently active.

An `ApiCredential` belongs to the application and should be scoped to the appropriate subscription or access permissions. It holds metadata such as the key identifier, a hash of the secret, creation date, expiry date, and revocation status.

This relationship lets one application have multiple credentials and lets you revoke one credential without deleting the application itself.

### Usage events and audit records

A `UsageEvent` records operational information such as the provider tenant, consumer organization, application, route, timestamp, HTTP status code, latency, and request identifier. These records drive the analytics dashboard and quota accounting.

An `AuditLog` captures management actions: who changed a route, who revoked a key, or who altered a subscription. Usage events and audit logs serve different purposes and should not be merged into one generic logging table.

Finally, store a versioned representation of published gateway configuration. This makes configuration changes traceable and supports safe updates and rollbacks.

## 5. How multi-tenant data isolation works

Consider a database shared by ShopSphere and FinTrack. Both organizations might have records in the same `api_services` table.

| id      | tenant_id   | service_name  |
| ------- | ----------- | ------------- |
| svc_101 | tenant_shop | Inventory API |
| svc_202 | tenant_fin  | Payment API   |

When ShopSphere's administrator requests `GET /v1/management/apis`, the server derives the authorized tenant context from the authenticated user's identity and verified membership. The query then retrieves only services permitted for that tenant.

Conceptually, the database query would be:

```
SELECT *
FROM api_services
WHERE tenant_id = :authorized_tenant_id;
```

The critical detail is that `authorized_tenant_id` comes from trusted server-side authorization context, not an arbitrary ID supplied in the request body or an unverified `X-Tenant-ID` header.

Every tenant-owned resource must follow this principle, including routes, credentials, subscriptions, API products, and analytics. PostgreSQL row-level security can provide an additional layer of defense by restricting which rows a database role can access.

For your MVP, a shared PostgreSQL database with shared tables and explicit tenant scoping is practical. You can introduce stronger database-enforced isolation or dedicated databases for enterprise customers later.

There is also a distinction between the provider tenant and the consumer tenant. An API definition is owned by the provider, but a subscription belongs to the organization consuming that API. Your subscription and usage records should preserve both relationships. Otherwise, cross-company usage analytics and authorization will become difficult to implement correctly.

## 6. The complete API request data flow

Now consider what happens when DataPilot actually calls ShopSphere's Inventory API.

`diagram`

### Step 1: Resolve the route

The gateway receives the request and uses the hostname, path and HTTP method to find the relevant route in its active configuration.

For example:

`https://api.example.com/shopsphere/v1/inventory/items`

could map to:

`https://inventory.shopsphere.com/api/items`

The gateway must reject unknown or unpublished routes rather than forwarding arbitrary destinations. This is also important to prevent server-side request forgery (SSRF).

### Step 2: Identify the calling application

DataPilot sends its API credential in a header, for example:

```
GET /shopsphere/v1/inventory/items HTTP/1.1
Host: api.example.com
X-API-Key: <issued-api-key>
```

Your gateway uses the key identifier or prefix to look up the credential, then verifies the secret securely and checks that the credential is active. It also checks whether the related application and subscription are valid and whether the subscription authorizes access to the requested product and route.

Do not store retrievable plaintext API secrets in your database. Display a newly generated secret only at creation time and store a cryptographic hash for verification.

Also, an API key is primarily useful for identifying an application and associating its usage with an access plan. It should not replace authentication and authorization for your management dashboard. For user-facing management operations, use a proper identity mechanism such as a validated session or JWT-based authentication system. AWS similarly cautions against treating API keys as the sole mechanism for authenticating or authorizing users.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://docs.aws.amazon.com\&sz=32)

Amazon API Gateway+1



### Step 3: Enforce policies and limits

After identifying the application, the gateway evaluates the applicable access policies. These may include allowed routes, permitted HTTP methods, IP restrictions, request-size limits, and rate limits.

Redis is useful for distributed rate limiting because multiple gateway instances can share request counters. A key might be scoped to the tenant, application, credential, route, or some combination of these dimensions.

There are two related but distinct controls. Rate limiting regulates traffic over a short interval, such as 60 requests per minute. Quota enforcement regulates cumulative consumption over a larger period, such as 10,000 requests per month.

Usage plans, API keys, rate throttling, and quotas are standard elements of API management systems. However, usage-plan quotas are not necessarily hard spending limits; AWS explicitly documents that its usage-plan throttling and quotas operate on a best-effort basis. If strict quota enforcement is required in your platform, design and test the counter and reservation logic accordingly.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://docs.aws.amazon.com\&sz=32)

Amazon API Gateway+1



### Step 4: Forward the request

If all checks pass, the gateway forwards the request to the registered upstream service. It preserves appropriate headers, applies configured transformations, enforces timeouts, and uses server-side credentials where the upstream requires them.

The gateway should not expose the provider's internal credentials to the consumer. It should also have safeguards against unsafe redirects, connection exhaustion, unbounded response sizes, and arbitrary upstream URLs.

When the backend returns a response, the gateway sends it back to DataPilot. It should add a request or trace identifier so the caller and operator can correlate failures with the corresponding logs.

### Step 5: Record usage asynchronously

The gateway emits a usage event containing operational metadata and does not wait for the analytics database to complete its write before returning the response.

A background worker batches and persists those events. The analytics dashboard can then show total calls, error rates, latency percentiles, traffic over time, and consumption by application or API product.

This design prevents analytics writes from becoming a bottleneck for every API request. For a more reliable implementation, consider durable message delivery, duplicate-event handling, and a transactional outbox where appropriate.

## 7. The management data flow

The management flow begins when a provider logs into the tenant dashboard. Suppose ShopSphere wants to publish an Inventory API.

The user creates an API service and its upstream destination, defines routes, uploads or imports an OpenAPI specification, and groups the relevant endpoints into an API product. The administrator publishes that product and attaches one or more plans.

DataPilot then visits the developer portal, creates an application, and subscribes to the product. If the subscription requires provider approval, the platform marks it as pending until an authorized ShopSphere administrator approves it.

Once approved, the platform issues the appropriate credential and makes the subscription available to the gateway. The gateway receives the updated configuration, and DataPilot can start making authorized requests.

When ShopSphere later revokes DataPilot's subscription or changes its plan, the same process works in reverse: the control plane persists the change and publishes new policy configuration. The gateway must stop honoring the revoked access within your defined configuration propagation window.

This illustrates why configuration versioning, revocation, and cache invalidation deserve first-class attention in your implementation.

## 8. Technologies and deployment architecture

You do not need a large microservices ecosystem to build this project. The objective is to demonstrate clear boundaries, secure tenant isolation, and a gateway that enforces configuration correctly.

A practical implementation can use a React or TypeScript-based frontend for the tenant dashboard and developer portal, a single modular backend for management operations, PostgreSQL for durable application data, and Redis for distributed rate limiting and frequently accessed cache entries. Implement the gateway as a separate service using a suitable reverse-proxy framework or mature gateway foundation.

For example, a JVM implementation could use Spring Boot for the control plane and Spring Cloud Gateway for the data plane. A TypeScript implementation could use NestJS for management and a suitable Node.js proxy stack for request forwarding. Pick one primary backend ecosystem rather than building both at once.

The logical component structure might look like this:

PRESENTATION

Tenant Dashboard + Developer Portal

Organization settings, API catalog, subscriptions, keys, documentation and analytics.

CONTROL PLANE

One Modular Management Backend

Identity and RBAC · Tenant Management · API Registry · Plans and Subscriptions · Credential Lifecycle · Configuration Publishing · Audit

DATA PLANE

Separate API Gateway Service

Routing · Credential Verification · Policy Enforcement · Rate Limiting · Request Transformation · Usage Event Emission

SHARED INFRASTRUCTURE

PostgreSQL + Redis + Optional Message Broker

Durable configuration, tenant records, distributed counters, and asynchronous analytics processing.

During local development, Docker Compose can run the frontend, management backend, gateway, PostgreSQL and Redis together. Add a message broker and dedicated usage worker when you are ready to demonstrate durable asynchronous processing.

The important design rule is to keep the gateway request path independent from unnecessary management-plane calls. It should not need to query multiple application tables or contact the frontend for every request.

## 9. How to build the project incrementally

### Phase 1 — Multi-tenancy and identity

First implement signup, login, organization creation, invitations, memberships and roles. Make tenant-scoped authorization work before adding the gateway. Create automated tests demonstrating that a user in ShopSphere cannot retrieve or modify FinTrack's resources, even when the user guesses their IDs.

This phase establishes the trust boundaries on which every subsequent feature depends.

### Phase 2 — API registry and request proxying

Implement upstream services, route definitions, environments and basic configuration publishing. Build the gateway so that a registered public route actually forwards requests to a test backend. Include method matching, path mapping, timeouts and safe destination validation.

At the end of this phase, the platform is already more than a CRUD application: it can manage APIs and route live traffic through them.

### Phase 3 — Applications, subscriptions and access control

Introduce API products, plans, developer applications, subscription approvals and API credentials. The gateway should reject invalid or revoked credentials, inactive subscriptions and disallowed routes.

Add Redis-backed rate limiting, concurrency or request-size controls where appropriate, plus predictable error responses. A request exceeding its rate limit should return HTTP `429 Too Many Requests`; missing or invalid credentials generally produce `401 Unauthorized`, while a recognized caller that is not authorized should receive `403 Forbidden`.

### Phase 4 — Documentation and analytics

Provide OpenAPI import or authoring, a Swagger-style interactive documentation page, and a developer portal that explains how to subscribe and call each API. Build analytics views for traffic, latency, response codes and application-level consumption.

Use synthetic upstream APIs during development so you can reliably demonstrate success paths, timeouts, backend failures and limit violations without depending on external services.

### Phase 5 — Production-oriented reliability

Finally, work on key rotation, policy propagation, audit trails, secret redaction, gateway health checks, metrics, trace IDs and resilience under load. Add idempotent usage processing and reliable event delivery. Introduce API versioning and environment promotion so that a configuration can move from development to staging and production with a controlled process.

Test the gateway under concurrent traffic to verify that Redis limits behave correctly across multiple instances and that one tenant's traffic cannot bypass another tenant's limits. Test subscription revocation while requests are flowing. These scenarios will reveal more about the quality of your architecture than adding many CRUD endpoints.

## 10. What makes this a strong portfolio project?

The project's value is not merely that it allows users to create API keys. Its engineering depth comes from connecting the entire lifecycle: an API provider registers a backend, publishes a product, defines policies, a consumer subscribes, credentials grant appropriately scoped access, the gateway enforces the policy, and analytics records the resulting traffic.

A compelling demonstration would create two provider organizations, publish separate APIs, subscribe a third organization to one provider's product, and then show that invalid credentials are rejected, request bursts are throttled, traffic appears in analytics, and the subscription can be revoked without changing the upstream application.

The key architectural principle is this: the control plane decides how APIs should behave; the data plane enforces those decisions on live requests. Tenant isolation must span both planes, from the management database and configuration cache to credentials, rate-limit counters and analytics.

That is the foundation on which you can later build custom domains, monetization, billing integration, OAuth client credentials, API version management, webhooks, and enterprise isolation.

2. now move to implementation part. should be first work on system design? can we built in java ?

## 11. The implementation stack I recommend

Yes, we can build the entire platform in Java. My recommendation is to use Java for both the management backend and the gateway, but keep them as separate applications because they have different responsibilities.

Control plane — Spring Boot

Spring MVC, Spring Security, Spring Data JPA, PostgreSQL and Flyway.

Handles users, organizations, API registration, products, plans, subscriptions, credentials and configuration management.

Data plane — Spring Cloud Gateway

Java, Spring WebFlux, reactive HTTP routing and gateway filters.

Handles route matching, API-key validation, policy enforcement, rate limiting and forwarding to backend APIs.

Infrastructure

PostgreSQL, Redis and Docker Compose.

Stores durable records, manages shared rate-limit counters and provides reproducible local development infrastructure.

Frontend — when the core flow works

React and TypeScript.

Provides the tenant dashboard and developer portal. Begin with the backend and gateway contracts before building the complete UI.

For a new project, use Java 21 and a compatible Spring Boot/Spring Cloud combination. As of October 2026, Spring Boot 4.1.1 is stable, and the Spring Cloud 2025.1.x release train supports Spring Boot 4.1.x; Spring Cloud Gateway 5.0.3 is listed as stable. Generate the projects through [Spring Initializr](https://start.spring.io/?utm_source=chatgpt.com) and let the dependency-management configuration select compatible versions.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://docs.spring.io\&sz=32)

Home+2



One important detail: Spring Cloud Gateway uses a reactive programming model. Keep the management application conventional and synchronous where that makes development easier, but learn the gateway's WebFlux filters and request lifecycle separately. Avoid blocking database calls inside the gateway's reactive request path.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://docs.spring.io\&sz=32)

Home+1



## 12. What we should design before writing code

We should start with system design, but only enough design to guide implementation. The goal is not to document every future enterprise feature. It is to answer the decisions that would otherwise force us to rewrite important parts of the application.

The first design document should establish the initial scope. Our MVP will let organizations register APIs, configure routes, publish an API product, create consumer applications, issue credentials, enforce access policies, and inspect usage. Billing integration, a distributed microservice fleet, custom domains and advanced monetization can wait.

The second document should describe the architecture and two primary request flows: how an administrator publishes a route and how a consumer's request passes through the gateway. The third should define the database model and tenant ownership of every entity. The fourth should establish the security rules, especially organization membership, access control, credential revocation, configuration updates and upstream URL validation.

We should also record a few architectural decisions, such as why the control plane is a modular monolith, why the gateway is a separate deployment, and why we begin with shared PostgreSQL tables rather than a database per tenant.

## 13. Our first implementation milestone

Rather than building the dashboard, billing, analytics and every management endpoint at once, we should start with one end-to-end vertical slice.

`diagram`

For example, we could register a local test API at `http://mock-api:8080`, publish a public gateway route such as `/demo/products`, and verify that a request to the gateway reaches the correct test endpoint. We would also test that an unpublished route is unavailable and that one tenant cannot change another tenant's configuration.

Once this works, we add API credentials and subscription authorization, then Redis-backed rate limiting, followed by usage events and analytics. This order lets every major feature build upon a working system.

So our next step should be the system-design blueprint: define the exact MVP requirements, components, ER diagram, tenant-isolation rules, API contracts and repository structure. Then we can create the Java projects and implement the first vertical slice without overengineering the system.

3. system design
# System Design Plan: Multi-Tenant SaaS API Management Platform

Our goal is to design the system we intend to build in Java before writing the implementation. We should approach this as a backend engineering project: start with requirements, understand the domain, define the architecture, design the database and APIs, and then decide how the components communicate.

The important thing is to design enough to make good architectural decisions without overengineering features we have not yet built.

## Overall plan

## 01

Requirements and product scope

Define the problem, target users, functional requirements, non-functional requirements, MVP boundaries, and important use cases. This prevents us from designing components without knowing their responsibilities.

Deliverable: Requirements document and use-case model.

## 02

Domain model and business rules

Identify the entities and their relationships: Tenant, User, Membership, API Service, Route, API Product, Plan, Application, Subscription, API Credential and Usage Event. Define ownership, lifecycle and permissions.

Deliverable: Domain model and business rules.

## 03

High-level architecture

Decide the system boundaries, component responsibilities and communication paths. Establish why we need a management control plane and a separate API gateway data plane.

Deliverable: Architecture diagram and architectural decision records.

## 04

Database and tenant isolation

Design the ER diagram, primary and foreign keys, constraints, indexes, tenant ownership, and strategies for preventing cross-tenant data access.

Deliverable: ER diagram and initial database schema.

## 05

Critical workflows and API contracts

Design authentication, API registration, route publishing, subscription approval, credential validation, rate limiting, request forwarding, revocation and usage tracking.

Deliverable: Sequence diagrams and representative REST API contracts.

## 06

Security, reliability and scalability

Decide how to manage secrets, authorization, configuration propagation, Redis counters, cache invalidation, gateway failures, logging and asynchronous analytics.

Deliverable: Security model and failure-handling strategy.

## 07

Implementation blueprint

Translate the design into the Java repository structure, Spring modules, infrastructure setup, migration strategy, testing plan and implementation milestones.

Deliverable: Build-ready technical blueprint.

## The design decisions we will start with

To keep the work focused, I suggest we use the following initial assumptions throughout the design, revisiting them when a requirement gives us a reason to change them.

| Area                      | Initial decision                                               |
| ------------------------- | -------------------------------------------------------------- |
| Backend language          | Java 21                                                        |
| Management backend        | Spring Boot modular monolith                                   |
| API gateway               | Separate Spring Cloud Gateway application                      |
| Primary database          | PostgreSQL                                                     |
| Rate limiting and caching | Redis                                                          |
| Authentication            | Spring Security                                                |
| Database migrations       | Flyway                                                         |
| Local infrastructure      | Docker Compose                                                 |
| Data isolation            | Shared database and tables with tenant-scoped access           |
| Analytics                 | Usage events with asynchronous processing as a later milestone |
| Frontend                  | Deferred until the core backend flows are defined              |

These are starting choices rather than permanent commitments. For example, we will determine exactly how gateway configuration gets distributed during the architecture phase instead of prematurely committing to a message broker.

## How we will work through each phase

For every phase, we will first explain the concepts and the decisions we need to make, then create the relevant diagrams or design artifacts, and finally connect those decisions to the future Java implementation. We will not jump straight into code or introduce terminology without establishing why it matters.

We should also distinguish the MVP architecture from future production-grade improvements. The MVP must demonstrate real tenant isolation, dynamic API routing, subscription-based access, secure credentials and rate limiting. Distributed deployments, billing integrations and advanced enterprise features can be designed as extensions rather than requirements for the first version.

At the end, we should have a system design that is detailed enough to begin implementation without guessing how the core components, database relationships or request flows are supposed to work.

Our first discussion should be Phase 1: Requirements and Product Scope. We will establish exactly what the platform does, who uses it, what problems it solves, which features belong in the MVP, and how we will know the first version is complete. Everything else will follow from those decisions.

Phase 1: Requirements and Product Scope

# Phase 1 — Requirements and Product Scope

System Design Document · Version 1.0 · Multi-Tenant SaaS API Management Platform

The purpose of this phase is to define precisely what we are building before deciding how to implement it. By the end, we should know who uses the platform, what each user is allowed to do, which features belong in the first release, and what conditions must be satisfied for the MVP to be considered complete.

## 1.1. Problem statement

Organizations that expose APIs often need to implement API authentication, access control, traffic limits, documentation, and usage monitoring. When each organization implements these capabilities independently, it has to maintain the corresponding infrastructure and operational tooling itself.

Our platform provides these capabilities through a centralized API management service. An organization can register an existing backend API, configure how external requests reach it, publish it as an offering, and control which applications can consume it. Other organizations can discover the offering, subscribe to it, obtain credentials, and make requests through the platform's gateway.

The platform is multi-tenant because several organizations share the same service and infrastructure, while their resources, permissions, configurations, and usage information remain logically isolated.

The problem we are solving is not how to build backend APIs. It is how to manage access to existing APIs safely and consistently.

For example, an e-commerce company already has an Inventory API. It should not need to rewrite that API to make it available to partners. It should be able to register the existing backend with our platform, expose a controlled gateway endpoint, limit consumption, and monitor usage.

## 1.2. Product definition

Our product will be a multi-tenant API management SaaS with two main capabilities.

The first is API lifecycle management. Organizations can register services, configure routes, publish API products, manage consumer applications, define access plans, and inspect API usage through a management dashboard.

The second is API runtime enforcement. A gateway receives incoming requests, identifies the calling application, checks its credentials and subscription permissions, enforces traffic policies, forwards authorized requests to their upstream services, and records usage.

This leads to an important distinction that will guide the rest of our design.

Management capability

Configure the system

Users register APIs, publish routes, create plans, approve subscriptions, rotate credentials, and view analytics.

Runtime capability

Enforce the configuration

The gateway resolves routes, checks access permissions, applies rate limits, forwards requests, and records outcomes.

The management application must not become a mandatory intermediary for every API request. A consumer application should communicate with the gateway, and the gateway should enforce the published configuration without requiring a new management API call for each request.

## 1.3. Users and actors

We have several kinds of users, and we must understand their permissions before designing roles and database relationships.

Platform administrator. This actor operates the SaaS platform itself. They may inspect platform health, manage organizations, suspend abusive accounts, and perform global administrative operations. Their privileges are different from those of ordinary customer administrators.

Tenant owner or administrator. This user manages an organization's workspace, team membership, API services, routes, API products, plans, subscriptions, and applicable policies. They must not be able to access another organization's private resources merely by guessing an identifier.

Tenant developer or analyst. A developer works with API configurations, credentials, documentation, and operational information according to assigned permissions. An analyst primarily reads usage and performance information. The MVP can begin with a small set of roles rather than a complicated custom permission system.

API consumer developer. This person manages applications that consume published APIs. They discover products, register applications, request subscriptions, and use issued credentials to access authorized routes. They may belong to an organization that also publishes its own APIs.

A crucial modeling decision is that an organization is not permanently a provider or a consumer. A single organization can publish APIs and subscribe to products from other organizations. Therefore, provider and consumer are roles that an organization can play in a particular relationship, rather than mutually exclusive types of tenant.

## 1.4. Primary business workflow

We will design the initial product around one complete scenario: a provider publishes an API, a consumer subscribes to it, and the gateway enforces the resulting access rules.

`diagram`

Let's make this concrete. ShopSphere registers its Inventory API and publishes a Starter plan. DataPilot creates an application named `inventory-sync`, requests a subscription, and receives an API key after approval. DataPilot calls ShopSphere's API through our gateway. The gateway checks whether the key is valid, whether the subscription is active, whether the requested route is allowed, and whether the application is within its rate limit.

When those checks pass, the gateway forwards the request. When the key is invalid, the subscription is revoked, or the rate limit is exceeded, the gateway rejects the request with an appropriate HTTP status code. Usage information is recorded so the provider and the consumer can inspect relevant analytics.

This single scenario will determine most of our initial domain entities, APIs, access-control rules, and test cases.

## 1.5. Functional requirements

Functional requirements describe what the product must do. For the MVP, we should prioritize complete workflows instead of implementing many unrelated features.

### Organization and identity management

The system must allow users to register, authenticate, create an organization, and invite other users. An organization can have multiple members, and a user can belong to multiple organizations. Each membership has a role that determines the operations the user can perform.

Management requests must be authenticated, and authorization must be checked against the user's membership in the active organization. The platform administrator operates in a separate global administrative context.

### API registration and route management

An organization must be able to register an existing backend service by providing its name, destination URL, and environment. It must be able to define gateway routes using public paths and HTTP methods, map those routes to upstream services, and publish or disable them.

For example, a provider could configure a public route such as `/v1/inventory/items` to forward requests to an existing backend endpoint such as `/api/items`. The system must reject invalid configurations and must not allow untrusted callers to use the gateway as an arbitrary proxy to internal services.

### API products, plans, and subscriptions

A provider must be able to group routes into a publishable API product. Each product can offer one or more plans defining its permitted access and rate limits.

A consumer organization must be able to register an application and request a subscription to a published product. The platform must track whether that subscription is pending, active, rejected, expired, or revoked. A provider administrator can approve or reject requests where approval is required.

A plan should have a clearly defined effective policy. For the first release, a configurable requests-per-minute limit is sufficient to demonstrate plan-based enforcement. More elaborate billing and quota policies can be added after the basic lifecycle works.

### Credential and access management

The system must issue API credentials for authorized consumer applications. It must store credential secrets securely, support revocation, and prevent revoked or inactive credentials from being used to access protected APIs.

The gateway must associate an incoming credential with its application and relevant subscription. A valid credential alone does not guarantee access: the application must also be authorized to consume the requested product and route.

### Gateway request processing

The gateway must resolve published routes, validate access credentials, enforce subscription and route policies, apply rate limits, and forward permitted requests to their configured upstream services. It must handle missing routes, invalid credentials, rate-limit violations, and upstream failures with appropriate HTTP responses.

Dynamic route configuration is an essential requirement. Publishing a new route should not require rebuilding or redeploying the gateway application.

### Analytics and auditability

The platform must record request counts, status codes, timestamps, latency and relevant application or route identifiers. Tenant dashboards should display basic usage over time and distinguish successful requests from failures.

Management actions that affect security or access, such as publishing routes, changing policies, issuing credentials and revoking subscriptions, should also be auditable. Audit history and runtime usage are separate concerns because they have different data requirements and purposes.

The requirements can be summarized by priority as follows.

| Priority            | Capabilities                                                                                                                                                                                                          |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| P0 — MVP essentials | Authentication, tenant isolation, memberships, API registration, dynamic routes, products and plans, applications and subscriptions, API credentials, gateway enforcement, rate limiting, basic analytics, audit logs |
| P1 — Next iteration | OpenAPI import, richer developer portal, API versions, key rotation workflows, more quota policies, configuration history and rollback                                                                                |
| Later               | Billing and payment integration, custom domains, advanced enterprise identity, monetization, complex policy plugins and dedicated tenant infrastructure                                                               |

P0 defines the first demonstrable product. P1 and later features should not delay the core end-to-end workflow.

## 1.6. Non-functional requirements

Non-functional requirements define the quality and operating constraints of the system. For an API management platform, security and correctness are more important than simply maximizing the number of features.

Security and tenant isolation. Every tenant-owned resource must be accessed only through verified server-side authorization. Tests must verify that cross-tenant reads and writes are denied, including requests that use valid identifiers belonging to another tenant. Credential secrets must never appear in application logs, and management operations must be protected independently from consumer API access.

Performance. The gateway should add as little latency as reasonably possible while validating credentials, checking policies and forwarding requests. We should measure gateway overhead separately from upstream response time. During development, we will benchmark a defined workload and document the environment and results instead of claiming production performance without evidence.

Configuration consistency. Changes to routes, subscription state and policies must reach the runtime gateway within a defined propagation interval. For the MVP, we can establish a provisional target of five seconds and verify it with integration tests. Revocation must be handled as a security-sensitive configuration update, not merely as a dashboard change.

Rate-limit correctness. Rate-limit counters must be shared across gateway instances. Otherwise, deploying two gateway instances could unintentionally double the permitted traffic. The design should use atomic counter operations and explicitly define the behavior when Redis is unavailable.

Reliability and failure handling. If an upstream API is unavailable, the gateway must return a controlled error rather than hanging indefinitely. Requests need timeouts, and connection or response processing must be bounded. Failures in analytics should not ordinarily prevent an otherwise authorized API response from reaching the consumer.

Observability. Each gateway request should have a correlation identifier and produce useful operational telemetry. Operators need to distinguish authentication failures, authorization failures, rate limiting, configuration problems and upstream errors without exposing credentials or sensitive request contents.

Maintainability and testability. The control plane and gateway should have clear responsibilities, automated tests and reproducible local infrastructure. Database schema changes must be versioned. Important authorization and request-processing rules should be tested independently as well as through end-to-end scenarios.

These requirements give us measurable design constraints without pretending that an initial local prototype already has the reliability guarantees of a commercial API platform.

## 1.7. What is explicitly out of scope?

Defining what we will not build is as important as listing the features we will implement.

The first version will not be a payment-processing or billing platform. Plans will define access policies, but we will not initially integrate payment gateways, invoices, automatic renewals or complex usage-based charges.

We will not develop the backend APIs that customers register. We will supply test services and let the platform manage existing APIs. Similarly, we will not build a full distributed microservices ecosystem for the management backend. The control plane can remain a modular monolith, with the gateway deployed separately.

Advanced OAuth authorization-server functionality, multiple gateway regions, dedicated databases per customer, sophisticated policy scripting and enterprise-specific identity federation can also wait. We should preserve the ability to introduce these capabilities later, but we should not let them complicate the first implementation.

## 1.8. MVP acceptance criteria

How will we know Phase 1 has translated into a working product? We need concrete scenarios that must pass, not just a list of screens or endpoints.

Tenant isolation works

ShopSphere cannot read or modify FinTrack's APIs, routes, subscriptions or credentials, even when it knows their resource identifiers.

Published APIs work through the gateway

A provider registers an existing backend, publishes a route and successfully receives a response through the public gateway endpoint.

Subscriptions control access

An authorized application can call its subscribed API, while an invalid credential or inactive subscription is rejected.

Rate limits are enforced

An application exceeding its configured limit receives HTTP `429`, and the limit remains effective across multiple gateway instances.

Revocation and analytics work

Revoking a credential or subscription blocks subsequent access within the defined propagation interval, while request usage and security-sensitive management actions can be inspected.

These criteria will become integration tests later. They also prevent a common project pitfall: building many management endpoints while never demonstrating that the gateway actually enforces their configuration.

## 1.9. Phase 1 output and design baseline

We have now established the product definition, actors, primary business workflow, functional requirements, non-functional constraints, MVP boundaries and acceptance criteria.

The resulting MVP is a platform where providers register and publish existing APIs, consumers subscribe through applications, and a separate gateway enforces credentials, permissions and rate limits while producing usage data.

We will carry these decisions into the next phase. The precise ownership relationships between organizations, APIs, applications, products, plans, subscriptions and credentials will determine the database structure, so that is what we need to resolve next.

Phase 2 is the domain model and business rules. We will identify each entity, explain why it exists, define its relationships and lifecycle, and then turn that model into the foundation for the ER diagram.

Phase 2 is the domain model and business rules.

# Phase 2 — Domain Model and Business Rules

System Design Document · Version 1.0 · Multi-Tenant SaaS API Management Platform

In Phase 1, we defined what our platform must do. Now we need to determine what information the platform manages, how that information is related, which organization owns it, and which rules must always remain true.

This phase is particularly important because the same business concept appears in different parts of the system. For example, an API product belongs to a provider, an application belongs to a consumer, and a subscription connects the two. If we model those relationships incorrectly, we will create security problems and unnecessary complexity in our Java implementation.

We will establish the domain model first, define its relationships and business rules next, and use that model as the foundation for the ER diagram and database schema in the next stage.

## 2.1. What is a domain model?

A domain model is a representation of the important concepts in a business and the relationships between them. In software, these concepts commonly become entities, their properties, and the rules governing how they can change.

Consider a simple example from our platform. ShopSphere owns an Inventory API. DataPilot has a software application that wants to consume this API. ShopSphere publishes the API through a product and defines a plan. DataPilot subscribes to that product, and the platform issues an API credential to DataPilot's application.

Each noun represents a potential domain concept: organization, service, product, plan, application, subscription, and credential. However, we should not automatically turn every noun into a database table. We need to understand what each concept represents and what responsibility it has before choosing its final representation.

Our model will distinguish between three broad categories: identity and organization management, API publishing and consumption, and operational records such as usage events and audit logs.

## 2.2. First understand the core relationships

Before discussing individual entities, let's establish the ownership model.

`daigram`

The important relationship is between the provider and consumer organizations. The provider owns and publishes an API product, while the consumer owns the application that subscribes to it. A subscription connects the application to the product under a selected plan.

This means a subscription is not owned in exactly the same way as an API service or a developer application. It crosses an organizational boundary. Both parties have legitimate interests in it, but their permissions differ.

For example, ShopSphere can approve or revoke access to its product, while DataPilot can manage its application and request access. Neither organization should gain unrestricted access to the other organization's private data.

This ownership principle will guide every entity we design.

## 2.3. Identity and organization domain

### User

A `User` represents an individual who can authenticate with the platform. Its identity data may include a unique identifier, email address, authentication-related information, account status, and timestamps.

A user is not the same thing as an organization. One developer might manage ShopSphere's API configurations in one workspace and analyze DataPilot's consumption in another. Creating a separate user account for every organization would unnecessarily duplicate identities.

The user entity therefore represents the person, not the organization they currently work in. In the database, the user's email should have an appropriate uniqueness constraint, and authentication secrets must never be stored in plaintext.

### Tenant or Organization

A `Tenant` represents an organization or workspace that uses our SaaS platform. It contains organizational information such as a name, unique slug, status, and creation timestamp.

Every customer-owned resource must be associated with an organization. This includes upstream services, products, applications, routes and relevant analytics data. A tenant is therefore a central security boundary as well as a business entity.

We will use the terms tenant and organization interchangeably in our system design, but the Java code should adopt one consistent name. I recommend `Tenant` in the database and domain model, because it communicates the multi-tenant architecture clearly.

### Membership

A `Membership` connects a user to an organization and specifies the user's role in that organization. It exists because a user can belong to many organizations, and an organization can have many users.

For example, a user might be an `ADMIN` in ShopSphere and a `DEVELOPER` in DataPilot. Their permissions must be determined by the membership associated with the active organization, not by a single global role stored on the user.

Our initial membership roles will be `OWNER`, `ADMIN`, `DEVELOPER` and `ANALYST`. The platform administrator is a separate global authority, not simply another tenant membership role.

When an organization is created, the creating user receives an owner membership. The platform should prevent the last owner from being removed or demoted until another owner has been designated.

Business rule: A user can operate on tenant-owned resources only when they have the required permission within that tenant. Knowing a resource's identifier does not grant access to it.

## 2.4. API publishing domain

Now let's model the resources that a provider organization manages.

### Upstream service

An `UpstreamService` represents an existing backend API that the provider wants to expose through our platform. It contains the service name, owning tenant, destination configuration, environment, operational status, and possibly connection-related settings.

For example, ShopSphere already runs an Inventory backend at `https://inventory.example.com`. Our platform does not need to recreate that backend. It registers the destination so the gateway knows where to forward authorized requests.

The service record represents the backend destination, not an individual HTTP endpoint. A service can support multiple routes.

Destination configuration is security-sensitive. Ordinary consumer requests must never be able to override the upstream URL, and service registration must validate destinations against the platform's outbound-network security policy. Otherwise, the gateway could become a proxy for accessing unintended internal resources.

### API route

An `ApiRoute` represents the mapping between an incoming gateway request and a request to an upstream service. It defines the public path, HTTP method, destination service, upstream path, and relevant runtime policies.

For example, the public route `/v1/inventory/items` with method `GET` could map to `/api/items` on ShopSphere's Inventory service. The gateway resolves that mapping and forwards requests when the applicable access checks succeed.

A route belongs to a product in our initial domain model and references an upstream service. One service can therefore serve many routes, and one product can expose many routes. To keep the MVP straightforward, each route belongs to one product at a time. If later we need to reuse the same route definition across products, we can introduce a separate product-route association.

A route must be unique within its effective gateway namespace for its HTTP method and environment. Published routes must reference valid upstream services, and the gateway must reject requests to unpublished or disabled routes.

### API product

An `ApiProduct` is the offering that a provider publishes for other organizations to consume. It groups the routes that belong to that offering and contains information such as name, slug, description, visibility, lifecycle status, and subscription-approval policy.

Think of a product as the contract offered to external developers, rather than the backend server itself.

For example, ShopSphere could publish an `Inventory API` product containing operations to retrieve items and check stock availability. The underlying Inventory service remains the upstream implementation, while the product defines the offering that consumers discover and subscribe to.

A product must belong to one provider tenant. Its routes must reference services owned by that same tenant, and it must have valid published routes and at least one usable plan before it can be published.

### Subscription plan

A `Plan` defines the access conditions associated with an API product. It includes a name, request-rate limit, optional periodic quota, plan status, and timestamps.

For our initial version, the Starter plan could permit 60 requests per minute and a monthly quota of 10,000 requests. These are illustrative limits, not fixed technical requirements.

A plan belongs to exactly one API product. This keeps the relationship easy to understand: the provider publishes a product and defines the access plans available for that product.

The plan's configuration must be validated when created or updated. Rate-limit values must be positive, and any quota window must be explicitly defined. We should record and audit policy changes, because changing a published plan can change how existing consumer traffic is handled.

For the MVP, plan policy changes can affect active subscriptions, provided that the change is audited and propagated to the gateway. Versioned plan revisions can be introduced later if we need stronger guarantees around preserving the precise terms accepted by existing subscribers.

## 2.5. API consumption domain

The provider-side entities describe what is available. We now need to model the consumer-side entities that allow an application to discover and use those offerings.

### Developer application

A `DeveloperApplication` represents a software application registered by a consumer organization. It might be a mobile app's backend, an internal integration service, or a scheduled synchronization job.

For example, DataPilot could register an application named `inventory-sync-service` to consume ShopSphere's Inventory API. The application belongs to DataPilot, not ShopSphere, even though it calls ShopSphere's API.

An application holds its identity, owning consumer tenant, display name, status, and creation metadata. It can have multiple credentials and multiple subscriptions, allowing it to consume more than one API product.

An application is different from a user. A user manages the application through the dashboard, but the software application itself makes API requests using its credentials.

### Subscription

A `Subscription` connects a consumer application to a provider's API product under a specific plan. This entity represents the provider's authorization to let that application consume the offering.

Suppose DataPilot subscribes `inventory-sync-service` to ShopSphere's Starter plan. The subscription records the application, product, selected plan, status, approval information, and relevant timestamps.

We should retain both the provider tenant and the consumer tenant as identifiable parties in the subscription. They can be derived through the product and application relationships, but storing the relationship context can simplify authorization checks and analytics queries if database constraints keep it consistent.

For the MVP, one application should have at most one pending or active subscription to a given product. A consumer that wants a different plan must use an explicit plan-change workflow rather than accidentally creating overlapping active subscriptions.

A subscription belongs to one product and a plan offered by that product. The platform must verify this relationship before activation, rather than trusting product and plan identifiers supplied by the client.

### API credential

An `ApiCredential` represents a secret used by an application to identify itself to the gateway. We will use API keys for the first version, although the model could be extended later to support other credential types.

A credential belongs to a developer application rather than directly to one subscription. This is a useful decision because the same application may consume multiple products. When a request arrives, the gateway identifies the application from its credential and then determines whether that application has an active subscription authorizing the requested product and route.

A credential stores a public lookup identifier or prefix, a cryptographic hash of the secret, its status, creation and expiry timestamps, and the last-used timestamp. The full secret is generated securely and displayed only at creation time. It must not be recoverable from the database or appear in application logs.

The important business rule is that a valid key identifies the application, but a valid key alone does not authorize access to every API on the platform. The gateway must also evaluate the relevant subscription, product, route and rate-limit policy.

Credential rotation and revocation must be supported. Revoking a key blocks requests using that key without requiring the application itself to be deleted.

## 2.6. Operational and governance entities

Not every important entity represents something a user directly manages. Some exist to make the platform secure, observable and operationally reliable.

### Usage event

A `UsageEvent` captures the outcome of a gateway request. Its data includes the request identifier, timestamp, route, application, subscription or product context, HTTP status code, and latency.

The event should identify both the provider and consumer organizations so each can view the appropriate side of API usage. ShopSphere can analyze traffic against its product, while DataPilot can analyze consumption by its application. Neither party should gain unrestricted access to the other's private configuration.

Usage events should not contain plaintext API keys, authorization headers, or sensitive request bodies. The gateway can emit these events asynchronously so routine analytics processing does not introduce a database write into the critical request path.

### Audit log

An `AuditLog` records security-sensitive management actions, such as creating a route, publishing a product, issuing a credential, approving a subscription or changing a rate limit.

It should identify the actor, organization context, action, target resource, timestamp and outcome. Audit records should be append-only from the perspective of ordinary tenant users.

An audit log answers the question, "Who changed the system, and what did they change?" A usage event answers, "What happened when an API request was processed?" Keeping these concerns separate makes both easier to query and protect.

### Gateway configuration version

A `GatewayConfigVersion` represents a version of the effective configuration that the gateway can load. It provides a way to distinguish saved management data from the configuration currently active in the data plane.

For example, when a route is published or a subscription is revoked, the control plane creates a new configuration version. Gateway instances periodically retrieve the latest version, validate it, and apply the update.

For an initial implementation, this can be a version number and a generated configuration snapshot. The gateway should continue using its last-known-good configuration when it receives an invalid update, rather than replacing working configuration with a broken one.

This entity is primarily an operational mechanism, rather than a core customer-facing domain object. More sophisticated configuration rollout, history and rollback can follow later.

## 2.7. The complete domain relationship model

The following diagram brings these entities together. It represents the proposed logical relationships, not yet the physical PostgreSQL schema.

`diagram`

Notice that the model contains two different paths from an organization to API-related resources. The provider tenant owns upstream services, products, plans and routes. The consumer tenant owns developer applications and their credentials. A subscription connects the two sides.

This is the most important relationship in the entire domain model. It lets us enforce access without making consumers the owners of APIs they merely use.

The diagram also omits some implementation details deliberately. For example, membership has a role attribute; routes have an HTTP method and public path; subscriptions have a lifecycle status. The next stage will turn those concepts into explicit columns, keys and constraints.

## 2.8. Business rules that must always remain true

Entities define what exists. Business rules define which states and relationships are allowed. These rules should be enforced in the backend and, where practical, reinforced with database constraints.

### Tenant ownership and authorization

Every organization-owned resource must have a verifiable ownership relationship. A route must reference a service owned by the same provider tenant as its product. A product must not contain routes from another provider's workspace.

Every management operation must verify the actor's membership, role and permission in the relevant tenant. It is not enough to filter data only on the frontend or trust a `tenantId` supplied by the caller. The backend must establish the tenant context from authenticated identity and authorized membership.

The same principle applies to analytics. A consumer can inspect usage associated with its own applications, while a provider can inspect usage against its own products. Neither party should be able to query the other's private records by changing an identifier.

### Publishing and route validity

A product can be published only when its configuration is valid. It must have at least one usable route and an available plan, every route must reference an eligible upstream service, and conflicting public route mappings must be rejected.

In the MVP, we will keep route-to-product ownership straightforward: each route definition belongs to a single product. If the same upstream operation needs to be exposed in a second product, we can create a separate route mapping to that upstream operation. This avoids ambiguous authorization and rate-limit selection when a request matches several products.

The gateway should only serve routes from successfully published configuration. Saving a draft must not make a route accessible to consumers.

### Subscription authorization

A consumer application can subscribe only to a published product and a plan belonging to that product. The platform must validate these relationships at the time of subscription approval and at runtime.

Pending, rejected, revoked and expired subscriptions do not authorize API requests. Only active subscriptions are eligible, and the requested route must belong to the subscribed product.

A revoked subscription must stop granting access within the configuration propagation interval established in Phase 1. We will use five seconds as the initial design target and test it rather than assume it works. For strict revocation guarantees, we will need a carefully designed invalidation or live-state-check mechanism rather than relying blindly on long-lived gateway caches.

### Rate limiting and quota enforcement

Traffic limits must be associated with the application and the relevant product or plan, rather than solely with an individual API key. Otherwise, consumers could bypass a limit simply by generating multiple keys.

For example, if a plan permits 60 requests per minute, rotating the application's credential should not create a fresh allowance for the same application and product. Redis must maintain shared counters across gateway instances, and counter updates must be atomic.

We must also define what happens if Redis is unavailable. For the initial implementation, when a mandatory rate-limit policy cannot be evaluated, the gateway will return a controlled service-unavailable response rather than silently permit unlimited traffic.

### Credential security

A credential must be uniquely identifiable, stored securely, revocable, and linked to an application. The database should contain the secret hash rather than the plaintext secret, and authentication failures should not reveal whether an unrelated tenant's credential exists.

Credential validity and subscription validity are separate checks. Revoking a credential blocks that credential, whereas revoking a subscription blocks access to the subscribed product even if the application holds another valid credential.

### Configuration and operational data

Only validated configuration should become active in the gateway. Publishing, disabling, or revoking a resource must create the appropriate configuration update and audit entry.

Usage-event processing should be safe against duplicate delivery. If the event pipeline retries a message, it should not accidentally count the same request twice in billing-related or quota-related aggregates. Exact quota enforcement may need synchronous counters or reservations; asynchronous analytics events alone are not sufficient to enforce a strict quota.

These invariants form the behavioral contract of the system. They are more important than any particular Java class or database framework.

## 2.9. Entity lifecycle and state transitions

The entities in our model do not remain in one state forever. Explicit lifecycle states make it easier to validate operations and prevent impossible transitions.

API product lifecycle

Draft

Published

Archived

Draft products are not consumer-accessible. Publishing requires valid configuration, and archiving prevents new subscriptions and new access according to the defined retirement policy.

Subscription lifecycle

Pending

Approve

Reject

Active

Rejected

Revocation or expiry of an active subscription

Revoked / Expired

Credential lifecycle

Active

Revoked or Expired

These states should be represented explicitly, preferably through Java enums and database constraints where appropriate. The service layer should control allowed transitions rather than letting API clients assign arbitrary state strings.

For example, a client should not be able to activate its own pending subscription merely by sending `"status": "ACTIVE"`. Approval must go through an authorized provider operation, which validates the product, plan and application before changing the state.

We should also preserve historical subscriptions when access is revoked. Deleting the record would make it harder to explain past usage, audit decisions, and diagnose authorization failures.

## 2.10. Translating this model into database design

Our domain model suggests a first version of the relational schema. The following table summarizes the principal tables and their responsibilities.

| Table                     | Principal responsibility                      |
| ------------------------- | --------------------------------------------- |
| `users`                   | Platform identities                           |
| `tenants`                 | Organizations and workspaces                  |
| `memberships`             | User-to-tenant relationships and tenant roles |
| `upstream_services`       | Provider-owned backend destinations           |
| `api_products`            | Provider-owned API offerings                  |
| `api_routes`              | Public-to-upstream route mappings             |
| `api_plans`               | Product access policies and limits            |
| `developer_applications`  | Consumer-owned software applications          |
| `api_credentials`         | Credential metadata and secret hashes         |
| `subscriptions`           | Application, product and plan authorization   |
| `usage_events`            | Gateway request outcomes                      |
| `audit_logs`              | Security-sensitive management history         |
| `gateway_config_versions` | Published runtime configuration snapshots     |

These tables will use generated primary keys and appropriate foreign keys. Tenant-owned resources will have explicit tenant ownership, and the database will enforce uniqueness rules where possible.

One important caveat is that cross-row business rules do not automatically become database constraints just because foreign keys exist. For instance, the fact that an API plan belongs to the same product referenced by a subscription must be validated explicitly or reinforced with a suitable composite constraint. We'll decide which rules belong in database constraints, transactions, or service logic when we design the schema.

We should avoid creating a separate microservice or database for every entity. These are domain entities inside the control plane, not a recommendation to build a dozen independent services. For our MVP, they can live in one Spring Boot modular monolith, while the gateway remains a separate application.

## 2.11. What we have decided in Phase 2

We have established the core entities and their ownership relationships, distinguished providers from consumers, and identified the rules governing publishing, subscriptions, credentials, tenant isolation and runtime access. We have also made several simplifying decisions for the MVP: routes belong to one product, credentials belong to applications, and a subscription connects an application to a product and one of its plans.

These choices give the system a coherent model without prematurely adding billing, custom policy scripting, or complex shared-route behavior.

The next step is Phase 3: High-Level Architecture. We will use this domain model to define the boundaries between the Spring Boot control plane, Spring Cloud Gateway data plane, PostgreSQL, Redis and the usage-event pipeline. We will then show how those components communicate during API registration, product publishing, subscription approval and live request processing.

Phase 3: High-Level Architecture
# Phase 3 — High-Level Architecture

System Design Document · Version 1.0 · Multi-Tenant SaaS API Management Platform

In Phase 2, we identified the entities and business rules of our platform. In this phase, we decide how the software components collaborate to enforce those rules.

The central architectural decision is to separate API management from API request processing. Organizations should be able to configure and manage their APIs without the management backend becoming a bottleneck for every incoming request.

We will use a Java-based architecture with a Spring Boot modular monolith for the control plane, a separate Spring Cloud Gateway application for the data plane, PostgreSQL for persistent records, and Redis for distributed rate limiting. Usage analytics will be processed asynchronously.

## 3.1. Architectural style

Our architecture combines three patterns, each serving a different purpose.

The control plane will use a modular monolith. This means one deployable Spring Boot application contains distinct modules for identity, tenants, API registration, products, subscriptions, credentials, configuration management and auditing. These modules have clearly defined responsibilities but do not require independent network communication.

The data plane will be a separate gateway application. It receives external API traffic, enforces the published configuration and forwards authorized requests to upstream services. Separating it allows the request-processing workload to scale independently from administrative operations.

Analytics will use asynchronous event processing. Gateway requests generate usage events that can be processed by a background worker, keeping analytics persistence away from the critical request path.

These patterns give us a useful separation of responsibilities without turning every domain entity into a separate microservice.

## 3.2. The overall architecture

`diagram`

The diagram shows logical components. The management modules belong to one application, even though they are drawn separately to make their responsibilities visible. The gateway is a separate deployable application, and Redis and PostgreSQL are shared infrastructure.

There are two important flows through this architecture. The management flow changes the state of the platform, such as registering a route or approving a subscription. The runtime flow handles API traffic using the current published configuration. Understanding the difference between these flows explains almost every major architectural decision that follows.

## 3.3. Control plane: managing the platform

The control plane is the management side of our product. It exposes REST endpoints to the tenant dashboard and developer portal, authenticates users, checks permissions and applies the domain rules from Phase 2.

Suppose ShopSphere wants to publish an Inventory API. Its administrator submits an API registration request. The control plane verifies that the user has appropriate privileges in ShopSphere's organization, validates the proposed upstream destination, saves the service configuration, and allows the administrator to define routes and a product.

Later, when DataPilot requests a subscription, the control plane checks the application, product and selected plan. If approval is required, the subscription remains pending until an authorized provider administrator approves it. The control plane then records the updated subscription state and prepares a new runtime configuration.

The control plane will contain the following logical modules.

| Module                  | Responsibility                                                 |
| ----------------------- | -------------------------------------------------------------- |
| Identity and tenant     | Authentication, users, memberships and organization roles      |
| API catalog             | Upstream services, routes, environments and product publishing |
| Subscription management | Products, plans, applications and subscription lifecycle       |
| Credential management   | API-key generation, storage, rotation and revocation           |
| Gateway configuration   | Publishing validated, versioned runtime configuration          |
| Audit                   | Recording security-sensitive administrative actions            |
| Usage and analytics     | Querying usage data and calculating aggregates                 |

These modules should communicate through clear internal interfaces and application services rather than calling one another's repositories indiscriminately. For example, subscription approval should go through the subscription service, which validates the product and plan, updates the state in a transaction, creates an audit record, and initiates configuration publication.

We will not create a separate Spring Boot application for each row. Their purpose is to establish code ownership and keep business logic organized as the project grows.

## 3.4. Data plane: processing API requests

The data plane consists of the API gateway. Its responsibility is to execute the configuration published by the control plane and handle requests quickly and predictably.

When DataPilot sends a request, the gateway resolves its route, checks the credential, verifies that the application has the necessary subscription and route permissions, checks its rate limit, and forwards the request to the upstream service. Once the upstream responds, the gateway returns the response and emits a usage event.

The gateway uses Spring Cloud Gateway, which provides a foundation for request routing and gateway filters. We will add the platform-specific behavior through custom filters or equivalent gateway components.

The gateway must not call the management backend for every API request. Doing so would introduce unnecessary latency and couple the availability of consumer traffic to the availability of administrative operations. Instead, it will use an in-memory representation of validated runtime configuration, refreshed when changes are published.

Redis is used for shared, distributed rate-limit counters, while PostgreSQL remains the durable source of management records. The gateway should not issue ordinary database queries directly for each request.

This division gives us an important operational advantage: the dashboard can be temporarily unavailable while the gateway continues serving requests using its last-known-good configuration, subject to the freshness and revocation safeguards we define later.

## 3.5. Configuration distribution: how the gateway learns about changes

The control plane and gateway cannot simply share Java objects because they run as separate applications. We need an explicit mechanism that transfers API definitions, policies and authorization state from the management side to the runtime side.

For our MVP, I recommend versioned configuration snapshots with periodic polling. This is easier to implement and debug than introducing a sophisticated distributed configuration system at the beginning.

When an administrator publishes an API or changes a subscription, the control plane validates the change and persists the updated records. It then creates a new configuration version that the gateway can retrieve through an internal configuration endpoint. Each snapshot contains the information necessary to evaluate routes, credentials, subscriptions and associated access policies. It contains credential hashes, never plaintext API secrets.

The gateway periodically checks whether a newer configuration version is available. If a new version exists, it downloads the snapshot, validates it and atomically replaces its active in-memory configuration. If the snapshot is invalid, the gateway keeps its previous valid version and reports the configuration error.

`daigram`

We should distinguish between persisting a management change and activating it at the gateway. The two actions are related, but they do not happen in one database transaction across both applications. The control plane should report configuration publication status, and the gateway should report which version it has activated.

For a provisional configuration propagation target of five seconds, we could poll every two seconds and measure the end-to-end delay. Polling interval alone does not guarantee the target; processing time, network failures and retries also matter.

The snapshot approach is deliberately simple. A large production deployment might instead publish configuration-change events, use a transactional outbox to avoid missing notifications, and push updates to gateway instances. Even in that design, a version check or periodic reconciliation remains useful for recovering from missed events.

One security detail is especially important: route configuration and credential metadata must travel over an authenticated internal channel. They must not be exposed through public management endpoints or returned to ordinary dashboard users.

## 3.6. The gateway request path

Now we can describe what happens when a consumer actually makes a request. This is the most performance-sensitive part of the system because it runs for every incoming API call.

`daigram`

The gateway first matches the request against the active route configuration using the configured host, path and HTTP method. A missing or unpublished route is rejected rather than forwarded to an arbitrary destination.

Next, the gateway identifies the application from the presented credential. The credential's secret is verified against its stored hash, and its status is checked. A valid credential identifies an application, but it does not automatically grant access to every product.

The gateway then checks the relevant subscription and confirms that the requested route is covered by the subscribed product. For example, DataPilot may be authorized for ShopSphere's Inventory API but not for its internal administrative API.

Only after those checks does the gateway evaluate rate limits in Redis. If access is permitted, it forwards the request to the registered upstream service, subject to timeouts and other proxy safeguards. Finally, it returns the response and generates a usage event with appropriate metadata.

The response codes shown in the diagram represent the normal cases. Invalid credentials commonly result in `401 Unauthorized`, insufficient permissions in `403 Forbidden`, missing routes in `404 Not Found`, and exceeded rate limits in `429 Too Many Requests`. An upstream timeout or unavailable service would typically result in a gateway error such as `504 Gateway Timeout` or `502 Bad Gateway`, respectively.

Notice that the route lookup, credential verification and subscription evaluation use published runtime state; they do not require separate PostgreSQL queries during every request. Redis is contacted for the shared rate-limit decision because that state must remain consistent across gateway instances.

## 3.7. What happens inside Redis?

Redis serves a distinct purpose from PostgreSQL. PostgreSQL stores durable records such as tenants, API products, plans, subscriptions and credential metadata. Redis provides fast shared state needed for request-time coordination.

Consider a Starter plan that permits 60 requests per minute. If ShopSphere's API is served by three gateway instances, an application must not receive 60 requests per minute on each instance. Its effective limit is 60 across the relevant application and product scope.

We can implement this with atomic Redis operations, such as a Lua script that checks and updates the relevant counter as one operation. A key can incorporate the application identifier, product identifier and time window, depending on the policy. The exact counter strategy, handling of window boundaries and quota semantics will be detailed in the security and scalability phase.

Redis might also hold other short-lived state, but we should not use it as the sole durable source of tenant ownership or subscription history. A cache entry expiring or Redis restarting must not erase the canonical record of which application belongs to which tenant.

For our first version, rate limiting is the principal reason to introduce Redis. We can add other caching only when a specific request path benefits from it.

## 3.8. Usage events and asynchronous analytics

The gateway must record request outcomes, but writing detailed analytics synchronously to PostgreSQL for every request would add unnecessary database work to the request path.

Instead, the gateway emits a usage event after processing the request. A background consumer processes that event and persists the usage record or updates suitable aggregates. The dashboard queries those persisted records to calculate request volume, status-code distribution, latency percentiles and consumption by application or product.

A usage event might contain the following logical fields:

```
{
  "eventId": "evt_123",
  "requestId": "req_456",
  "providerTenantId": "tenant_shop",
  "consumerTenantId": "tenant_data",
  "applicationId": "app_789",
  "productId": "product_inventory",
  "routeId": "route_items",
  "statusCode": 200,
  "latencyMs": 42,
  "timestamp": "2026-10-12T10:30:00Z"
}
```

These values are illustrative; the final event schema will be decided when we design the database and event contracts. The example deliberately contains no API secret or authorization header.

For a reliable asynchronous pipeline, I recommend RabbitMQ when we implement this stage. It provides a manageable queue-and-consumer model without requiring us to introduce a distributed streaming platform for the MVP. We can add the broker after the core routing and authorization flow is functional.

Events must have unique identifiers or another idempotency mechanism so that retries do not count the same request twice. We should also decide how the system behaves when the broker is unavailable. The gateway should not block indefinitely while trying to send analytics, but an unbounded in-memory queue can exhaust memory under load. A production-oriented design therefore needs bounded buffering, delivery monitoring and a documented loss-versus-availability policy.

There is another important distinction: asynchronous usage events are appropriate for analytics, but they must not be the sole mechanism for strict synchronous quota enforcement. The gateway must make the rate-limit decision using the relevant shared counter before forwarding the request.

## 3.9. Deployment architecture

We have defined the logical architecture. Now we need to decide how it runs during development.

For the MVP, Docker Compose can run the control plane, gateway, PostgreSQL and Redis together. RabbitMQ and a usage worker can be added when we implement the analytics pipeline. Running the system locally in containers gives us a repeatable environment and allows us to test the same network boundaries the applications will use when deployed.

`diagram`

The port numbers shown are proposed local development ports, not permanent public endpoints. We should expose only the ports needed by developers and test clients. PostgreSQL and Redis should normally remain on the internal container network, rather than being accessible to external callers.

The control plane should be the only application that writes management records. The gateway obtains runtime configuration through an authenticated internal endpoint and uses its own in-memory state for request processing. It does not need unrestricted access to the management database.

The mock upstream API is a test service, not part of the SaaS control plane. It lets us validate routing, authorization and error handling without depending on a real external provider's availability.

When we deploy to a hosted environment, we can run multiple instances of the control plane and gateway behind suitable load balancers. We do not need that deployment complexity to validate the architecture locally.

## 3.10. Security boundaries and failure behavior

A sound architecture must specify not only how requests succeed but also what happens when a component fails or a client behaves maliciously.

### Management security

The management API uses Spring Security to authenticate users and authorize actions based on their membership and role in the requested tenant. Its administrative endpoints must never treat an arbitrary organization ID supplied by the browser as proof of membership.

The platform administrator's global privileges should be explicitly separated from ordinary tenant permissions. Sensitive changes, including credential creation and revocation, route publication and subscription approval, should produce audit records.

### Gateway security

The gateway validates consumer credentials and subscription access using its published runtime state. It must reject unknown or disabled routes, protect internal configuration endpoints, apply timeouts and remove or control sensitive forwarding headers.

Upstream destination validation is essential. A tenant must not be able to configure the gateway to reach arbitrary internal addresses or cloud metadata endpoints. Destination validation, DNS handling and restrictions on redirects require deliberate implementation, rather than merely checking that a URL has a valid format.

The system must also establish trusted organization and application context itself. It should not trust caller-provided headers such as `X-Tenant-ID` as authorization evidence. Similarly, the gateway should not expose one provider's upstream credentials to a consumer.

### What happens when infrastructure fails?

| Failure                          | Expected behavior                                                                                                                                                                                                |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| PostgreSQL becomes unavailable   | Management operations requiring database writes fail safely. The gateway can continue with its last-known-good configuration while its freshness policy permits it.                                              |
| Redis becomes unavailable        | For mandatory rate limiting, the gateway rejects affected requests with a controlled `503 Service Unavailable` instead of silently bypassing the limit.                                                          |
| Upstream service times out       | The gateway enforces a timeout and returns an appropriate gateway error.                                                                                                                                         |
| Configuration update is invalid  | The gateway retains its previous valid snapshot and reports the failed update.                                                                                                                                   |
| Usage broker becomes unavailable | The gateway does not wait indefinitely for analytics. Buffering and retry policies must be bounded and observable.                                                                                               |
| Subscription is revoked          | The control plane publishes updated authorization state; the gateway stops accepting subsequent requests under the revoked subscription within the defined propagation target under normal operating conditions. |

The revocation target deserves particular attention. A cached snapshot cannot learn about a new revocation when the configuration source is unreachable. Therefore, a five-second propagation target can be verified under normal operation, but it cannot be guaranteed during every network or infrastructure failure by polling alone.

We should measure configuration age, raise an alert when updates stop arriving and define when a gateway must stop trusting excessively stale authorization state. If we later need stronger revocation guarantees, we can use a fast invalidation channel or shared authorization-state check, with the additional runtime cost that entails.

## 3.11. Important architectural decisions

These decisions should be written into a small architecture decision record (ADR) file so future code changes do not silently undermine the system design.

| Decision                               | Rationale                                                                                               |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| Modular monolith for the control plane | Keeps business logic in one deployable application while preserving clear module boundaries.            |
| Separate API gateway application       | Lets us scale and optimize the request-processing path independently from management operations.        |
| PostgreSQL as the source of truth      | Provides durable relational storage for tenant ownership, subscriptions and management records.         |
| Versioned runtime configuration        | Allows the gateway to execute published state without querying the management backend on every request. |
| Redis for distributed rate limiting    | Allows multiple gateway instances to share counters for the same application and product.               |
| Asynchronous usage processing          | Separates analytics persistence from the critical request path.                                         |
| Shared multi-tenant database           | Keeps the MVP manageable while requiring explicit authorization and tenant isolation at every layer.    |

We should also establish an important code-level boundary: the control plane owns the authoritative business state, while the gateway owns request-time enforcement. Both must respect the same authorization model, but they should not duplicate the entire management domain or allow the gateway to modify subscriptions directly.

## 3.12. Implementation implications for Java

The architecture suggests two Java applications rather than many microservices.

The control-plane application will use Spring Boot, Spring Security, Spring MVC, Spring Data JPA and Flyway. Its modules will include identity and tenants, API catalog, subscriptions, credentials, gateway configuration, audit and usage queries. Each module can have its own controllers or API interfaces, application services, domain objects and persistence components where needed.

The gateway application will use Spring Cloud Gateway and Spring WebFlux. Its runtime components will handle route configuration, credential verification, subscription-policy evaluation, Redis-backed rate limiting, upstream proxying and usage-event production.

PostgreSQL will persist durable records. Redis will coordinate rate-limit decisions across gateway instances. RabbitMQ and the usage worker can be introduced at the analytics implementation milestone.

We should avoid using blocking JPA operations directly inside the gateway's reactive request pipeline. The control plane can use conventional synchronous database access, while the gateway should use reactive-compatible operations and keep blocking work out of event-loop threads.

This split is a useful learning boundary as well: you will build familiar Spring Boot business logic on one side and a reactive request-processing pipeline on the other.

## 3.13. What we have established in Phase 3

We now have a logical architecture in which the control plane manages organizations and API offerings, the gateway enforces published configuration, PostgreSQL stores authoritative records, Redis coordinates distributed rate limits, and an asynchronous pipeline handles usage analytics.

We have also defined how management changes reach the gateway, how the runtime request path works, and how the system should respond to common failures.

The next stage is Phase 4: Database Design and Tenant Isolation. We will convert the domain model into an actual ER diagram, define columns and relationships for the PostgreSQL schema, establish foreign keys and uniqueness constraints, decide how to represent tenant ownership, and identify which isolation rules belong in the database and which belong in Spring Security and service-layer authorization.

That schema will become the foundation for our Flyway migrations and the first Java implementation.

Phase 4: Database Design and Tenant Isolation.
# Phase 4 — Database Design and Tenant Isolation

System Design Document · Version 1.0 · Multi-Tenant SaaS API Management Platform

In Phase 2, we established the domain entities and their relationships. In Phase 3, we decided that a Spring Boot control plane would manage the platform while a separate Spring Cloud Gateway application would process API traffic.

Now we need to translate those decisions into a database design that is consistent, secure and practical to implement.

The most important principle in this phase is that tenant isolation must be enforced by the data model and the backend, not just by the user interface. A user from ShopSphere must not be able to access FinTrack's resources by guessing their IDs, changing a URL parameter or manipulating a request body.

## 4.1. Database architecture and isolation strategy

For the MVP, I recommend a shared PostgreSQL database with shared tables. Every organization uses the same table structures, but every organization-owned record is associated with a tenant.

For example, the `api_products` table might contain products from hundreds of organizations. Each row has a `tenant_id` that identifies its provider organization. When ShopSphere requests its products, the application retrieves only products authorized for ShopSphere.

This is a practical starting point because it avoids maintaining a separate database and schema for every customer. It also allows us to use foreign keys, transactions, indexes and database constraints to enforce important relationships.

PostgreSQL

One database · Shared tables

ShopSphere

Tenant ID: `tenant_shop`

Its services, routes, products and applications are associated with its authorized tenant context.

FinTrack

Tenant ID: `tenant_fin`

Its resources share the tables but remain isolated by ownership and access policies.

Illustrative identifiers; actual database IDs will be UUIDs.

Our isolation strategy has three layers. First, Spring Security and the service layer authenticate users, resolve their organization memberships and authorize every management operation. Second, database constraints ensure that related records cannot accidentally connect resources owned by different tenants. Third, PostgreSQL Row-Level Security (RLS) can provide an additional database-level restriction on which rows the application can access. PostgreSQL supports policies for controlling row visibility and permitted modifications, with a default-deny behavior when RLS is enabled but no applicable policy exists.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://www.postgresql.org\&sz=32)

PostgreSQL+1



These layers have different responsibilities. Foreign keys protect data integrity, authorization protects business operations, and RLS provides an additional isolation boundary. None should be treated as a substitute for the others.

## 4.2. Primary keys, data types and conventions

We should establish consistent database conventions before defining individual tables.

Every main entity will use a UUID primary key, generated by PostgreSQL or the application. UUIDs are convenient across the control plane, gateway configuration and usage-event pipeline, although UUIDs do not themselves provide authorization or guarantee secrecy.

All timestamps will use PostgreSQL's `TIMESTAMPTZ` type and be stored and handled in UTC. Statuses such as `ACTIVE`, `REVOKED` and `PUBLISHED` will initially use strings with database `CHECK` constraints, keeping changes to the domain lifecycle manageable as the application evolves.

Tenant-owned tables will include a `tenant_id` column, usually a foreign key to `tenants.id`. Important columns will be `NOT NULL` unless the domain explicitly permits an absent value. We will use unique constraints and indexes for real business requirements, rather than relying solely on service-layer checks. PostgreSQL's constraints and foreign keys provide database-enforced consistency, while indexes support uniqueness and efficient lookups.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://www.postgresql.org\&sz=32)

PostgreSQL



We will also use `created_at` and `updated_at` on mutable management entities. Records that have historical or security significance, such as subscriptions, credentials, usage events and audit logs, should generally be deactivated, revoked or retained rather than physically deleted.

## 4.3. The complete ER diagram

The following diagram represents the planned relational model. It focuses on the entities, foreign-key relationships and ownership boundaries; the full set of columns and constraints follows in the next sections.

`diagram`

The core ownership relationships are straightforward: a tenant owns upstream services and API products, while consumer tenants own developer applications and credentials. A product exposes routes and offers plans. A subscription connects an application owned by one tenant to a product owned by another tenant.

This cross-tenant subscription relationship needs special treatment. We cannot require the provider and consumer tenant IDs to be equal, because they are supposed to differ. We must instead enforce that the application belongs to the consumer tenant and the product and plan belong to the provider tenant.

The diagram is a logical overview. Our implementation will also include operational tables for gateway configuration snapshots and indexes for efficient request-time and analytics queries.

## 4.4. Identity and tenant tables

The first group of tables establishes who is using the platform and which organization they can operate in.

| Table         | Important columns                                    | Primary constraints                                                             |
| ------------- | ---------------------------------------------------- | ------------------------------------------------------------------------------- |
| `users`       | `id`, `email`, `password_hash`, `status`, timestamps | Primary key; case-insensitive email uniqueness                                  |
| `tenants`     | `id`, `name`, `slug`, `status`, timestamps           | Primary key; globally unique slug                                               |
| `memberships` | `tenant_id`, `user_id`, `role`, `status`, timestamps | Composite primary key `(tenant_id, user_id)`; foreign keys to tenants and users |

The `users` table represents people, while the `tenants` table represents organizations. The `memberships` table connects them. This many-to-many relationship is important because one user may work with several organizations, holding a different role in each one.

The `memberships` table should have a composite primary key on `(tenant_id, user_id)` so that the same user cannot accidentally acquire duplicate memberships in a single organization. Roles should be constrained to the supported values, and inactive memberships should be retained rather than deleted when an employee leaves an organization.

Email uniqueness should be case-insensitive. One implementation is a unique index on `lower(email)` rather than assuming that `Alice@example.com` and `alice@example.com` are different people.

The tenant slug serves a different purpose from the UUID. It provides a readable name for URLs and gateway route namespaces, while the UUID is the stable internal identifier used in foreign keys.

## 4.5. API catalog tables

The next group describes what providers register and publish.

| Table               | Important columns                                                                                                              | Primary constraints                                                                    |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------- |
| `upstream_services` | `id`, `tenant_id`, `name`, `environment`, `base_url`, `status`                                                                 | Tenant foreign key; composite tenant-aware key; unique name per tenant and environment |
| `api_products`      | `id`, `tenant_id`, `slug`, `name`, `description`, `status`, `approval_required`                                                | Tenant foreign key; unique slug per tenant                                             |
| `api_plans`         | `id`, `tenant_id`, `product_id`, `name`, `rate_limit_per_minute`, `monthly_quota`, `status`                                    | Product ownership foreign key; positive limit constraints                              |
| `api_routes`        | `id`, `tenant_id`, `product_id`, `upstream_service_id`, `environment`, `http_method`, `public_path`, `upstream_path`, `status` | Tenant-aware foreign keys; unique public route mapping                                 |

A route must reference an upstream service and an API product belonging to the same tenant. This requirement can be enforced with composite foreign keys rather than trusting application code alone.

We will also establish a gateway namespace rule. Because all tenants initially share one public gateway hostname, the same HTTP method and public path cannot be registered twice in the same environment. A route could therefore be published as `/shopsphere/inventory/v1/items`, while another tenant uses `/fintrack/payments/v1/transactions`.

For the MVP, exact public paths are sufficient. More complex wildcard patterns and overlapping route priorities can be introduced later, because they require more sophisticated conflict detection.

A plan must reference the product that offers it. The plan's rate limit must be positive, and an optional quota must be positive with a clearly defined time window. A product's publication state and a route's activation state must also be distinguished: saving a draft does not automatically make it available to consumers.

## 4.6. Consumer and subscription tables

The consumption side has three main tables, plus their relationships to the provider catalog.

| Table                    | Important columns                                                                                                                        | Primary constraints                                                                            |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `developer_applications` | `id`, `tenant_id`, `name`, `status`, timestamps                                                                                          | Tenant foreign key; application identity unique within the tenant                              |
| `api_credentials`        | `id`, `tenant_id`, `application_id`, `key_prefix`, `secret_hash`, `status`, `expires_at`, `revoked_at`                                   | Application ownership foreign key; globally unique lookup prefix                               |
| `subscriptions`          | `id`, `provider_tenant_id`, `consumer_tenant_id`, `application_id`, `product_id`, `plan_id`, `status`, approval and lifecycle timestamps | Tenant-aware foreign keys to the consumer application, provider product and the product's plan |

The `api_credentials` table must never store the complete, reusable API secret in plaintext. We will generate a high-entropy secret, display it once when issued, and store a cryptographic hash for verification. A public key prefix or identifier allows the gateway to locate the relevant credential record without searching every credential.

Credentials belong to applications rather than directly to one product. That lets the gateway identify the calling application, then determine which subscriptions authorize it to access the requested product. If we eventually want credentials restricted to individual subscriptions, we can add explicit credential scopes or a subscription-specific credential model.

The `subscriptions` table is the most important cross-tenant relationship. It contains the provider tenant, consumer tenant, application, product and selected plan. Those relationships must agree: the application must belong to the consumer tenant, the product must belong to the provider tenant, and the plan must belong to that product.

We should enforce these relationships with composite foreign keys. That way, even if a service accidentally supplies the wrong tenant ID, PostgreSQL can reject the inconsistent relationship instead of storing it.

We must also prevent duplicate active subscriptions for the same application and product. A partial unique index can enforce this while allowing a previously revoked subscription to remain in history.

## 4.7. Operational tables

We need several additional tables for runtime visibility and configuration history.

The `usage_events` table records gateway outcomes. It should contain a unique event identifier for deduplication, request ID, timestamp, provider tenant, consumer tenant, application, subscription, product, route, HTTP status code and latency. It should not contain API secrets or sensitive request payloads. Both sides of the subscription relationship are useful: providers need to analyze traffic to their products, and consumers need to analyze their own applications' usage.

The `audit_logs` table records administrative actions, including who published a product, changed a policy, approved a subscription or revoked a credential. It contains the actor, tenant context, action, target resource, timestamp and carefully redacted metadata. It should preserve historical actions even after a user's membership becomes inactive.

The `gateway_config_versions` table stores versioned runtime configuration snapshots, indexed by environment and version. Because a shared gateway may serve many tenants, each snapshot represents the configuration needed for that gateway environment rather than belonging to just one tenant. A small configuration-state record can point to the latest published version for each environment.

For the MVP, the configuration snapshot can be stored as JSONB, with a version number and content hash. It may contain the route mappings, upstream destinations, active authorization metadata and rate-limit policies needed by the gateway. It must never contain plaintext API secrets or user passwords. The snapshot endpoint itself will be an authenticated internal interface, not a public API.

A single PostgreSQL database can hold all these tables initially. We can introduce a separate analytics database or time-partitioned usage storage later if measured event volume justifies it.

## 4.8. Enforcing tenant isolation with composite foreign keys

Let's examine the most important database-level protection.

Suppose the `upstream_services` table has a UUID primary key and a `tenant_id`. We add an additional unique constraint on `(tenant_id, id)`, allowing another table to reference the service together with its owner.

The `api_routes` table then stores both its tenant and the service it references. Instead of referencing `upstream_services.id` alone, it uses `(tenant_id, upstream_service_id)` to reference `(tenant_id, id)` in the upstream-service table.

The database will reject an attempt to attach a FinTrack route to a ShopSphere service if the tenant IDs do not match. The same principle applies to a route and its API product.

```

-- A service has a globally unique ID, and a tenant-aware key.
ALTER TABLE upstream_services
    ADD CONSTRAINT uq_upstream_services_tenant_id
    UNIQUE (tenant_id, id);

-- A route must reference a service owned by its own tenant.
ALTER TABLE api_routes
    ADD CONSTRAINT fk_api_routes_service_same_tenant
    FOREIGN KEY (tenant_id, upstream_service_id)
    REFERENCES upstream_services (tenant_id, id);

-- A route must reference a product owned by its own tenant.
ALTER TABLE api_routes
    ADD CONSTRAINT fk_api_routes_product_same_tenant
    FOREIGN KEY (tenant_id, product_id)
    REFERENCES api_products (tenant_id, id);

```

This is stronger than defining foreign keys only on `upstream_service_id` and `product_id`. Ordinary foreign keys ensure that the referenced records exist; the composite foreign keys also enforce that their tenant ownership matches.

PostgreSQL requires referenced foreign-key columns to be backed by a primary key, unique constraint or suitable unique index. That is why we explicitly establish the composite unique keys on parent tables.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://www.postgresql.org\&sz=32)

PostgreSQL+1



We should repeat the same approach for an application and its credentials, and for an application and its subscriptions. For subscriptions, however, we must enforce the correct provider and consumer relationships separately, because the two tenants are intentionally different.

## 4.9. Representative PostgreSQL schema

We can now turn the key relationships into SQL. The following is a representative schema for the API catalog and subscription workflow; it illustrates the most important constraints rather than replacing the complete set of versioned migrations we will create during implementation.

```

CREATE TABLE api_products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    slug VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    approval_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_api_products_tenant_id
        UNIQUE (tenant_id, id),

    CONSTRAINT uq_api_products_tenant_slug
        UNIQUE (tenant_id, slug),

    CONSTRAINT chk_api_products_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE api_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    rate_limit_per_minute INTEGER NOT NULL,
    monthly_quota BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_api_plans_product_id
        UNIQUE (tenant_id, product_id, id),

    CONSTRAINT fk_api_plans_product
        FOREIGN KEY (tenant_id, product_id)
        REFERENCES api_products (tenant_id, id),

    CONSTRAINT chk_api_plans_rate_limit
        CHECK (rate_limit_per_minute > 0),

    CONSTRAINT chk_api_plans_quota
        CHECK (monthly_quota IS NULL OR monthly_quota > 0),

    CONSTRAINT chk_api_plans_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE api_routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    upstream_service_id UUID NOT NULL,
    environment VARCHAR(30) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    public_path VARCHAR(500) NOT NULL,
    upstream_path VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_api_routes_tenant_id
        UNIQUE (tenant_id, id),

    CONSTRAINT fk_api_routes_product
        FOREIGN KEY (tenant_id, product_id)
        REFERENCES api_products (tenant_id, id),

    CONSTRAINT fk_api_routes_service
        FOREIGN KEY (tenant_id, upstream_service_id)
        REFERENCES upstream_services (tenant_id, id),

    CONSTRAINT chk_api_routes_method
        CHECK (http_method IN
          ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS')),

    CONSTRAINT chk_api_routes_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED'))
);

-- One shared gateway namespace: public paths must not collide.
CREATE UNIQUE INDEX uq_route_method_environment_path
    ON api_routes (environment, http_method, public_path);

```

There are a few important details in this schema. `api_plans` references the product and its tenant together, which prevents a plan from being attached to another provider's product. `api_routes` follows the same pattern for both its product and its upstream service. The unique route index assumes one shared gateway namespace per environment and exact public paths; we will validate route conflicts before publishing them.

The `status` columns use `CHECK` constraints to ensure that invalid states cannot be stored. Business transitions still belong in the application service: a database check can confirm that a product's status is one of the allowed strings, but it cannot by itself ensure that only an authorized provider administrator publishes that product.

Now consider the subscription itself:

```

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    provider_tenant_id UUID NOT NULL,
    consumer_tenant_id UUID NOT NULL,
    application_id UUID NOT NULL,
    product_id UUID NOT NULL,
    plan_id UUID NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT fk_subscription_consumer_application
        FOREIGN KEY (consumer_tenant_id, application_id)
        REFERENCES developer_applications (tenant_id, id),

    CONSTRAINT fk_subscription_provider_product
        FOREIGN KEY (provider_tenant_id, product_id)
        REFERENCES api_products (tenant_id, id),

    CONSTRAINT fk_subscription_product_plan
        FOREIGN KEY (provider_tenant_id, product_id, plan_id)
        REFERENCES api_plans (tenant_id, product_id, id),

    CONSTRAINT chk_subscription_status
        CHECK (status IN
          ('PENDING', 'ACTIVE', 'REJECTED', 'REVOKED', 'EXPIRED'))
);

-- At most one pending or active subscription per app and product.
CREATE UNIQUE INDEX uq_active_subscription_per_app_product
    ON subscriptions
       (consumer_tenant_id, application_id, product_id)
    WHERE status IN ('PENDING', 'ACTIVE');

```

Notice how the subscription does not require `provider_tenant_id` to equal `consumer_tenant_id`. Instead, its foreign keys establish two independent facts: the application belongs to the consumer, and the product and plan belong to the provider.

The partial unique index also handles concurrent subscription requests more safely than a simple "check first, then insert" application query. If two requests race, PostgreSQL still enforces the uniqueness rule, and the service can translate the constraint violation into a clear API response.

In the full migration, we will define `developer_applications`, `upstream_services` and the other referenced tables before creating these dependent tables. We will also add the remaining indexes, status constraints, audit and usage columns, and configuration version tables.

## 4.10. Application authorization and Row-Level Security

Database constraints prevent invalid relationships, but they do not automatically prevent a user from querying another tenant's existing row. We need explicit authorization.

Consider this request:

```
GET /v1/tenants/tenant_shop/api-products
```

The tenant identifier in the URL tells the server which organization the caller wants to access. It does not prove that the caller belongs to that organization.

Spring Security authenticates the user, after which the backend verifies their membership and required role. Only then does the application establish the authorized tenant context and execute a tenant-scoped query.

Conceptually, this query:

```
SELECT *
FROM api_products
WHERE tenant_id = :authorized_tenant_id;
```

is safer than querying by the product ID alone and checking ownership as an afterthought. The same rule must be applied to reads, updates, deletes, exports and analytics queries.

We should also make tenant scope explicit in repository APIs. For example, `findByIdAndTenantId(productId, tenantId)` makes the intended ownership check visible in code. We must avoid patterns in which the application loads any record by ID and assumes that frontend visibility rules provide security.

### Adding PostgreSQL Row-Level Security

For defense in depth, we can enable RLS on tenant-owned tables. PostgreSQL policies control which rows may be selected or modified, and the table owner generally bypasses RLS unless row security is forced. Superusers and roles with `BYPASSRLS` also bypass it.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://www.postgresql.org\&sz=32)

PostgreSQL+1



An illustrative policy for a table containing `tenant_id` is:

```

ALTER TABLE api_products ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_products FORCE ROW LEVEL SECURITY;

CREATE POLICY api_products_tenant_isolation
ON api_products
USING (
    tenant_id =
      NULLIF(current_setting('app.tenant_id', true), '')::UUID
)
WITH CHECK (
    tenant_id =
      NULLIF(current_setting('app.tenant_id', true), '')::UUID
);

```

Before executing tenant-scoped database operations, the backend can set the authorized tenant ID within the current transaction:

```
SELECT set_config('app.tenant_id', :authorized_tenant_id, true);
```

The final `true` makes the setting transaction-local. This matters when connections are reused from a connection pool: we do not want one request's tenant context to leak into the next request.

The tenant context must be set by trusted backend code only after membership authorization. The database application role must not be a superuser, table owner with unrestricted bypass, or a role granted `BYPASSRLS`. We must also account for privileged internal operations such as publishing a gateway configuration across multiple tenants. Those operations need a deliberately restricted service path and cannot simply inherit the ordinary tenant-user policy.

RLS needs special policies on records that legitimately involve both parties, such as subscriptions and usage events. A provider and a consumer may each have access to a record for different reasons, but row visibility alone does not determine which columns each may see. Application authorization and appropriately restricted response models remain necessary.

For the MVP, application-level tenant authorization and composite database constraints are mandatory. We should introduce RLS after the transaction and connection-pool behavior has been tested carefully. RLS is an additional safeguard, not a replacement for correctly scoped repository methods or authorization logic.

## 4.11. Indexing strategy

An index should support a query that the application actually performs. Adding indexes everywhere increases storage use and write overhead, so we will prioritize the management and request-authorization queries expected in the MVP.

| Table               | Index or constraint                                       | Why it matters                                            |
| ------------------- | --------------------------------------------------------- | --------------------------------------------------------- |
| `users`             | Unique index on `lower(email)`                            | Efficient, case-insensitive login lookup                  |
| `memberships`       | Primary key `(tenant_id, user_id)`                        | Finds a user's membership within an organization          |
| `upstream_services` | `(tenant_id, name, environment)` unique                   | Finds and validates a service within its tenant           |
| `api_products`      | `(tenant_id, slug)` unique                                | Finds a provider's product by slug                        |
| `api_routes`        | `(environment, http_method, public_path)` unique          | Prevents route collisions in the shared gateway namespace |
| `api_credentials`   | Unique `key_prefix`                                       | Identifies a credential efficiently                       |
| `subscriptions`     | Partial unique index for pending and active subscriptions | Prevents duplicate access relationships                   |
| `subscriptions`     | `(consumer_tenant_id, application_id, status)`            | Retrieves an application's relevant subscriptions         |
| `usage_events`      | `(provider_tenant_id, occurred_at DESC)`                  | Supports provider traffic analytics                       |
| `usage_events`      | `(consumer_tenant_id, occurred_at DESC)`                  | Supports consumer usage analytics                         |
| `audit_logs`        | `(tenant_id, occurred_at DESC)`                           | Retrieves recent administrative events                    |

Some indexes are created automatically when a primary key or unique constraint is declared. Foreign keys, however, do not automatically guarantee that the referencing columns have an efficient index for every query. We should add the indexes needed for common lookups, joins and deletes, then verify the design using realistic data and PostgreSQL's query plans.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://www.postgresql.org\&sz=32)

PostgreSQL+1



The gateway will use its active configuration snapshot for route and credential lookups, so its runtime performance is not dependent on querying PostgreSQL for every API request. These database indexes primarily help the control plane generate configuration, validate subscriptions, manage records and query historical data.

The usage-events table will grow much faster than most management tables. We should begin with a normal indexed table and introduce time-based partitioning when volume and query performance justify it. The exact approach depends on actual retention requirements and event volume rather than assumptions about future scale.

## 4.12. Deletion and historical data

We should avoid casually cascading deletions through the domain model. Deleting a tenant must not inadvertently erase the audit trail of credential revocations or the usage history needed to investigate access decisions.

For primary entities such as tenants, products and applications, deactivation or archival is generally safer than physical deletion. Subscriptions should preserve their lifecycle history; credentials should become revoked; and published products should be archived rather than erased while historical records still refer to them.

Foreign-key actions should reflect these choices. `RESTRICT` or the default restrictive behavior is appropriate for records whose dependents must be preserved. Cascading deletion may be reasonable for temporary draft data with no independent historical value, but it should be an explicit decision.

We also need a policy for user removal. Disabling a membership should remove the user's current tenant privileges without erasing audit events that identify actions they performed previously.

## 4.13. Migration strategy with Flyway

We should use Flyway for schema evolution rather than allowing Hibernate to modify the database schema automatically. The database schema is part of our design and must change in a controlled, reviewable way.

A possible migration sequence is:

```

src/main/resources/db/migration/
    V1__create_users_tenants_memberships.sql
    V2__create_upstream_services_products_routes.sql
    V3__create_plans_applications_credentials.sql
    V4__create_subscriptions_and_constraints.sql
    V5__create_usage_audit_and_gateway_config.sql
    V6__add_indexes_and_tenant_policies.sql

```

Flyway's versioned migrations run in order and record their history and checksums. Once a versioned migration has been applied to a persistent environment, the normal approach is to create a new migration for subsequent changes rather than editing the already-applied file.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub+1



For development, we can use Flyway to create and evolve the schema from an empty PostgreSQL database. Hibernate should validate the resulting schema rather than generate it automatically.

## 4.14. Tenant-isolation tests

The database design is not complete until we can prove that the intended restrictions hold. We should create integration tests using PostgreSQL, ideally through Testcontainers, so the tests exercise the actual database engine and its constraints.

A fundamental scenario creates two organizations, each with a user, upstream service, API product and application. ShopSphere's user must be able to access ShopSphere's authorized resources but must not be able to retrieve, update or delete FinTrack's resources by manipulating a UUID, URL parameter or request body.

We should also test cross-tenant relationships at the database level. Attempting to attach a ShopSphere route to a FinTrack service should fail because of the composite foreign key. Likewise, a subscription that references a plan belonging to a different product must be rejected.

The tests must cover subscription and credential behavior too. Two simultaneous requests must not create duplicate active subscriptions for the same application and product. Revoked credentials and subscriptions must stop authorizing requests after the gateway receives the updated configuration.

If RLS is enabled, additional tests must verify that missing tenant context results in no unauthorized row access, the tenant context does not leak across pooled transactions, and ordinary application roles cannot bypass the policy.

These are security tests, not just repository tests. They should be part of our automated test suite and run whenever the tenant model, repository layer, or authorization configuration changes.

## 4.15. Phase 4 design decisions and completion criteria

We have now established the initial PostgreSQL model, identified the important relationships and constraints, defined how provider and consumer tenants are represented, and established the responsibilities of application authorization, composite foreign keys and optional RLS.

The database design is ready to proceed toward implementation once the corresponding schema migrations and integration tests are created. Before we begin coding, we should ensure the following decisions remain consistent throughout the project.

Shared multi-tenant tables

Every organization-owned record has explicit ownership, and tenant context is enforced by backend authorization.

Consistent relationships

Composite foreign keys prevent routes, services, products, plans and applications from being linked across incompatible tenant boundaries.

Secure consumption

Credentials are stored as hashes, and subscriptions explicitly connect consumer applications to provider products and plans.

Operational history

Usage events, audit records and gateway configuration versions remain distinguishable and can be queried efficiently.

Database-enforced correctness

Uniqueness, referential integrity, status constraints and tenant-isolation scenarios are tested against PostgreSQL.

The next stage is Phase 5: Critical Workflows and API Contracts. We will specify the management REST endpoints, request and response structures, HTTP methods, authorization requirements and sequence diagrams for organization setup, API publishing, subscription approval, credential issuance, and request processing. That will give us the interface contracts needed before creating the Spring Boot controllers and services.

Phase 5 — Critical Workflows and API Contracts

# Phase 5 — Critical Workflows and API Contracts

System Design Document · Version 1.0 · Multi-Tenant SaaS API Management Platform

In Phase 4, we designed the PostgreSQL database and defined how tenant isolation and data integrity will be maintained. Now we need to determine how users and applications interact with the system through APIs, and how the system behaves as they perform different operations.

This phase bridges system design and actual Java implementation. Before creating Spring Boot controllers and services, we should agree on the endpoints, request formats, response formats, permissions, validation rules, and sequence of operations.

Our goal is not simply to list REST endpoints. We must define complete workflows so that each API operation maintains the business rules established in the earlier phases.

## 5.1. What are critical workflows and API contracts?

A critical workflow is a sequence of operations that completes an important business task. For example, publishing an API involves registering an upstream service, creating an API product, configuring routes, validating the configuration, and publishing it to the gateway.

An API contract defines how a client communicates with a backend endpoint. It specifies the HTTP method, URL, headers, request parameters, request body, response body, authentication requirements, authorization rules, and possible error responses.

Consider a simple endpoint:

```
POST /api/v1/tenants/{tenantId}/services
```

This endpoint registers an existing backend API for a tenant. Its contract must specify that the caller needs permission to manage services in that tenant, the destination URL must pass security validation, and a successful request creates a service record and returns HTTP `201 Created`.

The contract should also specify what happens when the user has no membership in the tenant, submits an invalid URL, or attempts to create a duplicate service.

A well-defined contract lets the frontend and backend developers work independently. It also lets us write integration tests before all the features are implemented.

## 5.2. Two different API surfaces

Our platform exposes two fundamentally different interfaces: the management REST API and the runtime gateway API.

Management REST API

Used by the dashboard and developer portal.

It handles tenant management, API registration, route configuration, subscriptions, credentials, publishing, and analytics queries. These endpoints will be implemented in our Spring Boot control plane.

Runtime gateway API

Used by external consumer applications.

It receives requests for published APIs, verifies application credentials, checks subscriptions and rate limits, and forwards authorized requests to provider backends.

For example, the dashboard might call `POST /api/v1/tenants/{tenantId}/products` to create an API product. A consumer application, by contrast, might call `GET https://api.example.com/shopsphere/inventory/v1/items` to retrieve inventory data through the gateway.

The first endpoint changes platform configuration. The second consumes a published API. We should keep these interfaces separate in both the architecture and the implementation.

## 5.3. The complete business workflow

Our main workflow begins with the API provider and ends with a consumer successfully calling the provider's backend.

`diagram`

Each stage corresponds to one or more backend operations. We should implement and test the workflow as a connected sequence rather than treating each endpoint as an isolated CRUD operation.

The following sections explain the important workflows and the API contracts we should define for each one.

## 5.4. Define consistent API contract standards

Before designing individual endpoints, we should establish conventions that apply across the entire management API.

All management endpoints will use the `/api/v1` prefix. We will use resource-oriented URLs, standard HTTP methods, JSON request and response bodies, and consistent error formats. Tenant IDs can appear in resource paths, but they identify the requested organization; they do not establish the caller's authorization.

Authenticated management requests will use the application's configured authentication mechanism. For an initial JWT-based implementation, requests will generally include:

```
Authorization: Bearer <access-token>
Content-Type: application/json
```

The backend will authenticate the caller, verify membership and permissions in the requested tenant, validate the operation, and then execute the relevant business logic. It must not trust a tenant ID or role simply because the client supplied it.

Successful resource creation will generally return `201 Created`, successful reads and updates will return `200 OK`, and operations that complete without a response body can use `204 No Content`. Invalid request data should generally produce `400 Bad Request`, unauthenticated requests `401 Unauthorized`, unauthorized operations `403 Forbidden`, and missing resources `404 Not Found`.

We should also standardize the error body, pagination format, date representation, and request-correlation IDs. These conventions make the API predictable and reduce duplicated logic across controllers.

## 5.5. Workflow 1 — Organization setup and access management

The first workflow establishes the identity and tenant context used by every subsequent operation.

A new user registers and authenticates. The user then creates an organization, becoming its initial owner. The system records a membership connecting the user to the new tenant. The owner can invite team members, and each accepted membership receives a role that governs its permissions.

The management API should expose contracts similar to these:

| Method and endpoint                       | Purpose                                | Authorization                                |
| ----------------------------------------- | -------------------------------------- | -------------------------------------------- |
| `POST /api/v1/auth/register`              | Register a user                        | Public, with validation and abuse protection |
| `POST /api/v1/auth/login`                 | Authenticate a user                    | Public, with rate limiting                   |
| `POST /api/v1/tenants`                    | Create an organization                 | Authenticated user                           |
| `GET /api/v1/tenants`                     | List organizations the user belongs to | Authenticated user                           |
| `POST /api/v1/tenants/{tenantId}/members` | Invite or add a member                 | Tenant owner or authorized administrator     |
| `GET /api/v1/tenants/{tenantId}/members`  | List organization members              | Authorized tenant member                     |

For example, the organization creation contract could accept:

```
{
  "name": "ShopSphere",
  "slug": "shopsphere"
}
```

The server generates the tenant ID and creates the initial owner membership. A successful response returns the new organization and its identifier, but the caller does not choose its own membership role through the request body.

Tenant authorization must be enforced on every subsequent request. A user who belongs to ShopSphere and FinTrack can access both organizations, but only with the permissions granted by the membership for the active tenant context.

This workflow establishes the security foundation. The remaining workflows assume that the authenticated identity and tenant membership have already been verified.

## 5.6. Workflow 2 — Register an API and publish a product

This is the main provider-side workflow. ShopSphere already has an Inventory backend and wants to expose selected operations through our platform.

The administrator first registers the upstream service. Next, the administrator creates an API product, defines one or more plans, configures routes that point to the registered service, and publishes the product.

We should keep registration and publishing as separate operations. An incomplete or invalid draft must not become available to consumers merely because its database records exist.

| Method and endpoint                                            | Purpose                          |
| -------------------------------------------------------------- | -------------------------------- |
| `POST /api/v1/tenants/{tenantId}/services`                     | Register an upstream service     |
| `GET /api/v1/tenants/{tenantId}/services`                      | List registered services         |
| `POST /api/v1/tenants/{tenantId}/products`                     | Create an API product            |
| `POST /api/v1/tenants/{tenantId}/products/{productId}/plans`   | Create a subscription plan       |
| `POST /api/v1/tenants/{tenantId}/products/{productId}/routes`  | Create a route for the product   |
| `POST /api/v1/tenants/{tenantId}/products/{productId}/publish` | Validate and publish the product |
| `GET /api/v1/tenants/{tenantId}/products/{productId}`          | Retrieve product configuration   |

A service-registration request might look like this:

```
{
  "name": "Inventory Service",
  "environment": "DEVELOPMENT",
  "baseUrl": "http://mock-api:8082"
}
```

In a local Docker environment, `mock-api` would be a service name reachable over the internal container network. In an actual deployment, the destination would be validated against the platform's outbound-network security policy.

A route creation request could look like:

```
{
  "upstreamServiceId": "service-uuid",
  "httpMethod": "GET",
  "publicPath": "/shopsphere/inventory/v1/items",
  "upstreamPath": "/api/items",
  "environment": "DEVELOPMENT"
}
```

The backend derives the provider tenant from the authorized request context or verifies the path's tenant ID against the authenticated user's membership. It checks that the upstream service belongs to that tenant and that the requested product is also owned by that tenant.

The publish operation must validate the complete configuration. At a minimum, it checks that the product has an available plan and valid published routes, that all referenced services are eligible, and that no route conflicts with another route in the gateway's namespace.

Only after the database transaction succeeds should the platform create or expose the new gateway configuration version. The gateway then retrieves and activates that version.

This sequence prevents a published product from referring to a nonexistent service or an unpublished route. It also means that publishing a product and activating its configuration in the gateway are related but distinct events.

## 5.7. Workflow 3 — Discover a product and request a subscription

Once ShopSphere publishes its Inventory API product, DataPilot should be able to discover it through the developer portal.

The catalog must expose only products that are intended to be discoverable and are in a valid published state. Private products can require additional access checks or invitations. The catalog response must not reveal private upstream credentials or other sensitive provider configuration.

The primary catalog and subscription endpoints are:

| Method and endpoint                                                              | Purpose                                   |
| -------------------------------------------------------------------------------- | ----------------------------------------- |
| `GET /api/v1/catalog/products`                                                   | Browse discoverable published products    |
| `GET /api/v1/catalog/products/{productId}`                                       | View product details and available plans  |
| `POST /api/v1/tenants/{tenantId}/applications`                                   | Register a consumer application           |
| `POST /api/v1/tenants/{tenantId}/subscriptions`                                  | Request a subscription                    |
| `POST /api/v1/tenants/{providerTenantId}/subscriptions/{subscriptionId}/approve` | Approve a pending request                 |
| `POST /api/v1/tenants/{providerTenantId}/subscriptions/{subscriptionId}/reject`  | Reject a pending request                  |
| `GET /api/v1/tenants/{tenantId}/subscriptions`                                   | List subscriptions visible to that tenant |

DataPilot first registers an application:

```
{
  "name": "inventory-sync-service",
  "description": "Synchronizes product inventory"
}
```

It then requests access to ShopSphere's product:

```
{
  "applicationId": "application-uuid",
  "productId": "product-uuid",
  "planId": "plan-uuid"
}
```

The server verifies that the application belongs to DataPilot, the product is published, the plan belongs to that product, and the caller has permission to manage subscriptions for the consumer organization.

If ShopSphere requires provider approval, the subscription is created with `PENDING` status. An authorized ShopSphere administrator can approve or reject the request. If approval is not required, the platform can activate the subscription automatically after validating all conditions.

These state transitions must happen through authorized service operations rather than allowing a caller to submit `"status": "ACTIVE"` in a request body.

We should also handle concurrent requests. If two requests attempt to create the same active subscription, a database constraint must prevent duplicate records, even if the requests pass application-level checks at almost the same time.

## 5.8. Workflow 4 — Issue, use and revoke API credentials

After DataPilot's subscription becomes active, the application needs a credential to make requests through the gateway.

A credential belongs to the consumer application and identifies it when requests arrive. The gateway then checks whether the application has a valid subscription that authorizes the requested API product and route. This separates identification from authorization.

The relevant endpoints are:

| Method and endpoint                                                                              | Purpose                    |
| ------------------------------------------------------------------------------------------------ | -------------------------- |
| `POST /api/v1/tenants/{tenantId}/applications/{applicationId}/credentials`                       | Issue a new API credential |
| `GET /api/v1/tenants/{tenantId}/applications/{applicationId}/credentials`                        | List credential metadata   |
| `POST /api/v1/tenants/{tenantId}/applications/{applicationId}/credentials/{credentialId}/revoke` | Revoke a credential        |

The create-credential response is a special case because the plaintext secret must be shown only once. A successful response could look like this:

```
{
  "id": "credential-uuid",
  "keyPrefix": "sk_live_ab12",
  "secret": "<generated-secret-shown-once>",
  "status": "ACTIVE",
  "createdAt": "2026-10-30T12:00:00Z"
}
```

The actual secret is securely generated. The database stores its hash and associated metadata, not the reusable plaintext value. Subsequent list requests return metadata such as the key prefix, status, expiry and creation date, but never return the secret again.

Revoking a credential makes the credential ineligible for subsequent requests. The control plane must publish a configuration update or otherwise invalidate the corresponding runtime state, and the gateway must stop accepting the revoked key within the agreed propagation target under normal operating conditions.

Credential revocation and subscription revocation are different operations. Revoking a credential disables one key, while revoking a subscription disables the application's access to a particular product. A separate valid credential must not allow an application to bypass a revoked subscription.

## 5.9. Workflow 5 — Process a consumer request through the gateway

This is the most important runtime workflow because it executes whenever an external application calls a protected API.

Suppose DataPilot wants to retrieve inventory items. It sends a request to the published gateway endpoint, using its API credential in a header.

```
GET /shopsphere/inventory/v1/items HTTP/1.1
Host: api.example.com
X-API-Key: <generated-secret>
```

The gateway does not treat the presence of a key as sufficient authorization. It resolves the published route, identifies the application, verifies the credential, checks the active subscription and route permissions, and enforces the applicable rate limit before forwarding the request.

`daigram`

The gateway should perform these checks using its active runtime configuration and shared Redis counters rather than querying PostgreSQL for each request. The upstream call is made only after authentication, authorization and rate-limit enforcement succeed.

The expected rejection behavior is part of the API contract. A missing or invalid key generally results in `401 Unauthorized`; a recognized application without access to the requested product results in `403 Forbidden`; a missing public route produces `404 Not Found`; and an exceeded rate limit produces `429 Too Many Requests`.

If the upstream service times out, the gateway should return a controlled `504 Gateway Timeout`. It should also record the request outcome, including an appropriate error category and correlation ID, without logging the API secret.

This workflow will be tested through actual HTTP requests to the gateway, not just by testing its internal classes. That ensures route configuration, credential verification, subscription policy, Redis and proxy behavior work together correctly.

## 5.10. Workflow 6 — Revoke a subscription and propagate configuration

Subscription revocation is a security-sensitive workflow because it must prevent future unauthorized API access.

An authorized ShopSphere administrator submits a revocation operation. The control plane verifies the administrator's role and ownership of the provider product, updates the subscription state and records the action in the audit log. It then publishes an updated configuration version or triggers the corresponding authorization-state invalidation.

The gateway applies that change and stops permitting requests under the revoked subscription. The system should report both the durable subscription state and the configuration propagation status so operators can distinguish a completed database update from a runtime update that is still pending.

A proposed contract is:

```
POST /api/v1/tenants/{providerTenantId}/subscriptions/{subscriptionId}/revoke
Authorization: Bearer <access-token>
```

A successful management response might be:

```
{
  "subscriptionId": "subscription-uuid",
  "status": "REVOKED",
  "configurationVersion": 18,
  "configurationStatus": "PENDING"
}
```

The configuration status is illustrative. The exact response contract should make clear whether the database operation succeeded and whether the gateway has activated the new version. The API should not claim immediate runtime revocation simply because the subscription row was updated.

If another credential belongs to the same application, it must not bypass the revoked subscription. Conversely, revoking a subscription should not disable unrelated subscriptions that the application holds for other products.

Configuration propagation failures must be visible and recoverable. A missed update should be retried or reconciled through version checks, with alerts when a gateway remains on stale configuration.

## 5.11. Workflow 7 — Query usage analytics and audit history

After requests have passed through the gateway, providers and consumers need to understand how the APIs are being used.

The gateway emits usage events asynchronously, and the analytics worker persists them or updates aggregated records. The management API exposes tenant-scoped queries over those records.

A representative endpoint is:

```
GET /api/v1/tenants/{tenantId}/analytics/usage
    ?from=2026-10-01T00:00:00Z
    &to=2026-10-30T23:59:59Z
    &productId=<product-uuid>
```

The response can include total requests, successful requests, failures, latency statistics and usage grouped by application or route. The backend must verify that the caller is permitted to inspect the requested tenant's data and that any supplied product or application belongs to a resource the caller may access.

Audit history uses a different endpoint because it records management actions rather than gateway requests:

```
GET /api/v1/tenants/{tenantId}/audit-logs
```

Audit responses can include the actor, action, target resource, timestamp and outcome, with sensitive values redacted. A developer should not be able to use an analytics filter to inspect another organization's usage simply by changing a tenant, product or application identifier.

For large result sets, these endpoints should support pagination and bounded date ranges. We should also establish retention rules for usage events, since operational request records can grow substantially faster than management records.

## 5.12. Standard request and response contracts

Now that the workflows are defined, we need to standardize how the endpoints communicate.

Every management operation should use dedicated request and response DTOs rather than exposing JPA entities directly. For example, `CreateApiProductRequest` contains the fields a user is allowed to submit, while `ApiProductResponse` contains the fields the client is permitted to see.

This prevents internal fields such as ownership metadata, secret hashes, or audit information from accidentally becoming part of a public response. It also allows us to evolve the database without forcing every internal change to become an API contract change.

A standard error format could look like this:

```
{
  "timestamp": "2026-10-30T12:10:00Z",
  "status": 403,
  "code": "TENANT_ACCESS_DENIED",
  "message": "You are not authorized to perform this operation.",
  "path": "/api/v1/tenants/tenant-123/products",
  "requestId": "req-456"
}
```

The client receives a useful explanation and a stable error code, but not internal stack traces, SQL details, credential hashes, or information that reveals another tenant's private resources.

Validation errors can additionally provide field-level information. For instance, if `rateLimitPerMinute` must be positive, the response can identify that field and explain the validation requirement. Unexpected server errors should use a generic external message while preserving appropriate diagnostic details in protected logs.

We should use one shared error-handling mechanism in Spring Boot, such as a `@RestControllerAdvice`, to convert validation errors, authorization failures, domain exceptions and unexpected errors into consistent responses.

## 5.13. Contract rules for authentication and authorization

Authentication and authorization must be described for each endpoint, not treated as a generic feature that every controller automatically handles correctly.

The following table establishes the initial permission model.

| Operation                                       | Required permission                                              |
| ----------------------------------------------- | ---------------------------------------------------------------- |
| Create an organization                          | Authenticated user                                               |
| Invite or remove organization members           | Tenant owner or authorized administrator                         |
| Register upstream services and configure routes | Tenant administrator or an appropriately privileged developer    |
| Publish or archive an API product               | Authorized provider administrator                                |
| Create consumer applications                    | Authorized consumer-tenant member                                |
| Request a subscription                          | Authorized member of the consumer tenant                         |
| Approve or reject a subscription                | Authorized administrator of the provider tenant                  |
| Create or revoke application credentials        | Authorized application owner or tenant administrator             |
| View analytics                                  | Authorized tenant member with the relevant analytics permission  |
| Perform global platform operations              | Platform administrator with explicitly granted global privileges |

The exact permission granularity can grow later. For the MVP, a small set of roles with explicit authorization checks is easier to test and maintain than a fully customizable policy engine.

A critical point is that a valid JWT does not prove the caller is allowed to perform every operation. The backend must verify the user's membership in the relevant organization, the required role and the ownership of the target resource.

Likewise, a valid API key identifies a consumer application but does not substitute for checking its active subscription and route permissions. These are two separate authorization systems: one for humans managing the platform and another for applications consuming published APIs.

## 5.14. Additional API design requirements

A production-oriented API contract must address more than the happy path.

Validation and safe state transitions. The backend must validate URL formats, HTTP methods, request sizes, plan limits and resource relationships. Publishing, approving and revoking should be explicit authorized operations. Clients should not be able to modify protected state by submitting arbitrary entity fields.

Pagination and filtering. Catalog listings, memberships, subscriptions, audit logs and analytics queries should have bounded pagination. Filters must be applied after authorization, not used to bypass it.

Concurrency and idempotency. Critical operations must remain correct under repeated or concurrent requests. Database constraints can prevent duplicate active subscriptions, and idempotency keys are useful for operations where a client retry could create an unwanted duplicate. Credential issuance should have clearly defined retry behavior because a generated secret is displayed only once.

API versioning. The `/api/v1` prefix provides an initial management API version. Incompatible contract changes should not be introduced silently. API products may have their own version identifiers, which are distinct from the management API version.

Configuration consistency. Publishing a product or revoking access should result in a versioned runtime update. The response contract must distinguish a successful database change from confirmation that the gateway has activated that change.

Observability. Management and runtime requests should carry correlation identifiers. Logs must record useful operational context while redacting passwords, API keys, authorization headers and other sensitive data.

These rules should be reflected in both the design documents and the corresponding integration tests.

## 5.15. How we will implement this in Spring Boot

The purpose of Phase 5 is to create contracts that are independent of our internal Java implementation. Once they are agreed upon, we can map them to controllers, request DTOs, application services and persistence operations.

For example, the service-registration request would follow a flow like this:

`daigram`

The controller should remain thin: it accepts the request, delegates to the application service and returns the response. The application service implements business rules and coordinates transactional operations. Repositories handle persistence, and DTO mapping determines what the caller receives.

For operations such as publishing a product, the transaction should validate and persist the relevant state and create a durable indication that a new gateway configuration is needed. Configuration generation and distribution must have a recovery mechanism if the gateway is unavailable after the database transaction commits.

For runtime requests, Spring Cloud Gateway uses a different execution path. Custom gateway filters perform route and credential checks, consult runtime policy state and Redis, proxy authorized traffic, and emit usage events. We should not implement consumer traffic by sending every request through the management controllers.

This separation keeps the API contracts stable even as the internal services and infrastructure evolve.

## 5.16. What must be tested before Phase 5 is complete?

The endpoints are not enough on their own; the workflows must work end to end.

We should verify that a tenant administrator can register a service, create a product and plan, define routes, and publish the product. A consumer should then be able to discover that product, register an application, request a subscription, receive provider approval where required, and obtain an API credential.

The runtime integration test should call a real test backend through the gateway. It should demonstrate successful forwarding with a valid credential, rejection of invalid credentials, denial of access without an active subscription, enforcement of the configured rate limit, and preservation of tenant isolation.

We must also test the negative cases. A user should not be able to publish another tenant's product. A plan must not be associated with a different product. Duplicate active subscriptions must be prevented even under concurrent requests. Revoked credentials and subscriptions must cease authorizing requests within the defined propagation interval under normal operating conditions.

Finally, usage analytics and audit logs should be checked to ensure they record the right events without exposing secrets or leaking data across organizations.

## 5.17. Phase 5 deliverables

At the end of this phase, the design should contain a versioned list of management endpoints, their request and response DTOs, authentication and authorization requirements, domain validations, HTTP status codes, standard errors, and the sequence diagrams for all critical workflows. We should also have a clear distinction between management endpoints, internal configuration-distribution endpoints and the public gateway API.

The main workflows are now defined: tenant setup, API registration and publishing, consumer subscription, credential issuance, gateway request processing, revocation and analytics retrieval.

The next stage is Phase 6: Security, Reliability and Scalability. We will examine the difficult operational questions raised by these workflows: API-key verification, distributed rate limiting, configuration propagation, credential and subscription revocation, gateway behavior during failures, safe upstream routing, asynchronous event delivery, and how the platform behaves when deployed across multiple instances.

That phase will complete the major system-design decisions before we move into implementation planning and begin writing the Java code.

Phase 6: Security, Reliability and Scalability.
# Phase 6 — Security, Reliability and Scalability

System Design Document · Version 1.0 · Multi-Tenant SaaS API Management Platform

In the previous phases, we designed the database, defined tenant isolation, established API contracts, and separated the control plane from the data plane.

Now we need to address a more challenging question:

What happens when someone attacks the platform, a component becomes unavailable, traffic increases significantly, or different gateway instances operate with outdated configurations?

For an API management platform, these are not optional considerations. The platform controls access to other organizations' APIs, handles credentials, processes potentially untrusted traffic, and sits directly in the communication path between consumers and providers.

We will organize this phase around three major engineering concerns.

Security

Prevent unauthorized access and attacks.

Protect tenant data, user accounts, API credentials, upstream services and runtime policies.

Reliability

Keep the system correct and recoverable.

Handle gateway failures, Redis outages, stale configurations, upstream failures and event-processing problems.

Scalability

Handle increasing traffic and customers.

Scale gateway instances, management operations, database queries, rate-limit counters and analytics independently.

## 6.1. Identify the security threats

Before deciding which security mechanisms to implement, we should identify what could go wrong.

Our platform has several trust boundaries. An attacker might be an external user attempting to steal API credentials, a consumer trying to access another tenant's API, a legitimate tenant configuring a malicious upstream destination, or an abusive application generating excessive traffic.

The OWASP API Security Top 10 (2023) identifies several risks particularly relevant to our platform, including Broken Object Level Authorization, Broken Authentication, Unrestricted Resource Consumption, Broken Function Level Authorization, Server-Side Request Forgery (SSRF), and Security Misconfiguration.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://api-security.owasp.org\&sz=32)

OWASP API Security Top 10+1



Let's examine how these threats apply to our architecture.

| Potential threat                | Example scenario                                         | Required protection                             |
| ------------------------------- | -------------------------------------------------------- | ----------------------------------------------- |
| Cross-tenant access             | ShopSphere accesses FinTrack's API configurations        | Tenant-scoped authorization                     |
| Credential theft                | An attacker obtains an API key                           | Secure key storage, rotation and revocation     |
| Privilege escalation            | Developer approves a subscription without permission     | RBAC and function-level authorization           |
| Excessive traffic               | An application floods an upstream API                    | Distributed rate limiting                       |
| SSRF                            | A tenant registers an internal server as an upstream URL | Destination validation and network restrictions |
| Configuration tampering         | Unauthorized user modifies gateway routing policies      | Authenticated configuration publishing          |
| Resource exhaustion             | Large requests consume gateway memory or CPU             | Request limits and bounded processing           |
| Replay or duplicated operations | A client repeats a sensitive management operation        | Idempotency and state-transition validation     |

Our security design should protect both interfaces: the management API used by people and the runtime gateway used by software applications.

## 6.2. Authentication and authorization security

We have two different authentication scenarios, and they must not be confused.

### Management API authentication

People accessing the tenant dashboard must authenticate through the control plane. For the MVP, we can use Spring Security with short-lived JWT access tokens and a controlled refresh-token mechanism.

The user authenticates, receives an access token, and sends it with subsequent management requests. Spring Security validates the token, establishes the authenticated identity, and allows the application to resolve the user's organization memberships.

Passwords should be stored using a password-specific adaptive hashing algorithm such as Argon2id or BCrypt, not as plaintext or ordinary SHA-256 hashes.

The system should also support account deactivation, token expiration, refresh-token invalidation and appropriate login rate limits.

### Role-based access control

Authentication tells us who is making the request. Authorization tells us whether that user can perform the requested operation.

For example, a ShopSphere developer might have permission to register and configure API routes but not approve consumer subscription requests or manage billing settings.

The backend should verify both the user's role and the ownership of the resource.

Consider this request:

```
DELETE /api/v1/tenants/tenant_fin/products/product_123
Authorization: Bearer <shopsphere-user-token>
```

Even if the product exists, the operation must be denied when the authenticated user has no authorized membership in FinTrack.

This is an example of Broken Object Level Authorization, which is a particularly important API security risk identified by OWASP.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://api-security.owasp.org\&sz=32)

OWASP API Security Top 10+1



Our rule: Never consider a resource ID, tenant ID or client-supplied role as proof of authorization. Every management operation must be authorized against the authenticated user's verified membership and the target resource.

## 6.3. API credential security

API credentials are among the most sensitive elements of our platform because they provide access to provider APIs.

We need to design their entire lifecycle, from generation to validation, rotation and revocation.

### Secure credential generation

When an application requests a new API key, the control plane generates a cryptographically secure random secret with sufficient entropy. For example, we can generate 32 random bytes and encode them using Base64 URL-safe encoding.

A key could look conceptually like:

```
sk_live_a81f2c.<random-secret>
```

The prefix helps identify the credential format and locate its database record. The random secret provides the authentication material.

The platform displays the complete key only once. It must never store or return the original secret after creation.

### Secure credential storage

Instead of storing the plaintext key, store its cryptographic digest.

For a high-entropy randomly generated key, a suitable design is to compute an HMAC-SHA-256 digest using a server-held pepper, and store the digest along with the key identifier. The pepper remains outside the database in protected application configuration or a secrets manager.

When the gateway receives a key, it identifies the credential using its public prefix, computes the expected digest and compares it with the stored digest using a constant-time comparison.

The gateway must never log the key or expose it through error responses.

OWASP's Secrets Management guidance emphasizes controlling secret access, recording lifecycle metadata, supporting rotation and revocation, and preventing plaintext secrets from appearing in logs.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://cheatsheetseries.owasp.org\&sz=32)

OWASP Cheat Sheet Series+1



### Credential rotation

Suppose DataPilot suspects that its API key has been exposed.

The platform should allow it to create a replacement key while temporarily retaining the old key. This creates an overlap period during which both keys can work. Once the consuming application has switched to the new key, the old key is revoked.

This avoids unnecessary downtime during routine credential rotation.

The gateway must refresh or invalidate credential state when a key is revoked. Key rotation and subscription revocation must remain separate operations: one affects a particular credential, while the other affects authorization to a specific product.

## 6.4. Tenant isolation and authorization boundaries

Our shared-table database architecture makes tenant isolation a central security responsibility.

We will enforce it at multiple layers.

At the application layer, the control plane derives the authorized tenant context from the authenticated user and membership. Repository methods include tenant ownership in their queries, and domain operations validate the ownership of related resources.

At the database layer, composite foreign keys prevent invalid cross-tenant relationships. PostgreSQL RLS can provide another layer of protection, provided that the application uses appropriately restricted database roles and correctly manages transaction-local tenant context.

At the runtime layer, the gateway associates incoming credentials with consumer applications and verifies their subscriptions against the provider's published product configuration. A request cannot choose its own provider or consumer tenant by sending an arbitrary custom header.

We should also sanitize forwarding headers. A caller must not be able to inject trusted internal headers such as an application identity or tenant identity and trick the upstream backend into trusting them. The gateway should remove untrusted versions and, where necessary, generate trusted internal metadata itself.

The fundamental rule is that tenant identity is established through trusted authentication and authorization, not through request data supplied by the caller.

## 6.5. Prevent Server-Side Request Forgery in the gateway

This is one of the most important security problems specific to our project.

Remember, tenants can register their own upstream backend URLs. This means our gateway makes outgoing HTTP requests to destinations configured by customers.

A malicious tenant could attempt to register a destination such as:

```
http://localhost:8080/admin
http://127.0.0.1:5432
http://169.254.169.254/
http://internal-service:8080/secrets
```

If the gateway accepts arbitrary destinations, an attacker might use it to reach internal services, databases or cloud metadata endpoints that should never be exposed externally.

This is called Server-Side Request Forgery (SSRF). OWASP explicitly identifies SSRF as an API security risk.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://api-security.owasp.org\&sz=32)

OWASP API Security Top 10+1



We should implement destination validation when an upstream service is registered and enforce network-level restrictions when the gateway connects to it.

The validation layer should reject prohibited IP ranges, including loopback, link-local, multicast and cloud metadata destinations. It should resolve hostnames safely, account for DNS rebinding, and validate destination addresses at connection time rather than trusting an earlier DNS lookup. Redirects must either be disabled or checked again before following them.

However, there is an architectural consideration: some legitimate enterprise customers have backend services inside private networks. We should not accidentally block every possible private destination forever. For the MVP, restrict destinations to an explicitly permitted set of test and public upstream hosts. Private-network connectivity can later be introduced as a separate, authenticated integration feature.

We should also apply outbound firewall or network policies. Application-level URL validation alone is not sufficient.

## 6.6. Rate limiting and abuse prevention

The gateway is publicly accessible, so it must protect upstream services from excessive requests.

Suppose DataPilot has subscribed to the Starter plan, which allows 60 requests per minute. If it exceeds that limit, the gateway should reject further requests with HTTP `429 Too Many Requests`.

There are two major responsibilities here: determining the limiting scope and maintaining the counter correctly.

### Limiting scope

We should not limit traffic using only an API key because a consumer could create multiple credentials and bypass the restriction.

For our platform, the primary rate-limit scope should be the subscription or the combination of application and product. All credentials belonging to that application must contribute to the same counter for that product.

A Redis key could follow this logical structure:

```
rate-limit:{subscriptionId}:{timeWindow}
```

This gives the application one shared allowance for the corresponding subscription.

We can additionally introduce limits at the tenant, route and gateway levels to protect infrastructure independently from customer plans.

### Choosing the rate-limiting algorithm

A fixed-window counter is straightforward. For example, if the limit is 60 requests per minute, Redis maintains a counter for the current minute. When the counter reaches 60, subsequent requests are rejected until the next window.

The disadvantage is boundary bursting. An application might send 60 requests at the end of one minute and another 60 immediately at the beginning of the next.

For the MVP, we can use a token-bucket-style limiter, which allows controlled bursts while enforcing an average refill rate. The gateway should perform the counter update atomically so concurrent requests cannot independently observe the same available capacity.

Redis provides atomic counter operations and Lua scripting for implementing rate-limit checks without race conditions. It also supports counter expiration to remove old time-window data.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://redis.io\&sz=32)

Docs



### Rate limit versus quota

These two policies are different.

A rate limit controls how rapidly an application sends requests, such as 60 requests per minute. A quota controls cumulative consumption over a longer period, such as 10,000 requests per month.

The platform should expose both concepts in its plan model, but we should implement and verify the rate-limit mechanism first. Strict monthly quotas require durable, atomic accounting and recovery behavior. We should not assume that asynchronously processed usage events are sufficient to enforce them correctly.

### What happens when Redis fails?

Redis is required for our shared rate-limit decision. If Redis becomes unavailable, gateway instances cannot safely calculate the global allowance using independent local counters.

For APIs with mandatory rate-limit policies, I recommend a fail-closed behavior: return a controlled `503 Service Unavailable` rather than allowing unlimited traffic.

This sacrifices some availability to preserve the access policy. We can later introduce more sophisticated degradation strategies for different customer tiers, but the MVP should have one predictable rule.

## 6.7. Request-level security and resource protection

Even a correctly authenticated request can be harmful if it consumes excessive resources.

The gateway should therefore impose maximum request-body sizes, reasonable header limits, bounded connection pools and request timeouts. It should restrict supported HTTP methods to those configured in the route and avoid passing unnecessary internal headers to upstream services.

The system should also protect against abusive patterns such as rapid application registration, repeated subscription requests, excessive credential creation and automated attacks against login endpoints. These management operations need their own limits; limiting only the consumer gateway traffic leaves the management API exposed.

CORS should be configured for the browser-based dashboard where appropriate, but CORS is not an authorization mechanism and does not secure server-to-server API calls.

All public traffic must use HTTPS. Internal communication between the control plane and gateway should use authenticated service-to-service connections, preferably TLS with mutual authentication in a production deployment.

Finally, request and response logging must be selective. We need enough information to investigate failures, but logging complete authorization headers, API keys, passwords, payment information or sensitive payloads would create an additional security vulnerability.

## 6.8. Reliability: what happens when a component fails?

Security protects the platform from unauthorized behavior. Reliability ensures the platform continues to behave correctly when infrastructure, networks or upstream services fail.

Our architecture has several dependencies:

`daigram`

Each dependency has a different failure consequence. We should not use the same retry or fallback behavior for all of them.

## 6.9. Configuration propagation and consistency

The control plane and gateway maintain different representations of system state.

PostgreSQL stores the authoritative configuration, while the gateway keeps an in-memory snapshot for fast request processing.

This creates an important problem: what happens when an administrator changes a route, revokes a credential or suspends a subscription while several gateways are running?

We need a configuration versioning and propagation mechanism.

### Version-based configuration updates

Suppose ShopSphere publishes version 10 of its API configuration. All gateway instances load version 10.

Later, ShopSphere revokes DataPilot's subscription. The control plane persists the change and creates version 11.

The gateway instances discover version 11 through the configuration distribution mechanism, retrieve the new snapshot, validate it and replace their active configuration.

Each gateway should track its active version independently.

`daigram`

A configuration snapshot must be applied atomically. We should never partially update the active route table or overwrite a working snapshot with invalid configuration.

If an update fails, the gateway retains its last-known-good configuration, reports the failure and retries later.

### How do we handle stale configurations?

This is where security and reliability intersect.

Suppose Gateway 1 has received version 11, but Gateway 2 is still running version 10. A revoked subscription might continue working through Gateway 2.

We therefore need each gateway to report its active version and last successful synchronization time. The control plane can monitor this state and alert when a gateway has not received a recent configuration update.

For the MVP, our target remains a five-second propagation interval under normal operating conditions. We can use a two-second polling interval initially and measure the actual update delay.

However, we must not claim guaranteed five-second revocation during a configuration-source outage.

We need an additional freshness policy: if a gateway's authorization configuration becomes older than an agreed maximum age, it should stop accepting requests that depend on potentially stale authorization state. In our initial design, we should favor denying protected traffic rather than continuing indefinitely with potentially revoked permissions.

This makes the failure behavior explicit and prevents stale caches from silently becoming permanent authorization sources.

### Making configuration publishing reliable

There is another failure scenario. The control plane might update PostgreSQL successfully, but crash before notifying the gateway that a new version exists.

To prevent this, we should eventually use a transactional outbox.

Within one database transaction, the control plane persists the business change, creates the new configuration version and writes a corresponding outbox event. A background publisher delivers the event to the notification mechanism. If delivery fails, it retries.

The database remains the source of truth, and periodic version polling provides reconciliation if a notification is lost.

For the MVP, polling is enough to build the first working implementation. We should introduce the transactional outbox when we implement reliable asynchronous publishing, rather than starting with unnecessary distributed messaging infrastructure.

## 6.10. Upstream service failures

Remember that our platform does not own the APIs registered by customers. It only manages and forwards requests to them.

Therefore, an upstream service might become unavailable even while our own gateway is healthy.

Consider this scenario: DataPilot calls ShopSphere's Inventory API, but ShopSphere's backend takes 30 seconds to respond. We must not allow that request to occupy gateway resources indefinitely.

We should introduce three resilience mechanisms.

Timeouts limit how long the gateway waits for an upstream response. We should configure connection and response timeouts separately, with values appropriate for the type of API being consumed.

Circuit breakers prevent repeated requests from continuously hitting an unhealthy backend. When failures cross a configured threshold, the circuit opens and the gateway temporarily rejects or short-circuits further requests. After a recovery interval, it allows a limited number of trial requests to determine whether the backend is healthy again.

Bulkheads isolate resources so that one slow upstream does not exhaust all gateway connections and prevent requests to unrelated providers from being processed.

`daigram`

We can implement these mechanisms using suitable Spring Cloud Gateway resilience integrations and a circuit-breaker library such as Resilience4j.

Retries should be used carefully. Retrying a failed `GET` request may be acceptable under appropriate conditions, but retrying a `POST` that creates an order or performs a payment could produce duplicate operations. For non-idempotent operations, retries require an explicit idempotency contract with the upstream service.

When an upstream is unavailable, the gateway should return a controlled error such as `502 Bad Gateway`, `503 Service Unavailable` or `504 Gateway Timeout`, depending on the failure.

The gateway must not indefinitely retry failed upstream calls because this can amplify an outage into a larger cascading failure.

## 6.11. Database and infrastructure reliability

PostgreSQL is the durable source of truth for our management data. Losing it would affect organization management, product configuration, subscription records and historical information.

We should therefore implement automated database backups, restricted database access and a tested restoration procedure. For a production system, point-in-time recovery (PITR) can help restore the database to a selected moment using a base backup and retained transaction logs.

We should periodically test restoring backups. A backup that has never been restored is not sufficient evidence that recovery will work.

Redis has a different role. In our architecture, it stores short-lived rate-limit counters and optional cached state, so it should not become the only place where critical business records exist.

However, losing Redis counters can temporarily reset traffic allowances. That means Redis failure and recovery must be treated as a security and policy concern, not merely a performance issue. Strict monthly quota accounting should use a durable accounting mechanism rather than depend exclusively on disposable rate-limit counters.

We should also establish database connection limits, connection-pool sizing, health checks and controlled application startup behavior. The gateway should not become unavailable simply because an optional analytics worker is restarting, but it must not start accepting protected traffic without the runtime configuration required to enforce access rules.

## 6.12. Asynchronous usage processing reliability

Earlier, we decided that the gateway should emit usage events and allow a background worker to persist them.

Now we need to make that asynchronous pipeline reliable.

Suppose the gateway processes a successful request and publishes its usage event. The RabbitMQ broker becomes temporarily unavailable, and the event is not delivered.

If we simply ignore the error, our analytics dashboard will show incorrect request totals.

The pipeline should therefore use bounded retries, durable messaging where configured, dead-letter handling for permanently failing messages, and idempotent event processing.

Every event should have a unique `event_id`. The analytics worker can enforce a unique constraint on that identifier so the same event is not counted twice when a broker redelivers a message.

`diagram`

For the MVP, usage analytics can be operationally best-effort, provided the limitations are explicit and it is not used as the authoritative source for strict access decisions or billing.

If we later introduce monetization based on consumed requests, we must strengthen this pipeline. We would need durable collection, reconciliation, event retention and auditable accounting so that dropped events do not cause incorrect customer charges.

This is an important architectural distinction: an analytics pipeline and a billing ledger are not automatically the same system.

## 6.13. Scalability: how will the platform handle more tenants and traffic?

Let's consider a future scenario.

Initially, our platform has five organizations, ten API products and a few thousand requests per day. Later, it supports hundreds of organizations, thousands of products and millions of gateway requests per day.

We should be able to increase capacity without redesigning the entire architecture.

The separation between the control plane and data plane already gives us an advantage because these two workloads behave differently.

The control plane receives comparatively fewer administrative operations, while the gateway processes the continuous stream of consumer API requests.

We can therefore scale them independently.

### Horizontal scaling

The control plane should be stateless across instances. User authentication state should be represented through validated tokens or shared session infrastructure, and durable business state remains in PostgreSQL.

This allows us to run multiple Spring Boot control-plane instances behind a load balancer.

The same principle applies to the gateway. Multiple gateway instances can process incoming API requests independently because they obtain shared configuration and use Redis for distributed rate limiting.

`diagram`

If traffic increases, we can add gateway instances without changing the management backend or creating separate databases for each gateway.

However, the shared Redis instance and configuration source can become bottlenecks. We will need to monitor them and scale them appropriately.

### PostgreSQL scaling

As the number of organizations grows, management queries and usage analytics queries will have different performance characteristics.

Management queries typically involve tenants, products, subscriptions and memberships. These can be optimized using appropriate indexes and tenant-scoped queries.

Usage-event queries are different because the event table can grow very quickly. We can introduce time-based partitioning, retention policies and aggregated usage tables when the volume justifies them.

For example, instead of calculating monthly analytics by scanning millions of raw request records every time a dashboard loads, a background worker can maintain daily aggregates by tenant, application, product and status code.

Read replicas can also be introduced for reporting workloads when read traffic becomes significant. They should not be used for authorization decisions that require immediately consistent subscription or revocation state.

### Redis scaling

Redis becomes increasingly important when many gateway instances share the same rate-limit counters.

A centralized Redis deployment provides common counters, but as traffic grows, we may need Redis clustering, capacity planning, key-expiration strategies and careful handling of hot keys.

For example, a single extremely popular API subscription might generate a very large number of counter updates against the same logical rate-limit key.

We should monitor Redis command latency, memory consumption, eviction behavior and connection usage. The platform must never silently change from shared rate limiting to isolated per-instance counters just because Redis is unavailable.

### Asynchronous analytics scaling

The usage-event pipeline can be scaled independently of gateway traffic by increasing the number of consumers, optimizing database batch writes and partitioning event storage.

The gateway should emit events efficiently and avoid waiting for slow analytics queries.

A message broker also helps absorb temporary bursts. However, it does not eliminate processing limits: if events arrive faster than workers can persist them, queue depth increases. We must monitor processing lag and ensure that the queue has suitable retention and recovery policies.

## 6.14. Observability and monitoring

A platform can appear to work correctly while silently accumulating failures. We need visibility into the health of individual components and the complete request lifecycle.

We should use structured logging, metrics and distributed tracing.

Logging records what happened during a particular operation. For example, if a consumer receives a `403 Forbidden`, the logs should help identify whether the subscription was inactive or the route was not included in its product.

Metrics show how the system behaves over time. Useful metrics include gateway request volume, response status distribution, p95/p99 latency, Redis operation latency, upstream failures, active configuration versions, queue depth and database connection utilization.

Distributed tracing helps follow a request across the gateway, upstream API and relevant internal processing components. Each request should have a correlation or trace identifier.

A possible monitoring architecture is:

`diagram`

For the Java applications, Spring Boot Actuator and Micrometer are suitable starting points for application health and metrics. Prometheus can scrape metrics, while Grafana can visualize them. OpenTelemetry can provide distributed tracing.

We should define a few important service-level indicators:

| Indicator                           | What it tells us                                        |
| ----------------------------------- | ------------------------------------------------------- |
| Gateway availability                | Whether consumer requests can reach the gateway         |
| Gateway p95 latency                 | Whether request processing is becoming slow             |
| Upstream error rate                 | Whether provider backends are failing                   |
| Redis latency                       | Whether rate-limit enforcement is becoming a bottleneck |
| Configuration propagation delay     | How quickly gateway instances receive policy changes    |
| Stale configuration count           | Whether any gateway is running outdated configuration   |
| Analytics queue lag                 | Whether usage events are accumulating                   |
| Cross-tenant authorization failures | Whether callers are attempting unauthorized access      |

For our MVP, the five-second configuration propagation target is one of the most useful measurements to verify. We should record both the time the control plane publishes a version and the time each gateway activates it.

## 6.15. Deployment and scaling strategy

We should not attempt to implement all the scaling mechanisms at once. A progressive deployment strategy will allow us to verify the architecture before introducing additional infrastructure.

Stage 1 — MVP

Initial implementation

We run one control-plane instance, one gateway instance, PostgreSQL and Redis using Docker Compose. A mock upstream API validates routing, and analytics can begin with asynchronous processing once the core gateway works.

Stage 2 — Horizontally scalable deployment

Run multiple stateless control-plane and gateway instances behind load balancers. Use shared Redis counters and a common versioned configuration source. Introduce a durable message broker and independently scalable analytics workers.

Stage 3 — Production hardening

Introduce PostgreSQL backup and recovery, database read replicas where appropriate, partitioned usage-event storage, Redis high availability, automated configuration reconciliation, centralized observability and load testing.

Stage 4 — Enterprise and multi-region capabilities

Consider regional gateways, tenant-specific network connectivity, dedicated database infrastructure, advanced key management and cross-region traffic policies. Global rate limiting requires an explicit consistency design because independently maintained regional counters can permit higher aggregate traffic than a global limit.

The main architectural advantage is that our initial modular monolith and separate gateway do not need to be discarded as the platform grows. We can scale the existing boundaries first and introduce additional services only when a measured operational requirement justifies them.

## 6.16. Security and reliability testing strategy

Security and reliability should be validated through automated tests, not simply documented as design principles.

We should organize testing around realistic attack and failure scenarios.

| Scenario                               | Expected result                             |
| -------------------------------------- | ------------------------------------------- |
| User accesses another tenant's product | `403` or appropriately non-disclosing `404` |
| Invalid API credential                 | `401 Unauthorized`                          |
| Revoked credential used                | Request rejected                            |
| Inactive subscription used             | `403 Forbidden`                             |
| Application exceeds rate limit         | `429 Too Many Requests`                     |
| Redis unavailable                      | Mandatory rate-limited requests fail safely |
| Malicious upstream URL                 | Registration or connection rejected         |
| Upstream service becomes unavailable   | Controlled gateway error                    |
| Invalid configuration snapshot         | Previous valid configuration retained       |
| Gateway misses configuration update    | Stale version detected and recovered        |
| Message broker temporarily unavailable | Bounded retry/recovery mechanism activated  |
| Same usage event is delivered twice    | Event is not counted twice                  |

We should use different testing approaches for different layers.

Unit testing verifies individual business rules, such as subscription state transitions, permission evaluation and configuration validation.

Integration testing verifies interactions with PostgreSQL, Redis, Spring Security and the gateway. Testcontainers is useful because we can run actual PostgreSQL and Redis instances in automated test environments rather than relying only on mocks.

End-to-end testing verifies a complete journey: create two organizations, register an API, publish a product, subscribe another organization, issue a key, and call the provider API through the gateway.

Load testing measures gateway throughput, latency and rate-limit behavior under concurrent traffic. It should also test multiple gateway instances sharing the same Redis counters.

Failure testing deliberately makes Redis unavailable, interrupts configuration delivery, shuts down an upstream service and delays analytics processing. We then verify whether the platform follows its documented failure behavior.

A particularly valuable test is to revoke a subscription while traffic is flowing through multiple gateway instances. We should measure how long each gateway takes to stop accepting the revoked access.

This gives us evidence that the security and consistency requirements actually work.

## 6.17. What should we implement in the MVP versus later?

One common mistake in system design is trying to solve every possible production problem before the basic product works. We should separate essential protections from advanced infrastructure.

| Area                | MVP implementation                               | Future improvement                                 |
| ------------------- | ------------------------------------------------ | -------------------------------------------------- |
| Authentication      | Spring Security and JWT                          | Enterprise SSO and MFA                             |
| Tenant isolation    | Scoped queries and composite foreign keys        | Fully tested PostgreSQL RLS                        |
| API credentials     | Secure generation, hash storage, revocation      | Managed key vault and automated rotation           |
| Upstream security   | Destination validation and outbound restrictions | Private networking and advanced egress policies    |
| Rate limiting       | Redis-backed distributed limiter                 | Advanced multi-region rate limiting                |
| Configuration       | Versioned snapshots and polling                  | Event-driven push and transactional outbox         |
| Upstream resilience | Timeouts and circuit breakers                    | Advanced adaptive traffic management               |
| Analytics           | Asynchronous usage events                        | Distributed analytics and reporting infrastructure |
| Monitoring          | Actuator, Micrometer and structured logs         | Full tracing and centralized observability         |
| Scaling             | Independent gateway deployment                   | Multi-region and enterprise deployment             |

For the first working version, security controls must not be postponed simply because their production implementation can become sophisticated. Tenant authorization, secure API keys, upstream destination validation, rate limiting and safe configuration updates belong in the MVP.

Advanced distributed deployment, multi-region synchronization and enterprise key-management infrastructure can be introduced progressively.

## 6.18. Final architecture after Phase 6

Combining everything we have designed, the resulting architecture looks like this:

`diagram`

This architecture incorporates the major decisions across our system design.

The control plane is responsible for authoritative management operations. The gateway enforces published policies on incoming traffic. PostgreSQL preserves durable state, Redis handles distributed rate-limit counters, RabbitMQ supports asynchronous event processing, and observability infrastructure provides visibility across the system.

Security, reliability and scalability are addressed through explicit boundaries rather than being treated as features added after implementation.

## 6.19. Phase 6 completion and transition to implementation

At the end of Phase 6, we have established how the platform should protect credentials and tenant data, prevent SSRF, enforce traffic policies, handle upstream and infrastructure failures, propagate configuration changes, process analytics events and scale independent components.

The major design decisions are now established.

System design progress

6 phases completed

Phase 1 — Requirements and Product Scope

Phase 2 — Domain Model and Business Rules

Phase 3 — High-Level Architecture

Phase 4 — Database Design and Tenant Isolation

Phase 5 — Critical Workflows and API Contracts

Phase 6 — Security, Reliability and Scalability

Next: Phase 7 — Implementation Blueprint

## What comes next: Phase 7 — Implementation Blueprint

We are approaching the point where we can start building the Java applications. However, before opening Spring Initializr and generating code, we should translate the system design into a concrete implementation plan.

Phase 7 should cover the repository structure, control-plane module boundaries, gateway package structure, dependency selection, Docker Compose infrastructure, Flyway migration ordering, security implementation, testing strategy and implementation milestones.

I recommend organizing the actual development into independently testable milestones, starting with:

Milestone 1: Project setup and infrastructure, followed by tenant authentication and authorization.

Milestone 2: API registration, API products, plans and dynamic route configuration.

Milestone 3: Gateway implementation and actual upstream request forwarding.

Milestone 4: Consumer applications, subscriptions, credential management and runtime authorization.

Milestone 5: Redis-backed rate limiting, configuration propagation and revocation.

Milestone 6: Usage analytics, audit logs, monitoring and reliability testing.

One important recommendation: do not start by building every management endpoint. Start with a vertical slice that proves an organization can register an upstream API and successfully call it through your gateway. Then add the security and subscription layers around that working foundation.

Our objective is to build a working API management platform, not just a collection of Spring Boot CRUD modules.


Phase 7 — Implementation Blueprint
Both are valid observations. In fact, your questions highlight two architectural distinctions that we should make clearer before starting implementation.

There is no contradiction between having two independent applications and keeping them inside one parent repository. Similarly, the modular monolith architecture does not prevent us from using the conventional `Controller → Service → Repository → Entity` structure inside every feature module.

Let's clarify both decisions and establish the structure we should actually follow.

# 1. Why have a parent folder if Control Plane and API Gateway are independent applications?

The parent folder represents the Git repository (monorepo), not a parent application.

There are two separate concepts here:

Independent applications means the Control Plane and API Gateway have separate application entry points, dependencies, configurations, execution processes and deployment units.

Monorepo means we keep the source code of both applications in one Git repository.

These concepts are independent of each other.

For example, consider this structure:

```

api-management-platform/           <-- Git repository root
│
├── pom.xml                        <-- Maven parent/aggregator
│
├── control-plane/                 <-- Independent Spring Boot app
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/
│           └── resources/
│
├── api-gateway/                   <-- Independent Spring Boot app
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/
│           └── resources/
│
├── docker-compose.yml
├── .env.example
├── docs/
└── README.md

```

Here, the root `../pom.xml` is a Maven aggregator with `pom` packaging. It is not an executable Spring Boot application.

Its responsibility is to coordinate the builds of its child projects and optionally manage shared dependency versions, Java version and plugin configurations.

Conceptually:

```

<project>
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.platform</groupId>
    <artifactId>api-management-platform</artifactId>
    <version>1.0.0</version>

    <packaging>pom</packaging>

    <modules>
        <module>control-plane</module>
        <module>api-gateway</module>
    </modules>
</project>

```

Each application has its own `../pom.xml` and can produce its own executable JAR and Docker image.

| Aspect                  | Control Plane           | API Gateway                               |
| ----------------------- | ----------------------- | ----------------------------------------- |
| Application entry point | Own `main()`            | Own `main()`                              |
| Spring configuration    | Independent             | Independent                               |
| Dependencies            | Management dependencies | Gateway dependencies                      |
| Port                    | 8080                    | 8081                                      |
| Deployment              | Separate container      | Separate container                        |
| Scaling                 | Independent             | Independent                               |
| Database access         | PostgreSQL through JPA  | Primarily Redis and runtime configuration |
| Main responsibility     | API management          | API traffic processing                    |

For example, we can build them separately:

```
mvn -pl control-plane -am clean package
```

```
mvn -pl api-gateway -am clean package
```

The `-am` option builds required reactor dependencies when applicable.

### Could we use separate Git repositories?

Absolutely.

We could have:

```
api-management-control-plane/
api-management-gateway/
```

Each would have its own root `../pom.xml`, Docker configuration and Git history.

However, I recommend a monorepo for this project because both applications are being developed together, share API contracts and need coordinated changes during the initial development.

My recommendation: Keep one Git repository, but maintain two independently deployable Spring Boot applications.

# 2. Why didn't we use Controller, Service, Repository and Entity for every module?

You are correct that we should explicitly follow this structure.

In Phase 7, I suggested another architectural naming convention:

```
api/
application/
domain/
infrastructure/
```

This comes from Clean Architecture and Hexagonal Architecture.

It represents essentially the same responsibilities, but organizes them using architectural layers rather than traditional Spring component names.

Here is the relationship:

| Conventional Spring structure | Architecture-oriented structure |
| ----------------------------- | ------------------------------- |
| Controller                    | API / Presentation              |
| Service                       | Application                     |
| Entity and domain logic       | Domain                          |
| Repository                    | Infrastructure / Persistence    |
| DTO                           | API contracts                   |

For our project, I recommend using the conventional Spring structure inside each business module, while maintaining the architectural separation between modules.

This makes the code easier to understand and is perfectly compatible with a modular monolith.

## 2.1. What our modular monolith should look like

We should follow a package-by-feature approach.

That means we first divide the application according to business functionality, then organize each feature into its own layers.

For example:

```

control-plane/
│
└── src/main/java/com/platform/controlplane/
    │
    ├── ControlPlaneApplication.java
    │
    ├── identity/
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   ├── dto/
    │   └── enums/
    │
    ├── tenant/
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   ├── dto/
    │   └── enums/
    │
    ├── apicatalog/
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   ├── dto/
    │   └── enums/
    │
    ├── subscription/
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   ├── dto/
    │   └── enums/
    │
    ├── credential/
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   ├── dto/
    │   └── enums/
    │
    ├── gatewayconfig/
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   └── dto/
    │
    ├── audit/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   └── dto/
    │
    └── shared/
        ├── security/
        ├── exception/
        ├── configuration/
        └── tenantcontext/

```

Notice that each feature owns its controllers, services, repositories, entities and DTOs.

For example, the `tenant` module manages tenants and memberships. The `apicatalog` module manages upstream services, API products, plans and routes. The `subscription` module manages subscription requests, approvals and state transitions.

We don't create one giant repository package containing repositories for every feature.

## 2.2. How a feature module works internally

Let's take the `apicatalog` module as an example.

Suppose a tenant administrator wants to register an upstream API.

The request flows as follows:

`diagram`

The responsibilities are clearly separated.

Controller: Receives HTTP requests, validates their structure, extracts request context and delegates to the service.

Service: Contains business logic. It verifies ownership, validates the requested configuration, coordinates database operations and enforces business rules.

Repository: Performs database access using Spring Data JPA. It contains tenant-scoped queries and persistence operations.

Entity: Represents the persistent domain object, such as `UpstreamService`, `ApiProduct` or `ApiRoute`.

DTO: Defines what the client is allowed to send and receive. It prevents internal database fields from leaking into API responses.

For example:

```
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/services")
@RequiredArgsConstructor
public class ApiServiceController {

    private final ApiServiceService apiServiceService;

    @PostMapping
    public ResponseEntity<ApiServiceResponse> register(
            @PathVariable UUID tenantId,
            @Valid @RequestBody RegisterApiServiceRequest request) {

        ApiServiceResponse response =
                apiServiceService.register(tenantId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }
}
```

The controller delegates everything important to the service. It does not directly access the repository or implement business rules.

The service then performs the ownership checks, validates the destination and saves the entity through the repository.

This is the conventional Spring Boot development approach we should use throughout the control plane.

# 3. Does this still qualify as a modular monolith?

Yes. This is actually a good way to implement a modular monolith.

The distinction is that a modular monolith is defined by its logical module boundaries, not by the number of packages or layers inside each module.

Consider this:

Control Plane — One Spring Boot Application

One deployable JAR · One application process

Identity Module

Controller · Service · Repository · Entity

Tenant Module

Controller · Service · Repository · Entity

API Catalog Module

Controller · Service · Repository · Entity

Subscription Module

Controller · Service · Repository · Entity

Credential Module

Controller · Service · Repository · Entity

Gateway Config Module

Controller · Service · Repository · Entity

Audit Module

Controller · Service · Repository · Entity

PostgreSQL

Shared database, controlled module ownership

All seven modules run inside one Spring Boot application. They share the same deployment, but their responsibilities are separated.

The important part is that modules should not freely access each other's internal repositories or entities.

For example, the Subscription module should not directly modify a product by accessing `ApiProductRepository`.

Instead, it should use an exposed service or well-defined interface from the API Catalog module to retrieve the required product information.

Likewise, the API Catalog module should not implement subscription approval logic.

This is how we prevent a modular monolith from gradually becoming a tightly coupled application.

## 3.1. Module interaction example

Suppose a developer requests a subscription.

The `SubscriptionService` needs to validate that the selected product and plan exist and are published.

It can communicate with the API Catalog module through its exposed application service:

```
ApiProduct product =
        apiProductService.getPublishedProduct(productId);

ApiPlan plan =
        apiPlanService.getPlanForProduct(productId, planId);
```

The Subscription module should not execute arbitrary API Catalog repository queries itself.

The subscription module then validates the consumer application, checks its own business rules, creates the subscription and records the operation.

This is logical modularity even though all modules execute within one JVM.

# 4. Corrected final project structure

Combining both decisions, this is the structure I recommend we follow during implementation.

```

api-management-platform/
│
├── pom.xml
│
├── control-plane/
│   ├── pom.xml
│   │
│   └── src/
│       ├── main/
│       │   ├── java/com/platform/controlplane/
│       │   │   │
│       │   │   ├── ControlPlaneApplication.java
│       │   │   │
│       │   │   ├── identity/
│       │   │   │   ├── controller/
│       │   │   │   ├── service/
│       │   │   │   ├── repository/
│       │   │   │   ├── entity/
│       │   │   │   └── dto/
│       │   │   │
│       │   │   ├── tenant/
│       │   │   │   ├── controller/
│       │   │   │   ├── service/
│       │   │   │   ├── repository/
│       │   │   │   ├── entity/
│       │   │   │   └── dto/
│       │   │   │
│       │   │   ├── apicatalog/
│       │   │   │   ├── controller/
│       │   │   │   ├── service/
│       │   │   │   ├── repository/
│       │   │   │   ├── entity/
│       │   │   │   └── dto/
│       │   │   │
│       │   │   ├── subscription/
│       │   │   ├── credential/
│       │   │   ├── gatewayconfig/
│       │   │   ├── audit/
│       │   │   ├── analytics/
│       │   │   └── shared/
│       │   │
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       │
│       └── test/
│
├── api-gateway/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/platform/gateway/
│       │   │   ├── GatewayApplication.java
│       │   │   ├── routing/
│       │   │   ├── security/
│       │   │   ├── ratelimit/
│       │   │   ├── configsync/
│       │   │   ├── proxy/
│       │   │   └── usage/
│       │   └── resources/
│       │       └── application.yml
│       └── test/
│
├── usage-worker/
│   └── (introduced in analytics milestone)
│
├── infrastructure/
│
├── docs/
│
├── docker-compose.yml
├── .env.example
└── README.md

```

The `api-gateway` does not need to follow the same controller-service-repository structure because it is not a conventional CRUD management application. Its architecture should be organized around routing, filters, security, rate limiting and configuration synchronization.

Similarly, not every module needs every folder. The audit module might not need a public controller initially, and some simple domain objects may not require separate service classes. We should introduce layers where they serve a real responsibility.

# 5. Final architectural decisions

Let's lock these decisions for the implementation:

| Decision                   | Final approach                                  |
| -------------------------- | ----------------------------------------------- |
| Repository                 | Single monorepo                                 |
| Root Maven POM             | Parent/aggregator only                          |
| Control Plane              | Independent Spring Boot application             |
| API Gateway                | Independent Spring Cloud Gateway application    |
| Control Plane architecture | Modular monolith                                |
| Module organization        | Package by feature                              |
| Module internal structure  | Controller, Service, Repository, Entity, DTO    |
| Module interaction         | Through exposed application services/interfaces |
| Database                   | Shared PostgreSQL                               |
| Database migrations        | Flyway                                          |
| Infrastructure             | Docker Compose                                  |

One additional recommendation: we should enforce module boundaries gradually using package visibility, architectural tests and optionally Spring Modulith. This helps prevent developers from bypassing module services and accessing internal repositories directly. [Spring Modulith documentation](https://docs.spring.io/spring-modulith/reference/?utm_source=chatgpt.com).

My conclusion: Your suggested structure is the one we should use. It combines the familiarity of conventional Spring Boot development with the separation of responsibilities required by a modular monolith, while keeping the Control Plane and API Gateway independently deployable.

Before implementing Milestone 1, we should use this corrected structure as the authoritative repository and package design rather than mixing the two organizational approaches.
