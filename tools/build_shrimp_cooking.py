"""Build the reviewed cooking-range method from the two saved Wiki articles."""
import argparse
import hashlib
import json
from pathlib import Path

from build_bronze_production import Rows
from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
PIN = ROOT / "tools/fixtures/shrimps_cooking.json"
RAW_PIN = ROOT / "tools/fixtures/fishing_sources.json"
OUTPUT = ROOT / "src/main/resources/cooking_recipes.json"


def evidence(title, revision):
    return {"page": title, "revisionId": revision,
            "sourceUrl": "https://oldschool.runescape.wiki/w/" + title.replace(" ", "_") + "?oldid=" + str(revision)}


def build(cooked, raw):
    article = cooked["wikitext"]
    if (cooked["title"] != "Shrimps" or cooked["revisionId"] != 15183319
            or hashlib.sha256(article.encode()).hexdigest() != "4a5901ebee7a6f1cc558bc1aa22fbacecaa43f0f6f60a4e2fc7a09cdb0343998"):
        raise ValueError("Review the cooked Shrimps article revision")
    if (raw["title"] != "Raw shrimps" or raw["revisionId"] != 15331265
            or hashlib.sha256(raw["wikitext"].encode()).hexdigest() != "b8639910bf62329c3ece329ffb22714f591af309996f33b4be612aa5a41e13e2"
            or "cooked into [[shrimps]] at level 1 [[Cooking]]" not in raw["wikitext"]):
        raise ValueError("Raw shrimps does not corroborate the successful conversion")

    section = article.split("===Facility===", 1)[1].split("===Cooking chance===", 1)[0]
    recipes = [params for _, _, kind, params, _ in templates(section) if kind == "recipe"]
    required = {"skill1": "Cooking", "skill1lvl": "1", "members": "No", "facilities": "Cooking range",
                "mat1": "Raw shrimps", "output1": "Shrimps"}
    if len(recipes) != 1 or any(recipes[0].get(key) != value for key, value in required.items()):
        raise ValueError("Cooking-range recipe changed")
    if ("when successful" not in article or "chance to fail, resulting in [[burnt shrimp]]" not in article
            or "on a [[fire]] or [[cooking range]]" not in article):
        raise ValueError("Missing cooking outcome or alternate facility evidence")

    rendered = cooked["facilityRenderedHtml"]
    if hashlib.sha256(rendered.encode()).hexdigest() != cooked["facilityHtmlSha256"]:
        raise ValueError("Changed saved recipe rendering")
    parsed = Rows()
    parsed.feed(rendered)
    materials = [row[1:3] for row in parsed.rows if len(row) == 4 and row[0] == ""]
    if (materials != [["Raw shrimps", "1"], ["Shrimps", "1"]] or ["Tools", "None", "Facilities", "Cooking range"] not in parsed.rows
            or not any(row[:2] == ["Cooking", "1"] for row in parsed.rows)):
        raise ValueError("Rendered recipe does not establish input, output, tool and facility")

    return {"schemaVersion": 1, "recipes": [{
        "id": "production:shrimps:cooking-range", "action": "Cook", "facility": "Cooking range",
        "inputs": [{"itemName": "Raw shrimps", "quantity": 1}], "output": {"itemName": "Shrimps", "quantity": 1},
        "outputBasis": "ON_SUCCESS", "failure": "BURN", "skill": "Cooking", "level": 1, "membersOnly": False,
        "tools": [], "alternateFacilityNote": "Raw shrimps can also be cooked on a fire.",
        "evidence": [evidence("Shrimps", 15183319), evidence("Raw shrimps", 15331265)]
    }]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    cooked = json.loads(PIN.read_text(encoding="utf8"))
    raw = json.loads(RAW_PIN.read_text(encoding="utf8"))["sources"]["Raw shrimps"]
    document = json.dumps(build(cooked, raw), ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf8") != document:
            raise SystemExit("Cooking resource differs from reviewed evidence")
    else:
        OUTPUT.write_text(document, encoding="utf8")
    print("1 reviewed cooking-range recipe with conditional output")
