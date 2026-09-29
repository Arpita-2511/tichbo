# Monitoring (Phase 14, Steps 2–5)

**Locally verified (Phase 14, Step 5).** A real Prometheus (v3.15.0) was
installed and run against this exact `prometheus.yml`, and a real Eventtick
service (`gateway-service`, port 8080) was started alongside it. Confirmed:
Prometheus's web UI on **http://localhost:9090**; the `eventtick-services`
job recognized with all four configured targets; `gateway-service` reported
**UP** by Prometheus itself (not inferred); `user-service`/`catalog-service`/
`booking-service` reported **DOWN** — correctly, because those three
couldn't be started in that environment (no `DB_PASSWORD` for their
PostgreSQL connection), not because of any Prometheus or scrape-config
problem; and real, changing metric samples (`http_server_requests_seconds_count`
increasing across scrapes) were observed. See `docs/architecture.md` §46.6
for the full results, including the three targets' exact down-reason.

**Update — Phase 15 Step 4 (payment-service added, all targets UP).**
`payment-service` (port 8084, Phase 15) was not in the original four-target
config: running Prometheus against the unmodified file showed exactly four
targets and none for port 8084 — a real coverage gap, fixed by adding one
target entry. Re-verified live: `promtool check config` passes, and with all
five services running (against a real PostgreSQL 18, so the earlier
missing-`DB_PASSWORD` limitation no longer applies) Prometheus reported
**all five targets UP** (`gateway-service`, `user-service`,
`catalog-service`, `booking-service`, `payment-service`); Prometheus held
real `payment-service` series (e.g. `http_server_requests_seconds_count`
for `POST /api/payments`); Grafana's datasource health check returned OK,
its `service` variable listed all five services, and a panel query for
`payment-service` returned data. See `docs/architecture.md` §25.4.

The local monitoring flow, end to end:

```
Eventtick services  --/actuator/prometheus-->  Prometheus  --PromQL-->  Grafana
   (Phase 14, Step 1)      (this dir,             (this dir,
                             Step 2)                grafana/, Step 3)
```

`prometheus.yml` in this directory configures a **local Prometheus server**
to scrape the five Eventtick backend services' `/actuator/prometheus`
endpoints (added in Phase 14, Step 1). `grafana/` configures a **local
Grafana server** that queries that Prometheus and renders one dashboard —
see `grafana/README.md` for Grafana's own setup and run instructions; this
file covers Prometheus (Step 2) only, unchanged from before Step 3.

This directory does not contain Prometheus or Grafana themselves, and
nothing here is started automatically by any Eventtick service — you run
each of them yourself, against the config files here, when you want to look
at metrics locally.

See `docs/architecture.md` §46 for the full design (why static targets, why
Prometheus talks to each service directly instead of through the Gateway,
why Grafana talks only to Prometheus and never to the Gateway or the
services directly, what's still not implemented).

## What this does *not* do

- Does not install Prometheus or Grafana for you.
- Does not add Prometheus, Grafana, or any monitoring traffic as a Gateway
  route — Prometheus scrapes `localhost:8080/8081/8082/8083` directly, and
  Grafana queries Prometheus directly; neither goes through the Gateway.
- Does not include alerting, Docker/Compose, or a custom
  `rate_limit.requests` metric — those are separate, later Phase 14 steps.

## Prerequisites

- The five backend services must actually be running (see each service's
  own `README.md` under `backend/`) — Prometheus scrapes real, live
  `/actuator/prometheus` endpoints; it has nothing to read if the services
  aren't up.
- A local Prometheus binary. This repository does not pin a specific
  version — install a current stable release from
  [prometheus.io/download](https://prometheus.io/download/) (the official
  releases page lists the latest stable build for Windows/Linux/macOS;
  verify the version there rather than trusting a hardcoded number here).

## Running Prometheus on Windows

Prometheus ships as a plain executable, not a service — there is no
`systemd` on Windows and this project has no Docker/Compose setup yet, so
"download and run the .exe" is the only supported path for this step.

1. Download the **Windows** build (`prometheus-<version>.windows-amd64.zip`)
   from the downloads page above and extract it anywhere, e.g.
   `C:\prometheus`.
2. From a terminal (PowerShell or Git Bash), run it against this repo's
   config — point `--config.file` at this file rather than copying it
   into the Prometheus install directory, so it always reflects what's in
   version control:

   PowerShell:
   ```powershell
   cd C:\prometheus
   .\prometheus.exe --config.file="C:\Users\ADMIN\Desktop\tichboo\monitoring\prometheus.yml"
   ```

   Git Bash:
   ```bash
   cd /c/prometheus
   ./prometheus.exe --config.file=/c/Users/ADMIN/Desktop/tichboo/monitoring/prometheus.yml
   ```

   (Do **not** use a Linux-style `./prometheus` with no extension, and don't
   expect a `prometheus` systemd unit — this is a Windows executable started
   directly in the foreground.)
3. Prometheus's own UI is reachable at **http://localhost:9090** (its
   default port — unrelated to and never proxied by the Eventtick Gateway).
4. Under **Status → Targets** in that UI, all five `eventtick-services`
   targets should show **UP** once the corresponding backend service is
   running. A target with no matching service running shows **DOWN** with a
   connection-refused error — that's expected, not a Prometheus problem.
5. Stop it with `Ctrl+C` in the terminal it's running in.

## Next: Grafana

Once Prometheus is running and its targets are UP, `grafana/README.md`
covers running Grafana against this same Prometheus to view the
**Eventtick Service Overview** dashboard.

## Scope

- Scrape interval: `15s` (a reasonable local-development default, not tuned
  for production).
- Targets are static (`localhost:8080`–`8083`) — no service discovery.
  Deploying these services anywhere other than localhost (a different host,
  a container, multiple instances) means editing `prometheus.yml`'s targets
  by hand; that's a deliberate limitation of this step, not an oversight.
- Local development scope only — no remote-write, no long-term storage
  configuration, no authentication on the scrape targets (the Actuator
  endpoints were intentionally exposed unauthenticated for exactly this
  purpose — see `docs/architecture.md` §46 and `docs/api-contracts.md`'s
  "Operational endpoints" section).
