import copy
import hashlib
import json
import unittest
from collections import Counter
import build_sentinel_rolls as source
from validate_access_requirements import validate


class SentinelRollTest(unittest.TestCase):
    def inputs(self):
        return (json.loads(source.PIN.read_text(encoding="utf8")),
                json.loads((source.DATA / "wiki_monster_drops.json").read_text(encoding="utf8")))

    def test_all_recorded_rows_have_exact_classification(self):
        classified = source.classify(*self.inputs())
        self.assertEqual(Counter(regular=15, guaranteed=2, tertiary=6), Counter(row["category"] for row in classified))
        categories = {row["drop"]["itemName"]: row["category"] for row in classified}
        self.assertEqual("tertiary", categories["Basilisk jaw"])
        self.assertEqual("guaranteed", categories["Brimstone key"])
        self.assertEqual("regular", categories["Astral rune"])
        data = source.build()
        self.assertEqual(data, json.loads(source.OUT.read_text(encoding="utf8")))
        self.assertTrue(all(f["perRoll"] and f["scope"] == "ITEM" for f in data["facts"]))
        locations = json.loads((source.DATA / "monster_locations.json").read_text(encoding="utf8"))
        self.assertEqual([], validate(data, locations, self.inputs()[1]))

    def test_missing_roll_marker_and_changed_section_withhold_classification(self):
        for old, new in (("|rarity=1/52|rolls=3", "|rarity=1/52"),
                         ("===Weapons and armour===", "===Tertiary===")):
            pin, drops = self.inputs()
            pin["wikitext"] = pin["wikitext"].replace(old, new, 1)
            pin["wikitextSha256"] = hashlib.sha256(pin["wikitext"].encode()).hexdigest()
            with self.assertRaisesRegex(ValueError, "classification"):
                source.classify(pin, drops)

    def test_changed_or_duplicate_raw_signature_withholds_classification(self):
        pin, drops = self.inputs()
        row = next(d for d in drops if d["monsterName"] == "Basilisk Sentinel")
        row["quantity"] = "999"
        with self.assertRaisesRegex(ValueError, "signature"):
            source.classify(pin, drops)
        pin, drops = self.inputs()
        drops.append(copy.deepcopy(next(d for d in drops if d["monsterName"] == "Basilisk Sentinel")))
        with self.assertRaisesRegex(ValueError, "ambiguous"):
            source.classify(pin, drops)

    def test_per_roll_marker_requires_bound_item_context(self):
        data = source.build()
        locations = json.loads((source.DATA / "monster_locations.json").read_text(encoding="utf8"))
        drops = self.inputs()[1]
        for field, value in (("scope", "MONSTER"), ("appliesTo", "ITEM_DROP"), ("perRoll", False)):
            changed = copy.deepcopy(data)
            changed["facts"][0][field] = value
            self.assertTrue(validate(changed, locations, drops))
