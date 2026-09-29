# Grafana (Phase 14, Steps 3–5)

**Locally verified (Phase 14, Step 5).** A real Grafana (v13.2.2) was
installed and run on Windows against this exact provisioning config,
alongside the real Prometheus and `gateway-service` from
`monitoring/README.md`'s own Step 5 note. Confirmed via Grafana's own HTTP
API (not the browser UI, which this environment can't screenshot, but the
same API the UI itself calls): the provisioned **Prometheus** datasource
appeared automatically with `uid: prometheus`; its health check returned
`"status": "OK", "message": "Successfully queried the Prometheus API"`; the
**Eventtick Service Overview** dashboard was discovered automatically
(`GET /api/search` found it, no import step); and all five panels' exact
PromQL queries were executed through Grafana's real query engine
(`POST /api/ds/query`) and returned real, live, changing data points for
`gateway-service` — including the 5xx-error-rate panel, confirmed to rise
from `0` to a positive value after generating fresh `/actuator/health`
traffic (Redis isn't running locally, so that endpoint's aggregate health
check genuinely returns 503 — see Phase 14 Step 1's documented behavior).
Two real setup problems were found and fixed along the way; see "Known
issues, fixed in Phase 14 Step 5" below. Full results: `docs/architecture.md`
§46.6.

Grafana is the **visualization layer** on top of the local Prometheus server
set up in Step 2 (`monitoring/prometheus.yml`, `monitoring/README.md`). It
queries Prometheus with PromQL; it never talks to the five Eventtick
services directly, is never routed through the Eventtick Gateway, and is not
an Eventtick API (see `docs/architecture.md` §46.4).

```
Eventtick services  --/actuator/prometheus-->  Prometheus  --PromQL-->  Grafana
```

This directory does not contain Grafana itself, and nothing here is started
automatically. You run Grafana yourself, pointed at this directory's
provisioning config, when you want to look at dashboards locally.

## What's here

```
monitoring/grafana/
├── README.md                                  (this file)
├── provisioning/
│   ├── datasources/
│   │   └── prometheus.yml                     auto-registers Prometheus as a datasource
│   └── dashboards/
│       └── dashboards.yml                     tells Grafana where to load dashboard JSON from
└── dashboards/
    └── eventtick-service-overview.json         the one dashboard this step adds
```

Grafana's own provisioning mechanism reads `provisioning/datasources/` and
`provisioning/dashboards/` on startup — nothing needs to be clicked through
in the UI for the datasource or the dashboard to appear, **provided Grafana
is pointed at this `provisioning/` directory** (see "Running" below).

## Prerequisites

- Prometheus must be running first (Step 2 — see `monitoring/README.md`),
  scraping the five Eventtick services, for the dashboard to show real data.
  Grafana itself will start fine without Prometheus running, but its
  datasource will show as unreachable and the dashboard's panels will be
  empty.
- A local Grafana installation. This repository does not pin a specific
  version — install a current stable release from
  [grafana.com/grafana/download](https://grafana.com/grafana/download)
  (select **Windows**; verify the version there rather than trusting a
  hardcoded number here).

## Running Grafana on Windows

Grafana for Windows ships as a zip containing `bin\grafana.exe` plus a
`conf\` directory — not a Docker image, not a systemd service, and this
project has no Docker/Compose setup yet, so "run the .exe with a config file
pointed at this repo" is the supported path for this step. **As of Grafana
13.x, the executable is `grafana.exe` with a `server` subcommand** — older
Grafana versions instead shipped a separate `grafana-server.exe` with no
subcommand; check `.\bin\grafana.exe --help` if your downloaded version's
layout differs from what's below.

1. Download the **Windows** build from the page above and extract it
   anywhere, e.g. `C:\grafana`.
2. Point Grafana's provisioning at this repository. The simplest way on
   Windows is a small custom config file (Grafana ships a default
   `conf\defaults.ini`; you override just the paths you need in your own
   file, e.g. `C:\grafana\conf\eventtick.ini`):

   ```ini
   [paths]
   provisioning = C:\Users\ADMIN\Desktop\tichboo\monitoring\grafana\provisioning

   [plugins]
   # Works around a real, reproducible Windows issue found in Phase 14 Step
   # 5: Grafana's background updater tries to re-download its own bundled
   # Prometheus datasource plugin on every startup, and on Windows the file
   # delete/replace step fails ("Access is denied"), which leaves that
   # startup's Prometheus plugin type unregistered — the datasource shows up
   # in Connections -> Data sources, but every query against it fails with
   # "Plugin not registered" until Grafana is restarted (sometimes more than
   # once). Disabling this removes the failure entirely; it does not disable
   # the Prometheus datasource itself, which still ships bundled either way.
   preinstall_auto_update = false
   ```

   (Adjust the provisioning path for your own checkout.) The dashboard
   provider's own `path` setting in `provisioning\dashboards\dashboards.yml`
   also needs to point at your checkout's `monitoring\grafana\dashboards`
   folder — it's committed with an example value for this repository's
   current location; update it if your checkout lives somewhere else.

3. Start it from a terminal (PowerShell or Git Bash), pointed at that config
   — **`--homepath` must be an absolute path**; a relative one like `.`
   silently resolves against a Linux default and Grafana exits immediately
   with `could not find core plugins in directory /usr/share/grafana/public`:

   PowerShell:
   ```powershell
   cd C:\grafana
   .\bin\grafana.exe server --config="C:\grafana\conf\eventtick.ini" --homepath="C:\grafana"
   ```

   Git Bash:
   ```bash
   cd /c/grafana
   ./bin/grafana.exe server --config=/c/grafana/conf/eventtick.ini --homepath=/c/grafana
   ```

4. Open **http://localhost:3000** (Grafana's default port — unrelated to,
   and never proxied by, the Eventtick Gateway or Prometheus's own port
   9090). Default first-login credentials are `admin` / `admin`; Grafana
   will prompt you to change the password.
5. Under **Connections → Data sources**, confirm a **Prometheus** datasource
   already exists (provisioned automatically, pointed at
   `http://localhost:9090`) — you should not need to add one by hand. Open
   it and use its **Save & test** button — it should report success; if it
   instead says the plugin isn't registered, see step 2's `preinstall_auto_update`
   note and restart Grafana.
6. Under **Dashboards**, open **Eventtick Service Overview** — it should
   already be listed (provisioned automatically), no import step needed.
7. Stop Grafana with `Ctrl+C` in the terminal it's running in.

## The dashboard

**Eventtick Service Overview** — one small dashboard, five panels, built
only from metrics the existing Actuator/Micrometer setup (Phase 14, Step 1)
actually exposes. A `service` template variable (backed by Prometheus's
`service` label — see `monitoring/prometheus.yml`'s per-target labels) lets
you filter to one service or look at all five together. See
`docs/architecture.md` §46.4 for exactly which metrics back each panel and
the one documented limitation (average latency, not percentiles — no
histogram buckets are exposed).

## Known issues, fixed in Phase 14 Steps 4–5

**Step 4 — dashboard datasource reference.** Step 3's original dashboard JSON referenced its datasource as
`"uid": "${DS_PROMETHEUS}"`, with a matching `__inputs` block — the format
Grafana produces when you **export** a dashboard for sharing, meant to be
resolved by the **Dashboards → Import** wizard. That placeholder is never
substituted by file-based provisioning (the mechanism this project actually
uses, via `provisioning/dashboards/dashboards.yml`), so every panel and the
`service` variable would have loaded with no working datasource — a
dashboard that "provisioned" successfully but silently rendered nothing.
Step 4 fixed this: the datasource provisioning YAML now sets an explicit
`uid: prometheus`, and the dashboard JSON references that literal uid
directly instead of the import-only placeholder; `__inputs` was removed as
no longer relevant. This was caught by validation, not by a live Grafana run
(Grafana still isn't installed in this environment — see "Prerequisites"
above) — it's a structural inconsistency confirmed by [Grafana's own
provisioning docs and community reports of the exact same
symptom](https://community.grafana.com/t/how-to-fix-error-updating-options-datasource-named-ds-prometheus-was-not-found-in-provisioned-dashboard/46538),
not something observed running.

**Step 5 — two real setup problems, found only by actually installing and
running Grafana:**

1. **Wrong binary name.** These instructions originally said
   `bin\grafana-server.exe` — correct for older Grafana releases, but
   Grafana 13.x (the current stable release at the time of Step 5) ships
   `bin\grafana.exe` with a `server` subcommand instead. Fixed above.
2. **Bundled Prometheus plugin fails to self-update on Windows.** Grafana's
   background plugin installer tries to update its own bundled Prometheus
   datasource plugin on every startup; on Windows the old file can't be
   deleted while in use ("Access is denied"), and that failure left the
   Prometheus plugin type completely unregistered for that run — the
   datasource still appeared in the UI, but every query against it failed
   with `"Plugin not registered"`. Setting `preinstall_auto_update = false`
   (see step 2 above) stops Grafana from attempting the update at all, and
   the plugin registers normally from the version already bundled in the
   download. Confirmed fixed: after applying it and restarting, the
   datasource health check returned `"status": "OK"` and every dashboard
   panel query returned real data (see the note at the top of this file and
   `docs/architecture.md` §46.6).

Neither problem is specific to this project's config — they're Grafana/
Windows packaging quirks, unrelated to the datasource/dashboard provisioning
YAML itself (which needed no further changes beyond Step 4's fix).

## Scope

- Local development only — no provisioned alerting, no auth changes to
  Grafana itself beyond its own default login, no remote Grafana instance.
- No new application metrics were added for this dashboard — every panel
  queries a metric Spring Boot Actuator/Micrometer already exposed as of
  Step 1.
- A specific Grafana version is not pinned — see "Prerequisites" above.
