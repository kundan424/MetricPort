# Architecture Overview

This platform adopts the separation of concerns between:
- **Control Plane**: Handles administrative workflows, tenant isolation, API catalogs, consumer subscriptions, and metadata persistence in PostgreSQL.
- **Data Plane (API Gateway)**: High-throughput, low-latency reactive gateway (Spring Cloud Gateway) that routes requests, checks Redis-backed rate limits, and validates credentials.

Refer to the root `projectDiscussion.md` for full design and architecture details.
