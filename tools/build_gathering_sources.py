"""Offline, reviewed Iron ore gathering slice: one method at one mine.

Default writes a proposal. --apply creates the resource/manifest once; --check
compares both and all nine earlier JSON resources. No automatic coverage crawl.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

from build_monster_locations import templates, clean, boolean, access_notes, reference_definitions
from build_ground_sources import digest, require, section_at

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources'
RESOURCE = RESOURCES / 'wiki_gathering_sources.json'
MANIFEST = ROOT / 'docs/data/gathering_sources.json'
FIXTURE = ROOT / 'tools/fixtures/gathering_sources.json'
FIXTURE_PIN = '287df1df3816d308d5672b36fe7e3e500bcb5ac65e35294da9f24eda8844d98a'


def load_fixture():
    fixture = json.loads(FIXTURE.read_text(encoding='utf-8'))
    require(digest(fixture) == FIXTURE_PIN, 'Reviewed source or allowlist changed; explicit review required')
    return fixture


def one_template(source, template, predicate=lambda p: True):
    matches = [t for t in templates(source['wikitext']) if t[2] == template and predicate(t[3])]
    require(len(matches) == 1, 'Expected one reviewed ' + template + ' template')
    return matches[0]


def read_acquisition_info(source, template):
    # Uses/Products and Firemaking consume the queried item. They are never
    # acquisition adapters. Only the reviewed Mining adapter is enabled here.
    require(template == 'mining info', 'Not an approved acquisition template')
    start, end, _, params, positional = one_template(source, template)
    require(section_at(source['wikitext'], start) == ['Mining info'], 'Not a reviewed acquisition section')
    require(not positional and set(params) == {'name', 'level', 'xp', 'rock', 'tool', 'time'},
            'Unexpected skill field; review required')
    require(params['name'] == 'Iron rocks' and params['rock'] == '[[Iron rocks]]', 'Resource identity changed')
    require(params['level'] == '15', 'Required Mining level changed')
    require(params['tool'] == '[[Pickaxe]]', 'Required tool changed')
    return {'skill': 'Mining', 'level': int(params['level']), 'boostable': None}, source['wikitext'][start:end]


def build_proposal(fixture):
    sources, review = fixture['sources'], fixture['review']
    require(set(sources) == {'Iron ore', 'Iron rocks', 'South-west Varrock mine', 'Logs'}, 'Source inventory changed')
    require(review['itemId'] == 440 and review['itemName'] == 'Iron ore'
            and review['skill'] == 'Mining' and review['level'] == 15 and review['toolCategory'] == 'Pickaxe'
            and review['resource'] == 'Iron rocks' and review['location'] == 'South-west Varrock mine'
            and review['membersOnly'] is False and review['resourceCount'] == 3,
            'Approved scope changed')
    item, resource, mine = [sources[k] for k in ['Iron ore', 'Iron rocks', review['location']]]
    box = one_template(item, 'infobox item')[3]
    require(box.get('id') == '440' and box.get('name') == 'Iron ore', 'Canonical output identity changed')
    requirement, skill_raw = read_acquisition_info(item, review['template'])
    require(hashlib.sha256(skill_raw.encode()).hexdigest() == review['skillTemplateSha256'], 'Skill template changed')
    scenery = one_template(resource, 'infobox scenery')[3]
    require(scenery.get('name') == 'Iron rocks' and scenery.get('options') == 'Mine', 'Resource interaction changed')
    output = one_template(resource, 'dropslineskill', lambda p: p.get('name') == 'Iron ore')[3]
    require(output.get('skill') == 'Mining', 'Resource does not yield the queried item through Mining')
    a, b, _, params, positions = one_template(resource, 'objectlocline',
            lambda p: clean(p.get('location', '')) == review['location'])
    require(section_at(resource['wikitext'], a) == ['Locations'], 'Unreviewed resource location section')
    require(set(params) == {'name', 'location', 'members', 'mapid', 'mtype', 'leagueregion'},
            'Unexpected location field or count override')
    require(params['name'] == review['resource'], 'Location resource changed')
    require(boolean(params['members']) is False, 'Location membership changed or unknown')
    unknown = set()
    location = clean(params['location'], unknown)
    restrictions = access_notes(params['location'], reference_definitions(resource['wikitext']), unknown)
    require(not unknown and not restrictions, 'Unreviewed location restriction or markup')
    require(params['location'] == '[[South-west Varrock mine]]', 'Location identity changed')
    points = []
    for position in positions:
        match = re.fullmatch(r'x:(\d+),y:(\d+)', position)
        require(match is not None, 'Unreviewed coordinate or quantity annotation')
        points.append({'x': int(match[1]), 'y': int(match[2])})
    require(len(points) == 3 and len({(p['x'], p['y']) for p in points}) == 3, 'Rock count changed')
    location_raw = resource['wikitext'][a:b]
    require(hashlib.sha256(location_raw.encode()).hexdigest() == review['locationTemplateSha256'], 'Location template changed')
    mine_box = one_template(mine, 'infobox location')[3]
    require(mine_box.get('name') == location and boolean(mine_box.get('members', '')) is False, 'Mine identity or membership changed')
    require('3 [[iron rocks]]' in mine['wikitext'], 'Independent mine count changed')
    require('An aggressive level-6 [[mugger]] hangs around the mine' in mine['wikitext'], 'Hazard evidence changed')
    require(review['contextNote'] == 'Caution: aggressive mugger nearby.', 'Unreviewed contextual advice')

    method_identity = {'outputItemId': 440, 'kind': 'GATHERING', 'skill': 'Mining',
                       'resourcePage': 'Iron rocks', 'interactionVariant': 'Standard'}
    method_id = digest(method_identity)[:20]
    occurrence_identity = {'methodId': method_id, 'location': location, 'coordinates': points,
                           'plane': None, 'rawMapId': params['mapid']}
    method = dict(method_identity, methodId=method_id, actionLabel='Mine iron rocks',
                  output={'itemId': 440, 'itemName': 'Iron ore', 'quantity': None, 'quantityBasis': 'not asserted'},
                  skills=[requirement], tools=[{'kind': 'CATEGORY', 'name': review['toolCategory'], 'itemId': None}],
                  membersOnly=None, accessCoverage='incomplete', verifiedConditions=[])
    occurrence = dict(occurrence_identity, occurrenceId=digest(occurrence_identity)[:20], membersOnly=False,
                      resourceCount=len(points), resourceCountBasis='Wiki points corroborated by mine page',
                      accessCoverage='incomplete', verifiedConditions=[],
                      contextNotes=[{'kind': 'HAZARD', 'text': review['contextNote'], 'eligibilityGate': False}])
    # Only these derived strings enter the existing ItemSource presentation. The
    # typed facts remain independent of wording and never evaluate an account.
    record = {'id': occurrence['occurrenceId'], 'itemId': method['output']['itemId'],
              'itemName': method['output']['itemName'], 'sourceType': 'Gathering',
              'sourceName': method['actionLabel'], 'location': occurrence['location'], 'cost': '',
              'notes': 'Mining level ' + str(requirement['level']) + ' · ' + method['tools'][0]['name'] + ' required\n'
                       + str(occurrence['resourceCount']) + ' iron rocks · F2P\n' + occurrence['contextNotes'][0]['text']}
    provenance = []
    for source in (item, resource, mine):
        provenance.append({k: source[k] for k in ['title', 'revisionId', 'sourceUrl', 'historyUrl', 'rawSnapshotSha256']}
                          | {'wikitextSha256': hashlib.sha256(source['wikitext'].encode()).hexdigest()})
    return {'baseCommit': fixture['baseCommit'], 'records': [record], 'method': method, 'occurrence': occurrence,
            'provenance': provenance, 'rawSkillTemplate': skill_raw, 'rawLocationTemplate': location_raw,
            'limitations': ['One item, method and named mine only; other locations exist.',
                           'Access requirements not fully checked; no account eligibility inference.',
                           'No output quantity, rates, timers, ranking, navigation or ingredient expansion.',
                           'Logs fixture is a negative acquisition-direction test, not generated coverage.']}


def run(apply=False, check=False, output=None):
    fixture = load_fixture()
    proposal = build_proposal(fixture)
    for name, expected in fixture['unchangedResourceSha256'].items():
        require(digest(json.loads((RESOURCES / name).read_text(encoding='utf-8'))) == expected,
                'Existing resource changed: ' + name)
    if check:
        require(json.loads(RESOURCE.read_text(encoding='utf-8')) == proposal['records'], 'Runtime gathering records differ')
        require(json.loads(MANIFEST.read_text(encoding='utf-8')) == proposal, 'Gathering provenance differs')
    elif apply:
        require(not RESOURCE.exists() and not MANIFEST.exists(), 'Slice already exists; use --check')
        RESOURCE.write_text(json.dumps(proposal['records'], ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        MANIFEST.write_text(json.dumps(proposal, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    else:
        output = output or ROOT / '.cache/gathering_sources_proposal.json'
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(proposal, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'records': 1, 'rocks': 3, 'unchangedResources': 9,
                      'mode': 'check' if check else 'apply' if apply else 'proposal'}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--check', action='store_true')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    run(args.apply, args.check, args.output)
