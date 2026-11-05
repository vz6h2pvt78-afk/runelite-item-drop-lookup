import copy
import hashlib
import json
import unittest
import build_sentinel_spawn as source
from validate_access_requirements import validate


class SentinelSpawnTest(unittest.TestCase):
    def pins(self):
        return (json.loads(source.PIN.read_text(encoding="utf8")),
                json.loads(source.PARENT.read_text(encoding="utf8")))

    def test_packaged_context_reproduces_and_preserves_exact_semantics(self):
        data = source.build()
        self.assertEqual(data, json.loads(source.OUT.read_text(encoding="utf8")))
        fact, = data["facts"]
        self.assertEqual(("Basilisk Sentinel", "MONSTER", "ENCOUNTER_CONTEXT", "CONTEXT"),
                         tuple(fact[k] for k in ("monsterName", "scope", "appliesTo", "type")))
        self.assertEqual(source.TEXT, fact["text"])
        self.assertNotIn("1/", fact["text"])
        self.assertEqual([15373808, 15340714], [p["revisionId"] for p in fact["evidence"]])
        read = lambda name: json.loads((source.ROOT / "src/main/resources" / name).read_text(encoding="utf8"))
        self.assertEqual([], validate(data, read("monster_locations.json"), read("wiki_monster_drops.json")))

    def test_each_primary_is_independently_required(self):
        for index in (0, 1):
            pins = list(self.pins())
            pins[index]["revisionId"] += 1
            with self.assertRaisesRegex(ValueError, "Changed spawn primary"):
                source.extract(*pins)

    def test_missing_task_unlock_or_parent_identity_withholds_context(self):
        replacements = [(0, "[[basilisk knight]]"), (0, "''Bigger and Badder''"),
                        (1, "While on a basilisk Slayer task"), (1, "after you kill a Basilisk Knight"),
                        (1, "[[Slayer Rewards|Bigger and Badder]] unlocked")]
        for index, text in replacements:
            pins = copy.deepcopy(self.pins())
            self.assertIn(text, pins[index]["wikitext"])
            pins[index]["wikitext"] = pins[index]["wikitext"].replace(text, "Unverified condition")
            pins[index]["wikitextSha256"] = hashlib.sha256(pins[index]["wikitext"].encode()).hexdigest()
            with self.assertRaisesRegex(ValueError, "Missing"):
                source.extract(*pins)
