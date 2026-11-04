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
        self.assertEqual(16, self.check()["lookupOutcomes"])

    def test_coverage_never_exposes_duplicate_or_quest_gated_outputs(self):
        coverage = self.data["lootCoverage"][0]["includedOutcomeIds"]
        original = list(coverage)
        for excluded in ["dragon-dragonstone-bolt-tips-10-30", "dragon-dragonstone-bolt-tips-36-36", "dragon-dragon-javelin-tips"]:
            coverage[:] = original + [excluded]
            with self.assertRaisesRegex(ValueError, "Lookup coverage"):
                self.check()

    def test_duplicate_item_outcomes_cannot_be_collapsed(self):
        self.data["lootEvents"][0]["outcomes"].pop(1)
        with self.assertRaisesRegex(ValueError, "outcome table"):
            self.check()

    def test_yew_uses_ent_threshold_and_other_logs_remain_unverified(self):
        self.assertEqual(3, self.check()["harvests"])
        self.assertEqual({"Magic logs", "Yew logs", "Logs"}, {h["itemName"] for h in self.data["harvests"]})
        yew = next(h for h in self.data["harvests"] if h["itemName"] == "Yew logs")
        yew["outputCondition"]["minBaseLevel"] = 60
        with self.assertRaisesRegex(ValueError, "output requirement"):
            self.check()

    def test_logs_boundary_cannot_be_simplified_to_a_hard_maximum(self):
        logs = next(h for h in self.data["harvests"] if h["itemName"] == "Logs")
        logs["outputCondition"] = {"kind": "SKILL", "scope": "OUTPUT", "skill": "Woodcutting",
                                   "maxBaseLevelExclusive": 45, "text": "Unavailable at 45 or above."}
        with self.assertRaisesRegex(ValueError, "level-45 conflict"):
            self.check()

    def test_ordinary_logs_do_not_acquire_the_guild_association(self):
        logs = next(h for h in self.data["harvests"] if h["itemName"] == "Logs")
        logs["locations"].append(copy.deepcopy(self.data["harvests"][0]["locations"][2]))
        with self.assertRaisesRegex(ValueError, "Harvest associations"):
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
