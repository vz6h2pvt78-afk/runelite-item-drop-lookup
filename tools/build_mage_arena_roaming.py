"""Pin six existing drops to a reviewed roaming encounter, without location bindings."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
PINS = ROOT / "tools/fixtures/mage_arena_roaming"
OUT = ROOT / "src/main/resources/mage_arena_roaming.json"
QUEST = "Mage Arena II"
QUEST_REVISION = 15368856
MONSTERS = {
    "Derwen": (15317252, "Ent's roots"),
    "Porazdir": (15355703, "Demon's heart"),
    "Justiciar Zachariah": (15384376, "Justiciar's hand"),
}
SHARED_SUPPORT = [
    "Here, players must defeat three powerful god followers roaming the Wilderness- [[Porazdir]], [[Justiciar Zachariah]], and [[Derwen]]-",
    "After you have received the [[enchanted symbol]], you can look for the three god followers located within the deep [[Wilderness]], around [[Lava Dragon Isle]].",
    "The followers' spawn locations are random",
    "Spawn rotations are unique for each player",
    "It's possible to kill a follower multiple times to obtain extra components",
]


def read_pin(title):
    return json.loads((PINS / (title.replace(" ", "_") + ".json")).read_text(encoding="utf8"))


def validate_primary(pin, title, revision, kind):
    if (pin["title"] != title or pin["revisionId"] != revision
            or hashlib.sha256(pin["wikitext"].encode()).hexdigest() != pin["wikitextSha256"]):
        raise ValueError("Changed roaming primary")
    boxes = [params for _, _, name, params, _ in templates(pin["wikitext"]) if name == kind]
    if len(boxes) != 1 or boxes[0].get("name") != title or boxes[0].get("members") != "Yes":
        raise ValueError("Unverified exact identity or encounter membership")


def validate_shared(pin):
    validate_primary(pin, QUEST, QUEST_REVISION, "infobox miniquest")
    if any(fragment not in pin["wikitext"] for fragment in SHARED_SUPPORT):
        raise ValueError("Missing explicit follower, roaming, tracking or repeat-encounter evidence")


def evidence(title, revision):
    return {"page": title, "revisionId": revision,
            "sourceUrl": "https://oldschool.runescape.wiki/w/" + title.replace(" ", "_") + "?oldid=" + str(revision)}


def build():
    validate_shared(read_pin(QUEST))
    raw = json.loads((ROOT / "src/main/resources/wiki_monster_drops.json").read_text(encoding="utf8"))
    signatures = Counter(tuple(row.get(key, "") for key in ("monsterName", "itemName", "quantity", "dropRate", "notes")) for row in raw)
    result = []
    for monster, (revision, remains) in MONSTERS.items():
        validate_primary(read_pin(monster), monster, revision, "infobox monster")
        for item, rate, suffix in [(remains, "Always", "remains"), ("Looting bag", "1/3", "bag")]:
            if signatures[(monster, item, "1", rate, "")] != 1:
                raise ValueError("Changed or ambiguous exact roaming drop")
            result.append({
                "id": "mage-arena-" + monster.lower().replace(" ", "-") + "-" + suffix,
                "kind": "ROAMING_ENCOUNTER", "monsterName": monster, "itemName": item,
                "quantity": "1", "dropRate": rate, "notes": "", "area": "Deep Wilderness",
                "wilderness": True, "membersOnly": True, "tracking": "Enchanted symbol",
                "evidence": [evidence(monster, revision), evidence(QUEST, QUEST_REVISION)],
            })
    return {"schemaVersion": 1, "encounters": result}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Roaming context differs from reviewed primaries")
    else:
        OUT.write_text(expected, encoding="utf8")
    print("6 existing drops,3 reviewed roaming members encounters,0 location bindings")
