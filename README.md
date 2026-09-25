# docx4j FO renderer (`docx4j-fo-renderer`)

> This is a modified distribution derived from Apache FOP 2.11. It is
> maintained by Plutext/docx4j and is not an Apache Software Foundation
> release. Apache FOP is a trademark of the Apache Software Foundation.

This branch (`docx4j-2.11`) is an upstream-tracking fork of
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

## Tracking upstream

`upstream` is `apache/xmlgraphics-fop`; the branch merges upstream at least at
every Apache release and whenever a fix sent from here lands. A change without
a JIRA is sent upstream first unless it is docx4j-specific.

## Building

Apache FOP's own Maven build, Java 8 and later:

    mvn -B package checkstyle:check spotbugs:check
    mvn install -DskipTests

Apache's own README for FOP is the `README` file beside this one; its legal
information, and the `LICENSE` and `NOTICE` files, apply to this distribution.
