import os
import json
from dotenv import load_dotenv
from openai import OpenAI

load_dotenv()

client = OpenAI(
    api_key=os.getenv("OPENAI_API_KEY"),
    base_url=os.getenv("OPENAI_BASE_URL"),
)

MODEL = "minimax.minimax-m2"

# ---------------------------------------------------------------------------
# STEP 1: Define your tools as plain Python functions
# ---------------------------------------------------------------------------

def get_weather(city: str) -> dict:
    """Simulated weather lookup — replace with a real API call."""
    fake_data = {
        "new york": {"temp_f": 45, "condition": "Cloudy"},
        "london": {"temp_f": 52, "condition": "Rainy"},
        "tokyo": {"temp_f": 68, "condition": "Sunny"},
    }
    result = fake_data.get(city.lower(), {"temp_f": 70, "condition": "Unknown city, assuming nice"})
    return {"city": city, **result}


def calculate(expression: str) -> dict:
    """Evaluate a math expression safely."""
    allowed = set("0123456789+-*/.() ")
    if not all(c in allowed for c in expression):
        return {"error": "Invalid characters in expression"}
    try:
        return {"expression": expression, "result": eval(expression)}
    except Exception as e:
        return {"expression": expression, "error": str(e)}


def search_notes(query: str) -> dict:
    """Simulated search over user notes — replace with a real DB/vector search."""
    notes = [
        {"id": 1, "text": "Meeting with Alice on Friday at 3pm about the Q2 roadmap."},
        {"id": 2, "text": "Dentist appointment next Monday at 10am."},
        {"id": 3, "text": "Buy groceries: milk, eggs, bread, and coffee."},
    ]
    matches = [n for n in notes if query.lower() in n["text"].lower()]
    return {"query": query, "results": matches if matches else "No notes found."}


# ---------------------------------------------------------------------------
# STEP 2: Map function names to callables + define JSON schemas for the LLM
# ---------------------------------------------------------------------------

TOOL_FUNCTIONS = {
    "get_weather": get_weather,
    "calculate": calculate,
    "search_notes": search_notes,
}

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "Get the current weather for a city",
            "parameters": {
                "type": "object",
                "properties": {
                    "city": {"type": "string", "description": "City name, e.g. 'New York'"}
                },
                "required": ["city"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "calculate",
            "description": "Evaluate a mathematical expression and return the result",
            "parameters": {
                "type": "object",
                "properties": {
                    "expression": {"type": "string", "description": "Math expression, e.g. '2 + 2 * 3'"}
                },
                "required": ["expression"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_notes",
            "description": "Search the user's personal notes for a keyword or topic",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "description": "Search keyword, e.g. 'dentist'"}
                },
                "required": ["query"],
            },
        },
    },
]

# ---------------------------------------------------------------------------
# STEP 3: The agent loop — send messages, execute tools, repeat
# ---------------------------------------------------------------------------

def run_agent(user_message: str):
    print(f"\n{'='*60}")
    print(f"USER: {user_message}")
    print('='*60)

    messages = [
        {"role": "system", "content": "You are a helpful assistant. Use the available tools when needed to answer the user's question. You can call multiple tools if necessary."},
        {"role": "user", "content": user_message},
    ]

    while True:
        print("\n>> Sending to LLM...")
        response = client.chat.completions.create(
            model=MODEL,
            messages=messages,
            tools=TOOLS,
            tool_choice="auto",
        )

        choice = response.choices[0]

        if choice.finish_reason == "tool_calls" or (choice.message.tool_calls):
            # The LLM wants to call one or more tools
            assistant_msg = choice.message
            messages.append(assistant_msg)

            for tool_call in assistant_msg.tool_calls:
                fn_name = tool_call.function.name
                fn_args = json.loads(tool_call.function.arguments)

                print(f"   TOOL CALL: {fn_name}({fn_args})")

                fn = TOOL_FUNCTIONS.get(fn_name)
                if fn:
                    result = fn(**fn_args)
                else:
                    result = {"error": f"Unknown tool: {fn_name}"}

                print(f"   TOOL RESULT: {json.dumps(result)}")

                messages.append({
                    "role": "tool",
                    "tool_call_id": tool_call.id,
                    "content": json.dumps(result),
                })

            # Loop back — the LLM will see the tool results and continue
        else:
            # The LLM is done — it has a final text answer
            print(f"\nASSISTANT: {choice.message.content}")
            break


# ---------------------------------------------------------------------------
# STEP 4: Try it out with different queries
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    run_agent("What's the weather in Tokyo and New York?")
    run_agent("What is 1547 * 38 + 92?")
    run_agent("Do I have any notes about a dentist?")
    run_agent("What's 2+2 and what's the weather in London?")