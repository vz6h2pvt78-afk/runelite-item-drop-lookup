"""Preserve three exact elite-clue reward conditions from two pinned primaries."""
import argparse
import hashlib
import json
from pathlib import Path

from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources"
PINS = ROOT / "tools/fixtures/elite_clue_conditions"
OUT = DATA / "elite_clue_conditions.json"
REVISIONS = {"Aberrant spectre": 15371564, "King Black Dragon": 15385987}
SPECTRE_PROOF = ("<ref name=elite group=d>Only if you have an [[Treasure Trails/Full guide/Elite#Cryptic clues|elite cryptic clue]] "
                 "that requires you to kill an aberrant spectre; in this case, it will drop the next step of the Treasure Trail "
                 "(or a casket, if this was the last step).</ref>")
SPECTRE_REFERENCE = "<ref name=elite group=d />"
KBD_PROOF = ("<ref group=d>The key is only dropped when completing an elite clue scroll asking you to kill the King Black Dragon. "
             "It will only be dropped if the player does not have a copy of the key in their bank or inventory, "
             "however the [[Drop trick]] can be used to obtain duplicates.</ref>")
REVIEWED = [
    ("elite-clue-aberrant-spectre-next-step", "Aberrant spectre", "Clue scroll (elite)",
     "Only for a non-final elite cryptic clue step that asks you to kill an aberrant spectre."),
    ("elite-clue-aberrant-spectre-casket", "Aberrant spectre", "Reward casket (elite)",
     "Only for the final elite cryptic clue step that asks you to kill an aberrant spectre."),
    ("elite-clue-king-black-dragon-key", "King Black Dragon", "Key (elite)",
     "Only for an elite clue step asking for a King Black Dragon kill, with no Key (elite) in your bank or inventory."),
]


def extract(pins, drops):
    rows = {}
    for name, revision in REVISIONS.items():
        pin = pins[name]
        raw = pin["wikitext"]
        if (pin["title"] != name or pin["revisionId"] != revision
                or hashlib.sha256(raw.encode()).hexdigest() != pin["wikitextSha256"]):
            raise ValueError("Changed elite clue primary")
        direct = [(fields.get("name"), fields) for _, _, kind, fields, _ in templates(raw) if kind == "dropsline"]
        for _, monster, item, _ in REVIEWED:
            if monster != name:
                continue
            matches = [fields for item_name, fields in direct if item_name == item]
            if len(matches) != 1:
                raise ValueError("Missing or ambiguous direct clue reward")
            rows[(name, item)] = matches[0]
    if rows[("Aberrant spectre", "Clue scroll (elite)")].get("raritynotes") != SPECTRE_PROOF:
        raise ValueError("Missing explicit next-step/final-casket distinction")
    if rows[("Aberrant spectre", "Reward casket (elite)")].get("raritynotes") != SPECTRE_REFERENCE:
        raise ValueError("Unresolved casket named reference")
    if pins["Aberrant spectre"]["wikitext"].count(SPECTRE_PROOF) != 1:
        raise ValueError("Ambiguous shared elite reference")
    if rows[("King Black Dragon", "Key (elite)")].get("raritynotes") != KBD_PROOF:
        raise ValueError("Missing KBD clue or existing-key condition")
    facts = []
    for identifier, monster, item, text in REVIEWED:
        row = rows[(monster, item)]
        if (row.get("quantity"), row.get("rarity")) != ("1", "Always"):
            raise ValueError("Changed direct elite reward signature")
        if set(row) - {"name", "quantity", "rarity", "raritynotes", "gemw", "bucket"}:
            raise ValueError("Unreviewed elite reward metadata")
        recorded = [drop for drop in drops if drop["monsterName"] == monster and drop["itemName"] == item]
        if len(recorded) != 1 or any(recorded[0].get(key) != value for key, value in {"quantity": "1", "dropRate": "Always", "notes": ""}.items()):
            raise ValueError("Changed or ambiguous recorded elite reward")
        facts.append({
            "id": identifier, "monsterName": monster, "itemName": item,
            "scope": "ITEM", "appliesTo": "ITEM_DROP", "type": "CONTEXT",
            "dropSignature": {"quantity": "1", "dropRate": "Always", "notes": ""}, "text": text,
            "evidence": [{"page": monster, "revisionId": REVISIONS[monster],
                "sourceUrl": "https://oldschool.runescape.wiki/w/" + monster.replace(" ", "_") + "?oldid=" + str(REVISIONS[monster])}],
        })
    return {"schemaVersion": 1, "checkedAt": "2026-11-05T14:36:47Z", "facts": facts}


def build():
    pins = {name: json.loads((PINS / (name.replace(" ", "_") + ".json")).read_text(encoding="utf8")) for name in REVISIONS}
    drops = json.loads((DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
    return extract(pins, drops)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Elite clue conditions differ from their primaries")
    else:
        OUT.write_text(expected, encoding="utf8")
    print("3exact conditional elite rewards; non-final clue, final casket and KBD key kept distinct")
