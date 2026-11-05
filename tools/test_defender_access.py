import copy
import json
import unittest
import build_defender_access as defender
from validate_access_requirements import validate


class DefenderAccessTest(unittest.TestCase):
    def setUp(self):
        self.pin = json.loads(defender.PIN.read_text(encoding="utf8"))
        self.locations = json.loads((defender.DATA/"monster_locations.json").read_text(encoding="utf8"))
        self.drops = json.loads((defender.DATA/"wiki_monster_drops.json").read_text(encoding="utf8"))

    def test_only_the_exact_basement_version_gets_the_earned_unlock(self):
        result = defender.build(self.pin, self.locations, self.drops)
        self.assertEqual(result, json.loads(defender.OUTPUT.read_text(encoding="utf8")))
        row, = result["facts"]
        self.assertEqual(("ITEM","ACCESS","UNLOCK"), (row["scope"],row["appliesTo"],row["type"]))
        self.assertFalse(validate(result,self.locations,self.drops,require_item_signatures=True))
        upstairs = copy.deepcopy(result)
        upstairs["facts"][0]["dropSignature"]["verifiedLocationId"] = "ec2299b243b871a0ab97"
        self.assertTrue(validate(upstairs,self.locations,self.drops,require_item_signatures=True))
        broken = next(b for b in self.locations["dropBindings"] if b["itemName"]=="Dragon defender")
        self.locations["dropBindings"].append(copy.deepcopy(broken))
        with self.assertRaisesRegex(ValueError,"single verified basement"):
            defender.build(self.pin,self.locations,self.drops)
        self.assertTrue(validate(result,self.locations,self.drops,require_item_signatures=True))

    def test_different_version_is_not_equivalent_to_the_same_location_id(self):
        result = defender.build(self.pin, self.locations, self.drops)
        result["facts"][0]["dropSignature"]["dropVersion"]="Warriors' Guild"
        self.assertTrue(validate(result,self.locations,self.drops,require_item_signatures=True))
