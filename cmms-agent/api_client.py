import os
import requests


class APIClient:
    def __init__(self):
        self.base_url = os.getenv("CMMS_API_URL", "http://localhost:8080")
        self.email = os.getenv("CMMS_EMAIL")
        self.password = os.getenv("CMMS_PASSWORD")
        self._token = None

    def _login(self):
        try:
            resp = requests.post(
                f"{self.base_url}/auth/signin",
                json={
                    "email": self.email,
                    "password": self.password,
                    "type": "client",
                },
                timeout=10,
            )
        except requests.ConnectionError:
            raise ConnectionError(
                f"Cannot reach API at {self.base_url}. Is the server running?"
            )

        if resp.status_code != 200:
            raise PermissionError(
                "Login failed. Check CMMS_EMAIL and CMMS_PASSWORD in .env"
            )

        data = resp.json()
        self._token = data.get("accessToken") or data.get("token") or data.get("access_token")
        if not self._token:
            raise PermissionError(
                f"Login succeeded but no token found in response: {list(data.keys())}"
            )

    def _headers(self):
        if not self._token:
            self._login()
        return {
            "Authorization": f"Bearer {self._token}",
            "Content-Type": "application/json",
        }

    def _request(self, method, path, **kwargs):
        url = f"{self.base_url}{path}"
        kwargs.setdefault("timeout", 15)

        try:
            resp = requests.request(method, url, headers=self._headers(), **kwargs)
        except requests.ConnectionError:
            return {
                "error": f"Cannot reach API at {self.base_url}. Is the server running?"
            }

        if resp.status_code == 401:
            self._token = None
            try:
                resp = requests.request(method, url, headers=self._headers(), **kwargs)
            except requests.ConnectionError:
                return {"error": f"Cannot reach API at {self.base_url}."}

        if resp.status_code == 403:
            return {
                "error": "Permission denied. Your user role doesn't have access to this action."
            }

        if resp.status_code == 404:
            return {"error": "Entity not found."}

        if resp.status_code >= 400:
            try:
                detail = resp.json().get("message", resp.text)
            except Exception:
                detail = resp.text
            return {"error": f"API error ({resp.status_code}): {detail}"}

        if resp.status_code == 204 or not resp.text:
            return {"success": True}

        return resp.json()

    def get(self, path, params=None):
        return self._request("GET", path, params=params)

    def post(self, path, body=None):
        return self._request("POST", path, json=body or {})

    def post_search(self, path, criteria=None):
        if criteria is None:
            criteria = {}
        criteria.setdefault("filterFields", [])
        criteria.setdefault("pageSize", 10)
        criteria.setdefault("pageNum", 0)
        criteria.setdefault("direction", "DESC")
        return self._request("POST", path, json=criteria)

    def patch(self, path, body=None):
        return self._request("PATCH", path, json=body or {})

    def delete(self, path):
        return self._request("DELETE", path)
