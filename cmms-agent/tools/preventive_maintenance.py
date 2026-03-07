_client = None


def _get_client():
    global _client
    if _client is None:
        from api_client import APIClient
        _client = APIClient()
    return _client


def add_preventive_maintenance(
    name: str,
    frequency: int,
    recurrenceType: str,
    recurrenceBasedOn: str,
    asset: int = None,
    location: int = None,
    assignedTo: list = None,
    team: int = None,
    startsOn: str = None,
    endsOn: str = None,
    dueDateDelay: int = None,
    daysOfWeek: list = None,
) -> dict:
    valid_recurrence_types = {"DAILY", "WEEKLY", "MONTHLY", "YEARLY"}
    if recurrenceType not in valid_recurrence_types:
        return {"error": f"Invalid recurrenceType '{recurrenceType}'. Must be one of: {', '.join(sorted(valid_recurrence_types))}"}

    valid_based_on = {"TIME", "METER"}
    if recurrenceBasedOn not in valid_based_on:
        return {"error": f"Invalid recurrenceBasedOn '{recurrenceBasedOn}'. Must be one of: {', '.join(sorted(valid_based_on))}"}

    body = {
        "name": name,
        "frequency": frequency,
        "recurrenceType": recurrenceType,
        "recurrenceBasedOn": recurrenceBasedOn,
    }
    if asset is not None:
        body["asset"] = {"id": asset}
    if location is not None:
        body["location"] = {"id": location}
    if assignedTo:
        body["assignedTo"] = [{"id": uid} for uid in assignedTo]
    if team is not None:
        body["team"] = {"id": team}
    if startsOn:
        body["startsOn"] = startsOn
    if endsOn:
        body["endsOn"] = endsOn
    if dueDateDelay is not None:
        body["dueDateDelay"] = dueDateDelay
    if daysOfWeek is not None:
        body["daysOfWeek"] = daysOfWeek

    result = _get_client().post("/preventive-maintenances", body)
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "success": True,
        "message": f"Preventive maintenance '{name}' created successfully.",
        "preventiveMaintenance": result,
        "_entity": "preventive_maintenance",
        "_id": result.get("id"),
    }


def list_preventive_maintenances(page: int = 0, pageSize: int = 10, search: str = "") -> dict:
    criteria = {
        "filterFields": [],
        "pageSize": pageSize,
        "pageNum": page,
        "direction": "DESC",
    }
    if search:
        criteria["filterFields"].append(
            {"field": "name", "value": search, "operation": "cn", "joinType": "AND"}
        )
    result = _get_client().post_search("/preventive-maintenances/search", criteria)
    if isinstance(result, dict) and "error" in result:
        return result
    content = result.get("content", [])
    pm_list = [
        {
            "id": pm.get("id"),
            "name": pm.get("name"),
            "frequency": pm.get("frequency"),
            "recurrenceType": pm.get("recurrenceType"),
            "recurrenceBasedOn": pm.get("recurrenceBasedOn"),
            "startsOn": pm.get("startsOn"),
            "asset": pm.get("asset", {}).get("name") if pm.get("asset") else None,
            "location": pm.get("location", {}).get("name") if pm.get("location") else None,
        }
        for pm in content
    ]
    return {
        "preventiveMaintenances": pm_list,
        "total": result.get("totalElements", len(pm_list)),
        "page": page,
        "pageSize": pageSize,
    }


def search_preventive_maintenances(query: str, page: int = 0, pageSize: int = 10) -> dict:
    return list_preventive_maintenances(page=page, pageSize=pageSize, search=query)


def delete_preventive_maintenance(id: int, confirmed: bool = False) -> dict:
    if not confirmed:
        return {
            "confirm_required": True,
            "message": f"Are you sure you want to delete preventive maintenance with id {id}? Call delete_preventive_maintenance again with confirmed=true to proceed.",
        }
    result = _get_client().delete(f"/preventive-maintenances/{id}")
    if isinstance(result, dict) and "error" in result:
        return result
    return {"success": True, "message": f"Preventive maintenance {id} deleted successfully."}


TOOL_FUNCTIONS = {
    "add_preventive_maintenance": add_preventive_maintenance,
    "list_preventive_maintenances": list_preventive_maintenances,
    "search_preventive_maintenances": search_preventive_maintenances,
    "delete_preventive_maintenance": delete_preventive_maintenance,
}

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "add_preventive_maintenance",
            "description": "Create a new preventive maintenance schedule in Atlas CMMS",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "PM schedule name (required)"},
                    "frequency": {"type": "integer", "description": "Recurrence frequency (e.g. every N days/weeks) (required)"},
                    "recurrenceType": {
                        "type": "string",
                        "enum": ["DAILY", "WEEKLY", "MONTHLY", "YEARLY"],
                        "description": "Recurrence period type (required)",
                    },
                    "recurrenceBasedOn": {
                        "type": "string",
                        "enum": ["TIME", "METER"],
                        "description": "Whether recurrence is time-based or meter-based (required)",
                    },
                    "asset": {"type": "integer", "description": "Asset ID to associate with"},
                    "location": {"type": "integer", "description": "Location ID"},
                    "assignedTo": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "description": "List of user IDs to assign",
                    },
                    "team": {"type": "integer", "description": "Team ID to assign"},
                    "startsOn": {"type": "string", "description": "Start date in ISO format (YYYY-MM-DD)"},
                    "endsOn": {"type": "string", "description": "End date in ISO format (YYYY-MM-DD)"},
                    "dueDateDelay": {"type": "integer", "description": "Number of days before due date to create the work order"},
                    "daysOfWeek": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "description": "Days of week (0=Sunday through 6=Saturday) for WEEKLY recurrence",
                    },
                },
                "required": ["name", "frequency", "recurrenceType", "recurrenceBasedOn"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_preventive_maintenances",
            "description": "List preventive maintenance schedules with optional name filter",
            "parameters": {
                "type": "object",
                "properties": {
                    "page": {"type": "integer", "description": "Page number (0-based)", "default": 0},
                    "pageSize": {"type": "integer", "description": "Results per page", "default": 10},
                    "search": {"type": "string", "description": "Filter by PM name (partial match)"},
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_preventive_maintenances",
            "description": "Search preventive maintenance schedules by name",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "description": "Search query"},
                    "page": {"type": "integer", "description": "Page number (0-based)", "default": 0},
                    "pageSize": {"type": "integer", "description": "Results per page", "default": 10},
                },
                "required": ["query"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_preventive_maintenance",
            "description": "Delete a preventive maintenance schedule. Requires confirmation.",
            "parameters": {
                "type": "object",
                "properties": {
                    "id": {"type": "integer", "description": "Preventive maintenance ID to delete"},
                    "confirmed": {"type": "boolean", "description": "Set to true to confirm deletion", "default": False},
                },
                "required": ["id"],
            },
        },
    },
]
