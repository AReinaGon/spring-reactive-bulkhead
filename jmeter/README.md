# JMeter load-test plans — Bulkhead PoC

Sustained-load plans that reproduce a **cascading failure through a shared resource pool** and show
the **Bulkhead** containing it. Same ramp methodology as the cache stampede's `local-collapse.jmx`
(scheduler-driven thread group + think-time timer + fine report granularity for time series).

| Plan | Drives | Compares |
|------|--------|----------|
| `bulkhead-event-page.jmx` | `GET /api/events/{eventId}/page` at a fixed arrival rate | shared-budget (fragile) vs isolated bulkheads, while the recommendations downstream is degraded mid-run |

The experiment has three time windows: healthy → recommendations degraded (latency injected via the
`downstream-simulator` control endpoint) → optional recovery. The star graph is the **critical path
(availability/page) success/latency over time**: one line collapses with the widget, the other stays
flat.

```bash
# one combo (mode x isolated); run the six of them to get the full picture
jmeter -n -t jmeter/bulkhead-event-page.jmx \
  -Jport=8081 -Jmode=semaphore -Jisolated=false \
  -Jthreads=250 -Jrampup=5 -Jduration=70 -Jthinkms=0 \
  -Jjmeter.reportgenerator.overall_granularity=2000 \
  -l out.jtl -e -o report/
```

The plan drives **open-loop** load: a `ConstantThroughputTimer` fixes the arrival rate (12000/min =
200 req/s) so that when recommendations is degraded mid-run the shared resource oversubscribes — a
closed-loop generator self-throttles and never reproduces the leak. Degrade recommendations at the
chosen instant via the simulator: `curl -X POST "http://localhost:9090/control?target=recommendations&latencyMs=3000"`.

✅ Executed in Phase 2. Results, the exact commands, and the evidence are in
`docs/fase-2-pruebas-carga-evidencias.md`. Headline: under the shared budget the critical path sheds
errors during degradation (semaphore 15.6 %, pool 2.2 %); per-downstream bulkheads keep it at 0 %.
