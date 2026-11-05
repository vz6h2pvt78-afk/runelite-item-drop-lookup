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

    def test_reviewed_raw_rows_have_only_their_own_context(self):
        data = source.build()
        self.assertEqual(data, json.loads(source.OUTPUT.read_text(encoding="utf8")))
        self.assertEqual(12, len(data["facts"]))
        places = json.loads((source.DATA/"monster_locations.json").read_text(encoding="utf8"))
        self.assertEqual([], validate(data, places, self.drops, require_item_signatures=True))
        for fact in data["facts"]:
            rate = "Always" if fact["itemName"] in ("Big bones","Bones") else "1/360"
            if fact["itemName"] == "Dragon pickaxe": rate = "1/256"
            self.assertEqual({"dropRate":rate, "notes":"", "quantity":"1"}, fact["dropSignature"])

    def test_pickaxe_group_rule_never_applies_to_artio_or_another_rate(self):
        pins = json.loads(source.PICKAXES.read_text(encoding="utf8"))
        artio = next(p for p in pins if p["name"] == "Artio")
        with self.assertRaises(ValueError):
            source.pickaxe_fact(artio, self.drops)
        callisto = next(p for p in pins if p["name"] == "Callisto")
        for patch in ({"heading":"===Other==="}, {"instruction":"Rate unknown"},
                      {"row":callisto["row"].replace("1/256","1/358")}):
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                source.pickaxe_fact(dict(callisto,**patch),self.drops)

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
