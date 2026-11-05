"""Extract the seven independently reviewed armour preparations and token awards."""
import argparse
import hashlib
import json
import re
from pathlib import Path

from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
PINS = ROOT / "tools/fixtures/animator_rewards"
DATA = ROOT / "src/main/resources"
OUT = DATA / "animator_rewards.json"
REVISIONS = {"Bronze":15364174, "Iron":15364176, "Steel":15364175, "Black":15364177,
             "Mithril":15364178, "Adamant":15364179, "Rune":15364180}


def extract(pin, metal, drops):
    title = "Animated " + metal + " Armour"
    raw, revision = pin["wikitext"], pin["revisionId"]
    if (pin["title"] != title or revision != REVISIONS[metal]
            or hashlib.sha256(raw.encode()).hexdigest() != pin["wikitextSha256"]):
        raise ValueError("Unreviewed animator evidence")
    setup = re.findall(r"To summon one, players must use (.*?) on the \[\[magical animator\]\]", raw)
    pieces = [metal.lower() + " " + kind for kind in ("platebody","platelegs","full helm")]
    if len(setup) != 1 or sorted(re.findall(r"\[\[([^\]|]+)\]\]", setup[0])) != sorted(pieces):
        raise ValueError("Ambiguous or mixed suit for " + title)
    rows = [p for _, _, t, p, _ in templates(raw) if t == "dropsline" and p.get("name") == "Warrior guild token"]
    if len(rows) != 1 or rows[0].get("rarity") != "Always" or not rows[0]["quantity"].isdigit():
        raise ValueError("Token drop row changed")
    row = rows[0]
    tiers = re.fullmatch(r"<ref>The amount of tokens dropped is increased to (\d+) after unlocking the "
                         r"\[\[easy Combat Achievements\]\] rewards tier, or (\d+) after unlocking the "
                         r"\[\[medium Combat Achievements\]\] rewards tier\.</ref>", row["raritynotes"])
    if not tiers:
        raise ValueError("The two reward quantities need review")
    candidates = [d for d in drops if d["monsterName"] == title and d["itemName"] == row["name"]]
    if (len(candidates) != 1 or candidates[0]["quantity"] != row["quantity"]
            or candidates[0]["dropRate"] != row["rarity"] or candidates[0].get("notes") != ""):
        raise ValueError("Ambiguous original drop signature for " + title)
    return {"monsterName":title, "itemName":row["name"], "quantity":row["quantity"], "dropRate":"Always", "notes":"",
            "metal":metal, "items":[p.capitalize() for p in pieces], "facility":"Magical animator",
            "easyTokens":int(tiers[1]), "mediumTokens":int(tiers[2]),
            "evidence":[{"page":title,"revisionId":revision,"sourceUrl":"https://oldschool.runescape.wiki/w/"
                         + title.replace(" ","_") + "?oldid=" + str(revision)}]}


def build():
    drops = json.loads((DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
    return {"schemaVersion":1, "rewards":[extract(json.loads((PINS / (metal + ".json")).read_text(encoding="utf8")),metal,drops)
                                         for metal in REVISIONS]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    rendered = json.dumps(build(), ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != rendered:
            raise SystemExit("Animator rewards differ from reviewed evidence")
    else:
        OUT.write_text(rendered, encoding="utf8")
    print("7 reviewed animator token awards")
