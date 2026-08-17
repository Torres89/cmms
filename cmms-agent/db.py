"""
Postgres-backed state for the agent: chat sessions, the tool audit trail and
LLM usage metering.

Sessions used to live in an in-process dict, which meant they died on restart
and could not be shared across replicas. The tables here are created by the
API's Liquibase changelog (2026_08_15_1786000001_agent_sessions.xml); this
module only reads and writes them.

If no database is configured the module degrades to an in-memory store so the
agent still runs in a bare development checkout.
"""

import json
import logging
import os
import threading
import time
import uuid
from typing import Any, Optional

log = logging.getLogger("agent.db")

SESSION_TIMEOUT = int(os.getenv("AGENT_SESSION_TIMEOUT", str(30 * 60)))

_pool = None
_pool_lock = threading.Lock()
_unavailable = False


def _dsn() -> Optional[str]:
    """Build a libpq DSN from the same env vars the API uses."""
    explicit = os.getenv("AGENT_DB_DSN")
    if explicit:
        return explicit
    db_url = os.getenv("DB_URL")  # e.g. "postgres/atlas" — host/database
    user = os.getenv("DB_USER")
    password = os.getenv("DB_PWD")
    if not db_url or not user:
        return None
    host, _, database = db_url.partition("/")
    if ":" in host:
        host, _, port = host.partition(":")
    else:
        port = "5432"
    return (
        f"host={host} port={port} dbname={database or 'atlas'} "
        f"user={user} password={password or ''}"
    )


def _get_pool():
    global _pool, _unavailable
    if _unavailable:
        return None
    if _pool is not None:
        return _pool
    with _pool_lock:
        if _pool is not None:
            return _pool
        dsn = _dsn()
        if not dsn:
            log.warning("No database configured for the agent; using in-memory sessions")
            _unavailable = True
            return None
        try:
            from psycopg_pool import ConnectionPool

            _pool = ConnectionPool(dsn, min_size=1, max_size=4, open=True, timeout=10)
            _pool.wait(timeout=10)
        except Exception as exc:  # pragma: no cover - depends on deployment
            log.warning("Agent database unavailable (%s); using in-memory sessions", exc)
            _unavailable = True
            return None
    return _pool


def available() -> bool:
    return _get_pool() is not None


# ---------------------------------------------------------------------------
# In-memory fallback
# ---------------------------------------------------------------------------

_memory: dict[str, dict] = {}
_memory_lock = threading.Lock()


# ---------------------------------------------------------------------------
# Sessions
# ---------------------------------------------------------------------------

def ensure_session(
    session_id: Optional[str],
    company_id: int,
    user_id: int,
    asset_id: Optional[int] = None,
    channel: str = "WEB",
    title: Optional[str] = None,
) -> str:
    """Return an existing session id owned by this user, or create a new one."""
    session_id = session_id or str(uuid.uuid4())
    pool = _get_pool()
    if pool is None:
        with _memory_lock:
            entry = _memory.get(session_id)
            if entry is None or entry["user_id"] != user_id:
                _memory[session_id] = {
                    "company_id": company_id,
                    "user_id": user_id,
                    "asset_id": asset_id,
                    "messages": [],
                    "ts": time.time(),
                }
            else:
                entry["ts"] = time.time()
                if asset_id is not None:
                    entry["asset_id"] = asset_id
        return session_id

    with pool.connection() as conn:
        row = conn.execute(
            "SELECT user_id, company_id FROM chat_session WHERE id = %s", (session_id,)
        ).fetchone()
        if row is None:
            conn.execute(
                """INSERT INTO chat_session (id, company_id, user_id, asset_id, title, channel)
                   VALUES (%s, %s, %s, %s, %s, %s)""",
                (session_id, company_id, user_id, asset_id, title, channel),
            )
        elif row[0] != user_id or row[1] != company_id:
            # Never let one user resume another user's conversation.
            session_id = str(uuid.uuid4())
            conn.execute(
                """INSERT INTO chat_session (id, company_id, user_id, asset_id, title, channel)
                   VALUES (%s, %s, %s, %s, %s, %s)""",
                (session_id, company_id, user_id, asset_id, title, channel),
            )
        else:
            conn.execute(
                """UPDATE chat_session
                      SET updated_at = now(),
                          asset_id = COALESCE(%s, asset_id)
                    WHERE id = %s""",
                (asset_id, session_id),
            )
    return session_id


def load_messages(session_id: str, user_id: int, limit: int = 60) -> list[dict]:
    """Load the stored conversation for a session, oldest first."""
    pool = _get_pool()
    if pool is None:
        with _memory_lock:
            entry = _memory.get(session_id)
            if not entry or entry["user_id"] != user_id:
                return []
            if time.time() - entry["ts"] > SESSION_TIMEOUT:
                entry["messages"] = []
            return list(entry["messages"])

    with pool.connection() as conn:
        rows = conn.execute(
            """SELECT role, content, tool_calls, tool_call_id, name
                 FROM chat_message m
                 JOIN chat_session s ON s.id = m.session_id
                WHERE m.session_id = %s AND s.user_id = %s
                ORDER BY m.seq DESC
                LIMIT %s""",
            (session_id, user_id, limit),
        ).fetchall()

    messages = []
    for role, content, tool_calls, tool_call_id, name in reversed(rows):
        msg: dict[str, Any] = {"role": role}
        if content is not None:
            msg["content"] = content
        if tool_calls:
            msg["tool_calls"] = json.loads(tool_calls)
            msg.setdefault("content", None)
        if tool_call_id:
            msg["tool_call_id"] = tool_call_id
        if name:
            msg["name"] = name
        messages.append(msg)
    return messages


def append_messages(session_id: str, messages: list[dict]) -> None:
    """Persist new messages at the end of a session."""
    if not messages:
        return
    pool = _get_pool()
    if pool is None:
        with _memory_lock:
            entry = _memory.setdefault(
                session_id, {"company_id": None, "user_id": None, "messages": [], "ts": time.time()}
            )
            entry["messages"].extend(messages)
            entry["ts"] = time.time()
        return

    with pool.connection() as conn:
        next_seq = conn.execute(
            "SELECT COALESCE(MAX(seq), 0) + 1 FROM chat_message WHERE session_id = %s",
            (session_id,),
        ).fetchone()[0]
        for offset, msg in enumerate(messages):
            tool_calls = msg.get("tool_calls")
            conn.execute(
                """INSERT INTO chat_message
                       (session_id, seq, role, content, tool_calls, tool_call_id, name)
                   VALUES (%s, %s, %s, %s, %s, %s, %s)""",
                (
                    session_id,
                    next_seq + offset,
                    msg.get("role", "assistant"),
                    msg.get("content"),
                    json.dumps(tool_calls) if tool_calls else None,
                    msg.get("tool_call_id"),
                    msg.get("name"),
                ),
            )
        conn.execute(
            "UPDATE chat_session SET updated_at = now() WHERE id = %s", (session_id,)
        )


def list_sessions(company_id: int, user_id: int, limit: int = 30) -> list[dict]:
    pool = _get_pool()
    if pool is None:
        return []
    with pool.connection() as conn:
        rows = conn.execute(
            """SELECT s.id, s.title, s.asset_id, s.updated_at,
                      (SELECT content FROM chat_message
                        WHERE session_id = s.id AND role = 'user'
                        ORDER BY seq LIMIT 1)
                 FROM chat_session s
                WHERE s.company_id = %s AND s.user_id = %s
                ORDER BY s.updated_at DESC
                LIMIT %s""",
            (company_id, user_id, limit),
        ).fetchall()
    return [
        {
            "id": r[0],
            "title": r[1] or (r[4][:80] if r[4] else "Conversation"),
            "assetId": r[2],
            "updatedAt": r[3].isoformat() if r[3] else None,
        }
        for r in rows
    ]


def delete_session(session_id: str, user_id: int) -> bool:
    pool = _get_pool()
    if pool is None:
        with _memory_lock:
            return _memory.pop(session_id, None) is not None
    with pool.connection() as conn:
        cur = conn.execute(
            "DELETE FROM chat_session WHERE id = %s AND user_id = %s", (session_id, user_id)
        )
        return cur.rowcount > 0


# ---------------------------------------------------------------------------
# Audit trail
# ---------------------------------------------------------------------------

_REDACT_KEYS = {"password", "apikey", "api_key", "token", "secret"}


def _sanitize(value: Any, max_len: int = 4000) -> Optional[str]:
    if value is None:
        return None
    try:
        if isinstance(value, dict):
            value = {
                k: ("***" if k.lower().replace("-", "_") in _REDACT_KEYS else v)
                for k, v in value.items()
            }
        text = json.dumps(value, default=str)
    except (TypeError, ValueError):
        text = str(value)
    return text[:max_len]


def log_action(
    company_id: int,
    user_id: Optional[int],
    session_id: Optional[str],
    tool_name: str,
    arguments: Any,
    result: Any,
    succeeded: bool,
    mutating: bool = False,
    client: str = "web",
    latency_ms: Optional[int] = None,
) -> None:
    """Record one tool invocation. Never raises — auditing must not break a turn."""
    pool = _get_pool()
    if pool is None:
        return
    try:
        with pool.connection() as conn:
            conn.execute(
                """INSERT INTO chat_action_log
                       (company_id, user_id, session_id, client, tool_name, mutating,
                        arguments, result, succeeded, latency_ms)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
                (
                    company_id,
                    user_id,
                    session_id,
                    client,
                    tool_name,
                    mutating,
                    _sanitize(arguments),
                    _sanitize(result),
                    succeeded,
                    latency_ms,
                ),
            )
    except Exception as exc:  # pragma: no cover
        log.warning("Could not write chat_action_log: %s", exc)


def record_usage(
    company_id: int,
    user_id: Optional[int],
    session_id: Optional[str],
    door: str,
    provider: Optional[str],
    model: Optional[str],
    prompt_tokens: int,
    completion_tokens: int,
    tool_calls: int,
    latency_ms: Optional[int],
    succeeded: bool,
) -> None:
    """Record one LLM call. Written for every door so usage is always answerable."""
    pool = _get_pool()
    if pool is None:
        return
    try:
        with pool.connection() as conn:
            conn.execute(
                """INSERT INTO llm_usage
                       (company_id, user_id, session_id, door, provider, model,
                        prompt_tokens, completion_tokens, tool_calls, latency_ms, succeeded)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
                (
                    company_id,
                    user_id,
                    session_id,
                    door,
                    provider,
                    model,
                    prompt_tokens,
                    completion_tokens,
                    tool_calls,
                    latency_ms,
                    succeeded,
                ),
            )
    except Exception as exc:  # pragma: no cover
        log.warning("Could not write llm_usage: %s", exc)


def month_to_date_tokens(company_id: int) -> int:
    """Total tokens a company has burned this calendar month (for fair-use caps)."""
    pool = _get_pool()
    if pool is None:
        return 0
    try:
        with pool.connection() as conn:
            row = conn.execute(
                """SELECT COALESCE(SUM(prompt_tokens + completion_tokens), 0)
                     FROM llm_usage
                    WHERE company_id = %s
                      AND created_at >= date_trunc('month', now())""",
                (company_id,),
            ).fetchone()
        return int(row[0] or 0)
    except Exception:  # pragma: no cover
        return 0


def usage_summary(company_id: int, days: int = 30) -> dict:
    pool = _get_pool()
    if pool is None:
        return {"available": False}
    with pool.connection() as conn:
        row = conn.execute(
            """SELECT COUNT(*), COALESCE(SUM(prompt_tokens), 0), COALESCE(SUM(completion_tokens), 0),
                      COALESCE(SUM(tool_calls), 0)
                 FROM llm_usage
                WHERE company_id = %s AND created_at >= now() - make_interval(days => %s)""",
            (company_id, days),
        ).fetchone()
        by_model = conn.execute(
            """SELECT model, COUNT(*), COALESCE(SUM(prompt_tokens + completion_tokens), 0)
                 FROM llm_usage
                WHERE company_id = %s AND created_at >= now() - make_interval(days => %s)
                GROUP BY model ORDER BY 3 DESC""",
            (company_id, days),
        ).fetchall()
    return {
        "available": True,
        "days": days,
        "calls": row[0],
        "promptTokens": row[1],
        "completionTokens": row[2],
        "toolCalls": row[3],
        "monthToDateTokens": month_to_date_tokens(company_id),
        "byModel": [{"model": m, "calls": c, "tokens": t} for m, c, t in by_model],
    }
