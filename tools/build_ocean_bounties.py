"""Build five assignment levels and eight signature-qualified item restrictions offline."""
import argparse
import hashlib
import json
from pathlib import Path
import re

from build_monster_locations import templates
from build_shark_bounties import DEFINITION, REFERENCE

ROOT = Path(__file__).resolve().parents[1]
PINS = ROOT / "tools/fixtures/ocean_bounties"
DATA = ROOT / "src/main/resources"
OUT = DATA / "ocean_bounty_access.json"
REVIEWED = {
    "Eagle ray": (15228801, 30, ("skin", "fin")),
    "Butterfly ray": (15372299, 40, ("skin", "fin")),
    "Stingray": (15349228, 45, ("skin", "fin")),
    "Narwhal": (15305362, 62, ("blubber", "tusk")),
    "Orca": (15358302, 80, ()),
}


def extract(pin, drops):
    title, raw = pin["title"], pin["wikitext"]
    revision, level, suffixes = REVIEWED[title]
    if pin["revisionId"] != revision or hashlib.sha256(raw.encode()).hexdigest() != pin["wikitextSha256"]:
        raise ValueError("Unreviewed ocean creature revision")
    task = rf"(?:may be assigned to kill them as a|may assigned as a|can be assigned as) \[\[bounty tasks?\]\] starting at (?:level )?{level} \[\[Sailing\]\]"
    if not re.search(task, raw):
        raise ValueError("Missing assignment-level prose for " + title)
    key = title.lower().replace(" ", "-")
    base = {"monsterName":title, "evidence":[{"page":title, "revisionId":revision,
            "sourceUrl":"https://oldschool.runescape.wiki/w/" + title.replace(" ", "_") + "?oldid=" + str(revision)}]}
    facts = [dict(base, id=key+"-bounty-eligibility", scope="MONSTER", appliesTo="TASK_ELIGIBILITY", type="SKILL",
                  skill="Sailing", level=level, text=f"{level} Sailing to receive {title.lower()} bounty tasks.")]
    if not suffixes:
        # Orca's named bounty reference has competing definitions. Only the independent assignment prose is used.
        return facts
    if raw.count(DEFINITION) != 1 or re.search(r"\{\{Refn\s*\|name=bounty\b",raw,re.I):
        raise ValueError("Bounty reference must have one unambiguous definition")
    names = {title + " " + suffix for suffix in suffixes}
    rows = [p for _,_,kind,p,_ in templates(raw) if kind == "dropsline" and p.get("name") in names]
    if len(rows) != 2 or {p["name"] for p in rows} != names or {p.get("raritynotes") for p in rows} != {DEFINITION, REFERENCE}:
        raise ValueError("Both exact rows must share the verified reference")
    for row in rows:
        item = row["name"]
        matches = [d for d in drops if d["monsterName"] == title and d["itemName"] == item]
        if len(matches) != 1:
            raise ValueError("Missing or ambiguous bundled drop for " + item)
        bound = {k:matches[0][k] for k in ("quantity", "dropRate", "notes")}
        if bound != {"quantity":row["quantity"], "dropRate":row["rarity"], "notes":""}:
            raise ValueError("Drop signature differs from the reviewed row")
        expected_alt = "1/3" if item == "Butterfly ray fin" else None
        if row.get("altrarity") != expected_alt:
            raise ValueError("Review the alternate rate conditions for " + item)
        facts.append(dict(base, id=item.lower().replace(" ","-")+"-bounty", scope="ITEM", itemName=item,
                          appliesTo="ITEM_DROP", type="ACTIVE_TASK", dropSignature=bound,
                          text=f"Only drops on an applicable bounty task. {level} Sailing to receive one."))
    return facts


def build():
    drops = json.loads((DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
    facts = []
    for title in REVIEWED:
        pin = json.loads((PINS / (title.replace(" ","_")+".json")).read_text(encoding="utf8"))
        if pin["title"] != title:
            raise ValueError("Wrong primary for " + title)
        facts.extend(extract(pin, drops))
    return {"schemaVersion":1, "checkedAt":"2026-11-05T01:10:00Z", "facts":facts}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    document = json.dumps(build(), ensure_ascii=False, indent=2)+"\n"
    if args.check:
        if OUT.read_text(encoding="utf8") != document:
            raise SystemExit("Ocean bounty facts differ from the saved evidence")
    else:
        OUT.write_text(document,encoding="utf8")
    print("5 assignment levels and 8 signature-qualified ocean bounty drops")
