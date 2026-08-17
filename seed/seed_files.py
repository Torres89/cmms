# -*- coding: utf-8 -*-
"""Upload generated files and attach them to assets / parts / PMs / work orders.
Idempotent: caches uploaded file ids in ids.json under 'file' and attach state
under 'attach_done'."""
import os
import sys
import json

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from client import Client, ApiError

HERE = os.path.dirname(os.path.abspath(__file__))
FILES = os.path.join(HERE, "files")
IDS_PATH = os.path.join(HERE, "ids.json")

c = Client()
with open(IDS_PATH) as f:
    ids = json.load(f)


def save():
    with open(IDS_PATH, "w") as f:
        json.dump(ids, f, indent=2)


def aid(name):
    return ids.get("asset", {}).get(name)


def pid(name):
    return ids.get("part", {}).get(name)


def pm_id(title):
    v = ids.get("pm_done", {}).get(title)
    return v if isinstance(v, int) else None


def wo_id(title):
    return ids.get("wo_done", {}).get(title)


# ----- file -> targets mappings
MANUALS = {  # pdf -> asset names
    "Haas-VF2-Operators-Manual.pdf": ["Haas VF-2 #1", "Haas VF-2 #2"],
    "Haas-Mill-Maintenance-Guide.pdf": ["Haas VF-2 #1", "Haas VF-2 #2", "Haas VF-4"],
    "DMG-MORI-CMX800V-Manual.pdf": ["DMG MORI CMX 800 V"],
    "Mazak-QTN200M-Operation-Manual.pdf": ["Mazak QTN-200M"],
    "FANUC-RoboDrill-Maintenance.pdf": ["Fanuc RoboDrill"],
    "Atlas-Copco-GA37-Service-Manual.pdf": ["Atlas Copco Compressor"],
}
SDS = {  # pdf -> part names
    "TRIM-MicroSol-685-SDS.pdf": ["Coolant - TRIM MicroSol 685 (55 gal)"],
    "Mobil-Vactra-No2-SDS.pdf": ["Way Lube - Mobil Vactra No.2 (5 gal)"],
    "Mobil-DTE-25-SDS.pdf": ["Hydraulic Oil - Mobil DTE 25 (5 gal)"],
    "Mobil-Velocite-10-SDS.pdf": ["Spindle Oil - Mobil Velocite No.10 (5 gal)"],
}
CHECKLISTS = {  # pdf -> PM titles
    "Daily-Machine-Inspection-Checklist.pdf": ["Daily Machine Inspection - VMC", "Daily Machine Inspection - Lathe"],
    "Monthly-PM-Checklist-VMC.pdf": ["Monthly ATC Inspection", "Monthly Safety Interlock Test"],
    "Quarterly-Backlash-Measurement-Form.pdf": ["Quarterly Backlash Measurement"],
    "Annual-Accuracy-Audit-Template.pdf": ["Annual Accuracy Audit (Ballbar Test)"],
    "Coolant-Management-Log.pdf": ["Daily Coolant Check - VMC Area", "Daily Coolant Check - Lathe Area"],
}
PHOTOS = {  # jpg -> WO titles
    "doosan-spindle-damage-01.jpg": ["Emergency spindle repair - Doosan Lynx"],
    "doosan-spindle-damage-02.jpg": ["Emergency spindle repair - Doosan Lynx"],
    "bar-feeder-install-complete.jpg": ["Install new bar feeder - Mazak QTN"],
    "vf4-way-cover-torn.jpg": ["Replace torn Y-axis way cover - VF-4"],
}


def upload_file(fn, ftype):
    cache = ids.setdefault("file", {})
    if fn in cache:
        return cache[fn]
    path = os.path.join(FILES, fn)
    res = c.upload(path, folder="demo", hidden="false", file_type=ftype)
    fid = res[0]["id"]
    cache[fn] = fid
    save()
    return fid


def main():
    c.login()
    print("Uploading files...")
    # upload all and bucket by target
    asset_files = {}   # asset name -> [file id]
    part_files = {}
    pm_files = {}
    wo_files = {}

    for fn, targets in MANUALS.items():
        fid = upload_file(fn, "OTHER")
        print("  up", fn, "->", fid)
        for t in targets:
            asset_files.setdefault(t, []).append(fid)
    for fn, targets in SDS.items():
        fid = upload_file(fn, "OTHER")
        print("  up", fn, "->", fid)
        for t in targets:
            part_files.setdefault(t, []).append(fid)
    for fn, targets in CHECKLISTS.items():
        fid = upload_file(fn, "OTHER")
        print("  up", fn, "->", fid)
        for t in targets:
            pm_files.setdefault(t, []).append(fid)
    for fn, targets in PHOTOS.items():
        fid = upload_file(fn, "IMAGE")
        print("  up", fn, "->", fid)
        for t in targets:
            wo_files.setdefault(t, []).append(fid)

    done = ids.setdefault("attach_done", {})

    print("\nAttaching manuals to assets...")
    for name, fids in asset_files.items():
        key = "asset:" + name
        if done.get(key):
            print("  (done)", name); continue
        a = aid(name)
        if not a:
            print("  !no asset", name); continue
        try:
            c.patch(f"/assets/{a}", {"name": name, "status": "OPERATIONAL",
                                     "files": [{"id": x} for x in fids]})
            done[key] = True
            print(f"  {name}: {len(fids)} file(s)")
        except ApiError as e:
            print(f"  FAIL {name}: {e}")
    save()

    print("\nAttaching SDS to parts...")
    for name, fids in part_files.items():
        key = "part:" + name
        if done.get(key):
            print("  (done)", name); continue
        p = pid(name)
        if not p:
            print("  !no part", name); continue
        try:
            c.patch(f"/parts/{p}", {"name": name, "files": [{"id": x} for x in fids]})
            done[key] = True
            print(f"  {name}: {len(fids)} file(s)")
        except ApiError as e:
            print(f"  FAIL {name}: {e}")
    save()

    print("\nAttaching checklists to PMs...")
    for title, fids in pm_files.items():
        key = "pm:" + title
        if done.get(key):
            print("  (done)", title); continue
        p = pm_id(title)
        if not p:
            print("  !no PM", title); continue
        try:
            c.patch(f"/preventive-maintenances/{p}",
                    {"title": title, "name": title, "files": [{"id": x} for x in fids]})
            done[key] = True
            print(f"  {title}: {len(fids)} file(s)")
        except ApiError as e:
            print(f"  FAIL {title}: {e}")
    save()

    print("\nAttaching photos to work orders...")
    for title, fids in wo_files.items():
        key = "wo:" + title
        if done.get(key):
            print("  (done)", title); continue
        w = wo_id(title)
        if not w:
            print("  !no WO", title); continue
        try:
            c.patch(f"/work-orders/files/{w}/add", [{"id": x} for x in fids])
            done[key] = True
            print(f"  {title}: {len(fids)} file(s)")
        except ApiError as e:
            print(f"  FAIL {title}: {e}")
    save()
    print("\nFile stage done.")


if __name__ == "__main__":
    main()
