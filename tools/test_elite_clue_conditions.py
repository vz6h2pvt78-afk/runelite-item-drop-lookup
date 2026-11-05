import copy
import hashlib
import json
import unittest

import build_elite_clue_conditions as source
from validate_access_requirements import validate


class EliteClueConditionTest(unittest.TestCase):
    def inputs(self):
        pins = {name: json.loads((source.PINS / (name.replace(" ", "_") + ".json")).read_text(encoding="utf8"))
                for name in source.REVISIONS}
        drops = json.loads((source.DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
        return pins, drops

    def test_three_distinct_rewards_reproduce(self):
        data = source.build()
        self.assertEqual(data, json.loads(source.OUT.read_text(encoding="utf8")))
        self.assertEqual(3, len(data["facts"]))
        clue, casket, key = data["facts"]
        self.assertIn("non-final", clue["text"])
        self.assertIn("the final", casket["text"])
        self.assertNotIn("non-final", casket["text"])
        self.assertNotIn("cryptic", key["text"])
        self.assertIn("no Key (elite) in your bank or inventory", key["text"])
        for fact in data["facts"]:
            self.assertEqual({"quantity": "1", "dropRate": "Always", "notes": ""}, fact["dropSignature"])
            self.assertEqual(("ITEM", "ITEM_DROP", "CONTEXT"), tuple(fact[field] for field in ("scope", "appliesTo", "type")))
        locations = json.loads((source.DATA / "monster_locations.json").read_text(encoding="utf8"))
        self.assertEqual([], validate(data, locations, self.inputs()[1]))

    def test_explicit_stage_distinction_and_reference_are_required(self):
        changes = [
            ("Aberrant spectre", "(or a casket, if this was the last step)", "(or some other reward)"),
            ("Aberrant spectre", source.SPECTRE_REFERENCE, "<ref name=unknown group=d />"),
            ("King Black Dragon", "does not have a copy of the key in their bank or inventory", "has an unspecified state"),
        ]
        for name, old, new in changes:
            pins, drops = self.inputs()
            pins[name]["wikitext"] = pins[name]["wikitext"].replace(old, new)
            pins[name]["wikitextSha256"] = hashlib.sha256(pins[name]["wikitext"].encode()).hexdigest()
            with self.assertRaises(ValueError):
                source.extract(pins, drops)

    def test_each_primary_and_raw_signature_must_match(self):
        for name in source.REVISIONS:
            pins, drops = self.inputs()
            pins[name]["revisionId"] += 1
            with self.assertRaisesRegex(ValueError, "primary"):
                source.extract(pins, drops)
        for _, monster, item, _ in source.REVIEWED:
            for field, value in (("quantity", "2"), ("dropRate", "1/2"), ("notes", "Changed")):
                pins, drops = self.inputs()
                target = next(drop for drop in drops if drop["monsterName"] == monster and drop["itemName"] == item)
                target[field] = value
                with self.assertRaisesRegex(ValueError, "recorded"):
                    source.extract(pins, drops)
            pins, drops = self.inputs()
            drops.append(copy.deepcopy(next(drop for drop in drops if drop["monsterName"] == monster and drop["itemName"] == item)))
            with self.assertRaisesRegex(ValueError, "ambiguous"):
                source.extract(pins, drops)


if __name__ == "__main__":
    unittest.main()
