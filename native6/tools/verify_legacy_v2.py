#!/usr/bin/env python3
"""Read-only verification of AlSafa Backup v2. Does not modify backup data.

Usage: python3 native6/tools/verify_legacy_v2.py /path/to/AlSafa-Back.json
"""
import collections
import json
import sys
from pathlib import Path

SECTIONS = (
    "products", "maintenanceParts", "sales", "repairs", "purchases",
    "suppliers", "payments", "returns", "cashTransactions",
    "supplierLedger", "partnerWithdrawals", "dailyClosings", "expenses",
)
TARGETS = {
    "sale": "sales", "repair": "repairs", "purchase": "purchases",
    "supplier": "suppliers", "partnerWithdrawal": "partnerWithdrawals",
    "expense": "expenses", "dailyClosing": "dailyClosings", "return": "returns",
}
WORKFLOW_TYPES = {
    "currencyExchange", "opening", "sale-payment-correction",
    "repair-payment-correction",
}

def verify(path):
    with Path(path).open("r", encoding="utf-8") as stream:
        source = json.load(stream)
    if source.get("format") != "alsafa-accounting-backup" or source.get("formatVersion") != 2:
        raise ValueError("Expected AlSafa Backup v2")
    data = source["data"]
    ids = {}
    issues = []
    counts = {}
    for section in SECTIONS:
        rows = data.get(section, [])
        counts[section] = len(rows)
        keys = [r.get("id") for r in rows]
        ids[section] = set(keys)
        if not all(keys):
            issues.append(f"{section}: missing IDs")
        if len(keys) != len(ids[section]):
            issues.append(f"{section}: duplicate IDs")
    references = collections.Counter()
    for section in ("payments", "returns", "cashTransactions"):
        for record in data.get(section, []):
            reference = record.get("referenceId")
            if not reference:
                continue
            kind = record.get("referenceType", "")
            if kind in WORKFLOW_TYPES:
                references[(section, "workflow")] += 1
            elif kind not in TARGETS:
                references[(section, "unknown")] += 1
                issues.append(f"{section}: unknown reference type {kind}")
            elif reference not in ids[TARGETS[kind]]:
                references[(section, "broken")] += 1
                issues.append(f"{section}: broken {kind} reference")
            else:
                references[(section, "linked")] += 1
    duplicate_invoice_numbers = {}
    for section in ("sales", "repairs"):
        duplicates = collections.Counter(
            str(r.get("invoiceNo")) for r in data.get(section, []) if r.get("invoiceNo") is not None
        )
        duplicate_invoice_numbers[section] = {k: n for k, n in duplicates.items() if n > 1}
    return {
        "format": source["format"],
        "formatVersion": source["formatVersion"],
        "counts": counts,
        "referenceCounts": {
            f"{section}/{status}": n for (section, status), n in sorted(references.items())
        },
        "duplicateInvoiceNumbers": duplicate_invoice_numbers,
        "issues": issues[:100],
        "issueCount": len(issues),
        "financialPosting": "NONE",
        "operationalRestoreVerified": False,
    }

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify_legacy_v2.py BACKUP.json")
    result = verify(sys.argv[1])
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(1 if result["issueCount"] else 0)
