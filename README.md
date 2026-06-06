# RuneLite Item Drop Lookup

A RuneLite plugin for looking up Old School RuneScape item drop sources directly inside the RuneLite sidebar.

This plugin is currently in alpha. The core lookup system is working, but data coverage and polish are still being improved.

## Current Features

* Search for OSRS items by name
* View monster drop sources
* View drop rates and quantities
* View Grand Exchange prices for tradeable items
* Shows untradeable status when GE data is unavailable or marked untradeable
* Supports non-NPC sources for selected items
* Source type filtering
* Local JSON-based data files for fast lookup
* Wiki-generated monster drop dataset

## Current Data Sources

The plugin currently uses local JSON resources generated from OSRS Wiki data and supporting local data files.

Current resource files include:

* `wiki_monster_drops.json`
* `ge_prices.json`
* `item_sources.json`

The monster drop data is currently generated from OSRS Wiki monster pages. The current alpha dataset includes roughly 15,000 cleaned monster drop records.

### Files that are NOT runtime data

* `drops.json` — **not loaded by the plugin.** It is only written by
  `tools/convert_osrsbox_drops.py` (an offline OSRSBox converter) and is read by
  nothing at runtime. When debugging plugin search results, ignore `drops.json`
  and inspect `wiki_monster_drops.json`, which is the actual NPC-drop source
  (`DropLookupService` loads `/wiki_monster_drops.json`).
* The `wiki_drops_*.json` files (`wiki_drops_skipped_report.json`,
  `wiki_drops_failed_pages.json`, `wiki_drops_zero_record_pages.json`,
  `wiki_drops_monster_pages.json`) are build diagnostics written by
  `tools/build_wiki_drops.py`, not runtime lookup data.

## Known Alpha Limitations

This project is still early and some data may be incomplete or imperfect.

Known limitations:

* Clue scroll drop source coverage may be incomplete
* Some human NPCs, animal NPCs, or unusual NPC variants may be missing
* Some boss minion drop relationships may not be complete
* Non-monster sources are handled separately and are still being expanded
* Search aliases are basic and may not catch every slang or no-space search
* Wiki data parsing is still being improved

If an item does not show a source, it does not necessarily mean the item has no source. It may mean the source is not currently covered by the local dataset.

## Example Searches

Examples of working searches during alpha testing:

* `abyssal whip`
* `dragon boots`
* `bandos tassets`
* `zenyte shard`
* `brimstone key`
* `hydra's claw`
* `rune pouch`
* `graceful hood`
* `cowhide`
* `raw chicken`

## Development Status

This project is in alpha development.

Current goals:

1. Replace the old limited drop dataset with cleaned OSRS Wiki monster drop data
2. Preserve and expand non-NPC source lookup
3. Improve search matching and aliases
4. Improve clue scroll and unusual source coverage
5. Add better documentation for data generation tools
6. Continue improving generated data quality before public release

## Project Structure

```text
src/main/java/com/itemdroplookup
  DropLookupService.java
  DropSource.java
  ItemDropLookupConfig.java
  ItemDropLookupPanel.java
  ItemDropLookupPlugin.java
  ItemPrice.java
  ItemPriceLookupService.java
  ItemSource.java
  ItemSourceLookupService.java

src/main/resources
  wiki_monster_drops.json
  ge_prices.json
  item_sources.json

tools
  build_ge_prices.py
  build_wiki_drops.py
  convert_osrsbox_drops.py
```

## Building and Running

From the project root:

```powershell
.\gradlew clean run
```

The plugin can then be tested through RuneLite’s development client.

## Data Generation

GE price data can be generated with:

```powershell
py tools/build_ge_prices.py
```

Wiki monster drop data can be generated with:

```powershell
py tools/build_wiki_drops.py
```

Generated monster drop output is written to:

```text
src/main/resources/wiki_monster_drops.json
```

## Disclaimer

This is an unofficial RuneLite plugin project and is not affiliated with Jagex or the official Old School RuneScape team.

Old School RuneScape and OSRS are trademarks of Jagex Ltd.

Drop data and item information are based on OSRS Wiki data and local generated resources. Accuracy may vary during alpha development.
