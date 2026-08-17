"""
Three doors to a model, none of them ours.

* **Door 1** — the customer's own MCP client (Claude, ChatGPT, Copilot…). It
  never reaches this module: the client runs the model and only calls our tools.
* **Door 2** — the customer's own API key, stored per company. Zero token cost
  to us.
* **Door 3** — the managed add-on: our key, metered per company, fair-use capped.

Which door a company is on comes from its ``CompanySettings``; this module
resolves it per request and hands back a ready OpenAI-compatible client.
"""

import logging
import os
import threading
import time
from dataclasses import dataclass
from typing import Optional

import requests
from openai import OpenAI

log = logging.getLogger("agent.llm")

CMMS_API_URL = os.getenv("CMMS_API_URL", "http://localhost:8080").rstrip("/")
INTERNAL_SERVICE_TOKEN = os.getenv("INTERNAL_SERVICE_TOKEN", "")

# Door 3 / fallback: our own credentials.
MANAGED_API_KEY = os.getenv("OPENAI_API_KEY", "")
MANAGED_BASE_URL = os.getenv("OPENAI_BASE_URL") or None
MANAGED_MODEL = os.getenv("MODEL", "minimax.minimax-m2")
# Soft warning at 80 %, hard stop at 100 %. Never silently degrade.
MANAGED_MONTHLY_TOKEN_CAP = int(os.getenv("MANAGED_MONTHLY_TOKEN_CAP", "3000000"))

_ANTHROPIC_BASE = "https://api.anthropic.com/v1/"
_OPENAI_BASE = None  # the SDK default

_config_cache: dict[int, tuple[float, "AiConfig"]] = {}
_config_lock = threading.Lock()
_CONFIG_TTL = 120.0


class LlmUnavailable(Exception):
    """No usable model for this company — always says why."""


@dataclass(frozen=True)
class AiConfig:
    door: str            # BYOK | MANAGED | NONE
    provider: str        # ANTHROPIC | OPENAI | CUSTOM | NONE
    model: str
    api_key: str
    base_url: Optional[str]
    monthly_token_cap: Optional[int]


def _default_model(provider: str) -> str:
    if provider == "ANTHROPIC":
        return "claude-sonnet-5"
    if provider == "OPENAI":
        return "gpt-4.1"
    return MANAGED_MODEL


def _base_url(provider: str) -> Optional[str]:
    if provider == "ANTHROPIC":
        return _ANTHROPIC_BASE
    if provider == "OPENAI":
        return _OPENAI_BASE
    return MANAGED_BASE_URL


def _managed_config() -> AiConfig:
    if not MANAGED_API_KEY:
        raise LlmUnavailable(
            "No AI provider is configured for your company. Add your own API key in "
            "Settings → AI, or ask your administrator to enable the managed AI add-on."
        )
    return AiConfig(
        door="MANAGED",
        provider="CUSTOM",
        model=MANAGED_MODEL,
        api_key=MANAGED_API_KEY,
        base_url=MANAGED_BASE_URL,
        monthly_token_cap=MANAGED_MONTHLY_TOKEN_CAP,
    )


def _fetch_company_config(company_id: int) -> Optional[AiConfig]:
    """
    Ask the API for this company's AI settings.

    The decrypted key is only ever served on the internal endpoint, which
    requires the shared service token — it is never returned by a user-facing
    API and never logged here.
    """
    if not INTERNAL_SERVICE_TOKEN:
        return None
    try:
        resp = requests.get(
            f"{CMMS_API_URL}/internal/ai-config/{company_id}",
            headers={"X-Internal-Token": INTERNAL_SERVICE_TOKEN},
            timeout=10,
        )
    except requests.RequestException as exc:
        log.warning("Could not read AI config for company %s: %s", company_id, exc)
        return None
    if resp.status_code == 404:
        return None
    if resp.status_code >= 400:
        log.warning("AI config lookup failed (%s) for company %s", resp.status_code, company_id)
        return None

    data = resp.json()
    provider = (data.get("provider") or "NONE").upper()
    if provider in ("NONE", ""):
        return AiConfig("NONE", "NONE", "", "", None, None)
    if provider == "MANAGED":
        managed = _managed_config()
        cap = data.get("monthlyTokenCap") or managed.monthly_token_cap
        return AiConfig(
            "MANAGED", managed.provider, data.get("model") or managed.model,
            managed.api_key, managed.base_url, cap,
        )

    key = data.get("apiKey") or ""
    if not key:
        return AiConfig("NONE", provider, "", "", None, None)
    return AiConfig(
        door="BYOK",
        provider=provider,
        model=data.get("model") or _default_model(provider),
        api_key=key,
        base_url=data.get("baseUrl") or _base_url(provider),
        monthly_token_cap=data.get("monthlyTokenCap"),
    )


def resolve_config(company_id: int) -> AiConfig:
    now = time.time()
    with _config_lock:
        cached = _config_cache.get(company_id)
        if cached and now - cached[0] < _CONFIG_TTL:
            return cached[1]

    config = _fetch_company_config(company_id)
    if config is None:
        # No per-company settings yet (or no internal token) — fall back to the
        # deployment-wide credentials.
        config = _managed_config()
    if config.door == "NONE":
        raise LlmUnavailable(
            "AI is not enabled for your company. Connect your own API key in "
            "Settings → AI, connect an MCP client, or enable the managed add-on."
        )

    with _config_lock:
        _config_cache[company_id] = (now, config)
    return config


def invalidate(company_id: int) -> None:
    with _config_lock:
        _config_cache.pop(company_id, None)


def check_fair_use(company_id: int, config: AiConfig) -> Optional[str]:
    """
    Enforce the cap before the call, not after.

    Returns a warning string at 80 %, raises :class:`LlmUnavailable` at 100 %.
    """
    if not config.monthly_token_cap:
        return None
    import db

    used = db.month_to_date_tokens(company_id)
    if used >= config.monthly_token_cap:
        raise LlmUnavailable(
            f"Your company has used its monthly AI allowance "
            f"({used:,} of {config.monthly_token_cap:,} tokens). "
            "Chat will resume next month, or you can switch to your own API key "
            "in Settings → AI for unmetered use."
        )
    if used >= config.monthly_token_cap * 0.8:
        pct = int(100 * used / config.monthly_token_cap)
        return (
            f"Heads up: your company has used {pct}% of this month's AI allowance."
        )
    return None


def build_client(config: AiConfig) -> OpenAI:
    return OpenAI(api_key=config.api_key, base_url=config.base_url)
