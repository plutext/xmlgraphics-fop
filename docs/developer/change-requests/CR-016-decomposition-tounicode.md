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

## 7. Follow-up, 2026-10-07: the mark-first stream order, and docx4j's `-ccmp`

Review requested by the docx4j session (docx4j-13) of its commit 3e05db3af, at Jason's word; its measurements are
quoted as it reported them (renderer r13 = 7ae4c950a, which carries this CR).

**What this CR leaves.** Each glyph's ToUnicode entry is right, but the glyphs stand in the content stream mark
first. `DefaultScriptProcessor.reorderCombiningMarks` (step 5 of `GlyphMapping.processWordMapping`) moves a mark
that has a GPOS x-placement ahead of its base in the mapped sequence, and the painter emits glyphs in that order.
2065's running head "Médiafigyelés" on r13: M (gid 42) at 0, U+0301 (gid 20) at 13.288, e (gid 12) at 8.965.
`pdftotext` and `mutool` read in stream order and give "Ḿediafigyeĺes"; PDFBox sorts by position and reads it
right, which is why the docx4j harness had paired it. The order is FOP's, not this CR's: before CR-016 the same
stream read wrong in a different way.

**Why the order is load-bearing.** `MarkToBaseSubtable.position` (`GlyphPositioningTable`, line 764) gives the mark
`baseAnchor - markAnchor` as its x-placement and subtracts nothing for the base's advance. That lands the mark
correctly only when it is painted at the base's origin, before the base. The Khmer branch (line 772), where the
script processor keeps marks after their base, is the exception that subtracts the base's width. Emitting marks
after their bases is therefore not a change to the reorder step alone: it is a change to the mark-to-base,
mark-to-ligature and mark-to-mark arithmetic, and to every renderer that consumes the adjustments. Not
recommended, and not a plausible upstream patch.

**docx4j's workaround (3e05db3af, 2026-10-06, unpushed).** `RunFontSelector` writes `fox:gsub-features="-liga -ccmp"`
(hook `gsub-features`, fop/CR-001) on a span of Latin, Greek, Cyrillic or Common characters that carries no
combining mark of its own (`noComposition`); a span with a mark keeps `ccmp`, which is the risk Enterprise CR-001
§6.6 item 42 named; runs that ask for ligatures are unchanged; off switch
`docx4j.convert.out.fo.simpleScriptCcmp=true`. This supersedes "docx4j workaround: none" in item 42.
Measured by the docx4j session, gate b160 against b159 on r13, four corpora and the probes: no line, page or probe
moved; the seven Cambria documents' text layers hold no combining marks (8371 had 7,799; 6693 769; 6195 175; 2065
107; 3236 34; 11126 8; 299 7). The ink changes at the accents: Cambria now draws the precomposed glyph, which is
what Word draws (é in 2065; ή on 8371 page 4 as one glyph, gid 603 in Word's subset), instead of base plus a
GPOS-placed mark (8371 page 4 about 12,300 pixels at 300 dpi; 6693 page 2 181; 2065 page 3 anti-aliasing only).

**Fork review of the workaround: acceptable.** What it rests on, read in this tree:
- FOP maps characters to glyphs through the cmap before GSUB runs, and its own pre-cmap decomposition
  (`CharNormalize`) covers Indic two-part vowels only. So with `ccmp` off, a precomposed letter with a cmap entry
  reaches the painter as one glyph, one character, in order. A precomposed letter without a cmap entry was not
  helped by `ccmp` before either, so nothing is lost there.
- What remains mark first: a span that carries a combining mark of its own, and every other script. Those keep this
  CR's per-glyph ToUnicode and FOP's order.
- The risk I can name: a font whose `ccmp` does something on plain letters other than decompose them (composing a
  sequence, or substituting a form). None of the corpus's faces is known to; the off switch covers one that does.

**Fork-side options, and the recommendation: none now.** Reversing the stream order is rejected above. A
renderer-side rule of "no `ccmp` on simple scripts unless the text has marks" would put the producer's knowledge of
Word at the wrong layer, where the `gsub-features` hook already lets the producer say it per span; recomposing
base plus mark to the cmap's precomposed glyph after GSUB would be a substitution stage that overrides the font's
own tables. Neither is upstreamable. The fork item that makes extraction order-independent for what remains is
`ActualText` per cluster, CR-002 §10.2's follow-up, which is also the shape upstream could take.
