"""Review exact League source identities without removing raw acquisition records."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
PINS = ROOT / "tools/fixtures/echo_sources"
OUT = ROOT / "src/main/resources/league_echo_exclusions.json"
REVISIONS = {
    "Amoxliatl (Echo)": 15210490,
    "Cerberus (Echo)": 15286453,
    "Corrupted Hunllef (Echo)": 15371262,
    "Dagannoth Kings (Echo)": 15289940,
    "Dagannoth Prime (Echo)": 15289941,
    "Dagannoth Rex (Echo)": 15289943,
    "Dagannoth Supreme (Echo)": 15289942,
    "Dusk (Echo)": 15289933,
    "Grotesque Guardians (Echo)": 15356130,
    "Hespori (Echo)": 15365104,
    "Kalphite Queen (Echo)": 15286450,
    "King Black Dragon (Echo)": 15286460,
    "Sol Heredit (Echo)": 15349115,
    "Thermonuclear smoke devil (Echo)": 15286455
}
ACTIVITIES = {"Dagannoth Kings (Echo)": "Echo Dagannoth Kings",
              "Grotesque Guardians (Echo)": "Echo Grotesque Guardians"}


def exclusion(pin):
    title = pin["title"]
    if (title not in REVISIONS or pin["revisionId"] != REVISIONS[title]
            or hashlib.sha256(pin["wikitext"].encode()).hexdigest() != pin["wikitextSha256"]):
        raise ValueError("Unreviewed Echo primary")
    league = "Raging Echoes League" if title == "Sol Heredit (Echo)" else "Demonic Pacts League"
    if (pin["league"] != league or pin["support"] not in pin["wikitext"]
            or "accessible during" not in pin["support"] or "[[" + league not in pin["support"]):
        raise ValueError("Missing explicit League encounter evidence")
    kind = "infobox activity" if title in ACTIVITIES else "infobox monster"
    boxes = [p for _, _, name, p, _ in templates(pin["wikitext"]) if name == kind]
    if len(boxes) != 1 or boxes[0].get("name") != ACTIVITIES.get(title, title):
        raise ValueError("Unclear exact encounter identity")
    return {"monsterName": title, "revisionId": pin["revisionId"], "league": league,
            "sourceUrl": "https://oldschool.runescape.wiki/w/" + title.replace(" ", "_") + "?oldid=" + str(pin["revisionId"])}


def build():
    sources = [exclusion(json.loads(file.read_text(encoding="utf8"))) for file in sorted(PINS.glob("*.json"))]
    if len(sources) != 14 or {row["monsterName"] for row in sources} != REVISIONS.keys():
        raise ValueError("Expected the fourteen reviewed source identities")
    raw = json.loads((ROOT / "src/main/resources/wiki_monster_drops.json").read_text(encoding="utf8"))
    rows = [row for row in raw if row["monsterName"] in REVISIONS]
    counts = Counter(row["monsterName"] for row in rows)
    identities = {row["itemName"] for row in rows}
    if len(rows) != 20 or len(identities) != 15 or counts.keys() != REVISIONS.keys():
        raise ValueError("Re-review the exact raw rows and derived item identities")
    return {"schemaVersion": 1, "sources": sources}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != expected:
            raise SystemExit("League exclusions differ from reviewed primaries")
    else:
        OUT.write_text(expected, encoding="utf8")
    print("14 reviewed League sources, 20 retained raw rows and 15 derived item identities")
