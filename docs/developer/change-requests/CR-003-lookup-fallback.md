# CR-003: the lookup fallback skips a font's own script table whenever a language is set

Status: IN PROGRESS 2026-09-27 on branch `CR-003-script-fallback`, off `2.11-docx4j.2`; not gated,
not merged. Registry key `fop/CR-003`. Upstream-bound: a defect in Apache FOP with no docx4j
specificity; the JIRA draft is `docs/upstream/no-default-script-table.txt`. Capability
`lookup-fallback` (`Docx4jFop.LOOKUP_FALLBACK`), so docx4j can tell whether its language
attribute costs it shaping.

Not a §6.6 item of its own; it is the mechanism behind the kerning half of item 15 (kerning
asked for and silently not delivered) and behind CR-002's Latin effect being nil through
docx4j (item 30, §9.6 of `CR-002`).

## 1. The defect

`GlyphTable.matchLookups(script, language, feature)` looks up the font's lookups under the
`(script, language)` language system. When that finds nothing it falls back once, to
`(DFLT, dflt)`. Two things are wrong with that, and a third with the language itself:

1. It skips the script's own default language system. A font's features for Latin live under
   `latn/dflt`; its `DFLT/dflt` table, if it has one, is often thinner (DejaVu Sans: `case ccmp
   dlig kern`, no `liga`) or absent (Carlito, Caladea: `cyrl grek latn`, no `DFLT`). So any
   language the font has no system for loses the script's features, and in a font without
   `DFLT` loses everything: no ligatures, no contextual forms, no kerning, no warning.
2. The language never matches anyway. The FO `language` property carries an ISO 639 code,
   `en` or `tr`; the font's language systems are OpenType tags, `ENG` or `TRK`; FOP passes the
   code through untranslated. So every run with a language set takes the fallback, and a
   language the font does distinguish (Turkish, whose `TRK` system withholds the `fi` ligature
   and handles the dotless i) gets the same treatment as one it does not.
3. `OTFScript.isWildCard` compares with the default script, not the wildcard. Harmless today,
   since the recursion it guards ends at an empty `DFLT` match, and fixed in passing.

docx4j writes `language="en" country="US"` on every block and inline it produces (all 191
FO files of its real corpus), from `w:lang` and the document defaults, and cannot drop it
because FOP chooses hyphenation patterns by it. So through docx4j, every Carlito run, which
is every Calibri document, is shaped and kerned by nothing, and DejaVu runs get its `DFLT`
subset. That is why CR-002 changed no Latin text layer on the corpus, and why the per-run
kerning docx4j asks for (item 15) is delivered for some fonts and not others.

## 2. Measured before the change

Fork command line, stock 2.11 code on this path, `pdftotext` and the content stream:

| font, attributes | ligatures | kerning |
|---|---|---|
| Carlito, `script="latn"`, no language | `ffi fi fl ft ti` form | applied |
| Carlito, `language="en"` | none | none: `AVATAR` 46.62 pt wide |
| DejaVu Sans, `language="en"` | none (its `DFLT` has no `liga`) | (no GPOS adjustments seen either way; FOP warns its class table format is unsupported) |
| DejaVuLGCSerif, `performSubstitution("fi", "latn", "tr")` | ligature forms (`TRK` never matched) | |
| DejaVuLGCSerif, `("fi", "latn", "TRK")` | no ligature, as the font intends | |

The docx4j session measured the same on its pipeline: Carlito unshaped throughout, DejaVu
Serif shaped only because that font's `DFLT` happens to carry `liga`. Whether a run shapes
today is font-accidental.

## 3. The change

`GlyphTable.matchLookups`:

1. The language is normalised first: an ISO 639 code is mapped to its OpenType language
   system tag (`OTFLanguage.fromLanguageCode`: `tr` to `TRK`, `en-US` to `ENG`, `zh-TW` to
   `ZHT`), a bare tag is accepted as given, `dflt` and `*` pass through. Unknown codes stay
   as they are and fall back below.
2. The fallback order becomes the one OpenType layout engines use: `(script, language)`,
   then `(script, dflt)`, then `(DFLT, dflt)`. Each step is taken only when the one before
   found nothing, so a font with the language system uses it, a font without it uses the
   script's default, and a font without the script uses `DFLT` as today.
3. `OTFScript.isWildCard` compares with `*`.

The mapping table is the ISO 639-1 codes of the languages whose tags `OTFLanguage` already
names, each entry referencing the constant so a wrong tag cannot compile. Codes not in the
table behave as today after step 2, which is strictly better than before.

Nothing else changes. `GlyphMapping` still derives the script from the text when the FO has
none, and still maps `auto` and `zyyy` to the wildcard.

## 4. What moves

Through docx4j this is the widest fidelity change the fork has made: ligatures where a run
asks for them (with CR-001's hook) and kerning wherever docx4j's per-run kerning asks (item
15), across nearly all Calibri text, plus every other font with no `DFLT` table or a thin
one. Word kerns Calibri only where `w:kern` asks, and docx4j already expresses that per run,
so the expected movement is towards Word, but that is for the gate to say, document by
document, against Word's golden: movers no worse, and no mover in a document that does not
ask.

Languages with their own system in the font (`TRK`, `ROM`, `SRB`, `AZE`, `KAZ`, the Sami
systems) now get it. That is what Word does; it is also a change from the fallback for those
runs and wants a look in the gate where the corpus has them.

## 5. Tests

- `GlyphTableFallbackTestCase`: the fallback order on synthetic tables shaped like Carlito
  and DejaVu Sans, the direct match of a present system, the missing-script case, the
  wildcard, and the `isWildCard` fix.
- `LanguageSystemTestCase`: the real `DejaVuLGCSerif` in the tree, whose `TRK` system omits
  `liga`: `fi` under `tr` stays two glyphs, under `en` and `dflt` becomes the ligature.
- The complex-script suites (`GSUBTestCase`, `GPOSTestCase`, `ComplexScriptsLayoutTestCase`)
  and CR-001's `GsubFeatureDeltaTestCase` unchanged and green.

## 6. Measurement after the change

Fork command line, this branch:

| font, attributes | ligatures | kerning |
|---|---|---|
| Carlito, `language="en"` | `ffi fi fl ft ti` form (26 glyphs to 21) | applied: `AVATAR` 43.19 pt, was 46.62 |
| DejaVu Sans, `language="en"` | form (26 glyphs to 22) | |
| DejaVu Sans, `language="tr"`, `fifi` | ligature forms (4 glyphs to 2): DejaVu Sans has no `TRK` system, so the script default applies, which is right | |
| DejaVuLGCSerif, `performSubstitution("fi", "latn", "tr")` | no ligature: its `TRK` system omits `liga`, and `tr` now reaches it (`LanguageSystemTestCase`) | |
| Noto Sans Arabic, `language="ar"` | unchanged, 13 glyphs (`ARA` absent; `arab/dflt` equals `DFLT/dflt` there) | |

## 7. Relation to docx4j's script change

The docx4j session had ordered a change to write `script` on its FO after CR-002, believing
the missing script was the cause. It is not: `GlyphMapping` derives `latn` from Latin text
already. The cause is the language, and with this change docx4j need write nothing new; the
capability lets it confirm the fork it runs on has the fallback before it relies on it.
