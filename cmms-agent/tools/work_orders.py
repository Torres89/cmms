_client = None


def _get_client():
    global _client
    if _client is None:
        from api_client import APIClient
        _client = APIClient()
    return _client


def create_work_order(
    title: str,
    description: str = "",
    priority: str = "NONE",
    dueDate: str = None,
    asset: int = None,
    location: int = None,
    category: int = None,
    assignedTo: list = None,
    team: int = None,
) -> dict:
    body = {"title": title, "priority": priority}
    if description:
        body["description"] = description
    if dueDate:
        body["dueDate"] = dueDate
    if asset is not None:
        body["asset"] = {"id": asset}
    if location is not None:
        body["location"] = {"id": location}
    if category is not None:
        body["category"] = {"id": category}
    if assignedTo:
        body["assignedTo"] = [{"id": uid} for uid in assignedTo]
    if team is not None:
        body["team"] = {"id": team}

    result = _get_client().post("/work-orders", body)
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "success": True,
        "message": f"Work order '{title}' created successfully.",
        "workOrder": result,
        "_entity": "work_order",
        "_id": result.get("id"),
    }


def list_work_orders(
    status: str = None,
    priority: str = None,
    page: int = 0,
    pageSize: int = 10,
) -> dict:
    criteria = {
        "filterFields": [],
        "pageSize": pageSize,
        "pageNum": page,
        "direction": "DESC",
    }
    if status:
        criteria["filterFields"].append(
            {"field": "status", "value": status, "operation": "eq", "joinType": "AND"}
        )
    if priority:
        criteria["filterFields"].append(
            {"field": "priority", "value": priority, "operation": "eq", "joinType": "AND"}
        )
    result = _get_client().post_search("/work-orders/search", criteria)
    if isinstance(result, dict) and "error" in result:
        return result
    content = result.get("content", [])
    wo_list = [
        {
            "id": wo.get("id"),
            "title": wo.get("title"),
            "status": wo.get("status"),
            "priority": wo.get("priority"),
            "dueDate": wo.get("dueDate"),
            "asset": wo.get("asset", {}).get("name") if wo.get("asset") else None,
        }
        for wo in content
    ]
    return {
        "workOrders": wo_list,
        "total": result.get("totalElements", len(wo_list)),
        "page": page,
        "pageSize": pageSize,
    }


def update_wo_status(id: int, status: str) -> dict:
    valid = {"OPEN", "IN_PROGRESS", "ON_HOLD", "COMPLETE"}
    if status not in valid:
        return {"error": f"Invalid status '{status}'. Must be one of: {', '.join(sorted(valid))}"}

    result = _get_client().patch(f"/work-orders/{id}", {"status": status})
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "success": True,
        "message": f"Work order {id} status updated to {status}.",
        "_entity": "work_order",
        "_id": id,
    }


def add_parts_to_wo(id: int, partId: int, quantity: float = 1) -> dict:
    current = _get_client().get(f"/work-orders/{id}")
    if isinstance(current, dict) and "error" in current:
        return current

    existing = current.get("partQuantities", [])
    existing.append({"part": {"id": partId}, "quantity": quantity})

    result = _get_client().patch(f"/work-orders/{id}", {"partQuantities": existing})
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "success": True,
        "message": f"Added part {partId} (qty {quantity}) to work order {id}.",
        "_entity": "work_order",
        "_id": id,
    }


TOOL_FUNCTIONS = {
    "create_work_order": create_work_order,
    "list_work_orders": list_work_orders,
    "update_wo_status": update_wo_status,
    "add_parts_to_wo": add_parts_to_wo,
}

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "create_work_order",
            "description": "Create a new maintenance work order in Atlas CMMS",
            "parameters": {
                "type": "object",
                "properties": {
                    "title": {"type": "string", "description": "Work order title (required)"},
                    "description": {"type": "string", "description": "Detailed description of the work"},
                    "priority": {
                        "type": "string",
                        "enum": ["NONE", "LOW", "MEDIUM", "HIGH"],
                        "description": "Priority level",
                        "default": "NONE",
                    },
                    "dueDate": {"type": "string", "description": "Due date in ISO format (YYYY-MM-DD)"},
                    "asset": {"type": "integer", "description": "Asset ID to associate with"},
                    "location": {"type": "integer", "description": "Location ID"},
                    "category": {"type": "integer", "description": "Work order category ID"},
                    "assignedTo": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "description": "List of user IDs to assign",
                    },
                    "team": {"type": "integer", "description": "Team ID to assign"},
                },
                "required": ["title"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_work_orders",
            "description": "List work orders with optional status and priority filters",
            "parameters": {
                "type": "object",
                "properties": {
                    "status": {
                        "type": "string",
                        "enum": ["OPEN", "IN_PROGRESS", "ON_HOLD", "COMPLETE"],
                        "description": "Filter by status",
                    },
                    "priority": {
                        "type": "string",
                        "enum": ["NONE", "LOW", "MEDIUM", "HIGH"],
                        "description": "Filter by priority",
                    },
                    "page": {"type": "integer", "description": "Page number (0-based)", "default": 0},
                    "pageSize": {"type": "integer", "description": "Results per page", "default": 10},
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_wo_status",
            "description": "Update the status of a work order",
            "parameters": {
                "type": "object",
                "properties": {
                    "id": {"type": "integer", "description": "Work order ID"},
                    "status": {
                        "type": "string",
                        "enum": ["OPEN", "IN_PROGRESS", "ON_HOLD", "COMPLETE"],
                        "description": "New status",
                    },
                },
                "required": ["id", "status"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "add_parts_to_wo",
            "description": "Add a spare part with quantity to a work order",
            "parameters": {
                "type": "object",
                "properties": {
                    "id": {"type": "integer", "description": "Work order ID"},
                    "partId": {"type": "integer", "description": "Part ID to add"},
                    "quantity": {"type": "number", "description": "Quantity of the part to add", "default": 1},
                },
                "required": ["id", "partId"],
            },
        },
    },
]
