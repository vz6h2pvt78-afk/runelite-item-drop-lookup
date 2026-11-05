import copy
import json
import unittest
import build_group_rewards as source
from validate_access_requirements import validate


class GroupRewardTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.drops = json.loads((source.DATA/"wiki_monster_drops.json").read_text(encoding="utf8"))
        cls.pin = json.loads(source.PINS.read_text(encoding="utf8"))[0]

    def test_nine_raw_rows_have_only_their_own_context(self):
        data = source.build()
        self.assertEqual(data, json.loads(source.OUTPUT.read_text(encoding="utf8")))
        self.assertEqual(9, len(data["facts"]))
        places = json.loads((source.DATA/"monster_locations.json").read_text(encoding="utf8"))
        self.assertEqual([], validate(data, places, self.drops, require_item_signatures=True))
        for fact in data["facts"]:
            rate = "Always" if fact["itemName"] in ("Big bones","Bones") else "1/360"
            self.assertEqual({"dropRate":rate, "notes":"", "quantity":"1"}, fact["dropSignature"])

    def test_missing_recipient_or_table_context_and_duplicates_are_withheld(self):
        for field, match in (("instruction","relative contribution"), ("groupQuantities","based on damage done"),
                             ("bones","did not deal the most damage"), ("unique","1/360")):
            pin = copy.deepcopy(self.pin)
            if isinstance(pin[field],list):
                pin[field] = [line.replace(match, "unverified") for line in pin[field]]
            else:
                pin[field] = pin[field].replace(match, "unverified")
            with self.subTest(field=field), self.assertRaises(ValueError):
                source.reward_facts(pin, self.drops)
        duplicate = next(r for r in self.drops if r["itemName"]=="Bones" and r["monsterName"]==self.pin["name"])
        with self.assertRaises(ValueError):
            source.reward_facts(self.pin, self.drops+[duplicate])
