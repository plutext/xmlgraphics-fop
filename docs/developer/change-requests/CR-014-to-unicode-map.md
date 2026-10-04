# CR-014: a font's ToUnicode text for given code points, from its configuration entry

Status: DONE ON BRANCH 2026-10-04, `CR-014-to-unicode-map` off `2.11-docx4j.5`; not merged, not gated.
Registry key `fop/CR-014`. Capability `to-unicode-map`. A docx4j hook; not upstream-bound as it stands
(see §5).

Requested by the docx4j session at Jason's word, 2026-10-04.
- Since docx4j d2a9690c1 (17.3.1), Symbol, Wingdings and Webdings are drawn in the real installed fonts.
  Their character maps are symbol-encoded (3,0), so the run's text is private-use (U+F020 to U+F0FF;
  `w:sym F04A` becomes U+F04A).
- FOP's ToUnicode CMap maps each glyph back to that private-use code point, so copying a bullet or a smiley
  out of the PDF gives a meaningless character, which hurts search and accessibility too.
- Word's PDF gives the Unicode equivalent. On corpus document 14_en-AU_tbl_5975, Word's ToUnicode maps
  Wingdings F04A to U+263A and leaves F04B (no BMP equivalent) as F04B; ours maps both to private-use.

## 1. The change

**Configuration.** A child of a `<font>` entry in the FOP configuration:

    <font embed-url="..."><font-triplet .../>
      <to-unicode code-point="F04A" unicode="263A"/>
      <to-unicode code-point="F04B" unicode="1F610"/>
    </font>

`code-point` and `unicode` are hexadecimal. `unicode` may be several code points separated by spaces,
and may lie outside the BMP; the CMap then writes a surrogate pair, `<D83DDE10>`.

**The path.**
- `DefaultFontConfig` parses the entries into the font's configuration (`Font.getToUnicode()`).
- `DefaultFontConfigurator` carries them to the `EmbedFontInfo` (`setToUnicode`).
- `LazyFont` hands them to the loaded `MultiByteFont` (`setToUnicodeOverrides`). A subclass of `LazyFont`,
  such as docx4j's, inherits this through `super`.
- `CIDSubset.getUnicodeSequences` and `CIDFull.getUnicodeSequences` pass each glyph's text through
  `MultiByteFont.toUnicodeText`, which replaces each code point that has an override.

That is the text CR-002's writer turns into the CMap. Nothing else reads the overrides, so glyph
selection, widths, the area tree and layout are unchanged. Without entries, nothing changes.

**The capability.** `Docx4jFop.TO_UNICODE_MAP = "to-unicode-map"`.

**Scope.** CID-keyed fonts (`MultiByteFont`), which is how FOP embeds a TrueType or OpenType face by
default, and what docx4j's symbol faces measured as (two-byte selectors in the CMap). A single-byte font's
ToUnicode comes from its encoding and is not covered.

## 2. Tests

- `ToUnicodeMapTestCase.testConfigurationEntriesAreParsed`: three entries, one outside the BMP and one of two
  code points, reach the parsed font configuration.
- `ToUnicodeMapTestCase.testOverridesReplaceTheToUnicodeText`: a subset of U+F04A, U+F04B and A publishes
  `￿, , , A`, and with overrides `￿, ☺, 😐, A`.
- `Docx4jHooksTestCase`: twelve capabilities.

## 3. Measured on the command line

Hack Nerd Font (which has glyphs at U+F04A and U+F04B), the text "A U+F04A B U+F04B C":
- without entries, `pdftotext` gives `A  B  C`;
- with entries F04A to 263A and F04B to 1F610, it gives `A ☺ B 😐 C`.

Every glyph is at the same position in both renders (`mutool draw -F stext` quads, identical checksum).

## 4. What docx4j does with it

With `to-unicode-map` present, docx4j's `FopConfigUtil` writes `<to-unicode>` entries into the font entry for
each real symbol face, from `org.docx4j.convert.out.common.writer.SymbolMapper`'s tables. Those cover Symbol,
Wingdings, Wingdings 2, Wingdings 3 and Webdings, keyed by the low byte, with the code point at U+F000 plus
that byte. The gate:
- control: no entries, nothing moves;
- with entries: layout identical, and the 5975 render extracts as ☺ and 😐.

## 5. Upstream

Not drafted. A symbol font's private-use code points have no Unicode meaning FOP could know. A general
"ToUnicode override per font" option could interest Apache, but it's a docx4j need first.
