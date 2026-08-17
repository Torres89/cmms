# -*- coding: utf-8 -*-
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from client import Client
c = Client(); c.login()


def count(path):
    r = c.post(f"{path}/search", {"filterFields": [], "pageSize": 1, "pageNum": 0, "direction": "DESC"})
    return r.get("totalElements")


def all_items(path, ps=300):
    r = c.post(f"{path}/search", {"filterFields": [], "pageSize": ps, "pageNum": 0, "direction": "DESC"})
    return r.get("content", [])

print("=== ENTITY COUNTS ===")
for e in ["locations", "assets", "parts", "vendors", "teams", "meters",
          "work-orders", "preventive-maintenances"]:
    print(f"  {e:26s}: {count('/' + e)}")
mp = c.get("/multi-parts"); print(f"  {'multi-parts':26s}: {len(mp) if isinstance(mp,list) else mp.get('totalElements')}")
us = c.post("/users/search", {"filterFields": [], "pageSize": 1, "pageNum": 0, "direction": "DESC"})
print(f"  {'users':26s}: {us.get('totalElements')}")
fl = c.post("/files/search", {"filterFields": [], "pageSize": 1, "pageNum": 0, "direction": "DESC"})
print(f"  {'files':26s}: {fl.get('totalElements')}")

print("\n=== RELATIONS / ATTACHMENTS ===")
assets = all_items("/assets")
print(f"  assets with files:   {sum(1 for a in assets if a.get('files'))}")
print(f"  assets with parts:   {sum(1 for a in assets if a.get('parts'))}")
print(f"  assets with parent:  {sum(1 for a in assets if a.get('parentAsset'))}")
print(f"  assets w/ primaryUser:{sum(1 for a in assets if a.get('primaryUser'))}")
parts = all_items("/parts")
print(f"  parts with vendor:   {sum(1 for p in parts if p.get('vendors'))}")
print(f"  parts with files:    {sum(1 for p in parts if p.get('files'))}")
print(f"  parts with category: {sum(1 for p in parts if p.get('category'))}")
pms = all_items("/preventive-maintenances")
print(f"  PMs with files:      {sum(1 for p in pms if p.get('files'))}")
print(f"  PMs with schedule:   {sum(1 for p in pms if p.get('schedule'))}")
print(f"  PMs with asset:      {sum(1 for p in pms if p.get('asset'))}")
wos = all_items("/work-orders")
from collections import Counter
st = Counter(w.get("status") for w in wos)
print(f"  WO statuses:         {dict(st)}")
print(f"  WOs with files:      {sum(1 for w in wos if w.get('files'))}")
print(f"  WOs completed w/ feedback: {sum(1 for w in wos if w.get('status')=='COMPLETE' and w.get('feedback'))}")
meters = all_items("/meters")
print(f"  meters with category:{sum(1 for m in meters if m.get('meterCategory'))}")
print("\nVerification complete.")
