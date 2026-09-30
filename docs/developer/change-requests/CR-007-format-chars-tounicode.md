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
is the one the character map gives it and whose advance is zero (`isKeptFormatCharacter`). The kept
glyph then carries its own association, `findUnsubstitutedCharacter` maps it back to its character,
it gets a subset selector when painted, and CR-002's CMap writer gives it its own entry. Advance 0,
so no layout or ink change. A character with no glyph (Carlito U+206A), a glyph with an advance, and
the C0 and C1 controls are elided as before, so the missing-character glyph is never drawn.

`MultiByteFontTestCase`: four cases on a hand-built character map and width array.

## 3. Measured

Command line, Arimo 12pt, kerning on, `language="en"`:

| | text layer (pdftotext) | CMap entry for U+200F | word boxes |
|---|---|---|---|
| before | `abcdef ghi` | none | 56.69-92.70, 96.03-112.04 |
| after | `abc` U+200F `def ghi` | present | identical |

A third block with U+206A inside the word behaves the same in Arimo. pdftotext wraps the kept mark
in its own bidi embedding controls (U+202B, U+202C) on output; that is the reader, not the CMap.

## 4. What moves, and the divergence to know

Text-layer-only movers: every document whose text holds a format character in a font FOP positions,
through docx4j now every Latin span. Ink and geometry identical. One divergence: a kept format glyph
sits in the sequence during GSUB and GPOS, so a ZWNJ between `f` and `i` blocks the ligature (its
purpose) and a format character between two kerned letters blocks the pair, where Word's shaper
ignores default-ignorables. The corpus cases are marks at word edges. What stays lost: a format
character the font has no glyph for; a synthetic empty glyph in the subset would be the next step
and is not cheap.

## 5. Gate

The docx4j session's: text layer of the four documents (86 characters back), still set glyph-identical,
scoreboards unchanged except 12013 restored. Order is Jason's.
