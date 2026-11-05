"""Generate a closed reviewed historical-source list from pinned explicit prose."""
import argparse
import hashlib
import json
from pathlib import Path
from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
PINS = ROOT / "tools/fixtures/historical_sources"
OUT = ROOT / "src/main/resources/historical_source_exclusions.json"
REVISIONS = {"Bloodthirst rockslug": 15204801}
SUPPORT = {
    "Bloodthirst rockslug": "The '''bloodthirst'''{{Sic}} '''rockslug''' was an unused variant of the [[giant rockslug]], the [[superior]] variant of the [[rockslug]], that would have appeared after unlocking the [[Bloodthirsty]] [[Trailblazer Reloaded League/Relics|relic]] in [[Trailblazer Reloaded League]]. Because rockslugs could not be assigned as a Slayer task during the league, it was never possible to encounter a bloodthirst rockslug."
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
    if len(boxes) != 1 or boxes[0].get("name") != INFOBOX_NAMES[title]:
        raise ValueError("Unclear exact historical encounter identity")
    return {"monsterName": title, "revisionId": pin["revisionId"],
            "sourceUrl": "https://oldschool.runescape.wiki/w/" + title.replace(" ", "_") + "?oldid=" + str(pin["revisionId"])}


def build():
    sources = [exclusion(json.loads(file.read_text(encoding="utf8"))) for file in sorted(PINS.glob("*.json"))]
    if len(sources) != 1 or {row["monsterName"] for row in sources} != REVISIONS.keys():
        raise ValueError("Expected the single reviewed historical source")
    raw = json.loads((ROOT / "src/main/resources/wiki_monster_drops.json").read_text(encoding="utf8"))
    rows = [row for row in raw if row["monsterName"] in REVISIONS]
    if len(rows) != 13 or len({row["itemName"] for row in rows}) != 12:
        raise ValueError("Re-review the thirteen raw rows and twelve item identities")
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
    print("1 reviewed historical source,13 retained raw rows and12 derived item identities")
