# Atlas CMMS Terminal Agent — Design

> **Date:** March 6, 2026  
> **Status:** Approved

---

## Overview

A Python terminal agent that lets maintenance teams manage Atlas CMMS via natural language commands. The agent uses an LLM (minimax-m2 via OpenAI-compatible API) with tool calling to parse commands, calls the Atlas REST API directly for speed, and opens Chrome via Playwright for visual verification of created/updated entities.

## Decisions

| Decision | Choice |
|----------|--------|
| Input style | Natural language (e.g. `add part "Bearing 6205" cost 12.50 qty 100`) |
| Execution strategy | API-first with browser verification |
| Authentication | Stored credentials in `.env`, auto-login with JWT |
| Architecture | Modular tool registry — each entity in its own file |
| LLM | `minimax.minimax-m2` via existing OpenAI-compatible endpoint |
| Rollout | Tiered: Parts + Work Orders + Assets first |

## Project Structure

```
cmms-agent/
  .env                  # CMMS_API_URL, CMMS_EMAIL, CMMS_PASSWORD, OPENAI_API_KEY, OPENAI_BASE_URL, MODEL
  agent.py              # Core REPL loop + LLM tool-calling cycle
  api_client.py         # Shared HTTP client: login, token caching, auto-refresh
  browser.py            # Playwright: persistent Chrome, navigate to entity page
  tool_registry.py      # Collects tool definitions + callables from tool modules
  tools/
    __init__.py
    parts.py            # add_part, list_parts, search_parts, delete_part
    work_orders.py      # create_work_order, list_work_orders, update_wo_status, add_parts_to_wo
    assets.py           # add_asset, list_assets, search_assets
  requirements.txt
```

## API Client (`api_client.py`)

Shared HTTP layer used by every tool module.

**Login flow:**
1. On first API call, sends `POST /auth/signin` with `{ email, password }` from `.env`
2. Stores JWT token in memory
3. Attaches `Authorization: Bearer <token>` to every request
4. On `401`, automatically re-authenticates and retries once

**Methods:** `get(path, params)`, `post(path, body)`, `post_search(path, criteria)`, `patch(path, body)`, `delete(path)`

**Error handling:**

| Error | Behavior |
|-------|----------|
| API unreachable | Friendly message: "Cannot reach API" |
| Auth failure | "Login failed. Check CMMS_EMAIL and CMMS_PASSWORD in .env" |
| 400/422 | Pass API error message to LLM for explanation |
| 403 | "Permission denied" |
| 404 | "Entity not found" |

## Tier 1 Tools

### Parts (`tools/parts.py`)

| Tool | API Call | Required | Optional |
|------|----------|----------|----------|
| `add_part` | `POST /parts` | `name` | `description`, `cost`, `quantity`, `unit`, `minQuantity`, `barcode`, `area`, `category`, `nonStock`, `additionalInfos` |
| `list_parts` | `POST /parts/search` | — | `page`, `pageSize`, `search` |
| `search_parts` | `POST /parts/search` | `query` | `page`, `pageSize` |
| `delete_part` | `DELETE /parts/{id}` | `id` | — |

### Work Orders (`tools/work_orders.py`)

| Tool | API Call | Required | Optional |
|------|----------|----------|----------|
| `create_work_order` | `POST /work-orders` | `title` | `description`, `priority`, `dueDate`, `asset`, `location`, `category`, `assignedTo`, `team` |
| `list_work_orders` | `POST /work-orders/search` | — | `status`, `priority`, `page`, `pageSize` |
| `update_wo_status` | `PATCH /work-orders/{id}` | `id`, `status` | — |
| `add_parts_to_wo` | `PATCH /work-orders/{id}` | `id`, `partId`, `quantity` | — |

### Assets (`tools/assets.py`)

| Tool | API Call | Required | Optional |
|------|----------|----------|----------|
| `add_asset` | `POST /assets` | `name` | `description`, `location`, `category`, `parentAsset`, `serialNumber`, `model`, `assignedTo`, `teams`, `vendors` |
| `list_assets` | `POST /assets/search` | — | `page`, `pageSize`, `search` |
| `search_assets` | `POST /assets/search` | `query` | `page`, `pageSize` |

## Browser Verification (`browser.py`)

- Uses a **persistent** Playwright Chrome instance (stays open between commands)
- Logs into the frontend at `http://localhost:3000` on first launch using `.env` credentials
- After create/update API calls, navigates to the entity page:

| Entity | URL |
|--------|-----|
| Part | `/app/inventory/parts/{id}` |
| Work Order | `/app/work-orders/{id}` |
| Asset | `/app/assets/{id}` |

- Skipped for list/search/delete commands
- If frontend is unavailable, prints a warning but doesn't fail

## Agent Core Loop (`agent.py`)

**System prompt** instructs the LLM to parse natural language into tool calls, ask for missing required fields, confirm destructive actions, and present results concisely.

**REPL flow:**
1. Print `atlas> ` prompt
2. Read user input (exit on `exit`/`quit`)
3. Send conversation history + tools to LLM
4. Execute any tool calls, feed results back to LLM
5. Print LLM's final response
6. Trigger browser verification if a create/update succeeded
7. Loop

**Conversation history** is kept in memory for the session to support contextual references.

## Tiered Rollout

| Tier | Entities | When |
|------|----------|------|
| **1** | Parts, Work Orders, Assets | Initial release |
| **2** | Locations, People & Teams, Vendors, Preventive Maintenance | Soon after |
| **3** | Meters, Purchase Orders, Requests, Analytics, Categories | Later |

Adding a new entity means creating a new file in `tools/` — no changes to the core agent.
