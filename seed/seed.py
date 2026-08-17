# -*- coding: utf-8 -*-
"""Idempotent seeder for the CNC machine-shop demo data.

Runs in dependency order. Resolves names->ids, persists the id map to ids.json so
the run is resumable and the file-attachment stage can reuse ids.

Usage:
    python seed.py            # run all stages
    python seed.py locations assets ...   # run selected stages
"""
import os
import sys
import json
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from client import Client, ApiError
import data as D

IDS_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ids.json")

c = Client()
CS_ID = None  # companySettings id, resolved at login


def resolve_company_settings():
    global CS_ID
    me = c.get("/auth/me")
    CS_ID = me.get("companySettingsId")
    return CS_ID

# ---------------------------------------------------------------- id registry
ids = {}
if os.path.exists(IDS_PATH):
    with open(IDS_PATH) as f:
        ids = json.load(f)


def save_ids():
    with open(IDS_PATH, "w") as f:
        json.dump(ids, f, indent=2)


def reg(kind, key, val):
    ids.setdefault(kind, {})[str(key)] = val


def get(kind, key):
    return ids.get(kind, {}).get(str(key))


def iso(d):
    """'YYYY-MM-DD' -> ISO instant string."""
    if not d:
        return None
    return f"{d}T08:00:00.000Z"


# ---------------------------------------------------------------- generic helpers
def find_in_search(path, name):
    res = c.search(path, name=name, page_size=200)
    for item in res.get("content", []):
        if item.get("name") == name:
            return item.get("id")
    return None


def get_or_create_search(kind, path, name, body):
    existing = get(kind, name)
    if existing:
        return existing
    fid = find_in_search(path, name)
    if fid:
        reg(kind, name, fid)
        return fid
    res = c.post(path, body)
    fid = res["id"]
    reg(kind, name, fid)
    return fid


def get_or_create_list(kind, path, name, body):
    """For category endpoints exposing GET <path> returning a plain list."""
    existing = get(kind, name)
    if existing:
        return existing
    try:
        lst = c.get(path)
        if isinstance(lst, list):
            for item in lst:
                if item.get("name") == name:
                    reg(kind, name, item["id"])
                    return item["id"]
    except ApiError:
        pass
    res = c.post(path, body)
    fid = res["id"]
    reg(kind, name, fid)
    return fid


# ================================================================ STAGES
def stage_locations():
    print("\n== Locations ==")
    for loc in D.LOCATIONS:
        body = {"name": loc["name"]}
        if loc.get("customId"):
            body["customId"] = loc["customId"]
        if loc.get("address"):
            body["address"] = loc["address"]
        if loc.get("parent"):
            pid = get("location", loc["parent"])
            if pid:
                body["parentLocation"] = {"id": pid}
        fid = get_or_create_search("location", "/locations", loc["name"], body)
        print(f"  {loc['name']} -> {fid}")
    save_ids()


def _cat_body(name, desc):
    return {"name": name, "description": desc, "companySettings": {"id": CS_ID}}


def stage_categories():
    print("\n== Categories ==")
    for name, desc in D.ASSET_CATEGORIES:
        fid = get_or_create_list("asset_cat", "/asset-categories", name, _cat_body(name, desc))
        print(f"  [asset] {name} -> {fid}")
    for name, desc in D.PART_CATEGORIES:
        fid = get_or_create_list("part_cat", "/part-categories", name, _cat_body(name, desc))
        print(f"  [part]  {name} -> {fid}")
    for name, desc in D.METER_CATEGORIES:
        fid = get_or_create_list("meter_cat", "/meter-categories", name, _cat_body(name, desc))
        print(f"  [meter] {name} -> {fid}")
    for name, desc in D.WO_CATEGORIES:
        fid = get_or_create_list("wo_cat", "/work-order-categories", name, _cat_body(name, desc))
        print(f"  [wo]    {name} -> {fid}")
    save_ids()


def stage_vendors():
    print("\n== Vendors ==")
    for v in D.VENDORS:
        body = {
            "name": v["name"],
            "companyName": v.get("companyName") or v["name"],
            "vendorType": v.get("vendorType", ""),
            "phone": v.get("phone", ""),
            "email": v.get("email", ""),
            "website": v.get("website", ""),
        }
        fid = get_or_create_search("vendor", "/vendors", v["name"], body)
        print(f"  {v['name']} -> {fid}")
    save_ids()


ROLE_IDS = {
    "Administrator": 2,
    "Limited Administrator": 3,
    "Technician": 4,
    "Viewer": 6,        # "View Only" (free)
    "View Only": 6,
}
DEMO_PASSWORD = "Demo1234!"


def _existing_users():
    res = c.post("/users/search", {"filterFields": [], "pageSize": 200, "pageNum": 0, "direction": "DESC"})
    m = {}
    for u in res.get("content", []):
        if u.get("email"):
            m[u["email"].lower()] = u["id"]
    return m


def stage_users():
    print("\n== Users ==")
    existing = _existing_users()
    for i, u in enumerate(D.USERS):
        email = u["email"].lower()
        if email in existing:
            uid = existing[email]
            reg("user", email, uid)
            print(f"  (exists) {email} -> {uid}")
            _patch_user(uid, u)
            continue
        role_id = ROLE_IDS[u["role"]]
        phone = f"+1-713-555-{1000 + i:04d}"[:16]
        # 1) invite (creates invitation tied to role+company)
        try:
            c.post("/users/invite", {"role": {"id": role_id}, "emails": [u["email"]],
                                     "disableSendingEmail": True})
        except ApiError as e:
            print(f"    invite warn {email}: {e}")
        # 2) signup -> creates enabled OwnUser in company
        try:
            res = c.post("/auth/signup", {
                "email": u["email"], "password": DEMO_PASSWORD,
                "firstName": u["firstName"], "lastName": u["lastName"],
                "phone": phone, "role": {"id": role_id},
            })
            uid = None
            if isinstance(res, dict):
                user_obj = res.get("user") or res
                uid = user_obj.get("id") if isinstance(user_obj, dict) else None
        except ApiError as e:
            print(f"    signup FAIL {email}: {e}")
            continue
        if not uid:
            uid = _existing_users().get(email)
        if uid:
            reg("user", email, uid)
            print(f"  {email} -> {uid} ({u['role']})")
            _patch_user(uid, u)
        else:
            print(f"    could not resolve id for {email}")
    save_ids()


def _patch_user(uid, u):
    body = {
        "firstName": u["firstName"], "lastName": u["lastName"],
        "rate": int(u.get("rate", 0)),
        "jobTitle": u.get("jobTitle", ""),
        "phone": "",
    }
    loc_id = get("location", u.get("location"))
    if loc_id:
        body["location"] = {"id": loc_id}
    try:
        c.patch(f"/users/{uid}", body)
    except ApiError as e:
        print(f"    patch user {uid} warn: {e}")


def stage_teams():
    print("\n== Teams ==")
    for t in D.TEAMS:
        member_ids = [get("user", m.lower()) for m in t["members"]]
        member_ids = [{"id": x} for x in member_ids if x]
        body = {"name": t["name"], "description": t.get("description", ""), "users": member_ids}
        fid = get_or_create_search("team", "/teams", t["name"], body)
        print(f"  {t['name']} -> {fid} ({len(member_ids)} members)")
    save_ids()


def stage_assets():
    print("\n== Assets ==")
    for a in D.ASSETS:
        (name, customId, cat, manufacturer, model, serial, loc, status,
         in_service, acq_cost, primary_email) = a
        body = {
            "name": name,
            "customId": customId,
            "serialNumber": serial,
            "model": model,
            "manufacturer": manufacturer,
            "status": status,
            "acquisitionCost": float(acq_cost),
            "inServiceDate": iso(in_service),
            "description": f"{manufacturer} {model}",
        }
        lid = get("location", loc)
        if lid:
            body["location"] = {"id": lid}
        cid = get("asset_cat", cat)
        if cid:
            body["category"] = {"id": cid}
        if primary_email:
            uid = get("user", primary_email.lower())
            if uid:
                body["primaryUser"] = {"id": uid}
                body["assignedTo"] = [{"id": uid}]
        fid = get_or_create_search("asset", "/assets", name, body)
        print(f"  {name} -> {fid}")
    save_ids()


def stage_asset_parents():
    print("\n== Asset parent links ==")
    for child, parent in D.ASSET_PARENTS.items():
        cid = get("asset", child)
        pid = get("asset", parent)
        if cid and pid:
            try:
                c.patch(f"/assets/{cid}", {"name": child, "status": "OPERATIONAL",
                                           "parentAsset": {"id": pid}})
                print(f"  {child} -> parent {parent}")
            except ApiError as e:
                print(f"  parent link warn {child}: {e}")
    save_ids()


def stage_parts():
    print("\n== Parts ==")
    for p in D.PARTS:
        name, pn, cat, cost, qty, minq, unit, vendor = p
        body = {
            "name": name, "barcode": pn, "cost": float(cost),
            "quantity": float(qty), "minQuantity": float(minq), "unit": unit,
            "description": f"{cat} - PN {pn}",
        }
        cid = get("part_cat", cat)
        if cid:
            body["category"] = {"id": cid}
        vid = get("vendor", vendor)
        if vid:
            body["vendors"] = [{"id": vid}]
        fid = get_or_create_search("part", "/parts", name, body)
        print(f"  {name} -> {fid}")
    save_ids()


def stage_part_asset_links():
    print("\n== Part<->Asset links (via asset PATCH) ==")
    # build asset -> [partIds]
    asset_parts = {}
    for part_name, asset_names in D.PART_ASSETS.items():
        pid = get("part", part_name)
        if not pid:
            continue
        for an in asset_names:
            asset_parts.setdefault(an, []).append(pid)
    for asset_name, part_ids in asset_parts.items():
        aid = get("asset", asset_name)
        if not aid:
            continue
        try:
            c.patch(f"/assets/{aid}", {"name": asset_name, "status": "OPERATIONAL",
                                       "parts": [{"id": x} for x in part_ids]})
            print(f"  {asset_name}: +{len(part_ids)} parts")
        except ApiError as e:
            print(f"  link warn {asset_name}: {e}")


def stage_multiparts():
    print("\n== MultiParts (sets) ==")
    existing = {}
    try:
        lst = c.get("/multi-parts")
        items = lst if isinstance(lst, list) else lst.get("content", [])
        for it in items:
            existing[it.get("name")] = it.get("id")
    except ApiError:
        pass
    for name, part_names in D.MULTIPARTS:
        if get("multipart", name) or name in existing:
            fid = get("multipart", name) or existing[name]
            reg("multipart", name, fid)
            print(f"  (exists) {name} -> {fid}")
            continue
        part_ids = [get("part", pn) for pn in part_names]
        part_ids = [{"id": x} for x in part_ids if x]
        body = {"name": name, "parts": part_ids}
        res = c.post("/multi-parts", body)
        fid = res["id"]
        reg("multipart", name, fid)
        print(f"  {name} -> {fid} ({len(part_ids)} parts)")
    save_ids()


def stage_meters():
    print("\n== Meters ==")
    for m in D.METERS:
        name, asset, unit, cat, freq, user_email = m
        aid = get("asset", asset)
        if not aid:
            print(f"  skip {name}: no asset")
            continue
        body = {"name": name, "unit": unit, "updateFrequency": int(freq), "asset": {"id": aid}}
        cid = get("meter_cat", cat)
        if cid:
            body["meterCategory"] = {"id": cid}
        uid = get("user", user_email.lower()) if user_email else None
        if uid:
            body["users"] = [{"id": uid}]
        fid = get_or_create_search("meter", "/meters", name, body)
        print(f"  {name} -> {fid}")
    save_ids()


def stage_readings():
    print("\n== Readings (latest per meter only; dates not backdatable) ==")
    latest = {}
    for r in D.METER_READINGS:
        meter, date, value, notes = r
        if meter not in latest or date > latest[meter][0]:
            latest[meter] = (date, value)
    done = ids.setdefault("reading_done", {})
    for meter, (date, value) in latest.items():
        if done.get(meter):
            print(f"  (done) {meter}")
            continue
        mid = get("meter", meter)
        if not mid:
            print(f"  skip {meter}: no meter")
            continue
        try:
            c.post("/readings", {"value": float(value), "meter": {"id": mid}})
            done[meter] = True
            print(f"  {meter} = {value}")
        except ApiError as e:
            print(f"  reading warn {meter}: {e}")
    save_ids()


def _rec_type(days):
    if days <= 1:
        return "DAILY"
    if days <= 7:
        return "WEEKLY"
    if days >= 365:
        return "YEARLY"
    return "MONTHLY"


def stage_pm():
    print("\n== Preventive Maintenance ==")
    done = ids.setdefault("pm_done", {})
    for pm in D.PM:
        title, desc, asset, cat, priority, freq, user_email, dur = pm
        if done.get(title):
            print(f"  (done) {title}")
            continue
        body = {
            "title": title,
            "description": desc,
            "priority": priority,
            "estimatedDuration": float(dur),
            "name": title,                       # schedule name
            "frequency": int(freq),
            "recurrenceType": _rec_type(freq),
            "recurrenceBasedOn": "SCHEDULED_DATE",
            "startsOn": "2026-06-15T08:00:00.000Z",
        }
        if body["recurrenceType"] == "WEEKLY":
            # 0=Monday .. 6=Sunday
            weekly_days = {
                "Weekly Tool Holder Inspection": [2],   # Wednesday
                "Weekly Way Cover Inspection": [4],      # Friday
                "Weekly Oil Mist Filter Check": [3],     # Thursday
            }
            body["daysOfWeek"] = weekly_days.get(title, [0])  # default Monday
        if asset:
            aid = get("asset", asset)
            if aid:
                body["asset"] = {"id": aid}
        cid = get("wo_cat", cat)
        if cid:
            body["category"] = {"id": cid}
        uid = get("user", user_email.lower()) if user_email else None
        if uid:
            body["assignedTo"] = [{"id": uid}]
            body["primaryUser"] = {"id": uid}
        try:
            res = c.post("/preventive-maintenances", body)
            done[title] = res.get("id", True)
            print(f"  {title} -> {res.get('id')}")
        except ApiError as e:
            print(f"  PM FAIL {title}: {e}")
    save_ids()


def stage_work_orders():
    print("\n== Work Orders ==")
    done = ids.setdefault("wo_done", {})
    for wo in D.WORK_ORDERS:
        (title, desc, asset, priority, status, cat, user_email,
         due, completed_on, feedback) = wo
        if done.get(title):
            print(f"  (done) {title}")
            continue
        body = {"title": title, "description": desc, "priority": priority}
        # use completedOn as dueDate for completed ones, else due
        body["dueDate"] = iso(completed_on or due)
        if asset:
            aid = get("asset", asset)
            if aid:
                body["asset"] = {"id": aid}
        cid = get("wo_cat", cat)
        if cid:
            body["category"] = {"id": cid}
        uid = get("user", user_email.lower()) if user_email else None
        if uid:
            body["assignedTo"] = [{"id": uid}]
            body["primaryUser"] = {"id": uid}
        try:
            res = c.post("/work-orders", body)
            wid = res["id"]
        except ApiError as e:
            print(f"  WO FAIL {title}: {e}")
            continue
        # status transitions
        if status == "IN_PROGRESS":
            try:
                c.patch(f"/work-orders/{wid}/change-status", {"status": "IN_PROGRESS"})
            except ApiError as e:
                print(f"    status warn {title}: {e}")
        elif status == "COMPLETE":
            try:
                c.patch(f"/work-orders/{wid}/change-status",
                        {"status": "COMPLETE", "feedback": feedback or ""})
            except ApiError as e:
                print(f"    complete warn {title}: {e}")
        done[title] = wid
        print(f"  {title} -> {wid} [{status}]")
    save_ids()


STAGES = [
    ("locations", stage_locations),
    ("categories", stage_categories),
    ("vendors", stage_vendors),
    ("users", stage_users),
    ("teams", stage_teams),
    ("assets", stage_assets),
    ("asset_parents", stage_asset_parents),
    ("parts", stage_parts),
    ("part_asset_links", stage_part_asset_links),
    ("multiparts", stage_multiparts),
    ("meters", stage_meters),
    ("readings", stage_readings),
    ("pm", stage_pm),
    ("work_orders", stage_work_orders),
]


def main():
    c.login()
    resolve_company_settings()
    print("Logged in to", c.base, "| companySettingsId =", CS_ID)
    selected = sys.argv[1:]
    for key, fn in STAGES:
        if selected and key not in selected:
            continue
        try:
            fn()
        except Exception as e:
            print(f"!! stage {key} error: {e}")
            save_ids()
            raise
    save_ids()
    print("\nDone.")


if __name__ == "__main__":
    main()
