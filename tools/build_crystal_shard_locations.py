"""Link five direct shard qualifiers to existing same-revision Iorwerth location records."""
import argparse
import json

import build_crystal_shard_conditions as conditions
from build_monster_locations import templates

DATA = conditions.DATA
OUT = DATA / "crystal_shard_locations.json"
TARGETS = {
    "Dark beast": ("5f9a03c81abe69928796", 182, ""),
    "Greater Nechryael": ("51b7c39b8d085595856b", 200, "Regular"),
    "Kurask": ("7d37e4898b37137e6b86", 106, ""),
    "Mutated Bloodveld": ("5d02c785be1ea0f0d404", 123, ""),
    "Waterfiend": ("6c6ac675635c5903fbfe", 115, ""),
}


def normalize(value):
    return " ".join(value.lower().replace("_", " ").replace("’", "'").split())


def extract(pin, drops, locations):
    fact = conditions.extract(pin, drops)
    name = fact["monsterName"]
    location_id, level, version = TARGETS[name]
    revision = conditions.REVISIONS[name]
    records = [row for row in locations["locations"] if row.get("id") == location_id]
    if len(records) != 1:
        raise ValueError("Missing or ambiguous stored location ID")
    place = records[0]
    url = "https://oldschool.runescape.wiki/w/" + name.replace(" ", "_")
    expected = {
        "monsterName": name, "sourcePage": name, "npcName": name,
        "revisionId": revision, "location": "Iorwerth Dungeon",
        "levels": [level], "levelsText": str(level), "members": True,
        "available": True, "wilderness": "NO",
        "wildernessEvidence": "https://oldschool.runescape.wiki/w/Moss_Giant_(Iorwerth_Dungeon)",
        "dropVersions": [version] if version else [], "version": "", "sourceUrl": url,
    }
    if any(place.get(key) != value for key, value in expected.items()):
        raise ValueError("Changed stored Iorwerth location metadata")
    parsed = list(templates(pin["wikitext"]))
    loclines = [fields for _, _, kind, fields, _ in parsed
                if kind == "locline" and fields.get("location") == "[[Iorwerth Dungeon]]"]
    if len(loclines) != 1 or any(loclines[0].get(key, "") != value for key, value in {
            "name": name, "levels": str(level), "members": "Yes", "dropversion": version}.items()):
        raise ValueError("Changed primary Iorwerth LocLine")
    table_version = None
    shard_versions = []
    for _, _, kind, fields, _ in parsed:
        if kind == "dropstablehead":
            table_version = fields.get("dropversion", "")
        elif kind == "dropstablebottom":
            table_version = None
        elif kind == "dropsline" and fields.get("name") == "Crystal shard":
            shard_versions.append(table_version)
    if shard_versions != [version]:
        raise ValueError("Incompatible direct shard table version")
    signature = {"monsterName": name, "itemName": "Crystal shard", **fact["dropSignature"]}
    if any(all(normalize(row.get(key, "")) == normalize(value) for key, value in signature.items())
           for row in locations["dropBindings"]):
        raise ValueError("Existing binding takes precedence; association needs review")
    return {**signature, "locationId": location_id, "locationName": "Iorwerth Dungeon",
            "dropVersion": version, "revisionId": revision, "sourceUrl": url + "?oldid=" + str(revision)}


def build():
    drops = json.loads((DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
    locations = json.loads((DATA / "monster_locations.json").read_text(encoding="utf8"))
    bindings = [extract(json.loads((conditions.PINS / (name.replace(" ", "_") + ".json")).read_text(encoding="utf8")),
                        drops, locations) for name in TARGETS]
    return {"schemaVersion": 1, "bindings": bindings}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("Reviewed shard locations differ from own-page and stored-location evidence")
    else:
        OUT.write_text(expected, encoding="utf8")
    print("5 exact reviewed item-location associations; core records and legacy bindings preserved")
