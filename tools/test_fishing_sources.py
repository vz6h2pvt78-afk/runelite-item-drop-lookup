import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import build_fishing_sources as fishing
from build_gathering_sources import build_proposal as mining, load_fixture as load_mining


class FishingSourceTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fishing.load_fixture()

    def replace(self, page, old, new):
        source = self.fixture['sources'][page]
        self.assertIn(old, source['wikitext'])
        source['wikitext'] = source['wikitext'].replace(old, new)

    def test_one_area_and_output_not_five_spots_or_guaranteed_catch(self):
        proposal = fishing.build_proposal(self.fixture)
        self.assertEqual(1, len(proposal['records']))
        row = proposal['records'][0]
        self.assertEqual((317, 'Raw shrimps', 'Gathering', 'Lumbridge Swamp'),
                         (row['itemId'], row['itemName'], row['sourceType'], row['location']))
        self.assertEqual(5, len(proposal['occurrence']['coordinates']))
        self.assertIsNone(proposal['occurrence']['resourceCount'])
        self.assertIsNone(proposal['method']['output']['quantity'])
        self.assertIsNone(proposal['method']['skills'][0]['boostable'])
        self.assertFalse(proposal['method']['baitRequired'])
        self.assertEqual('Small net', proposal['method']['interactionVariant'])
        self.assertEqual('', row['cost'])
        for excluded in ['chance', 'XP', 'quantity', '5 spots', 'Combat', 'League', 'Bait level', 'bait required']:
            self.assertNotIn(excluded, row['notes'])

    def test_raw_item_identity_cannot_be_replaced_by_cooked_or_npc_id(self):
        for old, new in [('|id = 317', '|id = 315'), ('|name = Raw shrimps', '|name = Shrimps'),
                         ('|id = 317', '|id = 1514')]:
            with self.subTest(new=new):
                self.fixture = fishing.load_fixture()
                self.replace('Raw shrimps', old, new)
                with self.assertRaisesRegex(ValueError, 'Canonical output identity'):
                    fishing.build_proposal(self.fixture)

    def test_tool_level_bait_and_unreviewed_conditions_fail_closed(self):
        for old, new in [('|level = 1\n', '|level = 5\n'), ('|tool = Small fishing net', '|tool = Fishing rod'),
                         ('|bait = None', '|bait = Fishing bait'),
                         ('|bait = None', '|bait = None\n|notes = Requires a quest')]:
            with self.subTest(new=new):
                self.fixture = fishing.load_fixture()
                self.replace('Raw shrimps', old, new)
                with self.assertRaises(ValueError):
                    fishing.build_proposal(self.fixture)

    def test_small_net_section_must_supply_fishing_output(self):
        page = 'Fishing spot (small net, bait)'
        for old, new in [('==Small net==', '==Other method=='),
                         ('{{DropsTableHead|dropversion=Small net}}', '{{DropsTableHead|dropversion=Bait}}'),
                         ('name=Raw shrimps|quantity=1|rarity=Common|skill=Fishing',
                          'name=Raw shrimps|quantity=1|rarity=Common|skill=Combat')]:
            with self.subTest(new=new):
                self.fixture = fishing.load_fixture()
                self.replace(page, old, new)
                with self.assertRaises(ValueError):
                    fishing.build_proposal(self.fixture)

    def test_adjacent_fish_and_bait_table_do_not_expand_reviewed_output(self):
        before = fishing.build_proposal(self.fixture)
        self.replace('Fishing spot (small net, bait)', 'name=Raw sardine', 'name=Raw shrimps')
        after = fishing.build_proposal(self.fixture)
        self.assertEqual(before['records'], after['records'])
        self.assertEqual([317], [r['itemId'] for r in after['records']])

    def test_exact_location_membership_variant_and_context_are_required(self):
        for old, new in [('|location = [[Lumbridge Swamp]]', '|location = [[Draynor Village]]'),
                         ('|location = [[Lumbridge Swamp]]\n|members = No',
                          '|location = [[Lumbridge Swamp]]\n|members = Yes'),
                         ('|dropversion=Small net, Bait', '|dropversion=Bait'),
                         ('|x:3246,y:3155', '|x:3246,y:3155,qty:5')]:
            with self.subTest(new=new):
                self.fixture = fishing.load_fixture()
                self.replace('Fishing spot (small net, bait)', old, new)
                with self.assertRaises(ValueError):
                    fishing.build_proposal(self.fixture)
        self.fixture = fishing.load_fixture()
        self.replace('Raw shrimps', 'near the [[Fishing tutor]]', 'near a bank')
        with self.assertRaisesRegex(ValueError, 'location/context'):
            fishing.build_proposal(self.fixture)

    def test_unrelated_runtime_data_and_previous_record_are_exactly_preserved(self):
        previous = mining(load_mining())['records']
        fishing.check_unchanged(self.fixture, previous)
        changed = copy.deepcopy(previous)
        changed[0]['notes'] += ' altered'
        with self.assertRaisesRegex(ValueError, 'Earlier gathering records'):
            fishing.check_unchanged(self.fixture, changed)
        self.fixture['unchangedResourceSha256']['wiki_shop_sources.json'] = 'wrong'
        with self.assertRaisesRegex(ValueError, 'Unrelated runtime resource'):
            fishing.check_unchanged(self.fixture, previous)

    def test_missing_malformed_or_replaced_fixture_never_synthesizes_a_method(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'fixture.json'
            with patch.object(fishing, 'FIXTURE', path):
                with self.assertRaises(FileNotFoundError):
                    fishing.load_fixture()
                path.write_text('{broken', encoding='utf-8')
                with self.assertRaises(json.JSONDecodeError):
                    fishing.load_fixture()
                path.write_text('{}', encoding='utf-8')
                with self.assertRaisesRegex(ValueError, 'explicit review'):
                    fishing.load_fixture()

    def test_revision_changes_provenance_without_changing_stable_identity(self):
        before = fishing.build_proposal(self.fixture)
        self.fixture['sources']['Raw shrimps']['revisionId'] += 1
        after = fishing.build_proposal(self.fixture)
        self.assertEqual(before['records'], after['records'])
        self.assertNotEqual(before['provenance'], after['provenance'])


if __name__ == '__main__':
    unittest.main()
