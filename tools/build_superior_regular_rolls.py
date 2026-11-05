"""Bind reviewed direct regular rolls for three exact superior monsters."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re

from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources"
PINS = ROOT / "tools/fixtures/superior_regular_rolls"
OUT = DATA / "superior_regular_rolls.json"
REVISIONS = {"Cave abomination": 15373805, "Marble gargoyle": 15383620, "Choke devil": 15373841}
EXPECTED = {
    "Cave abomination": Counter(regular=14, guaranteed=2, tertiary=2),
    "Marble gargoyle": Counter(regular=19, guaranteed=1, tertiary=1, held_pre_roll=2),
    "Choke devil": Counter(regular=23, guaranteed=2),
}
REGULAR_SECTIONS = {"Weapons and armour", "Runes", "Runes and ammunition", "Materials", "Coins", "Other"}
SUPPORT = ("In addition to 100% drops and tertiary drops, superior slayer monsters will always roll 3 additional items, "
           "of which only 1 roll may be from the superior unique table. The other rolls are performed on the regular "
           "counterpart's table; their displayed drop chances represent a single roll.")
TEXT = ("Three additional item rolls per kill, with at most one from the superior unique table. "
        "Guaranteed, tertiary and pre-roll drops are separate.")


def classify(pin, drops):
    name = pin["title"]
    raw = pin["wikitext"]
    if (name not in REVISIONS or pin["revisionId"] != REVISIONS[name]
            or hashlib.sha256(raw.encode()).hexdigest() != pin["wikitextSha256"] or SUPPORT not in raw):
        raise ValueError("Changed superior roll primary")
    boxes = [fields for _, _, kind, fields, _ in templates(raw) if kind == "infobox monster"]
    if len(boxes) != 1 or boxes[0].get("name") != name:
        raise ValueError("Unclear exact superior identity")
    direct = {}
    for start, _, kind, fields, _ in templates(raw):
        if kind != "dropsline":
            continue
        headings = re.findall(r"^===([^=]+)===$", raw[:start], re.M)
        signature = tuple(fields.get(key) for key in ("name", "quantity", "rarity"))
        direct.setdefault(signature, []).append((headings[-1] if headings else "", fields))
    recorded = [drop for drop in drops if drop["monsterName"] == name]
    signatures = [tuple(drop[key] for key in ("itemName", "quantity", "dropRate", "notes")) for drop in recorded]
    if len(recorded) != sum(EXPECTED[name].values()) or len(set(signatures)) != len(signatures):
        raise ValueError("Changed or ambiguous recorded superior rows")
    result = []
    for drop in recorded:
        signature = tuple(drop[key] for key in ("itemName", "quantity", "dropRate"))
        matches = direct.get(signature, [])
        if len(matches) != 1:
            raise ValueError("Missing or ambiguous exact superior signature")
        section, fields = matches[0]
        if section in REGULAR_SECTIONS and fields.get("rolls") == "3" and drop["notes"] == "":
            if set(fields) - {"name", "quantity", "rarity", "rolls", "gemw", "raritynotes"}:
                raise ValueError("Unreviewed regular variant metadata")
            if "raritynotes" in fields and not (name == "Choke devil" and drop["itemName"] == "Dust battlestaff"
                    and fields["raritynotes"].startswith("{{CiteTwitter|author=Mod Ash|")):
                raise ValueError("Unreviewed regular rate qualification")
            category = "regular"
        elif section == "100%" and "rolls" not in fields and fields.get("rarity") == "Always":
            category = "guaranteed"
        elif section == "Tertiary" and "rolls" not in fields:
            category = "tertiary"
        elif name == "Marble gargoyle" and section == "Pre-roll" and fields.get("rolls") == "3":
            category = "held_pre_roll"
        else:
            raise ValueError("Unreviewed superior classification")
        result.append({"category": category, "section": section, "drop": drop})
    if Counter(entry["category"] for entry in result) != EXPECTED[name]:
        raise ValueError("Superior category coverage changed")
    return result


def fact_id(drop):
    def slug(value):
        return re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return "superior-regular-" + "-".join(slug(drop[key]) for key in ("monsterName", "itemName", "quantity"))


def build():
    drops = json.loads((DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
    facts = []
    for name, revision in REVISIONS.items():
        pin = json.loads((PINS / (name.replace(" ", "_") + ".json")).read_text(encoding="utf8"))
        for entry in classify(pin, drops):
            if entry["category"] != "regular":
                continue
            drop = entry["drop"]
            facts.append({
                "id": fact_id(drop), "monsterName": name, "itemName": drop["itemName"],
                "scope": "ITEM", "appliesTo": "DROP_CONTEXT", "type": "CONTEXT", "perRoll": True,
                "dropSignature": {key: drop[key] for key in ("quantity", "dropRate", "notes")},
                "text": TEXT,
                "evidence": [{"page": name, "revisionId": revision,
                    "sourceUrl": "https://oldschool.runescape.wiki/w/" + name.replace(" ", "_") + "?oldid=" + str(revision)}],
            })
    if len({fact["id"] for fact in facts}) != 56:
        raise ValueError("Ambiguous superior context IDs")
    return {"schemaVersion": 1, "checkedAt": "2026-11-05T12:54:15Z", "facts": facts}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Superior regular rolls differ from reviewed primaries")
    else:
        OUT.write_text(expected, encoding="utf8")
    print("56 direct regular-roll contexts across three superiors; 8 guaranteed/tertiary and 2 pre-roll rows excluded")
