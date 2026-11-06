import copy
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zlib
from build_monster_artwork import build, png_dimensions, ROOT, REVIEW, ART, MANIFEST


class MonsterArtworkTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.review = json.loads(REVIEW.read_text(encoding="utf-8"))

    def test_reviewed_bundle_matches_manifest_and_full_inventory(self):
        self.assertEqual(MANIFEST.read_text(encoding="utf-8"), build(self.review, ART / "monsters"))
        self.assertEqual(1011, len(self.review["sources"]))
        self.assertEqual(1008, sum(r["status"] == "reviewed" for r in self.review["sources"]))
        self.assertEqual(64, sum(r["status"] == "reviewed" for r in self.review["groups"]))

    def test_held_source_cannot_be_promoted_by_group(self):
        source = {r["source"]: r for r in self.review["sources"]}
        groups = {r["group"]: r for r in self.review["groups"]}
        self.assertNotIn("asset", source["Wild dog (Last Man Standing)"])
        self.assertEqual("reviewed", source["Wild dog"]["status"])
        for name in ("Guard", "Mogre", "Wild dog"):
            self.assertEqual("held", groups[name]["status"])
            self.assertNotIn("asset", groups[name])

    def test_duplicate_source_and_unsafe_asset_rejected(self):
        data = copy.deepcopy(self.review)
        data["sources"][-1] = data["sources"][0]
        with self.assertRaisesRegex(ValueError, "source inventory"):
            build(data, ART / "monsters")
        data = copy.deepcopy(self.review)
        data["assets"][0]["asset"] = "../../bad"
        with self.assertRaisesRegex(ValueError, "asset id"):
            build(data, ART / "monsters")

    def test_altered_asset_hash_rejected(self):
        data = copy.deepcopy(self.review)
        data["assets"][0]["sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "Asset mismatch"):
            build(data, ART / "monsters")

    def test_group_cannot_invent_a_representative(self):
        data = copy.deepcopy(self.review)
        record = next(r for r in data["groups"] if r["group"] == "Goblin")
        record["representativeSource"] = "Dark beast"
        with self.assertRaisesRegex(ValueError, "representative"):
            build(data, ART / "monsters")

    def test_corrupt_png_rejected(self):
        data = next((ART / "monsters").glob("*.png")).read_bytes()
        self.assertEqual((36, 40), png_dimensions(data))
        for bad in (b"not png", data[:-5], data + b"extra", data[:40] + bytes([data[40] ^ 255]) + data[41:]):
            with self.assertRaises(ValueError):
                png_dimensions(bad)


if __name__ == "__main__":
    unittest.main()
