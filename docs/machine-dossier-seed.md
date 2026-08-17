# Machine dossier seed — Haas VF-2, FANUC ROBODRILL and Cat D6

Three real machines, documented to machine-specialist depth, with every figure
traced to a manufacturer datasheet or published service document.

| | |
|---|---|
| **Runs against** | `seed/seed_machines.py` |
| **Depends on** | `seed/seed.py` stages `locations categories vendors assets parts` |
| **Adds** | `seed/machines_data.py`, `api/.../packs/cnc_drill_tap_center.json`, `api/.../packs/crawler_dozer.json` |
| **Trims to** | `seed/purge_demo.py` — reduces an instance to just these three |

The set is chosen for two audiences: a CNC machine shop (the Haas and the
RoboDrill) and a small construction contractor (the dozer). Both should find
their own machine on the first screen rather than hunting through a fleet.

---

## Why these two machine tools

They are the same thing in a generic CMMS — both are "CNC Vertical Machining
Center" in the asset category list — and almost nothing about maintaining them
is the same. That contrast is the whole argument for per-machine depth:

|  | Haas VF-2 | FANUC ROBODRILL α-D21MiB5 |
|---|---|---|
| Taper | CT40 | BT30 |
| Spindle | 8,100 rpm, 22.4 kW | 10,000 rpm, 14.2 kW |
| Rapids | 25.4 m/min | 54 m/min |
| Tool change | 4.5 s chip-to-chip, 20-pocket carousel | 1.6 s cut-to-cut, 21-pocket turret |
| **Axis lubrication** | **Circulating oil.** Bijur pump, ~3 ml every 30 min at ~35 psi, with a low-pressure alarm (121) | **Grease.** Refilled on an operating-hour schedule. **No alarm exists.** |
| What kills it | A lube line that blocked while the reservoir still looked full | A greasing interval that got skipped during a busy month |
| Wrong-consumable hazard | **Severe.** Newer machines take Mobil SHC 625; Vactra No. 2 is too heavy, clogs the system and starves the spindle | Wrong grease, but no comparable documented failure path |

A generic CMMS gives both machines the same PM checklist. This seed gives them
the two different checklists they actually need — via two different packs, which
is the mechanism, not a special case.

## Why the dozer

It is the same mechanism aimed at a completely different trade, which is the
point: nothing about the platform is CNC-specific.

A Cat D6 has no controller to interrogate and no alarm history worth mining. Its
condition signal is **oil**, and the demo data is built around one causal chain
that a contractor will recognise immediately:

1. **18 Jun 2024** — a scheduled SOS sample on the left final drive comes back at
   340 ppm iron, up from 95 ppm two samples earlier. The machine feels fine.
   Nobody acts on it.
2. **11 Aug 2024** — the duo-cone seal fails, the hub runs low on oil and the
   bearings blue. Four days down, an $11,500 assembly, roughly $30k of lost days
   at $320/h.
3. The 250-hour service now says **take SOS samples from five compartments**, and
   a doubling result is a work order rather than a filing job.

The dozer also carries the undercarriage survey — chains at 62 % and 58 % worn,
measured and recorded — because undercarriage is usually the largest lifetime
cost on the machine and is the one line a contractor can genuinely forecast.

Sources: Cat D6 and D6 XE technical specification sheets (engine, weights,
service refill capacities, undercarriage and blade figures) published by
Caterpillar.

---

## What gets created

Running `python seed_machines.py` produces, per machine:

- **Equipment breakdown structure** from its pack — 24 positions for the Haas,
  21 for the RoboDrill, each a real asset so work orders and history attach at
  component level.
- **Spec sheet**: 22/26 captured for the Haas, 30/33 for the RoboDrill. The gaps
  are deliberate — they are figures that could not be sourced, and the
  completeness meter should say so rather than be padded.
- **Serialized components** with a back-to-birth ledger, including the Haas's
  *first* spindle: installed at commissioning, removed at 8,940 h after the
  bearing seizure. That is what makes "how long did the last one last" a
  question with an answer.
- **Meter readings** — 11,840 spindle hours on the Haas, 6,420 on the RoboDrill.
- **Part sourcing** — manufacturer part numbers, suppliers, prices, lead times
  and links for ten parts.
- **Failure history** in the ISO 14224 taxonomy, seven events across the two
  machines, each with mechanism, cause, corrective action and what changed as a
  result.
- **Fault events and dictionary entries** — Haas 121/102, FANUC
  SV0410/SV0411/SP1241.
- **Documents** registered and queued for indexing.

### The story the Haas data tells

It is one causal chain, and it is the reason the product exists:

1. **3 Apr 2023** — alarm 121. A way-lube line was crushed behind the rear way
   cover. The reservoir looked full, so the daily check had passed for three
   weeks while no oil reached the point.
2. **21 Apr 2023** — the front spindle bearing seized. Three days down, $4,500
   part, roughly $13k of lost capacity at $180/h.
3. The 500-hour PM task text changed from *"check way lube level"* to
   **"verify FLOW at each point"**, which is now what the pack ships with.

The current spindle went in at 8,940 h and the machine reads 11,840 h, so the
dossier shows it at **2,900 h of 12,000 — 76 % remaining**. That number is
computed from the ledger and the meter, not typed in.

---

## Sources

Every figure in `machines_data.py` comes from one of these.

**Haas VF-2**
- [Haas VF-2 machine page (Haas Automation UK)](https://www.haas.co.uk/machines/vf-2/) — travels, spindle speed/power/torque, rapids, table, tool changer, coolant capacity, air requirement
- [VF-2SS product page (Haas Automation)](https://www.haascnc.com/machines/vertical-mills/vf-series/models/small/vf-2ss.html) — taper options, spindle construction
- [Haas Axis Lubrication Oil — service manual](https://www.haascnc.com/service/online-manuals/lubrication-systems---service-manual/haas-axis-lubrication-oil.html) and [Lubricant, Grease and Sealant Tables — RD0040](https://www.haascnc.com/service/troubleshooting-and-how-to/reference-documents/lubricant--grease--and-sealant-tables-for-haas-machine-component.html) — Mobil SHC 625 specification
- [OIL, REFILL MOBIL SHC 625 — Haas Parts](https://parts.haascnc.com/haasparts/en/USD/Find-Replacement-Parts/Consumables-(Grease,-Oil,-Paint,-Sealants-)/OIL,-REFILL-MOBIL-SHC-625---1-QT-0-94-L/p/93-2220A) — part number 93-2220A
- [Why the wrong Haas lubrication is expensive (MachinesUsed)](https://www.machinesused.com/blog/post/if-you-run-haas-machines-make-sure-you-know-what-lubrication-you-should-be-using-failure-to-do-so-can-be-expensive-) — the Vactra No. 2 hazard
- [Axis Lubrication System — Bijur Mechanical, RD0004](https://www.haascnc.com/service/troubleshooting-and-how-to/reference-documents/axis-lubrication-system---bijur-mechanical.html) — 3 ml / 30 min at ~35 psi, alarm 121 behaviour
- [Haas Alarm 121 — LOW LUBE OR LOW PRESSURE](https://www.helmancnc.com/haas-alarm-121-low-lube-or-low-pressure/) and [Haas alarm code list](https://www.helmancnc.com/haas-alarm-codes/) — alarm text for 121, 102, 119
- [FILTER, STANDARD COOLANT HEAD/BOWL KIT — Haas Parts](https://parts.haascnc.com/haasparts/en/EUR/Find-Replacement-Parts/Coolant-Systems/Standard-Coolant/Filters/FILTER,-STANDARD-COOLANT-HEAD-BOWL-REPLACEMENT-KIT/p/93-2356) — part 93-2356
- [Spindle rebuild costs (Practical Machinist)](https://www.practicalmachinist.com/forum/threads/spindle-rebuild-costs.238540/) and [Haas VF-2 spindle repair case study (Northland Tool)](https://www.northlandtool.com/case-studies/haas-vf-2-spindle-repair/) — ~$4,500 exchange cartridge, $2,000–2,850 rebuild, and a real front-bearing seizure teardown

**FANUC ROBODRILL**
- [ROBODRILL α-D21MiB5 Plus product page (FANUC Europe)](https://www.fanuc.eu/eu-en/product/robodrill/robodrill-a-d21mib5-plus) — travels, table, taper, spindle speed/power/torque, tool changer, rapids, accuracy, repeatability, air, weight, control
- [ROBODRILL α-DiB Plus series brochure (FANUC America)](https://www.fanucamerica.com/docs/default-source/default-document-library/2018_robodrill_brochure.pdf) — series specifications
- [ROBODRILL maintenance manual](https://www.scribd.com/document/623590643/Maintenance-Manual-Basic-Version) — 200-hour spindle/turret gear greasing, 1000-hour LM guide and ballscrew greasing, grease unit part numbers A90L-0001-0534#LHLX1007 (700 cm³) and #LHLX1004 (400 cm³), recommended coolants
- [FANUC servo alarms SV0401 / SV0410 / SV0417 (AxisMD)](https://axismd.ai/blog/fanuc-servo-alarms-sv0401-sv0410-sv0417), [SV0410 excess error (ErrorLib)](https://errorlib.net/en/fanuc/series-0i-model-f-plus-parameter/sv0410-excess-error-stop) and [SV0411 excess error moving (Click2Electro)](https://click2electro.com/sv0411-excess-error-moving-fault-in-fanuc-drive/) — alarm meanings, parameter 1829, mechanical-binding causes

Where a figure could not be sourced — the RoboDrill's coolant tank capacity, the
Haas's electrical supply — it is simply absent, and the completeness meter
reports the machine as incompletely documented. That is the honest state.

---

## Running it

```bash
# 1. Base CNC-shop data (locations, vendors, assets, parts)
cd seed
python seed.py locations categories vendors assets parts

# 2. The machine-specialist layer
python seed_machines.py

# ...or one stage at a time
python seed_machines.py packs specs components report
```

Stages, in order: `assets vendors packs specs parts suppliers meters components
pm_baselines failures faults documents report`.

`assets` creates what the base CNC-shop seed does not know about — the dozer,
its `Earthmoving Equipment` category and the `Contractor Yard` site it sits in.

Idempotent throughout — each stage checks before it writes, so re-running fills
gaps rather than duplicating. `report` prints both dossier cards, which is the
fastest way to see whether a change did what you expected.

Environment: `CMMS_API_URL`, `CMMS_EMAIL`, `CMMS_PASSWORD`.

### Why `pm_baselines` exists

Instantiating a pack onto a machine with 11,840 hours on it and no service
history would otherwise show its 500-hour PM as 2,368 % overdue. That is
arithmetically true and practically useless — it is "no history yet", not
"urgent", and showing it as urgent is how people learn to ignore the due list.
The stage records where each service last actually happened, leaving the
machines in the state a working shop is in: one service comfortably away, one
getting close.

---

## Defects this seed surfaced

Building it against a running API found nine real bugs, all fixed:

1. **Spec labels never appeared.** `applyCatalogDefaults` looked up the
   catalogue using the spec's own company, which `@PrePersist` does not populate
   until after that code runs — so every spec created through the API missed its
   label, group and unit. It now falls back to the asset's company.
2. **Everything typed in read as unverified.** `isVerified()` tests
   `verifiedBy`, and the create path never set it. A person entering a value is
   a person vouching for it; only machine output should arrive unverified.
3. **Accuracy figures rounded away.** The dossier formatted numbers to three
   decimals, turning 0.0051 mm into "0.005" — which discards the part of a
   machine-tool accuracy figure that carries the meaning.
4. **Meter-based PMs baselined at zero.** Pack instantiation set
   `lastCompletedAt` but not `lastCompletedValue`, so a used machine looked
   thousands of percent overdue the moment it was commissioned into the system.
5. **Components installed with history showed zero hours.** Counters only
   advanced from readings taken *after* an install, so a spindle fitted at
   8,940 h on a machine reading 11,840 h showed 100 % life remaining. Installing
   with a meter value now credits the usage that has already accrued — which is
   exactly what a commissioning engineer means by "this went in at 8,940 hours".
6. **Every hours meter aged every installed component.** A machine carries
   several meters counted in hours — spindle hours, power-on hours, idle hours —
   and the roll-up credited a component from all of them. The Haas spindle,
   fitted at 8,940 h on a machine reading 11,840, showed 3,970 hours of wear
   instead of 2,900, because power-on hours were added on top. Meters now carry
   a `usageBasis` flag, set by the pack, and only that meter ages the parts
   installed beneath it.
7. **Re-instantiating a pack duplicated the whole PM schedule.** Positions and
   meters matched on their natural key and were reused; PMs were created
   unconditionally, so a second run left the machine with two of every service
   and a due list that read double. They now match on `templateKey`.
8. **The root asset list was paged without an order.** `/assets/children/0`
   takes a `Pageable` with no default sort, so Postgres returned rows in
   whatever order it liked — in practice a machine drifted to the end of the set
   the moment it was updated, and anything past the page size vanished. This is
   what hid the Haas and the RoboDrill at the bottom of a 22-machine list. Both
   the root query and the children query now sort deterministically.
9. **Assets silently disappeared from the asset list.** Two of my own changes
   combined into the worst bug of the lot. Adding fields to `AssetPatchDTO` meant
   any PATCH omitting them nulled them — and PATCH here replaces every mapped
   field, which is the established contract for the original columns. So a
   client updating an asset's name wiped its `level`. The new default search
   filter then read `level IN (SITE, SYSTEM, EQUIPMENT)`, and `NULL IN (...)` is
   not true in SQL, so those assets vanished from the list entirely. Running the
   *full* base seed — which PATCHes assets to link parents and parts — hid eight
   machines including the Haas.

   Fixed on both sides: the new fields on `AssetMapper`/`PartMapper` are
   `NullValuePropertyMappingStrategy.IGNORE` so an absent field is left alone
   rather than cleared, and the level filter carries an `IS NULL` alternative so
   an asset with no level is treated as a machine and always shown. A filter
   that silently hides a customer's assets is far worse than one that shows a
   sub-assembly it could have hidden.
