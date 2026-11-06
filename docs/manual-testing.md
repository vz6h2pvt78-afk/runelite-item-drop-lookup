# Obtainable manual acceptance

Use the exact candidate identified in Copy report and its saved checkpoint.
Automated tests and offline renders support this checklist; they do not replace
human acceptance of the actual sidebar.

Run the development client with Java 11 using `.\gradlew.bat run`, or use the
guarded launcher supplied with the checkpoint. The sidebar can be checked at the
login welcome screen. If gameplay context is needed, the player controls login
and gameplay; follow RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
instructions without sharing credentials.

## Focused acceptance

1. Open **Obtainable**. Search `potion`, `logs`, `fish` and `uncut`.
   Each should offer useful choices with the correct visible item pictures.
   Specific `Magic logs` should select that item. `Shark` and `Raw shark`
   must remain distinct.
2. Select **Prayer potion**, then **Monsters** or **All sources**. Doses should
   remain grouped without turning the chooser into a dose list. The GE reference
   is labelled as a four-dose snapshot. **Back to Matching items** should
   restore the chooser.
3. Search **Uncut zenyte**, then open **Production**. Verify one shard plus one
   cut Onyx, Crafting 70 without boosts, Monkey Madness II completed, wearing
   a small or big zombie monkey greegree, members, and the wall of flame
   beneath the Temple of Marimbo on Ape Atoll.
4. Use **Find Zenyte shard sources**. Expect Demonic gorilla at **1 in 300**
   and Tortured gorilla at **1 in 3000**. **Back to Uncut zenyte** should restore
   the recipe; **Back to item** opens the shard's overview.
5. Search **Zenyte**, open **Production**, and check one Uncut zenyte, a chisel
   and Crafting 89 with boosts. Follow both ingredient buttons. The named Back
   buttons should return through Uncut zenyte to Zenyte with the previous method,
   source-search text and scroll position.
6. Search **Red cape**. Expand the **Goblin** group: one group portrait should
   identify the monster family, without repeated cape icons on each drop card.
   Open a variant's details and return. Long names and warnings must fit.
7. Search **Crystal shard**, find **Dark beast**, and open its details.
   The item-specific restriction should be visible without first enabling
   **Other locations**. Wider monster locations must not imply that this item
   drops at every location.
8. Search **Demon's heart**, open **Porazdir**, and check the conspicuous
   Wilderness warning, deep-Wilderness roaming context and enchanted-symbol
   tracking. Danger must use text and an icon, not colour alone.
9. Search **Air rune** and **Eye of newt**, then open **Shops**.
   Loose items and packs should both be clear; pack costs say **per pack**.
   An exact **Air rune pack** search should keep the pack identity.
10. Search **Magic logs**, then **Gathering**. Ent harvesting must explain
    killing the Ent before chopping, alongside the combat/woodcutting conditions
    and applicable Wilderness danger. Logs must not be presented as a simple
    NPC drop.
11. In an NPC source list, change the method and area menus, enter a nonsense
    **Find source** value, and recover. **Clear search** clears only that text.
    Returning from details should preserve the item, menus and scroll position.
12. Search **Cooked chicken**. If the current dataset still has no acquisition
    sources, it should say so honestly rather than substitute Raw chicken.
    A made-up item should instead show no matching item. Back should recover
    the prior valid lookup when available.
13. Check Standard and Larger text on long cards, scroll a long list, and use
    Enter to search. Item pictures should load correctly after choosing an item
    and returning to its list. Wiki buttons should open the intended page only
    when clicked.
14. Use **Copy report** at a chooser, a selected item, a recipe and NPC details,
    then paste into a text editor. Check the query, exact selected item, method,
    source, filters and build/dataset identity. The shard report must not inherit
    its parent recipe summary. Searching alone must not change the clipboard or
    send a report.

A maintainer may separately check plugin disable/re-enable and client restart
through RuneLite's normal plugin controls. The sidebar should reopen normally,
without duplicate toolbar entries or stale results.

## Diagnostic build identity

`lookup_build.properties` records a diagnostic build label, three schema versions
and a digest of every bundled JSON dataset. Before a new runtime checkpoint, run
`python tools/check_report_identity.py --write <checkpoint-tag>` and verify with
`--expected-build <checkpoint-tag>`. The checkpoint tag identifies the frozen
runtime source; it is not a claim of a published Plugin Hub release.
Documentation-only follow-ups do not change the tested runtime identity.

The metadata guard canonicalizes JSON so Windows line endings do not change its
meaning. Missing metadata reports build identity unavailable; runtime never
guesses Git state. Copy report writes once per click and never reads clipboard.

For failures, record the query, source, active filters and observed behavior.
Keep human acceptance separate from successful automated or packaging checks.
Earlier checklists remain in Git history; use this checklist for the current UI.
