#!/usr/bin/env python3
"""Read-only Native 7 UI source audit against original tablet source archive.

Usage: python native7/tools/audit_legacy_ui.py path/to/legacy-source.zip
Does not edit either app, its database, or the archive.
"""
from __future__ import annotations

import argparse
import json
import re
import zipfile
from collections import Counter
from pathlib import Path

ROUTES = {
    "الرئيسية": "renderDashboard",
    "المنتجات": "renderProducts",
    "المبيعات": "renderSales",
    "الصيانة": "renderRepairs",
    "الموردون": "renderPurchases",
    "المصروفات": "renderExpenses",
    "الصناديق": "renderVaults",
    "التقارير": "renderReports",
    "الإعدادات": "renderSettings",
}
CSS_CLASSES = (
    "section-card", "section-title", "form-card", "form-grid",
    "field", "report-filter", "report-grid", "report-tile",
    "product-card", "product-card-head", "product-info",
    "transaction", "transaction-info", "list-row", "list-main",
    "list-side", "row-actions", "tiny-button", "status",
    "detail-list", "modal", "modal-head", "photo-picker",
    "photo-preview", "form-actions", "simple-action",
    "manage-card", "empty",
)


def css_rules(text: str, name: str) -> list[dict[str, str | int]]:
    # Match declarations from the full cascading stylesheet; do not stop at
    # the first selector, since later media rules often override it.
    pat = re.compile(r"([^{}]+)\{([^{}]*)\}", re.S)
    result = []
    for match in pat.finditer(text):
        selector = match.group(1).strip()
        if re.search(r"(?<![\w-])\." + re.escape(name) + r"(?![\w-])", selector):
            result.append({
                "offset": match.start(),
                "selector": " ".join(selector.split())[-220:],
                "declarations": " ".join(match.group(2).split())[:700],
            })
    return result



def route_elements(main_js: str, function: str) -> dict:
    """Inventory literals within one named renderer, not its invoked helpers.

    The boundary is the next top-level JS function declaration. This is a
    source-text inventory; runtime branches and templates may add elements.
    """
    head = re.search(r"(?m)^\\s*(?:async\\s+)?function\\s+" + re.escape(function) + r"\\s*\\(", main_js)
    if head is None:
        return {"present": False, "labels": [], "actions": [], "routes": []}
    next_head = re.search(r"(?m)^\\s*(?:async\\s+)?function\\s+[A-Za-z_$][\\w$]*\\s*\\(", main_js[head.end():])
    end = head.end() + next_head.start() if next_head else len(main_js)
    body = main_js[head.start():end]
    # Keep original source order, not alphabetical sorting.
    def ordered(pattern: str) -> list[str]:
        return list(dict.fromkeys(re.findall(pattern, body)))
    return {
        "present": True,
        "literal_buttons": len(re.findall(r"<button\\b", body, re.I)),
        "literal_inputs": len(re.findall(r"<input\\b|<textarea\\b", body, re.I)),
        "literal_selects": len(re.findall(r"<select\\b", body, re.I)),
        "actions": ordered(r'data-action\\s*=\\s*["\\x27]([^"\\x27]+)'),
        "routes": ordered(r'data-route\\s*=\\s*["\\x27]([^"\\x27]+)'),
        "dialog_mentions": ordered(r'\\b(?:showModal|openModal|closeModal|dialog|modal)\\b'),
        "animation_mentions": ordered(r'\\b(?:animate|transition|requestAnimationFrame|setTimeout)\\b'),
        "caveat": "Literal markup only; helpers, dynamic branches and runtime behavior are not evaluated.",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path, help="original tablet source ZIP")
    parser.add_argument("--output", type=Path, help="optional JSON report")
    args = parser.parse_args()
    with zipfile.ZipFile(args.archive) as archive:
        names = set(archive.namelist())
        if "assets/style.css" not in names or "assets/main.js" not in names:
            parser.error("Not an original tablet source ZIP: style.css/main.js missing")
        styles = archive.read("assets/style.css").decode("utf-8")
        override = archive.read("assets/styles.css").decode("utf-8") if "assets/styles.css" in names else ""
        main_js = archive.read("assets/main.js").decode("utf-8")
        index = archive.read("assets/index.html").decode("utf-8") if "assets/index.html" in names else ""
    routes = {label: {"function": function, "present": bool(re.search(
        r"\b" + re.escape(function) + r"\b", main_js
    ))} for label, function in ROUTES.items()}
    classes = {name: {
        "base": css_rules(styles, name),
        "overrides": css_rules(override, name),
    } for name in CSS_CLASSES}
    report = {
        "archive": args.archive.name,
        "routes": routes,
        "route_elements": {label: route_elements(main_js, function) for label, function in ROUTES.items()},
        "stylesheet_loads": re.findall(r"<link[^>]+href=[\"']([^\"']+\.css[^\"']*)", index),
        "css_classes": classes,
        "warnings": [
            "CSS occurrences are source evidence, not proof of native rendering parity.",
            "CSS rules within @media must be interpreted with viewport conditions.",
            "Source font rules do not supersede Native 7's user-required Tajawal.",
            "Requires device screenshot and functional verification.",
        ],
    }
    print("Legacy tablet source route inventory:")
    for label, info in routes.items():
        print(f"  {label}: {info['function']} — {'found' if info['present'] else 'MISSING'}")
    print("\nCSS selectors (style.css / styles.css):")
    for name, info in classes.items():
        print(f"  .{name}: {len(info['base'])} / {len(info['overrides'])}")
    print("\nStylesheets loaded:", ", ".join(report["stylesheet_loads"]) or "(not detected)")
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print("Report:", args.output)


if __name__ == "__main__":
    main()
