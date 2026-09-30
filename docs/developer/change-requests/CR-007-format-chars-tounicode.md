# CR-007: a format character with a zero-width glyph stays in the text layer

Status: DONE ON BRANCH 2026-09-30, `CR-007-format-chars-tounicode` off `2.11-docx4j.2` (0eb280c86); not
gated, not merged. Registry key `fop/CR-007`. Upstream-bound: a defect in Apache FOP with no docx4j
specificity; JIRA draft `docs/upstream/format-characters-tounicode.txt`. No capability: docx4j has no
workaround to drop and nothing to gate on; the text layer simply improves.

Enterprise CR-001 §6.6 item 34, the docx4j session's, found on the gate that retired the `+noliga`
twin (docx4j 476ba3147): with every Latin span on the CID declaration, 66 U+200F, 19 U+206A and one
U+200E across four corpus documents left ToUnicode, where Word keeps them and the single-byte path had
kept them. Ink unchanged; the line-parity scoreboard counts the lost U+206A as words (real 12013,
1.000 to 0.863).

## 1. The mechanism

`MultiByteFont.performSubstitution` calls `elideControls` unless `retainControls`, which
`TextLayoutManager` hard-codes false. `isElidableControl` covers the C0 and C1 controls, U+200B to
U+200F, U+2028 to U+202E, U+2060 and U+2066 to U+206F. The glyph and its association are dropped
before `mapGlyphsToChars`, so nothing reaches the subset or the CMap. FOP elides because a font
without a glyph for the character would draw its missing-character glyph.

Measured at font level: Arimo maps U+200E, U+200F and U+206A to glyphs 670, 671 and 679, all of
advance 0; Tinos and DejaVu Sans likewise (Tinos's U+2060 and U+FEFF are 799 wide, a font defect);
Carlito has U+200E and U+200F at 0 and no glyph for U+206A.

## 2. The change

`elideControls` keeps an association of exactly one format character (U+2000 to U+206F) whose glyph
is the one the character map gives it and whose advance is zero, and which is not a bidi control
(`isKeptFormatCharacter`, `isBidiControl`: U+200E, U+200F, U+202A to U+202E, U+2066 to U+2069). The
text layer is written in visual order, after FOP's own bidi resolution, so a bidi control there has
done its work already and a reader applies it a second time; pdftotext did exactly that on the first
cut, wrapping each kept mark in U+202B and U+202C. Word's PDF writer drops them for the same reason
(§5). The narrowing is the first gate's finding; the first cut kept the marks. The kept
glyph then carries its own association, `findUnsubstitutedCharacter` maps it back to its character,
it gets a subset selector when painted, and CR-002's CMap writer gives it its own entry. Advance 0,
so no layout or ink change. A character with no glyph (Carlito U+206A), a glyph with an advance, and
the C0 and C1 controls are elided as before, so the missing-character glyph is never drawn.

`MultiByteFontTestCase`: five cases on a hand-built character map and width array: U+206A kept, U+200F
elided although zero-width, U+200C elided for want of a glyph, a wide U+206B elided, a C0 control elided.

## 3. Measured

Command line, Arimo 12pt, kerning on, `language="en"`:

| | text layer (pdftotext) | CMap entry for U+200F | word boxes |
|---|---|---|---|
| before | `abcdef ghi` | none | 56.69-92.70, 96.03-112.04 |
| first cut | `abc` U+200F `def ghi` | present | identical |
| narrowed | `abcdef ghi` | none | identical |

A third block with U+206A inside the word: `abc` U+206A `def` on both cuts, boxes identical. The first
cut's kept mark came out of pdftotext wrapped in U+202B and U+202C: the reader re-applying a control
the layout had already resolved, which is the reason for the narrowing.

## 4. What moves, and the divergence to know

Text-layer-only movers: every document whose text holds a kept format character inside a word mapping
in a font FOP positions, through docx4j now every Latin span. Not movers, measured: U+200B (a zero-width
space to the TextLayoutManager, its own mapping, dropped from the area) and U+2028 (an explicit break),
which never reach the substitution; the bidi controls, now elided. Ink and geometry identical. One divergence: a kept format glyph
sits in the sequence during GSUB and GPOS, so a ZWNJ between `f` and `i` blocks the ligature (its
purpose) and a format character between two kerned letters blocks the pair, where Word's shaper
ignores default-ignorables. The corpus cases are marks at word edges. What stays lost: a format
character the font has no glyph for; a synthetic empty glyph in the subset would be the next step
and is not cheap.

## 5. Gate, 2026-10-01: the first cut narrowed, then merge

Run by the docx4j session on b67c1a1b1 (installed by Jason 06:25), `cr007-cand` against `notwin2-cand`,
597 documents, `mutool trace`. Geometry passed everywhere: still set 579 glyph-identical, the other 13
DATE and TIME fields crossing midnight; the five movers with every non-format glyph at the same position
and the count change exactly the kept characters (+13 and +6 U+206A, +65 and +1 U+200F, +1 U+200E).
Against Word's goldens the two halves differ: 12013 and 2600 gained their U+206A exactly as Word's text
layer has them (scoreboards 0.8627 to 1.0000, 0.8196 to 0.8242), but Word's PDFs hold none of the bidi
marks, so 5083, 252 and 2451 moved away from the golden (-0.0062, -0.0032, -0.0005). Read as "narrow,
then merge". Narrowed as §2 says; with it the three bidi documents are identical to the baseline by
construction (the only change there was the kept marks), so the docx4j session reads them by hand
rather than re-rendering. U+200C and U+200D remain kept where a font has a zero-width glyph: Word
honours them in shaping, and nothing in the goldens says what it writes to ToUnicode; no corpus
document holds one.
