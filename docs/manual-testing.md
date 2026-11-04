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
   Use **Find source** with `fire giant` to reach it directly.
   Confirm rate `1/128`, quantity, levels 86 / 104 / 109, Brimhaven floors,
   Catacombs, Waterfall and other locations are readable.
5. In details choose **Wilderness only**: Deep Wilderness Dungeon should remain.
   **Exclude Wilderness** should remove it. Members should retain Fire
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
10. Search **dragon pickaxe**, open **King Black Dragon**: the card must say
    **Outside Wilderness**, and its note must explain the Wilderness journey.
    Check **Corporeal Beast** via **spirit shield** for its safe lair and
    direct-teleport note.
11. Exercise a source with missing location data and an unverified binding.
    Expect an honest empty state or “monster locations only” explanation and a
    working Back button. **Brimstone key / Fire giant** is an intentionally
    unverified conditional-drop example.
12. Search **coins**: sources beyond the former first 100 must remain accessible,
    including within collapsed families. Record any noticeable rendering delay.
13. Regression checks: **runepouch** must match precisely; **spade** must show shops;
    **graceful hood** should appear under Reward Shops; **prospector boots** must
    retain golden-nugget / Volcanic Mine point costs at the appropriate shops.
14. Confirm there are no League-region controls or labels. Check long notes and
    client resizing, keyboard/mouse Locations and Back, and
    Wiki buttons opening the corresponding page only when clicked.

15. Scroll a long result list: item search and **Find source** must remain visible.
    Search **coins**, find `fire giant`, then clear Find source: all 1,689 drop
    records must return. A new item search must clear the previous source text.
16. Open a monster and scroll its locations: Back and location filters must remain
    visible. Back must retain both the item query and Find source text.
17. Expand **Goblin** under **goblin mail**, then toggle GE price off and on. The
    group must remain expanded. Group buttons should work with Tab and Space.
18. Search **prospector boots**, select **Reward Shops**, and find `motherlode`.
    Expect the golden-nugget option. NPC controls should be hidden in shop views.
    Select NPC Drops: the empty state should offer **Show all sources**, restoring
    source type, Wilderness and Find source in one click.
19. **Rune pouch** and **runepouch** must return three direct rewards: 750 Slayer
    reward points, 75 LMS points at Justine's shop, and all four Mage Training Arena
    point amounts. The Arena cost must read as a combined cost, not alternatives.
    **Rune pouch note** remains distinct with its own GE price. Broad **pouch**
    still includes related items. Currency-only entries such as Grace's hood still
    say the amount is not recorded. Shop Wiki shortcuts open an item search on
    the OSRS Wiki. A made-up item should show a concise no-match message.
20. A broad query matching unrelated items must not show one arbitrary item's GE
    price. Potion families use the explicitly labelled four-dose reference.
21. Search **Prayer potion**, **Saradomin brew**, and **Super restore** without a
    dose. All available dose variants should appear. Super restore must not include
    Blighted super restore or Super restore mix. **Kamil** must be absent because
    these are quest-encounter supplies. GE uses the four-dose item and says
    **GE snapshot (4-dose)**. An explicit one-dose query still uses that reference.
    **Prayer mix** has no four-dose item, so its reference says unavailable.
22. Search **Air rune** and **Eye of newt**, then open **View buying options**.
    Loose items and 100-item packs must both appear. Find source `pack` should
    narrow to 15 Air rune offers or nine Eye of newt offers. Eye packs contain
    noted items. All pack costs say **per pack**; GE remains the individual item.
23. **Air rune pack** must return the actual pack (14 offers), not loose Air runes.
    **Tzhaar air rune pack** must show 645 Tokkul per pack and the gloves note.
24. **Raw shark** should show two stocked shops. Etceteria Fish and other shops
    with default stock zero must be absent. **Coal** has six shop offers.
25. **Adamant platebody** must not include Animated Adamant Armour. Yew logs must
    not include historical Bloodthirsty NPCs. Warrior guild token rewards remain.
26. **Toadflax** shows three Brimhaven vouchers in the current store; the old-ticket
    exchange explicitly requires discontinued tickets. Theatre of Blood's mid-raid
    chest is absent from normal item searches.
27. Open **Toadflax -> Zulrah**. Missing location data should be explained, irrelevant
    location filters hidden, and **View on OSRS Wiki** should lead to the Zulrah page.
    By contrast, Fire giant filtered to Wilderness and F2P has existing records but
    no matches: filters and **Clear location filters** must remain usable.
28. **Coins -> Shark (Sailing)** must say **Rate: Not recorded** in both results and
    detail. Unknown rates must not become guaranteed drops. Location cards should
    say **Combat level**, without implying that Slayer or Sailing requirements are
    represented by that number.

29. **Magic logs -> Gathering** shows one Ent method. Read the kill-then-chop action,
    usable axe and 75 base Woodcutting requirement. Confirm two Wilderness locations
    say level 101 and two noted logs per successful chop; Guild says level 86 and
    one. Ent must be absent from **NPC Drops** for logs, while **Slayer's enchantment**
    still has its direct Ent drop and Wilderness task condition. **Yew logs** uses
    61 base Woodcutting and the same three encounters. **Logs** lists only two
    Wilderness encounters and states that exactly level 45 is uncertain. Oak,
    Willow and Maple explain that harvesting details are unavailable.
30. **Amulet of glory -> Loot & containers** shows one Dragon impling loot method,
    three noted amulets and a qualified Wiki-listed 1/19. Opening an existing jar
    requires no Hunter level. Catching requirements belong only to capture, with
    separate jar/immediate-loot routes. Exact **Dragon impling jar** and charged
    glory searches must not become searches for this uncharged outcome.
31. On both event cards, try Standard and Larger text, scroll to the Method Wiki
    button, and switch freely between methods. Check that no text is clipped and
    that Find source narrows these methods without changing the selected item.

32. **Adamant sq shield -> Night beast** shows the raw listed rate and quantity,
    plus one Drop context explanation of the three additional rolls. It must not
    claim three independent regular rolls or triple the quantity. Dark beast must
    not inherit the context. Big bones and Long bone keep their separate raw rates.
33. **Dragonstone -> Zulrah** shows quantity 1 and **1 in 1593.6 per roll; 2 rolls
    per kill** in both the result and details. Location data remains unavailable;
    the Wiki button points to Zulrah. Specific NPC-area filters may exclude it
    because its location is unverified; All restores it. **Uncut dragonstone** and
    **Dragonstone bolt tips** must not inherit this exact source/rate.

For failures, record query, monster/rate, active filters and observed behavior.
Confirm these checks before considering public-release work. The proposed code
license and wiki-data notices also need owner review before publication.
