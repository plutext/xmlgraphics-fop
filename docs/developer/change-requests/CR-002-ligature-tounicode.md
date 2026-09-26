# CR-002: a ligature's `ToUnicode` entry is a private-use code point, not its letters

Status: DRAFT (analysis only, no code). Raised 2026-09-25 while designing
`CR-001`. Registry key `fop/CR-002`. Upstream-bound: this is a defect in Apache
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
