# License scope and source review

Original Item Drop Lookup code is licensed under BSD 2-Clause. The root LICENSE
and bundled LICENSE.txt contain the same text, with the attribution “Apollo and
Item Drop Lookup contributors.” The public author is ApolloLumen/vz6h2pvt78-afk.
These statements apply only to rights held in the original project contributions.
They do not claim copyright in public-domain scaffolding or third-party material.

## Third-party scope

- Wiki adaptations retain CC BY-NC-SA 3.0 terms and source-policy exceptions.
  The code license does not replace those terms. Runtime notices and source page
  URLs accompany the resources. Older drop/shop revision IDs were not retained;
  source links are attribution, not a claim of exact historical reconstruction.
- The RuneLite template's public-domain dedication and inherited contributor
  acknowledgement are retained. Gradle wrapper notices/licenses are preserved.
- Unused OSRSBox input/output data and its converter are absent from the current
  tree and runtime JAR. Historical copies remain under the upstream GPL/Wiki/Jagex
  terms described in THIRD_PARTY_NOTICES.md and licenses/OSRSBox-GPL-3.0.txt.
- Runtime diagnostics are outside the JAR. All seven active lookup JSON files
  are unchanged from the tested development checkpoint 09c6eb6.

## Price API distinction disclosed for review

The real-time price API policy encourages community-facing tools and specifies
acceptable use. The inspected documentation does not state a separate explicit
redistribution license for numerical API responses. This is distinct from the
Wiki's text license; no blanket BSD or CC license is asserted over API fields.
No separate permission from the Wiki team has been obtained, and this review is
not an independent legal determination or a guarantee of Plugin Hub acceptance.

The snapshot contains 4,569 unique item-name records, of which 4,394 have a price.
Fields are itemName, price, highAlch, tradeable and notes. Every notes value is
the same generator-authored source sentence. There are no images or copied
examine/descriptive text. The generator processed the complete mapping response,
excluding only missing IDs/names. The original response and fetch timestamp
were not retained; the file was committed on 2 June 2026. The historical upstream
record count cannot be stated exactly. Current counts are not evidence of a
legally insignificant extract. Source provenance and limitations remain explicit
in the README, attribution notices and submission description.

## Primary sources checked on 2026-11-02

- https://github.com/runelite/plugin-hub/blob/master/README.md
- https://github.com/runelite/plugin-hub-tooling/blob/v5/LICENSE.templateplugin
- https://oldschool.runescape.wiki/w/RuneScape:Copyrights
- https://meta.weirdgloop.org/w/Licensing
- https://creativecommons.org/licenses/by-nc-sa/3.0/legalcode
- https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices (revision 15374019)
- https://github.com/osrsbox/osrsbox-db#project-license

Wiki API rights metadata reports CC BY-NC-SA 3.0. That metadata does not erase
source exceptions or automatically license off-wiki API content.
