from hashlib import sha256
import json
import unittest
from validate_access_requirements import ROOT


class ExtraSlayerCasesTest(unittest.TestCase):
    def test_pinned_levels_and_equipment_actions_remain_scoped(self):
        pins = json.loads((ROOT / "tools/fixtures/extra_slayer_cases.json").read_text(encoding="utf-8"))
        self.assertEqual({"Dust devil": 15386431, "Gargoyle": 15385687}, {p["title"]: p["revisionId"] for p in pins})
        for pin in pins:
            self.assertEqual(pin["wikitextSha256"], sha256(pin["wikitext"].encode()).hexdigest())
        text = {p["title"]: p["wikitext"] for p in pins}
        self.assertIn("requires level 65 Slayer to kill", text["Dust devil"])
        self.assertIn("must be equipped to fight dust devils effectively", text["Dust devil"])
        self.assertIn("requiring 75 [[Slayer]] in order to be harmed", text["Gargoyle"])
        self.assertIn("must be used when they reach 8 or lower", text["Gargoyle"])
        self.assertIn("automatically smash gargoyles even without the perk if it is equipped", text["Gargoyle"])
        data = json.loads((ROOT / "src/main/resources/access_requirements.json").read_text(encoding="utf-8"))
        selected = [f for f in data["facts"] if f["monsterName"] in text]
        self.assertEqual(4, len(selected))
        for fact in selected:
            self.assertEqual(("MONSTER", "COMBAT"), (fact["scope"], fact["appliesTo"]))
            self.assertEqual(fact["monsterName"], fact["evidence"][0]["page"])
            self.assertNotIn("locationId", fact)
            self.assertNotIn("itemName", fact)
            if fact["type"] == "SKILL":
                self.assertEqual("Slayer", fact["skill"])
                self.assertEqual({"Dust devil": 65, "Gargoyle": 75}[fact["monsterName"]], fact["level"])
            elif fact["monsterName"] == "Dust devil":
                self.assertIn("Equip a facemask, Slayer helmet or Slayer helmet (i)", fact["text"])
                self.assertIn("to prevent", fact["text"])
            else:
                self.assertIn("use a rock hammer or rock thrownhammer at 8 HP or lower", fact["text"])
                self.assertIn("wield a granite hammer for automatic finishing", fact["text"])


if __name__ == "__main__":
    unittest.main()
