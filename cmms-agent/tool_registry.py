"""
Collects tool definitions and callables from all tool modules.
Each tool module exposes TOOLS (list of OpenAI tool schemas) and TOOL_FUNCTIONS (dict of name->callable).
"""

from tools import parts, work_orders, assets, locations, people, teams, vendors, preventive_maintenance

_MODULES = [parts, work_orders, assets, locations, people, teams, vendors, preventive_maintenance]


def get_all_tools():
    tools = []
    for mod in _MODULES:
        tools.extend(mod.TOOLS)
    return tools


def get_all_functions():
    funcs = {}
    for mod in _MODULES:
        funcs.update(mod.TOOL_FUNCTIONS)
    return funcs
