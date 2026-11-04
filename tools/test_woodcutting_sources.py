import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import build_woodcutting_sources as wood
from build_gathering_sources import build_proposal as mining, load_fixture as load_mining
from build_fishing_sources import build_proposal as fishing, load_fixture as load_fishing


class WoodcuttingSourceTests(unittest.TestCase):
    def setUp(self):
        self.fixture = wood.load_fixture()

    def replace(self, page, old, new):
        source = self.fixture['sources'][page]
        self.assertIn(old, source['wikitext'])
        source['wikitext'] = source['wikitext'].replace(old, new)

    def test_one_ordinary_tree_method_preserves_unknown_location_and_yield_facts(self):
        result = wood.build_proposal(self.fixture)
        self.assertEqual(1, len(result['records']))
        row = result['records'][0]
        self.assertEqual((1511, 'Logs', 'Gathering', 'Chop ordinary trees'),
                         (row['itemId'], row['itemName'], row['sourceType'], row['sourceName']))
        self.assertEqual('Draynor Village — near the bank', row['location'])
        self.assertEqual('Woodcutting level 1 · Axe required', row['notes'])
        self.assertIsNone(result['occurrence']['coordinates'])
        self.assertIsNone(result['occurrence']['membersOnly'])
        self.assertIsNone(result['occurrence']['resourceCount'])
        self.assertIsNone(result['method']['output']['quantity'])
        self.assertIsNone(result['method']['skills'][0]['boostable'])
        for unwanted in ['Ent', 'Wilderness', '45', 'F2P', 'Quantity', 'XP', 'Always', '35.4']:
            self.assertNotIn(unwanted, row['notes'])

    def test_consumption_info_is_not_used_to_infer_logs_acquisition(self):
        before = wood.build_proposal(self.fixture)
        self.replace('Logs', '{{Firemaking info', '{{Unreviewed consumption')
        after = wood.build_proposal(self.fixture)
        self.assertEqual(before['records'], after['records'])
        self.replace('Tree', '{{Woodcutting info', '{{Firemaking info')
        with self.assertRaisesRegex(ValueError, 'reviewed woodcutting info'):
            wood.build_proposal(self.fixture)

    def test_output_identity_and_activity_do_not_inherit_other_tree_families(self):
        for page, old, new in [('Logs', '|id = 1511', '|id = 1521'),
                               ('Tree', 'name=Logs|quantity=1', 'name=Oak logs|quantity=1'),
                               ('Tree', 'rarity=Always|skill=Woodcutting', 'rarity=Always|skill=Combat'),
                               ('Tree', '|options = Chop down', '|options = Inspect')]:
            with self.subTest(new=new):
                self.fixture = wood.load_fixture()
                self.replace(page, old, new)
                with self.assertRaises(ValueError):
                    wood.build_proposal(self.fixture)

    def test_level_tool_and_new_conditions_need_explicit_review(self):
        for old, new in [('|level = 1\n', '|level = 15\n'), ('|tool = [[Axe]]', '|tool = [[Knife]]'),
                         ('|level = 1\n', '|level = 1\n|notes = Requires a quest\n')]:
            with self.subTest(new=new):
                self.fixture = wood.load_fixture()
                self.replace('Tree', old, new)
                with self.assertRaises(ValueError):
                    wood.build_proposal(self.fixture)

    def test_location_must_remain_in_the_reviewed_prose_section(self):
        for old, new in [('near the bank in [[Draynor Village]]', 'near the bank in [[Lumbridge]]'),
                         ('==Tree locations==', '==Trivia==')]:
            with self.subTest(new=new):
                self.fixture = wood.load_fixture()
                self.replace('Tree', old, new)
                with self.assertRaises(ValueError):
                    wood.build_proposal(self.fixture)

    def test_prior_gathering_and_ent_runtime_evidence_remain_unchanged(self):
        previous = mining(load_mining())['records'] + fishing(load_fishing())['records']
        wood.check_unchanged(self.fixture, previous)
        changed = copy.deepcopy(previous)
        changed[1]['notes'] += ' changed'
        with self.assertRaisesRegex(ValueError, 'Earlier Mining/Fishing'):
            wood.check_unchanged(self.fixture, changed)
        self.fixture['unchangedFileSha256']['src/main/resources/acquisition_events.json'] = 'wrong'
        with self.assertRaisesRegex(ValueError, 'Unrelated file'):
            wood.check_unchanged(self.fixture, previous)

    def test_missing_malformed_or_changed_evidence_never_synthesizes_a_tree(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'fixture.json'
            with patch.object(wood, 'FIXTURE', path):
                with self.assertRaises(FileNotFoundError): wood.load_fixture()
                path.write_text('{broken', encoding='utf-8')
                with self.assertRaises(json.JSONDecodeError): wood.load_fixture()
                path.write_text('{}', encoding='utf-8')
                with self.assertRaisesRegex(ValueError, 'explicit review'): wood.load_fixture()

    def test_revision_changes_provenance_but_not_method_identity(self):
        before = wood.build_proposal(self.fixture)
        self.fixture['sources']['Tree']['revisionId'] += 1
        after = wood.build_proposal(self.fixture)
        self.assertEqual(before['records'], after['records'])
        self.assertNotEqual(before['provenance'], after['provenance'])


if __name__ == '__main__':
    unittest.main()
