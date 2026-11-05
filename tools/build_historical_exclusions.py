"""Generate a closed reviewed historical-source list from pinned explicit prose."""
import argparse
import hashlib
import json
from pathlib import Path
from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
PINS = ROOT / "tools/fixtures/historical_sources"
OUT = ROOT / "src/main/resources/historical_source_exclusions.json"
REVISIONS = {
    "Bloodthirst rockslug": 15204801,
    "Demon of Balance": 15225385,
    "Demon of Darkness": 15355707,
    "Demon of Light": 15375355,
    "General Danknuck": 15204759,
    "Scavenger runt": 15362673,
    "Unusual Chicken": 15204760,
}
SUPPORT = {
    "Bloodthirst rockslug": (
        "The '''bloodthirst'''{{Sic}} '''rockslug''' was an unused variant of the [[giant rockslug]], the "
        "[[superior]] variant of the [[rockslug]], that would have appeared after unlocking the [[Bloodthirsty]] "
        "[[Trailblazer Reloaded League/Relics|relic]] in [[Trailblazer Reloaded League]]. Because rockslugs could "
        "not be assigned as a Slayer task during the league, it was never possible to encounter a bloodthirst "
        "rockslug."
    ),
    "Demon of Balance": (
        "It was replaced by [[Derwen]] on [[Update:PvM QoL updates & Wilderness Rejuvenation improvements|an "
        "update]] on February 8th [[2018]]."
    ),
    "Demon of Darkness": (
        "It was replaced by [[Porazdir]] on [[Update:PvM QoL updates & Wilderness Rejuvenation improvements|an "
        "update]] on February 8th [[2018]]."
    ),
    "Demon of Light": (
        "It was replaced by [[Justiciar Zachariah]] on [[Update:PvM QoL updates & Wilderness Rejuvenation "
        "improvements|an update]] on February 8th [[2018]]."
    ),
    "General Danknuck": (
        "'''General Danknuck''' was a high ranking [[goblin (race)|goblin]] general who lead the final attempt on "
        "the invasion on [[Falador]] during the [[2014 Goblin Invasion]] event. He could only be harmed with an "
        "[[event rpg]], and had similar drops to [[Painted Goblin]]s, albeit in higher quantities. He was removed "
        "from the game at the end of the event."
    ),
    "Scavenger runt": (
        "|change = Following a balance update, scavenger runts were removed and replaced with their [[Scavenger "
        "beast|bigger versions]] in all scavenger rooms."
    ),
    "Unusual Chicken": (
        "They were removed on [[10 December]] [[2014]] with the introduction of the [[Trading post]]."
    ),
}
INFOBOX_NAMES = {"Bloodthirst rockslug": "Bloodthirst rockslug{{Sic}}"}


def exclusion(pin):
    title = pin["title"]
    if (title not in REVISIONS or pin["revisionId"] != REVISIONS[title]
            or hashlib.sha256(pin["wikitext"].encode()).hexdigest() != pin["wikitextSha256"]):
        raise ValueError("Unreviewed historical primary")
    if pin["support"] != SUPPORT[title] or pin["support"] not in pin["wikitext"]:
        raise ValueError("Missing explicit historical encounter evidence")
    boxes = [params for _, _, kind, params, _ in templates(pin["wikitext"]) if kind == "infobox monster"]
    if len(boxes) != 1 or boxes[0].get("name") != INFOBOX_NAMES.get(title, title):
        raise ValueError("Unclear exact historical encounter identity")
    return {"monsterName": title, "revisionId": pin["revisionId"],
            "sourceUrl": "https://oldschool.runescape.wiki/w/" + title.replace(" ", "_") + "?oldid=" + str(pin["revisionId"])}


def build():
    sources = [exclusion(json.loads(file.read_text(encoding="utf8"))) for file in sorted(PINS.glob("*.json"))]
    if len(sources) != 7 or {row["monsterName"] for row in sources} != REVISIONS.keys():
        raise ValueError("Expected the seven reviewed historical sources")
    raw = json.loads((ROOT / "src/main/resources/wiki_monster_drops.json").read_text(encoding="utf8"))
    rows = [row for row in raw if row["monsterName"] in REVISIONS]
    if len(rows) != 44 or len({row["itemName"] for row in rows}) != 39:
        raise ValueError("Re-review the forty-four raw rows and thirty-nine item identities")
    return {"schemaVersion": 1, "sources": sources}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Historical exclusions differ from reviewed primaries")
    else:
        OUT.write_text(expected, encoding="utf8")
    print("7 reviewed historical sources,44 retained raw rows and39 derived item identities")
