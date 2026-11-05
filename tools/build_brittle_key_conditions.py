"""Reproduce the two independently evidenced Brittle key drop conditions."""
import argparse
import hashlib
import json
from pathlib import Path

from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources"
PINS = ROOT / "tools/fixtures/brittle_key_conditions"
OUT = DATA / "brittle_key_conditions.json"
REVISIONS = {"Gargoyle": 15385687, "Marble gargoyle": 15383620}
TEXT = ("Requires a Gargoyles Slayer task, Slayer Tower roof access still locked, "
        "and no Brittle key in your bank or inventory.")
PROOF = ("<ref group='d'>Brittle keys are only dropped if the player has not unlocked the roof access, "
         "does not have one in their bank or inventory, and has gargoyles as their current [[Slayer task]].</ref>")


def extract(pin, drops):
    name = pin["title"]
    raw = pin["wikitext"]
    if (name not in REVISIONS or pin["revisionId"] != REVISIONS[name]
            or hashlib.sha256(raw.encode()).hexdigest() != pin["wikitextSha256"]):
        raise ValueError("Changed Brittle key primary")
    matches = [fields for _, _, kind, fields, _ in templates(raw)
               if kind == "dropsline" and fields.get("name") == "Brittle key"]
    expected = {"name": "Brittle key", "quantity": "1", "rarity": "1/150", "raritynotes": PROOF, "gemw": "No"}
    if matches != [expected]:
        raise ValueError("Missing or ambiguous exact Brittle key condition")
    signature = {"monsterName": name, "itemName": "Brittle key", "quantity": "1", "dropRate": "1/150", "notes": ""}
    recorded = [drop for drop in drops if drop["monsterName"] == name and drop["itemName"] == "Brittle key"]
    if len(recorded) != 1 or any(recorded[0].get(key) != value for key, value in signature.items()):
        raise ValueError("Changed or ambiguous Brittle key source signature")
    return {
        "id": "brittle-key-" + name.lower().replace(" ", "-"), "monsterName": name, "itemName": "Brittle key",
        "scope": "ITEM", "appliesTo": "ITEM_DROP", "type": "CONTEXT",
        "dropSignature": {key: signature[key] for key in ("quantity", "dropRate", "notes")},
        "text": TEXT,
        "evidence": [{"page": name, "revisionId": REVISIONS[name],
            "sourceUrl": "https://oldschool.runescape.wiki/w/" + name.replace(" ", "_") + "?oldid=" + str(REVISIONS[name])}],
    }


def build():
    drops = json.loads((DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
    facts = [extract(json.loads((PINS / (name.replace(" ", "_") + ".json")).read_text(encoding="utf8")), drops)
             for name in REVISIONS]
    return {"schemaVersion": 1, "checkedAt": "2026-11-05T13:12:00Z", "facts": facts}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Brittle key conditions differ from their primaries")
    else:
        OUT.write_text(expected, encoding="utf8")
    print("2 exact Brittle key conditions; rates, quantities and source eligibility preserved")
