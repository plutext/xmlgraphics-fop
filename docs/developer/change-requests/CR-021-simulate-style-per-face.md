# CR-021: simulated italic and bold as Word draws them, per face

Status: DONE 2026-10-07, gated PASS by the docx4j session (§9) and merged to `2.11-docx4j.5` by fast-forward
(dbe0b7cf2; the change is eb003c325); unreleased. Full CI build: 3860 tests, 0 failures, checkstyle and spotbugs
clean.
by Jason the same day, to ship in `2.11-docx4j.5`. Registry key `fop/CR-021`. Capability `simulate-style-per-face`
(§3.3). A fix, not a hook: FOP's `simulate-style` is wrong for any face that is already italic or bold, and its
stroke is right at one size only. So it is upstream-bound (§6), and the code is the same on Apache `main`.

Enterprise CR-001 §6.6 item 46, found by the docx4j session (real3 2065, ledger9; batch 53) and specified by its
probe `fonts-light-bold-italic` (Word run 2026-10-07).

## 1. The defect

A font entry with `simulate-style="true"` asks FOP to synthesise the style a triplet names from the face it is
registered on. `PDFPainter.startSimulateStyle` does that from the triplet and the entry's flag alone:
- a triplet of weight 700 is stroked (`2 Tr`) with a line of 0.31543pt, whatever the font size;
- a triplet of style italic is sheared by 0.3333 in the text matrix.

It never asks whether the face is italic or bold already, though FOP has the face's italic angle (`post` table)
and weight class (OS/2), read by `OFFontLoader` into `CustomFont`. So:
- **An italic face is slanted twice.** A family with an italic face and no bold one (Calibri Light, Aptos Light,
  the other light and semilight Office faces) cannot have its bold italic drawn as Word draws it, the italic face
  emboldened: declared on the italic file, FOP shears it again. docx4j declares the regular file instead, and FOP
  draws "Összeállítás:" in 2065 in Calibri-Light, sheared and stroked, where Word draws Calibri-LightItalic,
  unsheared and stroked.
- **The stroke is Word's at 11.04pt only.** Word strokes its synthetic bold at 1/35 em at every size; FOP's fixed
  0.31543 is 11.04/35. At 22pt Word's bold is twice as heavy as FOP's, at 8pt FOP's is 40% too heavy.
- **A bold face is stroked again** if a bold triplet is registered on it with `simulate-style`, as when only the
  italic is to be synthesised for a family with a bold face and no bold italic.

## 2. Word's rule (the docx4j session's probe)

Measured in Word's PDF of `fonts-light-bold-italic`, at 7.92, 11.04, 12.96, 16.08, 22.08 and 36pt:
- Word never shears an italic face: Calibri Light bold italic is Calibri-LightItalic, unsheared, stroked.
- Its synthetic italic is a shear of exactly 0.3333, FOP's own constant: Aptos Light, which has no italic face in
  Word, is Aptos-Light sheared 0.3333, and its bold italic that plus the stroke.
- Its synthetic bold is a stroke of 0.02857 em (1/35) at every size, upright and italic alike.
- A real face is not stroked: Calibri's bold italic.
- Synthetic bold also spreads the glyphs, about 0.02pt each. That is docx4j's to model through spacing; the
  painter draws the advances it is given.

## 3. The change

### 3.1 In `PDFPainter.startSimulateStyle` and `endSimulateStyle`

- **Shear only an upright face**: a triplet of style italic is sheared only where the face's italic angle is 0.
- **Stroke only a face that is not bold**: a triplet of weight 700 is stroked only where the face's weight class
  is below 700 (0, unknown, counts as not bold, as now).
- **Stroke at 1/35 em**: the line width is the font size divided by 35, written with five decimals as the
  constant was. At 11.04pt it is the old 0.31543.

`endSimulateStyle` resets the rendering mode on the same condition that set it.

The italic angle is the `post` table's, as `OpenFont.getItalicAngle` gives it: whole degrees, truncated. A face
slanted by less than a degree counts as upright and is sheared, as before.

### 3.2 What does not change

The entry's flag still decides whether anything is synthesised; a face registered without `simulate-style` is
drawn as it is. The shear is still 0.3333, Word's own. The advances, and so the layout, are untouched: only the
painted glyphs differ.

### 3.3 Capability

`Docx4jFop.SIMULATE_STYLE_PER_FACE = "simulate-style-per-face"`. docx4j declares the italic file with
`simulate-style` for (italic, bold) wherever a family has an italic face and no bold one, only where the
capability is present; elsewhere it keeps declaring the regular file, which FOP shears.

## 4. Tests

In `PDFPainterTestCase`, with a `MultiByteFont` as the existing simulate-style tests use:
1. The existing tests, at 11.04pt: an upright face of unknown weight, bold italic triplet: stroked at 0.31543 and
   sheared 0.3333, as before.
2. An italic face (italic angle -11), bold italic triplet: stroked, not sheared.
3. A bold face (weight 700), bold italic triplet: sheared, not stroked, and no `0 Tr`.
4. The stroke at 22.08pt: 0.63086, Word's measured value.

## 5. What moves through docx4j

Without its own change, the stroke width of every simulated bold (the `+nobold` light faces): heavier above
11.04pt, lighter below; nothing in layout or the text layer. With docx4j declaring the italic file for (italic,
bold) under the capability, the light faces' bold italic becomes the italic face stroked, as Word draws it. The
gate is the docx4j session's: the render compares at the pixel level, since the scoreboard reads text and
position, not stroke.

## 6. Upstream

Apache `main` has the same code (`PDFPainter` lines 612 to 626 at 5be8c69b6). A JIRA and a pull request against
`main`, on Jason's OK of the text, after the gate.

## 7. Estimate

Half a day to a gated snapshot: the change and its four tests, the capability, the build.

## 8. Measured on the command line

Carlito at 24pt, the triplet (X, italic, bold) declared with `simulate-style` on each of three faces, read from the
decompressed content stream:
- on Carlito-Italic: `2 Tr 0.68571 w`, `1 0 0 -1 ... Tm`: stroked at 24/35, not sheared;
- on Carlito-Regular: `2 Tr 0.68571 w`, `1 0 0.3333 -1 ... Tm`: stroked and sheared;
- on Carlito-Bold: `1 0 0.3333 -1 ... Tm` and no `Tr`: sheared, not stroked.

Before the change all three were `2 Tr 0.31543 w` and sheared.

## 9. Gate (the docx4j session), 2026-10-07

Renderer r15, the jar of eb003c325 outside `~/.m2` (`~/fop-renderers/r15-CR-021-eb003c325/`, sha256 c5312afe...),
read with `movers.py`, which shows moves under 0.02 line parity that the score log's list hides.
- **(a) b174, docx4j unchanged, against b173 on r13: PASS.** The only mover is 5075, +8 lines in real2 and
  real-c2, which is CR-020's (r15 carries it, r13 does not), as in its own gate b170; no render errors. In 2065's
  traces every simulated bold is stroked at size / 35 (11, 13, 16, 22, 38.4 and 48pt), and the 13pt bold italic
  is still the sheared regular face, docx4j declaring the regular file.
- **(b) b175, docx4j declaring the italic file for (italic, bold) under `simulate-style-per-face`, against b174.**
  The drawing is Word's: the probe's bold italic lines are Calibri-LightItalic, unsheared, stroked at 1/35 em,
  and 2065's "Összeállítás:" likewise. But 13265 loses 5 lines (1.0000 to 0.9836), nothing else moving: the
  italic face's advances lack Word's synthetic-bold spread (about 0.02pt a glyph), as b162 found. That spread is
  docx4j's to model, so its declaration change is held until it carries it. CR-021 itself passes.

  *Corrected 2026-10-07 by the docx4j session:* 13265's -5 was not the synthetic-bold spread. Its bold italic is
  Book Antiqua, which Word draws in the real BookAntiqua-BoldItalic face, and docx4j's `MicrosoftFonts.xml`
  listed only Book Antiqua's regular file (the only one of its 91 entries missing faces), so docx4j treated it as
  a family without a bold face and sent its bold italic down the simulate path. With the entry fixed, 13265 is
  on Word's lines again (304 of 305). docx4j gates the entry fix, then the italic-file declaration on top of it.
  CR-021's PASS is unaffected.

