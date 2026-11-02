# Monster-location acceptance testing

The implementation is ready for manual acceptance testing. Automated checks and
offline renders cannot verify in-game behavior.

1. Open the project in IntelliJ, choosing Temurin 11 for project SDK and Gradle JVM,
   or run `.\gradlew.bat run` in the repository. The supplied local
   `Launch development client.cmd` sets Java 11 and the working directory.
2. For a Jagex account, follow RuneLite's current
   [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
   instructions. Complete authentication yourself; do not copy credentials into chat.
3. Enable **Item Drop Lookup** if needed and open its sidebar.
4. Search **rune scimitar**. Open **Fire giant** by card or Locations button.
   Confirm rate `1/128`, quantity, levels 86 / 104 / 109, Brimhaven floors,
   Catacombs, Waterfall and other locations are readable.
5. In details choose **Wilderness only**: Deep Wilderness Dungeon should remain.
   **Exclude Wilderness** should remove it. League region **Kourend** should show
   appropriate Catacombs / Giants' Den / Karuulm rows. Members should retain Fire
   giants; F2P should show a clear no-matches state. Clear filters to restore all,
   including the explicitly unreachable Crandor/Karamja row near the end.
6. Press **Back to results**. Confirm query, source filter, GE checkbox, expanded
   monster groups, results and scroll position remain. Repeat after scrolling far
   down the results and changing detail filters.
7. Search **goblin mail**. Goblin `5/128` should include **Lumbridge**, level 2, F2P.
   Goblin `10/128` must not claim Lumbridge for that rate. **All monster locations**
   should reveal the wider context with a warning.
8. Search **black sword**: Abyssal demon `4/68` should show Wilderness Slayer Cave;
   `4/128` should show standard locations. **Abyssal whip** `1/512` should retain
   both contexts.
9. At item level choose Wilderness-only, then Exclude Wilderness. Fire giant can
   qualify in both via different locations. Clearing filters must restore every
   source. Status text should explain unknown/unverified exclusions. Shops and
   other non-NPC sources follow the separate source-type filter.
10. Search **dragon pickaxe**, open **King Black Dragon**: Wilderness must be **No**,
    League region can still be Wilderness, and its note must explain the
    Wilderness journey. Check **Corporeal Beast** via **spirit shield** for its safe
    lair and direct-teleport note.
11. Exercise a source with missing location data and an unverified binding.
    Expect an honest empty state or “monster locations only” explanation and a
    working Back button. **Brimstone key / Fire giant** is an intentionally
    unverified conditional-drop example.
12. Search **coins**: sources beyond the former first 100 must remain accessible,
    including within collapsed families. Record any noticeable rendering delay.
13. Regression checks: **runepouch** must match precisely; **spade** must show shops;
    **graceful hood** should appear under Reward Shops; **prospector boots** must
    retain golden-nugget / Volcanic Mine point costs at the appropriate shops.
14. Check long notes and client resizing, keyboard/mouse Locations and Back, and
    Wiki buttons opening the corresponding page only when clicked.

For failures, record query, monster/rate, active filters and observed behavior.
Confirm these checks before considering public-release work. The proposed code
license and wiki-data notices also need owner review before publication.
