# Machine Specialist Platform — implementation notes

What has actually been built against
`docs/plans/2026-08-15-machine-specialist-platform.md`, and the decisions taken
along the way that the plan left open.

| | |
|---|---|
| **Date** | 2026-08-16 |
| **Scope** | `api/`, `frontend/`, `mobile/`, `cmms-agent/`, `ingest-worker/`, `telemetry-collector/`, deployment |
| **Status** | All phases implemented. Infrastructure steps (EC2 resize, S3 bucket creation, DNS) are deployment actions, not code, and are listed at the end. |

---

## 1. What is where

```
api/                     Spring Boot — the schema, the dossier, retrieval, packs
cmms-agent/              Per-user auth, the tool surface, the remote MCP server
ingest-worker/           NEW — Docling parsing, OCR, chunking, EmbeddingGemma
telemetry-collector/     NEW — MTConnect and ISO 15143-3 pollers
frontend/                The dossier page, commissioning, AI settings
mobile/                  Scan-first dossier + on-machine assistant
```

---

## 2. Phase 0 — foundation

### The security defect, fixed first

`cmms-agent` logged in once with `CMMS_EMAIL`/`CMMS_PASSWORD` and every chat
request rode that token, while `POST /chat` had no authentication at all. Every
chat user therefore acted as that admin, in that admin's organization.

- `cmms-agent/auth.py` — every request carries the caller's own Atlas JWT,
  validated against `GET /auth/me` (cached 60 s). No shared secret to
  distribute, and the API stays the single source of truth on identity.
- `api_client.py` is now constructed **per request** with that token and bound
  to a `ContextVar`; the eight tool modules read it through `get_client()`.
  The service account is gone. The CLI (`agent.py`) signs a real user in.
- CORS is driven by `CMMS_FRONT_URL` + `AGENT_ALLOWED_ORIGINS`; no hardcoded
  localhost in production, and no wildcard.

### Persisted state and metering

`chat_session`, `chat_message`, `chat_action_log`, `llm_usage`,
`mcp_oauth_client` (changelog `1786000001`). Written by the agent, which owns
them — they carry no JPA entity, so there is no Hibernate mapping to keep in
sync with a service that isn't Java.

`llm_usage` is populated on **every** door, including the ones that cost us
nothing, because "is this thing actually being used" is a care-plan review
question regardless of who pays for the tokens.

### Storage

`StorageType` gained `S3` and `LOCAL`.

- `S3Service` — S3 / Cloudflare R2 via the MinIO SDK (already a dependency and
  S3-compatible), with region and path-style handling.
- `LocalStorageService` — filesystem for self-hosted installs. No presigned
  URLs exist on a filesystem, so it mints its own: HMAC-signed, expiring links
  to `/files/local`, served by `LocalFileController` with real HTTP range
  support and no buffering.
- `assertSafeToBuffer` caps what `download()` will pull into heap.
  `FileType.requiresSignedUrlOnly()` marks video and CAD as never-inline.

`scripts/backup.sh` now branches on `STORAGE_TYPE`: on S3/GCS it only dumps
Postgres, because the files already live somewhere durable.

### Deployment

- Postgres image → `pgvector/pgvector:pg16`, with the musl→glibc collation
  warning documented in `CLAUDE.md` and in the compose file itself.
- JVM capped via `JAVA_OPTS=-XX:MaxRAMPercentage=50` in `api/Dockerfile`.
- `Caddyfile` added to `.gitignore`.
- AGPL §13 source offer surfaced in the marketing footer and the app sidebar,
  driven by `SOURCE_CODE_URL`.

### Door 1 — the remote MCP server

`cmms-agent/mcp_server.py`. Remote HTTPS with the Streamable HTTP binding,
because ChatGPT accepts nothing else. Implements:

- OAuth 2.1 with PKCE — discovery documents, dynamic client registration
  (RFC 7591, persisted so restarts don't break clients), an authorize page, and
  a token endpoint. **The issued access token is an Atlas JWT**, so every tool
  call lands in the API as that user with org isolation intact.
- `tools/list`, `tools/call`, and `resources/list` / `resources/read` —
  asset dossiers are exposed as MCP *resources* so a client can browse machines
  without a tool round-trip.
- Per-token rate limiting. An external client can loop; the CMMS should not
  fall over when it does.

---

## 3. Phase 1 — the machine dossier

Changelog `1786000002`. Every column nullable or defaulted; existing rows keep
working.

| Plan section | Built |
|---|---|
| §2.1 EBS | `AssetLevel`, `TrackingClass`, `positionCode`, `criticality`, `downtimeCostPerHour`, `replacementCost`, `equipmentClass` on `Asset` |
| §2.2 Typed specs | `AssetSpec` with full provenance, `SpecKeyCatalog`, completeness meter |
| §2.3 Components | `ComponentInstance` + append-only `ComponentEvent`, install/remove/overhaul/scrap, hours roll-up, 10 %/5 % life alerts |
| §2.4 BOM | `AssetBomLine` |
| §2.5 Sourcing | Part enrichment, `PartSupplier`, `PartCrossReference` |
| §2.6 Failures | `FailureMode`, `FailureEvent`, ranked candidates, Pareto with MTBF/MTTR |
| §2.7 Multi-counter PMs | `MaintenanceInterval` + `TriggerMode`, computed progress |
| §2.10 Custom fields | Made polymorphic (`entityType` + `entityId`), existing vendor rows migrated |
| §3.5 Dossier | `AssetDossierService`, `GET /assets/{id}/dossier` (JSON + text) |

**Decisions the plan left open:**

- *Open decision 2 (upstream compatibility)* — resolved in favour of the
  simpler designs: `AssetLevel` lives on `Asset` and `FileType` was widened in
  place. This makes upstream merges harder, which is the accepted trade. The
  divergence is confined to enum values and additive columns, so a merge
  conflict will be visible rather than silent.
- Sub-assemblies are `Asset` rows, as the plan recommended. That single choice
  is why component-level work orders, meters, files, PMs and downtime all work
  with no new code.
- Counter roll-up hooks `ReadingService.create`, so it applies to readings from
  any source — the UI, an import, or the telemetry collector.
- Meter classification (hours vs cycles) reads the meter's unit and name; an
  unrecognised unit simply drives no counters rather than guessing.

---

## 4. Phase 2 — knowledge and AI surfaces

Changelog `1786000003`.

- `document_chunk` with `tsvector` (generated, `'simple'` — stemming would
  mangle alarm codes and part numbers) and `VECTOR(768)`, HNSW + GIN indexes.
  **No JPA entity**: Hibernate has no native vector mapping and `validate`
  would fight it, and the hybrid query is native SQL either way.
- `KnowledgeService` — the RRF fusion from the plan, verbatim. If pgvector is
  unavailable it logs and degrades to lexical-only rather than failing: a shop
  with BM25 over its manuals is far better off than a shop with a stack trace.
- `ingest-worker/` — claims jobs with `FOR UPDATE SKIP LOCKED`, parses with
  Docling (falling back to pypdf, then OCR), chunks with heading paths and page
  ranges, embeds with EmbeddingGemma-300M, and proposes `AssetSpec` rows and
  fault-code dictionary entries. Serial by design.
- Extraction only proposes keys that already exist in the equipment class's
  catalogue. A bad match then produces a wrong value on a real field — visible
  and correctable — rather than inventing a field nobody asked for. Everything
  lands unverified and `ON CONFLICT` refuses to overwrite a verified value.
- `DiagnosisService` — dossier, then candidates ranked by *this machine's* own
  history, then retrieval per candidate, then likely parts. Safety lines are
  extracted from the retrieved text into their own field so they cannot be
  paraphrased away.
- Doors 2 and 3 — `CompanySettings` AI fields, AES-GCM encryption
  (`SecretEncryptionService`), a settings screen that only ever shows a masked
  suffix, and `/internal/ai-config/{companyId}` behind a constant-time shared
  secret for the agent.
- Nameplate capture — `prepare` assembles a signed image URL, local OCR text
  and the expected field list with a JSON schema; the caller's model reads it;
  `apply` writes the result as unverified specs and only fills asset identity
  fields that are currently blank.

---

## 5. Phase 3 — procurement

`SupplierCatalogAdapter` with `ManualAdapter` (always present) and
`McMasterCarrAdapter` (reports itself unconfigured without credentials, so
nothing depends on it). Catalogue results are returned for a human to accept,
never written straight in — an auto-imported price for the wrong part is worse
than no price, because nobody knows to doubt it.

`RestockService` builds the "order what's due" kit: consumables due within a
horizon, priced, with lead times, flagged `urgent` when the lead time already
exceeds the time remaining. `suggestReorderPoint` returns null rather than a
number when there is no real consumption history to compute from.

---

## 6. Phase 4 — telemetry

Changelog `1786000004`: `MeterSource` and `FaultEvent`.

`telemetry-collector/` polls per the config stored on `meter_source.config`, so
adding a machine is a settings change rather than a deploy. Readings are posted
through `/internal/telemetry/readings` → `ReadingService`, **not** straight into
the table, so component roll-up and meter triggers still fire.

`FaultEvent` is kept distinct from `FailureEvent`: one is what the control said,
the other is what a person concluded had broken. Repeats of a still-active fault
fold into the existing row rather than piling up.

---

## 7. Phase 5 — packs and commissioning

`AssetPackService` + `POST /asset-templates/{key}/instantiate` (with a dry run).
Three packs shipped, bilingual: `CNC_MACHINING_CENTER_VMC`, `CNC_DRILL_TAP_CENTER`
and `CRAWLER_DOZER`.

The drill/tap pack is deliberately separate from the VMC pack rather than a
variant of it, because a BT30 RoboDrill and a 40-taper Haas are the same
category in a generic CMMS and share almost none of their maintenance: one has
a circulating oil system with a low-pressure alarm, the other has grease on an
operating-hour schedule and no alarm at all. Two packs is the mechanism working
as intended.

The pack content is grounded in what actually breaks these machines — the
`LUBE-NOFLOW` failure mode and the "verify FLOW at each point, not just
reservoir level" task exist because checking the reservoir is the standard
mistake, and the dozer's 250-hour service leads with SOS sampling because
trending wear metals is the highest-value predictive practice available on that
equipment and needs no sensors at all.

Packs can also be registered at runtime via `POST /asset-templates`, which is
the mechanism that keeps a customer wanting different PM templates off a code
branch.

`PlanFeatures` gained `MACHINE_KNOWLEDGE`, `COMPONENT_TRACKING`, `TELEMETRY`,
`SUPPLIER_CATALOG`, `MCP_ACCESS`, `MANAGED_AI`. These gate the hosted service,
not the AGPL source.

---

## 8. UI

- **Dossier page** — header band (identity, health, completeness bar, "Ask
  about this machine") above six new tabs: Structure, Specs, Parts & BOM,
  Documents, History, plus the original tabs unmoved.
- **Scoped chat** — the header button dispatches `atlas:chat-scope`; the widget
  pins the machine and the agent injects a fresh dossier card every turn.
- **Failure capture** — a dialog on work-order close, candidates pre-filtered by
  equipment class and ranked by this machine's history, prefilled from the
  catalogue, and skippable. The design target is fifteen seconds.
- **Commissioning page** — pack → documents → batch approval, with a checklist
  that doubles as the handover document.
- **AI settings** — the three doors, with the MCP endpoint shown for copying.
- **Mobile** — dossier as the first tab after a scan, cached for offline, with
  the four one-tap actions and an on-machine assistant screen.

All strings are in English and Spanish.

---

## 9. Verification performed

| Check | Result |
|---|---|
| `api` compiles (`mvnw -o compile`) | pass |
| All 30 new Liquibase changesets against a real `pgvector/pgvector:pg16` | applied cleanly, including the vector extension, the HNSW index and the `custom_field` polymorphic migration |
| Hibernate `ddl-auto: validate` against that schema | **passed** — every entity mapping matches the migrations |
| Entity ↔ migration column cross-check | every new entity column has a migration |
| End-to-end smoke test against a running API | see below |
| Frontend typecheck (TS 5.4) | no errors in any changed file |
| Python services (`py_compile`) | pass |
| Mobile | **not typechecked** — `mobile/node_modules` is not installed in this environment. The files parse, but the mobile changes are unverified by compilation. |

### What the smoke test exercised

Against a live API with a seeded admin: pack listing and instantiation (dry run
and real), spec creation and the completeness meter, ranked failure modes,
BOM, knowledge search, fault-code lookup, `diagnose`, plan proposals, the
restock kit, the dossier in both formats, AI config round-tripping, the
internal endpoints' auth, component install and its ledger, meter readings
rolling into component hours, life-limit alerts, and multi-counter PM progress.

Confirmed working end to end: a reading of 11,300 h on a spindle with a
12,000 h limit produced `6 % remaining`, fired the 10 % life alert, and rendered
on the dossier card as
`SPN Component SN SP-77120 — 11,300 h (limit 12,000 h) — 6 % remaining`.

### Defects found by running it, not by reading it

1. **Liquibase would not parse.** XML forbids `--` inside comments and the Phase 1
   changelog's section separators used it.
2. **Envers refused to start.** It requires
   `@Audited(targetAuditMode = NOT_AUDITED)` on relations from an audited entity
   to a non-audited one; `ComponentEvent` and `FailureEvent` each needed it on
   every association.
3. **The new `Asset` fields never reached the API.** `AssetShowDTO`,
   `AssetPatchDTO` and `AssetMiniDTO` had no `level`, `positionCode`,
   `equipmentClass`, `criticality` or `trackingClass`, so the dossier page and
   the pack tooling were blind to the structure they had just created.
4. **A machine's BOM came back empty.** Consumables attach to the subunit that
   owns them (a coolant filter belongs to `COOL`), but the BOM query only asked
   the top-level asset. Now a recursive subtree query, matching how components
   already worked.
5. **Asset lists showed the inside of machines.** Instantiating a pack added 24
   sub-assemblies to a two-machine shop's asset list. `AssetService` now applies
   a default `level IN (SITE, SYSTEM, EQUIPMENT)` predicate, which a caller can
   override by filtering on `level` explicitly.
6. **Component counters never advanced.** `Reading.meter` arrives from the
   request body as `{"id": n}` with no asset attached, so the roll-up's
   `meter.getAsset()` guard silently returned every time. It now loads the real
   meter by id rather than trusting the association. This one was invisible to
   inspection — the code read correctly and failed quietly, which is exactly the
   failure mode a silent early-return produces.

---

## 9a. Seed data — three real machines

`seed/seed_machines.py` + `seed/machines_data.py` take the shallow asset records
the base CNC-shop seed creates and deepen them to full dossiers: a **Haas
VF-2**, a **FANUC ROBODRILL alpha-D21MiB5** and a **Cat D6 crawler dozer**.
Every figure is traced to a manufacturer datasheet, service document or
published alarm list — see `docs/machine-dossier-seed.md` for the source list.

The dozer is not a machine tool, has no controller to interrogate and lives on a
different site, which is the point: the two CNC machines demonstrate depth
within a trade, the dozer demonstrates that none of the mechanism is specific to
one. Its `assets` stage creates what the CNC-shop seed has no reason to know
about — the machine, an `Earthmoving Equipment` category and a `Contractor Yard`
site.

Also added, because the seed needed them and they were genuine gaps:

- `FaultCodeDictionary` entity, repository and `/fault-codes` controller. The
  table existed from Phase 2 but nothing could read or write it.
- Shared fault-code reference data seeded by Liquibase with a null
  `company_id` — Haas 121/102/119 and FANUC SV0410/SV0411/SV0401/SP1241. This is
  the one place a shared corpus is appropriate: published manufacturer alarm
  codes, not anyone's documents. A customer's own enrichment wins on lookup.

### Trimming an instance for a demo

`seed/purge_demo.py` reduces an instance to a named set of machines, deleting
every other asset and the work orders, PMs, meters, components, parts, vendors
and locations that only existed for them. It runs entirely through the REST API
in dependency order, refuses to start if a keep-list machine is missing, and
takes `--dry-run`. Destructive by design — point it at a demo instance.

Running the seed against a live API found nine defects, all fixed and listed in
`docs/machine-dossier-seed.md`. Three are worth repeating here because they were
silent rather than loud:

- **Spec labels never resolved.** The catalogue lookup used the spec's own
  company, which `@PrePersist` does not populate until after that code runs, so
  every spec created through the API lost its label, group and unit.
- **Components installed with history showed zero hours.** Counters only
  advanced from readings taken *after* an install, so a spindle fitted at
  8,940 h on a machine reading 11,840 h showed 100 % life remaining instead of
  76 %. Installing with a meter value now credits the usage already accrued.
- **Every hours meter aged every installed component.** A machine carries
  several counters measured in hours — spindle hours, power-on hours, idle hours
  — and both the running roll-up and the install back-fill credited components
  from whichever one they found first. The Haas spindle read 3,970 h of wear
  instead of 2,900, and the dozer's final drive was aged by *idle* hours.
  `Meter.usageBasis` now names the one counter a part's life is spent against,
  the packs set it, and both paths respect it. Migration
  `2026_08_16_1786000006_meter_usage_basis.xml`.

A third was a content defect rather than a code one: the VMC pack named
"Way lube (ISO VG 68 slideway)" as its consumable, and on a modern Haas that is
precisely the oil that clogs the lubrication system and kills the spindle. The
pack now names no product at all — the machine's `way_lube_spec` does, per
machine, alongside a `way_lube_warning` key.

---

## 10. Not code — deployment actions still outstanding

These are from the plan's Phase 0 and cannot be done from the repository:

- [ ] Allocate an **Elastic IP before** resizing the instance. Changing instance
      type preserves the instance ID but the public IP changes without one, and
      Caddy terminates TLS against a DNS name — you would come back up with a
      broken certificate.
- [ ] Resize off `t2.medium` to `t3.xlarge` / `m7i-flex.xlarge`. t2 is
      burstable, so sustained load drains CPU credits and throttles to baseline.
- [ ] **Dump and restore** Postgres into the pgvector image. Do not reuse the
      volume — see the collation warning.
- [ ] Create the S3/R2 bucket, set `STORAGE_S3_*`, and `aws s3 sync` the
      existing MinIO volume in.
- [ ] Set `INTERNAL_SERVICE_TOKEN` and `SECRET_ENCRYPTION_KEY` to real secrets.
- [ ] Point `MCP_PUBLIC_URL` at a real HTTPS hostname and add the Caddy route,
      then verify the connector against both Claude and ChatGPT.
- [ ] Publish the fork source and set `SOURCE_CODE_URL` (AGPL §13).
- [ ] Serve the frontend build from Caddy and drop the nginx container.
