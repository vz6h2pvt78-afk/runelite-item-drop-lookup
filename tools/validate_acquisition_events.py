"""Offline evidence checks for the bounded harvest / ordinary-loot sidecar.

Runtime rejects unsupported schema/conditions too. This independent check compares
the complete ordinary table to pinned Wiki text and harvest links to existing data.
It never fetches pages, changes generated drops, or infers player eligibility.
"""
from datetime import datetime
from hashlib import sha256
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / "src/main/resources/acquisition_events.json"
FIXTURE = ROOT / "tools/fixtures/acquisition_event_evidence.json"
PINS = {"Ent": 15388968, "Dragon impling": 15371528,
        "Dragon impling jar": 15371529, "Impling": 15360701,
        "Template:Ent Calculator": 14807076, "Module:Ent calculator": 14807078}
SIGNATURE = ("itemName", "monsterName", "quantity", "dropRate", "notes")
LOOKUP_ITEMS = {"Mystic robe bottom", "Amulet of glory", "Dragonstone amulet", "Dragon arrow",
                "Dragonstone bolts", "Dragon longsword", "Dragon dagger(p++)", "Dragon dart", "Dragonstone",
                "Dragon dart tip", "Dragon arrowtips", "Babydragon bones", "Dragon bones", "Magic seed",
                "Snapdragon seed", "Summer pie"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate(data, pins, locations, drops):
    require(set(data) == {"schemaVersion", "checkedAt", "attribution", "harvests", "lootEvents", "lootCoverage"}, "Root fields changed")
    require(data["schemaVersion"] == 1, "Unsupported schema")
    require(datetime.fromisoformat(data["checkedAt"].replace("Z", "+00:00")).tzinfo is not None, "Missing timestamp timezone")
    require("CC BY-NC-SA 3.0" in data["attribution"], "Missing attribution")
    evidence = {p["title"]: p for p in pins}
    require({p["title"]: p["revisionId"] for p in pins} == PINS and len(pins) == len(PINS), "Evidence pin set changed")
    for pin in pins:
        require(sha256(pin["wikitext"].encode()).hexdigest() == pin["wikitextSha256"], "Evidence pin text changed")

    def check_refs(value):
        if isinstance(value, list):
            for entry in value:
                check_refs(entry)
        elif isinstance(value, dict):
            for key, entries in value.items():
                if key == "evidence":
                    require(isinstance(entries, list) and entries, "Missing evidence")
                    for ref in entries:
                        require(set(ref) == {"page", "revisionId", "sourceUrl"}, "Evidence fields changed")
                        require(ref["page"] in PINS and ref["revisionId"] == PINS[ref["page"]], "Unreviewed evidence pin")
                        expected = "https://oldschool.runescape.wiki/w/" + ref["page"].replace(" ", "_") + "?oldid=" + str(ref["revisionId"])
                        require(ref["sourceUrl"] == expected, "Evidence URL mismatch")
                else:
                    check_refs(entries)
    check_refs(data)
    by_id = {r["id"]: r for r in locations["locations"]}
    require(len(data["harvests"]) == 3 and {h["itemName"] for h in data["harvests"]} == {"Magic logs", "Yew logs", "Logs"},
            "Harvest coverage expansion needs review")
    for harvest in data["harvests"]:
        validate_harvest(harvest, evidence, by_id, drops)
    validate_loot(data, evidence)
    return {"harvests": len(data["harvests"]), "harvestLocations": sum(len(h["locations"]) for h in data["harvests"]),
            "lootEvents": 1, "ordinaryOutcomes": 19, "lookupOutcomes": len(LOOKUP_ITEMS)}


def validate_harvest(harvest, evidence, by_id, drops):
    item = harvest["itemName"]
    require(harvest["id"] == "ent-" + item.lower().replace(" ", "-"), "Unexpected harvest identity")
    require(harvest["prerequisiteAction"] == "DEFEAT_MONSTER" and harvest["chanceKind"] == "VARIABLE", "Wrong harvest action/chance")
    require(harvest["toolRequirement"] == {"kind": "TOOL_CATEGORY", "tool": "AXE", "qualification": "USABLE_AT_CURRENT_SKILL",
                                          "scope": "HARVEST", "text": "An axe usable at your Woodcutting level."}, "Wrong tool requirement")
    if item == "Logs":
        require(harvest["outputCondition"] == {"kind": "SCOPED_STATEMENT", "scope": "OUTPUT",
                "text": "Output range: below 45 base Woodcutting. At 45, availability is uncertain because Wiki sources disagree. Unavailable above 45."},
                "Logs level-45 conflict must remain explicit; no hard upper bound")
        conflict = json.loads((ROOT / "tools/fixtures/ent_boundary_conflict.json").read_text())
        require(conflict["supportedOutputRangeBelow"] == 45 and conflict["unresolvedLevels"] == [45]
                and conflict["unavailableAbove"] == 45 and conflict["basis"] == "BASE_LEVEL"
                and conflict["articleRevisionId"] == PINS["Ent"]
                and conflict["calculatorRevisionId"] == PINS["Module:Ent calculator"], "Conflict evidence changed")
        require("regular [[logs]] are not dropped once you reach level 45" in evidence["Ent"]["wikitext"], "Missing article boundary")
        calculator = evidence["Module:Ent calculator"]["wikitext"]
        require('{31, 45}' in calculator and '{"Plain",   "Plain",   "Plain",   "Oak"' in calculator
                and 'dataTable.data[roll][levelBinIndex]' in calculator, "Missing conflicting calculator cell")
        require(any(ref["page"] == "Module:Ent calculator" for ref in harvest["evidence"]), "Missing conflict source")
    else:
        minimum = {"Magic logs": 75, "Yew logs": 61}[item]
        require(harvest["outputCondition"] == {"kind": "SKILL", "scope": "OUTPUT", "skill": "Woodcutting", "minBaseLevel": minimum,
                                              "text": f"Requires {minimum} base Woodcutting for {item}."}, "Wrong output requirement")
    require("[[magic logs]] are only obtainable at level 75" in evidence["Ent"]["wikitext"], "Missing threshold evidence")
    require("[[yew logs]] are only obtainable at level 61" in evidence["Ent"]["wikitext"], "Missing yew threshold evidence")
    require("2 noted logs drop on each success instead of 1 in the Woodcutting Guild" in evidence["Ent"]["wikitext"], "Missing yield evidence")
    legacy = harvest["legacyDrop"]
    require(set(legacy) == set(SIGNATURE), "Legacy signature fields changed")
    require(legacy == dict(zip(SIGNATURE, [item, "Ent", "1 (noted)", "Varies", ""])), "Legacy signature changed")
    require(sum(all(row.get(k) == legacy[k] for k in SIGNATURE) for row in drops) == 1, "Exact legacy source missing or ambiguous")
    expected_locations = {"96e8b6f8e85241dc9c51": (101, 2), "a68fdb7c45298260aeff": (101, 2), "b8ac024e7c04439a486b": (86, 1)}
    if item == "Logs":
        del expected_locations["b8ac024e7c04439a486b"] # Ordinary Logs/Guild remains explicitly held.
    require(len(harvest["locations"]) == len(expected_locations) and {r["locationId"] for r in harvest["locations"]} == set(expected_locations), "Harvest associations changed")
    for association in harvest["locations"]:
        record = by_id.get(association["locationId"])
        level, quantity = expected_locations[association["locationId"]]
        require(record is not None and record["monsterName"] == "Ent" and record["available"] is True
                and record["location"] == association["locationName"] and record["levels"] == [level], "Changed harvest location record")
        require(association["combatLevel"] == level and association["quantity"] == quantity
                and association["noted"] is True and association["quantityBasis"] == "SUCCESSFUL_HARVEST", "Wrong harvest yield/encounter")


def validate_loot(data, evidence):
    # The source contains two legitimate bolt-tip rows. Preserve both, in order.
    expected_outcomes = []
    pattern = r"\{\{DropsLineReward\|name=([^|{}]+)\|quantity=([^|{}]+)\|rarity=1/19(?=\||\}\})"
    for name, amount in re.findall(pattern, evidence["Dragon impling jar"]["wikitext"]):
        match = re.fullmatch(r"(\d+)(?:-(\d+))?( \(noted\))?", amount)
        require(match is not None, "Unrecognized pinned quantity")
        expected_outcomes.append((name, int(match[1]), int(match[2] or match[1]), bool(match[3]), "1/19"))
    require(len(expected_outcomes) == 19, "Incomplete pinned ordinary table")
    require(len(data["lootEvents"]) == 1, "Unexpected loot event set")
    event = data["lootEvents"][0]
    require(event["id"] == "dragon-impling-ordinary-loot" and event["containerName"] == "Dragon impling jar", "Wrong loot identity")
    actual = [(o["itemName"], o["quantityMin"], o["quantityMax"], o["noted"], o["listedChance"]) for o in event["outcomes"]]
    require(actual == expected_outcomes, "Ordinary outcome table differs from pinned source")
    require(len({o["id"] for o in event["outcomes"]}) == 19, "Duplicate outcome ID")
    for outcome in event["outcomes"]:
        expected_keys = {"id", "itemName", "quantityMin", "quantityMax", "noted", "listedChance"}
        if outcome["itemName"] == "Dragon javelin tips":
            expected_keys |= {"eligibility", "whenIneligible"}
            require(outcome["eligibility"] == {"kind": "QUEST", "quest": "Monkey Madness II", "state": "COMPLETED", "scope": "OUTCOME"}
                    and outcome["whenIneligible"] == "REROLL_WITHIN_ORDINARY_TABLE", "Missing quest reroll semantics")
        require(set(outcome) == expected_keys, "Unsupported outcome field or condition")
    require(event["probabilityBasis"] == "WIKI_LISTED_TABLE" and event["quantityBasis"] == "SUCCESSFUL_OUTCOME", "Wrong loot basis")
    selected_ids = [o["id"] for o in event["outcomes"] if o["itemName"] in LOOKUP_ITEMS]
    require(len(selected_ids) == 16 and data["lootCoverage"] == [{"eventId": event["id"], "includedOutcomeIds": selected_ids}],
            "Lookup coverage expansion needs review")
    routes = {r["kind"]: r for r in event["routes"]}
    require(len(event["routes"]) == 3 and set(routes) == {"OPEN_EXISTING", "CATCH_INTO_CONTAINER", "CATCH_AND_LOOT"}, "Wrong alternative routes")
    require(routes["OPEN_EXISTING"]["inputItem"] == "Dragon impling jar" and routes["OPEN_EXISTING"]["captureOptions"] == [], "Opening inherited capture requirement")
    for kind, area, jar in [("CATCH_INTO_CONTAINER", "Gielinor or Puro-Puro", "EMPTY_JAR_PRESENT"),
                            ("CATCH_AND_LOOT", "Outside Puro-Puro", "EMPTY_JAR_ABSENT")]:
        route = routes[kind]
        require(route["area"] == area and route["jarState"] == jar, "Wrong catch route constraints")
        require([(o["kind"], o["hunterLevel"], o["skill"], o["scope"], o["equipmentAlternatives"]) for o in route["captureOptions"]]
                == [("NET", 83, "Hunter", "CAPTURE", ["BUTTERFLY_NET", "MAGIC_BUTTERFLY_NET"]),
                    ("BAREHANDED", 93, "Hunter", "CAPTURE", [])], "Wrong capture requirements")


def load_inputs():
    return [json.loads(path.read_text(encoding="utf-8-sig")) for path in
            (RESOURCE, FIXTURE, ROOT / "src/main/resources/monster_locations.json", ROOT / "src/main/resources/wiki_monster_drops.json")]


if __name__ == "__main__":
    print(json.dumps(validate(*load_inputs())))
