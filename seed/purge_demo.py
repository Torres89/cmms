"""
Reduce an instance to the three demo machines.

The demo is pitched to two audiences: a CNC shop and a small construction
contractor. Twenty-two machines makes both of them hunt for the one that looks
like theirs. Three machines - a Haas VMC, a FANUC drill-tap centre and a Cat
dozer - puts the thing being demonstrated on the first screen.

What it removes: every asset outside the keep list, along with the work orders,
preventive maintenances, meters, readings and components that hang off them,
plus the parts, locations and vendors that nothing keeps referencing afterwards.

Everything goes through the REST API, in dependency order, so the same script
works against a local instance or a deployed one.

    CMMS_API_URL=http://localhost:8099 python purge_demo.py          # do it
    CMMS_API_URL=http://localhost:8099 python purge_demo.py --dry-run

Destructive and not reversible. Point it at a demo instance.
"""
import sys

import machines_data as M
from client import Client

KEEP_MACHINES = [
    "Haas VF-2 #1",
    "Fanuc RoboDrill",
    "Cat D6 Dozer",
]

# Locations worth keeping even when no surviving asset sits in them, so the two
# sites still read as places with a layout rather than as three empty rooms.
KEEP_LOCATIONS = [
    "Precision CNC Shop", "Machine Shop Floor", "VMC Area",
    "Tool Crib", "Maintenance Shop", "Shipping & Receiving",
    "Contractor Yard", "Equipment Yard",
]

# Tool-crib stock is not linked to any asset, so nothing above can tell what it
# belongs to. These are the items that still serve one of the surviving machines
# - CT40 tooling and coolant for the Haas, BT30 for the RoboDrill. Turning
# inserts, EDM stock and compressor filters go with the machines they served.
KEEP_PARTS = [
    "CAT40 End Mill Holder 3/4\"", "CAT40 End Mill Holder 1/2\"",
    "CAT40 ER32 Collet Chuck", "CAT40 Shell Mill Arbor",
    "CAT40 Pull Stud (Retention Knob)", "BT40 ER32 Collet Chuck",
    "ER32 Collet 1/2\"", "ER32 Collet 3/8\"", "ER32 Collet 1/4\"", "ER32 Collet 3/4\"",
    "1/2\" 4-Flute Carbide End Mill", "3/4\" 4-Flute Carbide End Mill",
    "1/2\" Ball Nose End Mill", "2\" Indexable Face Mill Body", "R245 Face Mill Insert",
    "1/2\" Carbide Drill 5xD", "3/8\" Carbide Drill 3xD", "Center Drill #3",
    "Coolant - TRIM MicroSol 685 (55 gal)",
    "Spindle Bearing Set - Front (CAT40)", "Spindle Bearing Set - Rear (CAT40)",
    "Ball Screw Nut (Haas VF)", "Ball Screw Support Bearing",
    "Linear Guide Block (THK SSR25)", "Linear Guide Wiper Seal Kit",
    "X-Axis Way Cover (Haas VF-2)", "Servo Motor X-Axis (Haas)",
    "Spindle Drive Belt (Haas)", "ATC Belt (Haas)",
    "Door Interlock Switch", "Proximity Sensor", "Coolant Pump Motor",
    # Deliberately kept: it is stocked, it fits nothing on this machine, and the
    # Haas spec sheet carries a warning about exactly this oil. A demo that shows
    # the warning is more useful with the hazard actually sitting in the crib.
    "Way Lube - Mobil Vactra No.2 (5 gal)",
]

DRY = "--dry-run" in sys.argv

c = Client()


def log(action, what):
    print(f"  {'would ' if DRY else ''}{action}: {what}")


def delete(path, what):
    log("delete", what)
    if DRY:
        return True
    try:
        c.delete(path)
        return True
    except Exception as exc:  # noqa: BLE001 - report and carry on
        print(f"    !! failed: {str(exc)[:200]}")
        return False


def children(asset_id):
    """/assets/children is paged, and its default page is 20. Ask for all of it."""
    return c.get(f"/assets/children/{asset_id}", params={"page": 0, "size": 500})


def subtree(asset_id, out):
    """Collect an asset and everything under it, deepest first."""
    for child in children(asset_id):
        subtree(child["id"], out)
    out.append(asset_id)
    return out


def main():
    print(f"Purging {c.base} down to: {', '.join(KEEP_MACHINES)}")
    if DRY:
        print("DRY RUN - nothing will be deleted\n")

    roots = children(0)
    keep_roots = [a for a in roots if a["name"] in KEEP_MACHINES]
    drop_roots = [a for a in roots if a["name"] not in KEEP_MACHINES]

    missing = set(KEEP_MACHINES) - {a["name"] for a in keep_roots}
    if missing:
        # Better to stop than to delete twenty machines and find out the one we
        # were keeping was spelled differently.
        print(f"!! keep-list machines not found: {sorted(missing)}")
        print("   Seed them first, or fix KEEP_MACHINES. Nothing deleted.")
        return 1

    keep_ids = set()
    for root in keep_roots:
        keep_ids.update(subtree(root["id"], []))
    drop_ids = []
    for root in drop_roots:
        drop_ids.extend(subtree(root["id"], []))
    drop_set = set(drop_ids)

    print(f"\nKeeping {len(keep_ids)} assets across {len(keep_roots)} machines")
    print(f"Dropping {len(drop_ids)} assets across {len(drop_roots)} machines")

    # --- Work orders -------------------------------------------------------
    print("\n== Work orders ==")
    for wo in c.post("/work-orders/search",
                     {"pageNum": 0, "pageSize": 500, "filterFields": []})["content"]:
        asset = wo.get("asset")
        if asset and asset["id"] in drop_set:
            delete(f"/work-orders/{wo['id']}", f"WO #{wo['id']} {wo.get('title', '')[:50]}")

    # --- Preventive maintenances ------------------------------------------
    print("\n== Preventive maintenances ==")
    for pm in c.post("/preventive-maintenances/search",
                     {"pageNum": 0, "pageSize": 500, "filterFields": []})["content"]:
        asset = pm.get("asset")
        if asset and asset["id"] in drop_set:
            delete(f"/preventive-maintenances/{pm['id']}", f"PM {pm.get('name', pm['id'])}")

    # --- Components (unhook the ledger before the position disappears) -----
    print("\n== Components ==")
    for comp in c.get("/components"):
        pos = comp.get("currentPosition") or {}
        if pos.get("id") in drop_set:
            delete(f"/components/{comp['id']}", f"component {comp.get('serialNumber')}")

    # --- Meters ------------------------------------------------------------
    print("\n== Meters ==")
    for asset_id in drop_ids:
        for meter in c.get(f"/meters/asset/{asset_id}"):
            delete(f"/meters/{meter['id']}", f"meter {meter.get('name')} on asset {asset_id}")

    # --- Assets, deepest first --------------------------------------------
    print("\n== Assets ==")
    names = {a["id"]: a["name"] for a in roots}
    for asset_id in drop_ids:
        delete(f"/assets/{asset_id}", f"asset {asset_id} {names.get(asset_id, '')}")

    # --- Parts nothing references any more ---------------------------------
    #
    # A part earns its place three ways: it is on a surviving machine's BOM, a
    # serialized component is one of them, or the machine seed names it. The
    # asset<->part link alone is not enough - pack consumables attach to the
    # sub-assembly that consumes them, and the tool crib stock is not linked to
    # any asset at all.
    print("\n== Orphaned parts ==")
    keep_parts = set(M.NEW_PARTS_NAMES) | set(KEEP_PARTS)
    for comp in c.get("/components"):
        part_type = comp.get("partType") or {}
        if part_type.get("name"):
            keep_parts.add(part_type["name"])

    for part in c.post("/parts/search",
                       {"pageNum": 0, "pageSize": 1000, "filterFields": []})["content"]:
        if part["name"] in keep_parts:
            continue
        used_on = {line["asset"]["id"] for line in c.get(f"/bom-lines/part/{part['id']}")
                   if line.get("asset")}
        if used_on & keep_ids:
            continue
        if {a["id"] for a in c.get(f"/assets/part/{part['id']}")} & keep_ids:
            continue
        delete(f"/parts/{part['id']}", f"part {part.get('name')}")

    # --- Vendors nobody buys from any more ---------------------------------
    print("\n== Unused vendors ==")
    surviving = c.post("/parts/search",
                       {"pageNum": 0, "pageSize": 1000, "filterFields": []})["content"]
    used_vendors = set()
    for part in surviving:
        for supplier in c.get(f"/parts/{part['id']}/sourcing").get("suppliers", []):
            used_vendors.add(supplier["vendorId"])
    for vendor in c.post("/vendors/search",
                         {"pageNum": 0, "pageSize": 500, "filterFields": []})["content"]:
        if vendor["id"] in used_vendors:
            continue
        delete(f"/vendors/{vendor['id']}", f"vendor {vendor.get('name')}")

    # --- Locations with nothing left in them -------------------------------
    print("\n== Empty locations ==")
    for loc in c.post("/locations/search",
                      {"pageNum": 0, "pageSize": 500, "filterFields": []})["content"]:
        if loc["name"] in KEEP_LOCATIONS:
            continue
        if c.get(f"/assets/location/{loc['id']}"):
            continue
        if c.get(f"/locations/children/{loc['id']}"):
            continue
        delete(f"/locations/{loc['id']}", f"location {loc['name']}")

    print("\nDone.")
    if not DRY:
        remaining = children(0)
        print(f"Top-level assets now: {[a['name'] for a in remaining]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
