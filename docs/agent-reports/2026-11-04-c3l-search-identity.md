# C3l — Identity-safe search

Base: C3jk `0a278a58809d00268ccd2834453da011d5d67b09`.
Build/tag: `development-2026-11-04-c3l-search-identity`.
Status: offline verified; Apollo acceptance pending. C3g remains the frozen fallback.

The actual 60-query audit reproduced eight changed query results: `d boots` included Red/Insulated boots, `r scim` omitted a shop, invalid Prayer potion(5)/Super restore(9) borrowed other doses/families, and four spellings of Dragon dagger(p)/(p+) returned (p++). These now resolve to the exact reviewed alias source set or an honest no-match. The other 52 audit rows, including counts, are unchanged. Empty results for an unavailable variant mean no recorded acquisition source, not that the item cannot exist.

`ItemNameMatching` centralizes existing aliases, normalization, explicit qualifier compatibility, matching tiers and compact collision handling. Plus signs remain identity-bearing; numeric and parenthetical qualifiers constrain candidates; explicit qualifiers do not enter typo fallback. All canonical names tied in a compact tier survive as separate chooser options. The global source selection applies the same tier across drops/shops/events/ground/gathering. Price lookup uses exact/compact identity and refuses ambiguous compact collisions. No new aliases or fuzzy ranking system were added. Each lookup caches ranks only for its own distinct item names; no player query cache persists.

Unqualified potion searches still group all recorded doses; explicit and compact numeric doses retain the selected dose. Reviewed Glory (uncharged) is still the only uncharged alias. Rune pouch/note, packs/contents, compact possessives, broad uncut (nine choices), and useful typo discovery remain supported. Every reviewed alias is tested against its complete canonical acquisition-source set. Synthetic collisions verify both the central matcher and the existing chooser.

One existing data issue was exposed, not silently normalized: generated reward rows call a four-item bundle `Blighted super restore(4) (x4)`. The UI already excluded it from the ordinary potion family. The currency test now queries that full recorded row identity; new checks prevent `(4)` searches borrowing `(x4)` rows. GPT approved this strict boundary in review `315c3788-db44-4133-8b1a-e66cdf0c73ac`. A future reviewed bundle projection may represent contents quantity correctly; no generic suffix stripping was introduced.

All runtime JSON is unchanged from C3jk. Dataset digest remains `d326c3175b3bd48e02364ae8e2b4eaf485662086b2409aaa712e0042e8fbfe36`, all schemas remain 1. Copy report implementation is untouched; the Back/recovery test now also covers invalid-dose search and exact restoration of the previous report.

Validation: 132 Java tests pass in normal and official packaged builds; 103 Python tests; all access/acquisition/location/item-source/report validators. Java 11 / RuneLite 1.13.5 package: 64 classes, 62 API references, zero missing/disallowed; runtime resources exact, Atkinson font loads. Package SHA256 `4bbd8f733d98d866b691eeba37671392b7bef358b75ad765eea4fa79ebb60125`. Standard/Larger Swing bounds previews and detail renders pass. Human visual, keyboard, gameplay and actual clipboard acceptance remain pending.

No research used for C3l. Last usage check: 42% remaining; three reset credits available; zero resets used today. No push, Hub change, public feedback or telemetry. Source ZIP, full bundle and independent recovery checkout accompany this checkpoint.
