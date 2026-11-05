"""Recover two named Wilderness encounters described in pinned page prose."""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PINS = ROOT / "tools/fixtures/encounter_lairs"
OUTPUT = ROOT / "src/main/resources/reviewed_encounter_lairs.json"
PAGES = (("spindel", "Spindel", 15358779), ("calvarion", "Calvar'ion", 15375801))


def encounter(pin, title, revision):
    if pin["title"] != title or pin["revisionId"] != revision or pin["membersField"] != "|members = Yes":
        raise ValueError("Unreviewed encounter identity or membership")
    lead = re.fullmatch(
        r"'''" + re.escape(title) + r"''' is a weaker variant of \[\[[^\]]+\]\] who is fought in the "
        r"\[\[([^\]|]+)\]\], a \[\[singles-plus combat\]\] area in level (\d+) \[\[Wilderness\]\]\. .*",
        pin["lead"])
    combat = re.fullmatch(r"\|combat = (\d+)", pin["combatField"])
    if not lead or not combat:
        raise ValueError("Missing normal lair, Wilderness level or combat level in " + title)
    return {"monsterName": title, "revisionId": revision, "lair": lead[1],
            "wildernessLevel": int(lead[2]), "combatLevel": int(combat[1])}


def build():
    return {"schemaVersion": 1, "lairs": [
        encounter(json.loads((PINS / (key + ".json")).read_text(encoding="utf8")), name, revision)
        for key, name, revision in PAGES]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Reviewed lairs differ from pinned primary evidence")
    else:
        OUTPUT.write_text(expected, encoding="utf8")
    print("2 reviewed normal encounter lairs")
