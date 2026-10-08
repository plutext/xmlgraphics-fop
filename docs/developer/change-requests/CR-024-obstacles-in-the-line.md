# CR-024: obstacles in the line - text on both sides of a float, and around page-anchored objects

Status: DESIGN 2026-10-08, not started; Jason asked for it after CR-023 shipped its two hooks, on the question whether
the floating-table work is one instance of the general case of wrapping around pictures, charts and text boxes. It
is. Registry key `fop/CR-024`. Enterprise CR-001 §6.6 items 10 (text on both sides of an anchored object) and 11 (wrap
around a page-positioned object), both open since the first survey; what CR-023 §4.3 sized at three to four weeks,
re-sized here with the page-anchored half included (§6). Nothing here is built; the estimates are for Jason's
ordering against the `.6` release, the merge of Apache `main` and `fop/CR-019`.

## 1. Word's model, from its own dialogs

Jason's `floats.docx` (2026-10-08) holds Word 365's dialogs for the four kinds of thing that wrap. Pictures, text
boxes and charts get one Layout dialog: a wrapping style (in line, square, tight, through, top and bottom, behind
text, in front of text), which side the text goes (both sides, left only, right only, largest only), distances from
the text on four sides, and a position relative to the paragraph, the margin or the page. Tables get the same choices
under other names: Table Properties offers text wrapping none or around, and "around" unlocks a positioning dialog
whose horizontal and vertical anchors, distances from the surrounding text, "move with text" and "allow overlap" are
the picture dialog's position tab.

In the XML: `wp:anchor` with `wp:wrapSquare`, `wrapTight`, `wrapThrough`, `wrapTopAndBottom` or `wrapNone`,
`wrapText` both sides, left, right or largest, `distT`, `distB`, `distL`, `distR`, and `positionH`/`positionV` with
`relativeFrom` and an offset or alignment, against `w:tblpPr` with `tblpX`, `tblpY`, `horzAnchor`, `vertAnchor`,
`tblpXSpec`, `tblpYSpec`, `leftFromText` and the other three distances, and `w:tblOverlap`. docx4j already carries all
four through the same two FOs: a side float for a wrapped object, an absolutely positioned block-container for one
that does not wrap. VML text boxes and frames (`w:framePr`) arrive the same way.

**The general object.** A rectangle in the column, with an anchor (a paragraph, or the margin or page), an offset
from it in both axes, a wrap mode, a side rule, four distances, and an overlap rule. Every setting is a row of the
table below, and the fork today covers the first row.

| Word setting | Fork today | Missing |
|---|---|---|
| Square, one side, at the column's edge, anchored to a paragraph | `fo:float`: CR-020's edges, CR-022's `clear`, CR-023's offset | nothing |
| Square, both sides, or an x position away from the edge | one side, chosen by the object's centre | text on both sides (item 10): §4 |
| Largest side only | docx4j may choose the side by room today | nothing in FOP |
| Top and bottom | the band: in the flow, or `clear` | nothing |
| Behind or in front of text | block-container, no wrap | nothing |
| Anchored to the page or margin, with wrap | block-container, no wrap: the lines ignore it | an obstacle the lines know about (item 11): §4 |
| Tight and through | treated as square | contours; a rectangular picture's tight is square anyway |
| Distances from text | the float's margins | nothing |
| Allow overlap | FOP stacks same-side floats | overlapping floats; rare, not addressed |

The two missing pieces that matter are one piece: a line laid out in segments around a set of rectangles. Where the
rectangles come from is secondary, and the design below takes tables, pictures, text boxes and charts as inputs it
does not distinguish, so that the picture case is not built twice.

## 2. What the fork has

`fo:float` is one-sided (start or end). FOP lays a side float out in passes of the page breaking (CR-020 §1, CR-022
§3.5, CR-023 §3): the float's anchor box is seen, the first legal break after it is the start edge, the float's area is
placed and the page sequence's intrusion adjustment (`setStartIntrusionAdjustment`, `setEndIntrusionAdjustment`) is
set to the float's width; the content from the edge is read again with every line narrowed by that adjustment
(`PageSequenceLayoutManager.getCurrentColumnWidth`), until the first legal break after which the content and the glue
reach the float's foot (or an FO with `clear`, or a line that does not fit), where the content is read a third time
with the adjustment off. CR-023 added the offset below the anchor's top, held back by one more restart, and the page
refusing a float it cannot hold. The intrusion is a property of the column for the whole pass, not of the line: every
line between the start edge and the end edge is narrowed by the same amount on the same side.

docx4j's measured cost of the one side (CR-032 §3, 2026-10-08): the following paragraph seven lines lower than Word's
on a 40% table centred in the column (96pt), 54pt with a 41pt sliver on the near side, 15pt with a 75% table; and
the b189 documents, 6705, 1616, 9832, 8236 and 14776, which lose one to eleven lines each where Word puts single
words in the sliver and the rest on the far side. Page-anchored wrap has no fork number yet: docx4j positions the
object and the lines run under it.

## 3. FOP's line manager today

`LineLayoutManager` breaks a paragraph with one width: `ipd = context.getRefIPD()` (lines 740 and 810), and its
`LineBreakingAlgorithm` extends `BreakingAlgorithm`, whose `computeDifference` uses `getLineWidth()`, the constant.
`BreakingAlgorithm.getLineWidth(int line)` exists as a hook and returns the constant too. The only per-line variation
is the first line's text-indent. Each line's `LineBreakPosition` already records its own `lineWidth`, `startIndent`
and `endIndent`, and `addAreas` sets the line area's ipd from it (`lineArea.setIPD(lbp.lineWidth)`), so the areas
side can take a width per line as it stands; the breaking side cannot. docx4j's `WordLineLayoutManager` subclasses
`LineLayoutManager` through the `inline-access` hook (`LineBreakPosition` public with its constructor and getters),
so the manager's shape is a contract with docx4j: whatever changes here changes there.

Knuth's line breaking with a width per line is TeX's `\parshape`: the algorithm is the same, the width is looked up
by line number. A line in several segments is not in Knuth: it is a line whose width is the sum of its segments'
with a forced break between them that costs nothing, and areas that place the second segment beside the first.

## 4. Design

### 4.1 Obstacles

An obstacle is a rectangle in the page, grown by its four distances, with a side rule. Two sources:

- **A paragraph-anchored float**, as now: its rectangle is known when the float's area is placed at the start edge
  (`FloatContentLayoutManager.addChildArea`: x from the side and the start indent, y the flow's height plus the
  offset's shift, width and height from the area). Today only its width reaches the lines, as the column's
  adjustment; here the rectangle is registered on the page viewport and the adjustment goes.
- **A page-anchored object**: docx4j's absolutely positioned block-container, marked `fox:wrap="square"` with
  `fox:wrap-sides` and the four distances, registered on the page viewport when the page is made, before any line of
  the page is laid out.

`PageViewport` (or the page sequence manager per page) keeps the list; `obstaclesBetween(y1, y2)` answers which
rectangles a band of the page meets, and `segmentsAt(y1, y2, columnStart, columnEnd, sides)` the free segments of
that band after the side rule: both sides gives every free segment, left only the segment before the obstacle, right
only the one after, largest the widest.

### 4.2 Phase A: a width per line (`parshape`)

The line manager asks, for line n of a paragraph that starts at y0 with line pitch h, for the free width of the band
[y0 + n·h, y0 + (n + 1)·h]; with one obstacle at the column's edge that is the width per line. `LineBreakingAlgorithm`
overrides `getLineWidth(int line)` to answer from a width table built before breaking, and `computeDifference` uses
the width of the line the node would end (`getLineWidth(activeNode.line + 1)`), as `PageBreakingAlgorithm` already
does for pages. The `LineBreakPosition` records the width and the start indent it was broken for; `addAreas` already
honours both. The first line's text-indent composes as now.

Where y0 comes from is the question the restarts answer. For a paragraph-anchored float, the restart from the start
edge gives the paragraph's y relative to the float; the page breaker's intrusion adjustment is replaced by the
obstacle's rectangle, and the width table is built from it at the restart. For a page-anchored obstacle, y0 is known
only when the page breaker places the paragraph: so a page-anchored obstacle becomes a *virtual float*, triggered by
position rather than by an anchor box: in the page breaking's first pass, at the legal break before the first line
that would enter an obstacle's band (the same crossing test as CR-023's `nextLineCrosses`), the breaker takes a start
edge as it does for a float, with the obstacle's rectangle as the "float", and the end edge at the obstacle's foot as
CR-020 has it. No area is placed (the object is already on the page), and no anchor box is needed. Everything built
for floats in CR-020, CR-022 and CR-023 then serves page-anchored objects unchanged: the edge logic, the clearance,
the deferral into tables, the kept breaks, the walked-past edge.

The line pitch: the paragraph's line-height where it is constant, which docx4j's paragraphs are; a paragraph with
lines of different heights gets its table rebuilt from the actual heights when the breaking is run again, which
`LineLayoutManager` already does for a changed ipd (`getChangedKnuthElements`).

Phase A alone gives: one-sided wrap at any x (the lines beside a float not at the column's edge get the width of the
larger side, by the side rule), top-and-bottom and square for page-anchored objects (item 11's picture at the page's
corner no longer has text under it), and the foundation for B. It does not give text on both sides.

### 4.3 Phase B: a line in segments

A band with two free segments (left and right of a centred obstacle, or between two obstacles) breaks as two
"lines" of the breaking algorithm with a forced, free break between them, and lays out as one visual line: the
second segment's `LineBreakPosition` carries a start indent at the obstacle's far edge and a flag that it continues
the line before; `addAreas` makes a second `LineArea` at that x and the block does not advance its stacking position
for it (`BlockLayoutManager.addChildArea`, a flag on the area). Alignment is per segment: a justified segment
stretches to its own width, a centred one centres in it. Hyphenation applies at the segment boundary as at a line
end. The last-line rule (`text-align-last`) applies to the visual line's last segment.

What the breaking needs for this: the width table of A becomes a segment table (a list of widths per line), and the
line counter of the algorithm counts segments, with a map back to visual lines for line-height, widows and orphans,
which count visual lines. `LineLayoutPossibilities` and the line-count machinery of `LineLayoutManager` (the
`activePossibility`, the `lineLayouts` of `updateData1/2`) take the map.

### 4.4 What docx4j writes

- Paragraph-anchored: `fo:float` as now, with `fox:float-offset` (CR-023); new `fox:float-x` for an x position away
  from the column's edge (`tblpX`, `positionH`), `fox:wrap-sides` (both, left, right, largest) and the distances as
  the float's margins, which the obstacle grows by.
- Page- or margin-anchored: the absolutely positioned block-container as now, with `fox:wrap="square"`,
  `fox:wrap-sides` and the distances. Behind, in front and none write nothing.
- Capability `obstacles-in-the-line` for A, `line-segments` for B, so that docx4j writes `fox:float-x` and
  `fox:wrap` only where they are honoured and keeps the one-sided fallback on Apache FOP.

### 4.5 Not in scope

Tight and through contours (rectangles only; a rectangular picture's tight equals square); behind and in front
(no wrap); negative offsets; allow overlap; a float anchored in a table cell beside another obstacle.

## 5. Tests and gates

- Layout tests: a float at an x inside the column with text on the wider side (A), a page-anchored block-container
  with `fox:wrap` and lines around it (A), a centred float with text on both sides (B), two floats on one anchor with
  text between them (B), the sliver case (B), each with a control without the attribute.
- The docx4j session's probes, which are the measure: `table-floating-offset-sides` cases 1 and 3 (96pt and 54pt a
  page), `table-floating-wide-anchor-text` case 3 (the 75% table), `table-floating-pair` case 1 (two tables side by
  side with the text between), the b189 documents (6705, 1616, 9832, 8236, 14776), and for item 11 the anchored-
  picture probes of CR-001 §6.8 with a page-anchored picture, which need a Word run.
- Nothing moving on a document with no obstacle, and nothing moving on the one-sided cases CR-020 to CR-023 gated.

## 6. Estimate

| phase | content | time with the gate |
|---|---|---|
| A | the obstacle registry; the width table and `getLineWidth(line)` in the breaking; the intrusion adjustment replaced by the rectangle; page-anchored objects as virtual floats; `fox:float-x`, `fox:wrap` | 2 weeks |
| B | segments: the segment table, the line-count map, the second `LineArea`, alignment per segment; `fox:wrap-sides` | 2 to 3 weeks |
| docx4j | the attributes written, the one-sided fallback kept, the gates | with each phase |

Four to five weeks in all, against CR-023 §4.3's three to four without the page-anchored half. A is worth shipping
alone: it closes item 11's square case and the one-sided-at-any-x case, and it is what B stands on.

## 7. Risks

- `WordLineLayoutManager`: docx4j's subclass calls `LineBreakPosition`'s constructor and reads the manager's fields
  through the `inline-access` hook; A adds fields to the position and B a flag, both by setters, as CR-023 §4.2 did;
  the docx4j session is told before any signature its manager uses changes (CLAUDE.md, Hazards).
- Justification and `text-align-last` per segment, and hyphenation at a segment boundary: measured against Word on
  the probes, since Word's choices there are not in the spec.
- Widows and orphans count visual lines, which B's segment counting must map; keeps and breaks unchanged.
- Performance: a restart per obstacle a paragraph meets, as a float costs today; a page with many obstacles restarts
  as many times.
- A table beside an obstacle keeps its width and is shifted, as beside a float today; a table across an obstacle
  is not laid out around it (its rows are not lines).

## 8. Upstream

XSL-FO 1.1 has no both-sides float and no page-anchored wrap; Antenna House's `axf:float-*` is the precedent for
an extension. The fork's attributes are `fox:`, the fork's own; the `parshape` width per line in the breaking
algorithm is general and could go to Apache on its own if a use arises there.

## 9. Decisions for Jason

1. Whether to start at all, and when: after the `.6` release and the merge of Apache `main` is this session's
   recommendation, both being cheaper and overdue.
2. A alone first, or A and B as one CR.
3. The Word run for the page-anchored probes (item 11), which the docx4j session would cut from CR-001 §6.8's
   anchored-picture cases.
