import json
import unittest
import build_shark_bounties as sharks


class BountyEvidenceTest(unittest.TestCase):
    def test_nine_approved_facts_reproduce_from_three_named_revisions(self):
        data = sharks.build()
        self.assertEqual(data, json.loads(sharks.OUTPUT.read_text(encoding="utf8")))
        self.assertEqual(9, len(data["facts"]))
        levels = {r["monsterName"]: r["level"] for r in data["facts"] if r["type"] == "SKILL"}
        self.assertEqual({"Bull shark": 30, "Hammerhead shark": 40, "Tiger shark": 55}, levels)
        for fact in data["facts"]:
            self.assertEqual(fact["monsterName"], fact["evidence"][0]["page"])
            if fact["type"] == "ACTIVE_TASK":
                self.assertIn(fact["itemName"], [fact["monsterName"] + suffix for suffix in (" jaw", " liver")])

    def test_named_reference_and_both_exact_rows_are_required(self):
        raw = json.loads((sharks.PINS / "Tiger_shark.json").read_text(encoding="utf8"))["wikitext"]
        for unsupported in (raw.replace(sharks.DEFINITION, sharks.REFERENCE), raw.replace("Tiger shark jaw|quantity", "Other jaw|quantity")):
            with self.assertRaises(ValueError):
                sharks.drops_with_bounty_condition(unsupported, "Tiger shark")
