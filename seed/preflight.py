# -*- coding: utf-8 -*-
"""Preflight: verify METER and FILE plan features work on the live instance.
Creates throwaway entities, tests, then deletes them."""
import sys
import os
from client import Client, ApiError

c = Client()
c.login()
print("login OK")

created = {}
try:
    loc = c.post("/locations", {"name": "ZZ_PREFLIGHT_LOC", "customId": "ZZ-PRE"})
    created["loc"] = loc["id"]
    print("location OK id=", loc["id"])

    asset = c.post("/assets", {"name": "ZZ_PREFLIGHT_ASSET", "location": {"id": loc["id"]},
                               "status": "OPERATIONAL"})
    created["asset"] = asset["id"]
    print("asset OK id=", asset["id"])

    # Meter (requires METER plan feature)
    try:
        meter = c.post("/meters", {"name": "ZZ_PRE_METER", "unit": "hours", "updateFrequency": 7,
                                   "asset": {"id": asset["id"]}})
        created["meter"] = meter["id"]
        print("METER FEATURE: OK id=", meter["id"])
        # Reading
        try:
            rd = c.post("/readings", {"value": 123.4, "meter": {"id": meter["id"]}})
            print("READING: OK", rd.get("id"))
        except ApiError as e:
            print("READING: FAIL", e)
    except ApiError as e:
        print("METER FEATURE: FAIL", e)

    # File upload (requires FILE plan feature + FILE_ATTACHMENTS entitlement)
    tmpf = os.path.join(os.path.dirname(__file__), "ZZ_pre.txt")
    with open(tmpf, "w") as f:
        f.write("preflight test file")
    try:
        up = c.upload(tmpf, folder="demo", hidden="false", file_type="OTHER")
        fid = up[0]["id"]
        print("FILE UPLOAD: OK id=", fid, "url=", up[0].get("url", "")[:60])
        # attach to asset
        try:
            c.patch(f"/assets/{asset['id']}", {"files": [{"id": fid}]})
            print("FILE ATTACH to asset: OK")
        except ApiError as e:
            print("FILE ATTACH: FAIL", e)
        try:
            c.delete(f"/files/{fid}")
        except Exception:
            pass
    except ApiError as e:
        print("FILE UPLOAD: FAIL", e)
    os.remove(tmpf)

finally:
    # cleanup
    if "meter" in created:
        try: c.delete(f"/meters/{created['meter']}")
        except Exception as e: print("cleanup meter", e)
    if "asset" in created:
        try: c.delete(f"/assets/{created['asset']}")
        except Exception as e: print("cleanup asset", e)
    if "loc" in created:
        try: c.delete(f"/locations/{created['loc']}")
        except Exception as e: print("cleanup loc", e)
    print("cleanup done")
