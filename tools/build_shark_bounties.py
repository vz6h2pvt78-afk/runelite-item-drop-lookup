"""Reproduce three bounty levels and six exact drop conditions from saved Wiki revisions."""
import argparse
import hashlib
import json
from pathlib import Path

from build_monster_locations import templates

ROOT = Path(__file__).resolve().parents[1]
PINS = ROOT / "tools/fixtures/shark_bounties"
OUTPUT = ROOT / "src/main/resources/shark_bounty_access.json"
REVIEWED = {
    "Bull shark": (15372302, 30, "83426150a13f702aff228fb904cb0b5ec3f6334411463e8bdf0bd4bf7ae20912"),
    "Hammerhead shark": (15285222, 40, "9f269d1c31df9c50c66a4e70311c0009d1157f88150f07afc7775e8a2980b255"),
    "Tiger shark": (15379177, 55, "1f04c14760358c60ad992de268b8bbc3a34dfb66cbc017d37d356b754252a110"),
}
DEFINITION = "<ref name=bounty group=d>Only dropped while on an applicable [[bounty task]].</ref>"
REFERENCE = "<ref name=bounty group=d />"


def drops_with_bounty_condition(raw, title):
    items = {title + " jaw", title + " liver"}
    rows = [params for _, _, kind, params, _ in templates(raw)
            if kind == "dropsline" and params.get("name") in items]
    if len(rows) != 2 or {row["name"] for row in rows} != items:
        raise ValueError("Missing or ambiguous jaw/liver stock for " + title)
    if {row.get("raritynotes") for row in rows} != {DEFINITION, REFERENCE} or raw.count(DEFINITION) != 1:
        raise ValueError("Bounty reference must resolve to one shared condition")
    for row in rows:
        is_jaw = row["name"].endswith(" jaw")
        if row.get("quantity") != "1" or row.get("rarity") != ("1/2" if is_jaw else "1/10"):
            raise ValueError("Review the changed drop row")
        alternate = "1/3" if is_jaw and title in ("Bull shark", "Hammerhead shark") else None
        if row.get("altrarity") != alternate:
            raise ValueError("Review the alternate rate evidence")
    return sorted(items)


def build():
    facts = []
    for title, (revision, level, digest) in REVIEWED.items():
        pin = json.loads((PINS / (title.replace(" ", "_") + ".json")).read_text(encoding="utf8"))
        raw = pin["wikitext"]
        if (pin["title"] != title or pin["revisionId"] != revision
                or hashlib.sha256(raw.encode()).hexdigest() != digest or pin["wikitextSha256"] != digest):
            raise ValueError("Unreviewed shark revision")
        phrase = f"assigned to kill them as a [[bounty task]] starting at {level} [[Sailing]]"
        if phrase not in raw:
            raise ValueError("Missing bounty assignment level")
        key = title.lower().replace(" ", "-")
        url = "https://oldschool.runescape.wiki/w/" + title.replace(" ", "_") + "?oldid=" + str(revision)
        base = {"monsterName": title, "evidence": [{"page": title, "revisionId": revision, "sourceUrl": url}]}
        facts.append(dict(base, id=key + "-bounty-eligibility", scope="MONSTER", appliesTo="TASK_ELIGIBILITY",
                          type="SKILL", skill="Sailing", level=level, text=f"{level} Sailing to receive {title.lower()} bounty tasks."))
        for item in drops_with_bounty_condition(raw, title):
            facts.append(dict(base, id=item.lower().replace(" ", "-") + "-bounty", scope="ITEM", itemName=item,
                              appliesTo="ITEM_DROP", type="ACTIVE_TASK",
                              text=f"Only drops on an applicable bounty task. {level} Sailing to receive one."))
    return {"schemaVersion": 1, "checkedAt": "2026-11-05T00:00:00Z", "facts": facts}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    result = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf8") != result:
            raise SystemExit("Bounty resource differs from reviewed evidence")
    else:
        OUTPUT.write_text(result, encoding="utf8")
    print("3 bounty levels and 6 item-specific bounty conditions")
