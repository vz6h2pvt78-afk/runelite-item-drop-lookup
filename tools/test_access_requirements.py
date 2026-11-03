import copy
import json
import unittest
from validate_access_requirements import ROOT, validate


class RequirementsValidationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        resources = ROOT / "src/main/resources"
        cls.data = json.loads((resources / "access_requirements.json").read_text(encoding="utf-8"))
        cls.locations = json.loads((resources / "monster_locations.json").read_text(encoding="utf-8"))
        cls.drops = json.loads((resources / "wiki_monster_drops.json").read_text(encoding="utf-8"))

    def check_mutation(self, change, expected):
        data = copy.deepcopy(self.data)
        change(data)
        self.assertTrue(any(expected in error for error in validate(data, self.locations, self.drops)))

    def test_curated_dataset_is_valid(self):
        self.assertEqual([], validate(self.data, self.locations, self.drops))

    def test_regenerated_location_id_requires_review(self):
        self.check_mutation(lambda d: d["facts"][1].update(locationId="changed-after-wiki-edit"), "Dangling location")

    def test_location_cannot_leak_to_another_monster(self):
        self.check_mutation(lambda d: d["facts"][1].update(monsterName="Goblin"), "monster mismatch")

    def test_item_scope_requires_real_pair(self):
        self.check_mutation(lambda d: d["facts"][-1].update(itemName="Abyssal whip"), "Unknown exact monster/item")

    def test_provenance_cannot_point_to_different_revision(self):
        self.check_mutation(lambda d: d["facts"][0]["evidence"][0].update(revisionId=1), "exact Wiki page and revision")

    def test_missing_provenance_rejected(self):
        self.check_mutation(lambda d: d["facts"][0].update(evidence=[]), "Missing provenance")

    def test_duplicate_ids_rejected(self):
        self.check_mutation(lambda d: d["facts"].append(copy.deepcopy(d["facts"][0])), "Duplicate id")

    def test_unimplemented_variant_selector_never_silently_ignored(self):
        self.check_mutation(lambda d: d["facts"][0].update(dropVersion="Quest"), "Unsupported fields")

    def test_scope_cannot_ignore_a_location_condition(self):
        self.check_mutation(lambda d: d["facts"][1].update(scope="MONSTER"), "locationId must")


if __name__ == "__main__":
    unittest.main()
