# CR-019: ActualText per cluster, so a PDF reader gets the right text where the glyphs do not spell it

Status: PROPOSED 2026-10-07; design and estimate, not started. Registry key `fop/CR-019` (entry added to
`tasks.yaml` by the docx4j session, uncommitted there). Capability `actual-text-clusters` (§3.4). A fix, not a
hook: FOP's text layer is wrong for any reader that reads the content stream in order, whoever produced the FO.
So it is upstream-bound (§8), and done twice as CLAUDE.md has it: a `FOP-####` branch against `trunk`, and the
fork's. Requested by Jason through the
docx4j session (docx4j-13, 2026-10-07), as the follow-up fop/CR-002 §10.2 named and fop/CR-016 §7 recommended.

**Enterprise CR-001 §6.6 items that bear on it.** Item 30 (CR-002: a ligature's ToUnicode), whose §10.2 left the
second glyph of a one-character cluster on a private-use code point because no ToUnicode entry can say that two
glyphs share one character; item 42 (CR-016: a decomposition's pieces), whose follow-up in CR-016 §7 found the
pieces right but in the wrong order; and item 26 (CR-006: a glyph shared by two characters), which ToUnicode
cannot settle either, since it has one entry per glyph. Each is a case where the glyphs, read in stream order
through ToUnicode, do not spell the source text.

## 1. The defect, and why ToUnicode cannot fix it

A PDF text layer is built from two things: the ToUnicode CMap, one text string per glyph, and the order the glyphs
stand in the content stream. CR-002, CR-006 and CR-016 made the per-glyph string as right as it can be. What is
left is structural:

- **Order.** `DefaultScriptProcessor.reorderCombiningMarks` moves a mark with a GPOS x-placement ahead of its
  base, and the mark-to-base arithmetic (`GlyphPositioningTable` line 764) depends on it (CR-016 §7). So
  "é" drawn as e plus U+0301 stands in the stream as U+0301, e. `pdftotext` and `mutool` read "́e"; PDFBox sorts
  by position and reads it right. Indic scripts reorder more: `IndicScriptProcessor` puts a pre-base matra before
  its consonant, so a syllable's glyphs are in visual order and its text comes out in visual order.
- **One character, several glyphs**, where the glyphs are not Unicode's decomposition (CR-016's rule): an Arabic
  letter drawn as a dotless base and its dots. The second glyph keeps a private-use code point (CR-002 §10.2,
  measured on four readers: every other choice reads worse somewhere).
- **One glyph, two characters** (CR-006's family): Cambria's `hyphen` for U+2010 and U+002D, a Kangxi radical and
  its ideograph. One entry per glyph; whichever character it carries, the other reads wrong.

PDF has the mechanism for exactly this: `ActualText` on a marked-content sequence (PDF 32000-1 §14.9.4) replaces
the text a reader extracts for the glyphs inside it, whatever ToUnicode says and whatever order they stand in.
FOP already writes one: `PDFPainter.drawText` puts the unhyphenated word in `/ActualText` on the structure
sequence of a hyphenated word, in accessibility mode only (`PDFContentGenerator.beginMarkedContentSequence`).

## 2. What FOP has, read on `2.11-docx4j.5`

- **The cluster is known at layout and then thrown away.** `GlyphMapping.processWordMapping` runs substitution
  and positioning with a list of `CharAssociation`s, one per output glyph, each naming the source characters the
  glyph stands for (CR-002 reads them to record meanings). `TextLayoutManager` line 988 calls it with
  `retainAssociations` false, so the list is not kept. What reaches the area tree is the mapped character
  sequence: one character per glyph, a private-use code point for a glyph with no cmap character.
- **The word area.** `WordArea` carries the mapped text, the letter adjustments, the bidi levels and the GPOS
  adjustments. It is serialised to the area tree XML (`XMLRenderer`, `AreaTreeParser`).
- **The intermediate format.** `IFRenderer.renderWord` buffers consecutive words and spaces into one
  `IFPainter.drawText(x, y, letterSpacing, wordSpacing, dp, text, nextIsSpace)` call (`TextUtil.flush`), so one
  painter call may hold several word areas. `IFSerializer` writes it as `<text>` and `IFParser` reads it back.
- **The PDF painter.** `PDFPainter.drawTextWithDP` and `drawTextWithDX` walk the text one code point at a time,
  writing each glyph into the pending `TJ` array through `PDFTextUtil` (`writeTJMappedChar`, `adjustGlyphTJ`),
  and `writeTJ` flushes the array. A marked-content sequence may sit inside a text object as long as its `BDC`
  and `EMC` are both inside it, so a cluster's `Span` can wrap its glyphs between two flushes.
- **Nesting.** `beginMarkedContentSequence` asserts that no sequence is open, because it manages the structure
  content item. A cluster's sequence has no MCID and nests inside the structure sequence; it needs its own small
  pair of methods that do not touch that state.
- **PDFBox is a test dependency** of fop-core, so an extraction test can run inside the suite.

## 3. The change

### 3.1 Clusters, defined

A cluster is a maximal run of consecutive output glyphs whose associations together cover one contiguous range of
source characters, cut as finely as that allows: a plain letter is a cluster of one glyph and one character; a
ligature one glyph and several characters; a decomposed letter several glyphs and one character; a base with its
marks several glyphs and several characters; an Indic syllable its glyphs and its characters. The association
list after `reorderCombiningMarks` gives it directly, since the reorder carries the associations with the glyphs.

The text of a cluster is its source characters in logical order, from the `FOText`, not from the mapped sequence.

### 3.2 Which clusters get ActualText

Three tiers, in rising order of cost; the first is the proposal, the others are measured (§9, open question 1):

- **Tier A, proposed.** A cluster with more than one glyph (a decomposition, a base with marks, an Indic
  syllable, a split letter), or whose glyphs are not in logical order. This is the structural case, which
  ToUnicode cannot express. A ligature (several characters, one glyph) is excluded: CR-002's ToUnicode entry
  spells it, and a reader that ignores ToUnicode is a reader that ignores everything.
- **Tier B.** Tier A, plus a single-glyph cluster whose glyph the cmap reaches from a different character than the
  source one (the shared glyph: U+2010 on the `hyphen` glyph, the ideograph on the radical's glyph). Deterministic
  from the cmap alone; closes CR-006's family. Costs one span per such character.
- **Tier C.** Every cluster whose mapped sequence differs from its source characters, which includes every
  Arabic contextual form. Correct and complete, and heavy: one span per shaped letter. Not proposed.

The rule must not depend on the glyph meanings CR-002 records, because those are final only when the font is
written: a glyph seen with one meaning at paint time can be seen with another later and lose it.

### 3.3 Where the clusters travel

1. **Layout.** `TextLayoutManager` retains associations (`retainAssociations` true). After the mapping, the
   clusters of the word are computed once, in code-point indices into the mapped text, and only those that
   qualify under the tier are kept: for each, start, glyph count and source text. A word with none carries null.
2. **Area tree.** `WordArea` gains the cluster list, with a constructor overload; `XMLRenderer` writes it as an
   attribute of `<word>`, never as character data or a child element, and `AreaTreeParser` reads it, so the
   area-tree round trip (the 770-case parser test) keeps it. docx4j's `Paginate` counts every character inside
   `<word>` and `<space>` and ignores attributes, and its export-fo tests read `<word>` by `getTextContent()`
   (docx4j session, 2026-10-07), so an attribute is the one safe shape there.
3. **Intermediate format.** `IFRenderer.TextUtil` concatenates the clusters of the words it buffers, offsetting
   each by the buffered text before it, and passes them to a new `IFPainter.drawText` overload taking them.
   `AbstractIFPainter` gives the overload a default that drops the clusters and calls the old one, so the PS,
   AFP, PCL, Java2D, bitmap and SVG painters are unchanged. `IFSerializer` writes a `clusters` attribute on
   `<text>`; `IFParser` reads it. The attribute is in code-point indices of the text: the dp array is indexed by
   UTF-16 unit and is wrong after a supplementary character (CR-005), and clusters must not inherit that.
4. **PDF.** `PDFPainter.drawTextWithDP` and `drawTextWithDX`, at a cluster's first glyph: flush the `TJ` array,
   write `/Span <</ActualText (text)>> BDC`; after its last glyph: flush and write `EMC`. The text is escaped as
   the hyphenation ActualText is (`PDFText.escapeText`, UTF-16BE with a byte-order mark where needed). Two new
   methods on `PDFContentGenerator` write the pair without touching the structure-sequence state.
5. **Interplay with the existing ActualText.** When the structure sequence already carries `/ActualText` (a
   hyphenated word in accessibility mode), no cluster spans are written inside it: the outer replacement covers
   the whole sequence, and a nested one would be meaningless.

### 3.4 Capability

`Docx4jFop.ACTUAL_TEXT_CLUSTERS = "actual-text-clusters"`, so docx4j's gate expects logical stream order only
where the renderer writes ActualText (asked for by the docx4j session, 2026-10-07; it gates every fork change
through `FopCapabilities`). The FO is unchanged; the capability only says the renderer does this.

### 3.5 Off switch

A renderer option, `actual-text` in the PDF renderer configuration (and `FOUserAgent` or the renderer config
object, whichever the PDF renderer options use), default on. A producer that wants the smaller file can turn it
off. Upstream may prefer default off; the branch for the pull request can carry the opposite default (§8).

### 3.6 Unchanged

Ink, byte for byte: the glyphs, their positions and the `TJ` arithmetic are the same; only `BDC`/`EMC` operators
and the breaks between `TJ` arrays are added. ToUnicode: CR-002, CR-006 and CR-016 stay as they are, and a reader
that ignores ActualText reads what it reads today. Every renderer other than PDF.

## 4. Tests (FOP's own, before any docx4j gate)

1. **Cluster computation**, unit-tested on the fonts CR-002's and CR-016's tests use: a decomposed letter (two
   glyphs, one character), a base with two marks, a ligature (excluded under tier A), a plain word (no clusters),
   an Indic syllable with a pre-base matra if a test font with the tables is available.
2. **Area tree and IF round trip**: `XMLRenderer` to `AreaTreeParser`, and `IFSerializer` to `IFParser`, keep the
   clusters; a text with a supplementary character before a cluster keeps the right offsets.
3. **PDF content stream**: the span wraps exactly the cluster's glyphs, inside the text object, with the `TJ`
   array flushed on both sides; the ink operators are unchanged against the stream without the option.
4. **Accessibility mode**: the span nests inside the `/P <</MCID n>>` sequence; a hyphenated word gets the outer
   ActualText and no inner spans; the structure tree is unchanged.
5. **Extraction with PDFBox** (in the suite): `PDFTextStripper` reads the decomposed letter in logical order.
   Outside the suite, by hand as CR-002 §10.2 did, on the same sample: poppler (`pdftotext`), mupdf (`mutool`),
   pdf.js and PDFium, recorded in this CR before the gate. A reader that mishandles nested spans would be a
   reason to change the shape, not to ship and find out.
6. **Off switch**: with it off, the stream is byte-identical to today's.
7. **The full suite**: unchanged apart from the new tests.

## 5. Risks

- **Readers.** Four readers are measured before the gate (test 5). pdf.js and PDFium have both changed their
  ActualText handling in recent years; what they do today is what counts, and it is measured, not assumed.
- **File size.** Each span is about 45 bytes before Flate, plus a broken `TJ`. Under tier A, after docx4j's
  `-ccmp` (CR-016 §7), the Latin corpora have few clusters; Arabic and Indic documents have many. The gate
  measures size per affected document (§7).
- **Search and selection.** A viewer selects the glyphs and copies the ActualText; a search for the source text
  finds it. A viewer that searches the glyphs' ToUnicode instead still finds what it finds today.
- **PDF/UA and tagged PDF.** A marked-content sequence without an MCID, nested inside a structure content item,
  is permitted, and ActualText on a `Span` is the documented use. veraPDF is not in the suite; if the docx4j side
  has it, one tagged sample through it is worth the minute.
- **The IF buffer.** Clusters from several words in one `drawText` are offset by the buffered text, which
  `StringUtils.processSoftHyphen` may shorten; the offsets are computed after it.
- **Upkeep.** `TextLayoutManager`, `WordArea`, the IF serialiser and parser, and `PDFPainter` all change upstream
  occasionally; this is carried through each merge of Apache `main` (fop/CR-009's pattern) until it lands.

## 6. Reachability, as the docx4j session offered to measure

- After docx4j 3e05db3af (`-ccmp` on mark-free simple-script spans), the seven Cambria documents no longer
  exercise this. What does: spans that keep `ccmp` because they carry a combining mark of their own; Arabic
  letters split into base and dots; Indic text; the shared-glyph cases under tier B. The docx4j session will
  count mark-bearing clusters per script across the corpora, which sizes the file-size risk as well.
- A Word probe docx, as offered: decomposed Latin (e + U+0301, a + U+0308), Greek and Cyrillic with combining
  marks, an Indic line and an Arabic line. Word's PDF gives the target extraction text per cluster. Yes, please
  write it; it is also the sample for test 5's four readers.

**Measured by the docx4j session, 2026-10-07** (four corpora, 450 distinct documents; base plus combining-mark
clusters counted from the docx text, `w:t` only, hidden text not excluded):
- Cyrillic: 2 documents, 310 clusters (3489 has 309 decomposed й as и + U+0306; 3310 has 1). Bengali: 1 document
  (11334), 27. Syriac: 1 document (11334), 4. Devanagari: 1 document (394), 4. Latin, Greek, Arabic and Hebrew: none;
  Arabic text totals 93 characters in 3 documents. The corpora are European.
- No run in any corpus asks for ligatures, so docx4j's `-ccmp` covers every precomposed Latin, Greek and Cyrillic
  letter there (147,074 Latin, 20,228 Cyrillic, 8,511 Greek).
- Text layers against Word's (mark-bearing words, NFC): 3489 agrees, its 309 decomposed й in order; 394 has one
  mis-order, "है," read as ह , ै (U+0939 U+002C U+0948), the vowel sign after the comma, where Word reads "है" (the
  mechanism is not read yet: `DefaultScriptProcessor` moves a mark before its base, not after a following glyph,
  so this is something else to read when implementing); 11334 differs on many words, but Word's own text layer
  is wrong there too (Bengali "রাখনেে"), so Word is no reference for logical order on that document.
- So one confirmed instance in the corpora, and the file-size risk of tier A is negligible on this sample. The
  real exposure is non-European documents, which these corpora do not hold. Per-document detail is in the docx4j
  session's scratchpad.

**The probe**, `actualtext-clusters.docx` (docx4j a7b598481, on the share at `X:\fidelity\corpus\`, indexed in
`corpus.txt`; awaiting Jason's Word run). Fourteen cases, each on the line after a Calibri label: L1 to L3 Latin
precomposed, decomposed and mixed in Cambria; L4 and L5 Latin decomposed in Calibri and Times New Roman; L6
Vietnamese stacked marks; L7 and L8 Greek precomposed and decomposed; L9 Cyrillic decomposed (3489's shape); L10
and L11 Devanagari in Nirmala UI with a pre-base vowel sign, a conjunct and 394's "है,"; L12 Bengali with pre-base
and two-part vowel signs and a precomposed য়; L13 Arabic with harakat and lam-alef; L14 Hebrew with niqqud.
docx4j's render on r13 (cand79) already shows the shapes this CR is for: L2, L3, L6 and L8 read marks ahead of
their base ("r ́esumé", "σ ́ημερα"); L10 and L11 the pre-base i-matra first ("िहन्दी") and "ह,ै"; L12 the
precomposed য় split with its nukta first ("কোথা ়য"); L13 and L14 marks displaced. L1, L7 and L9 read right. Two
docx4j-side findings came with it, recorded in the Enterprise register (8c055aa): a span holding a mark keeps
`ccmp`, so a precomposed é in it is decomposed and mis-ordered too (L3; the gap 3e05db3af leaves, which this CR
closes); and Carlito, standing in for Calibri, has no combining marks, so FOP draws its missing glyph (L4).

## 7. Gate (the docx4j session's)

ActualText changes only the text layer, so a pass is: page rasters identical on every document; the harness's
scores unmoved (it pairs by PDFBox's position sort, which already reads these clusters right); and a new
stream-order extraction check (`pdftotext` and `mutool`) on the affected documents and the probe reading each
cluster in logical order. The target is the source text in logical order; Word's PDF is the check where it
agrees, and not the reference where it is wrong itself (11334's Bengali, §6). Also recorded: PDF size before and after per affected document, and the probe's
extraction on the four readers.

## 8. Upstream

A fix, so upstream first: a JIRA (search first for an existing report of wrong extraction order for Indic or
decomposed text; there may be one) and a pull request from a `FOP-####` branch against `trunk`, in the worktree at
`../fop-upstream-wt`, with the IF format change called out, since Apache guards that format. On Apache `main` the
painter and IF code should be checked for drift before the design is assumed to transfer. Posting to JIRA needs
Jason's OK on the text, per the memory note.

## 9. Open questions

1. **Tier.** A as proposed, or B (shared glyphs too)? B is deterministic and closes CR-006's family; the cost is
   one span per such character, which the reachability count will size.
2. **Scope by script.** Should the first step restrict spans to the scripts measured (Latin, Greek, Cyrillic,
   Arabic, the Indic scripts), or apply to any cluster the rule selects? The rule is script-independent; a
   restriction would only be caution.
3. **Default of the off switch.** On in the fork. For upstream, on or off is Apache's call; the pull request can
   state the size cost and offer either.
4. **Tagged PDF.** Confirmed by reading the specification, not by a checker. veraPDF is not installed on the
   docx4j side (2026-10-07); running one tagged sample through it needs it installed, which is Jason's call.
5. **Readers.** Which four to measure: poppler, mupdf, pdf.js and PDFium as in CR-002 §10.2, plus PDFBox in the
   suite. Any reader docx4j's users name?
6. *Answered 2026-10-07:* a capability is wanted, `actual-text-clusters` (§3.4).
7. *Answered 2026-10-07:* `FOPAreaTreeHelper` does not read `<word>`; `Paginate` counts the characters inside
   `<word>` and `<space>` and ignores attributes. So the clusters go on `<word>` as an attribute (§3.3, step 2).

The docx4j session's lean on 1, for Jason to weigh: tier A first, since it is what the corpus measurements can
test, and tier B waits on the reachability count. Questions 1 to 3 are Jason's.

## 10. Estimate

About five to six days in the fork, to a gated snapshot:
- clusters at layout, on `WordArea`, through the area-tree XML: about a day and a half;
- the IF overload, serialiser and parser, and the PDF painter with the off switch: about a day and a half;
- the seven tests, the four readers by hand, and what they find: about a day and a half;
- the full build, the README row, the hand-off: half a day;
- the upstream branch against `trunk`, the JIRA text and the pull request: about a day, after the gate.
