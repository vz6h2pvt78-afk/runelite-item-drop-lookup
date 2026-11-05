"""Reuse pinned encounter and recipient evidence for six exact bone rewards."""
import argparse
import json
from pathlib import Path
from build_group_rewards import PINS as GROUP_PINS, BOSSES, BONES, parse_row, reward_facts
from build_voidwaker_lairs import PINS as LAIR_PINS, reviewed

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources"
OUT = DATA / "bone_reward_lairs.json"


def extract(groups, lairs, locations, drops):
    if len(groups) != 3 or {pin["name"] for pin in groups} != BOSSES.keys():
        raise ValueError("Unreviewed bone boss set")
    result = []
    for group in groups:
        reward_facts(group, drops)
        name = group["name"]
        lair = lairs[name]
        if lair["revisionId"] != group["revisionId"]:
            raise ValueError("Lair and reward primary revisions disagree")
        for line in group["bones"]:
            row = parse_row(line)
            if row["name"] not in BONES or row["rarity"] != "Always":
                raise ValueError("Unreviewed bone reward")
            adapted = dict(lair, component=row["name"], componentLine=line)
            encounter = reviewed(adapted, locations, drops)
            identifier = "bone-" + name.lower().replace("'", "") + "-" + row["name"].lower().replace(" ", "-")
            result.append(dict(id=identifier, **encounter))
    if len(result) != 6 or len({row["id"] for row in result}) != 6:
        raise ValueError("Expected exactly six distinct bone signatures")
    return {"schemaVersion": 1, "encounters": result}


def build():
    read = lambda path: json.loads(path.read_text(encoding="utf8"))
    return extract(read(GROUP_PINS), {pin["monsterName"]: pin for pin in read(LAIR_PINS)},
                   read(DATA / "monster_locations.json"), read(DATA / "wiki_monster_drops.json"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Bone encounter warnings differ from the pinned evidence")
    else:
        OUT.write_text(expected, encoding="utf8")
    print("6 exact group-boss bone encounter warnings; recipient facts unchanged")
