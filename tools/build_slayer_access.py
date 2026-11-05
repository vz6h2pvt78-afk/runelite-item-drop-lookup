"""Build exact named monster facts from reviewed primary snapshots, entirely offline."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
PINS = ROOT / "tools/fixtures/slayer_access"
OUTPUT = ROOT / "src/main/resources/reviewed_slayer_access.json"

def exact_monster_name(box, title):
    numbered = {key[4:]:value for key, value in box.items() if re.fullmatch(r"name\d+", key)}
    names = list(numbered.values())
    if "name" in box:
        names.append(box["name"])
    else:
        versions = {key[7:] for key in box if re.fullmatch(r"version\d+", key)}
        if not versions or versions != numbered.keys():
            return False
    return bool(names) and all(value.strip().casefold() == title.casefold() for value in names)


def facts_for(pin):
    raw, name, level = pin["wikitext"],pin["title"],pin["slayerLevel"]
    if hashlib.sha256(raw.encode()).hexdigest()!=pin["wikitextSha256"]:
        raise ValueError("Changed article for "+name)
    parsed = list(templates(raw))
    boxes = [p for a,b,t,p,v in parsed if t == "infobox monster"]
    for _,_,kind,wrapper,_ in parsed:
        if kind == "multi infobox":
            for key, value in wrapper.items():
                if re.fullmatch(r"item\d+",key):
                    boxes.extend(p for _,_,t,p,_ in templates(value) if t == "infobox monster")
    if not boxes or not all(exact_monster_name(box, name) for box in boxes):
        raise ValueError("Monster infobox identity unclear for "+name)
    for box in boxes:
        levels = {value.strip() for key, value in box.items() if re.fullmatch(r"slaylvl\d*", key)}
        if levels != {str(level)}:
            raise ValueError("Contradictory or missing Slayer level for " + name)
    plain = re.sub(r"\[\[(?:[^\]|]+\|)?([^\]]+)\]\]",r"\1",raw)
    verb = pin.get("combatVerb", "kill")
    if verb not in ("kill", "harm"):
        raise ValueError("Unreviewed combat verb")
    pattern = rf"requir(?:e|es|ing) (?:a )?(?:level {level} Slayer|Slayer level of {level}|{level} Slayer) (?:in order )?to {verb}\b"
    if not re.search(pattern,plain,re.I):
        raise ValueError("Missing combat-level prose for "+name)
    rev = pin["revisionId"]
    evidence=[{"page":name,"revisionId":rev,"sourceUrl":"https://oldschool.runescape.wiki/w/"+name.replace(" ","_")+"?oldid="+str(rev)}]
    prefix=name.lower().replace(" ","-")
    base={"monsterName":name,"scope":"MONSTER","evidence":evidence}
    result=[dict(base,id=prefix+"-slayer",appliesTo="COMBAT",type="SKILL",skill="Slayer",level=level,text=f"{level} Slayer to {verb}.")]
    for extra in pin["additionalFacts"]:
        if not extra["support"] or not all(fragment in plain for fragment in extra["support"]):
            raise ValueError("Missing evidence for "+name+" "+extra["appliesTo"])
        if (extra["appliesTo"], extra["type"]) not in {("PROTECTION","EQUIPMENT"),("FINISHING","EQUIPMENT"),("COMBAT","CONTEXT"),("TASK_ELIGIBILITY","QUEST")}:
            raise ValueError("Unsupported monster mechanic")
        result.append(dict(base,id=prefix+"-"+extra["appliesTo"].lower().replace("_","-"),appliesTo=extra["appliesTo"],type=extra["type"],text=extra["text"]))
    return result

def build():
    facts=[fact for p in sorted(PINS.glob("*.json")) for fact in facts_for(json.loads(p.read_text(encoding="utf8")))]
    if not facts or len({f["id"] for f in facts})!=len(facts):raise ValueError("Missing or duplicate facts")
    return {"schemaVersion":1,"checkedAt":"2026-11-04T23:00:00Z","facts":facts}

if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--check",action="store_true");args=parser.parse_args()
    data=build();result=json.dumps(data,ensure_ascii=False,indent=2)+"\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf8")!=result:raise SystemExit("Slayer facts differ from reviewed evidence")
    else:OUTPUT.write_text(result,encoding="utf8")
    print(len(data["facts"]),"reviewed Slayer facts")
