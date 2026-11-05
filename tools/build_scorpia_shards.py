"""Restore two Scorpia encounter warnings using the original primary, lair and shard rows."""
import argparse
from collections import Counter
import json
import re
from pathlib import Path
from build_monster_locations import clean, templates

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
OUT = RES / "scorpia_shard_lairs.json"
PIN = ROOT / "tools/fixtures/encounter_lairs/scorpia.json"
CAVE = "Cave in the Scorpion Pit (north-east Wilderness)"
CAVE_ID = "f3fb4ec480822cf6cbb2"
ITEMS = {"Malediction shard 3", "Odium shard 3"}


def shard_warnings(pin, locations, drops):
    if pin["title"] != "Scorpia" or pin["revisionId"] != 15362166:
        raise ValueError("Unreviewed Scorpia primary")
    lead = re.fullmatch(r"Scorpia .* resides in a cave beneath the Scorpion Pit located in Wilderness level (\d+)-(\d+)\.",
                        clean(pin["encounter"]))
    if not lead or clean(pin["fight"]).find("player killers may attack you at any time while fighting the boss") == -1:
        raise ValueError("Missing primary location range or explicit PvP encounter warning")
    low, high = int(lead[1]), int(lead[2])
    if (low,high) != (53,55):
        raise ValueError("Re-review changed Scorpion Pit area")
    locline, = [p for _,_,kind,p,_ in templates(pin["locLine"]) if kind == "locline"]
    caves = [p for p in locations if p["monsterName"] == "Scorpia"]
    if len(caves) != 1 or clean(locline["location"]) != CAVE:
        raise ValueError("Ambiguous Scorpia cave")
    cave, = caves
    if (cave["id"] != CAVE_ID or cave["location"] != CAVE or cave["sourcePage"] != "Scorpia"
            or cave["revisionId"] != pin["revisionId"] or cave["wilderness"] != "YES" or not cave["available"]):
        raise ValueError("Scorpia cave identity changed")
    original = Counter((r["monsterName"],r["itemName"],r["quantity"],r["dropRate"],r["notes"]) for r in drops)
    shards = []
    for line in pin["shards"]:
        row, = [p for _,_,kind,p,_ in templates(line) if kind == "dropsline"]
        item = row["name"]
        if item not in ITEMS or (row["quantity"],row["rarity"]) != ("1","1/256") or original[("Scorpia",item,"1","1/256","")] != 1:
            raise ValueError("Missing unique original shard signature")
        shards.append(dict(itemName=item, monsterName="Scorpia", quantity="1", dropRate="1/256", notes="",
                           locationId=CAVE_ID, lair=CAVE, revisionId=pin["revisionId"],
                           wildernessLevelMin=low, wildernessLevelMax=high))
    if len(shards) != 2 or {s["itemName"] for s in shards} != ITEMS:
        raise ValueError("Only the two reviewed shards can receive this warning")
    return {"schemaVersion":1, "shards":shards}


def build():
    read = lambda path: json.loads(path.read_text(encoding="utf8"))
    return shard_warnings(read(PIN), read(RES/"monster_locations.json")["locations"], read(RES/"wiki_monster_drops.json"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check",action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(),indent=2,ensure_ascii=False)+"\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Scorpia warning differs from its primary evidence")
    else:
        OUT.write_text(expected,encoding="utf8")
    print("2 Scorpia shard encounter warnings for the reviewed Wilderness area range")
