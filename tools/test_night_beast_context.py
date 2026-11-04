from hashlib import sha256
import json
import unittest
from validate_access_requirements import ROOT, validate


class NightBeastContextTest(unittest.TestCase):
    def test_context_retains_pinned_roll_semantics_without_becoming_access(self):
        pin = json.loads((ROOT / "tools/fixtures/night_beast_context.json").read_text(encoding="utf-8-sig"))
        self.assertEqual(("Night beast", 15373912), (pin["title"], pin["revisionId"]))
        self.assertEqual(pin["wikitextSha256"], sha256(pin["wikitext"].encode()).hexdigest())
        self.assertIn("always roll 3 additional items, of which only 1 roll may be from the superior unique table", pin["wikitext"])
        self.assertIn("displayed drop chances represent a single roll", pin["wikitext"])
        data = json.loads((ROOT / "src/main/resources/access_requirements.json").read_text(encoding="utf-8"))
        facts = [f for f in data["facts"] if f["appliesTo"] == "DROP_CONTEXT"]
        self.assertEqual(1, len(facts))
        fact = facts[0]
        self.assertEqual(("Night beast", "MONSTER", "CONTEXT"), (fact["monsterName"], fact["scope"], fact["type"]))
        self.assertIn("At most one", fact["text"])
        self.assertIn("Regular-table rates and quantities shown here are per roll", fact["text"])
        self.assertIn("Guaranteed and tertiary drops are separate", fact["text"])
        fact["type"] = "QUEST"
        locations = json.loads((ROOT / "src/main/resources/monster_locations.json").read_text(encoding="utf-8"))
        drops = json.loads((ROOT / "src/main/resources/wiki_monster_drops.json").read_text(encoding="utf-8"))
        self.assertTrue(any("not an access requirement" in error for error in validate(data, locations, drops)))


if __name__ == "__main__":
    unittest.main()
