"""Review four exact Dragon pickaxe rewards at their existing Wilderness fighting lairs."""
import argparse
import json
from pathlib import Path
from build_voidwaker_lairs import reviewed

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT/"src/main/resources"
PINS = ROOT/"tools/fixtures/encounter_lairs/pickaxe.json"
OUTPUT = DATA/"pickaxe_lairs.json"


def build():
    pins=json.loads(PINS.read_text(encoding="utf8"))
    locations=json.loads((DATA/"monster_locations.json").read_text(encoding="utf8"))
    drops=json.loads((DATA/"wiki_monster_drops.json").read_text(encoding="utf8"))
    expected={"Artio":15358758,"Callisto":15360463,"Vet'ion":15360817,"Venenatis":15361982}
    if len(pins)!=4 or {p["name"]:p["revisionId"] for p in pins}!=expected:
        raise ValueError("Unreviewed boss primary")
    records=[]
    for pin in pins:
        adapted={"monsterName":pin["name"],"location":pin["lair"],"locationId":pin["locationId"],
                 "revisionId":pin["revisionId"],"primaryEncounterLead":pin["encounterLead"],
                 "component":"Dragon pickaxe","componentLine":pin["row"]}
        record=reviewed(adapted,locations,drops)
        if record["dropRate"]!=("1/358" if pin["name"]=="Artio" else "1/256"):
            raise ValueError("Changed Dragon pickaxe rate")
        records.append(record)
    return {"schemaVersion":1,"encounters":records}


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--check",action="store_true")
    args=parser.parse_args()
    expected=json.dumps(build(),indent=2,ensure_ascii=False)+"\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf8")!=expected:raise SystemExit("Pickaxe lairs differ from primary evidence")
    else:
        OUTPUT.write_text(expected,encoding="utf8")
    print("4 exact Dragon pickaxe encounter warnings")
