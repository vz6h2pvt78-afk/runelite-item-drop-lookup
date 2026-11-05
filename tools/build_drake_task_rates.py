"""Bind the two explicit on-task Drake rates without changing the underlying drop rows."""
import argparse
import hashlib
import json
from pathlib import Path
from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
PIN = ROOT / "tools/fixtures/slayer_access/Drake.json"
DATA = ROOT / "src/main/resources"
OUT = DATA / "conditional_drop_rates.json"
TOOTH = "Drake's tooth"
CLAW = "Drake's claw"
DEFINITION = '<ref group="d" name="drakeunique">The drop rate is increased to 1/512 while on a drake [[Slayer task]].</ref>'
REUSE = '<ref group="d" name="drakeunique" />'
CITATION = '{{NamedRef|Drake uniques}}'


def extract(raw, drops):
    if raw.count(DEFINITION) != 1 or raw.count('name="drakeunique"') != 2:
        raise ValueError("Unresolved Drake rate reference")
    reviewed = []
    for _, _, kind, row, _ in templates(raw):
        item = row.get("name")
        if kind != "dropsline" or item not in (TOOTH, CLAW):
            continue
        proof = (DEFINITION if item == TOOTH else REUSE) + CITATION
        if (row.get("quantity"), row.get("rarity"), row.get("altrarity"), row.get("raritynotes")) != ("1","1/2560","1/512",proof):
            raise ValueError("Drake row or named task condition changed")
        signature = {"monsterName":"Drake","itemName":item,"quantity":"1","dropRate":"1/2560","notes":""}
        same = [d for d in drops if d["monsterName"]=="Drake" and d["itemName"]==item]
        if len(same)!=1 or any(same[0].get(k)!=v for k,v in signature.items()):
            raise ValueError("Missing or ambiguous Drake source signature")
        reviewed.append(dict(signature, taskName="Drake", onTaskRate="1/512", evidence=[{
            "page":"Drake","revisionId":15371749,"sourceUrl":"https://oldschool.runescape.wiki/w/Drake?oldid=15371749"}]))
    if len(reviewed)!=2 or {r["itemName"] for r in reviewed}!={TOOTH,CLAW}:
        raise ValueError("Both reviewed Drake drops are required")
    return {"schemaVersion":1, "rates":reviewed}


def build():
    pin=json.loads(PIN.read_text(encoding="utf8"))
    if pin["title"]!="Drake" or pin["revisionId"]!=15371749 or hashlib.sha256(pin["wikitext"].encode()).hexdigest()!=pin["wikitextSha256"]:
        raise ValueError("Wrong Drake evidence")
    return extract(pin["wikitext"],json.loads((DATA/"wiki_monster_drops.json").read_text(encoding="utf8")))


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--check",action="store_true")
    args=parser.parse_args()
    document=json.dumps(build(),indent=2,ensure_ascii=False)+"\n"
    if args.check:
        if OUT.read_text(encoding="utf8")!=document:raise SystemExit("Drake rate resource differs from its source")
    else:OUT.write_text(document,encoding="utf8")
    print("2 exact Drake task-rate enrichments")
