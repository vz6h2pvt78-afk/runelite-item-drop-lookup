import copy
import json
import unittest

import build_impling_loot as builder

class ImplingTableTest(unittest.TestCase):
    def test_all_enabled_tables_reproduce_packaged_resource(self):
        actual = json.loads(builder.OUTPUT.read_text(encoding="utf-8"))
        self.assertEqual(builder.build(), actual)
        self.assertTrue(actual["tables"])

    def test_changed_source_text_rejected(self):
        pin = json.loads((builder.FIXTURES / "Baby.json").read_text(encoding="utf-8"))
        changed = copy.deepcopy(pin)
        changed["wikitext"] = changed["wikitext"].replace("rarity=1/10", "rarity=1/20", 1)
        with self.assertRaisesRegex(ValueError, "Changed Wiki snapshot"):
            builder.build_event(changed)

    def test_empty_and_pre_roll_are_not_ordinary_searchable_items(self):
        def table(tier):
            return builder.build_event(json.loads((builder.FIXTURES / (tier+".json")).read_text(encoding="utf-8")))
        baby = table("Baby")
        no_item = next(o for o in baby["event"]["outcomes"] if o["itemName"] == "Nothing")
        self.assertEqual("1/10", no_item["listedChance"])
        self.assertNotIn(no_item["id"], baby["coverage"]["includedOutcomeIds"])
        gourmet = table("Gourmet")["event"]
        self.assertNotIn("Grubby key", [o["itemName"] for o in gourmet["outcomes"]])
        self.assertIn("pre-roll", gourmet["probabilityContext"])
