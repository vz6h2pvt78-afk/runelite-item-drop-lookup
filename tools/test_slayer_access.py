import hashlib
import json
import unittest
import build_slayer_access as source
from validate_access_requirements import validate

class SlayerEvidenceTest(unittest.TestCase):
    def test_packaged_facts_reproduce_and_remain_exact_monster_scoped(self):
        data=source.build()
        self.assertEqual(data,json.loads(source.OUTPUT.read_text(encoding='utf8')))
        self.assertTrue(all(f['scope']=='MONSTER' and f['evidence'][0]['page']==f['monsterName'] for f in data['facts']))

    def test_conflicting_multibox_level_is_withheld(self):
        pin=json.loads((source.PINS/'Cave_crawler.json').read_text(encoding='utf8'))
        pin['wikitext']=pin['wikitext'].replace('|slaylvl = 10','|slaylvl = 99',1)
        pin['wikitextSha256']=hashlib.sha256(pin['wikitext'].encode()).hexdigest()
        with self.assertRaisesRegex(ValueError,'Contradictory'):
            source.facts_for(pin)

    def test_missing_level_in_either_variant_is_withheld(self):
        pin=json.loads((source.PINS/'Cave_crawler.json').read_text(encoding='utf8'))
        pin['wikitext']=pin['wikitext'].replace('|slaylvl = 10','',1)
        pin['wikitextSha256']=hashlib.sha256(pin['wikitext'].encode()).hexdigest()
        with self.assertRaisesRegex(ValueError,'missing Slayer level'):
            source.facts_for(pin)

    def test_numbered_spiritual_mage_names_must_cover_every_version(self):
        pin = json.loads((source.PINS / "Spiritual_mage.json").read_text(encoding="utf8"))
        import re
        for replacement in ("|omitted = Spiritual Mage", "|name5 = Spiritual ranger"):
            altered = dict(pin)
            altered["wikitext"], count = re.subn(r"\|name5\s*=\s*Spiritual Mage", replacement, pin["wikitext"], count=1)
            self.assertEqual(1, count)
            altered["wikitextSha256"] = hashlib.sha256(altered["wikitext"].encode()).hexdigest()
            with self.assertRaisesRegex(ValueError, "identity unclear"):
                source.facts_for(altered)
        harm = json.loads((source.PINS / "Nechryael.json").read_text(encoding="utf8"))
        harm["combatVerb"] = "kill"
        with self.assertRaisesRegex(ValueError, "combat-level prose"):
            source.facts_for(harm)

    def test_finisher_requires_its_own_proof(self):
        pin=json.loads((source.PINS/'Rockslug.json').read_text(encoding='utf8'))
        pin['additionalFacts'][0]['support']=['Unverified item']
        with self.assertRaisesRegex(ValueError,'Missing evidence'):
            source.facts_for(pin)
