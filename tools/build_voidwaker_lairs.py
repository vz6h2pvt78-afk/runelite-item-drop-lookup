"""Bind four reviewed encounter warnings to existing reward tuples, without creating drop bindings."""
import argparse
import json
import re
from pathlib import Path

from build_monster_locations import clean, templates

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources"
PINS = ROOT / "tools/fixtures/encounter_lairs/voidwaker.json"
OUTPUT = DATA / "voidwaker_lairs.json"


def reviewed(pin, locations, drops):
    name, lair = pin["monsterName"], pin["location"]
    places = [p for p in locations["locations"] if p["monsterName"] == name]
    if len(places) != 1:
        raise ValueError("Ambiguous lair for " + name)
    p = places[0]
    if (p["sourcePage"] != name or p["revisionId"] != pin["revisionId"] or p["id"] != pin["locationId"]
            or p["location"] != lair or p["wilderness"] != "YES" or not p["available"]):
        raise ValueError("Location or primary revision changed")
    lead = clean(pin["primaryEncounterLead"])
    where = re.search(r"(?:is fought in|resides in) (?:the )?" + re.escape(lair)
                      + r"(?:, a singles-plus combat area in|,? at) level (\d+) Wilderness\b", lead)
    if not lead.startswith(name + " ") or not where:
        raise ValueError("Missing normal encounter and Wilderness level")
    rows = [params for _, _, kind, params, _ in templates(pin["componentLine"]) if kind == "dropsline"]
    if len(rows) != 1 or rows[0].get("name") != pin["component"] or rows[0].get("quantity") != "1":
        raise ValueError("The reviewed reward row changed")
    target = dict(monsterName=name, itemName=pin["component"], quantity="1", dropRate=rows[0]["rarity"], notes="")
    if sum(all(d.get(k) == v for k,v in target.items()) for d in drops) != 1:
        raise ValueError("Reward must have one exact original drop")
    return dict(target, locationId=p["id"], lair=lair, revisionId=p["revisionId"], wildernessLevel=int(where[1]))


def build():
    pins = json.loads(PINS.read_text(encoding="utf8"))
    locations = json.loads((DATA/"monster_locations.json").read_text(encoding="utf8"))
    drops = json.loads((DATA/"wiki_monster_drops.json").read_text(encoding="utf8"))
    rewards = {"Artio": "Voidwaker hilt", "Callisto": "Voidwaker hilt", "Venenatis": "Voidwaker gem", "Vet'ion": "Voidwaker blade"}
    if len(pins) != 4 or {p["monsterName"]:p["component"] for p in pins} != rewards:
        raise ValueError("Unreviewed Voidwaker encounter")
    return {"schemaVersion": 1, "encounters": [reviewed(p, locations, drops) for p in pins]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Voidwaker encounter context differs from primary evidence")
    else:
        OUTPUT.write_text(expected, encoding="utf8")
    print("4 exact Voidwaker encounter contexts")
