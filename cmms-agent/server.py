"""
Atlas CMMS Agent — FastAPI Chat Server
Run: python server.py
"""

import os
import json
import time
import uuid

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

load_dotenv()

from openai import OpenAI
from tool_registry import get_all_tools, get_all_functions
from browser import ENTITY_URL_MAP

MODEL = os.getenv("MODEL", "minimax.minimax-m2")
FRONT_URL = os.getenv("CMMS_FRONT_URL", "http://localhost:3000")

client = OpenAI(
    api_key=os.getenv("OPENAI_API_KEY"),
    base_url=os.getenv("OPENAI_BASE_URL"),
)

SYSTEM_PROMPT = """\
You are an Atlas CMMS assistant. You help maintenance teams manage their \
computerized maintenance management system via natural language commands.

You have tools to manage:
- **Parts** (spare parts inventory): add, list, search, delete
- **Work Orders** (maintenance tasks): create, list, update status, add parts
- **Assets** (equipment): add, list, search
- **Locations** (facility locations): add, list, search, delete
- **People** (users): list, search, invite
- **Teams** (maintenance teams): add, list, search, delete
- **Vendors** (external vendors): add, list, search, delete
- **Preventive Maintenance** (scheduled PMs): add, list, search, delete

Rules:
1. When the user asks to create or add something, call the appropriate tool.
2. If a required field is missing (e.g. name for a part, title for a work order), \
ask the user for it before calling the tool.
3. For destructive actions (delete), always confirm with the user first by calling \
the tool with confirmed=false, then relay the confirmation prompt.
4. When listing or searching, present results in a clean, readable table format.
5. If a command references another entity by name (e.g. an asset name when creating \
a work order), search for it first to resolve the ID, then use the ID.
6. Be concise. Don't over-explain.
7. For work order priorities, valid values are: NONE, LOW, MEDIUM, HIGH.
8. For work order statuses, valid values are: OPEN, IN_PROGRESS, ON_HOLD, COMPLETE.
9. For preventive maintenance, recurrenceType values are: DAILY, WEEKLY, MONTHLY, YEARLY.
10. For preventive maintenance, recurrenceBasedOn values are: TIME, METER.
11. When creating a PM that references an asset, location, or team by name, \
search for it first to resolve the ID.
"""

# ---------------------------------------------------------------------------
# Session store
# ---------------------------------------------------------------------------

SESSION_TIMEOUT = 30 * 60  # 30 minutes

_sessions: dict[str, dict] = {}


def _get_session(session_id: str) -> list:
    now = time.time()
    if session_id not in _sessions or now - _sessions[session_id]["ts"] > SESSION_TIMEOUT:
        _sessions[session_id] = {
            "messages": [{"role": "system", "content": SYSTEM_PROMPT}],
            "ts": now,
        }
    _sessions[session_id]["ts"] = now
    return _sessions[session_id]["messages"]


def _cleanup_sessions():
    now = time.time()
    expired = [sid for sid, s in _sessions.items() if now - s["ts"] > SESSION_TIMEOUT]
    for sid in expired:
        del _sessions[sid]


# ---------------------------------------------------------------------------
# Entity link builder
# ---------------------------------------------------------------------------

def _build_entity_link(entity_type: str, entity_id) -> dict | None:
    url_template = ENTITY_URL_MAP.get(entity_type)
    if not url_template:
        return None
    url = url_template.format(id=entity_id)
    label = f"View {entity_type.replace('_', ' ').title()} #{entity_id}"
    return {"label": label, "url": url}


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(title="Atlas CMMS Agent")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[FRONT_URL, "http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class ChatRequest(BaseModel):
    message: str
    session_id: str = ""


class ChatLink(BaseModel):
    label: str
    url: str


class ChatResponse(BaseModel):
    reply: str
    links: list[ChatLink] = []


@app.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    _cleanup_sessions()

    session_id = req.session_id or str(uuid.uuid4())
    messages = _get_session(session_id)
    messages.append({"role": "user", "content": req.message})

    tools = get_all_tools()
    functions = get_all_functions()
    links: list[dict] = []

    # Agent loop — same logic as agent.py
    while True:
        try:
            response = client.chat.completions.create(
                model=MODEL,
                messages=messages,
                tools=tools,
                tool_choice="auto",
            )
        except Exception as e:
            return ChatResponse(reply=f"Error reaching LLM API: {e}", links=[])

        choice = response.choices[0]

        if choice.finish_reason == "tool_calls" or choice.message.tool_calls:
            assistant_msg = choice.message
            messages.append(assistant_msg)

            for tool_call in assistant_msg.tool_calls:
                fn_name = tool_call.function.name
                try:
                    fn_args = json.loads(tool_call.function.arguments)
                except json.JSONDecodeError:
                    fn_args = {}

                fn = functions.get(fn_name)
                if fn:
                    try:
                        result = fn(**fn_args)
                    except Exception as e:
                        result = {"error": str(e)}
                else:
                    result = {"error": f"Unknown tool: {fn_name}"}

                # Collect entity links
                if isinstance(result, dict):
                    entity_type = result.pop("_entity", None)
                    entity_id = result.pop("_id", None)
                    if entity_type and entity_id and result.get("success"):
                        link = _build_entity_link(entity_type, entity_id)
                        if link:
                            links.append(link)

                result_str = json.dumps(result)
                messages.append({
                    "role": "tool",
                    "tool_call_id": tool_call.id,
                    "content": result_str,
                })
        else:
            reply = choice.message.content or ""
            if choice.message.content:
                messages.append({"role": "assistant", "content": reply})
            return ChatResponse(reply=reply, links=links)


@app.get("/health")
def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import uvicorn

    port = int(os.getenv("AGENT_PORT", "8001"))
    print(f"\n  Atlas CMMS Agent Server starting on http://localhost:{port}\n")
    uvicorn.run(app, host="0.0.0.0", port=port)
