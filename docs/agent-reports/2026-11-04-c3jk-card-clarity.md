# C3j/C3k — Card hierarchy and location clarity

Base: C3h `34307f171de0596c98d045c41bcc5140b8850e3b`.
Build/tag: `development-2026-11-04-c3jk-card-clarity`.
Status: offline verified; Apollo acceptance pending. C3g remains frozen fallback.

Source → canonical Item now heads NPC, shop, pickup, gathering and event cards. Pack cards keep contents identity in the heading and explicit Buy/Contains/per-pack cost in the body. Costs precede location. NPC details put rate/quantity before requirements without splitting off special-rate qualifiers. Existing border style now consistently separates non-NPC and location cards. No new visual system, fonts or ranking.

The two exact reviewed Chaos Temple Ent locations share a summary only when encounter/yield lines, Wilderness state, membership, access text, notes and source destination agree. Their IDs and full individual associations/search text remain intact. The existing navigation destination is the same Ent Wiki for both; no individual map actions were removed. Mutated-context tests prevent grouping when membership, Wilderness, access text, notes or destination differs. Guild remains separate. The warning comes from the verified enum, never a name substring. Grouping reduces repeated location prose; the new danger warning takes additional space and is not claimed to make every card shorter.

Oak/Willow/Maple retain unverified exact requirements and now explain the base-Woodcutting/axe dependence. C3i inspected the actual Ent graph using Bronze, Rune and Dragon axes, finding a positive ordinary-Logs level-45 tooltip (4.16%) despite article text excluding it. Existing module uses one level for the output bin, axe cutoff and success formula, while the article separates base/current level. GPT review `01d9a50a-2125-44e7-adb1-c1d95fac6fc6` agreed no new numeric requirements should be inferred. Existing three supported outputs and uncertainty remain unchanged. C3i used no new pages: 44% remaining before / 43% after, rounded account-shared counters, no exact cost claim.

All runtime JSON is byte-for-byte unchanged from C3h; only build label changes in resource metadata. Dataset remains `d326c3175b3bd48e02364ae8e2b4eaf485662086b2409aaa712e0042e8fbfe36`. Complete acquisition routes/outcomes and lookup counts remain unchanged. Impling summary is intentionally the C3h version for Apollo to review.

Validation: 126 Java tests in normal and official packaged builds; 103 Python tests; all data/identity validators. Java11/RuneLite1.13.5 package: 63 classes,62 API references,0 missing/disallowed, all runtime resources exact, bundled font instantiated. Package SHA256 `d58814a51b55183777f5bad3c04599f8ef4ef7f3e0ed784cd7666b0e0ff50c14`.

Real Swing Standard/Larger previews pass bounds checks. Visually inspected Magic Ent, Gargoyle detail, Air rune pack, Temple Supplies Death talisman and Oak fallback. Example Standard card heights: pack319px, talisman291px, pickup141px, mining295px, Oak327px at209px card width. Human visual/keyboard/gameplay/clipboard acceptance still required.

No push, Hub change, reset, public feedback or telemetry. Full source ZIP/bundle and independent recovery checkout accompany checkpoint. Next: bounded aliases/variants/search identity pass (C3l), with exact identity preserved.
