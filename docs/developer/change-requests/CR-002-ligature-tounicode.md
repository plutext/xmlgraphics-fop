# CR-002: a ligature's `ToUnicode` entry is a private-use code point, not its letters

Status: DRAFT, reviewed against the code and measured 2026-09-27 (§9); no code yet.
Raised 2026-09-25 while designing `CR-001`. Registry key `fop/CR-002`. Upstream-bound: this is a defect in Apache
FOP with no docx4j specificity, so it goes upstream first.

This is **Enterprise CR-001 §6.6 item 30**, which is the canonical entry and
records the docx4j-side workaround. The defect was already known there: it was
found on the corpus in docx4j 17.0.5, and the `+noliga` font twin was built partly
for it. What this CR added on 2026-09-25 was the mechanism in §2, traced from
FOP's code. The symptom was not new; the cause was.

## 1. The defect

Any PDF FOP produces with ligatures enabled, which is the default, carries a
`ToUnicode` entry for each ligature glyph that points at a private-use code point
rather than at the letters the ligature stands for. Text extraction, search, copy
and paste, and screen readers all receive U+E000 and upward where the document
says `fi`.

The docx4j session measured it first: a third of the lines of a French corpus
document extracted `ti` as U+E000, which is how their line-parity score noticed
at all. The chain below was then verified here from the code.

## 2. The chain

1. `MultiByteFont.mapGlyphsToChars` walks the substituted glyph sequence. For
   each glyph it tries `findUnsubstitutedCharacter` first, which succeeds only
   where substitution left the glyph alone.
2. A ligature glyph was produced by substitution, so that returns nothing and the
   code falls through to `findCharacterFromGlyphIndex(gi)`.
3. A ligature glyph normally has no cmap entry, so that calls
   `createPrivateUseMapping(gi)`, which mints a code point from `nextPrivateUse`.
   That field is initialised to `0xE000`, so the first such glyph in a font
   becomes U+E000, the next U+E001, and so on up to U+F8FF.
4. The painter hands that code point to `MultiByteFont.mapChar`, which calls
   `CIDSubset.mapCodePoint(glyphIndex, codePoint)`. That records the code point as
   the glyph's unicode in `usedCharsIndex`.
5. `CIDSubset.getChars()` builds a `char[]` from `getUnicode(i)` for every used
   glyph, and `PDFFactory` hands exactly that array to `PDFToUnicodeCMap`.

So the private-use code point minted in step 3 to give the glyph *some* character
identity becomes the glyph's published meaning in step 5. The mechanism is sound
for its original purpose, which is round-tripping a glyph that has no Unicode
meaning at all. It is wrong for a ligature, which has a perfectly good Unicode
meaning of two or more characters.

## 3. Why it goes unnoticed

The page looks right. The glyph is drawn correctly and the advance is correct, so
no visual comparison and no rendering test sees anything. The defect only appears
when someone selects text, searches the document, or runs a screen reader over it,
and the affected characters are the commonest ligatures in Latin text: `fi`,
`fl`, `ff`, `ffi`, `ti` in some fonts.

It is a searchability and PDF/UA defect rather than a fidelity one. That is
probably why it has survived: FOP's own accessibility suite checks structure, and
its layout tests check geometry, and neither reads the text layer back.

## 4. Why it is not fixable where CR-001 works

`mapGlyphsToChars` returns a `CharSequence` with one character per glyph. It
cannot express "this glyph means two characters", so no change confined to that
method can fix this.

`PDFToUnicodeCMap` is likewise built from a flat `char[]` indexed by glyph
selector. It does handle a two-element case, but only for a surrogate pair
standing for one code point, not for two distinct characters.

The information needed is already present. A `GlyphSequence` carries a
`CharAssociation` per glyph recording which characters produced it, and that is
the same mechanism the Kangxi radical fix used to prefer the originating
character. What is missing is a path for a multi-character association to reach
the CMap.

## 5. The fix, read against the code

*Read with §9, the review of 2026-09-27, which corrects the key in step 1, the CIDFull
claim in step 2, and widens the scope beyond ligatures.*

Verified against the source on 2026-09-27, which changed two things the first draft said.

**The information is there and my own earlier fix already reads it.**
`findUnsubstitutedCharacter` takes `gs.getAssociation(i)` and returns early when
`a.getCount() != 1`. That early return is exactly the ligature case: a glyph produced from
two or more characters. Those characters are `ca[a.getStart()]` through
`ca[a.getStart() + a.getCount() - 1]`. So the Kangxi radical fix already stands at the
right place holding the right data, and simply discards it.

**The steps.**

1. `MultiByteFont.mapGlyphsToChars` records, per glyph index, the character sequence the
   association names, whenever the count exceeds one. The returned `CharSequence` keeps
   one char per glyph, because that is its contract; the sequence goes into a side map.
2. `CIDSet` gains a way to read a per-glyph sequence, and `CIDSubset` stores it alongside
   `usedCharsIndex`. `CIDFull` returns single characters as today. The interface has two
   implementors, so this is small; it must be an abstract method rather than a Java 8
   default, for the checkstyle reason recorded in `CR-001` §7.
3. `PDFFactory` passes the sequences to `PDFToUnicodeCMap` instead of `getChars()`.
4. `PDFToUnicodeCMap` emits a `bfchar` whose destination is a string where the sequence is
   longer than one character.

**The obstacle the first draft missed, and it is the real work.**
`PDFToUnicodeCMap` does not take a per-glyph list. It takes a flat `char[]` and derives
the glyph selector from array *position*, with a surrogate pair consuming two slots.
Variable-length entries destroy that scheme. And `partOfRange` groups consecutive entries
whose code points are contiguous into a `bfrange`, which has no meaning for a
multi-character destination, so such entries must be forced out of ranging and into
`bfchar`.

So step 4 is not "emit a string": it is changing the CMap writer's input from a positional
`char[]` to a per-glyph structure, keeping the existing range optimisation for the
single-character majority and excluding multi-character entries from it. That is the bulk
of the change and where the risk sits, because every existing PDF's `ToUnicode` comes out
of that writer.

**What must not change.** The private-use mapping stays: it is still the glyph's identity
in the font's own encoding, and still the right answer for a glyph with no character
behind it at all. Only its use as the glyph's *published meaning* is wrong.

## 5a. The writer change, designed

Read the writer on 2026-09-27. It is 412 lines whose every helper, `partOfRange`,
`sameRangeEntryAsNext`, `startOfRange` and `endOfRange`, indexes a `char[]` positionally,
with a surrogate pair occupying two slots.

**The representation becomes `String[]`, one entry per glyph selector.** A surrogate pair
stops being two slots and becomes one entry of length two, which removes the positional
hack rather than adding to it. A ligature is likewise one entry, of length two or more.

**Rangeable means "a single code point".** An entry may join a `bfrange` only when it is
one code point: length one, or length two forming a valid surrogate pair. A ligature is
two code points and can never range, so it is forced into `bfchar`. That single rule
replaces the surrogate special-casing scattered through the four helpers.

**The safety net comes first, and now exists.**
`ToUnicodeCharacterisationTestCase` pins the writer's current output: contiguous runs
packing into one range, scattered points becoming chars, a surrogate pair staying one code
point, the single-byte code space, and the mixed hex case, destinations lower-case while
the code space is upper-case, which is a byte-level property of every PDF FOP writes and
is easy to lose by accident. It also pins the defect itself: three consecutive private-use
ligature glyphs currently pack into one `bfrange`, so the text layer reads U+E000 upward.

Those tests were written against the real output, not from the specification: three of the
six expectations were wrong first time, all on hex case. That is the point of a
characterisation test, and it is why the refactor gets one before it starts.

## 6. Measurement

The gate is text extraction, not geometry, which is the reverse of most FOP work.

- A rendering of text containing `fi`, `fl`, `ff` and `ffi` in a font with a
  `liga` table, extracted and compared against the source characters. Before the
  fix the extraction yields private-use code points; after it, the letters. This
  must fail first.
- The same rendering compared byte for byte in its drawn content, to show the
  fix changes only the text layer and not a single glyph or advance.
- A CJK document, to show the Kangxi radical fix still holds, since it works the
  same association machinery.
- A font with genuinely meaningless glyphs, to show the private-use fallback
  still applies where there is no character to publish.

## 7. Relation to CR-001, to the twin, and to the corpus

The twin does not make this go away even where it applies. Item 30 records the
limit plainly: **a run that does ask for ligatures still gets the private-use text
layer.** Since Word 365's default template asks for contextual ligatures in every
new document, that is the common case rather than the exception, and it is
unaffected by anything `CR-001` does.

`CR-001` switches ligatures off where Word asks for none. On the corpus that
hides this defect rather than fixing it, because a suppressed ligature has no
glyph to mis-map. The two are independent: this one matters wherever ligatures
are correctly applied, which after `CR-001` is every document that does ask for
them, and Word 365's default template asks for contextual ligatures in every new
document.

So the order matters less than it appears, but this is the item with the wider
audience: it affects every FOP user with the default configuration, not only
docx4j.

## 8. Upstream

Filed as a JIRA with §2 as the description and §5 as the proposal, before any
patch. There is no docx4j specificity, so there is no reason for the fork to
carry it ahead of Apache, beyond the fork getting it sooner if review is slow.

## 9. Review of 2026-09-27: measured, and what it changes

Measured with the fork's command line (`org.apache.fop.cli.Main` on the
`2.11-docx4j.2-SNAPSHOT` jars, no docx4j in the path), fonts from `/usr/share/fonts`,
the text layer read back with `pdftotext` and `mutool draw -F txt`, and the `ToUnicode`
streams read from `mutool clean -d` output. The docx4j session measured its own pipeline
the same day; its answers are in §9.6. Nothing here is inferred from the code alone.

### 9.1 The defect is wider than ligatures

Carlito, `script="latn"`, the text `office affluent fifty flow ti fi`, extracts as
`oﬃce aﬄuent ﬁ\uE000y ﬂow \uE001 ﬁ`. Two things in that line:

- `ft` and `ti` are the private-use case of §2: glyph 91 and glyph 2210 were minted
  U+E000 and U+E001. This is item 30's `ti`, reproduced outside docx4j.
- `fi`, `fl`, `ffi`, `ffl` come out as U+FB01 to U+FB04, because Carlito's cmap maps those
  presentation forms, so `findCharacterFromGlyphIndex` finds a real code point and nothing
  is minted. Tolerable for extractors that normalise compatibility characters, but not
  what the document says.

Noto Sans Arabic, `script="arab"`, the text `السلام عليكم`, extracts the medial yeh as
U+E001 U+E000. The two glyphs are 18 `uni066E.medi.wide` and 318
`twodotshorizontalbelowar`: a `ccmp` decomposition into dotless base plus dots, then a
contextual form. Each is a single substitution with an association of count one, and each
has no cmap entry, so each was minted. The rest of the word came out as presentation forms
(U+FE8E and so on) for the same cmap reason as Carlito's `fi`.

So the key the design in §5 uses, `a.getCount() != 1`, is the wrong key. The condition
that matters is that `findUnsubstitutedCharacter` returned nothing, which is "this glyph
was substituted", whatever the count. Ligatures are the count-greater-than-one case of
it; Arabic contextual forms and decompositions are the count-one case, and they are the
common case in Arabic text. The docx4j session's own render of its Arabic probe through
Apache FOP 2.11 counted 105 private-use characters against 308 base letters and 441
presentation forms.

### 9.2 What a substituted glyph publishes

The rule that replaces §5 step 1: in `mapGlyphsToChars`, whenever
`findUnsubstitutedCharacter` returns zero and the association is non-null with a count
above zero, record the association's characters as the glyph's published meaning, keyed
by glyph index. The character put into the returned `CharSequence` does not change: it is
still the cmap's code point or the minted private-use one, because that is the identity
`findGlyphIndex` maps back to the glyph at render time. Meaning and identity separate;
only the meaning reaches the CMap.

Two consequences to decide, both recommended:

- A cmap-mapped ligature (`fi` at U+FB01) is substituted too, so under this rule it
  publishes `fi` rather than U+FB01. That changes text layers that were already tolerable,
  so the measurement must show it, but it is what the document says and what Word writes.
- A disjoint association, which `CharAssociation.join` produces when a ligature's
  components had ignored mark glyphs between them, must be read through
  `getSubIntervals()`, not `ca[start .. start + count)`. The skipped marks stay in the
  glyph sequence as glyphs of their own and publish themselves; reading the flat interval
  would publish them twice.

### 9.3 One character, several glyphs: the open decision

A multiple substitution replicates one association onto every output glyph
(`GlyphSubstitutionTable` line 407, `CharAssociation.replicate`). Under §9.2 both glyphs
of the yeh above publish U+064A. `ToUnicode` cannot express many glyphs to one character;
the mechanism the spec gives for that is `ActualText`, which FOP writes only around
hyphenated words in accessibility mode (`PDFPainter`, `beginTextObject`). Measured by
hand-editing the Arabic sample's CMap:

| second glyph's destination | `pdftotext` | `mutool` |
|---|---|---|
| U+064A again | `عل يكم`: one letter, and a spurious space | the same |
| empty string `<>` | `عليكم`, correct | `علي�كم`, U+FFFD |
| today's U+E000 | private-use | private-use |

Neither option is right in both readers. The choice, to be made in the measurement step
after also checking pdf.js and Acrobat, and recorded here:

- (a) the first glyph of a shared association publishes the characters and the rest
  publish an empty string: right in poppler, a replacement character in mupdf;
- (b) the rest keep their private-use code point: no reader gets it right, nothing gets
  worse than today;
- (c) `ActualText` per cluster, a content-stream change, out of this CR's scope and noted
  for the JIRA.

"First" is in glyph-sequence order, and since the meaning map is per glyph index, a
glyph that is only ever a follower (the dots glyph) is recorded as one; a glyph seen first
in one place and second in another keeps its first recording.

### 9.4 Where the meaning lives, and who writes it

- The map is on the `MultiByteFont`, keyed by glyph index, filled at layout in
  `mapGlyphsToChars`.
- It reaches the subset at render, in `MultiByteFont.mapCodePoint(cp)`, at the call to
  `cidSet.mapCodePoint(glyphIndex, cp)`: the font hands the meaning across at the same
  moment. That is the one new `CIDSet` method, abstract, two implementors.
- `CIDFull` is not "single characters as today" as §5 step 2 says: its `getChars()` comes
  from `font.getChars()` by glyph index, so full embedding must consult the same map by
  glyph index or the fix is subset-only.
- Lifetime: the font instance must be shared between layout and render. It is, in FOP's
  own single-run pipelines and in docx4j's main pass (§9.6). The two-process
  intermediate-format and area-tree paths already lose the private-use mints, since the
  rendering JVM never minted them, so nothing worsens there; the JIRA should state the
  boundary.

### 9.5 The writer

- Keep the `char[]` constructor as an adapter that joins a surrogate pair into one entry,
  so the single-byte call site in `PDFFactory` and both existing test classes stay as
  they are. Upstream's `PDFToUnicodeCMapTestCase` is the second safety net that §5a
  did not name: it pins the 100-entry section split, surrogate pairs at a section
  boundary, and the 256-entry single-byte rejection.
- Rangeable means one code point, as §5a says. Everything else is a `bfchar` with a
  string destination, including the empty destination if §9.3 chooses (a).

### 9.6 What the docx4j session confirmed, 2026-09-27

- The `+noliga` twin stays after this CR: it exists for fidelity (Word draws no standard
  ligature unless `w14:ligatures` asks), and this CR only fixes what the PDF says about a
  glyph that is correctly drawn. Nothing in `FopCapabilities` will gate the twin on a
  CR-002 capability.
- Through docx4j today the visible Latin effect is near zero, because Carlito gets no GSUB
  at all there (the missing-`DFLT` finding, `docs/upstream/no-default-script-table.txt`).
  Arabic and other complex-script documents move now; Latin moves once docx4j's script fix
  lands, which is ordered after this CR for that reason.
- The gate: the line-parity score pairs lines by their text, so a line carrying U+E000
  against Word's `ti` is unmatched today and recovers when fixed. Pair it with a
  per-document private-use count from `pdftotext` before and after; the docx4j session
  has a script to adapt.
- Pipeline: docx4j lays out and renders in one FOP run (`FORendererApacheFOP`,
  `IFRenderer` plus `ConfiguredPDFDocumentHandler`). Its other FOP runs produce area-tree
  XML that docx4j reads with its own SAX handlers and never feeds back to FOP.
- API: nothing in docx4j-export-fo, docx4j-core or the fidelity harness extends or uses
  `CIDSet`, `CIDSubset`, `CIDFull` or `PDFToUnicodeCMap`; the `org.docx4j.fonts.fop`
  copies are docx4j's own namespace.

### 9.7 A separate finding, recorded with the `DFLT` draft

The same samples with `language="en"` added ligated nothing, in Carlito and in DejaVu
alike. That is the language face of the `DFLT` fallback, not this defect, and it is
recorded in `docs/upstream/no-default-script-table.txt`.

### 9.8 The measurement in §6, brought up to date

- Carlito `office affluent fifty flow ti fi`: today `ﬁ\uE000y` and `\uE001`; after,
  `fifty` and `ti`, and `fi` rather than U+FB01 if §9.2's first decision stands.
- Noto Sans Arabic `السلام عليكم`: today two private-use characters; after, none, and the
  yeh cluster reads per §9.3's decision. Read with `pdftotext` and `mutool` both, since
  they disagree.
- The CJK radical, and a glyph with no character behind it, as §6 already says.
- The drawn content byte for byte unchanged, as §6 already says.

The JIRA in §8 should be drafted from §9.1, not §2: the wider statement is the true one.
