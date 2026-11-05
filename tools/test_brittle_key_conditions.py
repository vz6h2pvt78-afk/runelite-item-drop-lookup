import copy
import hashlib
import json
import unittest

import build_brittle_key_conditions as source
from validate_access_requirements import validate


class BrittleKeyConditionTest(unittest.TestCase):
    def inputs(self, name):
        pin = json.loads((source.PINS / (name.replace(" ", "_") + ".json")).read_text(encoding="utf8"))
        drops = json.loads((source.DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
        return pin, drops

    def test_two_independently_pinned_conditions_reproduce(self):
        data = source.build()
        self.assertEqual(data, json.loads(source.OUT.read_text(encoding="utf8")))
        self.assertEqual(list(source.REVISIONS), [fact["monsterName"] for fact in data["facts"]])
        for fact in data["facts"]:
            self.assertEqual(("ITEM", "ITEM_DROP", "CONTEXT"), tuple(fact[key] for key in ("scope", "appliesTo", "type")))
            self.assertEqual({"quantity": "1", "dropRate": "1/150", "notes": ""}, fact["dropSignature"])
            self.assertEqual(source.TEXT, fact["text"])
            self.assertEqual(fact["monsterName"], fact["evidence"][0]["page"])
        locations = json.loads((source.DATA / "monster_locations.json").read_text(encoding="utf8"))
        self.assertEqual([], validate(data, locations, self.inputs("Gargoyle")[1]))

    def test_each_of_three_conditions_is_required_on_each_primary(self):
        for name in source.REVISIONS:
            for phrase in ("has not unlocked the roof access", "does not have one in their bank or inventory",
                           "has gargoyles as their current [[Slayer task]]"):
                pin, drops = self.inputs(name)
                pin["wikitext"] = pin["wikitext"].replace(phrase, "Unreviewed condition")
                pin["wikitextSha256"] = hashlib.sha256(pin["wikitext"].encode()).hexdigest()
                with self.assertRaisesRegex(ValueError, "condition"):
                    source.extract(pin, drops)

    def test_revision_and_raw_signature_changes_fail_closed(self):
        for name in source.REVISIONS:
            pin, drops = self.inputs(name)
            pin["revisionId"] += 1
            with self.assertRaisesRegex(ValueError, "primary"):
                source.extract(pin, drops)
            for field, value in (("quantity", "2"), ("dropRate", "1/151"), ("notes", "Changed")):
                pin, drops = self.inputs(name)
                target = next(drop for drop in drops if drop["monsterName"] == name and drop["itemName"] == "Brittle key")
                target[field] = value
                with self.assertRaisesRegex(ValueError, "signature"):
                    source.extract(pin, drops)
            pin, drops = self.inputs(name)
            drops.append(copy.deepcopy(next(drop for drop in drops if drop["monsterName"] == name and drop["itemName"] == "Brittle key")))
            with self.assertRaisesRegex(ValueError, "ambiguous"):
                source.extract(pin, drops)


if __name__ == "__main__":
    unittest.main()
