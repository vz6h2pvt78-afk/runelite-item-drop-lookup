"""Add one ordinary shaman source from a direct unique row and its parent transclusion."""
import argparse
import hashlib
import json
from pathlib import Path
from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
PINS = ROOT / "tools/fixtures/dragon_warhammer"
OUT = ROOT / "src/main/resources/dragon_warhammer_source.json"


def extract(parent, unique, existing):
    evidence = []
    for pin, title, revision in ((parent, "Lizardman shaman", 15371945),
                                 (unique, "Template:Uniques/Lizardman shaman", 14669471)):
        if (pin["title"] != title or pin["revisionId"] != revision
                or hashlib.sha256(pin["wikitext"].encode()).hexdigest() != pin["wikitextSha256"]):
            raise ValueError("Changed Dragon warhammer primary")
        evidence.append({"page": title, "revisionId": revision,
                         "sourceUrl": "https://oldschool.runescape.wiki/w/" + title.replace(" ", "_") + "?oldid=" + str(revision)})
    parent_templates = list(templates(parent["wikitext"]))
    boxes = [p for _, _, kind, p, _ in parent_templates if kind == "infobox monster"]
    if len(boxes) != 1:
        raise ValueError("Ambiguous parent identity")
    box = boxes[0]
    if (box.get("name"), box.get("version1"), box.get("version2"), box.get("members")) != (
            "Lizardman shaman", "Standard", "Lizardman Temple", "Yes"):
        raise ValueError("Changed ordinary shaman versions")
    if "{{otheruses|the shamans encountered on the surface|the Chambers of Xeric variant|Lizardman shaman (Chambers of Xeric)}}" not in parent["wikitext"]:
        raise ValueError("Missing raid distinction")
    calls = [(start, p, positional) for start, _, kind, p, positional in parent_templates if kind == "uniques/lizardman shaman"]
    if len(calls) != 1 or calls[0][1] or calls[0][2] or calls[0][0] < parent["wikitext"].index("==Drops=="):
        raise ValueError("Missing unqualified ordinary unique transclusion")
    parsed = list(templates(unique["wikitext"]))
    if [kind for _, _, kind, _, _ in parsed] != ["dropstablehead", "dropsline", "dropstablebottom"]:
        raise ValueError("Unique table delegates or changes row structure")
    row = parsed[1][3]
    if row != {"name": "Dragon warhammer", "quantity": "1", "rarity": "1/3000"}:
        raise ValueError("Changed exact unique row or conditions")
    if not unique["wikitext"].startswith("<includeonly>===Pre-roll==="):
        raise ValueError("Unreviewed unique table heading")
    if "The drop rate of the dragon warhammer has been increased from 1/5,000 to 1/3,000." not in parent["wikitext"]:
        raise ValueError("Missing independent parent rate")
    if not any(d["monsterName"] == "Lizardman shaman" for d in existing):
        raise ValueError("Missing ordinary parent in existing data")
    if any(d["monsterName"] == "Lizardman shaman" and d["itemName"] == "Dragon warhammer" for d in existing):
        raise ValueError("Reconcile an already recorded Dragon warhammer source")
    source = {"itemName": "Dragon warhammer", "monsterName": "Lizardman shaman", "quantity": "1",
              "dropRate": "1/3000", "notes": "", "membersOnly": True, "dropTable": "Pre-roll", "evidence": evidence}
    return {"schemaVersion": 1, "sources": [source]}


def build():
    read = lambda path: json.loads(path.read_text(encoding="utf8"))
    return extract(read(PINS / "parent.json"), read(PINS / "unique.json"),
                   read(ROOT / "src/main/resources/wiki_monster_drops.json"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Dragon warhammer source differs from reviewed primaries")
    else:
        OUT.write_text(expected, encoding="utf8")
    print("1 exact ordinary Lizardman shaman Dragon warhammer source; no location binding")
