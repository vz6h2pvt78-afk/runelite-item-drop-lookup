"""Classify only recorded direct Sentinel drops and bind their per-roll context."""
import argparse
import hashlib
import json
import re
from collections import Counter
from pathlib import Path
from build_monster_locations import templates
from build_sentinel_spawn import PIN

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources"
OUT = DATA / "sentinel_regular_rolls.json"
TEXT = ("Three additional item rolls per kill. At most one uses the superior unique table. "
        "Guaranteed and tertiary drops are separate.")
SUPPORT = ("In addition to 100% drops and tertiary drops, superior slayer monsters will always roll 3 additional items, "
           "of which only 1 roll may be from the superior unique table. The other rolls are performed on the regular counterpart's table.")
REGULAR_SECTIONS = {"Weapons and armour", "Runes and ammunition", "Other"}


def classify(pin, drops):
    raw = pin["wikitext"]
    if (pin["title"] != "Basilisk Sentinel" or pin["revisionId"] != 15373808
            or hashlib.sha256(raw.encode()).hexdigest() != pin["wikitextSha256"] or SUPPORT not in raw):
        raise ValueError("Changed Sentinel roll primary")
    direct = {}
    for start, _, kind, row, _ in templates(raw):
        if kind != "dropsline":
            continue
        headings = re.findall(r"^===([^=]+)===$", raw[:start], re.M)
        direct.setdefault(row.get("name"), []).append((headings[-1] if headings else "", row))
    recorded = [d for d in drops if d["monsterName"] == "Basilisk Sentinel"]
    if len(recorded) != 23 or len({d["itemName"] for d in recorded}) != 23:
        raise ValueError("Changed or ambiguous recorded Sentinel rows")
    classified = []
    for drop in recorded:
        matches = direct.get(drop["itemName"], [])
        if len(matches) != 1:
            raise ValueError("Missing or ambiguous direct Sentinel row")
        section, row = matches[0]
        if (row.get("quantity"), row.get("rarity")) != (drop["quantity"], drop["dropRate"]):
            raise ValueError("Exact Sentinel source signature changed")
        if section in REGULAR_SECTIONS and row.get("rolls") == "3" and drop["notes"] == "":
            category = "regular"
        elif section == "100%" and "rolls" not in row and row.get("rarity") == "Always":
            category = "guaranteed"
        elif section == "Tertiary" and "rolls" not in row:
            category = "tertiary"
        else:
            raise ValueError("Unreviewed Sentinel table or roll classification")
        classified.append({"category": category, "section": section, "drop": drop})
    if Counter(row["category"] for row in classified) != {"regular": 15, "guaranteed": 2, "tertiary": 6}:
        raise ValueError("Sentinel category coverage changed")
    return classified


def build():
    pin = json.loads(PIN.read_text(encoding="utf8"))
    drops = json.loads((DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
    facts = []
    for entry in classify(pin, drops):
        if entry["category"] != "regular":
            continue
        drop = entry["drop"]
        facts.append({"id": "sentinel-regular-" + drop["itemName"].lower().replace(" ", "-"),
                      "monsterName": "Basilisk Sentinel", "itemName": drop["itemName"],
                      "scope": "ITEM", "appliesTo": "DROP_CONTEXT", "type": "CONTEXT", "perRoll": True,
                      "dropSignature": {k: drop[k] for k in ("quantity", "dropRate", "notes")},
                      "text": TEXT, "evidence": [{"page": "Basilisk Sentinel", "revisionId": 15373808,
                        "sourceUrl": "https://oldschool.runescape.wiki/w/Basilisk_Sentinel?oldid=15373808"}]})
    return {"schemaVersion": 1, "checkedAt": "2026-11-05T05:24:10Z", "facts": facts}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Sentinel roll context differs from reviewed evidence")
    else:
        OUT.write_text(expected, encoding="utf8")
    print("15 exact regular Sentinel contexts; 2 guaranteed and 6 tertiary rows excluded")
