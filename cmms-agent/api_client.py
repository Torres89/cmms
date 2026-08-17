"""
HTTP client for the Atlas CMMS API.

The client is **per request**: it is constructed with the calling user's own
token, so every call the agent makes is executed as that user and inherits the
API's org isolation and role checks. There is no service account.
"""

import os
from contextvars import ContextVar
from typing import Optional

import requests

# The client bound to the request currently being served. Tool modules read it
# through :func:`get_client` so they never need to know about auth.
_current_client: ContextVar[Optional["APIClient"]] = ContextVar(
    "current_api_client", default=None
)


class APIClient:
    def __init__(self, token: str, base_url: Optional[str] = None):
        if not token:
            raise ValueError("APIClient requires the caller's access token")
        self.base_url = (base_url or os.getenv("CMMS_API_URL", "http://localhost:8080")).rstrip("/")
        self._token = token

    @classmethod
    def login(cls, email: str, password: str, base_url: Optional[str] = None) -> "APIClient":
        """Exchange credentials for a token. Used by the interactive CLI only."""
        base = (base_url or os.getenv("CMMS_API_URL", "http://localhost:8080")).rstrip("/")
        try:
            resp = requests.post(
                f"{base}/auth/signin",
                json={"email": email, "password": password, "type": "client"},
                timeout=10,
            )
        except requests.ConnectionError:
            raise ConnectionError(f"Cannot reach API at {base}. Is the server running?")

        if resp.status_code != 200:
            raise PermissionError("Login failed. Check the email and password.")

        data = resp.json()
        token = data.get("accessToken") or data.get("token") or data.get("access_token")
        if not token:
            raise PermissionError(
                f"Login succeeded but no token found in response: {list(data.keys())}"
            )
        return cls(token, base)

    def _headers(self):
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
            return {"error": "Your session has expired. Please sign in again."}

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


def set_client(client: APIClient):
    """Bind an API client to the current request context. Returns a reset token."""
    return _current_client.set(client)


def reset_client(token) -> None:
    _current_client.reset(token)


def get_client() -> APIClient:
    """Return the API client bound to the request being served."""
    client = _current_client.get()
    if client is None:
        raise RuntimeError(
            "No authenticated API client bound to this request. "
            "Tools may only be called inside an authenticated agent turn."
        )
    return client
