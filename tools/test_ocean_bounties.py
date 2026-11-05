import json
import unittest
import build_ocean_bounties as ocean
from validate_access_requirements import validate


class OceanBountyTest(unittest.TestCase):
    def setUp(self):
        self.drops = json.loads((ocean.DATA/"wiki_monster_drops.json").read_text(encoding="utf8"))
        self.places = json.loads((ocean.DATA/"monster_locations.json").read_text(encoding="utf8"))

    def test_five_levels_eight_exclusive_signatures_and_only_assignment_for_orca(self):
        result = ocean.build()
        self.assertEqual(result, json.loads(ocean.OUT.read_text(encoding="utf8")))
        self.assertEqual({"Eagle ray":30,"Butterfly ray":40,"Stingray":45,"Narwhal":62,"Orca":80},
                         {f["monsterName"]:f["level"] for f in result["facts"] if f["scope"]=="MONSTER"})
        exact = [f for f in result["facts"] if f["scope"]=="ITEM"]
        self.assertEqual(8,len(exact))
        self.assertFalse(any(f["monsterName"]=="Orca" for f in exact))
        self.assertFalse(validate(result,self.places,self.drops,require_item_signatures=True))

    def test_orphan_duplicate_and_absent_signature_fail_the_bound_supplement(self):
        result = ocean.build()
        fact = next(f for f in result["facts"] if f["scope"]=="ITEM")
        matches = [d for d in self.drops if d["itemName"]==fact["itemName"] and d["monsterName"]==fact["monsterName"]]
        self.assertEqual(1,len(matches))
        self.assertTrue(validate(result,self.places,self.drops+matches,require_item_signatures=True))
        fact["dropSignature"]["quantity"]="999"
        self.assertTrue(validate(result,self.places,self.drops,require_item_signatures=True))
        del fact["dropSignature"]
        self.assertTrue(validate(result,self.places,self.drops,require_item_signatures=True))
