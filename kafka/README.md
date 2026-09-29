# Kafka (Phase 16 Step 2 — infrastructure and topic reference)

**Not installed by this repository, and not required for `mvn test`.**
booking-service's own tests use an in-process, real (not mocked) broker via
`spring-kafka-test`'s `@EmbeddedKafka` — the same "real infrastructure over
a mock" choice already made for Redis (`embedded-redis`, see
`docs/architecture.md`'s "Redis testing" note) — so the whole suite,
including every Kafka-touching test, runs with no external broker at all.
This directory is for anyone who wants to run booking-service against a
**real**, standalone local Kafka, e.g. to watch `OutboxPublisher` actually
publish and to inspect a message with `kafka-console-consumer`.

This machine has neither Docker nor an installed WSL distribution (the
same constraint already documented for Redis and for Prometheus/Grafana in
`monitoring/README.md`), so — matching that same precedent exactly —
"download the official binary and run it directly" is the supported local
path here too, not a Docker Compose file.

## What this does *not* do

- Does not install Kafka for you.
- Does not add Kafka as a Gateway route — nothing here is ever reached
  through the Gateway; producers and (future) consumers talk to the broker
  directly, the same boundary Prometheus/Grafana already use for metrics.
- Does not create topics automatically. booking-service's own application
  code never creates a topic at startup (`docs/architecture.md` §47.4/§48:
  "prefer infrastructure/configuration over application startup code that
  silently creates production topics") — topics are provisioned by the
  commands below, once, as an infrastructure step.
- Does not run a consumer of any kind. No event catalogued in
  `docs/architecture.md` §47.2 has a real consumer yet — see that
  document's own "not yet implemented" list.

## Prerequisites

- A local Kafka binary. This repository does not pin a specific version —
  install a current stable release from
  [kafka.apache.org/downloads](https://kafka.apache.org/downloads) (verify
  the version there rather than trusting a hardcoded number here). Any
  release using **KRaft mode** (no separate Zookeeper process) is simplest
  for a single local broker.

## Running a single local broker on Windows (KRaft mode)

Kafka ships with both `bin/*.sh` (Unix) and `bin\windows\*.bat` (Windows)
launcher scripts in the same download — no separate Windows build, unlike
Prometheus/Grafana.

1. Download and extract the release anywhere, e.g. `C:\kafka`.
2. Generate a cluster id and format the KRaft storage directory — a one-time
   step per install, not per run (Git Bash):

   ```bash
   cd /c/kafka
   KAFKA_CLUSTER_ID=$(bin/kafka-storage.sh random-uuid)
   bin/kafka-storage.sh format -t "$KAFKA_CLUSTER_ID" -c config/server.properties --standalone
   ```

   **Version note (confirmed against the real 4.3.1 release used for this
   step's live verification, §49.8):** the layout described by older Kafka
   docs — a separate `config/kraft/server.properties` — no longer exists as
   of 4.x. `config/server.properties` is already a combined-mode KRaft
   config (`process.roles=broker,controller`, listeners on `9092`/`9093`
   out of the box); use it directly. `kafka-storage.sh format` also now
   *requires* one of `--standalone`, `--initial-controllers`, or
   `--no-initial-controllers` — `--standalone` is correct for the
   single-local-broker case this file describes. Without it, `format`
   fails with "Because `controller.quorum.voters` is not set on this
   controller, you must specify one of the following...". The default
   `log.dirs` (`/tmp/kraft-combined-logs`) can be left as-is under Git
   Bash/WSL, or pointed at a Windows-native path (e.g.
   `C:/kafka/kraft-logs`, written as `/c/kafka/kraft-logs` in the
   properties file) to avoid depending on Git Bash's `/tmp` mapping.

3. Start the broker (Git Bash):

   ```bash
   cd /c/kafka
   bin/kafka-server-start.sh config/server.properties
   ```

   PowerShell (the same script, via the `.bat` launchers):

   ```powershell
   cd C:\kafka
   .\bin\windows\kafka-server-start.bat .\config\server.properties
   ```

   The default config listens on `localhost:9092` — the same default
   booking-service's own `KAFKA_BOOTSTRAP_SERVERS` fallback already assumes
   (see `backend/booking-service/src/main/resources/application.yml`). Startup
   logs a harmless `MalformedURLException`/`Invalid URL C:/…/tools-log4j2.yaml`
   from log4j2 trying to parse a Windows drive letter as a URI scheme when
   launched from Git Bash — logging still works via its fallback; this is
   cosmetic noise, not a failure.

4. Create the four approved topics (see below) — once, after the broker is
   up:

   ```bash
   cd /c/kafka
   for t in eventtick.booking eventtick.payment eventtick.catalog eventtick.user; do
     bin/kafka-topics.sh --create --topic "$t" --bootstrap-server localhost:9092 \
       --partitions 3 --replication-factor 1
   done
   ```

5. Run booking-service normally (`KAFKA_BOOTSTRAP_SERVERS` defaults to
   `localhost:9092`, matching step 3). To watch a published event:

   ```bash
   cd /c/kafka
   bin/kafka-console-consumer.sh --topic eventtick.booking --bootstrap-server localhost:9092 --from-beginning
   ```

   Each line is one event envelope's full JSON — plain `StringSerializer`
   on both key and value (`docs/architecture.md` §47.5's "inspectable"
   requirement), so no consumer-side Java class or schema registry is
   needed to read it.

## The four topics

Approved in `docs/architecture.md` §47.4; only `eventtick.booking` has a
real producer as of this step (`BookingCreated`, via `OutboxPublisher`) —
the other three are provisioned now so the topic layout matches the
approved design from the start, not created reactively per producer later.

| Topic | Owning producer | Partitions | Replication (local) | Retention |
|---|---|---|---|---|
| `eventtick.booking` | booking-service | 3 | 1 | 7 days (default) |
| `eventtick.payment` | payment-service (not yet a producer) | 3 | 1 | 7 days (default) |
| `eventtick.catalog` | catalog-service (not yet a producer) | 3 | 1 | 7 days (default) |
| `eventtick.user` | user-service (not yet a producer) | 3 | 1 | 7 days (default) |

- **Partitions (3):** a reasonable, commonly-used small default for this
  scale — not a tuned value. Ordering is guaranteed per-partition, keyed by
  `aggregateId` (`docs/architecture.md` §47.7), so every event for one
  aggregate lands in the same partition regardless of partition count.
- **Replication factor (1):** the only value possible with a single local
  broker. **This is not production fault tolerance** — a real deployment
  needs a multi-broker cluster and a replication factor greater than 1; the
  single-local-broker setup above is for development and manual
  verification only.
- **Retention (7 days):** Kafka's own default (`retention.ms`), left
  unchanged rather than tuned — matching how `payment.default-currency`'s
  own default was documented as "a placeholder default only, not a stated
  business requirement." Long enough for a consumer outage to recover from
  and replay from, per `docs/architecture.md` §47.6/§47.10, without being
  unbounded on a laptop's disk.

See `docs/architecture.md` §48 for what's actually implemented so far
(the outbox, the publisher, `BookingCreated`) versus what remains design
only.
