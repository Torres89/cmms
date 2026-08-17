# -*- coding: utf-8 -*-
"""Thin REST client for the Atlas CMMS production API used by the seed scripts."""
import os
import sys
import json
import requests

BASE = os.getenv("CMMS_API_URL", "https://api.cmms-demo.automationhr-ai.com")
EMAIL = os.getenv("CMMS_EMAIL", "admin@test.com")
PASSWORD = os.getenv("CMMS_PASSWORD", "Admin1234")


class Client:
    def __init__(self, base=BASE, email=EMAIL, password=PASSWORD):
        self.base = base.rstrip("/")
        self.email = email
        self.password = password
        self._token = None
        self.s = requests.Session()

    def login(self):
        r = self.s.post(f"{self.base}/auth/signin",
                        json={"email": self.email, "password": self.password, "type": "client"},
                        timeout=30)
        r.raise_for_status()
        self._token = r.json()["accessToken"]
        return self._token

    def h(self, extra=None):
        if not self._token:
            self.login()
        hd = {"Authorization": f"Bearer {self._token}", "Content-Type": "application/json"}
        if extra:
            hd.update(extra)
        return hd

    def _check(self, r):
        if r.status_code == 401:
            self.login()
            return None  # signal retry
        if r.status_code >= 400:
            raise ApiError(r.status_code, r.text)
        if r.status_code == 204 or not r.text:
            return {"success": True}
        try:
            return r.json()
        except Exception:
            return r.text

    def get(self, path, params=None):
        r = self.s.get(f"{self.base}{path}", headers=self.h(), params=params, timeout=60)
        res = self._check(r)
        if res is None:
            r = self.s.get(f"{self.base}{path}", headers=self.h(), params=params, timeout=60)
            res = self._check(r)
        return res

    def post(self, path, body=None):
        r = self.s.post(f"{self.base}{path}", headers=self.h(), data=json.dumps(body or {}), timeout=60)
        res = self._check(r)
        if res is None:
            r = self.s.post(f"{self.base}{path}", headers=self.h(), data=json.dumps(body or {}), timeout=60)
            res = self._check(r)
        return res

    def patch(self, path, body=None):
        r = self.s.patch(f"{self.base}{path}", headers=self.h(), data=json.dumps(body or {}), timeout=60)
        res = self._check(r)
        if res is None:
            r = self.s.patch(f"{self.base}{path}", headers=self.h(), data=json.dumps(body or {}), timeout=60)
            res = self._check(r)
        return res

    def delete(self, path):
        r = self.s.delete(f"{self.base}{path}", headers=self.h(), timeout=60)
        return self._check(r) or {"success": True}

    def search(self, path, name=None, page_size=200):
        crit = {"filterFields": [], "pageSize": page_size, "pageNum": 0, "direction": "DESC"}
        if name is not None:
            crit["filterFields"].append({"field": "name", "value": name, "operation": "cn"})
        return self.post(f"{path}/search", crit)

    def upload(self, file_path, folder="demo", hidden="false", file_type="OTHER"):
        import mimetypes
        if not self._token:
            self.login()
        mime = mimetypes.guess_type(file_path)[0] or "application/octet-stream"

        def _do():
            with open(file_path, "rb") as fh:
                files = {"files": (os.path.basename(file_path), fh, mime)}
                data = {"folder": folder, "hidden": hidden, "type": file_type}
                return self.s.post(f"{self.base}/files/upload",
                                   headers={"Authorization": f"Bearer {self._token}"},
                                   files=files, data=data, timeout=120)
        r = _do()
        if r.status_code == 401:
            self.login()
            r = _do()
        if r.status_code >= 400:
            raise ApiError(r.status_code, r.text)
        return r.json()


class ApiError(Exception):
    def __init__(self, status, text):
        self.status = status
        self.text = text
        super().__init__(f"API {status}: {text[:400]}")
