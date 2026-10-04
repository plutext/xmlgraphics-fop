# CR-015: a font's descender is not above its baseline, nor 0 for want of glyphs to guess from

Status: DONE 2026-10-05, gated PASS by the docx4j session (§5) and merged to `2.11-docx4j.5` (aad832b49; full
build after the merge 3762 tests, 0 failures, checkstyle and spotbugs clean); unreleased. Implemented at
Jason's word on branch `CR-015-positive-typo-descender` (code a8c3afc7d); the full build on the branch passes, 3760
tests, 0 failures, checkstyle and spotbugs clean. Registry key `fop/CR-015`. Capability `ascender-descender`. Upstream-bound: a FOP
defect in 2.11 and in Apache `main`, reported as [FOP-1896](https://issues.apache.org/jira/browse/FOP-1896)
(Open since 2011, "Incorrect text underlines position for some fonts"). A patch attached there in 2011 made the
same two changes; it stalled in 2012 because it was an edited source file rather than a diff, and the fix
had no test font. Branch `FOP-1896` in `../fop-upstream-wt` (da279ee9d), on Apache `main` at ab5d6eba6: the fop-core
suite passes, 3663 tests, checkstyle clean, and the three tests fail without the change. Pull request
[#119](https://github.com/apache/xmlgraphics-fop/pull/119) opened 2026-10-05 at Jason's word; the pull request
text is in `../fop-upstream-prs/FOP-1896.md`. The comment on FOP-1896 (`docs/upstream/typo-descender.txt`), approved
by Jason the same day, was posted by the docx4j session as jharrop (id 18123196, 2026-10-04T21:35Z UTC), with the
pull request's link.

Enterprise CR-001 §6.6 item 40. Found by the docx4j session (batch 51, corpus document 5975, a column of
`w:sym` Wingdings smileys). docx4j works around it for its own lines with `RunFontSelector.symbolLineHeight`
(17.3.1).

## 1. The mechanism

`OpenFont` sets a TrueType or OpenType font's ascender and descender in two steps, both run for every font.

1. **`determineAscDesc`** takes the OS/2 `sTypoAscender` and `sTypoDescender` when the ascender is positive
   and the box fits the em. Otherwise it takes hhea when that box fits. Otherwise, as a fallback, it takes
   OS/2 again whenever its ascender is positive. Nowhere does it check that the descender is at or below the
   baseline. Windows' Wingdings has `sTypoDescender` **+420** of 2048, a sign error: its hhea descender is
   −432 and its `usWinDescent` 432. FOP took +420, so its Wingdings text area was 0.565 em tall, all of it
   above the baseline.
2. **`guessVerticalMetricsFromGlyphBBox`**: when the chosen ascender and descender together exceed the em,
   they are replaced with the top of the glyph for `d` and the bottom of the glyph for `p`. It does this
   whether or not it found those glyphs. A font without them gets an ascender and descender of **0**. FOP's
   own `TTFFileTestCase` asserted this for AndroidEmoji, with the comment "TODO: Nedd to be fixed?".

Wingdings needs both fixed. Its hhea box (1841 + 432) exceeds its em, and it has no `d` or `p` glyph. So
refusing the OS/2 values alone sends it to the guess, which makes both values 0 (measured: worse than before).

## 2. The change

In `OpenFont`:
- `determineAscDesc`: the OS/2 values are used, in the first branch and in the fallback, only when
  `sTypoDescender <= 0`. A zero descender stays acceptable, since a font may have nothing below its baseline.
  No guard on hhea: none of the fonts scanned (§4) has a positive hhea descender.
- `guessVerticalMetricsFromGlyphBBox`: the glyph-derived values replace the table values only when both were
  found and lie on the right sides of the baseline (`localAscender > 0 && localDescender < 0`).

These are the two conditions of the 2011 patch on FOP-1896, arrived at independently and then compared. That
patch also required a negative hhea descender in the second branch, which no font here needs.

Capability `ascender-descender` (the thirteenth on this branch): a font's ascender and descender are never
a typo descender above the baseline, and never 0 for want of `d` and `p` glyphs.

## 3. Tests

In `TTFFileTestCase`, with the test fonts patched in memory (`sTypoDescender` at offset 70 of the OS/2 table):
- `testPositiveTypoDescenderNotTakenWithoutGlyphsToGuessFrom`: AndroidEmoji with `sTypoDescender` +650. This is
  Wingdings' case: the hhea box exceeds the em and there are no `d` or `p` glyphs. Expected: the hhea values,
  2200 and −650. Before the change, 1074 and +317 (per 1000 em); with only the first half, 0 and 0.
- `testPositiveTypoDescenderNotTakenWithGlyphsToGuessFrom`: DejaVuLGCSerif with `sTypoDescender` +492. This is
  the Lucida case: the glyph-derived 1556 and −426 (`d`'s top, `p`'s bottom). Before, the descender was +240.
- `testGetLowerCaseAscent` now expects AndroidEmoji's 2200 where it expected 0, and its descender, −650.

All three fail without the change.

## 4. Measured

**Every font on this machine**, loaded through `FontLoader` before and after: the Linux sets under
`/usr/share/fonts` and `~/fidelity-fonts`, and the Windows and Office sets. That is 2,563 distinct files by
content, 2,547 of which load.

| on stock FOP | fonts |
|---|---|
| ascender and descender both 0 | 978 |
| descender above the baseline (from the OS/2 table or the glyph guess) | 75 |
| moved by the first half alone (refusing the OS/2 values) | 31, Wingdings 1 to 3 among them, to 0 and 0 |
| moved by both halves | 1,053 |

Every font the change moves had, on stock FOP, an ascender of 0 or a descender at or above the baseline:
- 0 and 0 from the guess;
- a typo descender above the baseline;
- glyph-derived values on the wrong side of the baseline, such as Nerd Font monospace builds whose `p` sits
  above the baseline.

After it, no font that loads has a descender above the baseline or both values 0. The zero-metric fonts are most Noto fonts for scripts other than Latin
(Devanagari, Bengali, Thai, Arabic, Hebrew, Sinhala and the rest), the Droid script fonts, and among Windows and
Office faces MT Extra, the Euclid set, MS Reference Specialty and Algerian. The fonts with a positive typo
descender (32 distinct) are Wingdings 1 to 3, Lucida Sans, Lucida Fax, Lucida Sans Typewriter, Lucida
Handwriting, Maiandra GD, Haettenschweiler, a barcode font and several legacy Indic fonts. In each one it is
the hhea descender with the sign flipped.

**Laid out** (area tree, 11pt, `line-height` normal):

| face | stock FOP: text area, baseline from its top | after | Word's line (win metrics) |
|---|---|---|---|
| Wingdings | 6.215pt, 8.470pt | 12.188pt, 9.878pt | 12.21pt, 9.89pt |
| Lucida Sans | 6.215pt, 8.470pt | 10.582pt, 8.470pt (from `d` and `p`) | 12.53pt |
| Noto Sans Devanagari | 0pt, 0pt | 14.344pt, 9.856pt | 20.97pt |

Line pitch does not change, since `line-height` still sets it; what moves is where the baseline sits in the
line. Drawn:
- **Stock FOP.** Lucida Sans' underline runs through the letters, FOP-1896's report: the underline is placed
  from the descender. The Devanagari line rises into the line above, because its baseline sits in the middle
  of its line.
- **After.** The underline is under the text, and the Devanagari sits within its own line.

Wingdings now has Word's metrics exactly: its hhea values are its win values. Lucida Sans takes FOP's usual
glyph-derived values, as any font whose box exceeds the em does. Word's line uses the win metrics, which FOP
does not read at all; that is not this change.

## 5. What moves through docx4j

Every document with text in a face from §4. Glyph-fallback faces count.

**Retracted (2026-10-05): the partition first proposed here does not work.** This section said FOP writes each
font's ascender and descender to the PDF as the font descriptor's `/Ascent` and `/Descent`, so the faces that
moved would be the ones whose descriptor changed. That holds only for a single-byte font. A TrueType font
embedded as a CID font gets `PDFCIDFontDescriptor`, which takes `/Ascent` and `/Descent` from the font's bounding
box (`fontBBox[3]`, `fontBBox[1]`). The docx4j session measured it: 5975's descriptors are identical on the
control and the measurement for every face, and Wingdings' read 898 and −210 on stock FOP too, while layout used
770 and +205. In its place, the faces that move are listed by the name FOP embeds (the PDF `BaseFont` without
its subset tag): 761 names from the 1,677 moved files of §4, given to the docx4j session as
`~/fidelity-cr030/cr015-moved-faces.txt`, with a per-file table of the values before and after. Eight of the
names (`DroidSansFallback`, `NotoSansElymaic-Regular`, six `NotoSansSinhala` styles) also belong to another
version of the font that does not move. The docx4j workaround
(`symbolLineHeight`) sets the line height of a `w:sym` span. This change sets the font's own metrics, from which
FOP places the baseline in that line, so the two meet in 5975.

docx4j reads these metrics itself, by the docx4j session's account of its code (2026-10-05):
- `WordBlockLayoutManager.initialize` takes `lead` and `follow` from the block font's ascender and descender.
- `WordLineLayoutManager.wordLine` uses them in three places:
  - A run without its own line height gets a pitch scaled by `lead + follow`. With a block font at 0 and 0
    that divides by zero, and the cast leaves a pitch of −1.
  - An inline at the picture baseline takes FOP's altitude.
  - A block without docx4j's line hints stacks with FOP's altitude and depth.
- Most text runs carry docx4j's own line height, with their ascent from Word's win metrics rather than FOP's.

So movers are expected among documents whose block font is a moved face, or whose runs lack a line-height
span: fields, generated text, and `w:sym` on the XSLT pathway. That pathway never had `symbolLineHeight`, so
5975 rendered through it shows what this change alone recovers. The docx4j session agreed the second finding
(Wingdings' `d` and `p` are only at U+F064 and U+F070, which the guess does not find) and recorded item 40
(Enterprise a29d111).

**Corpus document 5975, the fork alone** (the docx4j session, 2026-10-05, on install r8 against r6). On docx4j's
XSLT pathway, where `w:sym` never had `symbolLineHeight`, the smiley's baseline was:
- r6 (stock metrics): 165.32, 4.41pt high, the old bug;
- r8 (this change): 170.75, 1.02pt low.

Word's is 169.73. On the visitor pathway with `symbolLineHeight`, both read 169.57, 0.16pt from Word's. So this
change corrects FOP's metrics, but docx4j's inherited pitch still scales by the block font's box, and the
workaround stays useful on this renderer.

**Gated 2026-10-05, PASS** (the docx4j session). Install r8 (this branch) against r6 (`.5` with CR-012 and
CR-013, the branch's base), with the same docx4j jars, over the three corpora, real-c2 and the probes,
partitioned by the embedded names above:
- 112 of 739 documents embed a moved face.
- 3 movers, all inside the partition, none outside it:
  - real2 `14_en-US_tbl_394` (Noto Sans Devanagari): +9 lines, 0.4277 to 0.4798;
  - real `12_en-US_num_tbl_5905` (Noto Sans Ethiopic): +2, 0.4769 to 0.5077;
  - its real-c2 copy (same faces): −1, 0.4627 to 0.4478. This is a pairing change in a document near 0.45
    whose script faces were 0 and 0 before.
- 5975 stays at 1.0000, and 13118 (0.9830), 2514 (0.7148) and 13265 (0.9869) are unchanged.
- No probe moved.
- None of the other 109 documents with a moved face moved. On this corpus the `lead + follow` pitch path made
  no difference, since most text runs carry docx4j's own line height.

## 6. Not addressed

- FOP never reads `usWinAscent` and `usWinDescent`, which Word's line height uses. Where hhea and win differ,
  as for Lucida Sans (1980 against 1900) or most Noto fonts, FOP's text area is not Word's.
- A font whose box exceeds the em still takes its `d` and `p` glyphs' bounds, where found: FOP's existing
  rule, unchanged.
