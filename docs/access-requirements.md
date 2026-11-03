# Scoped access requirements

`access_requirements.json` is a small, manually reviewed factual sidecar. It does not alter or filter drop sources, location bindings, or account eligibility. Missing information means unknown, never unrestricted. The first version covers twelve facts across Abyssal demon, Fire giant at Waterfall Dungeon, Kraken, Vorkath and Great white shark. It is intentionally incomplete.

Each fact identifies an exact monster page and one scope: MONSTER, LOCATION (with an existing locationId), or ITEM (with an exact itemName). No parent-monster or fuzzy-name fallback exists. `appliesTo` distinguishes COMBAT, ACCESS, TASK_ELIGIBILITY, ITEM_DROP, ENCOUNTER_CONTEXT and ACTIVITY_CONTEXT. The readable text retains conditional alternatives. `type` describes the kind of fact; SKILL additionally includes skill and level. None of these fields form an executable rule engine.

Evidence contains page, revisionId, and a revision-pinned OSRS Wiki URL. The dataset includes checkedAt and attribution. Summaries are adaptations of OSRS Wiki contributor content under CC BY-NC-SA 3.0; see THIRD_PARTY_NOTICES.txt. Click a detail fact to open its first evidence page; its tooltip lists all evidence references.

Only exact monster-wide combat requirements and exact item-drop conditions appear on source cards. Assignment eligibility and encounter/activity context are labelled separately in the scrollable detail body. Location restrictions stay within their specific location card. These facts are context, not proof that an unverified item/location pairing is valid.

## Location identity and regeneration

The existing location builder hashes the complete normalized location record plus coordinate tokens. IDs are deterministic for unchanged input, but can change when a Wiki revision or other included field changes. They are not permanent semantic IDs. Never silently rebind by a similar location name. Run `python tools/validate_access_requirements.py` after any location/drop regeneration: a dangling ID or mismatched monster fails validation and requires source review. Java regression tests also exercise the reviewed location targets.

The requirements validator rejects unknown fields (including unsupported variant selectors), scopes, missing provenance, wrong revision links, invalid skill metadata, duplicate IDs, dangling locations and unknown monster/item pairs. Version-specific requirements must not be added as generic monster requirements. The Vorkath record explicitly describes post-quest repeatability as context because its existing mixed-level Ungael row has no verified drop bindings.

## Verification

With Java 11: `gradlew.bat test build`. With Python: `python -m unittest discover -s tools -p "test_*.py"`, followed by `validate_monster_locations.py`, `validate_item_sources.py`, and `validate_access_requirements.py` from tools.

Focused cases: Abyssal demon 85 Slayer without Sire/superior inheritance; cave-specific task without Catacombs/Ankou inheritance; Waterfall amulet and rope alternatives; Kraken combat gates versus 50 Magic task eligibility; Vorkath's unverified encounter mapping; Great white shark's 75 Sailing bounty eligibility and exact jaw/liver drop conditions. Existing tests retain Back, query/filter/scroll state and exact drop/location bindings.

Future scaling should begin with explicit unversioned Infobox Monster Slayer levels. Quest prose, variant fields, item conditions, sea routes and boat requirements need additional evidence and scoped binding. A number appearing on a page is insufficient evidence for a universal combat restriction.
