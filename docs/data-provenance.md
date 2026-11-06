# Data provenance

All paths below are relative to the repository root. The original snapshot sources
are listed below; subsequent bounded changes are recorded in
[reviewed acquisition corrections](acquisition-corrections.md). See
[third-party notices](../THIRD_PARTY_NOTICES.md) for source rights and adaptation
terms, and [license review](license-review.md) for license scope and the disclosed price API terms distinction.

| File | Origin and transformation | Evidence and limitation |
|---|---|---|
| src/main/resources/wiki_gathering_sources.json | Reviewed Iron ore Mining, Raw shrimps Fishing and ordinary-tree Logs Woodcutting | Separate pinned fixtures and manifests for each activity; tools/build_gathering_sources.py checks all three offline; limited named-location coverage, no inferred rates/eligibility |
| tools/fixtures/gathering_sources.json, fishing_sources.json, woodcutting_sources.json | Saved Wiki item/resource/location revisions | Full wikitext and snapshot/revision hashes; not shipped in the plugin; same Wiki attribution and terms; derived provenance in docs/data/ |
| src/main/resources/zenyte_production.json | Two reviewed creation recipes: Zenyte cutting and Uncut zenyte fusion | Zenyte revision 15357771 and Uncut zenyte revision 15218231; raw articles and rendered quantities pinned in tools/fixtures/zenyte_production.json; build_zenyte_production.py checks both; existing shard drops unchanged |
| src/main/resources/acquisition_events.json | Reviewed Ent harvest and Dragon impling ordinary-loot events | Revision-pinned associations; full 19-outcome loot table separate from lookup coverage; tools/validate_acquisition_events.py checks pinned text and exact source/location identities |
| tools/fixtures/acquisition_event_evidence.json | Ent, Dragon impling, Dragon impling jar and Impling Wiki revisions | Offline wikitext/SHA evidence, not shipped in the plugin; same Wiki attribution and terms |
| src/main/resources/wiki_monster_drops.json | OSRS Wiki monster pages; tools/build_wiki_drops.py parses and normalizes drop tables | monsterName is the source page; old revision IDs were not recorded |
| src/main/resources/wiki_shop_sources.json | OSRS Wiki shop tables/infoboxes; tools/build_shop_sources.py plus reviewed corrections | sourceName is the source page; old revision IDs were not recorded; a fresh generator run alone can lose corrections |
| src/main/resources/monster_locations.json | OSRS Wiki location/access text; tools/build_monster_locations.py plus exact overrides | sourceUrl/revisionId per location; docs/data/monster_locations_report.json revision manifest |
| src/main/resources/item_packs.json | Manually reviewed Wiki pack contents and costs | packName, wikiRevision and verifiedOn per pack |
| src/main/resources/potion_families.json | Curated Wiki potion-category/page names | source and verifiedOn; per-page revisions not retained |
| src/main/resources/item_sources.json | Curated Wiki reward facts and original explanatory notes | Rune pouch, Slayer Rewards, Justine's shop, Mage Training Arena Rewards, Grace/Graceful hood; see bundled source links |
| src/main/resources/ge_prices.json | Wiki prices API mapping/latest responses; tools/build_ge_prices.py selects high price or low fallback | Snapshot committed 2026-06-02; original fetch timestamp absent; API redistribution-license limitation documented |
| tools/data/wiki_drops_failed_pages.json | Wiki drop generation diagnostics | Moved unchanged out of runtime resources |
| tools/data/wiki_drops_monster_pages.json | Wiki monster category worklist | Moved unchanged; supplies source page titles |
| tools/data/wiki_drops_skipped_report.json | Wiki extraction failures and fragments | Moved unchanged; extracted material retains Wiki terms |
| tools/data/wiki_drops_zero_record_pages.json | Wiki pages without parsed records | Moved unchanged |
| tools/data/wiki_shop_sources_report.json | Wiki shop generation diagnostics/sample rows | Moved unchanged; extracted material retains Wiki terms |
| tools/data/monster_location_overrides.json | Reviewed location classifications and adapted access notes | Source links in each override |
| docs/data/monster_locations_report.json | Wiki location generator diagnostic and revision manifest | Generated alongside locations, with coverage gaps explicitly counted |
| src/main/resources/data_sources.txt | Generated page URLs for attribution, not fresh data validation | Original records/worklist, locations, packs, potion and curated reward sources |
| src/main/resources/LICENSE.txt | Copy of the original-code BSD 2-Clause license | Third-party resources retain separate terms |
| src/main/resources/THIRD_PARTY_NOTICES.txt | Runtime-focused third-party notices | Ships in standard Hub artifact without a custom build step; root notices additionally cover test-only assets |
| src/main/resources/monster_locations_notice.txt | Location-specific credit pointing to complete notices | Retained for existing location documentation |

## Code, helpers and assets

`src/main/java`, `src/test/java` and the Python tools are project implementations
and tests; they use external APIs/libraries by dependency rather than embedding
those libraries. The initial plugin/config/test scaffold and Gradle project
structure descend from the RuneLite public-domain template. Current bundled
assets also include reviewed monster portraits, the Atkinson Hyperlegible font
and the maintainer-supplied Obtainable satchel artwork. Their sources, licenses
and attribution are recorded in the third-party notices and bundled artwork/font
notices; they are not covered by the original-code license merely because they
ship with the plugin. This records the available Git/source evidence, not an
independent authorship guarantee for all historical contributions.

`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` are upstream Gradle
wrapper tooling under Apache-2.0. Their original notices and license are retained.
The standard plugin JAR does not include the wrapper, development test classes,
Python tools, reports, RuneLite libraries or OSRSBox files.

The previous OSRSBox input/output/converter remain in historical commits and in
the local recovery checkpoint. They are not used to generate the active Wiki
drop snapshot. Their removal does not relicense prior copies. See historical
OSRSBox terms in the notices and licenses directory.
