"""
Structure-aware chunking.

Two rules do most of the work:

* **Never split a table.** A torque table cut in half is worse than useless —
  half the rows retrieve without their headers and a technician reads the wrong
  value. Tables are emitted whole, even when that overruns the target size.
* **Carry the heading path.** Every chunk records where it came from
  ("5 Maintenance > 5.3 Spindle"), and its page range, so a citation can say
  "Maintenance Manual, p. 5-14" rather than "somewhere in the manual".
"""

import re
from typing import Iterable, Optional

import config
from parse import Page, heading_of

# A pipe table, or a run of lines that are mostly whitespace-separated columns.
_TABLE_ROW = re.compile(r"^\s*\|.*\|\s*$")


def _estimate_tokens(text: str) -> int:
    """Roughly 4 characters per token — close enough for sizing chunks."""
    return max(1, len(text) // 4)


def chunk_pages(pages: Iterable[Page]) -> list[dict]:
    """
    Turn parsed pages into chunks with section paths and page ranges.
    """
    blocks = list(_blocks(pages))
    chunks: list[dict] = []

    current_text: list[str] = []
    current_tokens = 0
    current_pages: list[int] = []
    current_section: Optional[str] = None

    def flush():
        nonlocal current_text, current_tokens, current_pages, current_section
        if not current_text:
            return
        content = "\n".join(current_text).strip()
        if content:
            chunks.append({
                "content": content,
                "section": current_section,
                "page_from": min(current_pages) if current_pages else None,
                "page_to": max(current_pages) if current_pages else None,
                "token_count": current_tokens,
            })
        current_text = []
        current_tokens = 0
        current_pages = []

    for block in blocks:
        tokens = _estimate_tokens(block["text"])

        # A table goes out on its own, whole, whatever its size.
        if block["is_table"]:
            flush()
            chunks.append({
                "content": _with_heading(block["section"], block["text"]),
                "section": block["section"],
                "page_from": block["page"],
                "page_to": block["page"],
                "token_count": tokens,
            })
            current_section = block["section"]
            continue

        # A new top-level section starts a new chunk: mixing two sections in one
        # chunk makes the citation ambiguous.
        if current_text and block["section"] != current_section:
            flush()

        if current_tokens + tokens > config.CHUNK_MAX_TOKENS and current_text:
            flush()

        if not current_text:
            current_section = block["section"]
            heading = _with_heading(block["section"], "")
            if heading:
                current_text.append(heading.strip())
                current_tokens += _estimate_tokens(heading)

        current_text.append(block["text"])
        current_tokens += tokens
        current_pages.append(block["page"])

        if current_tokens >= config.CHUNK_TARGET_TOKENS:
            tail = _tail(current_text, config.CHUNK_OVERLAP_TOKENS)
            last_page = current_pages[-1]
            section = current_section
            flush()
            # A little overlap so a sentence spanning a boundary is still
            # retrievable from both sides.
            if tail:
                current_text = [tail]
                current_tokens = _estimate_tokens(tail)
                current_pages = [last_page]
                current_section = section

    flush()
    return [chunk for chunk in chunks if len(chunk["content"].strip()) >= 40]


def _with_heading(section: Optional[str], body: str) -> str:
    """Prefix the heading path so the chunk is self-describing when retrieved."""
    if not section:
        return body
    return f"[{section}]\n{body}" if body else f"[{section}]"


def _tail(lines: list[str], overlap_tokens: int) -> str:
    text = "\n".join(lines)
    characters = overlap_tokens * 4
    if len(text) <= characters:
        return ""
    tail = text[-characters:]
    # Start the overlap at a sentence boundary where we can.
    boundary = tail.find(". ")
    return tail[boundary + 2:] if 0 <= boundary < len(tail) - 40 else tail


def _blocks(pages: Iterable[Page]):
    """
    Walk pages line by line, tracking the heading path and grouping table rows.
    """
    heading_stack: list[tuple[int, str]] = []

    for page in pages:
        table_buffer: list[str] = []

        for raw_line in page.text.splitlines():
            line = raw_line.rstrip()
            if not line.strip():
                if table_buffer:
                    yield _table_block(table_buffer, page.number, _section(heading_stack))
                    table_buffer = []
                continue

            if _TABLE_ROW.match(line):
                table_buffer.append(line)
                continue

            if table_buffer:
                yield _table_block(table_buffer, page.number, _section(heading_stack))
                table_buffer = []

            found = heading_of(line)
            if found:
                level, title = found
                while heading_stack and heading_stack[-1][0] >= level:
                    heading_stack.pop()
                heading_stack.append((level, title))
                continue

            yield {
                "text": line,
                "page": page.number,
                "section": _section(heading_stack),
                "is_table": False,
            }

        if table_buffer:
            yield _table_block(table_buffer, page.number, _section(heading_stack))


def _table_block(rows: list[str], page: int, section: Optional[str]) -> dict:
    return {
        "text": "\n".join(rows),
        "page": page,
        "section": section,
        "is_table": True,
    }


def _section(stack: list[tuple[int, str]]) -> Optional[str]:
    if not stack:
        return None
    return " > ".join(title for _level, title in stack)
