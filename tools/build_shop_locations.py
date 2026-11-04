"""Bind exact stock rows to the shop version IDs explicitly listed on their own Wiki table."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from collections import Counter
from build_monster_locations import clean, templates

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources"
PINS = ROOT / "tools/fixtures/shop_sites"
OUTPUT = DATA / "shop_locations.json"
SHOPS = {"Forestry Shop":(15374369,4,"Stock"), "Slayer Rewards":(15373095,10,"Buy")}

def extract(pin, bundled):
    name, raw = pin["title"], pin["wikitext"]
    revision, version_count, section = SHOPS[name]
    if revision != pin["revisionId"] or hashlib.sha256(raw.encode()).hexdigest() != pin["wikitextSha256"]:
        raise ValueError("Shop revision changed")
    parsed = list(templates(raw))
    boxes = [p for _,_,t,p,_ in parsed if t == "infobox shop"]
    if len(boxes) != 1 or boxes[0].get("name") != name:
        raise ValueError("Ambiguous shop")
    box = boxes[0]
    sites = []
    for n in range(1,version_count+1):
        version = box["shopversion"+str(n)]
        if version != "("+str(n)+")": raise ValueError("Unknown site version")
        place, branch = clean(box["location"+str(n)]), clean(box["version"+str(n)])
        members = box.get("members"+str(n),box.get("members"))
        if members not in ("Yes","No"): raise ValueError("Unknown site membership")
        sites.append({"version":version,"place":place,"branch":branch,"membersShop":members=="Yes"})
    heads = [(a,b,p) for a,b,t,p,_ in parsed if t == "storetablehead"]
    if len(heads) != 1: raise ValueError("Unreviewed shop table")
    start, end, head = heads[0]
    headings = re.findall(r"^=+\s*(.*?)\s*=+\s*$",raw[:start],re.M)
    if not headings or headings[-1] != section or head["shopversion"].split(",") != [s["version"] for s in sites]:
        raise ValueError("Table does not bind the reviewed shop versions")
    close = raw.lower().index("{{storetablebottom",end)
    stock = {}
    for a,_,kind,p,_ in parsed:
        if not end <= a < close or kind != "storeline": continue
        if p.get("shopversion") or p.get("version"): continue
        item = clean(p.get("displayname",p["name"]))
        if item in stock: raise ValueError("Ambiguous stock identity")
        member = re.fullmatch(r"\{\{Members\|(yes|no)\}\}",p.get("column4",""),re.I) if name == "Forestry Shop" else None
        stock[item] = member[1].lower()=="yes" if member else None
    rows = [r for r in bundled if r.get("sourceName")==name and r.get("sourceType")=="Reward Shop"]
    frequency = Counter(r["itemName"] for r in rows)
    offers = []
    for r in rows:
        item=r["itemName"]
        if item not in stock or r.get("location") or frequency[item]!=1: continue
        offers.append({"itemName":item,"cost":r["cost"],"notes":r["notes"],"itemMembers":stock[item]})
    return {"sourceName":name,"sourceType":"Reward Shop","sites":sites,"offers":offers,
            "evidence":[{"page":name,"revisionId":revision,"sourceUrl":"https://oldschool.runescape.wiki/w/"+name.replace(" ","_")+"?oldid="+str(revision)}]}

def build():
    bundled=json.loads((DATA/"wiki_shop_sources.json").read_text(encoding="utf8"))
    return {"schemaVersion":1,"shops":[extract(json.loads(p.read_text(encoding="utf8")),bundled) for p in sorted(PINS.glob("*.json"))]}

if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--check",action="store_true");args=parser.parse_args()
    result=json.dumps(build(),ensure_ascii=False,indent=2)+"\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf8")!=result: raise SystemExit("Shop location resource differs from its evidence")
    else: OUTPUT.write_text(result,encoding="utf8")
