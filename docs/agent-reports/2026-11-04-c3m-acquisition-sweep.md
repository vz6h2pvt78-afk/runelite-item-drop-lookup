# C3m — Bounded acquisition correctness sweep

Base: C3l `d1037c96e1305e9b0a9bfd6013fba4d1d8f2ec0b`.
Build/tag: `development-2026-11-04-c3m-soul-wars`.
Status: offline verified; human acceptance pending. C3g remains frozen fallback.

A finite actual-package audit of 30 queries covered Apollo's original complaint cases, supported Ent/log states, shop currencies/packs, item-specific access, and broad blighted bundle discovery. It reproduced a concrete purchase error: Soul Wars Reward Shop displayed coin prices and a stock-depletion warning. The ordinary potion family remained uncontaminated by the `(x4)` pseudo-item, but broad `blighted` exposed those bundle labels.

One new page was pinned: [Soul Wars Reward Shop, revision 15337354](https://oldschool.runescape.wiki/w/Soul_Wars_Reward_Shop?oldid=15337354). Its explicit table currency is Zeal Tokens; all 11 stock rows have infinite stock. All existing numeric amounts match the source. GPT approved the exact 11-row boundary in review `cc14965a-c43a-499f-9b4b-64e9b8dc3b77`.

The established exact-shop currency override now corrects all 11 rows and removes the false stock-depletion wording. Eight exact verified blighted labels say `10 Zeal Tokens per bundle`. Ectoplasmator remains 250, Soul cape 2,500, and Spoils of war 30 Zeal Tokens, without bundle wording. Item names, order, type, source/location and quantities are unchanged; no row was inserted or removed. Generated JSON remains untouched. No generic reward-shop assumption, bundle parser, canonical projection or schema change.

The 30-query before/after package comparison proves only cost/notes for those 11 exact identities changed; every other field, source row, count, chooser and access fact remains unchanged, including LMS/Bounty. It also checks animated-equipment exclusion while preserving tokens, Kamil/raid-supply/zero-stock exclusions, verified pack contents, and Ent gathering classification. Body and Death runes were included as ordinary-search controls, not asserted to have packs: only existing reviewed pack definitions are offered.

Validation: 134 Java tests in normal and official packaged builds, 103 Python tests, every data/identity validator. Java 11 / RuneLite 1.13.5 package: 64 classes, 62 API references, zero missing/disallowed, runtime resources exact, font load successful. Package SHA256 `f93475357e6dd34d5f19eca8ebf8f1a0f5eea971b83c9dd2a311d59d28c89869`. Dataset digest remains `d326c3175b3bd48e02364ae8e2b4eaf485662086b2409aaa712e0042e8fbfe36`; schemas remain 1. Standard/Larger bounds checks include both new offer examples. Actual rendered Standard cards inspected: restore bundle 209x253; Soul cape 209x211. No clipped text observed.

Deferred: reward-bundle projection should eventually represent canonical contained item plus purchase quantity separately. Broad searches still expose `(xN)` labels; this patch deliberately does not hide or silently normalize them. Other deferred Ent conditions and human acceptance gates remain.

Research accounting: this one-page concrete-contradiction block read 42% remaining before and 42% after; rounded/shared readings do not establish zero research cost. Acquisition evidence now contains 22 unique pinned pages. No further Wiki work in this checkpoint. No reset, push, Hub change, public feedback or telemetry. Source ZIP/full bundle/independent recovery clone accompany the checkpoint.
