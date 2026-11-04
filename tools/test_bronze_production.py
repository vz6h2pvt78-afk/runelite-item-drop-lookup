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

    def test_unavailable_expanded_template_evidence_blocks_guessed_defaults(self):
        self.pin['standardRenderedHtml'] = ''
        import hashlib
        self.pin['standardHtmlSha256'] = hashlib.sha256(b'').hexdigest()
        with self.assertRaises(ValueError):
            recipe.build(self.pin)
