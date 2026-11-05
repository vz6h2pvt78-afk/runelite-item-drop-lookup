import copy
import json
import unittest
import build_bone_lairs as source


class BoneLairTest(unittest.TestCase):
    def inputs(self):
        read = lambda path: json.loads(path.read_text(encoding="utf8"))
        return [read(source.GROUP_PINS), {pin["monsterName"]: pin for pin in read(source.LAIR_PINS)},
                read(source.DATA / "monster_locations.json"), read(source.DATA / "wiki_monster_drops.json")]

    def test_exact_six_signatures_reproduce_without_binding_mutation(self):
        inputs = self.inputs()
        unchanged = copy.deepcopy(inputs)
        data = source.extract(*inputs)
        self.assertEqual(data, json.loads(source.OUT.read_text(encoding="utf8")))
        self.assertEqual(unchanged, inputs)
        self.assertEqual({(boss, item) for boss in ("Callisto", "Vet'ion", "Venenatis") for item in ("Bones", "Big bones")},
                         {(row["monsterName"], row["itemName"]) for row in data["encounters"]})
        self.assertTrue(all((row["quantity"], row["dropRate"], row["notes"]) == ("1", "Always", "") for row in data["encounters"]))

    def test_recipient_and_lair_proofs_are_independently_required(self):
        inputs = self.inputs()
        inputs[0][0]["bones"] = [line.replace("the player who deals the most damage in the fight", "unverified recipient")
                                 for line in inputs[0][0]["bones"]]
        with self.assertRaisesRegex(ValueError, "recipient"):
            source.extract(*inputs)
        inputs = self.inputs()
        inputs[1]["Callisto"]["revisionId"] += 1
        with self.assertRaisesRegex(ValueError, "revisions disagree"):
            source.extract(*inputs)
        inputs = self.inputs()
        next(row for row in inputs[2]["locations"] if row["monsterName"] == "Callisto")["wilderness"] = "NO"
        with self.assertRaisesRegex(ValueError, "Location"):
            source.extract(*inputs)
