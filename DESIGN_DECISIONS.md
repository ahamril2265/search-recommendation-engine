# Design Decisions — Product Search & Recommendation Engine

A read-heavy, eventually consistent service: PostgreSQL is the source of truth, Elasticsearch serves search, Redis caches results, and Kafka keeps the search index in sync after every write.

## Stack

Java 17 · Spring Boot 3.3 · PostgreSQL · Elasticsearch 8.15 · Redis 7.4 · Kafka 3.8 (KRaft) · k6

## Decisions

| Decision | Chosen over | Why | Trade-off |
| --- | --- | --- | --- |
| PostgreSQL as source of truth, Elasticsearch for search only | Searching PostgreSQL directly | Elasticsearch provides fuzzy matching, relevance ranking and autocomplete. PostgreSQL stays the one place data is authoritative. | Two stores to keep in sync. |
| Kafka between the database and the index | Updating Elasticsearch directly after each write | A slow or unavailable Elasticsearch doesn't fail the user's write, and events can be retried or replayed. | One more system to run; the index lags slightly. |
| Publish after commit (`@TransactionalEventListener(AFTER_COMMIT)`) | Publishing inside the transaction | Publishing inside the transaction let the consumer read rows before they were committed. Found by spotting a stale description next to a fresh price. | A crash between commit and publish can drop an event (see next steps). |
| Consumer re-reads PostgreSQL by product ID | Trusting the event payload | The index always reflects the latest committed state, and duplicate or out-of-order events are harmless. | One extra read per event. |
| Kafka in KRaft mode | Kafka with ZooKeeper | Current Kafka architecture; one fewer service. | — |
| Redis cache-aside, 7-minute TTL, whole search cache cleared on any product update | Tracking which cached results contain each product | Correctness over hit rate: precise invalidation is complex, and searches are cheap to recompute. | Every product edit empties the search cache. |
| Denormalised index documents (category name copied in) | Referencing category by ID | Elasticsearch has no cheap joins; copying keeps queries simple and fast. | Renaming a category requires reindexing its products. |
| `text` mapping for name and description, `keyword` for category and tags | One type everywhere | `text` is tokenised for fuzzy search; `keyword` is exact for filters and aggregations. | Slightly larger index. |
| Item-based cosine similarity computed per request | Offline precomputation (matrix factorisation) | A correct, explainable baseline at this scale (50 users, 1,362 interactions), with popularity fallbacks for cold start. | Cost grows with items × user history. |
| k6 load testing | Manual testing only | Surfaced a Redis serialisation error that only appeared under concurrent load. | — |

## Measured (k6, 20 virtual users, 30 s)

Search 5.4 ms average · cached search 2.6 ms · recommendations 24.6 ms · 0% errors.

## Next steps

- Transactional outbox (or CDC with Debezium) so no event is lost if the app crashes between commit and publish.
- Precompute recommendations offline once the catalogue grows.
- Finer-grained cache invalidation if hit rate becomes a concern.
