"""Read-only validation of the reviewed requirements sidecar and exact bindings."""
from datetime import datetime
import json
from pathlib import Path
import re
from urllib.parse import parse_qs, unquote, urlparse

ROOT = Path(__file__).resolve().parents[1]
SCOPES = {"MONSTER", "LOCATION", "ITEM"}
APPLICABILITY = {"PROTECTION", "FINISHING", "COMBAT", "ACCESS", "TASK_ELIGIBILITY", "ITEM_DROP", "ENCOUNTER_CONTEXT", "ACTIVITY_CONTEXT", "DROP_CONTEXT"}
TYPES = {"SKILL", "ACTIVE_TASK", "QUEST", "EQUIPMENT", "CONTEXT"}


def norm(value):
    return re.sub(r"\s+", " ", str(value or "").replace("_", " ").replace("\u2019", "'")).strip().casefold()


def validate(data, locations, drops):
    errors = []
    if not isinstance(data, dict) or data.get("schemaVersion") != 1 or not isinstance(data.get("facts"), list):
        return ["Unsupported or malformed requirements dataset"]
    try:
        stamp = datetime.fromisoformat(data.get("checkedAt", "").replace("Z", "+00:00"))
        if stamp.tzinfo is None:
            raise ValueError("Missing timezone")
    except (ValueError, TypeError, AttributeError):
        errors.append("Missing or invalid checkedAt timestamp")
    by_id = {row["id"]: row for row in locations["locations"]}
    known_monsters = {norm(row.get("monsterName")) for row in drops} | {
        norm(row.get("monsterName")) for row in by_id.values()}
    known_items = {(norm(row.get("monsterName")), norm(row.get("itemName"))) for row in drops}
    ids = set()
    allowed = {"id", "monsterName", "scope", "locationId", "itemName", "appliesTo", "type", "text", "skill", "level", "evidence"}
    for index, fact in enumerate(data["facts"]):
        if not isinstance(fact, dict):
            errors.append(f"Fact {index}: not an object")
            continue
        label = fact.get("id", f"index {index}")
        def fail(reason):
            errors.append(f"{label}: {reason}")
        if fact.get("appliesTo") == "PROTECTION" and (fact.get("scope") != "MONSTER" or fact.get("type") not in {"EQUIPMENT", "CONTEXT"}):
            fail("Protection must be a monster equipment or mitigation fact")
        if fact.get("appliesTo") == "FINISHING" and (fact.get("scope") != "MONSTER" or fact.get("type") != "EQUIPMENT"):
            fail("Finishing must be a monster equipment fact")
        if fact.get("appliesTo") == "DROP_CONTEXT" and (fact.get("scope") != "MONSTER" or fact.get("type") != "CONTEXT"):
            fail("Drop context must be a monster context, not an access requirement")
        if set(fact) - allowed:
            fail("Unsupported fields (variant constraints must never be silently ignored)")
        for key in ("id", "monsterName", "text"):
            if not isinstance(fact.get(key), str) or not fact[key].strip():
                fail(f"Missing {key}")
        if not isinstance(label, str):
            fail("Invalid id")
            continue
        if label in ids:
            fail("Duplicate id")
        ids.add(label)
        monster = norm(fact.get("monsterName"))
        if monster not in known_monsters:
            fail("Unknown exact monster page")
        if fact.get("scope") not in SCOPES or fact.get("appliesTo") not in APPLICABILITY or fact.get("type") not in TYPES:
            fail("Invalid scope, applicability or type")
        if bool(fact.get("locationId")) != (fact.get("scope") == "LOCATION"):
            fail("locationId must appear only and always with LOCATION scope")
        if bool(fact.get("itemName")) != (fact.get("scope") == "ITEM"):
            fail("itemName must appear only and always with ITEM scope")
        if fact.get("scope") == "LOCATION":
            location = by_id.get(fact.get("locationId"))
            if location is None or norm(location.get("monsterName")) != monster:
                fail("Dangling location reference or monster mismatch; re-review after location regeneration")
        if fact.get("scope") == "ITEM":
            if (monster, norm(fact.get("itemName"))) not in known_items:
                fail("Unknown exact monster/item pair")
            if fact.get("appliesTo") != "ITEM_DROP":
                fail("ITEM facts must describe ITEM_DROP eligibility")
        if fact.get("type") == "SKILL":
            if not isinstance(fact.get("skill"), str) or not fact["skill"].strip() or type(fact.get("level")) is not int or not 1 <= fact["level"] <= 99:
                fail("Invalid skill requirement")
        elif "skill" in fact or "level" in fact:
            fail("Skill metadata on a non-skill fact")
        evidence = fact.get("evidence")
        if not isinstance(evidence, list) or not evidence:
            fail("Missing provenance")
            continue
        for source in evidence:
            if not isinstance(source, dict) or not isinstance(source.get("page"), str) or not source["page"].strip() or type(source.get("revisionId")) is not int or source["revisionId"] <= 0:
                fail("Malformed provenance")
                continue
            url = urlparse(str(source.get("sourceUrl", "")))
            if (url.scheme != "https" or url.netloc != "oldschool.runescape.wiki" or not url.path.startswith("/w/")
                    or norm(unquote(url.path[3:])) != norm(source["page"])
                    or parse_qs(url.query) != {"oldid": [str(source["revisionId"])]} or url.fragment):
                fail("Evidence URL must identify the exact Wiki page and revision")
    return errors


def main():
    resources = ROOT / "src/main/resources"
    read = lambda name: json.loads((resources / name).read_text(encoding="utf-8"))
    data = read("access_requirements.json")
    supplements = [read("reviewed_slayer_access.json"), read("shark_bounty_access.json")]
    locations, drops = read("monster_locations.json"), read("wiki_monster_drops.json")
    errors = []
    for extra in supplements:
        errors.extend(validate(extra, locations, drops))
        data["facts"].extend(extra["facts"])
    errors += validate(data, locations, drops)
    for error in errors:
        print(error)
    print(f"Validated {len(data.get('facts', []))} access facts: {len(errors)} error(s)")
    return bool(errors)


if __name__ == "__main__":
    raise SystemExit(main())
