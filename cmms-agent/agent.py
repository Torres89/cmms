"""
Atlas CMMS Terminal Agent
Run: python agent.py
"""

import os
import sys
import json
import asyncio

from dotenv import load_dotenv
from openai import OpenAI

load_dotenv()

from tool_registry import get_all_tools, get_all_functions
import browser

MODEL = os.getenv("MODEL", "minimax.minimax-m2")

client = OpenAI(
    api_key=os.getenv("OPENAI_API_KEY"),
    base_url=os.getenv("OPENAI_BASE_URL"),
)

SYSTEM_PROMPT = """You are an Atlas CMMS assistant. You help maintenance teams manage their \
computerized maintenance management system via natural language commands.

You have tools to manage:
- **Parts** (spare parts inventory): add, list, search, delete
- **Work Orders** (maintenance tasks): create, list, update status, add parts
- **Assets** (equipment): add, list, search

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
"""


def run_agent_loop():
    tools = get_all_tools()
    functions = get_all_functions()
    messages = [{"role": "system", "content": SYSTEM_PROMPT}]

    print("\n" + "=" * 60)
    print("  Atlas CMMS Agent")
    print("  Type natural language commands to manage your CMMS.")
    print("  Type 'exit' or 'quit' to stop.")
    print("=" * 60 + "\n")

    while True:
        try:
            user_input = input("atlas> ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nGoodbye!")
            break

        if not user_input:
            continue
        if user_input.lower() in ("exit", "quit"):
            print("Goodbye!")
            break

        messages.append({"role": "user", "content": user_input})

        verification_targets = []

        while True:
            try:
                response = client.chat.completions.create(
                    model=MODEL,
                    messages=messages,
                    tools=tools,
                    tool_choice="auto",
                )
            except Exception as e:
                print(f"\n  [error] Cannot reach LLM API: {e}")
                print("  Check OPENAI_BASE_URL and OPENAI_API_KEY in .env\n")
                messages.pop()
                break

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

                    print(f"  -> {fn_name}({json.dumps(fn_args, indent=None)})")

                    fn = functions.get(fn_name)
                    if fn:
                        try:
                            result = fn(**fn_args)
                        except Exception as e:
                            result = {"error": str(e)}
                    else:
                        result = {"error": f"Unknown tool: {fn_name}"}

                    if isinstance(result, dict):
                        entity_type = result.pop("_entity", None)
                        entity_id = result.pop("_id", None)
                        if entity_type and entity_id and result.get("success"):
                            verification_targets.append((entity_type, entity_id))

                    result_str = json.dumps(result)
                    messages.append({
                        "role": "tool",
                        "tool_call_id": tool_call.id,
                        "content": result_str,
                    })
            else:
                if choice.message.content:
                    print(f"\n{choice.message.content}\n")
                break

        for entity_type, entity_id in verification_targets:
            try:
                browser.verify_entity(entity_type, entity_id)
            except Exception as e:
                print(f"  [browser] Verification skipped: {e}")


def main():
    try:
        run_agent_loop()
    finally:
        try:
            asyncio.run(browser.close())
        except Exception:
            pass


if __name__ == "__main__":
    main()
