"""
Per-user authentication for the Atlas CMMS agent.

The agent no longer holds service-account credentials. Every request must carry
the caller's own Atlas JWT; identity is resolved against the API's ``/auth/me``
endpoint so all org isolation and role checks in the API apply unchanged.
"""

import os
import time
import threading
from dataclasses import dataclass
from typing import Optional

import requests

CMMS_API_URL = os.getenv("CMMS_API_URL", "http://localhost:8080").rstrip("/")

# Identity lookups are cached briefly so a multi-tool chat turn doesn't hit
# /auth/me once per tool call.
_CACHE_TTL = 60.0
_cache: dict[str, tuple[float, "Principal"]] = {}
_cache_lock = threading.Lock()


class AuthError(Exception):
    """Raised when a caller cannot be authenticated."""

    def __init__(self, message: str, status_code: int = 401):
        super().__init__(message)
        self.message = message
        self.status_code = status_code


@dataclass(frozen=True)
class Principal:
    """The authenticated caller behind a request."""

    token: str
    user_id: int
    company_id: int
    email: str
    first_name: str = ""
    last_name: str = ""
    role_name: str = ""
    role_type: str = ""

    @property
    def display_name(self) -> str:
        full = f"{self.first_name} {self.last_name}".strip()
        return full or self.email

    @property
    def is_admin(self) -> bool:
        return self.role_type in ("ROLE_ADMIN", "ROLE_SUPER_ADMIN")


def extract_bearer_token(authorization: Optional[str]) -> str:
    """Pull the raw token out of an ``Authorization: Bearer …`` header."""
    if not authorization:
        raise AuthError("Missing Authorization header")
    parts = authorization.split(None, 1)
    if len(parts) != 2 or parts[0].lower() != "bearer" or not parts[1].strip():
        raise AuthError("Authorization header must be 'Bearer <token>'")
    return parts[1].strip()


def resolve_principal(token: str) -> Principal:
    """Validate a token against the CMMS API and return the caller's identity."""
    now = time.time()
    with _cache_lock:
        cached = _cache.get(token)
        if cached and now - cached[0] < _CACHE_TTL:
            return cached[1]

    try:
        resp = requests.get(
            f"{CMMS_API_URL}/auth/me",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10,
        )
    except requests.RequestException as exc:
        raise AuthError(f"Cannot reach the CMMS API to verify the token: {exc}", 503)

    if resp.status_code in (401, 403):
        raise AuthError("Invalid or expired token")
    if resp.status_code >= 400:
        raise AuthError(f"Token verification failed ({resp.status_code})", 502)

    try:
        data = resp.json()
    except ValueError:
        raise AuthError("Unexpected response from the CMMS API", 502)

    company_id = data.get("companyId")
    user_id = data.get("id")
    if company_id is None or user_id is None:
        raise AuthError("Token holder has no company; cannot scope this request", 403)

    role = data.get("role") or {}
    principal = Principal(
        token=token,
        user_id=int(user_id),
        company_id=int(company_id),
        email=data.get("email") or "",
        first_name=data.get("firstName") or "",
        last_name=data.get("lastName") or "",
        role_name=role.get("name") or "",
        role_type=role.get("roleType") or "",
    )

    with _cache_lock:
        _cache[token] = (now, principal)
        if len(_cache) > 500:  # keep the cache from growing without bound
            for stale in [k for k, (ts, _) in _cache.items() if now - ts > _CACHE_TTL]:
                _cache.pop(stale, None)

    return principal


def authenticate(authorization: Optional[str]) -> Principal:
    """Convenience: header string in, :class:`Principal` out."""
    return resolve_principal(extract_bearer_token(authorization))
