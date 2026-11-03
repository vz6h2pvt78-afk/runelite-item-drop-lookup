# November 3 presentation candidate

Apollo selected the item-centred overview (B) after a live prototype review. This
development branch implements that flow; visual acceptance of the implementation
is still required. The accepted Plugin Hub build is a separate protected version.

An exact item goes straight to its acquisition overview. Broad queries offer
matching items first. Method buttons select a method explicitly, while Continue
with saved filters keeps the previous method and restrictions. See all sources
selects all methods; any deliberate NPC-area and Find source restrictions remain
visible and can be cleared in the empty-state recovery action.

Source filters are collapsed initially to leave room for results. Their summary
remains visible. Closed dropdowns forward wheel scrolling to the surrounding
list; normal keyboard selection remains available. Back preserves the source
finder, selected method, expanded groups and scroll position. Detail filters and
scroll are associated with the original drop record, so another item cannot
inherit an unrelated monster's detail state. A different selected item starts
with an empty source finder; the method and NPC-area preference are retained.

All eligible results remain reachable. Lists above 250 source cards initially
render 50 cards, with an explicit Show more sources control. Loaded batches and
expanded groups survive a return through the overview. Counts in the overview
refer to buying offers, drop entries or location records, not unique shops,
monsters or individual spawn points. Broad-query selection intentionally shows
one selected item at a time; it does not remove data.

The packaged OpenDyslexic Regular family supplies 13-point standard and 16-point
larger text. No RuneLite-wide font defaults are changed. Text wraps to the actual
viewport, including scrollbar space. The development-only review launcher offers
a warmer colour treatment on the same working screen at the same text size.
That comparison control is absent from the production JAR.

Numeric fractions use reciprocal odds rounded to the nearest tenth, omitting
trailing .0 and prefixing About when rounded. A non-certain fraction that would
round to 1 in 1 remains raw. Conditional/unparseable text, Always, Varies and
missing values remain explicit. Original rates remain in details and continue to
drive sorting and location bindings. Known unresolved Ent, Night beast and Zulrah
cases use neutral listed rates. This presentation precaution does not fix their
acquisition mechanics or establish a per-kill probability.

Cross-service exact-item protection already existed in the baseline. Extracting
the selection model preserves that protection, including Dragonstone versus
Dragonstone bolts. It is not a newly fixed production matching bug. Verified
pack-content associations, ordinary potion families, explicit doses and the
four-dose GE reference policy are retained. All ten runtime JSON files remain
unchanged in this presentation checkpoint.

For live review use `gradlew runImplementedReview` with JDK 11 and the gold B
sidebar icon. Compare Current styling and Warmer styling on Spade's overview and
Lyre/Lensa's detail. Also try Raw bear meat, Black bead with Shops retained,
`uncut`, potion names, rune packs, larger text, keyboard navigation and wheel
scrolling over a closed filter. C3 acquisition corrections are not included yet.
The normal development plugin remains available through `gradlew run`.
