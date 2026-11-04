import copy
import unittest
from validate_acquisition_events import load_inputs, validate


class AcquisitionEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.data, self.pins, self.locations, self.drops = load_inputs()

    def check(self):
        return validate(self.data, self.pins, self.locations, self.drops)

    def test_complete_table_and_separate_lookup_coverage(self):
        self.assertEqual(19, self.check()["ordinaryOutcomes"])
        self.assertEqual(1, self.check()["lookupOutcomes"])

    def test_duplicate_item_outcomes_cannot_be_collapsed(self):
        self.data["lootEvents"][0]["outcomes"].pop(1)
        with self.assertRaisesRegex(ValueError, "outcome table"):
            self.check()

    def test_seed_noting_and_quest_scope_are_not_inferred(self):
        outcome = next(o for o in self.data["lootEvents"][0]["outcomes"] if o["itemName"] == "Snapdragon seed")
        outcome["noted"] = True
        with self.assertRaisesRegex(ValueError, "outcome table"):
            self.check()
        outcome["noted"] = False
        javelin = next(o for o in self.data["lootEvents"][0]["outcomes"] if o["itemName"] == "Dragon javelin tips")
        javelin["eligibility"]["scope"] = "OPEN_EXISTING"
        with self.assertRaisesRegex(ValueError, "quest reroll"):
            self.check()

    def test_per_kill_quantity_and_changed_legacy_signature_are_rejected(self):
        original = copy.deepcopy(self.data)
        self.data["harvests"][0]["locations"][0]["quantityBasis"] = "PER_KILL"
        with self.assertRaisesRegex(ValueError, "yield/encounter"):
            self.check()
        self.data = original
        self.data["harvests"][0]["legacyDrop"]["quantity"] = "2 (noted)"
        with self.assertRaisesRegex(ValueError, "signature changed"):
            self.check()

    def test_missing_location_cannot_fall_back_to_the_general_monster(self):
        self.locations["locations"] = [r for r in self.locations["locations"] if r["id"] != "b8ac024e7c04439a486b"]
        with self.assertRaisesRegex(ValueError, "location record"):
            self.check()

    def test_evidence_changes_and_capture_gates_on_opening_are_rejected(self):
        self.pins[0]["wikitext"] += "Changed"
        with self.assertRaisesRegex(ValueError, "pin text"):
            self.check()
        self.pins[0]["wikitext"] = self.pins[0]["wikitext"][:-7]
        self.data["lootEvents"][0]["routes"][0]["captureOptions"] = [{"hunterLevel": 83}]
        with self.assertRaisesRegex(ValueError, "Opening inherited"):
            self.check()


if __name__ == "__main__":
    unittest.main()
