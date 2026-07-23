# Spring Reactive Bulkhead PoC

A hands-on proof of concept that reproduces a **cascading failure through a shared resource pool** in
a high-concurrency ticketing platform, and demonstrates how the **Bulkhead pattern** contains the
blast radius so that one slow, non-critical dependency cannot take the whole page down.

Part of the **"Resiliencia en Spring WebFlux"** series (an open, ongoing arc):

- Parts 1–2 — [Cache Stampede](https://github.com/AReinaGon/spring-reactive-cache-stampede): stop your
  own service from *starting* the fire (coalescing + distributed lock).
- **Part 3 — Bulkhead (this repo):** stop *another* service's slowness from spreading to yours.
- Part 4 — Circuit Breaker: stop calling a dependency that is already down so it can recover.
- …more to come (rate limiting, timeouts/retries, load shedding, graceful degradation).

> Same universe as the cache stampede PoC: the Bad Bunny on-sale at 23:59. This time the database is
> fine. The thing that breaks the page is a **non-critical widget**.

---

## The realistic topology

The service under test is **not** a monolith that owns everything. It is the **event-page BFF**
(backend-for-frontend / aggregator): the service the web and mobile apps call to render an event
page. By its nature it fans out to several downstreams of **mixed criticality**:

- **Availability** (seats + price) — **critical**. This is, narratively, the very service from
  Parts 1–2. If it is not shown, the user cannot buy.
- **Recommendations** ("related events" sidebar) — **non-critical**. A nice-to-have widget.

The recommendation engine and the availability service are their **own** services; the BFF only
*calls* them. That is exactly where a bulkhead belongs: it isolates the **caller's** resources spent
on each downstream.

---

## The problem this reproduces

During the on-sale, the recommendations service gets **slow** (not even down, just slow). If both
downstream calls share the **same resource budget** (one Netty connection pool / one concurrency
limit), the slow recommendations calls **hold their connections while they wait**, the shared pool
drains, and now the **availability** call — which the fast availability service would answer in
20 ms — cannot get a connection either. A non-critical sidebar has blanked out seats and prices. That
is a cascading failure through a shared resource, the "first domino" that Part 2 ended on.

The **Bulkhead pattern** gives each dependency its own compartment (its own connection pool **plus** a
bounded-concurrency limiter with fail-fast). When recommendations floods its own compartment, the
overflow is rejected instantly and falls back to an empty sidebar, while availability keeps its own
connections intact and the page still renders seats and prices.

```
NO BULKHEAD (shared budget)                    BULKHEAD (isolated compartments)

 availability ─┐                               availability ─┐  [availability pool: 20] ─► OK (20 ms)
 recommendations ─┤ one shared pool (N)        recommendations ─┤ [recs pool + semaphore: 10] ─► full → fallback
               ├─► slow recs hold every slot                  │
               ▼                                              ▼
   availability starves ─► PAGE HAS NO SEATS     availability unaffected ─► PAGE STILL RENDERS
```

> **Not just a missing timeout.** A timeout on the recommendations call fixes *that* request's
> composition, but under sustained load it does not stop recommendations from **exhausting the shared
> pool** in the window before timeouts fire. The bulkhead caps how many connections recommendations
> can ever hold. Timeout and bulkhead are complementary; the PoC shows both.

---

## Modules

| Module | Role |
|--------|------|
| `reactive-bulkhead` | The event-page BFF. Two variants: **shared budget** (fragile) vs **isolated bulkheads** (resilient), both aggregating availability + recommendations via `WebClient`. |
| `downstream-simulator` | A tiny reactive app that fakes the two downstreams with tunable **latency** and **error rate** knobs, so a load test can make recommendations "go slow" on demand. |

---

## Evidence (two layers, like the cache stampede)

1. **Deterministic integration test** (`BulkheadIsolationTest`): an **in-JVM Reactor load generator**
   fires N truly-concurrent renders against a **Reactor Netty stub** downstream with a slow
   recommendations knob, and asserts the **critical** path stays healthy under the bulkhead and starves
   under the shared budget. Hard numbers, no external load generator, no Docker. ✅ Done.
2. **JMeter sustained load** (`jmeter/bulkhead-event-page.jmx`): **open-loop** traffic at a fixed
   arrival rate (200 req/s) with the recommendations degradation injected mid-run. The star graph is
   JMeter's own *Codes Per Second*: under the shared budget a line of HTTP 500s appears the second the
   widget slows down; with the bulkhead it never does. ✅ Done.

---

## Build, run and test

> **Prerequisite:** JDK 25 (Corretto). Maven may be running on an older JDK, so build with `JAVA_HOME`
> pointing at 25 (e.g. `JAVA_HOME=/path/to/corretto-25 mvn ...`). The tests need **no Docker**; Docker
> is only for running the apps in containers.

### Run the tests

```bash
cd reactive-bulkhead
mvn test        # ContextLoadsTest (wiring boots) + BulkheadIsolationTest (the deterministic proof)
```

`BulkheadIsolationTest` starts an in-process Reactor Netty stub for the two downstreams, shrinks the
shared resource to 2 (per-downstream stays ample), degrades recommendations to 600 ms, and fires 100
truly-concurrent renders per layer and variant, counting how many **critical** (availability) responses
land within a 1 s SLA. Representative result (the fragile variant stays in single digits, the isolated
one is 100/100 on every run):

```
layer      | SHARED (fragile) critical OK | ISOLATED (bulkhead) critical OK
scheduler  |   8/100                      | 100/100
pool       |   8/100                      | 100/100
semaphore  |   3/100                      | 100/100
```

### Run the apps (Docker)

```bash
# build both jars first (Corretto 25), then bring up BFF + simulator:
(cd downstream-simulator && mvn -DskipTests package)
(cd reactive-bulkhead    && mvn -DskipTests package)
docker compose -f deploy/docker/docker-compose.yml up --build -d
#   BFF on :8080, simulator on :9090   (if 8080 is taken:  HOST_PORT=8081 docker compose ... up -d)
```

Or run the jars directly:

```bash
java -jar downstream-simulator/target/downstream-simulator-1.0.0-SNAPSHOT.jar            # :9090
SERVER_PORT=8081 java -jar reactive-bulkhead/target/reactive-bulkhead-1.0.0-SNAPSHOT.jar  # :8081
```

### Run the apps (Kubernetes)

If you have a local Kubernetes cluster (like Docker Desktop or Kind):

```bash
# Build the images into your local Docker daemon
docker build -t downstream-simulator:local ./downstream-simulator
docker build -t reactive-bulkhead:local ./reactive-bulkhead

# If using Kind, load them into the cluster:
# kind load docker-image downstream-simulator:local
# kind load docker-image reactive-bulkhead:local

# Apply the manifests
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/app.yaml

# The BFF service is a LoadBalancer on port 8080
```

### Drive the demo

```bash
# healthy: full page with recommendations
curl "http://localhost:8080/api/events/bad-bunny-2027/page?mode=semaphore&isolated=false"
# degrade the non-critical downstream (the "trick" that provokes the leak):
curl -X POST "http://localhost:9090/control?target=recommendations&latencyMs=3000"
# then compare isolated=false vs isolated=true under load (see jmeter/ for the sustained ramp, Phase 2)
```

---

## Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 25 (Corretto) | base language |
| Spring Boot | 4.0.6 | framework |
| Spring WebFlux + Reactor Netty | bundled | non-blocking reactive HTTP + `WebClient` |
| Project Reactor | bundled | reactive operators, `Schedulers`, `.timeout()` |
| Resilience4j | 2.3.0 (`bulkhead` + `reactor` + `micrometer`, **core, not the Boot-3 starter**) | reactive semaphore **Bulkhead** (layer 3) + Actuator metrics |
| Actuator + Micrometer | bundled | expose `resilience4j_bulkhead_available_concurrent_calls` |

| JMeter | 5.6.3 | Phase 2 sustained-load evidence (throughput over time) |

---

## Status

✅ **Phase 1 done** — deterministic integration test (see *Run the tests*): fragile in single digits,
isolated 100/100.

✅ **Phase 2 done (JMeter, all via Docker).** Sustained **open-loop** load (200 req/s) with
recommendations degraded mid-run. Both variants degrade in latency to ~305 ms (the fallback timeout),
but only the **shared budget also sheds critical errors** — the bulkhead eliminates them:

```
critical error % during degradation   |  shared (fragile)  |  isolated (bulkhead)
semaphore                             |       15.6 %       |        0 %
pool                                  |        2.2 %       |        0 %
scheduler                             |        0.0 %       |        0 %   (graceful at this rate)
```

Key load-testing lesson: a **closed-loop** generator self-throttles when responses slow, so it never
oversubscribes the shared resource — you must drive **open-loop** (a Constant Throughput Timer) to
reproduce resource exhaustion.

To reproduce the load test yourself using the provided JMeter plan:

```bash
jmeter -n -t jmeter/bulkhead-event-page.jmx -Jport=8080 -Jmode=semaphore -Jisolated=false \
  -Jthreads=250 -Jrampup=5 -Jduration=70 -Jthinkms=0 \
  -l out.jtl -e -o report/
```

⏭️ **Next — Phase 3:** write the Substack article (Part 3) with the evidence in hand.
