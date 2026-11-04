import copy
import json
import unittest
from patch_zulrah_dragonstone import EXPECTED, FIXTURE, RESOURCE, reviewed_patch


class ZulrahDragonstoneTest(unittest.TestCase):
    def setUp(self):
        self.fixture = json.loads(FIXTURE.read_text(encoding="utf-8-sig"))
        rows = json.loads(RESOURCE.read_text(encoding="utf-8-sig"))
        self.before = [r for r in rows if not (r.get("itemName") == "Dragonstone" and r.get("monsterName") == "Zulrah")]

    def test_one_supported_addition_preserves_all_other_records_and_order(self):
        result, changed = reviewed_patch(self.before, self.fixture)
        self.assertTrue(changed)
        self.assertEqual(self.before, result[:-1])
        self.assertEqual(EXPECTED, result[-1])
        self.assertFalse(reviewed_patch(result, self.fixture)[1])

    def test_existing_conflict_is_not_silently_repaired_or_duplicated(self):
        before = self.before + [dict(EXPECTED, quantity="2")]
        with self.assertRaisesRegex(ValueError, "Existing exact source differs"):
            reviewed_patch(before, self.fixture)
        with self.assertRaisesRegex(ValueError, "Existing exact source differs"):
            reviewed_patch(self.before + [EXPECTED, EXPECTED], self.fixture)

    def test_per_kill_guess_or_uncut_identity_cannot_replace_exact_source(self):
        for field, value in [("dropRate", "1/796.8"), ("itemName", "Uncut dragonstone"), ("quantity", "2")]:
            fixture = copy.deepcopy(self.fixture)
            fixture["expectedRecord"][field] = value
            with self.assertRaisesRegex(ValueError, "Unreviewed replacement"):
                reviewed_patch(self.before, fixture)

    def test_changed_pin_or_rendered_quantity_requires_review(self):
        fixture = copy.deepcopy(self.fixture)
        fixture["evidence"][0]["wikitext"] += " changed"
        with self.assertRaisesRegex(ValueError, "Evidence pin text"):
            reviewed_patch(self.before, fixture)
        fixture = copy.deepcopy(self.fixture)
        fixture["renderedDragonstoneRow"] = fixture["renderedDragonstoneRow"].replace('<td data-sort-value="1">1</td>', '<td>2</td>')
        with self.assertRaisesRegex(ValueError, "Rendered row"):
            reviewed_patch(self.before, fixture)


if __name__ == "__main__":
    unittest.main()
