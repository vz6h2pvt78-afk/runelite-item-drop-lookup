import unittest

from build_gathering_sources import load_fixture, build_proposal, read_acquisition_info


class GatheringSourceTests(unittest.TestCase):
    def setUp(self):
        self.fixture = load_fixture()

    def replace(self, page, before, after):
        self.assertIn(before, self.fixture['sources'][page]['wikitext'])
        self.fixture['sources'][page]['wikitext'] = self.fixture['sources'][page]['wikitext'].replace(before, after)

    def rejected(self, pattern):
        with self.assertRaisesRegex(ValueError, pattern):
            build_proposal(self.fixture)

    def test_one_action_preserves_unknown_quantity_and_separate_hazard(self):
        p = build_proposal(self.fixture)
        self.assertEqual(1, len(p['records']))
        self.assertEqual(440, p['records'][0]['itemId'])
        self.assertEqual('Mine iron rocks', p['records'][0]['sourceName'])
        self.assertEqual(3, p['occurrence']['resourceCount'])
        self.assertFalse(p['occurrence']['membersOnly'])
        self.assertIsNone(p['method']['membersOnly'])
        self.assertIsNone(p['method']['output']['quantity'])
        self.assertIsNone(p['method']['skills'][0]['boostable'])
        self.assertEqual([{'skill': 'Mining', 'level': 15, 'boostable': None}], p['method']['skills'])
        self.assertFalse(p['occurrence']['contextNotes'][0]['eligibilityGate'])
        self.assertEqual([], p['occurrence']['verifiedConditions'])
        self.assertNotIn('Combat', p['records'][0]['notes'])
        self.assertNotIn('quantity', p['records'][0]['notes'].lower())

    def test_logs_firemaking_consumption_cannot_become_logs_acquisition(self):
        logs = self.fixture['sources']['Logs']
        self.assertIn('{{Firemaking info', logs['wikitext'])
        with self.assertRaisesRegex(ValueError, 'Not an approved acquisition template'):
            read_acquisition_info(logs, 'firemaking info')

    def test_replacing_acquisition_with_consumption_is_rejected(self):
        self.replace('Iron ore', '{{Mining info', '{{Firemaking info')
        self.rejected('Expected one reviewed mining info')

    def test_output_id_cannot_be_an_object_id(self):
        self.replace('Iron ore', '|id = 440', '|id = 11365')
        self.rejected('Canonical output identity')

    def test_skill_and_tool_changes_cannot_silently_keep_old_display(self):
        for old, new, message in [('|level = 15', '|level = 60', 'Mining level'),
                                  ('|tool = [[Pickaxe]]', '|tool = [[Axe]]', 'Required tool'),
                                  ('|rock = [[Iron rocks]]', '|rock = [[Coal rocks]]', 'Resource identity')]:
            with self.subTest(message=message):
                self.fixture = load_fixture()
                self.replace('Iron ore', old, new)
                self.rejected(message)

    def test_unreviewed_skill_field_is_not_ignored(self):
        self.replace('Iron ore', '|level = 15', '|level = 15\n|notes = Requires a quest')
        self.rejected('Unexpected skill field')

    def test_locations_cannot_be_silently_substituted(self):
        self.replace('Iron rocks', '|location = [[South-west Varrock mine]]', '|location = [[Mining Guild]]')
        self.rejected('Expected one reviewed objectlocline')

    def test_unknown_or_members_location_cannot_be_labelled_f2p(self):
        for value in ['', 'Yes']:
            with self.subTest(value=value):
                self.fixture = load_fixture()
                self.replace('Iron rocks', '|location = [[South-west Varrock mine]]\n|members = No',
                             '|location = [[South-west Varrock mine]]\n|members = ' + value)
                self.rejected('membership changed or unknown')

    def test_changed_rock_count_or_coordinate_quantity_needs_review(self):
        for replacement, message in [('x:3181,y:3373|x:3175,y:3368', 'Rock count'),
                                     ('x:3181,y:3373|x:3175,y:3368|x:3175,y:3366,qty:3', 'quantity annotation')]:
            with self.subTest(message=message):
                self.fixture = load_fixture()
                self.replace('Iron rocks', 'x:3181,y:3373|x:3175,y:3368|x:3175,y:3366', replacement)
                self.rejected(message)

    def test_count_override_and_new_restriction_are_not_silently_stripped(self):
        for replacement in ['[[South-west Varrock mine]]|spawns=3',
                             '[[South-west Varrock mine]]{{Refn|Requires a quest.}}']:
            with self.subTest(value=replacement):
                self.fixture = load_fixture()
                self.replace('Iron rocks', '|location = [[South-west Varrock mine]]', '|location = ' + replacement)
                self.rejected('Unexpected location field|restriction|Expected one reviewed')

    def test_independent_mine_count_and_hazard_must_remain_supported(self):
        for old,new,message in [('3 [[iron rocks]]', '4 [[iron rocks]]', 'Independent mine count'),
                                ('An aggressive level-6 [[mugger]]', 'A friendly level-6 [[mugger]]', 'Hazard evidence')]:
            with self.subTest(message=message):
                self.fixture = load_fixture()
                self.replace('South-west Varrock mine', old, new)
                self.rejected(message)

    def test_revision_updates_provenance_not_method_or_occurrence_identity(self):
        before = build_proposal(self.fixture)
        self.fixture['sources']['Iron ore']['revisionId'] += 1
        after = build_proposal(self.fixture)
        self.assertEqual(before['records'], after['records'])
        self.assertEqual(before['method']['methodId'], after['method']['methodId'])
        self.assertNotEqual(before['provenance'], after['provenance'])


if __name__ == '__main__':
    unittest.main()
