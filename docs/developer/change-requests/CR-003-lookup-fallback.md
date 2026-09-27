# CR-003: the lookup fallback skips a font's own script table whenever a language is set

Status: IN PROGRESS 2026-09-27 on branch `CR-003-script-fallback`, off `2.11-docx4j.2`, with the kerning flag (§8); not gated,
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

## 8. The kerning flag, found by the gate's design before the gate ran

The docx4j session asked, before running anything, whether a font entry's `kerning="false"`
stops GPOS kerning, because docx4j declares every font twice: plain with `kerning="false"`
for the runs Word does not kern, and a `+kern` twin with `kerning="true"` for the runs
`w:kern` applies to. Measured: it does not. Carlito under `language="en"` with the §3
change gave `AVATAR` 43.19 pt wide under both settings. The flag gated only the legacy
`kern` table (`useKerningAdjustments` reads `hasKerning`, which is that table); GPOS ran
whenever the font had a GPOS table, with `kern` in every script processor's positioning
list. So §3 alone would have kerned every plain Carlito run, which Word does not, and the
gate's own assertion, kerning only where docx4j asked, would have failed it. And for any
font whose `DFLT/dflt` already carried `kern` (DejaVu Serif), plain runs had been kerned all
along; that was a standing discrepancy the flag fix also removes.

Two smaller findings under it: the loader never copied the flag onto the font object, so
`CustomFont.isKerningEnabled()` was true for every TrueType font whatever the
configuration said; and the flag's documentation ("enables or disables kerning for the
font") already promised what it did not do.

The change, on the same branch since without it §3 is a regression on Calibri:

- `OFFontLoader` records the flag on the font (`setKerningEnabled`).
- `MultiByteFont.performPositioning` passes a feature delta of `-kern` when kerning is off.
  Marks are still positioned (`mark`, `mkmk` stay); only kerning goes.
- `GlyphPositioningTable.position` and `ScriptProcessor.position` gain overloads taking the
  delta, applied by the same helper CR-001 built for GSUB, now named `applyFeatureDelta`
  with the old name kept.
- Capability `kerning-flag` (`Docx4jFop.KERNING_FLAG`), distinct from `lookup-fallback`,
  because it is a distinct promise a producer relies on.

No per-run `fox:gpos-features` is needed: docx4j's plain and kerned declarations now mean
what they say. Agreed with the docx4j session 2026-09-27, which had first proposed the
per-run form and withdrew it for this. JIRA draft: `docs/upstream/kerning-flag-gpos.txt`.

Measured with the flag honoured, Carlito, `language="en"`, `AVATAR Toffee fifty office`:

| declaration | `AVATAR` width | glyphs | ligatures |
|---|---|---|---|
| `kerning="true"` | 43.19 pt | 21 | form |
| `kerning="false"` | 46.62 pt | 21 | form |

Ligatures are GSUB and follow §3 either way; only kerning follows the flag, which is the
separation docx4j's twins were built on.

## 9. Gate plan, agreed 2026-09-27

Partition by font before rendering, done docx4j-side over each document's FO: a span can
move only if its font has no `DFLT`, or a `DFLT/dflt` feature set different from its
script's, or a language system matching a document language under `OTFLanguage`'s table;
twin declarations, fonts the baseline PDF embeds as anything but a CID font, and the CJK
fonts docx4j declares `advanced="false"` excluded since they never shape. First pass on §3
alone: 477 still, 121 movers of 598. Remodelled for §8 (a plain declaration keeps `kern`
before and drops it after, a `+kern` declaration keeps it on both sides, `mark` and `mkmk`
count only where the span holds a combining mark, `cpsp` only with a capital): 501 still, 97
movers, at `/home/jharrop/fidelity-gsub-partition/cr003/partition3.tsv`, local only. Classes:
kern-gained, about 38, `+kern` runs in Arimo, Tinos and Carlito; kern-lost, about 13, plain
runs in DejaVu Serif, DejaVu Serif Condensed and DejaVu Sans; `ccmp`/`cpsp`/`locl` only,
about 45, mostly expected to be recorded rather than to move; script shaping, 3, DejaVu with
Arabic or Indic spans. The three assertions read per class: the still set byte
identical; kerning appears or disappears only as the class predicts; ligatures only where
CR-001's hook resolves to them; no mover worse against Word; a predicted mover that did not
move recorded, not failed. The still set is far smaller than CR-001's 602, and the record
will say so rather than borrow that gate's signal.

**The kern-lost class will not move (measured after the partition, §10).** FOP applies no
kerning to DejaVu Sans or Serif under any setting, so those plain runs were never kerned and
have nothing to lose; the docx4j session was told before the gate ran and reclassed: 510
still, 88 movers (kern-gained 37, `ccmp`-led 46, `case` 3, `abvm` 1, `blwf` 1).

## 10. Found on the way: FOP never kerns DejaVu

One line, `AVATAR To Ye` at 14pt, `language="en"`, line width from `pdftotext -bbox`, every
combination of the font entry's `kerning` and `advanced`:

| font | kerning on | kerning off |
|---|---|---|
| Carlito | 75.26 pt (advanced on or off) | 80.92 pt |
| Arimo | 92.90 pt (advanced on or off) | 99.19 pt |
| DejaVu Sans | 99.68 pt | 99.68 pt |
| DejaVu Serif | 99.01 pt | 99.01 pt |

Carlito and Arimo kern under the flag, and identically with `advanced="false"`, which
neither has a legacy `kern` table to explain: the per-font `advanced` attribute is ignored.
`LazyFont` takes the user agent's complex-scripts flag whenever a resource resolver is
present, which is always in normal use, and reads `EmbedFontInfo.getAdvanced()` only when it
is not. That is a further upstream defect, and it bears on docx4j, whose CJK workaround
(`FopConfigUtil.mustNotUseOpenTypeLayout`, `advanced=false` per font) therefore does nothing
in FOP; the docx4j session has been told to check its CJK baselines. DejaVu kerns under no
route: FOP
reads its GPOS `kern`, two class-based pair-positioning subtables (format 2, logged at FINE),
and applies nothing; and `GlyphMapping.useKerningAdjustments` then skips the legacy table
because the GPOS `kern` feature exists. So the font is unkerned with `kerning="true"` too.
Why format 2 yields nothing is not traced. Not yet a §6.6 item; proposed to the docx4j
session with the measurement, and it touches item 15's record, which closed per-run
kerning on measurements that may not have included a DejaVu run.

## 11. First gate, 2026-09-27: FAIL on "no mover worse", under investigation

Baseline the CR-002 branch, candidate ca8cf0115, four corpora. Text layer unchanged, as
expected. Geometry moved in 49 documents (partition, corrected during the reading, 447
still, 151 movers). Scoreboards: 11 improvements (for instance 15_de-DE_2299 0.7174 to
0.8913, 14_es-MX_tbl_14140 0.8788 to 0.9848) and 5 regressions: four in `+kern` runs of
Tinos or Arimo (15_ru-RU_tbl_475, 12_pt-BR_fields23_num_tbl_14776, 12_en-US_tbl_13872,
12_en-US_num_tbl_9832), where the space after a comma or full stop closes so far that
`pdftotext` loses it ("repair, if" to "repair,if"), and one in Carlito Greek
(14_en-GB_sdt_num_8371, 70 to 71 pages against Word's 68). Four predicted-still documents
moved, in one of them a space after a bold CID span while the glyph before it did not.

The docx4j session also settled §10's `advanced` question for its own pipeline: its
`WordWidthsFontCollection` passes `useComplexScripts && getAdvanced()` where FOP's stock
collection passes the global flag alone, so `advanced=false` is honoured there and the CJK
exclusion stands (0 radicals in the text layer with it, 156 without, on one document).
DejaVu's kerning is §6.6 item 32.

None of the three symptoms reproduces on the fork's command line, old (the 2.11-docx4j.1
core) against the branch, per-glyph positions from `mutool draw -F stext`:

- `cpsp` is not applied: `DefaultScriptProcessor`'s positioning list is `kern`, `mark`,
  `mkmk`, and no processor names `cpsp`. Carlito Greek under `language="el"`: a
  `kerning="true"` line 265.44 pt to 261.30 (narrower), a `kerning="false"` line unchanged
  with no glyph moving, so `ccmp` and `locl` did nothing to that text either.
- Kerned spaces: Arimo `kerning="true"`, "repair, if however, you repair. Further", gaps
  after comma and full stop 3.05 pt old and new; with docx4j's form of the space,
  `<fo:inline word-spacing="-0.7pt"> </fo:inline>`, 2.35 pt old and new; Tinos Cyrillic
  2.75 both; justified paragraphs open the gaps by up to 0.1 pt as the kerned words free
  space, never close them. In FOP's complex mapping path, used for any font with GSUB or
  GPOS before and after this change, GPOS applies within a word only; a space is its own
  mapping and is never kerned with a neighbour; the neighbour kerning of the simple path
  comes from the legacy `kern` table, which Arimo, Tinos and Carlito lack.
- Single-byte regular Carlito with a bold CID span: the bold word kerns internally (the
  colon moves 0.33 pt with it) and what follows moves by the same; the gap after the colon
  is unchanged. Not the reported pattern, where the glyph before the space stayed put.

docx4j's `kernSpaces` (`RunFontSelector`) wraps a space in a `word-spacing` inline only when
the font's legacy kern table has a pair for it; Arimo and Carlito have no such table, so
those spaces are plain glue in the FO. The exact FO around "During repair, if however,
you" and around the Greek overflow, with the font declarations, has been asked for; the
mechanism is not established, and the branch stays unmerged.
