"""Offline, exact-row Temple Supplies correction; no discovery or network reads."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tools/fixtures/temple_supplies_patch.json"
RESOURCE = ROOT / "src/main/resources/wiki_shop_sources.json"


def reviewed_patch(rows, fixture):
    source = fixture["wikitext"]
    if (fixture["title"] != "Temple Supplies" or fixture["revisionId"] != 15318800
            or hashlib.sha256(source.encode("utf-8")).hexdigest() != fixture["wikitextSha256"]):
        raise ValueError("Evidence pin changed; review required")
    if source.count("{{StoreTableHead|currency=Abyssal pearls|") != 1:
        raise ValueError("Expected one explicit Abyssal pearls table")
    entries = {row["itemName"]: row for row in fixture["patchEntries"]}
    if len(entries) != 18 or len(fixture["patchEntries"]) != 18:
        raise ValueError("Unexpected reviewed item set")
    evidence_rows = {}
    for line in source.splitlines():
        match = re.match(r"\{\{StoreLine\|name=([^|]+)\|.*?\|sell=(\d+)\|", line)
        if match:
            evidence_rows[match[1].casefold()] = (int(match[2]), line)
    target = [row for row in rows if row.get("sourceName") == "Temple Supplies"]
    if len(target) != 18 or {r["itemName"] for r in target} != set(entries):
        raise ValueError("Existing Temple Supplies identity/count changed")
    result = copy.deepcopy(rows)
    changes = []
    for row in result:
        if row.get("sourceName") != "Temple Supplies":
            continue
        entry = entries[row["itemName"]]
        amount, evidence_line = evidence_rows[row["itemName"].casefold()]
        if amount != entry["amount"]:
            raise ValueError("Reviewed amount differs from pinned item row")
        if row["sourceType"] != "Shop" or row["location"] != "Temple of the Eye":
            raise ValueError("Existing source identity changed")
        quest = entry["quest"]
        if quest and ("Only available after completing [[" + quest + "]]" not in evidence_line):
            raise ValueError("Quest condition not supported by this item row")
        if not quest and "Only available after completing" in evidence_line:
            raise ValueError("Unreviewed item-specific unlock")
        after = {"cost": f"{amount:,} Abyssal pearls", "notes": "Members shop."}
        if quest:
            after["notes"] += " Requires " + quest + " completion."
        current = {field: row[field] for field in ("cost", "notes")}
        if current == after:
            continue
        if current != entry["expectedBefore"]:
            raise ValueError("Expected previous cost/notes changed: " + row["itemName"])
        changes.append({"itemName": row["itemName"], "before": current, "after": after})
        row.update(after)
    return result, changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--apply", action="store_true")
    mode.add_argument("--check", action="store_true")
    args = parser.parse_args()
    fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
    before = json.loads(RESOURCE.read_text(encoding="utf-8"))
    after, changes = reviewed_patch(before, fixture)
    if args.apply and changes:
        RESOURCE.write_text(json.dumps(after, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"reviewedRows": 18, "changes": changes, "applied": bool(args.apply and changes)}, indent=2))
    return 0 if args.apply or not changes else 1


if __name__ == "__main__":
    raise SystemExit(main())
