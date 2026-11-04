"""One reviewed ordinary-tree Logs method; independent of Ent harvesting."""
import hashlib
import json
from pathlib import Path

from build_ground_sources import digest, require, section_at
from build_fishing_sources import reviewed_template

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tools/fixtures/woodcutting_sources.json'
MANIFEST = ROOT / 'docs/data/woodcutting_sources.json'
FIXTURE_PIN = '501fcc37433ffc8516b4d1ceea9af1562c847132caa1abf07a93fae40bc207ae'
LOCATION_EVIDENCE = ('Some more popular spots to cut regular trees are near the bank in [[Draynor Village]], '
                     'as well as in the areas surrounding the [[Grand Exchange]] and [[Varrock West Bank]].')


def load_fixture():
    fixture = json.loads(FIXTURE.read_text(encoding='utf-8'))
    require(digest(fixture) == FIXTURE_PIN, 'Reviewed Woodcutting evidence or scope changed; explicit review required')
    return fixture


def build_proposal(fixture):
    require(set(fixture['sources']) == {'Logs', 'Tree'}, 'Woodcutting source inventory changed')
    require(fixture['review'] == {'itemId': 1511, 'itemName': 'Logs', 'skill': 'Woodcutting', 'level': 1,
                                 'tool': 'Axe', 'resource': 'Tree', 'location': 'Draynor Village — near the bank'},
            'Approved Woodcutting scope changed')
    item, resource = [fixture['sources'][key] for key in ['Logs', 'Tree']]
    box = reviewed_template(item, 'infobox item', [])[3]
    require(box.get('id') == '1511' and box.get('name') == 'Logs', 'Canonical Logs identity changed')
    scenery = reviewed_template(resource, 'infobox scenery', [])[3]
    require(scenery.get('name') == 'Tree' and scenery.get('options') == 'Chop down', 'Ordinary-tree interaction changed')
    start, end, _, skill, positional = reviewed_template(resource, 'woodcutting info', ['Woodcutting info'])
    require(not positional and set(skill) == {'name', 'level', 'xp', 'tool', 'time'}, 'Unreviewed Woodcutting field')
    require(skill['name'] == 'Tree', 'Woodcutting resource identity changed')
    require(skill['level'] == '1' and skill['tool'] == '[[Axe]]', 'Woodcutting requirement or tool changed')
    skill_raw = resource['wikitext'][start:end]
    output = reviewed_template(resource, 'dropslineskill', ['Woodcutting info'], lambda p: p.get('name') == 'Logs')[3]
    require(output == {'name': 'Logs', 'quantity': '1', 'rarity': 'Always', 'skill': 'Woodcutting'},
            'Ordinary-tree output association changed')
    require(LOCATION_EVIDENCE in resource['wikitext'], 'Reviewed ordinary-tree location prose changed')
    require(section_at(resource['wikitext'], resource['wikitext'].index(LOCATION_EVIDENCE)) == ['Tree locations'],
            'Location prose is outside the reviewed section')
    identity = {'outputItemId': 1511, 'kind': 'GATHERING', 'skill': 'Woodcutting',
                'resourcePage': 'Tree', 'interactionVariant': 'Ordinary tree'}
    method_id = digest(identity)[:20]
    occurrence_identity = {'methodId': method_id, 'location': fixture['review']['location'],
                           'coordinates': None, 'plane': None, 'rawMapId': None}
    method = dict(identity, methodId=method_id, actionLabel='Chop ordinary trees',
                  output={'itemId': 1511, 'itemName': 'Logs', 'quantity': None, 'quantityBasis': 'not asserted'},
                  skills=[{'skill': 'Woodcutting', 'level': 1, 'boostable': None}],
                  tools=[{'kind': 'CATEGORY', 'name': 'Axe', 'itemId': None}],
                  membersOnly=None, accessCoverage='incomplete', verifiedConditions=[])
    occurrence = dict(occurrence_identity, occurrenceId=digest(occurrence_identity)[:20], membersOnly=None,
                      resourceCount=None, resourceCountBasis='not asserted; named area from prose only',
                      accessCoverage='incomplete', verifiedConditions=[], contextNotes=[])
    record = {'id': occurrence['occurrenceId'], 'itemId': 1511, 'itemName': 'Logs', 'sourceType': 'Gathering',
              'sourceName': method['actionLabel'], 'location': occurrence['location'], 'cost': '',
              'notes': 'Woodcutting level 1 · Axe required'}
    provenance = [{k: source[k] for k in ['title', 'revisionId', 'sourceUrl', 'historyUrl', 'rawSnapshotSha256']}
                  | {'wikitextSha256': hashlib.sha256(source['wikitext'].encode()).hexdigest()}
                  for source in (item, resource)]
    return {'baseCommit': fixture['baseCommit'], 'records': [record], 'method': method, 'occurrence': occurrence,
            'provenance': provenance, 'rawSkillTemplate': skill_raw, 'rawLocationProse': LOCATION_EVIDENCE,
            'limitations': ['One ordinary-tree method and named area only; other tree locations exist.',
                           'No coordinates, active tree counts, location membership, rates or quantities asserted.',
                           'Independent of Ent combat/harvest conditions and all other tree/log families.',
                           'No XP, timers, double-yield, banking/training advice, ranking or account eligibility.']}


def check_unchanged(fixture, previous_records):
    require(previous_records == fixture['previousGatheringRecords'], 'Earlier Mining/Fishing records changed')
    for name, expected in fixture['unchangedFileSha256'].items():
        require(hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == expected, 'Unrelated file changed: ' + name)
