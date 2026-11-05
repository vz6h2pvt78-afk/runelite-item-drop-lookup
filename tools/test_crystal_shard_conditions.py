import copy
import hashlib
import json
import unittest

import build_crystal_shard_conditions as source
from validate_access_requirements import validate


class CrystalShardConditionTest(unittest.TestCase):
    def inputs(self, name):
        pin = json.loads((source.PINS / (name.replace(" ", "_") + ".json")).read_text(encoding="utf8"))
        drops = json.loads((source.DATA / "wiki_monster_drops.json").read_text(encoding="utf8"))
        return pin, drops

    def test_nine_independently_pinned_conditions_reproduce(self):
        data = source.build()
        self.assertEqual(data, json.loads(source.OUT.read_text(encoding="utf8")))
        self.assertEqual(set(source.REVISIONS), {fact["monsterName"] for fact in data["facts"]})
        self.assertEqual(9, len(data["facts"]))
        for fact in data["facts"]:
            self.assertEqual(("ITEM", "ITEM_DROP", "CONTEXT"), tuple(fact[key] for key in ("scope", "appliesTo", "type")))
            self.assertEqual(fact["monsterName"], fact["evidence"][0]["page"])
            expected = {"quantity": "6-10", "dropRate": "Always", "notes": ""} if fact["monsterName"] in source.GUARANTEED else {"quantity": "3-5", "dropRate": "1/24", "notes": ""}
            self.assertEqual(expected, fact["dropSignature"])
            self.assertNotIn("perRoll", fact)
        locations = json.loads((source.DATA / "monster_locations.json").read_text(encoding="utf8"))
        self.assertEqual([], validate(data, locations, self.inputs("Dark beast")[1]))

    def test_changed_primary_location_note_and_roll_metadata_fail_closed(self):
        for name in source.REVISIONS:
            pin, drops = self.inputs(name)
            pin["revisionId"] += 1
            with self.assertRaisesRegex(ValueError, "primary"):
                source.extract(pin, drops)
            for old, new in (("Iorwerth Dungeon", "Unverified place"),
                             ("|name=Crystal shard|", "|name=Crystal shard|rolls=3|")):
                pin, drops = self.inputs(name)
                self.assertIn(old, pin["wikitext"])
                pin["wikitext"] = pin["wikitext"].replace(old, new)
                pin["wikitextSha256"] = hashlib.sha256(pin["wikitext"].encode()).hexdigest()
                with self.assertRaises(ValueError):
                    source.extract(pin, drops)

    def test_raw_signature_mutation_or_duplication_withholds_context(self):
        for name in source.REVISIONS:
            for field, value in (("quantity", "999"), ("dropRate", "1/999"), ("notes", "Changed")):
                pin, drops = self.inputs(name)
                row = next(drop for drop in drops if drop["monsterName"] == name and drop["itemName"] == "Crystal shard")
                row[field] = value
                with self.assertRaisesRegex(ValueError, "signature"):
                    source.extract(pin, drops)
            pin, drops = self.inputs(name)
            drops.append(copy.deepcopy(next(drop for drop in drops if drop["monsterName"] == name and drop["itemName"] == "Crystal shard")))
            with self.assertRaisesRegex(ValueError, "ambiguous"):
                source.extract(pin, drops)


if __name__ == "__main__":
    unittest.main()
