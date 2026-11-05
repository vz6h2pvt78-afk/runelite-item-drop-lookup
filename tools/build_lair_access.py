"""Reproduce four named Wilderness entrance gates from their own pinned Wiki excerpts."""
import argparse
import json
import re
from pathlib import Path

from build_monster_locations import clean

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
PINS = ROOT / "tools/fixtures/encounter_lairs/entrances.json"
OUTPUT = RESOURCES / "lair_access.json"


def entrance(pin, locations):
    name, revision = pin["monsterName"], pin["sourceRevision"]
    places = [p for p in locations if p["monsterName"] == name]
    if len(places) != 1:
        raise ValueError("Expected one location for " + name)
    place = places[0]
    if (place["sourcePage"] != name or place["revisionId"] != revision or place["id"] != pin["locationId"]
            or place["location"] != pin["lair"] or place["wilderness"] != "YES" or not place["available"]):
        raise ValueError("Different lair or Wiki revision for " + name)
    boss, ordinary, tier = pin["bossTask"], pin["ordinaryTask"], pin["diaryTier"]
    plain = clean(pin["excerpt"])
    start = re.search(r"(?:To enter|In order to enter) " + re.escape(name) + r"'s? lair,", plain)
    if not start:
        raise ValueError("Missing exact lair entrance statement")
    expected = (r"(?:players must|the player must) have completed the " + tier
                + r" Wilderness diary(?: tasks)?, OR they must have a " + re.escape(boss)
                + r" boss Slayer task \(a " + ordinary + r" Slayer task does not bypass the diary requirement\)\.")
    if tier not in ("medium", "hard") or not re.fullmatch(expected, plain[start.end():].strip(), re.I):
        raise ValueError("Missing explicit diary OR boss-task alternative for " + name)
    return {"id": "lair-entry-" + place["id"], "monsterName": name, "scope": "LOCATION",
            "locationId": place["id"], "appliesTo": "ACCESS", "type": "CONTEXT",
            "text": f"Complete the {tier} Wilderness Diary or have a {boss} boss Slayer task. Ordinary {ordinary} tasks do not qualify.",
            "evidence": [{"page": name, "revisionId": revision,
                          "sourceUrl": f"https://oldschool.runescape.wiki/w/{name}?oldid={revision}"}]}


def build():
    places = json.loads((RESOURCES / "monster_locations.json").read_text(encoding="utf8"))["locations"]
    pins = json.loads(PINS.read_text(encoding="utf8"))
    if len(pins) != 4 or {p["monsterName"] for p in pins} != {"Artio", "Callisto", "Vet'ion", "Venenatis"}:
        raise ValueError("Unexpected lair entrance coverage")
    return {"schemaVersion": 1, "checkedAt": "2026-11-05T02:35:00Z",
            "facts": [entrance(p, places) for p in pins]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Lair access differs from pinned evidence")
    else:
        OUTPUT.write_text(expected, encoding="utf8")
    print("4 exact Wilderness lair entrance gates")
