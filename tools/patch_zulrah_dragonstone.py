"""One offline, evidence-pinned missing-source repair. Default is read-only."""
import argparse
import copy
from fractions import Fraction
from hashlib import sha256
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / "src/main/resources/wiki_monster_drops.json"
FIXTURE = ROOT / "tools/fixtures/zulrah_dragonstone.json"
EXPECTED = {"itemName": "Dragonstone", "monsterName": "Zulrah", "quantity": "1", "dropRate": "5/7968",
            "notes": "Rare drop table. Rate and quantity are per roll; two rolls per kill."}
PINS = {"Zulrah": 15385964, "Template:RareDropTable": 15104778, "Module:RareDropLines": 15343549}


def reviewed_patch(rows, fixture):
    pins = fixture["evidence"]
    if len(pins) != 3 or {p["title"]: p["revisionId"] for p in pins} != PINS:
        raise ValueError("Evidence pin set changed")
    for pin in pins:
        if sha256(pin["wikitext"].encode()).hexdigest() != pin["wikitextSha256"]:
            raise ValueError("Evidence pin text changed")
    texts = {p["title"]: p["wikitext"] for p in pins}
    call = re.search(r"\{\{RareDropTable\|([0-9]+/[0-9]+)\|naturetalisman=yes\|rolls=([0-9]+)\}\}", texts["Zulrah"])
    if call is None or call[1] != "10/249" or call[2] != "2":
        raise ValueError("Zulrah table call changed")
    if "{{#invoke:RareDropLines|main}}" not in texts["Template:RareDropTable"]:
        raise ValueError("Template module chain changed")
    outcome = re.search(r"item\s*=\s*'Dragonstone',\s*quantity\s*=\s*(\d+),\s*rarity\s*=\s*rdt_rate\s*\*\s*(\d+/\d+)", texts["Module:RareDropLines"])
    if outcome is None or outcome[1] != "1" or outcome[2] != "2/128":
        raise ValueError("Nested Dragonstone outcome changed")
    chance = Fraction(call[1]) * Fraction(outcome[2])
    if chance != Fraction(5, 7968) or str(chance) != EXPECTED["dropRate"]:
        raise ValueError("Per-roll probability derivation changed")
    rendered = fixture["renderedDragonstoneRow"]
    if ('title="Dragonstone"' not in rendered or 'data-drop-fraction="2 × 1/1,593.6"' not in rendered
            or '<td data-sort-value="1">1</td>' not in rendered):
        raise ValueError("Rendered row does not confirm quantity and per-roll basis")
    if fixture["expectedRecord"] != EXPECTED:
        raise ValueError("Unreviewed replacement record")
    current = [row for row in rows if row.get("itemName") == "Dragonstone" and row.get("monsterName") == "Zulrah"]
    if current:
        if len(current) != 1 or current[0] != EXPECTED:
            raise ValueError("Existing exact source differs; review rather than overwrite")
        return copy.deepcopy(rows), False
    result = copy.deepcopy(rows)
    result.append(dict(EXPECTED))
    return result, True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--apply", action="store_true")
    mode.add_argument("--check", action="store_true")
    args = parser.parse_args()
    rows = json.loads(RESOURCE.read_text(encoding="utf-8-sig"))
    fixture = json.loads(FIXTURE.read_text(encoding="utf-8-sig"))
    result, changed = reviewed_patch(rows, fixture)
    if args.apply and changed:
        RESOURCE.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"record": EXPECTED, "additionNeeded": changed, "applied": bool(args.apply and changed)}))
    if args.check and changed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
