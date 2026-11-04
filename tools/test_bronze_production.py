import copy
import json
import unittest
import build_bronze_production as recipe

class BronzeEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.pin = json.loads(recipe.PIN.read_text(encoding='utf8'))

    def test_packaged_quantities_follow_the_rendered_standard_tab(self):
        packaged = json.loads(recipe.OUTPUT.read_text(encoding='utf8'))
        self.assertEqual(recipe.build(self.pin), packaged)
        row = packaged['recipes'][0]
        self.assertEqual([{'itemName':'Copper ore','quantity':1},{'itemName':'Tin ore','quantity':1}],row['inputs'])
        self.assertEqual({'itemName':'Bronze bar','quantity':1},row['output'])
        self.assertTrue('Blast Furnace' not in row['facility'])

    def test_bronze_ground_rows_reproduce_without_quantity_or_respawn_assumptions(self):
        import build_bronze_ground as ground
        rows, provenance = ground.build(self.pin)
        self.assertEqual(json.loads(ground.OUT.read_text(encoding='utf8')), rows)
        self.assertEqual(json.loads(ground.EVIDENCE.read_text(encoding='utf8')), provenance)
        self.assertTrue(all(row['pickupQuantity'] is None for row in rows))
        self.assertEqual(['Task-only area'], provenance[0]['verifiedRestrictions'])
        self.assertTrue(all(not p['verifiedRestrictions'] for p in provenance[1:]))

    def test_unavailable_expanded_template_evidence_blocks_guessed_defaults(self):
        self.pin['standardRenderedHtml'] = ''
        import hashlib
        self.pin['standardHtmlSha256'] = hashlib.sha256(b'').hexdigest()
        with self.assertRaises(ValueError):
            recipe.build(self.pin)
