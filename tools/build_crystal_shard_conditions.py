"""Bind nine direct Crystal shard location qualifiers from pinned own-source notes."""
import argparse
import hashlib
import json
from pathlib import Path

from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources"
PINS = ROOT / "tools/fixtures/crystal_shard_conditions"
OUT = DATA / "crystal_shard_conditions.json"
REVISIONS = {
    "Dark beast": 15350345, "Greater Nechryael": 15341801,
    "Kurask": 15386817, "Mutated Bloodveld": 15338397, "Waterfiend": 15385712,
    "Insatiable mutated Bloodveld": 15357549, "King kurask": 15373844,
    "Nechryarch": 15373905, "Night beast": 15373912,
}
GUARANTEED = {"Insatiable mutated Bloodveld", "King kurask", "Nechryarch", "Night beast"}
WITHIN = "Crystal shards are only dropped by those found within the [[Iorwerth Dungeon]]."
NOTES = {name: "<ref group=d>" + WITHIN + "</ref>"
         for name in ("Greater Nechryael", "Kurask", "Mutated Bloodveld", "Waterfiend")}
NOTES.update({name: "{{Refn|" + WITHIN + "|group=d}}"
              for name in ("Insatiable mutated Bloodveld", "King kurask", "Night beast")})
NOTES["Dark beast"] = ("<ref group=d>Crystal shards are only dropped when they are killed while the player is in "
                       "[[Prifddinas]] or the [[Iorwerth Dungeon]].</ref>")
NOTES["Nechryarch"] = "{{Refn|group=d|Crystal shards are only dropped by those found in the [[Iorwerth Dungeon]].}}"
TEXT = "Only drops in Iorwerth Dungeon."
DARK_TEXT = "Only drops while in Prifddinas or Iorwerth Dungeon."


def extract(pin, drops):
    name = pin["title"]
    raw = pin["wikitext"]
    if (name not in REVISIONS or pin["revisionId"] != REVISIONS[name]
            or hashlib.sha256(raw.encode()).hexdigest() != pin["wikitextSha256"]):
        raise ValueError("Changed Crystal shard primary")
    quantity, rate = ("6-10", "Always") if name in GUARANTEED else ("3-5", "1/24")
    matches = [fields for _, _, kind, fields, _ in templates(raw)
               if kind == "dropsline" and fields.get("name") == "Crystal shard"]
    if len(matches) != 1:
        raise ValueError("Missing or ambiguous direct Crystal shard row")
    row = matches[0]
    if (row.get("quantity"), row.get("rarity"), row.get("raritynotes")) != (quantity, rate, NOTES[name]):
        raise ValueError("Changed Crystal shard signature or location qualifier")
    if set(row) - {"name", "quantity", "rarity", "raritynotes", "gemw", "leagueregion"}:
        raise ValueError("Unreviewed Crystal shard variant or roll metadata")
    signature = {"monsterName": name, "itemName": "Crystal shard", "quantity": quantity, "dropRate": rate, "notes": ""}
    recorded = [drop for drop in drops if drop["monsterName"] == name and drop["itemName"] == "Crystal shard"]
    if len(recorded) != 1 or any(recorded[0].get(key) != value for key, value in signature.items()):
        raise ValueError("Changed or ambiguous recorded Crystal shard signature")
    return {
        "id": "crystal-shard-" + name.lower().replace(" ", "-"), "monsterName": name, "itemName": "Crystal shard",
        "scope": "ITEM", "appliesTo": "ITEM_DROP", "type": "CONTEXT",
        "dropSignature": {key: signature[key] for key in ("quantity", "dropRate", "notes")},
        "text": DARK_TEXT if name == "Dark beast" else TEXT,
        "evidence": [{"page": name, "revisionId": REVISIONS[name],
            "sourceUrl": "https://oldschool.runescape.wiki/w/" + name.replace(" ", "_") + "?oldid=" + str(REVISIONS[name])}],
    }


def build():
    drops = json.loads((DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
    facts = [extract(json.loads((PINS / (name.replace(" ", "_") + ".json")).read_text(encoding="utf8")), drops)
             for name in REVISIONS]
    return {"schemaVersion": 1, "checkedAt": "2026-11-05T13:56:16Z", "facts": facts}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Crystal shard conditions differ from their primaries")
    else:
        OUT.write_text(expected, encoding="utf8")
    print("9exact Crystal shard location conditions; 5ordinary and4guaranteed signatures")
