# -*- coding: utf-8 -*-
"""Repair pass: the destructive PATCH mappers nulled fields not resent during the
link/attach steps. Re-PATCH every asset, part, and PM with its FULL body plus all
relations (parts, vendors, files, parent). Idempotent and safe to re-run."""
import os
import sys
import json

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from client import Client, ApiError
import data as D
from seed_files import MANUALS, SDS, CHECKLISTS

HERE = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(HERE, "ids.json")) as f:
    ids = json.load(f)

c = Client()


def iso(d):
    return f"{d}T08:00:00.000Z" if d else None


def A(name):
    return ids.get("asset", {}).get(name)


def P(name):
    return ids.get("part", {}).get(name)


def U(email):
    return ids.get("user", {}).get(email.lower()) if email else None


def FID(fn):
    return ids.get("file", {}).get(fn)


def lookup(kind, key):
    return ids.get(kind, {}).get(key)


# --- build relation maps -----------------------------------------------------
asset_parts = {}
for part_name, asset_names in D.PART_ASSETS.items():
    p = P(part_name)
    if not p:
        continue
    for an in asset_names:
        asset_parts.setdefault(an, []).append(p)

asset_files = {}
for fn, targets in MANUALS.items():
    fid = FID(fn)
    if not fid:
        continue
    for t in targets:
        asset_files.setdefault(t, []).append(fid)

part_files = {}
for fn, targets in SDS.items():
    fid = FID(fn)
    if not fid:
        continue
    for t in targets:
        part_files.setdefault(t, []).append(fid)

pm_files = {}
for fn, targets in CHECKLISTS.items():
    fid = FID(fn)
    if not fid:
        continue
    for t in targets:
        pm_files.setdefault(t, []).append(fid)


def repair_assets():
    print("\n== Repair assets ==")
    for a in D.ASSETS:
        (name, customId, cat, manufacturer, model, serial, loc, status,
         in_service, acq_cost, primary_email) = a
        aid = A(name)
        if not aid:
            continue
        body = {
            "name": name, "customId": customId, "serialNumber": serial,
            "model": model, "manufacturer": manufacturer, "status": status,
            "acquisitionCost": float(acq_cost), "inServiceDate": iso(in_service),
            "description": f"{manufacturer} {model}",
        }
        lid = lookup("location", loc)
        if lid:
            body["location"] = {"id": lid}
        cid = lookup("asset_cat", cat)
        if cid:
            body["category"] = {"id": cid}
        uid = U(primary_email)
        if uid:
            body["primaryUser"] = {"id": uid}
            body["assignedTo"] = [{"id": uid}]
        if name in D.ASSET_PARENTS:
            pid = A(D.ASSET_PARENTS[name])
            if pid:
                body["parentAsset"] = {"id": pid}
        if name in asset_parts:
            body["parts"] = [{"id": x} for x in asset_parts[name]]
        if name in asset_files:
            body["files"] = [{"id": x} for x in asset_files[name]]
        try:
            c.patch(f"/assets/{aid}", body)
            print(f"  {name}: parts={len(asset_parts.get(name,[]))} files={len(asset_files.get(name,[]))}")
        except ApiError as e:
            print(f"  FAIL {name}: {e}")


def repair_parts():
    print("\n== Repair parts ==")
    for p in D.PARTS:
        name, pn, cat, cost, qty, minq, unit, vendor = p
        pid = P(name)
        if not pid:
            continue
        body = {
            "name": name, "barcode": pn, "cost": float(cost),
            "quantity": float(qty), "minQuantity": float(minq), "unit": unit,
            "description": f"{cat} - PN {pn}",
        }
        cid = lookup("part_cat", cat)
        if cid:
            body["category"] = {"id": cid}
        vid = lookup("vendor", vendor)
        if vid:
            body["vendors"] = [{"id": vid}]
        if name in part_files:
            body["files"] = [{"id": x} for x in part_files[name]]
        try:
            c.patch(f"/parts/{pid}", body)
            extra = f" files={len(part_files[name])}" if name in part_files else ""
            print(f"  {name}{extra}")
        except ApiError as e:
            print(f"  FAIL {name}: {e}")


def _rec_type(days):
    if days <= 1:
        return "DAILY"
    if days <= 7:
        return "WEEKLY"
    if days >= 365:
        return "YEARLY"
    return "MONTHLY"


def repair_pms():
    print("\n== Repair PMs ==")
    pm_done = ids.get("pm_done", {})
    for pm in D.PM:
        title, desc, asset, cat, priority, freq, user_email, dur = pm
        pmid = pm_done.get(title)
        if not isinstance(pmid, int):
            continue
        body = {
            "title": title, "name": title, "description": desc,
            "priority": priority, "estimatedDuration": float(dur),
        }
        if asset:
            aid = A(asset)
            if aid:
                body["asset"] = {"id": aid}
        cid = lookup("wo_cat", cat)
        if cid:
            body["category"] = {"id": cid}
        uid = U(user_email)
        if uid:
            body["assignedTo"] = [{"id": uid}]
            body["primaryUser"] = {"id": uid}
        if title in pm_files:
            body["files"] = [{"id": x} for x in pm_files[title]]
        try:
            c.patch(f"/preventive-maintenances/{pmid}", body)
            extra = f" files={len(pm_files[title])}" if title in pm_files else ""
            print(f"  {title}{extra}")
        except ApiError as e:
            print(f"  FAIL {title}: {e}")


if __name__ == "__main__":
    c.login()
    print("Logged in. Running full re-PATCH repair...")
    repair_assets()
    repair_parts()
    repair_pms()
    print("\nRepair done.")
