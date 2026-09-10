# Product Search & Recommendation Engine

A backend system demonstrating full-text search, real-time data synchronization, caching, and collaborative-filtering recommendations — built with Spring Boot, PostgreSQL, Elasticsearch, Redis, and Kafka.

This project was built as a companion to a concurrent seat-booking system, deliberately covering a different half of backend engineering: data-intensive, read-heavy, eventually-consistent systems rather than transactional, lock-heavy ones.

---

## Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| Language / Framework | Java 17, Spring Boot 3.3.4 | Core application |
| Relational DB | PostgreSQL | System of record |
| Search | Elasticsearch 8.15.0 | Full-text search, filtering, autocomplete |
| Cache | Redis 7.4 | Cache-aside for search results |
| Messaging | Apache Kafka (KRaft mode) | Event-driven index synchronization |
| Build | Maven | Dependency management, build |
| Load Testing | k6 | Concurrency and latency verification |
| Data Seeding | Java Faker (datafaker) | Realistic synthetic product/interaction data |

---

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Client    │────▶│ Spring Boot  │────▶│  PostgreSQL │  (system of record)
└─────────────┘     │   REST API   │     └──────┬──────┘
                     └──────┬───────┘            │
                            │                     │ AFTER_COMMIT
                            ▼                     ▼
                     ┌──────────────┐     ┌─────────────┐
                     │    Redis     │     │    Kafka    │
                     │ (search      │     │ (product-   │
                     │  cache)      │     │  updates)   │
                     └──────────────┘     └──────┬──────┘
                                                  │
                                                  ▼
                                          ┌───────────────┐
                                          │   Consumer    │
                                          │ (reindex +    │
                                          │  cache evict) │
                                          └───────┬───────┘
                                                  │
                                                  ▼
                                          ┌───────────────┐
                                          │ Elasticsearch │
                                          │ (search index)│
                                          └───────────────┘
```

**Data flow for a product update:**
1. Client sends `PUT /api/products/{id}`
2. `ProductServiceImpl` updates PostgreSQL inside a `@Transactional` method
3. An internal Spring event is published (not yet sent to Kafka)
4. **Only after the transaction commits**, a `@TransactionalEventListener(phase = AFTER_COMMIT)` bridge forwards the event to Kafka — this closes a real dual-write race condition (see *Key Design Decisions* below)
5. `ProductUpdateConsumer` picks up the Kafka message, re-verifies PostgreSQL, reindexes the specific document in Elasticsearch, and invalidates the Redis search cache

---

## Features

- **Full-text search** — fuzzy, typo-tolerant multi-match search across product name and description, with category and price-range filtering, pagination
- **Autocomplete** — Elasticsearch completion suggester with word-level tokenization (matches on any word in a multi-word product name, not just the first)
- **Redis caching** — cache-aside pattern on search results, 7-minute TTL, JSON serialization with type metadata
- **Event-driven index sync** — Kafka-based pipeline keeps Elasticsearch consistent with PostgreSQL after writes, with a correctly-ordered publish-after-commit design
- **Collaborative filtering recommendations** — item-based cosine similarity over weighted user interactions (view/cart/purchase/rating)
- **Three-tier cold-start handling**:
  - Tier 1: global trending products (user has no interaction history)
  - Tier 2: category-based fallback (user has history, but collaborative filtering found no similarity signal — see *Item Cold-Start* below)
  - Tier 3: trending-with-exclusion as a final safety net
- **Full CRUD** for Products, Categories, and Users with validation, business-rule enforcement (e.g., a Category with existing Products cannot be deleted), and consistent error responses

---

## API Reference

### Products
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/products` | Create a product |
| GET | `/api/products/{id}` | Get product by ID |
| GET | `/api/products` | List all products |
| PUT | `/api/products/{id}` | Update a product |
| DELETE | `/api/products/{id}` | Delete a product |

### Categories
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/categories` | Create a category |
| GET | `/api/categories/{id}` | Get category by ID |
| GET | `/api/categories` | List all categories |
| PUT | `/api/categories/{id}` | Update a category |
| DELETE | `/api/categories/{id}` | Delete (blocked with `409` if products reference it) |

### Users
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/users` | Create a user |
| GET | `/api/users/{id}` | Get user by ID |
| GET | `/api/users` | List all users |
| PUT | `/api/users/{id}` | Update a user |
| DELETE | `/api/users/{id}` | Delete a user |

### Interactions
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/interactions` | Log a VIEW / CART / PURCHASE / RATING |
| GET | `/api/interactions/user/{userId}` | Interactions by user |
| GET | `/api/interactions/product/{productId}` | Interactions by product |

### Search & Discovery
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/search?q={query}&category={cat}&minPrice={min}&maxPrice={max}&page={p}&size={s}` | Fuzzy search with filters |
| GET | `/api/search/autocomplete?prefix={prefix}` | Autocomplete suggestions |
| GET | `/api/recommendations/{userId}?limit={n}` | Personalized recommendations |

---

## Key Design Decisions & Tradeoffs

**Elasticsearch document denormalization.** `ProductDocument` duplicates the category *name* directly (rather than referencing it by ID, as PostgreSQL does) because Elasticsearch doesn't do cheap joins across indices. This trades storage duplication for query simplicity and speed — the right tradeoff for a read-heavy search workload.

**`text` vs. `keyword` field mapping.** `name`/`description` are mapped as `text` (analyzed, tokenized, supports fuzzy/partial matching). `categoryName`/`tags` are mapped as `keyword` (exact-match only) since they're used for filtering and aggregation, not full-text search. `name` also has a `.keyword` multi-field for exact sort/aggregation without duplicating data.

**Dual-write race condition (found and fixed).** Initially, Kafka events were published inside the same `@Transactional` method that wrote to PostgreSQL, immediately after `save()`. Since `@Transactional` doesn't actually commit until the whole method returns, the Kafka consumer could read PostgreSQL *before* the transaction committed — under read-committed isolation, it would see stale data. This was diagnosed by comparing an Elasticsearch document's fields against the PostgreSQL row after an update and finding a stale `description` alongside a fresh `price`. Fixed using `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, which guarantees the Kafka publish only happens once the transaction has definitely committed.

**Cache invalidation strategy.** A single product can appear in many different cached search result sets. Rather than trying to track exactly which cache entries reference a given product (genuinely complex), the whole `productSearch` cache is cleared on any product update. This is a deliberate correctness-over-hit-rate tradeoff: better to serve a fresh result than a stale one, and search queries are cheap enough to recompute.

**Item-based collaborative filtering via cosine similarity**, computed at request time rather than precomputed offline. This is a legitimate, correct, explainable *starting point* — not a claim of production scale. At millions of items, a real system would precompute an item-similarity matrix offline (matrix factorization / ALS) rather than compute pairwise similarity per request.

**Item cold-start (a nuance beyond the original plan).** Collaborative filtering requires shared users between two products to compute similarity. A product interacted with by only one user is structurally isolated — no similarity signal exists, regardless of how much interaction history that one user has. This surfaced during manual testing and led to the Tier 2 category-based fallback, which is distinct from the more commonly discussed *user* cold-start problem.

---

## Load Testing Results

Tested with k6: 20 concurrent virtual users against search endpoints, 10 against recommendations, 30 seconds per scenario.

| Scenario | Avg | p90 | p95 | Error Rate |
|---|---|---|---|---|
| Search (varied queries) | 5.4ms | 4.2ms | 5.1ms | 0% |
| Search (cached, repeated query) | 2.6ms | 3.5ms | 3.9ms | 0% |
| Recommendations | 24.6ms | 28.5ms | 31.3ms | 0% |

~68 requests/sec sustained across all scenarios combined, zero failures at this configuration.

**A real bug was found via load testing that manual testing missed:** a Redis cache serialization regression (`ClassCastException: LinkedHashMap cannot be cast to SearchResponseDTO`) only manifested under concurrent load once a stale/misconfigured cache entry was read by a different thread than the one that wrote it. Single-request manual testing never exposed this. Diagnosing it required correlating k6's failure pattern (fast responses, high error rate) with server-side exception logs, which pointed to a Redis serializer configuration that hadn't been using type metadata correctly.

**Recommendations are the slowest endpoint by a clear margin**, consistent with it being the only endpoint doing real in-memory computation rather than delegating to a purpose-built index. At current scale (1,362 interactions, 50 users) this is a non-issue; at higher scale, precomputing similarity or caching per-user recommendations with a shorter TTL would be the next optimization.

---

## Setup

### Prerequisites
- JDK 17+
- Docker Desktop
- Maven (wrapper included)

### 1. Start infrastructure
```
docker compose up -d
```
This starts PostgreSQL, Elasticsearch, Redis, and Kafka (KRaft mode, no Zookeeper).

### 2. Configure
Copy `application.properties.example` to `application.properties` (or edit directly) and set your PostgreSQL credentials.

### 3. Seed data
Place a product CSV at `src/main/resources/data/products.csv` (columns: `Product Name`, `Price` at minimum). On first run:
- `ProductIndexInitializer` creates the Elasticsearch index with mapping
- `ProductSeeder` imports products, resolves categories via keyword matching, and generates realistic descriptions/tags via Faker
- `ElasticsearchSyncRunner` bulk-syncs PostgreSQL → Elasticsearch
- `InteractionSeeder` generates 50 synthetic users with category-weighted interaction histories, for realistic recommendation testing

### 4. Run
```
mvnw.cmd spring-boot:run
```
App runs on port `8082`.

### 5. Load test (optional)
```
k6 run load-test.js
```

---

## Known Limitations

- Recommendation computation is O(userInteractedItems × totalItems) per request — fine at current scale, would need precomputation at production scale
- Cache invalidation is coarse-grained (whole-cache clear on any product change), trading hit-rate for simplicity and correctness
- Item cold-start (isolated products with only one interacting user) falls back to category-based ranking rather than true collaborative filtering
- Source dataset's category/description/tag fields were synthetically generated (see *Lessons Learned*) rather than sourced from real product copy

---

## Lessons Learned

- **`@Transactional` scope matters for more than writes.** Any code path touching a lazy-loaded JPA association — including reads for a REST response, or reads to bulk-export data elsewhere — needs an open Hibernate session, not just the original write operation.
- **Event publishing inside a transaction races the commit.** Publishing to Kafka (or any external system) before a surrounding transaction commits can let downstream consumers observe stale data. `@TransactionalEventListener(phase = AFTER_COMMIT)` is the idiomatic Spring fix.
- **Free/synthetic datasets often have unusable auxiliary fields.** The source CSV's description and category columns were placeholder/randomized data — worth inspecting actual field *content*, not just schema, before building search relevance on top of it.
- **Load testing surfaces bugs that manual testing structurally cannot.** A cache serialization issue only appeared once concurrent reads/writes were possible — proof that "it works when I test it" and "it works under realistic traffic" are different claims.
- **API client library versions matter more than expected.** The official Elasticsearch Java client's query-builder API shape changed meaningfully across 8.x minor versions (e.g., `RangeQuery` moving from an untyped builder to a tagged-union of typed variants) — verifying against the exact installed version, not general familiarity with the library, was necessary more than once.

---

## Resume Bullet Points

- Built a product search engine using Elasticsearch with fuzzy matching, category/price filtering, and relevance ranking across 10,000+ indexed products
- Designed an event-driven synchronization pipeline with Kafka to propagate product updates from PostgreSQL to Elasticsearch, identifying and resolving a dual-write race condition using transactional event listeners
- Implemented Redis caching with a cache-aside pattern, verified via k6 load testing (20 concurrent users, 0% error rate, ~68 req/s sustained)
- Built an item-based collaborative filtering recommendation engine using cosine similarity, with a three-tier cold-start fallback strategy covering both user and item cold-start cases
- Diagnosed a concurrency-specific Redis serialization bug via load testing that was undetectable through manual single-request testing