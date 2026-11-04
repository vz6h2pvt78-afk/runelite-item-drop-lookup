"""Generate finite ordinary impling events from the reviewed offline Wiki snapshots."""
import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tools/fixtures/implings"
OUTPUT = ROOT / "src/main/resources/impling_loot.json"
GENERAL = {"page": "Impling", "revisionId": 15360701, "sourceUrl": "https://oldschool.runescape.wiki/w/Impling?oldid=15360701"}

def build_event(pin):
    name, revision, raw = pin["title"], pin["revisionId"], pin["wikitext"]
    if hashlib.sha256(raw.encode()).hexdigest() != pin["wikitextSha256"]:
        raise ValueError("Changed Wiki snapshot for " + name)
    if not re.fullmatch(r"[A-Z][a-z]+ impling", name) or revision <= 0:
        raise ValueError("Invalid impling identity")
    tier = name.split()[0]
    section = raw.split("==Loot==", 1)[1].split("===Tertiary===", 1)[0]
    if tier == "Gourmet":
        section = section.split("===Standard loot===", 1)[1]
    table = section.split("{{DropsTableHead", 1)[1].split("{{DropsTableBottom}}", 1)[0]
    source = {"page": name, "revisionId": revision, "sourceUrl": "https://oldschool.runescape.wiki/w/" + name.replace(" ", "_") + "?oldid=" + str(revision)}
    outcomes = []
    for line in table.splitlines()[1:]:
        if not line.strip():
            continue
        match = re.fullmatch(r"\{\{DropsLineSkill\|([^{}]+)\}\}", line.strip())
        if not match:
            raise ValueError("Unreviewed table syntax in " + name + ": " + line)
        parts = dict(field.split("=", 1) for field in match[1].split("|"))
        if set(parts) - {"name", "skill", "rarity", "quantity", "gemw"} or parts["skill"] != "Hunter":
            raise ValueError("Unreviewed condition in " + line)
        item = parts["name"]
        amount = re.fullmatch(r"(\d+)(?:-(\d+))?( \(noted\))?", parts.get("quantity", "0" if item == "Nothing" else ""))
        if not amount or not re.fullmatch(r"[1-9]\d*/[1-9]\d*", parts["rarity"]):
            raise ValueError("Unreviewed quantity or chance in " + line)
        low, high = int(amount[1]), int(amount[2] or amount[1])
        if high < low or (low == 0) != (item == "Nothing"):
            raise ValueError("Unexpected output amount")
        outcome_id = tier.lower() + "-" + re.sub(r"\s+", "-", item.lower()) + "-" + str(len(outcomes)+1)
        outcomes.append({"id": outcome_id, "itemName": item, "quantityMin": low, "quantityMax": high,
                         "noted": bool(amount[3]), "listedChance": parts["rarity"]})
    if len(outcomes) != pin["ordinaryRows"]:
        raise ValueError("Incomplete ordinary table for " + name)
    jar = name + " jar"
    area = "Prifddinas" if tier == "Crystal" else "Gielinor or Puro-Puro"
    direct = "Prifddinas" if tier == "Crystal" else "Outside Puro-Puro"
    options = [{"kind": k, "hunterLevel": level, "skill": "Hunter", "scope": "CAPTURE", "equipment": label,
                "equipmentAlternatives": alternatives, "evidence": [source, GENERAL]}
               for k,level,label,alternatives in [
                   ("NET",pin["netHunter"],"Butterfly net or magic butterfly net",["BUTTERFLY_NET","MAGIC_BUTTERFLY_NET"]),
                   ("BAREHANDED",pin["bareHunter"],"Bare hands",[])]]
    routes = [
        {"id":"open-obtained-jar", "kind":"OPEN_EXISTING", "inputItem":jar, "steps":["Obtain a filled "+jar, "Open the jar"],
         "captureOptions":[], "evidence":[source, GENERAL]},
        {"id":"catch-into-jar", "kind":"CATCH_INTO_CONTAINER", "area":area, "jarState":"EMPTY_JAR_PRESENT",
         "steps":["Catch the impling in an empty jar", "Open the filled jar"], "captureOptions":options, "evidence":[source,GENERAL]},
        {"id":"catch-and-loot", "kind":"CATCH_AND_LOOT", "area":direct, "jarState":"EMPTY_JAR_ABSENT",
         "steps":["Catch the impling without an empty jar", "Receive loot immediately"], "captureOptions":options, "evidence":[source,GENERAL]}]
    event_id = tier.lower()+"-impling-ordinary-loot"
    context = "Listed on the standard table after the Grubby key pre-roll." if tier == "Gourmet" else "Listed on the ordinary loot table."
    event = {"id":event_id, "sourceName":name+" loot", "containerName":jar, "probabilityBasis":"WIKI_LISTED_TABLE",
             "probabilityContext":context, "quantityBasis":"SUCCESSFUL_OUTCOME", "otherRewardsNotice":"Additional clue rewards and jar returns are separate.",
             "outcomes":outcomes, "routes":routes, "evidence":[source,GENERAL]}
    if tier == "Crystal":
        prif = json.loads((FIXTURES.parent/"prifddinas_access.json").read_text(encoding="utf-8"))
        if prif["revisionId"] != 15374558 or "require completion of [[Song of the Elves]]" not in prif["entryStatement"]:
            raise ValueError("Missing reviewed city access fact")
        event["captureAccess"] = {"scope":"LOCATION_ENTRY","area":"Prifddinas","quest":"Song of the Elves",
                                  "evidence":[{"page":"Prifddinas","revisionId":15374558,"sourceUrl":"https://oldschool.runescape.wiki/w/Prifddinas?oldid=15374558"}]}
    coverage = {"eventId":event_id, "includedOutcomeIds":[o["id"] for o in outcomes if o["itemName"] != "Nothing"]}
    return {"event": event, "coverage": coverage}

def build():
    pin = json.loads((FIXTURES.parent/"lucky_impling.json").read_text(encoding="utf-8"))
    raw = pin["wikitext"]
    if pin["revisionId"] != 15297402 or hashlib.sha256(raw.encode()).hexdigest() != pin["wikitextSha256"]:
        raise ValueError("Unexpected Lucky impling evidence")
    tiers = re.findall(r"\{\{DropsLineSkill\|name=Roll from (\w+) clue table\|quantity=1\|rarity=1/5\|", raw)
    if tiers != ["easy","medium","hard","elite","master"] or "89 [[Hunter]]" not in raw or "99 Hunter to capture it barehanded" not in raw:
        raise ValueError("Unexpected Lucky capture or reward tiers")
    lucky = {"id":"lucky-impling-jar-capture","sourceName":"Lucky impling","output":"Lucky impling jar","quantity":1,
             "netHunter":89,"bareHunter":99,"emptyJar":True,"areas":["Gielinor","Puro-Puro"],
             "opening":{"rolls":1,"tiers":tiers,"chancePerTier":"1/5","excluded":["Bloodhound","Clue scroll (master)"]},
             "evidence":[{"page":"Lucky impling","revisionId":15297402,"sourceUrl":"https://oldschool.runescape.wiki/w/Lucky_impling?oldid=15297402"},GENERAL]}
    return {"schemaVersion":1, "tables":[build_event(json.loads(p.read_text(encoding="utf-8"))) for p in sorted(FIXTURES.glob("*.json"))],
            "luckyCapture":lucky}

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    data = build()
    result = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf-8") != result:
            raise SystemExit("Bundled impling tables differ from the pinned inputs")
    else:
        OUTPUT.write_text(result, encoding="utf-8")
    print(len(data["tables"]), "reviewed impling tables")
