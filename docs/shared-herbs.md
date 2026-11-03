# Reviewed shared-herb expansion

This separate offline generator adds 44 missing herb records: Man (11), Abyssal demon Standard (11), Abyssal demon Wilderness Slayer Cave (11), and Goblin Drop table 2 (11). It is deliberately bounded and is not a replacement for the legacy full drop generator. Seeds, variable quantities, repeated rolls, replacement coins and other shared tables remain unsupported.

`tools/fixtures/shared_herbs.json` contains attributed, revision-pinned source wikitext and extracted rendered rows. Source and fixture hashes are checked before generation. The complete research snapshots and feasibility report are preserved separately in the overnight handoff. `docs/data/shared_herb_expansion.json` records each acquisition path, dependency revisions/hashes, entry probability, conditional weight, exact rational probability, Wiki-rounded display, quantity, world condition and location IDs.

Run with Python 3.10+ from the repository root:

```text
python tools/build_shared_herbs.py --output path/to/proposal.json
python tools/build_shared_herbs.py --apply
python tools/build_shared_herbs.py --check
python -m unittest discover -s tools -p "test_*.py"
```

The default produces a proposal without changing runtime resources. Apply requires the exact reviewed checkpoint data and appends exactly 44 drops and 44 bindings. Check verifies the complete applied append, all prior rows/locations/bindings, the provenance manifest, and other JSON resource hashes. It is specific to this checkpoint; future unrelated data changes require explicit review of the baseline assertions. Never weaken an assertion merely to make regeneration pass.

The 11 herb weights are extracted from the pinned template and must match the reviewed sequence and sum to 128. Entry probability is multiplied by weight/128 exactly once. Rounded rendered rates are cross-checks, never additional multipliers. Unsupported call arguments, rolls, quantities, changed source shape, unexpected weights, collisions and missing location evidence stop generation. Repeated direct/shared item paths require review instead of silent deduplication.

Existing location rows are reproduced from the captured page and compared exactly, including revision and ID, before appending bindings. Abyssal variants remain separate; Goblin herbs exclude table-1-only locations. All existing identities and bindings are unchanged. The original location-generation report remains a historical baseline; the incremental manifest records the 44 added bindings and runtime summary counts are updated accordingly.

## World restrictions

An optional `Boolean membersOnly` on a drop is three-state: true means verified members-world-only, false explicitly verified F2P availability, null means not recorded. Only the 44 new records have true; no existing record is reclassified. The existing five-part location-binding identity is unchanged. World eligibility does not belong to the monster or its geographical location.

New cards show the members-world condition and their table variant. In details, F2P-only yields no acquisition locations for these sources, even after selecting all monster locations. Members filtering includes F2P-accessible areas such as Lumbridge on a members world. Unknown geography is still excluded under specific membership filters. Existing null records retain the prior location-filter behavior and receive an explicit world-availability caveat in F2P view; they are not newly asserted to be F2P drops.

## Attribution and limitations

Wiki source text, adapted data and fixtures: OSRS Wiki contributors, CC BY-NC-SA 3.0, with exact revision links in each fixture and provenance chain. Changes include extracting herb rows, rational arithmetic, adapted notes and explicit world/variant associations. See `src/main/resources/THIRD_PARTY_NOTICES.txt` and `licenses/`.

The legacy full drop/location generators do not automatically preserve this slice. After any deliberate full regeneration, reapply through a reviewed baseline and rerun the checks. The source hashes prevent unreviewed Wiki template changes from silently affecting runtime data. These are Wiki consistency checks, not game-engine measurements. In-game acceptance remains user-only.
