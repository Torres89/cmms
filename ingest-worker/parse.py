"""
Document parsing.

Docling is the default: layout detection, reading-order recovery, and
TableFormer for table structure, all running locally. Maintenance manuals are
*full* of tables — torque values, lubricant charts, interval charts, alarm-code
lists — and a parser that flattens tables destroys exactly the content you most
need.

For scanned and photocopied legacy manuals there is an OCR fallback. (Rev. 1 of
the plan proposed a local vision-language model here; that was cut. A 4.5 GB
resident VLM costs more tenants than it is worth. When a page genuinely needs
vision, it goes to the customer's own model instead.)
"""

import logging
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import config

log = logging.getLogger("ingest.parse")

_converter = None


@dataclass
class Page:
    number: int
    text: str


@dataclass
class ParsedDocument:
    pages: list[Page] = field(default_factory=list)
    markdown: str = ""
    used_ocr: bool = False
    parser: str = "none"

    @property
    def page_count(self) -> int:
        return len(self.pages)

    @property
    def total_chars(self) -> int:
        return sum(len(p.text) for p in self.pages)


def parse(path: Path, filename: str) -> ParsedDocument:
    """Parse a document into pages of text, keeping page numbers intact."""
    suffix = Path(filename).suffix.lower()

    if suffix in (".txt", ".md"):
        text = path.read_text(encoding="utf-8", errors="replace")
        return ParsedDocument(pages=[Page(1, text)], markdown=text, parser="plaintext")

    if suffix in (".csv",):
        text = path.read_text(encoding="utf-8", errors="replace")
        return ParsedDocument(pages=[Page(1, text)], markdown=text, parser="plaintext")

    parsed = _parse_with_docling(path)
    if parsed is not None and _looks_like_text(parsed):
        return parsed

    fallback = _parse_with_pypdf(path)
    if fallback is not None and _looks_like_text(fallback):
        return fallback

    # Little or no extractable text means a scan. Clean industrial scans OCR at
    # 96-99 %, which is well worth the CPU.
    if config.ENABLE_OCR:
        ocr = _parse_with_ocr(path)
        if ocr is not None:
            return ocr

    return parsed or fallback or ParsedDocument()


def _looks_like_text(parsed: ParsedDocument) -> bool:
    if parsed.page_count == 0:
        return False
    return parsed.total_chars / parsed.page_count >= config.OCR_CHARS_PER_PAGE_THRESHOLD


def _docling_converter():
    global _converter
    if _converter is None:
        from docling.document_converter import DocumentConverter

        _converter = DocumentConverter()
    return _converter


def _parse_with_docling(path: Path) -> Optional[ParsedDocument]:
    try:
        result = _docling_converter().convert(str(path))
        document = result.document
    except Exception as exc:
        log.warning("Docling could not parse %s: %s", path.name, exc)
        return None

    markdown = ""
    try:
        markdown = document.export_to_markdown()
    except Exception:
        pass

    pages: list[Page] = []
    try:
        # Docling keeps a page number on every item; grouping by it is what
        # preserves "p. 5-14" in the eventual citation.
        by_page: dict[int, list[str]] = {}
        for item, _level in document.iterate_items():
            text = getattr(item, "text", None)
            if not text:
                continue
            page_number = 1
            provenance = getattr(item, "prov", None)
            if provenance:
                page_number = getattr(provenance[0], "page_no", 1) or 1
            by_page.setdefault(int(page_number), []).append(text)
        pages = [Page(number, "\n".join(parts)) for number, parts in sorted(by_page.items())]
    except Exception as exc:
        log.debug("Could not group Docling items by page: %s", exc)

    if not pages and markdown:
        pages = [Page(1, markdown)]
    if not pages:
        return None
    return ParsedDocument(pages=pages, markdown=markdown or "\n\n".join(p.text for p in pages),
                          parser="docling")


def _parse_with_pypdf(path: Path) -> Optional[ParsedDocument]:
    try:
        from pypdf import PdfReader
    except ImportError:
        return None
    try:
        reader = PdfReader(str(path))
        pages = [Page(index + 1, (page.extract_text() or ""))
                 for index, page in enumerate(reader.pages)]
        if not pages:
            return None
        return ParsedDocument(pages=pages, markdown="\n\n".join(p.text for p in pages),
                             parser="pypdf")
    except Exception as exc:
        log.warning("pypdf could not parse %s: %s", path.name, exc)
        return None


def _parse_with_ocr(path: Path) -> Optional[ParsedDocument]:
    """Rasterise and OCR page by page, so page numbers survive."""
    try:
        import pytesseract
        from pdf2image import convert_from_path
    except ImportError:
        log.info("OCR dependencies are not installed; skipping the OCR pass")
        return None

    try:
        images = convert_from_path(str(path), dpi=200)
    except Exception as exc:
        log.warning("Could not rasterise %s for OCR: %s", path.name, exc)
        return None

    pages: list[Page] = []
    for index, image in enumerate(images):
        try:
            text = pytesseract.image_to_string(image)
        except Exception as exc:
            log.warning("OCR failed on page %s of %s: %s", index + 1, path.name, exc)
            text = ""
        pages.append(Page(index + 1, text))

    if not pages:
        return None
    log.info("OCR'd %s pages of %s", len(pages), path.name)
    return ParsedDocument(pages=pages, markdown="\n\n".join(p.text for p in pages),
                          used_ocr=True, parser="ocr")


# ---------------------------------------------------------------------------
# Heading detection — the section path that goes on every chunk
# ---------------------------------------------------------------------------

_MARKDOWN_HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
# "5.3 Spindle lubrication", "5.3.1 Way lube" — how manuals actually number.
_NUMBERED_HEADING = re.compile(r"^\s*(\d+(?:\.\d+){0,3})\s+([A-Z][^\n]{2,80})$")
_SHOUTED_HEADING = re.compile(r"^\s*([A-Z][A-Z0-9 ,\-/&()]{6,70})\s*$")


def heading_of(line: str) -> Optional[tuple[int, str]]:
    """Return (level, title) if this line reads as a heading."""
    match = _MARKDOWN_HEADING.match(line)
    if match:
        return len(match.group(1)), match.group(2).strip()

    match = _NUMBERED_HEADING.match(line)
    if match:
        return match.group(1).count(".") + 1, f"{match.group(1)} {match.group(2).strip()}"

    match = _SHOUTED_HEADING.match(line)
    if match and not line.strip().endswith("."):
        return 1, match.group(1).strip().title()

    return None
