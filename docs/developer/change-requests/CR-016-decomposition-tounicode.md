# CR-016: a decomposition's glyphs each publish their piece, not the precomposed letter

Status: DONE 2026-10-05, gated PASS by the docx4j session (§5) and merged to `2.11-docx4j.5` by fast-forward
(28a18b70c); unreleased. The full build on the branch passes, 3765 tests, 0 failures, checkstyle and spotbugs clean. Registry key `fop/CR-016`. No capability: docx4j has no workaround that depends on it. A correction to
`fop/CR-002`, so it went upstream with CR-002's pull request,
[#116](https://github.com/apache/xmlgraphics-fop/pull/116) (FOP-3345), not as an issue of its own. Pushed there
2026-10-05 at Jason's word as 696830652 on branch `FOP-3345`: the fop-core suite against Apache `main` gives 3684
tests and 0 failures, checkstyle clean, and the two decomposition tests fail without it. A comment on the pull
request explains it (issuecomment-5986995470; text in `../fop-upstream-prs/FOP-3345-decomposition-comment.md`).

Enterprise CR-001 §6.6 item 42. Found by the docx4j session (ledger8 slice A, corpus documents 8371, 6693 and 7742,
about 1,900 class 2 lines) and handed to the fork at Jason's OK on 2026-10-05.

## 1. The defect, and where it came from

Cambria Regular's GSUB `ccmp` (lookup 10, a multiple substitution) decomposes precomposed letters: ά into `alpha`
and a tonos mark no character maps to (`glyph00646`), ü into `u` and `uni0308`, ć into `c` and `acutecomb`. Words
are drawn correctly. The text layer is wrong.

CR-002 (`MultiByteFont.recordGlyphMeaning`) records, for a substituted glyph, the characters its association
covers, which the ToUnicode CMap publishes. In a multiple substitution the first output glyph records the source
character and the others nothing. So `alpha` recorded ά. A plain α reaches the same glyph through the cmap, and a
glyph reached that way records nothing, so it never contests the meaning. A glyph has one ToUnicode entry, so every
α in the subset was published as ά, and likewise o, e, u, c, and A as Å.

Measured on a line in Cambria Regular (`cambria.ttc`, sub-font Cambria), `à a é e ü u ά α ό ο ć c Å A`, read by
`pdftotext`:

| build | text |
|---|---|
| `2.11-docx4j.1` (2.11 without CR-002) | `à a é e ü u α α ο ο ć c A A` |
| `2.11-docx4j.5` (with CR-002) | `à̀ à é́ é ü̈ ü ά ά ό ό ć́ ć Å Å` |
| this change | `à a é e ü u ά α ό ο ć c Å A` (decomposed: `a` U+0300, `α` U+0301, `A` U+030A) |

**So item 42 is a regression CR-002 introduced.** It has been in the fork since `2.11-docx4j.2`, and pull request
#116 carries it. Before CR-002, plain letters were right and only the marks without a cmap entry were lost to
private-use code points, so ά read α and Å read A. With CR-002 every plain letter that a decomposition also uses
read as the accented letter. A decomposed letter whose mark has a cmap entry read with its accent twice: the
base published à and the mark its own U+0300. CR-002's tests and gates did not catch it. Its Latin samples used
ligatures, its decomposition case was Arabic, and the docx4j corpus used Cambria only once the VM's fonts were
supplied (b82v).

## 2. The change

`MultiByteFont.decompositionPiece`. A multiple substitution may split one character into exactly as many glyphs as
its Unicode canonical decomposition (NFD) has characters. Then each glyph records its own piece of the
decomposition, in order: `alpha` α and the tonos glyph U+0301; `u` u and `uni0308` U+0308. The base glyph then
records its plain letter, which agrees with every plain use of the glyph. The mark records its combining
character, including a mark no character maps to, which before published a private-use code point.

Every other split keeps CR-002's rule: the first glyph records the character and the others nothing. That covers:
- a ligature;
- a single substitution;
- a decomposition the font draws in more or fewer glyphs than Unicode's;
- a letter Unicode does not decompose. An Arabic yeh drawn as a dotless base and its dots is one: yeh has no
  canonical decomposition. So the Arabic behaviour CR-002 measured and gated does not move.

The rule needs no knowledge of the font: the decomposition is Unicode's (`java.text.Normalizer`, NFD), and the
correspondence is by count and order. The extracted text is decomposed, canonically equivalent to what the document
wrote and identical to it after NFC normalisation.

## 3. Tests

In `MultiByteFontTestCase`, with a mocked GSUB as CR-002's tests use:
- `testDecompositionGivesEachGlyphItsPiece`: à split into `a` and a mapped grave accent records `a` and U+0300.
  Before the change, `a` recorded à.
- `testDecomposedBaseIsPublishedAsThePlainLetter`: ά split into alpha and an unmapped tonos records α and U+0301,
  and the subset publishes α and U+0301 for their selectors. Before the change, alpha recorded ά.
- `testSplitUnlikeTheDecompositionKeepsTheFirstGlyphRule`: à split into three glyphs keeps CR-002's rule.

The first two fail without the change. CR-002's own tests are unchanged and pass, including the Arabic-shaped
`testSecondGlyphOfOneCharacterRecordsNothing`.

## 4. Measured on the command line

On the line in §1, the page content stream and the embedded font of the two PDFs, before and after, are byte for
byte the same. Only the ToUnicode CMap object differs, so layout and drawing cannot move. `pdftotext` reads the line
exactly as written after NFC. `mutool` lists each combining mark before its base, by position. It did the same
before the change, and it is mutool's ordering.

## 5. What moves through docx4j

The text layer of every document set in a font whose substitutions decompose precomposed letters along Unicode's
lines, Cambria Regular above all; layout nowhere. The docx4j session counted nine class 2 documents in Cambria
Regular with mislabelled glyphs, about 1,900 lines, 8371 alone 1,517. Word's text is precomposed, so the gate's
harness must compare after NFC.

**Gated 2026-10-05, PASS** (the docx4j session). Install r9 (this branch) against r8 (`.5` with CR-015, without
CR-014, whose own gate moved nothing). Both scored with the same docx4j jars, through a harness that now compares
NFC on both sides, against the r8 baseline re-read the same way. Every page of every mover rasterises identically on
r8 and r9 (pdftoppm, 100dpi, grey). Nine movers, all up, all class 2, page counts kept:

| document | line parity | lines matched |
|---|---|---|
| real3 8371 | 0.1630 to 0.9918 | +1520 |
| real 6693 | 0.2118 to 0.9866 | +289 |
| real2 6195 | 0.8547 to 0.9658 | +65 |
| real3 2065 | 0.9102 to 0.9373 | +56 |
| real3 3236 | 0.9044 to 0.9334 | +17 |
| real 11126 | 0.8681 to 0.9560 | +8 |
| real3 299 | 0.9175 to 0.9897 | +7 |
| real 7742 | 0.9600 to 1.0000 | +5 |
| real 2852 | 0.9000 to 1.0000 | +5 |

real-c2 and the probes did not move. No document in another script moved, and six Russian and Arabic documents
spot-checked have identical ink on every page. The docx4j fidelity baseline is now b99r9.

## 6. Not addressed

- A decomposition into a different number of glyphs than Unicode's keeps CR-002's first-glyph rule. If its base
  glyph also has a cmap character, plain uses of that glyph still read as the source character. The full answer
  is `ActualText` per cluster (CR-002 §10.2).
- Cambria's `hyphen` glyph is shared by U+2010 and U+002D, and ToUnicode gives `-` (ledger8 A4). That is CR-006's
  family, not this change.
