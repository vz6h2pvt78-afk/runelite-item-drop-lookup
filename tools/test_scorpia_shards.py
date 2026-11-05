import json
import unittest
import build_scorpia_shards as source


class ScorpiaShardTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin = json.loads(source.PIN.read_text(encoding="utf8"))
        cls.caves = json.loads((source.RES/"monster_locations.json").read_text(encoding="utf8"))["locations"]
        cls.drops = json.loads((source.RES/"wiki_monster_drops.json").read_text(encoding="utf8"))

    def test_only_two_shards_with_exact_selected_rate_and_area_range(self):
        actual = source.shard_warnings(self.pin,self.caves,self.drops)
        self.assertEqual(actual, json.loads(source.OUT.read_text(encoding="utf8")))
        self.assertEqual({r["itemName"] for r in actual["shards"]}, source.ITEMS)
        self.assertTrue(all((r["wildernessLevelMin"],r["wildernessLevelMax"],r["dropRate"]) == (53,55,"1/256")
                            for r in actual["shards"]))

    def test_rejects_changed_range_primary_and_duplicate_drop_or_cave(self):
        shard = next(d for d in self.drops if d["monsterName"]=="Scorpia" and d["itemName"]=="Odium shard 3")
        cave = next(d for d in self.caves if d["monsterName"]=="Scorpia")
        for patch, caves, drops in [({"revisionId":1},self.caves,self.drops),
                                    ({"encounter":self.pin["encounter"].replace("53-55","54")},self.caves,self.drops),
                                    ({"fight":"The route passes through the Wilderness."},self.caves,self.drops),
                                    ({},self.caves+[cave],self.drops), ({},self.caves,self.drops+[shard])]:
            with self.subTest(fields=patch), self.assertRaises(ValueError):
                source.shard_warnings(dict(self.pin,**patch),caves,drops)
