"""
Persistent Playwright Chrome instance for visual verification of CMMS entities.
Launches once, stays open, navigates to entity pages after create/update operations.
"""

import os
import asyncio
from pathlib import Path

_playwright = None
_browser = None
_page = None
_logged_in = False

FRONT_URL = None

ENTITY_URL_MAP = {
    "part": "/app/inventory/parts/{id}",
    "work_order": "/app/work-orders/{id}",
    "asset": "/app/assets/{id}",
    "location": "/app/locations",
    "vendor": "/app/vendors-customers/vendors/{id}",
    "team": "/app/people-teams/teams/{id}",
    "person": "/app/people-teams/people/{id}",
    "preventive_maintenance": "/app/preventive-maintenances/{id}",
}


def _get_front_url():
    global FRONT_URL
    if FRONT_URL is None:
        FRONT_URL = os.getenv("CMMS_FRONT_URL", "http://localhost:3000")
    return FRONT_URL


async def _ensure_browser():
    global _playwright, _browser, _page
    if _page is not None:
        return _page

    from playwright.async_api import async_playwright

    user_data_dir = Path(__file__).parent / ".browser-data"
    user_data_dir.mkdir(exist_ok=True)

    _playwright = await async_playwright().start()
    _browser = await _playwright.chromium.launch_persistent_context(
        user_data_dir=str(user_data_dir),
        headless=False,
        args=["--start-maximized"],
        no_viewport=True,
    )
    _page = _browser.pages[0] if _browser.pages else await _browser.new_page()
    return _page


async def _login(page):
    global _logged_in
    if _logged_in:
        return

    front_url = _get_front_url()
    email = os.getenv("CMMS_EMAIL", "")
    password = os.getenv("CMMS_PASSWORD", "")

    await page.goto(front_url, wait_until="networkidle", timeout=15000)

    current_url = page.url
    if "/app/" in current_url:
        _logged_in = True
        return

    try:
        email_input = page.locator('input[type="email"], input[name="email"]').first
        await email_input.wait_for(state="visible", timeout=5000)
        await email_input.fill(email)

        password_input = page.locator('input[type="password"]').first
        await password_input.fill(password)

        submit = page.locator('button[type="submit"]').first
        await submit.click()

        await page.wait_for_url("**/app/**", timeout=15000)
        _logged_in = True
    except Exception as e:
        print(f"  [browser] Login failed: {e}")


async def _navigate_to_entity(entity_type: str, entity_id: int):
    url_template = ENTITY_URL_MAP.get(entity_type)
    if not url_template:
        return

    front_url = _get_front_url()
    path = url_template.format(id=entity_id)
    full_url = f"{front_url}{path}"

    try:
        page = await _ensure_browser()
        await _login(page)
        await page.goto(full_url, wait_until="networkidle", timeout=15000)
        print(f"  [browser] Opened {full_url}")
    except Exception as e:
        print(f"  [browser] Could not navigate to {full_url}: {e}")


def verify_entity(entity_type: str, entity_id: int):
    """Open the entity page in Chrome for visual verification. Non-blocking-safe."""
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        loop = None

    if loop and loop.is_running():
        loop.create_task(_navigate_to_entity(entity_type, entity_id))
    else:
        asyncio.run(_navigate_to_entity(entity_type, entity_id))


async def close():
    global _playwright, _browser, _page, _logged_in
    if _browser:
        await _browser.close()
    if _playwright:
        await _playwright.stop()
    _browser = None
    _page = None
    _playwright = None
    _logged_in = False
