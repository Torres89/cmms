# -*- coding: utf-8 -*-
"""
Seed the machine-specialist layer for two real machines.

Takes the shallow asset records the base CNC-shop seed created and turns them
into full dossiers: equipment breakdown structure from a pack, real datasheet
specs, serialized components with a back-to-birth ledger, meter history,
part sourcing, failure history and fault codes.

Idempotent. Every stage checks before it writes, so re-running only fills gaps.

Usage:
    python seed_machines.py                 # all stages
    python seed_machines.py packs specs     # selected stages
"""
import os
import sys
from datetime import datetime, timedelta

# The dossier card uses '·' and '→'. On Windows the console defaults to cp1252
# and printing it would raise rather than show the thing this script exists to
# produce.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
FILES_DIR = os.path.join(HERE, "files")

sys.path.insert(0, HERE)

from client import Client, ApiError
import machines_data as M

c = Client()

# Resolved once at login and reused.
COMPANY_ID = None


def iso(value):
    """'YYYY-MM-DD' or 'YYYY-MM-DDTHH:MM:SS' -> ISO instant."""
    if not value:
        return None
    if "T" in value:
        return f"{value}.000Z"
    return f"{value}T08:00:00.000Z"


def find_asset(name):
    """Assets are searched with an explicit level filter so sub-assemblies are visible too."""
    res = c.post("/assets/search", {
        "filterFields": [{"field": "name", "operation": "eq", "value": name,
                          "values": [], "alternatives": []}],
        "pageSize": 50, "pageNum": 0, "direction": "ASC", "sortField": "id",
    })
    for a in res.get("content", []):
        if a.get("name") == name:
            return a
    return None


def find_child(parent_id, position_code):
    """Find a position under an asset by its position code."""
    for child in c.get(f"/assets/children/{parent_id}") or []:
        if child.get("positionCode") == position_code:
            return child
        # Positions can be one level deeper (SPN-CART under SPN).
        found = find_child(child["id"], position_code)
        if found:
            return found
    return None


def find_part(name):
    res = c.post("/parts/search", {
        "filterFields": [{"field": "name", "operation": "eq", "value": name,
                          "values": [], "alternatives": []}],
        "pageSize": 50, "pageNum": 0, "direction": "ASC", "sortField": "id",
    })
    for p in res.get("content", []):
        if p.get("name") == name:
            return p
    return None


def find_vendor(name):
    res = c.post("/vendors/search", {
        "filterFields": [], "pageSize": 200, "pageNum": 0,
        "direction": "ASC", "sortField": "id",
    })
    for v in res.get("content", []):
        if v.get("name") == name or v.get("companyName") == name:
            return v
    return None


def find_location(name):
    res = c.post("/locations/search", {
        "filterFields": [], "pageSize": 200, "pageNum": 0,
        "direction": "ASC", "sortField": "id",
    })
    for loc in res.get("content", []):
        if loc.get("name") == name:
            return loc
    return None


# ---------------------------------------------------------------- stages

def stage_assets():
    """
    Create the machines the base CNC-shop seed doesn't know about.

    The dozer is one of them - it is not a machine tool and it does not belong
    in a machining bay, so it brings its own site and category with it.
    """
    print("\n== Bootstrap assets ==")

    for loc in M.BOOTSTRAP_LOCATIONS:
        if find_location(loc["name"]):
            print(f"  location exists {loc['name']}")
            continue
        body = {"name": loc["name"]}
        for key in ("customId", "address"):
            if loc.get(key):
                body[key] = loc[key]
        if loc.get("parent"):
            parent = find_location(loc["parent"])
            if parent:
                body["parentLocation"] = {"id": parent["id"]}
        c.post("/locations", body)
        print(f"  location created {loc['name']}")

    categories = {cat["name"]: cat for cat in c.get("/asset-categories")}
    settings_id = c.get("/auth/me").get("companySettingsId")
    for name, desc in M.BOOTSTRAP_CATEGORIES:
        if name in categories:
            print(f"  category exists {name}")
            continue
        body = {"name": name, "description": desc}
        if settings_id:
            body["companySettings"] = {"id": settings_id}
        categories[name] = c.post("/asset-categories", body)
        print(f"  category created {name}")

    for spec in M.BOOTSTRAP_ASSETS:
        if find_asset(spec["name"]):
            print(f"  asset exists {spec['name']}")
            continue
        body = {k: v for k, v in spec.items()
                if k not in ("category", "location", "inServiceDate")}
        body["inServiceDate"] = iso(spec["inServiceDate"])
        cat = categories.get(spec["category"])
        if cat:
            body["category"] = {"id": cat["id"]}
        loc = find_location(spec["location"])
        if loc:
            body["location"] = {"id": loc["id"]}
        created = c.post("/assets", body)
        print(f"  asset created {spec['name']} -> {created['id']}")


def stage_vendors():
    """Vendors the base CNC-shop seed doesn't create but these machines need."""
    print("\n== Vendors ==")
    for v in M.VENDORS:
        if find_vendor(v["name"]):
            print(f"  exists {v['name']}")
            continue
        c.post("/vendors", v)
        print(f"  created {v['name']}")


def stage_packs():
    """Build out the equipment breakdown structure from each machine's pack."""
    print("\n== Packs ==")
    for m in M.MACHINES:
        asset = find_asset(m["asset_name"])
        if not asset:
            print(f"  !! asset not found: {m['asset_name']} (run seed.py first)")
            continue

        # Identity first, so the pack lands on a machine that knows what it is.
        c.patch(f"/assets/{asset['id']}", {
            "name": m["asset_name"],
            "status": asset.get("status") or "OPERATIONAL",
            "level": "EQUIPMENT",
            "equipmentClass": m["pack"],
            "criticality": m["criticality"],
            "downtimeCostPerHour": m["downtime_cost_per_hour"],
            "replacementCost": m["replacement_cost"],
            "functionalDescription": m["functional_description"],
            "manufacturer": m["manufacturer"],
            "model": m["model"],
        })

        result = c.post(
            f"/asset-templates/{m['pack']}/instantiate?assetId={asset['id']}&dryRun=false", {})
        print(f"  {m['asset_name']}: {result.get('summary')}")


def stage_specs():
    """Write the datasheet values. Sourced facts, so they go in verified."""
    print("\n== Specs ==")
    for m in M.MACHINES:
        asset = find_asset(m["asset_name"])
        if not asset:
            continue
        existing = {s["specKey"]: s for s in c.get(f"/assets/{asset['id']}/specs")}
        written = 0
        for spec_key, value_text, value_num, unit in m["specs"] + m["measured_specs"]:
            if spec_key in existing:
                continue
            body = {
                "asset": {"id": asset["id"]},
                "specGroup": "General",   # the catalogue supplies the real group
                "specKey": spec_key,
                "unit": unit,
            }
            if value_text is not None:
                body["valueText"] = value_text
            if value_num is not None:
                body["valueNum"] = value_num
            try:
                c.post("/asset-specs", body)
                written += 1
            except ApiError as e:
                print(f"    warn {spec_key}: {e}")
        completeness = c.get(f"/assets/{asset['id']}/specs/completeness")
        print(f"  {m['asset_name']}: +{written} specs -> "
              f"{completeness['captured']}/{completeness['expected']} captured "
              f"({completeness['requiredCaptured']}/{completeness['requiredExpected']} required)")


def stage_parts():
    """Create the parts the dossiers need, then enrich them with real identity."""
    print("\n== Parts ==")
    for name, description, unit, cost, qty, min_qty, non_stock in M.NEW_PARTS:
        if find_part(name):
            continue
        c.post("/parts", {
            "name": name, "description": description, "unit": unit,
            "cost": cost, "quantity": qty, "minQuantity": min_qty, "nonStock": non_stock,
        })
        print(f"  created {name}")

    for name, manufacturer, mpn, criticality, lead_days, stock_recommended in M.PART_ENRICHMENT:
        part = find_part(name)
        if not part:
            print(f"  !! part not found: {name}")
            continue
        c.patch(f"/parts/{part['id']}", {
            "name": part["name"],
            "cost": part.get("cost", 0),
            "quantity": part.get("quantity", 0),
            "minQuantity": part.get("minQuantity", 0),
            "manufacturer": manufacturer,
            "mpn": mpn,
            "criticality": criticality,
            "leadTimeDaysTypical": float(lead_days),
            "stockRecommended": stock_recommended,
        })
    print(f"  enriched {len(M.PART_ENRICHMENT)} parts with manufacturer + MPN")


def stage_suppliers():
    """Where to buy each part, with a price that has a date on it."""
    print("\n== Suppliers ==")
    today = datetime.utcnow().strftime("%Y-%m-%d")
    for name, vendor_name, sku, price, currency, lead_days, url, preferred in M.PART_SUPPLIERS:
        part = find_part(name)
        vendor = find_vendor(vendor_name)
        if not part or not vendor:
            print(f"  !! skip {name} ({'no part' if not part else 'no vendor ' + vendor_name})")
            continue
        existing = c.get(f"/parts/{part['id']}/sourcing").get("suppliers", [])
        if any(s.get("supplierSku") == sku for s in existing):
            continue
        c.post("/part-suppliers", {
            "part": {"id": part["id"]},
            "vendor": {"id": vendor["id"]},
            "supplierSku": sku,
            "unitPrice": price,
            "currency": currency,
            "leadTimeDays": lead_days,
            "productUrl": url,
            "preferred": preferred,
            "priceCheckedAt": iso(today),
        })
        print(f"  {name} <- {vendor_name} @ {price} {currency}, {lead_days}d")


def stage_meters():
    """Give the meters a reading, which is what makes every counter live."""
    print("\n== Meter readings ==")
    for m in M.MACHINES:
        asset = find_asset(m["asset_name"])
        if not asset:
            continue
        meters = c.get(f"/meters/asset/{asset['id']}")
        for meter in meters:
            target = m["meters"].get(meter["name"])
            if target is None:
                continue
            readings = c.get(f"/readings/meter/{meter['id']}")
            if readings:
                continue
            try:
                c.post("/readings", {"meter": {"id": meter["id"]}, "value": float(target)})
                print(f"  {m['asset_name']} / {meter['name']} = {target:,} {meter.get('unit') or ''}")
            except ApiError as e:
                print(f"    warn {meter['name']}: {e}")


def stage_components():
    """Serialized components, installed into their positions through the ledger."""
    print("\n== Components ==")
    for m in M.MACHINES:
        asset = find_asset(m["asset_name"])
        if not asset:
            continue
        existing = {comp["serialNumber"]: comp for comp in c.get("/components")}

        # Retired components first, so the position history reads in order:
        # the old spindle went in, came out, and then the new one went in.
        for spec in m.get("retired_components", []):
            if spec["serial"] in existing:
                continue
            comp = _create_component(spec)
            position = find_child(asset["id"], spec["position"])
            if not position:
                print(f"  !! position {spec['position']} not found on {m['asset_name']}")
                continue
            c.post(f"/components/{comp['id']}/install", {
                "positionAssetId": position["id"],
                "occurredAt": iso(spec["installed_at"]),
                "meterValue": spec.get("meter_at_install"),
                "reason": "Installed at commissioning",
            })
            c.post(f"/components/{comp['id']}/remove", {
                "occurredAt": iso(spec["removed_at"]),
                "meterValue": spec.get("meter_at_removal"),
                "reason": spec["removal_reason"],
            })
            print(f"  {m['asset_name']} / {spec['position']}: {spec['serial']} "
                  f"installed then removed at {spec.get('meter_at_removal'):,} h")

        for spec in m["components"]:
            if spec["serial"] in existing:
                continue
            comp = _create_component(spec)
            position = find_child(asset["id"], spec["position"])
            if not position:
                print(f"  !! position {spec['position']} not found on {m['asset_name']}")
                continue
            c.post(f"/components/{comp['id']}/install", {
                "positionAssetId": position["id"],
                "occurredAt": iso(spec["installed_at"]),
                "meterValue": spec.get("meter_at_install"),
                "reason": spec.get("notes"),
            })
            print(f"  {m['asset_name']} / {spec['position']}: {spec['serial']} installed")


def _create_component(spec):
    body = {
        "serialNumber": spec["serial"],
        "manufacturer": spec["manufacturer"],
        "mpn": spec["mpn"],
        "acquiredAt": iso(spec["acquired"]),
        "acquisitionCost": spec["cost"],
        "notes": spec.get("notes"),
    }
    if spec.get("hour_limit"):
        body["hourLimit"] = float(spec["hour_limit"])
    part = find_part(spec["part"])
    if part:
        body["partType"] = {"id": part["id"]}
    return c.post("/components", body)


def stage_pm_baselines():
    """
    Record where each PM last actually happened.

    Meter-based intervals baseline at the meter's current reading when a pack is
    instantiated, which is right for "no history yet" but not interesting to
    look at. Setting a real last-completion point puts the machines in the state
    a running shop is actually in: one service comfortably away, one close.
    """
    print("\n== PM baselines ==")
    for m in M.MACHINES:
        asset = find_asset(m["asset_name"])
        if not asset:
            continue
        baselines = M.PM_BASELINES.get(m["asset_name"], [])
        dossier = c.get(f"/assets/{asset['id']}/dossier")
        pms = {pm["title"]: pm for pm in dossier.get("upcomingMaintenance", []) if pm.get("title")}

        # The spindle-hour meter is the counter the hour-based PMs run against.
        meters = {mt["name"]: mt for mt in c.get(f"/meters/asset/{asset['id']}")}
        spindle = meters.get("Spindle hours")
        current_hours = m["meters"].get("Spindle hours", 0)

        for fragment, hours_ago, days_ago in baselines:
            match = next((pm for title, pm in pms.items() if fragment.lower() in title.lower()), None)
            if not match:
                print(f"  !! no PM matching '{fragment}' on {m['asset_name']}")
                continue
            completed_at = datetime.utcnow() - timedelta(days=days_ago)
            c.post(f"/preventive-maintenances/{match['id']}/completed"
                   f"?at={int(completed_at.timestamp() * 1000)}", {})

            # markCompleted baselines meter intervals at the *current* reading,
            # which would read as 0 % used. Back-date them so the due list shows
            # real progress.
            if hours_ago is not None and spindle:
                for interval in c.get(f"/preventive-maintenances/{match['id']}/intervals"):
                    if interval.get("basis") != "METER":
                        continue
                    c.patch(f"/maintenance-intervals/{interval['id']}",
                            {"lastCompletedValue": float(max(0, current_hours - hours_ago))})

            status = c.get(f"/preventive-maintenances/{match['id']}/status")
            remaining = status.get("remaining")
            print(f"  {m['asset_name']} / {match['title']}: "
                  f"{round(status.get('percent') or 0)} % used"
                  + (f", ~{round(remaining)} {status.get('remainingUnit') or ''} left"
                     if remaining is not None else ""))


def stage_failures():
    """The failure history. This is what makes diagnosis rank by reality."""
    print("\n== Failure history ==")
    for m in M.MACHINES:
        asset = find_asset(m["asset_name"])
        if not asset:
            continue
        modes = {mode["code"]: mode for mode in c.get(f"/assets/{asset['id']}/failure-modes")}
        existing = c.get(f"/assets/{asset['id']}/failures")
        # Cause text is the most distinctive field; use it to stay idempotent.
        seen = {e.get("cause") for e in existing}

        for f in m["failures"]:
            if f["cause"] in seen:
                continue
            mode = modes.get(f["code"])
            if not mode:
                print(f"  !! failure mode {f['code']} not catalogued for {m['asset_name']}")
                continue
            body = {
                "asset": {"id": asset["id"]},
                "failureMode": {"id": mode["id"]},
                "mechanism": f.get("mechanism"),
                "cause": f["cause"],
                "detectedAt": f["detected_at"],
                "severity": f["severity"],
                "downtimeMinutes": f.get("downtime_minutes"),
                "repairCost": f.get("repair_cost"),
                "correctiveAction": f.get("corrective_action"),
                "preventiveRecommendation": f.get("preventive_recommendation"),
            }
            c.post("/failure-events", body)
            print(f"  {m['asset_name']}: {f['code']} ({f['occurred']})")


def stage_faults():
    """Fault events off the control, plus the shop's own code enrichment."""
    print("\n== Fault events & codes ==")
    for m in M.MACHINES:
        asset = find_asset(m["asset_name"])
        if not asset:
            continue
        existing = {(e["code"], e["occurredAt"][:10] if e.get("occurredAt") else None)
                    for e in c.get(f"/assets/{asset['id']}/fault-events")}
        for f in m["faults"]:
            key = (f["code"], f["occurred"][:10])
            if key in existing:
                continue
            event = c.post("/fault-events", {
                "asset": {"id": asset["id"]},
                "code": f["code"],
                "description": f.get("description"),
                "severity": f.get("severity"),
                "occurredAt": iso(f["occurred"]),
                "source": "MANUAL",
            })
            if f.get("cleared"):
                c.post(f"/fault-events/{event['id']}/clear", {})
            print(f"  {m['asset_name']}: {f['code']} @ {f['occurred']}")

    for entry in M.FAULT_CODES:
        c.post("/fault-codes", entry)
        print(f"  dictionary: {entry['code']} ({entry['equipmentClass']})")


def stage_documents():
    """Register the shop's PDFs as Documents so they get indexed."""
    print("\n== Documents ==")
    files = {f["name"]: f for f in _all_files()}
    for m in M.MACHINES:
        asset = find_asset(m["asset_name"])
        if not asset:
            continue
        existing = {d["title"] for d in c.get(f"/documents?assetId={asset['id']}")}
        for filename, title, doc_type, revision in m["documents"]:
            if title in existing:
                continue
            f = files.get(filename)
            if not f:
                # Upload it ourselves rather than depending on seed_files.py
                # having run. Uploaded as OTHER so the FileController does not
                # auto-register a second Document under a filename-derived
                # title; the record below is the one with real metadata.
                path = os.path.join(FILES_DIR, filename)
                if not os.path.exists(path):
                    print(f"  !! missing file on disk: {filename}")
                    continue
                f = c.upload(path, folder=f"assets/{asset['id']}", file_type="OTHER")[0]
                files[filename] = f
                print(f"    uploaded {filename} -> file #{f['id']}")
            c.post("/documents", {
                "file": {"id": f["id"]},
                "asset": {"id": asset["id"]},
                "equipmentClass": m["pack"],
                "docType": doc_type,
                "title": title,
                "revision": revision,
                "manufacturer": m["manufacturer"],
                "language": "en",
            })
            print(f"  {m['asset_name']}: {title} [{doc_type}] queued for indexing")

    queue = c.get("/documents/queue")
    pending = {k: v for k, v in (queue or {}).items() if v}
    print(f"  ingest queue: {pending or 'empty'}")


def _all_files():
    res = c.post("/files/search", {
        "filterFields": [], "pageSize": 500, "pageNum": 0,
        "direction": "ASC", "sortField": "id",
    })
    return res.get("content", [])


def stage_report():
    """Print each machine's dossier — the point of all of the above."""
    print("\n== Dossiers ==")
    for m in M.MACHINES:
        asset = find_asset(m["asset_name"])
        if not asset:
            continue
        card = c.get(f"/assets/{asset['id']}/dossier?format=text")
        print("\n" + "-" * 72)
        print(card.get("text", ""))
    print("-" * 72)


STAGES = [
    ("assets", stage_assets),
    ("vendors", stage_vendors),
    ("packs", stage_packs),
    ("specs", stage_specs),
    ("parts", stage_parts),
    ("suppliers", stage_suppliers),
    ("meters", stage_meters),
    ("components", stage_components),
    ("pm_baselines", stage_pm_baselines),
    ("failures", stage_failures),
    ("faults", stage_faults),
    ("documents", stage_documents),
    ("report", stage_report),
]


def main():
    global COMPANY_ID
    c.login()
    me = c.get("/auth/me")
    COMPANY_ID = me.get("companyId")
    print("Logged in to", c.base, "| companyId =", COMPANY_ID)

    selected = sys.argv[1:]
    for key, fn in STAGES:
        if selected and key not in selected:
            continue
        try:
            fn()
        except Exception as e:
            print(f"!! stage {key} error: {e}")
            raise
    print("\nDone.")


if __name__ == "__main__":
    main()
