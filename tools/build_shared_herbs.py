"""Reproduce the reviewed 44-row shared-herb slice offline; never mass-regenerate.

Default: write a proposal. --apply accepts the exact reviewed starting dataset.
--check verifies the complete applied result without writing. See docs/shared-herbs.md.
"""
import argparse
from collections import Counter, defaultdict
from copy import deepcopy
from decimal import Decimal, ROUND_HALF_UP
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import re

from build_monster_locations import drop_key, norm, parse_locations, templates, without_comments

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tools/fixtures/shared_herbs.json'
RESOURCES = ROOT / 'src/main/resources'
MANIFEST = ROOT / 'docs/data/shared_herb_expansion.json'
PINS = {
    'Man': '0f10005db5e9151b96047326ca115e976cb81ab4f718821fbf071376f5e03cc6',
    'Abyssal demon': '0f6ded68855a8c9e3ca8909415676428a7e32c247685d36b080c61a3eb3425be',
    'Goblin': '8fb8418f7779c41875537ba3df38f15f6d05306f897844fc6bd185234145a327',
    'Template:Human drop table': '872f3b54aafcea39850eea2399c9d1a7717451d547df6ef33a7ec42862ff5026',
    'Template:HerbDropLines': '0c7d83d89b77e7b747e7fc82e82c3243de0c0f45d784ea35722a8c9b3bf67228',
}
EXPECTED_CALLS = {
    'Man': [('', '23/128', True)],
    'Abyssal demon': [('Standard', '19/128', False), ('Wilderness Slayer Cave', '19/68', False)],
    'Goblin': [('Drop table 2', '2/128', True)],
}
EXPECTED_WEIGHTS = [32, 24, 18, 14, 11, 8, 6, 5, 4, 3, 3]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()


def load_fixture():
    fixture = json.loads(FIXTURE.read_text(encoding='utf-8'))
    require(digest(fixture) == '84c93e6f73c24e9ad1805ceb024ce5cf579b48b5966127ac5288e34a26210448',
            'Reviewed fixture or baseline changed; explicit review required')
    require(set(fixture['sources']) == set(PINS), 'Unexpected source inventory')
    for title, pin in PINS.items():
        require(digest(fixture['sources'][title]) == pin, f'Pinned source changed: {title}; review before updating')
    return fixture


def herb_weights(text):
    require(text.startswith('<includeonly>{{#vardefine:herbbase|{{#expr:{{{1}}}/128}}}}'), 'Unsupported herb base expression')
    weights = re.findall(r'{{DropsLine\|name=(Grimy [^|]+)\|.*?\|rarity=1/{{#expr:1/\((\d+)\*{{#var:herbbase}}\) round 1}}', text)
    require(len(weights) == text.count('{{DropsLine|') == 11, 'Expected exactly 11 herb rows')
    require([int(w) for _, w in weights] == EXPECTED_WEIGHTS, 'Unexpected herb weights')
    require(len({name for name, _ in weights}) == 11, 'Duplicate herb identity')
    require(sum(int(w) for _, w in weights) == 128, 'Herb weights do not sum to 128')
    return [(name, int(weight)) for name, weight in weights]


def find_calls(title, sources, path=()):
    require(title not in path, 'Shared-table cycle')
    source = sources[title]
    text = without_comments(source['wikitext'])
    if title.startswith('Template:'):
        require('<includeonly>' in text and '</includeonly>' in text, 'Missing template include boundary')
        text = text.split('<includeonly>', 1)[1].split('</includeonly>', 1)[0]
    context = None
    calls = []
    for start, end, name, params, positional in templates(text):
        if name == 'dropstablehead':
            context = params.get('dropversion', '')
        elif name == 'dropstablebottom':
            context = None
        elif name == 'human drop table':
            require(title == 'Man' and not params and not positional, 'Unsupported Human transclusion')
            calls.extend(find_calls('Template:Human drop table', sources, path + (title,)))
        elif name == 'herbdroplines':
            require(context is not None, 'Herb call outside a recognised drop table')
            require(not re.search(r'\|\s*\d+\s*=', text[start:end]), 'Explicit numbered parameters require review')
            require(set(params) <= {'f2p'}, 'Unsupported herb argument or rolls')
            require(1 <= len(positional) <= 2 and (len(positional) == 1 or positional[1] == '1'), 'Unsupported herb quantity')
            require(re.fullmatch(r'[1-9]\d*/[1-9]\d*', positional[0]) is not None, 'Unsupported entry probability')
            require(0 < Fraction(positional[0]) <= 1, 'Invalid entry probability')
            require(params.get('f2p', 'yes') == 'yes', 'Unexpected F2P parameter')
            calls.append({'path': list(path + (title, 'Template:HerbDropLines')), 'offset': start,
                          'dropVersion': context, 'entry': positional[0], 'f2pReplacement': 'f2p' in params})
    return calls


def rate_text(probability):
    reciprocal = Decimal(probability.denominator) / Decimal(probability.numerator)
    rounded = reciprocal.quantize(Decimal('0.1'), rounding=ROUND_HALF_UP)
    return '1/' + format(rounded, 'f').removesuffix('.0')


def checked_locations(monster, source, dataset):
    overrides = json.loads((ROOT / 'tools/data/monster_location_overrides.json').read_text(encoding='utf-8'))
    rules = {(norm(r.get('monsterName', '')), norm(r['location'])): r for r in overrides['locations']}
    issues = defaultdict(list)
    parsed = parse_locations(monster, source, source['wikitext'], rules, issues)
    require(not any(issues.values()), f'Location extraction requires review: {monster}: {dict(issues)}')
    existing = [r for r in dataset['locations'] if r['monsterName'] == monster]
    require(sorted(parsed, key=lambda r: r['id']) == sorted(existing, key=lambda r: r['id']),
            f'Current source/location evidence differs: {monster}; do not rebind approximately')
    return existing


def build_proposal(fixture, base_drops, base_locations):
    require(digest(base_drops) == fixture['baseDropSha256'], 'Starting drop dataset differs from reviewed checkpoint')
    require(digest(base_locations) == fixture['baseLocationSha256'], 'Starting location dataset differs from reviewed checkpoint')
    sources = fixture['sources']
    weights = herb_weights(sources['Template:HerbDropLines']['wikitext'])
    records, bindings, provenance = [], [], []
    path_ids, signatures = set(), {drop_key(r) for r in base_drops}
    base_pairs = {(r['monsterName'], r['itemName']) for r in base_drops}
    for monster, expected in EXPECTED_CALLS.items():
        calls = find_calls(monster, sources)
        require([(c['dropVersion'], c['entry'], c['f2pReplacement']) for c in calls] == expected,
                f'Unexpected herb call structure: {monster}')
        locations = checked_locations(monster, sources[monster], base_locations)
        rendered = sources[monster]['renderedHerbs']
        require(len(rendered) == len(calls) * 11, f'Unexpected rendered herb coverage: {monster}')
        for call_index, call in enumerate(calls):
            version = call['dropVersion']
            eligible = [r for r in locations if version in r['dropVersions']] if version else locations
            require(eligible and (version or all(not r['dropVersions'] for r in locations)), 'Unresolved location variant')
            for item_index, (item, weight) in enumerate(weights):
                probability = Fraction(call['entry']) * Fraction(weight, 128)
                record = {'itemName': item, 'monsterName': monster, 'quantity': '1', 'dropRate': rate_text(probability),
                          'notes': 'Members-world drop.' + (f' {version} only.' if version else ''), 'membersOnly': True}
                row = rendered[call_index * 11 + item_index]
                require((row['itemName'], row['quantity'], row['dropRate'].replace(',', ''), row['membersMarker']) ==
                        (item, '1', record['dropRate'], call['f2pReplacement']), f'Rendered rate/quantity/condition mismatch: {monster}/{item}')
                path_id = digest([monster, call['path'], call['offset'], version, item])
                require(path_id not in path_ids, 'Duplicate acquisition path')
                require(drop_key(record) not in signatures, 'Duplicate acquisition signature')
                require((monster, item) not in base_pairs, 'Existing direct/shared item collision requires review')
                path_ids.add(path_id)
                signatures.add(drop_key(record))
                records.append(record)
                binding = {k: record[k] for k in ['monsterName', 'itemName', 'quantity', 'dropRate', 'notes']}
                binding.update(locationIds=sorted(r['id'] for r in eligible), dropVersions=[version] if version else [],
                    matchMethods=['explicit-variant' if version else 'common-unversioned-table'],
                    evidence='Pinned shared-herb call and exact current location records; source requires a members world')
                bindings.append(binding)
                provenance.append({'pathId': path_id, 'record': record, 'call': call,
                    'sourceChain': [{'title': title, 'revisionId': sources[title]['revisionId'], 'sourceUrl': sources[title]['sourceUrl'],
                        'fixtureSha256': digest(sources[title]), 'rawSnapshotSha256': sources[title]['rawSnapshotSha256']} for title in call['path']],
                    'entryProbability': call['entry'], 'conditionalProbability': f'{weight}/128',
                    'effectiveProbability': str(probability), 'rateMeaning': 'One shared-table roll; Wiki-rounded reciprocal',
                    'quantity': '1', 'membersOnly': True, 'renderedCrossCheck': row, 'locationIds': binding['locationIds']})
    require(len(records) == len(bindings) == len(provenance) == 44, 'Expected exactly 44 approved rows')
    return {'baseCommit': fixture['baseCommit'], 'addedRecordCount': 44, 'addedBindingCount': 44,
            'records': records, 'bindings': bindings, 'provenance': provenance,
            'limitations': ['Only Man, Abyssal demon and Goblin herbs are expanded.',
                'No seed, multi-roll, quantity-range, replacement-coin or other shared-table expansion.',
                'Existing source world eligibility remains unknown unless explicitly recorded.',
                'Legacy full regeneration must reapply this separate slice and pass --check.']}


def expected_result(fixture, drops, locations, proposal):
    updated = deepcopy(locations)
    updated['dropBindings'].extend(proposal['bindings'])
    updated['summary']['verifiedDropBindings'] = len(updated['dropBindings'])
    updated['summary']['bindingMethodCounts'] = dict(Counter(method for b in updated['dropBindings'] for method in b['matchMethods']))
    return drops + proposal['records'], updated


def run(apply=False, check=False, output=None):
    fixture = load_fixture()
    drop_path, loc_path = RESOURCES/'wiki_monster_drops.json', RESOURCES/'monster_locations.json'
    actual_drops = json.loads(drop_path.read_text(encoding='utf-8'))
    actual_locations = json.loads(loc_path.read_text(encoding='utf-8'))
    base_drops = actual_drops[:fixture['baseDropCount']]
    base_locations = deepcopy(actual_locations)
    base_locations['dropBindings'] = base_locations['dropBindings'][:fixture['baseBindingCount']]
    base_locations['summary'] = fixture['baseLocationSummary']
    proposal = build_proposal(fixture, base_drops, base_locations)
    final_drops, final_locations = expected_result(fixture, base_drops, base_locations, proposal)
    for name, expected in fixture['unchangedResourceSha256'].items():
        require(digest(json.loads((RESOURCES/name).read_text(encoding='utf-8'))) == expected, f'Unrelated resource changed: {name}')
    if check:
        require(actual_drops == final_drops, 'Applied drops differ from exact approved append')
        require(actual_locations == final_locations, 'Applied locations/bindings differ from exact approved append')
        require(json.loads(MANIFEST.read_text(encoding='utf-8')) == proposal, 'Provenance manifest differs')
    else:
        if apply:
            require(actual_drops == base_drops and actual_locations == base_locations, 'Apply requires untouched baseline; use --check for applied data')
            # All validations precede writes. Neither locations nor old records are regenerated.
            drop_path.write_text(json.dumps(final_drops, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
            loc_path.write_text(json.dumps(final_locations, separators=(',', ':'), ensure_ascii=False)+'\n', encoding='utf-8')
            output = MANIFEST
        output = output or ROOT/'.cache/shared_herb_proposal.json'
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(proposal, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
    print(json.dumps({'records': 44, 'bindings': 44, 'unresolvedBindings': 0, 'mode': 'check' if check else 'apply' if apply else 'proposal'}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--apply', action='store_true')
    modes.add_argument('--check', action='store_true')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    run(args.apply, args.check, args.output)
