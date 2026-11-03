# Item Drop Lookup

Find OSRS item drops, shops and selected rewards from the RuneLite sidebar.

**Beta submission candidate.** This build has not been approved or released on
the RuneLite Plugin Hub. Source coverage is incomplete, and a missing result
does not mean that an item is unobtainable.

## Using the plugin

1. Open **Item Drop Lookup** in the sidebar and enter an item name.
2. Use the source filter to switch between NPC drops, shops and rewards. Buying
   options include verified packs, such as air rune packs and eye of newt packs.
3. Open an NPC card for known locations, combat levels and available access notes.
   **Back** keeps your search, filters and scroll position.
4. Use the Wiki links for missing details or to check a source before travelling.

Search potions by their ordinary name, such as `prayer potion`, to include all
available doses. Entering a specific dose still restricts that search. Potion
GE references use the four-dose item when one exists.

You can search within results for a monster, shop or shop location. NPC
Wilderness filters and detail membership filters retain an explicit unknown
state. League regions are not exposed as a filter. Shops with zero default
stock and known returned-item/resupply sources are excluded from acquisition
results.

## Data and limits

Lookups use bundled local snapshots. The plugin does not read account progress,
send player data, make background network requests, or perform game actions.
Wiki buttons open a page in your browser only when clicked.

- GE values are **snapshots, not live prices**. The current price file was added
  to this repository on 2 June 2026; its original fetch timestamp was not retained.
  Verify current prices before buying or selling.
- Slayer, Sailing, quest and task requirements are not comprehensively recorded.
  A listed source is not a guarantee that your character can access it.
- Fishing, other gathering, production, clues and shared drop tables have gaps.
- Monster locations are matched conservatively to drop variants. Unmatched rows
  link to the Wiki instead of borrowing another variant's locations.
- Some shop currencies and reward amounts remain unknown. Unverified reward-shop
  coin amounts are withheld. Curated corrections do not make all shops verified.
- Potion mixes and untradeable potions may have no four-dose GE reference.

The datasets and transformations are listed in [data provenance](docs/data-provenance.md).
OSRS Wiki-derived text and data have [separate attribution and terms](THIRD_PARTY_NOTICES.md).

## Development

Use JDK 11. From the repository root on Windows:

```powershell
.\gradlew.bat clean test build
.\gradlew.bat run
```

The development client is for manual testing; logging in and gameplay remain
under the player's control. Follow the [acceptance checklist](docs/manual-testing.md).

Plugin Hub uses `build=standard`, replacing this repository's Gradle build files.
Only `src/main/java` and `src/main/resources` are used by that standard build.
The development `shadowJar` is not the Plugin Hub distribution.

Offline Python generators are in `tools/`. Diagnostics and generation inputs
are in `tools/data/`; only the seven lookup JSON files and attribution notices
belong in runtime resources. Review generated changes and curated corrections
before replacing an existing snapshot. See [monster location generation](docs/monster-locations.md).

## License and credits

The proposed license for original plugin code is BSD 2-Clause; owner approval
of the attribution is still pending in this local submission candidate. See
[license review](docs/license-review.md). Third-party data and build tooling
retain their own terms; the code proposal does not relicense them.

Development and review used OpenAI Codex/ChatGPT assistance. The maintainer is
responsible for the submitted code and data. This project is not affiliated
with Jagex, RuneLite, Weird Gloop or the OSRS Wiki. RuneScape and Old School
RuneScape are Jagex trademarks.
