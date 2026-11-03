# Shop purchase currency parsing

The generator no longer uses a GE-value column to infer coins. Currency must be explicit in the selling-price cell's currency icon link, or in a corresponding StoreTableHead/StoreLine field. Metadata is paired only when the counts and ordered displayed item names of all direct tables match the rendered tables. The reviewed Tzhaar shop row template is supported for that identity matching. Unknown layouts can still use explicit currency icons; missing, conflicting or ambiguous evidence leaves the cost blank.

An exact, reviewed alias resolves `Points` on the Slayer Rewards page to Slayer points (Wiki revision 15373095 explicitly identifies Slayer reward points). Generic Points on other pages remain unknown. No fuzzy shop-name or category heuristic identifies a currency. Infobox Shop was inspected in the seven samples; it provided location/membership but no purchase currency for these pages, so no speculative infobox fallback was added.

Amounts must be a single positive integer, with optional thousands separators. Ranges, composite costs and footnote numbers are not concatenated into prices. A rendered Free price is retained when there are no additional purchase costs. Rows with a Logs required, Additional cost, or Items required column are held without a complete price when that field is populated. For example, Forestry Log basket remains blank with a note about additional purchase costs; Cape pouch has a verified bark-only price. This is not a universal recipe/cost parser.

Stock-dependent pricing notes require an explicit positive delta. Table-specific namenotes, such as Karamja gloves, are preserved when metadata matches. Current Brimhaven vouchers, current experience tickets, and historical tickets remain distinct. Pack amounts are per pack; no automatic unit multiplication occurs here.

The source fetch now requests wikitext, rendered text, revision and redirect resolution together. The diagnostic generation report retains page revisions. This avoids mixing templates and rendered output fetched in separate page revisions. It does not freeze revisions of every transcluded template or dynamic price module.

## Review boundary

Phase 2A changes the generator and fixtures only. No runtime shop JSON was regenerated for this checkpoint. The bounded review uses seven cached Wiki revisions and compares 95 rendered rows with existing source identities. Conditional Karamja-glove rows are held separately from the existing base-price rows. The generator's older first-row deduplication remains a limitation for broad variant support; no full regeneration should be published without reviewing variant preservation and output diffs.

`tools/fixtures/shop_currency_samples.json` contains reduced, attributed OSRS Wiki examples with original page/revision IDs. Unrelated prose, images and columns were removed; table/row identities, price/currency evidence and additional-cost columns were retained. CC BY-NC-SA 3.0 applies to the adapted Wiki data. Fourteen focused tests cover seven real pages plus missing/conflicting metadata, generic points, shuffled table identities, row overrides, multiple currencies and invalid amounts. Run all Python tests with `python -m unittest discover -s tools -p "test_*.py"`.
