# docx4j FO renderer (`docx4j-fo-renderer`)

> This is a modified distribution derived from Apache FOP 2.11. It is
> maintained by Plutext/docx4j and is not an Apache Software Foundation
> release. Apache FOP is a trademark of the Apache Software Foundation.

This branch (`2.11-docx4j.2`) is an upstream-tracking fork of
[Apache FOP](https://xmlgraphics.apache.org/fop/) 2.11, the XSL-FO formatter
[docx4j](https://www.docx4java.org/) uses for docx to PDF. It exists so that
docx4j's Word-layout-fidelity work can change FOP where reflection or
subclassing cannot reach, with every general fix sent upstream and the fork
degrading gracefully: `docx4j-export-fo` runs on Apache FOP 2.11 too, with the
rules that need a fork-only hook switched off. The design is docx4j's
[CR-020](https://github.com/plutext/docx4j/blob/VERSION_17_1_1/docs/developer/change-requests/CR-020-fo-renderer-fork.md).

The Java packages are Apache's (`org.apache.fop`), unchanged, so that docx4j's
layout managers and a consumer's own FOP extensions work on either. That means
**the fork and Apache FOP must never both be on one classpath**; docx4j warns
when they are.

## Coordinates

| Apache FOP 2.11                        | this fork (`org.docx4j`)        |
|----------------------------------------|---------------------------------|
| `org.apache.xmlgraphics:fop-parent`    | `docx4j-fo-renderer-parent`     |
| `org.apache.xmlgraphics:fop`           | `docx4j-fo-renderer`            |
| `org.apache.xmlgraphics:fop-core`      | `docx4j-fo-renderer-core`       |
| `org.apache.xmlgraphics:fop-events`    | `docx4j-fo-renderer-events`     |
| `org.apache.xmlgraphics:fop-util`      | `docx4j-fo-renderer-util`       |

Versions are `2.11-docx4j.N`: the Apache FOP line first, the fork's release
after. The automatic module names are Apache's (`org.apache.xmlgraphics.fop.core`
and so on), for the same reason the packages are. `fop-sandbox`, `fop-servlet`
and the transcoders are in the tree but not built.

Not yet released: until the first release the fork is consumed as a locally
installed `2.11-docx4j.1-SNAPSHOT` (`mvn install -DskipTests` here, then
`-Pfo-renderer-fork` in docx4j).

The marker class `org.apache.fop.docx4j.Docx4jFop` carries the version and the
names of the hooks the fork has (`capabilities()`); docx4j reads it reflectively.

## Going back to Apache FOP

From docx4j 17.3.0 `docx4j-export-fo` depends on this fork by default. The supported way
back is docx4j's own `apache-fop` profile; prefer it to hand-written exclusions.

For a build that cannot use that profile, exclude the fork and declare Apache FOP. All
four artifacts are named deliberately. The published `docx4j-fo-renderer` pom is flattened,
so it declares `-core`, `-events` and `-util` as direct dependencies rather than inheriting
them, and `docx4j-export-fo` declares `-events` itself as well as the aggregator. Excluding
only the aggregator therefore leaves the others behind. Naming an artifact that is not
present is harmless, so list all four and stay correct if docx4j changes which it declares.

```xml
<dependency>
  <groupId>org.docx4j</groupId>
  <artifactId>docx4j-export-fo</artifactId>
  <version>…</version>
  <exclusions>
    <exclusion><groupId>org.docx4j</groupId><artifactId>docx4j-fo-renderer</artifactId></exclusion>
    <exclusion><groupId>org.docx4j</groupId><artifactId>docx4j-fo-renderer-core</artifactId></exclusion>
    <exclusion><groupId>org.docx4j</groupId><artifactId>docx4j-fo-renderer-events</artifactId></exclusion>
    <exclusion><groupId>org.docx4j</groupId><artifactId>docx4j-fo-renderer-util</artifactId></exclusion>
  </exclusions>
</dependency>
<dependency>
  <groupId>org.apache.xmlgraphics</groupId>
  <artifactId>fop</artifactId>
  <version>2.11</version>
</dependency>
```

Check the result with `mvn dependency:tree`: no `org.docx4j:docx4j-fo-renderer*` should
remain. Leaving one alongside Apache FOP is the hazard above, and which of the two wins is
then decided by classpath order.

## Changes from Apache FOP 2.11

Every modified file carries a change notice under its licence header. Each
change is either an upstream fix waiting to be released, or a docx4j hook that
FOP would not want; the table says which.

| Change | Files | Upstream status |
|--------|-------|-----------------|
| FOP-3328: a format 3 anchor table's device tables are read from the anchor table's own offset (variable fonts threw an `AssertionError`) | `OTFAdvancedTypographicTableReader` | JIRA FOP-3328 filed; [apache/xmlgraphics-fop#106](https://github.com/apache/xmlgraphics-fop/pull/106) open |
| FOP-3330: `MultiByteFont`'s glyph bounding boxes are packed as ints, not a `Rectangle` per glyph (font memory retention) | `MultiByteFont`, `OFFontLoader`, `OpenFont` | JIRA FOP-3330 filed; [apache/xmlgraphics-fop#107](https://github.com/apache/xmlgraphics-fop/pull/107) open |
| `GlyfTable.isComposite` is false for an empty glyph: the subsetter read past the `glyf` table on a font whose last glyph is empty, and the font shipped unembedded | `GlyfTable` | JIRA text drafted, not yet filed |
| A CJK ideograph sharing a glyph with a Kangxi radical is mapped back to the ideograph, not the radical, so ToUnicode and extracted text carry the character that was written | `MultiByteFont` | JIRA text drafted, not yet filed |
| Surrogate pairs: a word is no longer split between a high surrogate and its low surrogate at a bidi-level change or a per-character font selection, and `CharUtilities.containsSurrogatePairAt` raises the documented `IllegalArgumentException` for a high surrogate at the end of a sequence rather than `StringIndexOutOfBoundsException` | `TextLayoutManager`, `CharUtilities` | Cherry-picked from Metanorma (70a1e75d5, ba2ec2ea4); JIRA text drafted, not yet filed |
| A substituted glyph's `ToUnicode` entry is the characters it stands for, not a private-use code point: a ligature glyph publishes its letters, an Arabic contextual form its letter, so search, copy and paste and screen readers get the text the document says (`fop/CR-002`, Enterprise CR-001 item 30). The second and later glyphs a decomposition produces from one character keep the private-use code point, since a CMap cannot say several glyphs share one character | `MultiByteFont`, `CIDSet`, `CIDSubset`, `CIDFull`, `PDFToUnicodeCMap`, `PDFFactory` | JIRA text drafted (`docs/upstream/tounicode-substituted-glyphs.txt`), not yet filed |
| `ToUnicode` selectors no longer drift by one after each supplementary-plane character: the CMap is built from one destination per selector rather than a positional `char[]` in which a surrogate pair took two slots, so the letter after an emoji or a mathematical alphanumeric no longer extracts as the letter after that (Enterprise CR-001 item 31) | `PDFToUnicodeCMap` | JIRA text drafted (`docs/upstream/tounicode-selector-drift.txt`), not yet filed |
| `GlyphTable.matchLookups` falls back from (script, language) to (script, dflt) before (DFLT, dflt), as OpenType layout engines do, and maps an ISO 639 language code to its OpenType language system tag first. A run with a language set no longer loses the font's ligatures, contextual forms and kerning where the font has no system for that language or no `DFLT` table (Carlito, Caladea); a language the font does distinguish (Turkish) gets its own system. `OTFScript.isWildCard` compared with the default script and now compares with the wildcard (`fop/CR-003`) | `GlyphTable`, `OTFLanguage`, `OTFScript` | JIRA text drafted (`docs/upstream/no-default-script-table.txt`), not yet filed |
| A font declared `kerning="false"` is positioned without the GPOS `kern` feature, as it already was without the legacy kern table; marks are still positioned. The loader now records the flag on the font, where it was true for every TrueType font whatever the configuration said (`fop/CR-003` §8) | `OFFontLoader`, `MultiByteFont`, `GlyphPositioningTable`, `ScriptProcessor` | JIRA text drafted (`docs/upstream/kerning-flag-gpos.txt`), not yet filed |
| Text drawn with glyph position adjustments keeps its letter spacing: `PDFPainter.drawTextWithDP` places each glyph by its own `Td`, so the `Tc` it set never reached the next glyph and a letter-spaced word in a font that kerns through GPOS was painted at its bare advances inside an area that kept the letter spaces (`fop/CR-005`, Enterprise CR-001 item 33) | `PDFPainter` | JIRA text drafted (`docs/upstream/letter-spacing-position-adjustments.txt`), not yet filed |
| A format character (a directional mark, a joiner) the font has a real zero-width glyph for stays in the glyph sequence, so it reaches the subset and the `ToUnicode` CMap and the text layer keeps it; before, every such character was elided on the CID path whether or not the font could draw it at no width (`fop/CR-007`, Enterprise CR-001 item 34) | `MultiByteFont` | JIRA text drafted (`docs/upstream/format-characters-tounicode.txt`), not yet filed |

### Hooks

Docx4j-only additions, each published under a `Docx4jFop` capability name so
that docx4j can gate the rule that needs it and fall back (by reflection into
the same members) on Apache FOP. All are public accessors or one public
setter; none changes what FOP does on its own.

| Capability | Members | Serves |
|------------|---------|--------|
| `pair-table` | `LineBreakUtils.setLineBreakPairProperty(before, after, value)` | Word's break after a hyphen before digits (UAX #14 since Unicode 8.0; docx4j Enterprise CR-001 §6.6 item 29). `LineBreakUtils` is generated from Unicode data by `src/main/codegen`; the method must be re-added after a regeneration. |
| `leader-placement` | `FilledArea.getUnitAreas()`; `LeafNodeLayoutManager.getCurrentArea()`, `setAreaInfoIPD(MinOptMax)` (its `setCurrentArea` is Apache's own); `LeaderLayoutManager.getFont()` | A line manager that grids and phases a placed leader (item 27) and gives a tab's leader the width and pattern of the stop it reached. |
| `inline-access` | `TextLayoutManager.getMappings()`, `getLetterSpaceIPD()`, `getSpaceCharIPD()`, `getFOText()`; `AlignmentContext(Font, int, WritingMode)` and `getLineHeight()` public (they were package-private and private); `InlineLayoutManager.getFont()`; `LeafPosition.setLeafPos(int)`; `LineLayoutManager.LineBreakPosition` public constructor and getters; `ListItemLayoutManager.getBodyList()` | What docx4j's Word line, text and list managers read (batch 48 item 2 and the rest of `LBP`). |
| `glyf-empty-glyph` | (the `GlyfTable` fix above) | docx4j drops its `FontPaddingResourceResolver` workaround when this is present. |
| `lookup-fallback` | (the `GlyphTable.matchLookups` fix above) | docx4j may write `language` on every block, as it does, and rely on the font's own script table being used; without it, it should expect no shaping or kerning from a font with no `DFLT` table. |
| `kerning-flag` | (the `kerning="false"` fix above) | docx4j's plain and `+kern` declarations of one font kern only where the run asked; without it, a font with a GPOS `kern` feature kerns under both. |
| `gsub-features` | `fox:gsub-features`, an inherited property carrying a space-separated delta such as `-liga` or `-liga +clig`; `TextFragment.getGsubFeatures()`; overloads of `Font`/`LazyFont`/`MultiByteFont.performSubstitution`, `Substitutable.performSubstitution`, `GlyphSubstitutionTable.substitute` and `ScriptProcessor.substitute` that take it, each existing signature delegating with `null` | Word applies standard ligatures only when `w14:ligatures` asks, while FOP's default feature list applies `liga` unconditionally and omits `clig` entirely, so the error runs both ways. Lets docx4j express the run's setting. Absent, FOP substitutes exactly as before. See `docs/developer/change-requests/CR-001-gsub-features-hook.md`. |

## Tracking upstream

Text drafted for a JIRA that is not yet filed lives with the change: in the commit
message for the `GlyfTable` empty-glyph fix and the Kangxi radical fix, and under
`docs/upstream/` for the surrogate-pair word split. `git log --grep` finds the first
two; each carries the stack, a reproducer and what was measured.

`upstream` is `apache/xmlgraphics-fop`; the branch merges upstream at least at
every Apache release and whenever a fix sent from here lands. A change without
a JIRA is sent upstream first unless it is docx4j-specific.

## Building

Apache FOP's own Maven build, Java 8 and later:

    mvn -B package checkstyle:check spotbugs:check
    mvn install -DskipTests

Apache's own README for FOP is the `README` file beside this one; its legal
information, and the `LICENSE` and `NOTICE` files, apply to this distribution.
