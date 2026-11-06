"""Reproduce two bounded zenyte recipes from pinned primary text and rendered quantities."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from build_bronze_production import Rows

ROOT = Path(__file__).resolve().parents[1]
PIN = ROOT / "tools/fixtures/zenyte_production.json"
OUTPUT = ROOT / "src/main/resources/zenyte_production.json"
PINS = {
    "Zenyte": (15357771, "dd7a49a27fa9cb49192535ff17c7eaba64cef9a5b81d6664adf6ab1b9902e421", "956c7b02368cfdffc513edb445c2b6c59b4f97ea58734cdc786ebe3226e4e4e9"),
    "Uncut zenyte": (15218231, "c8196cb7c3bc2e0d0cee42d1dc76c702ed9260e7dc03c80cb360b4c1ee031634", "27b09a76e0b48ac8f56ec35132665d852bc01ea7af8880a2ea28aa6c1a1a3c5b"),
    "Zenyte shard": (15209720, "ce750c1eb07aa8231fe8d9064e4f6b5beae042a543eaee8dabeb09d7a394909c", None),
}


def build(pin):
    pages = {p["page"]: p for p in pin["pages"]}
    if len(pages) != len(pin["pages"]) or set(pages) != set(PINS):
        raise ValueError("Unexpected zenyte evidence set")
    for name, (revision, raw_hash, html_hash) in PINS.items():
        p = pages[name]
        if p["revisionId"] != revision or hashlib.sha256(p["wikitext"].encode()).hexdigest() != raw_hash:
            raise ValueError("Changed primary revision/text: " + name)
        if p["sourceUrl"] != "https://oldschool.runescape.wiki/w/" + name.replace(" ", "_") + "?oldid=" + str(revision):
            raise ValueError("Wrong primary URL")
        if html_hash and hashlib.sha256(p["renderedHtml"].encode()).hexdigest() != html_hash:
            raise ValueError("Changed rendered quantities")
    recipes = []
    for name in ["Uncut zenyte", "Zenyte"]:
        page = pages[name]
        fuse = name == "Uncut zenyte"
        raw = page["wikitext"]
        recipe = raw.split("==Creation==", 1)[1].split("{{Recipe", 1)[1].split("\n}}", 1)[0]
        params = dict(re.findall(r"^\|(\w+)\s*=\s*(.+?)\s*$", recipe, re.M))
        if [params[k].strip() for k in ["skill1", "skill1lvl", "skill1boostable", "members", "output1"]] != [
                "Crafting", "70" if fuse else "89", "No" if fuse else "yes", "Yes", name]:
            raise ValueError("Unreviewed recipe requirement")
        if fuse and (params["mat1"] != "Onyx" or params["mat2"] != "Zenyte shard"
                     or params["facilities"] != "Wall of flame"
                     or params["tools"] != "Zombie monkey greegree (small)"
                     or "[[Monkey Madness II]]" not in params["notes"]
                     or "while wearing a [[zombie monkey greegree]] (small or big)" not in raw
                     or "beneath the [[Temple of Marimbo]] on [[Ape Atoll]]" not in raw):
            raise ValueError("Incomplete fusion prerequisites")
        if not fuse and (params["mat1"] != "Uncut zenyte" or params["tools"] != "Chisel"):
            raise ValueError("Changed cutting input/tool")
        rows = Rows()
        rows.feed(page["renderedHtml"])
        def amount(item):
            amounts = [row[2] for row in rows.rows if len(row) == 4 and row[1] == item and row[2].isdigit()]
            if amounts != ["1"]:
                raise ValueError("Unverified quantity: " + item)
            return {"itemName": item, "quantity": 1}
        target = "Zenyte shard" if fuse else "Uncut zenyte"
        requirements = (["Crafting 70 (cannot be boosted).", "Complete Monkey Madness II.",
                         "Wear a zombie monkey greegree (small or big).", "Members only."] if fuse else
                        ["Crafting 89 (boosts allowed).", "Chisel required.", "Members only."])
        recipes.append({
            "id": "production:uncut-zenyte:wall-of-flame" if fuse else "production:zenyte:chisel",
            "output": amount(name), "inputs": [amount(n) for n in (["Zenyte shard", "Onyx"] if fuse else ["Uncut zenyte"])],
            "sourceName": "Fuse an uncut zenyte" if fuse else "Cut an uncut zenyte",
            "locationText": "Wall of flame beneath the Temple of Marimbo, Ape Atoll." if fuse else "",
            "requirements": requirements,
            "evidence": [{k: page[k] for k in ["page", "revisionId", "sourceUrl"]}],
            "relatedItemActions": [{"itemName": target, "label": "Find " + target + " sources"}]
        })
    return {"schemaVersion": 1, "recipes": recipes}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    data = build(json.loads(PIN.read_text(encoding="utf-8")))
    rendered = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf-8") != rendered:
            raise SystemExit("Zenyte recipes differ from reviewed evidence")
    else:
        OUTPUT.write_text(rendered, encoding="utf-8", newline="\n")
    print("2 reviewed zenyte Production methods; existing shard acquisition rows unchanged")
