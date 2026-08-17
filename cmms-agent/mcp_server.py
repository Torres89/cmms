"""
Door 1 — the remote MCP server.

The customer connects the AI they already pay for (Claude, ChatGPT, Copilot,
Cursor…) to this endpoint. They supply the model; we supply the data, the tools
and the retrieval. Zero token cost on our side.

Transport is **remote HTTPS** with the Streamable HTTP binding, because ChatGPT
accepts nothing else. Auth is OAuth 2.1 with PKCE, and the issued access token
*is* an Atlas JWT, so every tool call lands in the API as that user and inherits
org isolation and role checks unchanged.
"""

import base64
import hashlib
import html
import json
import logging
import os
import secrets
import threading
import time
from typing import Any, Optional
from urllib.parse import urlencode

import requests
from fastapi import APIRouter, Header, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse

import db
from api_client import APIClient, reset_client, set_client
from auth import AuthError, Principal, authenticate, resolve_principal
from tool_registry import MUTATING_TOOLS, execute_tool, get_all_tools

log = logging.getLogger("agent.mcp")

router = APIRouter()

CMMS_API_URL = os.getenv("CMMS_API_URL", "http://localhost:8080").rstrip("/")
# Public base URL of this MCP service, e.g. https://mcp.customer.example
MCP_PUBLIC_URL = os.getenv("MCP_PUBLIC_URL", "http://localhost:8001").rstrip("/")
MCP_ENABLED = os.getenv("MCP_ENABLED", "true").lower() not in ("false", "0", "no")

SUPPORTED_PROTOCOL_VERSIONS = ["2026-07-28", "2025-06-18", "2025-03-26"]
DEFAULT_PROTOCOL_VERSION = "2025-06-18"

SERVER_INFO = {"name": "atlas-machine-specialist", "version": "1.0.0"}

AUTH_CODE_TTL = 120  # seconds — codes are single-use and short-lived


# ---------------------------------------------------------------------------
# OAuth 2.1 state
# ---------------------------------------------------------------------------

_codes: dict[str, dict] = {}
_codes_lock = threading.Lock()


def _register_client(metadata: dict) -> dict:
    """Dynamic client registration (RFC 7591). Persisted so restarts don't break clients."""
    client_id = "mcp-" + secrets.token_urlsafe(16)
    record = {
        "client_id": client_id,
        "client_name": metadata.get("client_name") or "MCP client",
        "redirect_uris": metadata.get("redirect_uris") or [],
        "grant_types": metadata.get("grant_types") or ["authorization_code", "refresh_token"],
        "token_endpoint_auth_method": "none",  # public client + PKCE
    }
    pool = db._get_pool()
    if pool is not None:
        with pool.connection() as conn:
            conn.execute(
                """INSERT INTO mcp_oauth_client (client_id, client_name, redirect_uris, metadata)
                   VALUES (%s, %s, %s, %s)""",
                (
                    client_id,
                    record["client_name"],
                    json.dumps(record["redirect_uris"]),
                    json.dumps(metadata),
                ),
            )
    else:
        _memory_clients[client_id] = record
    return record


_memory_clients: dict[str, dict] = {}


def _load_client(client_id: str) -> Optional[dict]:
    pool = db._get_pool()
    if pool is None:
        return _memory_clients.get(client_id)
    with pool.connection() as conn:
        row = conn.execute(
            "SELECT client_id, client_name, redirect_uris FROM mcp_oauth_client WHERE client_id = %s",
            (client_id,),
        ).fetchone()
    if row is None:
        return None
    return {"client_id": row[0], "client_name": row[1], "redirect_uris": json.loads(row[2] or "[]")}


def _verify_pkce(verifier: str, challenge: str, method: str) -> bool:
    if method == "plain":
        return secrets.compare_digest(verifier, challenge)
    digest = hashlib.sha256(verifier.encode("ascii")).digest()
    expected = base64.urlsafe_b64encode(digest).rstrip(b"=").decode("ascii")
    return secrets.compare_digest(expected, challenge)


def _prune_codes(now: float) -> None:
    for code in [c for c, v in _codes.items() if now - v["issued"] > AUTH_CODE_TTL]:
        _codes.pop(code, None)


# ---------------------------------------------------------------------------
# Discovery
# ---------------------------------------------------------------------------

@router.get("/.well-known/oauth-protected-resource")
@router.get("/.well-known/oauth-protected-resource/mcp")
def protected_resource_metadata():
    return {
        "resource": f"{MCP_PUBLIC_URL}/mcp",
        "authorization_servers": [MCP_PUBLIC_URL],
        "scopes_supported": ["atlas:read", "atlas:write"],
        "bearer_methods_supported": ["header"],
    }


@router.get("/.well-known/oauth-authorization-server")
@router.get("/.well-known/oauth-authorization-server/mcp")
def authorization_server_metadata():
    return {
        "issuer": MCP_PUBLIC_URL,
        "authorization_endpoint": f"{MCP_PUBLIC_URL}/oauth/authorize",
        "token_endpoint": f"{MCP_PUBLIC_URL}/oauth/token",
        "registration_endpoint": f"{MCP_PUBLIC_URL}/oauth/register",
        "response_types_supported": ["code"],
        "grant_types_supported": ["authorization_code"],
        "code_challenge_methods_supported": ["S256", "plain"],
        "token_endpoint_auth_methods_supported": ["none"],
        "scopes_supported": ["atlas:read", "atlas:write"],
    }


@router.post("/oauth/register")
async def register(request: Request):
    body = await request.json()
    redirect_uris = body.get("redirect_uris") or []
    if not redirect_uris:
        raise HTTPException(status_code=400, detail="redirect_uris is required")
    record = _register_client(body)
    return JSONResponse(record, status_code=201)


_LOGIN_PAGE = """<!doctype html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Connect to Atlas</title>
<style>
  body {{ font-family: system-ui, -apple-system, Segoe UI, sans-serif; background:#f5f6f8;
         display:flex; min-height:100vh; align-items:center; justify-content:center; margin:0; }}
  form {{ background:#fff; padding:2rem; border-radius:12px; width:min(380px, 90vw);
          box-shadow:0 1px 3px rgba(0,0,0,.1); }}
  h1 {{ font-size:1.15rem; margin:0 0 .35rem; }}
  p  {{ color:#5c6470; font-size:.875rem; margin:0 0 1.25rem; }}
  label {{ display:block; font-size:.8rem; font-weight:600; margin:.85rem 0 .3rem; }}
  input {{ width:100%; padding:.6rem .7rem; border:1px solid #d5d9e0; border-radius:7px;
           font-size:.95rem; box-sizing:border-box; }}
  button {{ width:100%; margin-top:1.4rem; padding:.7rem; border:0; border-radius:7px;
            background:#1c2536; color:#fff; font-size:.95rem; font-weight:600; cursor:pointer; }}
  .err {{ background:#fdecec; color:#a12b2b; padding:.6rem .7rem; border-radius:7px;
          font-size:.85rem; margin-bottom:1rem; }}
</style>
<form method="post" action="/oauth/authorize">
  <h1>Connect {client} to Atlas</h1>
  <p>Sign in with your Atlas account. {client} will act as you, with your permissions.</p>
  {error}
  <input type="hidden" name="client_id" value="{client_id}">
  <input type="hidden" name="redirect_uri" value="{redirect_uri}">
  <input type="hidden" name="state" value="{state}">
  <input type="hidden" name="code_challenge" value="{code_challenge}">
  <input type="hidden" name="code_challenge_method" value="{code_challenge_method}">
  <input type="hidden" name="scope" value="{scope}">
  <label for="email">Email</label>
  <input id="email" name="email" type="email" autocomplete="username" required autofocus>
  <label for="password">Password</label>
  <input id="password" name="password" type="password" autocomplete="current-password" required>
  <button type="submit">Sign in and connect</button>
</form>
"""


def _render_login(params: dict, error: str = "") -> HTMLResponse:
    client = _load_client(params.get("client_id", ""))
    return HTMLResponse(
        _LOGIN_PAGE.format(
            client=html.escape(client["client_name"] if client else "this client"),
            client_id=html.escape(params.get("client_id", "")),
            redirect_uri=html.escape(params.get("redirect_uri", "")),
            state=html.escape(params.get("state", "")),
            code_challenge=html.escape(params.get("code_challenge", "")),
            code_challenge_method=html.escape(params.get("code_challenge_method", "S256")),
            scope=html.escape(params.get("scope", "atlas:read atlas:write")),
            error=f'<div class="err">{html.escape(error)}</div>' if error else "",
        )
    )


@router.get("/oauth/authorize")
def authorize(request: Request):
    params = dict(request.query_params)
    if params.get("response_type") != "code":
        raise HTTPException(status_code=400, detail="Only response_type=code is supported")
    client = _load_client(params.get("client_id", ""))
    if client is None:
        raise HTTPException(status_code=400, detail="Unknown client_id")
    redirect_uri = params.get("redirect_uri", "")
    if client["redirect_uris"] and redirect_uri not in client["redirect_uris"]:
        raise HTTPException(status_code=400, detail="redirect_uri is not registered for this client")
    if not params.get("code_challenge"):
        raise HTTPException(status_code=400, detail="PKCE is required (code_challenge)")
    return _render_login(params)


@router.post("/oauth/authorize")
async def authorize_submit(request: Request):
    form = dict(await request.form())
    email = (form.get("email") or "").strip()
    password = form.get("password") or ""

    try:
        resp = requests.post(
            f"{CMMS_API_URL}/auth/signin",
            json={"email": email, "password": password, "type": "client"},
            timeout=15,
        )
    except requests.RequestException:
        return _render_login(form, "Could not reach Atlas. Try again in a moment.")

    if resp.status_code != 200:
        return _render_login(form, "Wrong email or password.")

    data = resp.json()
    token = data.get("accessToken") or data.get("token") or data.get("access_token")
    if not token:
        return _render_login(form, "Sign-in succeeded but no token was returned.")

    code = secrets.token_urlsafe(32)
    now = time.time()
    with _codes_lock:
        _prune_codes(now)
        _codes[code] = {
            "issued": now,
            "token": token,
            "client_id": form.get("client_id", ""),
            "redirect_uri": form.get("redirect_uri", ""),
            "code_challenge": form.get("code_challenge", ""),
            "code_challenge_method": form.get("code_challenge_method") or "S256",
        }

    query = {"code": code}
    if form.get("state"):
        query["state"] = form["state"]
    return RedirectResponse(f"{form.get('redirect_uri')}?{urlencode(query)}", status_code=303)


@router.post("/oauth/token")
async def token(request: Request):
    form = dict(await request.form())
    if form.get("grant_type") != "authorization_code":
        return JSONResponse({"error": "unsupported_grant_type"}, status_code=400)

    code = form.get("code", "")
    with _codes_lock:
        _prune_codes(time.time())
        entry = _codes.pop(code, None)  # single use

    if entry is None:
        return JSONResponse({"error": "invalid_grant"}, status_code=400)
    if entry["client_id"] and form.get("client_id") and entry["client_id"] != form["client_id"]:
        return JSONResponse({"error": "invalid_grant"}, status_code=400)
    if entry["redirect_uri"] and form.get("redirect_uri") != entry["redirect_uri"]:
        return JSONResponse({"error": "invalid_grant"}, status_code=400)
    if not _verify_pkce(
        form.get("code_verifier", ""), entry["code_challenge"], entry["code_challenge_method"]
    ):
        return JSONResponse({"error": "invalid_grant"}, status_code=400)

    return {
        "access_token": entry["token"],
        "token_type": "Bearer",
        # Matches the API's JWT lifetime (14 days).
        "expires_in": 14 * 24 * 3600,
        "scope": "atlas:read atlas:write",
    }


# ---------------------------------------------------------------------------
# Rate limiting — an external client can loop; the CMMS should not fall over
# ---------------------------------------------------------------------------

_RATE_WINDOW = 60.0
_RATE_LIMIT = int(os.getenv("MCP_RATE_LIMIT_PER_MINUTE", "120"))
_rate: dict[str, list[float]] = {}
_rate_lock = threading.Lock()


def _check_rate(key: str) -> None:
    now = time.time()
    with _rate_lock:
        hits = [t for t in _rate.get(key, []) if now - t < _RATE_WINDOW]
        if len(hits) >= _RATE_LIMIT:
            raise HTTPException(status_code=429, detail="Too many MCP requests; slow down.")
        hits.append(now)
        _rate[key] = hits


# ---------------------------------------------------------------------------
# MCP JSON-RPC
# ---------------------------------------------------------------------------

def _unauthorized() -> JSONResponse:
    """401 carrying the resource-metadata pointer clients use to start OAuth."""
    return JSONResponse(
        {"error": "invalid_token", "error_description": "Authentication required"},
        status_code=401,
        headers={
            "WWW-Authenticate": (
                'Bearer realm="atlas", '
                f'resource_metadata="{MCP_PUBLIC_URL}/.well-known/oauth-protected-resource"'
            )
        },
    )


def _mcp_tools() -> list[dict]:
    """The §5.3 surface as MCP tool descriptors."""
    tools = []
    for tool in get_all_tools():
        fn = tool["function"]
        descriptor = {
            "name": fn["name"],
            "description": fn.get("description", ""),
            "inputSchema": fn.get("parameters", {"type": "object", "properties": {}}),
        }
        if fn["name"] in MUTATING_TOOLS:
            descriptor["annotations"] = {
                "title": fn["name"].replace("_", " ").title(),
                "readOnlyHint": False,
                "destructiveHint": fn["name"].startswith("delete_"),
            }
        else:
            descriptor["annotations"] = {"readOnlyHint": True}
        tools.append(descriptor)
    return tools


def _list_resources(principal: Principal) -> list[dict]:
    """
    Asset dossiers as MCP resources, so a client can browse the machines
    without a tool round-trip.
    """
    client = APIClient(principal.token)
    result = client.post_search(
        "/assets/search",
        {"filterFields": [], "pageSize": 100, "pageNum": 0, "direction": "ASC", "sortField": "name"},
    )
    if not isinstance(result, dict) or "error" in result:
        return []
    resources = []
    for asset in result.get("content", []):
        asset_id = asset.get("id")
        if asset_id is None:
            continue
        label = asset.get("name") or f"Asset {asset_id}"
        model = asset.get("model") or ""
        resources.append({
            "uri": f"atlas://asset/{asset_id}/dossier",
            "name": label,
            "title": f"{label}{f' ({model})' if model else ''}",
            "description": "Current machine dossier: specs, meters, components, PMs due, failures.",
            "mimeType": "text/plain",
        })
    return resources


def _read_resource(uri: str) -> dict:
    if not uri.startswith("atlas://asset/") or not uri.endswith("/dossier"):
        raise ValueError(f"Unknown resource: {uri}")
    asset_id = uri[len("atlas://asset/"):-len("/dossier")]
    from tools.knowledge import get_machine_dossier

    result = get_machine_dossier(int(asset_id), format="text")
    text = result.get("text") if isinstance(result, dict) else str(result)
    if isinstance(result, dict) and "error" in result:
        text = result["error"]
    return {"uri": uri, "mimeType": "text/plain", "text": text or ""}


def _rpc_result(request_id, result) -> dict:
    return {"jsonrpc": "2.0", "id": request_id, "result": result}


def _rpc_error(request_id, code: int, message: str) -> dict:
    return {"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}}


def _handle_rpc(message: dict, principal: Principal) -> Optional[dict]:
    method = message.get("method")
    request_id = message.get("id")
    params = message.get("params") or {}

    # Notifications carry no id and get no response.
    if request_id is None and method and method.startswith("notifications/"):
        return None

    if method == "initialize":
        requested = params.get("protocolVersion")
        version = requested if requested in SUPPORTED_PROTOCOL_VERSIONS else DEFAULT_PROTOCOL_VERSION
        return _rpc_result(request_id, {
            "protocolVersion": version,
            "capabilities": {
                "tools": {"listChanged": False},
                "resources": {"listChanged": False, "subscribe": False},
            },
            "serverInfo": SERVER_INFO,
            "instructions": (
                "This server exposes one company's machines in Atlas CMMS. For any question "
                "about a specific machine, read its dossier resource or call "
                "get_machine_dossier first — it is the ground truth for the machine's current "
                "state. Cite the document title and page for anything taken from "
                "search_machine_docs. Tools that change data must be called with "
                "confirmed=false first; show the preview to the user before confirming."
            ),
        })

    if method == "ping":
        return _rpc_result(request_id, {})

    if method == "tools/list":
        return _rpc_result(request_id, {"tools": _mcp_tools()})

    if method == "tools/call":
        name = params.get("name", "")
        args = params.get("arguments") or {}
        ctx = set_client(APIClient(principal.token))
        try:
            result = execute_tool(name, args, principal, session_id=None, client="mcp")
        finally:
            reset_client(ctx)
        is_error = isinstance(result, dict) and "error" in result
        return _rpc_result(request_id, {
            "content": [{"type": "text", "text": json.dumps(result, default=str, indent=2)}],
            "structuredContent": result if isinstance(result, dict) else {"result": result},
            "isError": is_error,
        })

    if method == "resources/list":
        return _rpc_result(request_id, {"resources": _list_resources(principal)})

    if method == "resources/read":
        uri = params.get("uri", "")
        ctx = set_client(APIClient(principal.token))
        try:
            contents = _read_resource(uri)
        except ValueError as exc:
            return _rpc_error(request_id, -32602, str(exc))
        finally:
            reset_client(ctx)
        return _rpc_result(request_id, {"contents": [contents]})

    if method in ("prompts/list", "resources/templates/list"):
        key = "prompts" if method.startswith("prompts") else "resourceTemplates"
        return _rpc_result(request_id, {key: []})

    return _rpc_error(request_id, -32601, f"Method not found: {method}")


@router.post("/mcp")
async def mcp_endpoint(request: Request, authorization: Optional[str] = Header(default=None)):
    if not MCP_ENABLED:
        raise HTTPException(status_code=404, detail="MCP is not enabled on this deployment")

    try:
        principal = authenticate(authorization)
    except AuthError:
        return _unauthorized()

    _check_rate(f"{principal.company_id}:{principal.user_id}")

    try:
        payload = await request.json()
    except Exception:
        return JSONResponse(_rpc_error(None, -32700, "Parse error"), status_code=400)

    if isinstance(payload, list):  # JSON-RPC batch
        responses = [r for r in (_handle_rpc(m, principal) for m in payload) if r is not None]
        if not responses:
            return JSONResponse(None, status_code=202)
        return JSONResponse(responses)

    response = _handle_rpc(payload, principal)
    if response is None:
        return JSONResponse(None, status_code=202)
    return JSONResponse(response)


@router.get("/mcp")
def mcp_get(authorization: Optional[str] = Header(default=None)):
    """
    Clients open a GET stream for server-initiated messages. This server has
    none, so it declines cleanly rather than holding a connection open.
    """
    try:
        authenticate(authorization)
    except AuthError:
        return _unauthorized()
    raise HTTPException(status_code=405, detail="This server does not send unsolicited messages")
