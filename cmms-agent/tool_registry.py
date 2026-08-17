"""
The agent's tool surface.

One surface, exposed twice: as OpenAI-style function tools for the in-app chat
(Doors 2/3) and as MCP tools for external clients (Door 1). Everything goes
through :func:`execute_tool`, which is where write-safety and auditing live —
structurally, not as a prompt rule an external model could talk its way around.
"""

import json
import logging
import time
from typing import Any, Optional

from tools import (
    assets,
    knowledge,
    locations,
    parts,
    people,
    preventive_maintenance,
    teams,
    vendors,
    work_orders,
)

log = logging.getLogger("agent.tools")

_MODULES = [
    parts,
    work_orders,
    assets,
    locations,
    people,
    teams,
    vendors,
    preventive_maintenance,
    knowledge,
]

# Tools that change state. Every one of these is gated on an explicit
# `confirmed` argument; with confirmed=false the caller gets a preview instead
# of an effect. Read tools are open.
MUTATING_TOOLS: dict[str, str] = {
    "add_part": "create a part",
    "delete_part": "delete a part",
    "create_work_order": "create a work order",
    "update_work_order_status": "change a work order's status",
    "add_part_to_work_order": "add parts to a work order",
    "add_asset": "register an asset",
    "add_location": "create a location",
    "delete_location": "delete a location",
    "invite_person": "invite a user",
    "add_team": "create a team",
    "delete_team": "delete a team",
    "add_vendor": "create a vendor",
    "delete_vendor": "delete a vendor",
    "add_preventive_maintenance": "create a preventive maintenance schedule",
    "delete_preventive_maintenance": "delete a preventive maintenance schedule",
    "apply_extracted_specs": "write values read off a nameplate onto a machine",
}


def get_all_tools() -> list[dict]:
    """OpenAI-style tool schemas, with the confirmation contract applied."""
    tools = []
    for mod in _MODULES:
        for tool in mod.TOOLS:
            tools.append(_with_confirmation(tool))
    return tools


def _with_confirmation(tool: dict) -> dict:
    """Ensure every mutating tool advertises `confirmed` in its schema."""
    fn = tool.get("function", {})
    name = fn.get("name")
    if name not in MUTATING_TOOLS:
        return tool
    params = fn.get("parameters", {})
    props = params.get("properties", {})
    if "confirmed" in props:
        return tool
    # Copy so the module-level definition stays untouched.
    tool = json.loads(json.dumps(tool))
    props = tool["function"].setdefault("parameters", {}).setdefault("properties", {})
    props["confirmed"] = {
        "type": "boolean",
        "default": False,
        "description": (
            "Must be true to actually perform this action. Call with false first "
            "to get a preview of what would change, show it to the user, and only "
            "then call again with true."
        ),
    }
    return tool


def get_all_functions() -> dict[str, Any]:
    funcs = {}
    for mod in _MODULES:
        funcs.update(mod.TOOL_FUNCTIONS)
    return funcs


def _preview(tool_name: str, args: dict) -> dict:
    """Human-readable description of what a mutating call would do."""
    action = MUTATING_TOOLS.get(tool_name, "perform this action")
    details = ", ".join(
        f"{k}={v}" for k, v in sorted(args.items()) if k != "confirmed" and v not in (None, "", [])
    )
    return {
        "confirmation_required": True,
        "action": action,
        "message": (
            f"This will {action}" + (f" — {details}" if details else "") +
            f". Confirm to proceed, then call {tool_name} again with confirmed=true."
        ),
        "arguments": {k: v for k, v in args.items() if k != "confirmed"},
    }


def execute_tool(
    name: str,
    args: dict,
    principal,
    session_id: Optional[str] = None,
    client: str = "web",
    auto_confirm: bool = False,
) -> dict:
    """
    Run one tool call.

    Applies the write-safety contract, records the call in ``chat_action_log``
    and never lets an audit failure break the turn.
    """
    import db

    functions = get_all_functions()
    fn = functions.get(name)
    mutating = name in MUTATING_TOOLS
    started = time.perf_counter()

    if fn is None:
        result = {"error": f"Unknown tool: {name}"}
        db.log_action(
            principal.company_id, principal.user_id, session_id, name, args, result,
            succeeded=False, mutating=mutating, client=client,
        )
        return result

    if mutating and not auto_confirm and not bool(args.get("confirmed")):
        result = _preview(name, args)
        db.log_action(
            principal.company_id, principal.user_id, session_id, name, args, result,
            succeeded=True, mutating=False, client=client,
            latency_ms=int((time.perf_counter() - started) * 1000),
        )
        return result

    call_args = {k: v for k, v in args.items() if k != "confirmed"}
    # Tools that carry their own `confirmed` parameter still expect it.
    if mutating and "confirmed" in getattr(fn, "__code__", type("x", (), {"co_varnames": ()})).co_varnames:
        call_args["confirmed"] = True

    try:
        result = fn(**call_args)
        succeeded = not (isinstance(result, dict) and "error" in result)
    except TypeError as exc:
        result = {"error": f"Invalid arguments for {name}: {exc}"}
        succeeded = False
    except Exception as exc:  # pragma: no cover - defensive
        log.exception("Tool %s failed", name)
        result = {"error": str(exc)}
        succeeded = False

    db.log_action(
        principal.company_id, principal.user_id, session_id, name, args, result,
        succeeded=succeeded, mutating=mutating, client=client,
        latency_ms=int((time.perf_counter() - started) * 1000),
    )
    return result
