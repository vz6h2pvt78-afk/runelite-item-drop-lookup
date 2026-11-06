import copy
import json
import unittest
from validate_browse_groups import RESOURCES, validate


class BrowseGroupsTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((RESOURCES / "item_browse_groups.json").read_text())

    def test_reviewed_groups_are_valid(self):
        self.assertEqual({"logs": 14, "potions": 93, "fish": 62, "uncut": 10}, validate(self.data))

    def test_rejects_ambiguous_or_unreviewed_membership(self):
        mutations = [
            lambda d: d["groups"][0]["terms"].append("LOGS"),
            lambda d: d["groups"][0]["members"].append("Logs"),
            lambda d: d["groups"][0]["members"].append("Imaginary logs"),
            lambda d: d["groups"][1]["members"].append("Prayer potion(4)"),
            lambda d: d["groups"][0].update(overridesCanonical=False),
            lambda d: d["groups"][0].update(members=[]),
            lambda d: d["groups"][0]["terms"].append("dwh"),
            lambda d: d["groups"][1]["terms"].append("logs"),
        ]
        for mutation in mutations:
            data = copy.deepcopy(self.data)
            mutation(data)
            with self.assertRaises(ValueError):
                validate(data)

    def test_member_must_not_resolve_to_another_identity(self):
        with self.assertRaises(ValueError):
            validate(self.data, aliases={"logs": "Magic logs"})

    def test_beginner_groups_exclude_specialist_and_substring_noise(self):
        groups = {g["id"]: g["members"] for g in self.data["groups"]}
        self.assertNotIn("Prayer enhance (+)", groups["potions"])
        self.assertNotIn("Absorption", groups["potions"])
        self.assertNotIn("Blighted super restore", groups["potions"])
        self.assertNotIn("Blighted manta ray", groups["fish"])
        self.assertNotIn("Super ranging", groups["potions"])
        self.assertNotIn("Log basket", groups["logs"])
        self.assertNotIn("Pyre logs", groups["logs"])
        self.assertIn("Shark", groups["fish"])
        self.assertIn("Raw shark", groups["fish"])
        self.assertNotIn("Shark (Last Man Standing)", groups["fish"])
        self.assertNotIn("Lobster pot", groups["fish"])
