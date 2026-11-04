import copy
import json
import unittest
from patch_temple_supplies import FIXTURE, RESOURCE, reviewed_patch


class TempleSuppliesPatchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
        cls.current = json.loads(RESOURCE.read_text(encoding="utf-8"))

    def original_rows(self):
        rows = copy.deepcopy(self.current)
        expected = {r["itemName"]: r["expectedBefore"] for r in self.fixture["patchEntries"]}
        for row in rows:
            if row["sourceName"] == "Temple Supplies":
                row.update(expected[row["itemName"]])
        return rows

    def test_patch_changes_only_reviewed_cost_and_notes_without_reordering(self):
        before = self.original_rows()
        after, changes = reviewed_patch(before, self.fixture)
        self.assertEqual(18, len(changes))
        self.assertEqual(len(before), len(after))
        for old, new in zip(before, after):
            if old["sourceName"] == "Temple Supplies":
                self.assertEqual({k: v for k, v in old.items() if k not in {"cost", "notes"}},
                                 {k: v for k, v in new.items() if k not in {"cost", "notes"}})
            else:
                self.assertEqual(old, new)
        self.assertEqual([], reviewed_patch(after, self.fixture)[1])

    def test_changed_previous_value_is_rejected_before_any_write(self):
        rows = self.original_rows()
        next(r for r in rows if r["sourceName"] == "Temple Supplies")["cost"] = "999 coins"
        with self.assertRaisesRegex(ValueError, "previous cost/notes"):
            reviewed_patch(rows, self.fixture)

    def test_unlock_stays_with_its_item_and_current_resource_matches(self):
        after, changes = reviewed_patch(self.current, self.fixture)
        self.assertEqual([], changes)
        shop = {r["itemName"]: r for r in after if r["sourceName"] == "Temple Supplies"}
        self.assertEqual("10 Abyssal pearls", shop["Fire talisman"]["cost"])
        self.assertEqual("Members shop.", shop["Fire talisman"]["notes"])
        self.assertIn("Troll Stronghold", shop["Law talisman"]["notes"])
        self.assertIn("Sins of the Father", shop["Blood talisman"]["notes"])
        self.assertNotIn("Mourning", shop["Blood talisman"]["notes"])
        self.assertIn("Mourning's End Part II", shop["Death talisman"]["notes"])
        self.assertNotIn("Abyssal needle", shop)
        self.assertNotIn("Abyssal lantern", shop)

    def test_evidence_change_requires_review(self):
        fixture = copy.deepcopy(self.fixture)
        fixture["wikitext"] = fixture["wikitext"].replace("currency=Abyssal pearls", "currency=Coins")
        with self.assertRaisesRegex(ValueError, "Evidence pin"):
            reviewed_patch(self.current, fixture)


if __name__ == "__main__":
    unittest.main()
