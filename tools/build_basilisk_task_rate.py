"""Bind one explicit on-task Basilisk Knight jaw rate to its exact recorded source."""
import argparse
import hashlib
import json
from pathlib import Path
from build_monster_locations import templates

ROOT=Path(__file__).resolve().parents[1]
PIN=ROOT/"tools/fixtures/slayer_access/Basilisk_Knight.json"
DATA=ROOT/"src/main/resources"
OUT=DATA/"basilisk_jaw_task_rate.json"
NOTE='<ref group=d>The drop rate of a basilisk jaw is increased to 1/1,000 while on a basilisk [[Slayer task]].</ref>{{NamedRef|Basilisk Knight}}'


def extract(raw,drops):
    rows=[p for _,_,kind,p,_ in templates(raw) if kind=="dropsline" and p.get("name")=="Basilisk jaw"]
    if len(rows)!=1:
        raise ValueError("One exact Basilisk jaw row is required")
    row=rows[0]
    if (row.get("quantity"),row.get("rarity"),row.get("altrarity"),row.get("raritynotes"))!=("1","1/5000","1/1000",NOTE):
        raise ValueError("The exact jaw rate or task condition changed")
    if "(1/5000) when not on a Basilisk Slayer task" not in raw or "while on task grants a drop rate five times higher (1/1000)" not in raw:
        raise ValueError("Missing explicit off-task and on-task prose")
    signature={"monsterName":"Basilisk Knight","itemName":"Basilisk jaw","quantity":"1","dropRate":"1/5000","notes":""}
    matches=[d for d in drops if d["monsterName"]=="Basilisk Knight" and d["itemName"]=="Basilisk jaw"]
    if len(matches)!=1 or any(matches[0].get(k)!=v for k,v in signature.items()):
        raise ValueError("Missing or ambiguous Basilisk Knight source")
    rate=dict(signature,taskName="Basilisk",onTaskRate="1/1000",evidence=[{
        "page":"Basilisk Knight","revisionId":15340714,
        "sourceUrl":"https://oldschool.runescape.wiki/w/Basilisk_Knight?oldid=15340714"}])
    return {"schemaVersion":1,"rates":[rate]}


def build():
    pin=json.loads(PIN.read_text(encoding="utf8"))
    if pin["title"]!="Basilisk Knight" or pin["revisionId"]!=15340714 or hashlib.sha256(pin["wikitext"].encode()).hexdigest()!=pin["wikitextSha256"]:
        raise ValueError("Wrong Basilisk Knight primary")
    return extract(pin["wikitext"],json.loads((DATA/"wiki_monster_drops.json").read_text(encoding="utf8")))


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--check",action="store_true")
    args=parser.parse_args()
    expected=json.dumps(build(),indent=2,ensure_ascii=False)+"\n"
    if args.check:
        if OUT.read_text(encoding="utf8")!=expected:raise SystemExit("Basilisk task rate differs from primary evidence")
    else:
        OUT.write_text(expected,encoding="utf8")
    print("1 exact Basilisk Knight task-rate enrichment")
