"""Qualify nine existing group-boss reward signatures using each boss's own Wiki excerpt."""
import argparse
import json
from pathlib import Path
from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources"
PINS = ROOT / "tools/fixtures/encounter_lairs/group_rewards.json"
OUTPUT = DATA / "group_reward_access.json"
BOSSES = {"Callisto": (15360463, "Voidwaker hilt"), "Vet'ion": (15360817, "Voidwaker blade"),
          "Venenatis": (15361982, "Voidwaker gem")}
BONES = {
    "Big bones": ("TOP_DAMAGE", "the player who deals the most damage in the fight", "The player who dealt the most damage."),
    "Bones": ("OTHER_ELIGIBLE", "the players who are eligible for drops but did not deal the most damage",
              "Eligible players who dealt less damage than the top contributor.")}
UNIQUE_TEXT = "At most one unique per kill. Your damage contribution affects your chance to receive it."


def reward_facts(pin, drops):
    name, revision = pin["name"], pin["revisionId"]
    if name not in BOSSES or revision != BOSSES[name][0] or pin["tableHeading"] != "===Unique===":
        raise ValueError("Unreviewed group reward table")
    if ("drop rates for uniques are for each kill, rather than for each player" not in pin["instruction"]
            or "At most one item from this table is dropped per kill" not in pin["instruction"]
            or "depending on their relative contribution" not in pin["instruction"]
            or "split proportionally based on damage done" not in pin["groupQuantities"]):
        raise ValueError("Incomplete group-roll or recipient evidence")
    facts = []
    for line in [*pin["bones"], pin["unique"]]:
        table = [p for _,_,kind,p,_ in templates(line) if kind == "dropsline"]
        if len(table) != 1:
            raise ValueError("Missing exact reward line")
        row, = table
        item, rate = row["name"], row["rarity"]
        if item in BONES:
            role, condition, text = BONES[item]
            if rate != "Always" or condition not in row.get("raritynotes",""):
                raise ValueError("Missing bone recipient condition")
        elif item == BOSSES[name][1] and rate == "1/360":
            role, text = "PER_KILL_UNIQUE", UNIQUE_TEXT
        else:
            raise ValueError("Unreviewed reward item or rate")
        signature = {"quantity":row["quantity"], "dropRate":rate, "notes":""}
        original = dict(signature, itemName=item, monsterName=name)
        if signature["quantity"] != "1" or sum(all(r.get(k) == v for k,v in original.items()) for r in drops) != 1:
            raise ValueError("Missing or duplicate exact original reward")
        slug = name.lower().replace("'", "")
        facts.append({"id":"group-"+slug+"-"+role.lower(), "monsterName":name, "itemName":item,
                      "scope":"ITEM", "appliesTo":"ITEM_DROP", "type":"CONTEXT", "groupRewardRole":role,
                      "dropSignature":signature, "text":text, "evidence":[{"page":name, "revisionId":revision,
                      "sourceUrl":f"https://oldschool.runescape.wiki/w/{name}?oldid={revision}"}]})
    if len(facts) != 3 or {f["itemName"] for f in facts} != {*BONES, BOSSES[name][1]}:
        raise ValueError("Expected both bone recipients and one Voidwaker component")
    return facts


def build():
    pins = json.loads(PINS.read_text(encoding="utf8"))
    drops = json.loads((DATA/"wiki_monster_drops.json").read_text(encoding="utf8"))
    if len(pins) != 3 or {p["name"] for p in pins} != BOSSES.keys():
        raise ValueError("Unexpected group boss coverage")
    return {"schemaVersion":1, "checkedAt":"2026-11-05T02:55:00Z",
            "facts":[f for p in pins for f in reward_facts(p, drops)]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Group reward qualifiers differ from pinned evidence")
    else:
        OUTPUT.write_text(expected, encoding="utf8")
    print("6 bone recipient roles and 3 per-kill group unique rates")
