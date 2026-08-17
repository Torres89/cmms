"""
Atlas CMMS Agent — FastAPI chat server.

Every request is authenticated as the calling user and every tool call runs
with that user's own token, so the API's org isolation and role checks apply
unchanged. Sessions live in Postgres; tool calls are audited; token usage is
metered per company.

Run: python server.py
"""

import json
import logging
import os
import time
from typing import Optional

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

load_dotenv()

import db
import llm_provider
from api_client import APIClient, reset_client, set_client
from auth import AuthError, Principal, authenticate
from browser import ENTITY_URL_MAP
from tool_registry import execute_tool, get_all_tools

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
log = logging.getLogger("agent.server")

FRONT_URL = os.getenv("CMMS_FRONT_URL", "").rstrip("/")
EXTRA_ORIGINS = [
    o.strip().rstrip("/")
    for o in os.getenv("AGENT_ALLOWED_ORIGINS", "").split(",")
    if o.strip()
]
MAX_TOOL_ROUNDS = int(os.getenv("AGENT_MAX_TOOL_ROUNDS", "12"))
HISTORY_TOKEN_BUDGET = int(os.getenv("AGENT_HISTORY_TOKEN_BUDGET", "12000"))

SYSTEM_PROMPT = """\
You are the maintenance specialist for this company's machines, working inside \
Atlas CMMS.

You have two kinds of tools.

**Knowledge tools** — use these for any question about a specific machine:
- `get_machine_dossier` — always call this first when a machine is in scope. It \
tells you what is true about that machine right now.
- `search_machine_docs`, `lookup_fault_code`, `get_spec_sheet`, `get_bom`, \
`get_part_sourcing`, `get_component_status`, `get_maintenance_history`, \
`diagnose`, `propose_maintenance_plan`.

**CRUD tools** — parts, work orders, assets, locations, people, teams, vendors \
and preventive maintenance.

Rules:
1. Ground every technical claim in a tool result. If a document excerpt supports \
it, cite the document title and page. If nothing supports it, say you don't have \
that documented rather than reasoning from general knowledge about similar machines.
2. Never invent a part number, a supplier, a price, a torque value or an interval. \
If `get_bom` or `get_part_sourcing` comes back empty, say it hasn't been captured yet.
3. When a tool result contains a safety step from the manual, repeat it verbatim \
and do not paraphrase it away.
4. Mutating tools require confirmation. Call them with confirmed=false first, show \
the user the preview you get back, and only call again with confirmed=true once \
they agree.
5. If a command references another entity by name, search for it first to resolve \
the ID, then use the ID.
6. Be concise. Present lists as tables.
7. Valid work order priorities: NONE, LOW, MEDIUM, HIGH. Statuses: OPEN, \
IN_PROGRESS, ON_HOLD, COMPLETE. PM recurrence types: DAILY, WEEKLY, MONTHLY, \
YEARLY; recurrenceBasedOn: TIME, METER.
"""


# ---------------------------------------------------------------------------
# Auth dependency
# ---------------------------------------------------------------------------

def current_principal(authorization: Optional[str] = Header(default=None)) -> Principal:
    try:
        return authenticate(authorization)
    except AuthError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.message)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _build_entity_link(entity_type: str, entity_id) -> Optional[dict]:
    url_template = ENTITY_URL_MAP.get(entity_type)
    if not url_template:
        return None
    return {
        "label": f"View {entity_type.replace('_', ' ').title()} #{entity_id}",
        "url": url_template.format(id=entity_id),
    }


def _approx_tokens(messages: list[dict]) -> int:
    """Rough character-based estimate — good enough to trim history by budget."""
    total = 0
    for m in messages:
        total += len(m.get("content") or "") // 4
        if m.get("tool_calls"):
            total += len(json.dumps(m["tool_calls"], default=str)) // 4
    return total


def _trim_history(messages: list[dict], budget: int) -> list[dict]:
    """
    Drop oldest turns until the history fits the budget, never leaving a tool
    result orphaned from the assistant message that requested it.
    """
    trimmed = list(messages)
    while trimmed and _approx_tokens(trimmed) > budget:
        del trimmed[0]
        while trimmed and trimmed[0].get("role") == "tool":
            del trimmed[0]
    return trimmed


def _dossier_card(asset_id: int) -> Optional[str]:
    """Fetch the machine dossier so the model always sees current state."""
    from tools.knowledge import get_machine_dossier

    try:
        result = get_machine_dossier(asset_id, format="text")
    except Exception as exc:  # pragma: no cover - never break a turn on this
        log.warning("Could not load dossier for asset %s: %s", asset_id, exc)
        return None
    if isinstance(result, dict):
        if "error" in result:
            return None
        return result.get("text") or json.dumps(result, default=str)
    return str(result)


def _system_messages(asset_id: Optional[int]) -> list[dict]:
    messages = [{"role": "system", "content": SYSTEM_PROMPT}]
    if asset_id is not None:
        card = _dossier_card(asset_id)
        if card:
            messages.append({
                "role": "system",
                "content": (
                    f"The user is asking about asset #{asset_id}. This is its current "
                    f"state, generated from the database just now — trust it over "
                    f"anything in the conversation history:\n\n{card}"
                ),
            })
    return messages


def _serialize_assistant(message) -> dict:
    """Convert an SDK assistant message into the plain dict we store and resend."""
    out: dict = {"role": "assistant", "content": message.content}
    if getattr(message, "tool_calls", None):
        out["tool_calls"] = [
            {
                "id": tc.id,
                "type": "function",
                "function": {"name": tc.function.name, "arguments": tc.function.arguments},
            }
            for tc in message.tool_calls
        ]
    return out


# ---------------------------------------------------------------------------
# The agent loop
# ---------------------------------------------------------------------------

class TurnResult:
    def __init__(self):
        self.reply = ""
        self.links: list[dict] = []
        self.notices: list[str] = []
        self.new_messages: list[dict] = []


def _run_turn(
    principal: Principal,
    session_id: str,
    user_message: str,
    asset_id: Optional[int],
    client_label: str,
    on_event=None,
) -> TurnResult:
    """
    One user message through to a final assistant reply.

    ``on_event`` (optional) is called with (event_name, payload) so the SSE
    endpoint can stream progress without duplicating this logic.
    """
    result = TurnResult()

    def emit(event, payload):
        if on_event:
            on_event(event, payload)

    config = llm_provider.resolve_config(principal.company_id)
    warning = llm_provider.check_fair_use(principal.company_id, config)
    if warning:
        result.notices.append(warning)
        emit("notice", {"message": warning})

    llm = llm_provider.build_client(config)

    stored = db.load_messages(session_id, principal.user_id)
    history = _trim_history(stored, HISTORY_TOKEN_BUDGET)

    user_msg = {"role": "user", "content": user_message}
    result.new_messages.append(user_msg)

    # A fresh dossier card every turn — machine state changes between turns.
    messages = _system_messages(asset_id) + history + [user_msg]

    tools = get_all_tools()
    rounds = 0

    while True:
        rounds += 1
        if rounds > MAX_TOOL_ROUNDS:
            result.reply = (
                "I stopped after too many tool calls without reaching an answer. "
                "Could you narrow the question?"
            )
            result.new_messages.append({"role": "assistant", "content": result.reply})
            return result

        started = time.perf_counter()
        try:
            response = llm.chat.completions.create(
                model=config.model,
                messages=messages,
                tools=tools,
                tool_choice="auto",
            )
        except Exception as exc:
            latency = int((time.perf_counter() - started) * 1000)
            db.record_usage(
                principal.company_id, principal.user_id, session_id, config.door,
                config.provider, config.model, 0, 0, 0, latency, False,
            )
            raise LlmCallFailed(str(exc))

        latency = int((time.perf_counter() - started) * 1000)
        usage = getattr(response, "usage", None)
        choice = response.choices[0]
        tool_calls = getattr(choice.message, "tool_calls", None) or []
        db.record_usage(
            principal.company_id, principal.user_id, session_id, config.door,
            config.provider, config.model,
            getattr(usage, "prompt_tokens", 0) or 0,
            getattr(usage, "completion_tokens", 0) or 0,
            len(tool_calls), latency, True,
        )

        if not tool_calls:
            reply = choice.message.content or ""
            result.reply = reply
            result.new_messages.append({"role": "assistant", "content": reply})
            return result

        assistant_msg = _serialize_assistant(choice.message)
        messages.append(assistant_msg)
        result.new_messages.append(assistant_msg)

        for tool_call in tool_calls:
            fn_name = tool_call.function.name
            try:
                fn_args = json.loads(tool_call.function.arguments or "{}")
            except json.JSONDecodeError:
                fn_args = {}

            emit("tool", {"name": fn_name, "arguments": fn_args})

            tool_result = execute_tool(
                fn_name, fn_args, principal, session_id=session_id, client=client_label
            )

            if isinstance(tool_result, dict):
                entity_type = tool_result.pop("_entity", None)
                entity_id = tool_result.pop("_id", None)
                if entity_type and entity_id and tool_result.get("success"):
                    link = _build_entity_link(entity_type, entity_id)
                    if link:
                        result.links.append(link)
                        emit("link", link)

            tool_msg = {
                "role": "tool",
                "tool_call_id": tool_call.id,
                "name": fn_name,
                "content": json.dumps(tool_result, default=str),
            }
            messages.append(tool_msg)
            result.new_messages.append(tool_msg)


class LlmCallFailed(Exception):
    pass


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(title="Atlas CMMS Agent")

_origins = [o for o in ([FRONT_URL] + EXTRA_ORIGINS) if o]
if not _origins:
    # Development default only. In production CMMS_FRONT_URL is always set and
    # the agent sits behind Caddy on the same origin.
    _origins = ["http://localhost:3000"]
    log.warning("CMMS_FRONT_URL is not set; allowing %s only", _origins)

app.add_middleware(
    CORSMiddleware,
    allow_origins=_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "Accept", "Mcp-Session-Id"],
)


class ChatRequest(BaseModel):
    message: str
    session_id: str = ""
    asset_id: Optional[int] = None


class ChatLink(BaseModel):
    label: str
    url: str


class ChatResponse(BaseModel):
    reply: str
    session_id: str
    links: list[ChatLink] = []
    notices: list[str] = []


def _bind_client(principal: Principal):
    return set_client(APIClient(principal.token))


@app.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest, principal: Principal = Depends(current_principal)):
    session_id = db.ensure_session(
        req.session_id or None, principal.company_id, principal.user_id,
        asset_id=req.asset_id, channel="WEB",
    )
    ctx = _bind_client(principal)
    try:
        result = _run_turn(
            principal, session_id, req.message, req.asset_id, client_label="web"
        )
    except llm_provider.LlmUnavailable as exc:
        raise HTTPException(status_code=402, detail=str(exc))
    except LlmCallFailed as exc:
        raise HTTPException(status_code=502, detail=f"Error reaching the AI provider: {exc}")
    finally:
        reset_client(ctx)

    db.append_messages(session_id, result.new_messages)
    return ChatResponse(
        reply=result.reply,
        session_id=session_id,
        links=[ChatLink(**l) for l in result.links],
        notices=result.notices,
    )


@app.post("/chat/stream")
def chat_stream(req: ChatRequest, principal: Principal = Depends(current_principal)):
    """Server-sent events: progress while tools run, then the final reply."""
    session_id = db.ensure_session(
        req.session_id or None, principal.company_id, principal.user_id,
        asset_id=req.asset_id, channel="WEB",
    )

    def generate():
        events: list[tuple[str, dict]] = []
        ctx = _bind_client(principal)
        yield _sse("session", {"session_id": session_id})
        try:
            result = _run_turn(
                principal, session_id, req.message, req.asset_id,
                client_label="web",
                on_event=lambda name, payload: events.append((name, payload)),
            )
        except llm_provider.LlmUnavailable as exc:
            yield _sse("error", {"message": str(exc), "code": "AI_UNAVAILABLE"})
            return
        except LlmCallFailed as exc:
            yield _sse("error", {"message": f"Error reaching the AI provider: {exc}"})
            return
        finally:
            reset_client(ctx)

        for name, payload in events:
            yield _sse(name, payload)
        db.append_messages(session_id, result.new_messages)
        yield _sse(
            "reply",
            {"reply": result.reply, "links": result.links, "notices": result.notices},
        )
        yield _sse("done", {})

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, default=str)}\n\n"


@app.get("/chat/sessions")
def list_sessions(principal: Principal = Depends(current_principal)):
    return {"sessions": db.list_sessions(principal.company_id, principal.user_id)}


@app.get("/chat/sessions/{session_id}")
def get_session(session_id: str, principal: Principal = Depends(current_principal)):
    messages = db.load_messages(session_id, principal.user_id, limit=200)
    visible = [
        {"role": m["role"], "content": m.get("content")}
        for m in messages
        if m["role"] in ("user", "assistant") and m.get("content")
    ]
    return {"session_id": session_id, "messages": visible}


@app.delete("/chat/sessions/{session_id}")
def remove_session(session_id: str, principal: Principal = Depends(current_principal)):
    return {"deleted": db.delete_session(session_id, principal.user_id)}


@app.get("/usage")
def usage(days: int = 30, principal: Principal = Depends(current_principal)):
    return db.usage_summary(principal.company_id, days=days)


@app.get("/health")
def health():
    return {"status": "ok", "database": db.available()}


# The remote MCP server (Door 1) lives on the same process behind Caddy.
try:
    from mcp_server import router as mcp_router

    app.include_router(mcp_router)
except Exception as exc:  # pragma: no cover
    log.warning("MCP server not mounted: %s", exc)


if __name__ == "__main__":
    import uvicorn

    port = int(os.getenv("AGENT_PORT", "8001"))
    print(f"\n  Atlas CMMS Agent Server starting on http://localhost:{port}\n")
    uvicorn.run(app, host="0.0.0.0", port=port)
