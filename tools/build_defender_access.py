"""Bind the Dragon defender prerequisite to one reviewed basement Cyclops row."""
import argparse
import hashlib
import json
from pathlib import Path
from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources"
PIN = ROOT / "tools/fixtures/dragon_defender_access.json"
OUTPUT = DATA / "item_access.json"
LOCATION = "d5a1fab73fd067a98909"
VERSION = "Warriors' Guild Basement"


def build(pin, locations, drops):
    raw = pin["wikitext"]
    if (pin["title"] != "Cyclops" or pin["revisionId"] != 15337742
            or hashlib.sha256(raw.encode()).hexdigest() != "1cf7c488bc075b691c87cc013e473c31e26444c10f4fbd88c083c13e404dbc61"):
        raise ValueError("Unreviewed Cyclops revision")
    earn = "Once a player earns the rune defender, they can enter the basement of the Warriors' Guild and kill the stronger cyclopes found there for a chance at the dragon defender."
    if earn not in raw:
        raise ValueError("Missing earned prerequisite evidence")
    parsed = list(templates(raw))
    candidates = [(a,p) for a,_,kind,p,_ in parsed if kind=="dropsline" and p.get("name")=="Dragon defender"]
    if len(candidates)!=1:
        raise ValueError("Ambiguous Dragon defender occurrence")
    position, row = candidates[0]
    table = [entry for entry in parsed if entry[0]<position and entry[2] in ("dropstablehead","dropstablebottom")][-1]
    if table[2]!="dropstablehead" or table[3].get("dropversion")!=VERSION or (row["quantity"],row["rarity"])!=("1","1/100"):
        raise ValueError("Wrong table variant or signature")
    signature = {"monsterName":"Cyclops","itemName":"Dragon defender","quantity":"1","dropRate":"1/100","notes":""}
    def matches(item):
        return all(item.get(k)==v for k,v in signature.items())
    if len([d for d in drops if matches(d)])!=1:
        raise ValueError("Original Dragon defender source changed")
    bound = [b for b in locations["dropBindings"] if matches(b)]
    if (len(bound)!=1 or bound[0]["locationIds"]!=[LOCATION] or bound[0]["dropVersions"]!=[VERSION]
            or bound[0]["matchMethods"]!=["explicit-variant"]):
        raise ValueError("Dragon defender no longer has the single verified basement binding")
    sites = [l for l in locations["locations"] if l["id"]==LOCATION]
    if len(sites)!=1 or sites[0]["sourcePage"]!="Cyclops" or sites[0]["revisionId"]!=15337742 or sites[0]["location"]!="Warriors' Guild (basement)":
        raise ValueError("Basement location changed")
    return {"schemaVersion":1,"checkedAt":"2026-11-05T01:30:00Z","facts":[{
        "id":"dragon-defender-basement-unlock","monsterName":"Cyclops","scope":"ITEM","itemName":"Dragon defender",
        "appliesTo":"ACCESS","type":"UNLOCK","dropSignature":{"quantity":"1","dropRate":"1/100","notes":"",
            "verifiedLocationId":LOCATION,"dropVersion":VERSION},
        "text":"Earn a Rune defender to enter the Warriors' Guild basement.",
        "evidence":[{"page":"Cyclops","revisionId":15337742,"sourceUrl":"https://oldschool.runescape.wiki/w/Cyclops?oldid=15337742"}]
    }]}


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--check",action="store_true")
    args=parser.parse_args()
    def read(path):
        return json.loads(path.read_text(encoding="utf8"))
    document=json.dumps(build(read(PIN),read(DATA/"monster_locations.json"),read(DATA/"wiki_monster_drops.json")),ensure_ascii=False,indent=2)+"\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf8")!=document:raise SystemExit("Defender access differs from reviewed evidence")
    else:OUTPUT.write_text(document,encoding="utf8")
    print("1 location-bound Dragon defender prerequisite")
