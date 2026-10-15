# CR-024: obstacles in the line - text on both sides of a float, and around page-anchored objects

Status: PHASE A, the page-anchored half, MERGED to `2.11-docx4j.7` by fast-forward on Jason's word 2026-10-15
(tip f98834c2c; gated PASS b245 on r35, §11.9; several obstacles on one page, §11.8, is in the merge and gets its
own gate with the frames on r36); unreleased, in `docs/release-notes/2.11-docx4j.7.md`. Phase B (text on both
sides) waits on Jason. Ordered 2026-10-15, built on branch `CR-024-obstacles` the same day (§11). Designed 2026-10-08, reviewed
2026-10-15 against what the fork learnt since (§10: five changes to the design, the estimate for phase A 2.5 to 3 weeks); Jason asked for it after CR-023 shipped its two hooks, on the question whether
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

## 10. Review, 2026-10-15, in the light of what the fork learnt since

Jason asked for the design to be reviewed before ordering it. What changed between 2026-10-08 and now: `fop/CR-026`
phase B (a trial layout on a throwaway manager tree, because a line-level restart is destructive and docx4j's line
manager is a private copy), `fop/CR-027` (Apache's FOP-3331 puts a column-wide float in the flow), §6.6 item 50
(a tall inline doubles the line's half-leading), the docx4j session's Word probes (CR-032 §3 and §4.3: text takes
any room at all beside a float, a negative offset pulls a table into the preceding paragraph and the top margin,
the anchor's own lines split above an offset), and its question of 2026-10-15 on page-anchored tables (CR-032
phase 4), answered by measurement (a retained negative space-before on a flow's first block draws it above the
body top). Five changes follow; the model of §1 and the two phases stand.

**10.1 Where an obstacle becomes known, and the restart from the page's first break.** §4.1 says a page-anchored
object is "registered on the page viewport when the page is made, before any line of the page is laid out". It
cannot be: docx4j's page-anchored object is an absolutely positioned `fo:block-container` in the flow, laid out when
the flow reaches it, so the breaker learns of it at its element's place in the list, which is where the anchor
paragraph is, and the lines of the page above that paragraph are already in the list and may lie in the obstacle's
band. Word lets a page-anchored object, and a negative offset (CR-032 §3, `negative` case 1), affect text above its
anchor on the same page. Two ways, and the design takes both: docx4j writes a section-opening object first in its
section (cheap, and already its shape for case (a)); and the breaker, on meeting an obstacle whose band starts above
the current position on the page, restarts from the page's first break, which it holds (`startPage`), reading the
page's content again with the obstacle registered, as CR-020's float restart reads the content after an edge again.
The areas of the page are not yet added at that point, so nothing is undone; the cost is one more pass over the
page, as a float costs. That one mechanism serves page-anchored objects, negative offsets and a table a later
paragraph's float reaches up to, so it is built once, in phase A. A page-anchored object taller than its page
(CR-032 §3, `tall`) continues on the following pages in Word; FOP clips an absolutely positioned container at the
page, so the continuation is docx4j's split of the container, outside this CR, and the obstacle registry takes a
rectangle per page.

**10.2 The width per line is supplied below the line manager, not built in it.** §3 and §7 treat docx4j's
`WordLineLayoutManager` as a subclass reading fields through `inline-access`. It is more than that: a 4,600-line
copy of `LineLayoutManager` with its own private paragraph list, its own `LineBreakingAlgorithm` (a greedy breaker
that calls `getLineWidth()` at tab stops and line ends) and its own `makeLineBreakPosition` (CR-026 §9, where this
was learnt). Anything built inside `LineLayoutManager` reaches the gate only once docx4j copies it. So phase A's
width table is not the line manager's: a `LineWidthProvider` on the `LayoutContext`, set by the page breaker for the
pass (the obstacle registry, the column's x-range, the paragraph's y0), answers `widthAt(y1, y2)` for a band;
`BreakingAlgorithm.getLineWidth(int line)` consults it through the line's y, and the line position records the
width it was broken for, as it already does. docx4j's part is then one call in its greedy loop (the per-line width in
place of the constant) and the start indent on its position, gated on the capability so that it may lag. Phase B's
segment flag is the same shape. The capability names stand (`obstacles-in-the-line`, `line-segments`).

**10.3 The line pitch is not constant.** §4.2 builds the width table from "the paragraph's line-height where it is
constant, which docx4j's paragraphs are". They are not, in two known ways: docx4j places lines as Word does, each
from the tallest run on it (§6.6 item 13, §6.9), and an inline picture makes a line of its own height (item 50,
measured on 12301). So the table is built from estimated pitches, the paragraph broken, the actual line heights
read from the positions, the table rebuilt and the paragraph broken again until the bands agree, bounded at three
passes (two in practice): the fixed point §4.2 only implied. Each pass runs the breaking algorithm over stored
paragraphs, which the manager does already for a changed inline size; nothing is laid out twice.

**10.4 No trial layout, and nothing on the breaker's managers.** CR-026 phase B's lesson: a restart inside a
paragraph mutates the line manager, and areas added afterwards read that state, so a trial that must be undone has
to run on a throwaway tree. Phase A needs no trial: the virtual float is a production restart, as a float's is, and
the fixed point of 10.3 is inside one manager's breaking. Phase B's segments are likewise within the breaking. The
balancing trial of CR-026 and the obstacles coexist because the registry lives on the page viewport, which the
throwaway tree shares; the trial's lines then see the same obstacles.

**10.5 Three things the model now also covers, which the fork has as separate rules.** (i) FOP-3331's in-flow path
(CR-027: a float whose content is as wide as the space available goes in the flow) and CR-023's `float-overflow-below`
(a line that does not fit beside a float goes below it) are the obstacle model's rule for a band with no free
segment a word fits: the lines go below. Word's measured rule is "any room at all" (CR-032 §3), which phase B's
segments give exactly; in phase A the one-sided largest side keeps CR-023's rule. Neither is removed by A; B folds
them. (ii) The page-anchored tables of CR-032 phase 4: case (a), a section-opening table, needs nothing from the
fork now (the retained negative space-before, measured 2026-10-15); case (b), a table after content, is 10.1's
obstacle, so the `fox:page-y` hook offered as a three-to-five-day stopgap is not needed if A is ordered, and is
withdrawn from the design as a separate piece. (iii) Unequal columns (CR-026): `segmentsAt` takes the current
column's start and width from the span, not the body's, and an obstacle is clipped to the column it lies in.

**10.6 Estimate and decisions, restated.** Phase A: 2.5 to 3 weeks, not 2: the restart from the page's first break
(10.1) and the width provider below the line manager (10.2) are each a few days the first estimate did not hold,
the fixed point (10.3) a day. Phase B unchanged at 2 to 3 weeks. docx4j's side is larger than "the attributes
written": its greedy breaker takes the per-line width (A) and the segment flag (B), gated. For Jason: (1) whether to
order A now, which the docx4j session's page-anchored work is waiting on; (2) A alone first, as §6 recommends; (3)
the Word run for item 11's page-anchored picture probes, cut by the docx4j session from CR-001 §6.8; (4) whether
docx4j writes page-anchored objects first in their section meanwhile, which costs it nothing and removes the
page-start restart from the common case.


## 11. Phase A as built

**11.1 Increment 1, 2026-10-15: the page-anchored half, forward case.** Capability `obstacles-in-the-line`, the
twenty-sixth. `fox:wrap` (`none`, `square`, `top-and-bottom`), `fox:wrap-sides` (`both`, `left`, `right`, `largest`)
and `fox:wrap-distances` (up to four lengths, before after start end) on an absolutely positioned `fo:block-container`
(`Constants` 301 to 303, `FOPropertyMapping`, `ExtensionElementMapping`, `BlockContainer`). The obstacle
(`layoutmgr.Obstacle`: the rectangle, grown by the distances, with the side rule) is registered by
`BlockContainerLayoutManager.getNextKnuthElementsAbsolute` with the page-sequence's `ObstacleRegistry` when its
elements are made, page-relative for `fixed`, relative to the holding box's top and the column's start for
`absolute`. The page is not known then: docx4j's object is a fixed container in a zero-height wrapper at the head of
its anchor paragraph (`WordLayoutFixups.insertAnchorWrapper`), so only the wrapper's box reaches the page's list, with
the container's own box inside the wrapper's breaker. `PageBreakingAlgorithm.handleBox` therefore asks the registry
for an obstacle not yet started whose layout manager descends from one in the box's position chain, and arms it for
the page the box lands on (the active node of the highest part number): the band in list coordinates from that
page's start and the body's top in the page (`PageSequenceLayoutManager.columnGeometryInPage`, with the part's column
from `PageProvider.columnIndexOfPart`), the room in the part's column, the narrowing by the side rule
(`Obstacle.intrusionsIn`: the wider side for `largest` and, in phase A, `both`; the whole column for
`top-and-bottom`), and `pendingFloatTop` set to the band's top, which is CR-023's mechanism unchanged: the start edge is
the first legal break after which a line would cross the top (`nextLineCrosses`), and the edge node ends the
algorithm. `PageBreaker.addAreasForFloats` then finds no float placed and an armed obstacle, and `startObstacle` sets
the two intrusion adjustments and records a float of the band's remaining height from the edge
(`handleStartOfFloat(bottom - edgeY, edgeY, 0, false)`); the list is read again from the edge with the lines narrowed
through the reference IPD, and the foot ends it as a float's foot does (CR-020's edge, CR-022's clearance, CR-023's
overflow rule all apply). For `top-and-bottom` the obstacle ends at its own start edge with a clearance to the foot,
since the overflow rule tests the box after a break and the first line after the edge has none before it (measured: a
zero-width line was laid beside the band). A list that starts mid-page records where (`listStartFlowY`), so a band is
placed against the page whichever list it is found in.

So the "width per line" of §4.2 is, for an obstacle at any x in phase A, a width per band with a start indent, which
the float machinery's restarts already give at line granularity, and the narrowing reaches docx4j's line manager
through `LayoutContext.getRefIPD()` as a float's does: its greedy breaker and tab stops need no change for this half
(the docx4j session's correction of 2026-10-15 to §10.2, that its tab-stop logic reads the constant width too, is
answered the same way). The `LineWidthProvider` of §10.2 remains for the paragraph-anchored float at any x
(`fox:float-x`) and for phase B, where a line's width varies within a band.

Limits of the increment, each to be lifted or recorded: (i) a band that begins above the box that holds the
obstacle was taken from the box on; lifted by increment 2, below; (ii) an obstacle
met while a float or another obstacle is being handled waits for a later box that holds it, and is lost if none
follows (the common corpus case has one object a page; two on one page, or an object beside a floating table, is
the case to measure); (iii) the page a box lands on is read from the active node of the highest part, which is the
page in FOP's first-fit page breaking with floats and may not be under total-fit; (iv) a container taller than its
page is clipped by FOP as before, and the registry takes it for the page it starts on. Layout test
`block-container_fox-wrap.xml`, seven cases (the right margin, left only, largest at the left margin, top-and-bottom,
the four distances, an absolute container inside its paragraph, a control without `fox:wrap`): each passes;
checkstyle clean; the full suite's reading is in the commit (bf0a439c3: 3927/0).

**11.2 Increment 2, 2026-10-15: the restart from the page's first break (§10.1).** A band that begins above the box
that holds the obstacle, on the box's page, is honoured from its top. Two cases, both in
`PageBreakingAlgorithm.armObstacleHeldBy`. Where the band's top lies below the page's content top, the page's
elements are read again: every active node is dropped (`BreakingAlgorithm.removeAllNodes`) and the main loop goes
back to the page's start node with the band pending (`rewindRequested`, a hook in `BreakingAlgorithm`'s loop that
the subclass answers once; `restartFrom` resets the totals and the footnotes met since, and from the list's start
node, which is no break, the reading begins at the first element again), so the edge is found at the first legal
break before a line would cross the top as in increment 1, and the areas up to it are added once, since nothing
had been added. Where the band covers the page's top and the page's start is a break in the list, that break is the
edge itself: the edge node is made at the page start's position with its totals, every node is dropped so the
algorithm ends there, the parts before it are added, the float part is empty, and
`PageBreaker.addAreasForFloats` starts the obstacle on the new page instead of faking content on it
(`handleEmptyContent`), so the next list begins on that page with the lines narrowed from its first. The limit that
remains: a band above the start of a list that began mid-page (after a float's edge or an earlier obstacle's) is
honoured from the list's start, the lines before it being areas already; `armObstacleHeldBy` reads the earliest
point it can reach and refuses a band wholly above it. Two defects found by the test and fixed on the way: the
registry matches an obstacle to a box by the innermost layout manager of the box's position chain, the wrapper's,
since the flow's manager heads every chain and is an ancestor of every obstacle (the first box of the page claimed
it); and a block-container whose elements are made again after an edge registers its obstacle again, which the
registry now ignores, the first registration standing with what the breaker did with it. Test cases 8 (the band
above the anchor, the two one-line paragraphs before it beside the container) and 9 (the band covering the top of
page 2, the anchor mid-page, page 1 unchanged and page 2's lines narrowed from its first, the anchor's own first
line below the foot full width).

**11.3 Word's readings of the nine probes, 2026-10-15, and the text-box rule.** The docx4j session cut
`picture-page-square.docx` (nine cases, A4, 72pt margins, a 150 x 100pt picture each; its message of 2026-10-15) and
read Word 365's PDF (`~/fidelity-cr030/readings/2026-10-15-picture-page-square-word.txt`: the picture's rectangle and
every line within 20pt above and 30pt below it, with its text box and width). Against the model as built:

- Cases 1, 3, 4, 5, 6 (square at the right margin; at x 172 with `largest`, `left`, `right`; the four distances):
  the lines whose text crosses the band stop at the picture's edge less the distance (364.3 for case 1, 349.3 for
  case 6), `largest` takes the wider side (the right, from 331.1 = 322 + 9), `left` runs to 163, `right` from
  331.1. These are increment 1's `intrusionsIn` by construction: the room is the rectangle grown by the distances,
  the side rule picks the side, and the narrowed width is the column less the room on that side.
- Case 7 (a full-width picture, 250 to 330): no line in the band, the next line's pitch box starting at the foot
  (its text at 332.7): the clearance as FOP lays it after an edge. Case 8 (`top-and-bottom`): the same, the lines
  resuming at 302.8 below a 200 to 300 picture.
- Case 9 (the picture at page y 150, anchored in the seventh paragraph): Word re-wraps the paragraphs before the
  anchor, the lines from 151.7 narrowed: increment 2's restart from the page's first break, confirmed.
- Case 2 (centred, both sides): text on both sides, 72 to about 210 and from 381.8: phase B. Phase A's `largest`
  reading lays the right side only and will differ here, as §4.2 says.

One rule the fork did not have, which every reading fits: **Word's line box carries the line's leading above its
text, and a line is beside the object when that box crosses the band.** At the band's top this is the text's
bottom edge: case 1's line at 187.3 to 198.2 (its pitch box to 201.1, crossing the picture's top at 200) runs
full width, case 8's line at 187.2 to 198.2 stays above a `top-and-bottom` picture at 200, case 9's line at 137.8
to 148.8 stays full width above a picture at 150, and case 1's line at 201.1 is the first narrowed. At the foot it
is the text's top less the leading: case 6's line at 319.5 is narrowed below a band foot of 318 (its box from
316.7), and case 1's at 305.6 (box from 302.8) is not, below a foot of 300. Cases 7 and 8 show the same box from
the other side: below a cleared band the text resumes one leading below the foot (332.7 under 330, 302.8 under
300), the box's top at the foot. FOP's edge tests used the line's pitch box (`KnuthBlockBox` width = line-height
plus a half-leading each side), right at the foot within a half-leading and wrong at the top by up to the leading.
Built the same day: `nextLineCrosses` tests the next line's text bottom (`LineBreakPosition.getSpaceBefore() +
getLineHeight()`), the foot test takes the next line's box as Word draws it (its pitch top less the half-leading
below, `-getSpaceAfter()`), and `deactivateNode` leaves a line whose box lies below the foot where it is (no
clearance, the content after the edge starting at its top) while a forced edge still clears to the foot. This
serves floats too, which are the same model (§10.5); on the corpus it moved nothing (the docx4j session's control
gate b233, docx4j HEAD on r31 against `.6`: 0 movers on the four corpora and the probes). For docx4j's own line
manager, which gives the line its box with the leading below the text (Enterprise CR-001 item 14), the rule reads
its position's space-before and line-height as it sets them: its foot test is then the text's top, one leading
below Word's; its pitch question, open in its register. Test case 10 pins the rule (a band the fourth line crosses
by its leading only, full width; the eleventh beside the container by its box).

**11.4 Two defects from the docx4j session's r31 run of the probes (2026-10-15), fixed.** It rendered the probes
through docx4j with `fox:wrap` written (its CR-032 phase 5 candidate) and read them against Word's lines
(`~/fidelity-cr030/readings/2026-10-15-picture-page-square-r31-cand.txt`; reproducers under
`~/fidelity-cr030/repro/r31-*.fo`, which reproduce here through FOP's stock managers). (i) The band started one line
late wherever its top fell inside a paragraph's first line (cases 3 to 6 and 9, and the table probe's case 3):
orphans and widows leave no break element between a paragraph's first and second lines (and last two), so the first
legal break after the anchor box, where the edge could be made, was after the second line; setting orphans and
widows to 1 on the reproducer gave Word's lines exactly. Fixed in `LineLayoutManager`: at a boundary orphans or
widows keep, a `BreakElement` of infinite penalty and class line is emitted, which the page breaker never takes as
a page break (an infinite penalty), and which `PageBreakingAlgorithm.handlePenaltyAt` considers only when a band is
pending and the next line would cross its top, so that it becomes the edge (`startPendingFloat` inside
`considerLegalBreak`, `elementCanEndLine` then allowing it while a float is handled). The element is emitted only
in a page-sequence that carries an obstacle (`BlockContainer.bind` marks its `PageSequence`, `noteObstacle`;
`PageSequenceLayoutManager.hasObstacles`): emitted always, it changed the element list's shape and failed eight of
Apache's element-list tests (`block_space-before_space-after_2`, `block_margin_inherit` and six more), whose areas
it had not moved. docx4j's line manager emits its own elements with the same orphan and widow gap, so its copy
needs the same break element where `PageSequenceLayoutManager.hasObstacles()` is true, gated on the capability;
told. (ii) A container as wide as the column (the probe's case 7, the table probe's case 3) left no
room on either side, and the lines were laid one word each at zero width down the band, CR-023's overflow rule
not ending it (the first line after an edge has no break before it to be tested at): `Obstacle.intrusionsIn` now
returns the whole column when the side the rule names has no room, and `PageBreaker.startObstacle` clears to the
foot as for `top-and-bottom`. Test cases 11 and 12; on the reproducers, every defect page now reads as Word's
(page 3's line at 186.1 at 331.0 against Word's 331.1; the table's flow resuming at 208.1 against Word's 209.4).

**11.5 Two defects from the docx4j session's r32 corpus gate (b236, 2026-10-15), fixed.** The gate's good part:
the probes move to Word (`picture-page-square` 0.7273 to 0.9564, every defect page of §11.4 matching Word's
reading; `table-floating-page-after` 0.8085 to 0.9504), and corpus documents with page-anchored objects (13544
to 0.9815, 4621 and 4253 to 1.0, 5075 to Word's pages). Its control b235 (r32 without `fox:wrap`, against b233)
had one mover, 2189 at -2 lines, the foot rule of §11.3; unread. Two renderer defects, each losing content,
reproducers under `~/fidelity-cr030/repro/r32-*.fo`:

(A) 3475, a page-anchored logo at the top of its last page, the page drawn with one line under docx4j's managers.
The page starts at a forced break (`break-before="page"` on an empty paragraph before the anchor), so the page-top
edge of §11.2 is that forced break. `PageBreaker.handleFloatLayout` resolves the restart from the edge's position:
for a break between blocks (`addInBetweenBreak`, the parent's position) the restart is the next block, read again
from its start; for `break-before` the break element carries the block's own position
(`addKnuthElementsForBreakBefore`), so Apache's resolution restarted one level inside that block, at its line
manager, with the block's auxiliary position. FOP's own line manager survives that (the shape renders here); a
consumer's copy need not, and docx4j's lost the page. Fixed: at a forced break whose position belongs to a child of
the flow, the restart is that block itself, as a restart for a change of width reads a block. Test case 13.

(B) 8362, a column-wide page-anchored table at page y 66.65, anchored after a two-page table: the page the
breaker first met its box on lost its lines and the table was drawn on the next. The band, clearing the column,
pushed the content from the page's top down by its height, the anchor box with it, past the page's foot; the page
then broke before the box, which landed on the next page with the fixed container, and the first page's lines had
been cleared for nothing. The model's own answer, Word's too, is that the object moves with its anchor: when a
clearing band would push its box past the page's foot (`boxY + bandHeightInPage >= pageHeight`), the obstacle is
deferred: `computeDifference` returns too long for every break after the box on a page that cannot hold the box
below the band, so the page breaks before the box as CR-023's `pageCannotHoldFloat` breaks before an anchor; and
the obstacle is taken up for the page the box lands on, at the first legal break after the box by the first active
node whose page holds it (`takeDeferredObstacle`, the page-top edge at that node), or in `handleBox` again when
the algorithm's restart reads the box again. A band that no page holds with a line below it is given up, the
object drawn where it lands. Test case 14 (a 400pt column-wide container at the page's top anchored at 480pt: the
page keeps its thirty lines, the next opens with the container and the anchor below it). On the reproducer here
page 3 keeps its content and breaks before the anchor, which lands below the table on page 4.

Noted from the same gate, the docx4j session's side: a page-anchored table taller than the room below its
`tblpY` is clipped by FOP (limit (iv) of §11.1), so docx4j keeps such tables in the flow.

**11.6 The r33 gate (b238, b239; 2026-10-15) and 3475 traced under docx4j's managers.** Control b238 (r33 without
`fox:wrap`, against r32's) 0 movers. Candidate b239 (with `fox:wrap`, docx4j's edge-only break elements and its
tall-table guard) against b238: 17 movers; 8362 from -51 to +1 (§11.5 B holds), the probes keep their gains
(picture 0.73 to 0.96, after-content tables 0.81 to 0.95), 13544 +33, 4621 and 4253 to 1.0, 5075 to Word's pages;
left 3475 -30, 3955 -2, 6603 -2 (the last two unread). 2189 (§11.5, the foot rule) is not a candidate mover; its
float's area height is the docx4j session's to measure. 3475 was not fixed by (A) of §11.5 under docx4j's managers,
so it was traced here with their classpath (its recipe `~/fidelity-cr030/repro/r33-3475-howto.txt`, the renderer's
classes first): page 6 is a page-sequence of its own, so the band covers the list's start and the edge is the first
legal break on the page, after the empty break-before paragraph (a break between blocks, `Position:-1(Flow)`,
parts 0); the breaker then asks the flow to read again from its direct child. That child is not the anchor paragraph but
the empty paragraph carrying `break-before="page"` (the edge falls among its trailing elements under docx4j's
line manager), and a second trace, with prints in FOP's own flow and block managers, placed the loss in FOP:
`FlowLayoutManager`'s restart resets the block (`reset()`, with FOP's own note "won't work with forced breaks"),
the reset block serves its break-before again as a one-element list, the flow returns that list at once as a
forced break, the breaker strips the trailing break and has nothing, and the flow, still marked finished from the
first reading, is not read again: the page-sequence ends with the empty paragraph. docx4j's managers override
none of this; its element shape merely routes the restart to that block, where FOP's stock shape (test case 13)
routes it one level inside, to the block's line manager, where no reset happens. Fixed in `FlowLayoutManager`:
a block read again after an edge (`floatRestartLM`) gets `SUPPRESS_BREAK_BEFORE` on its context with
`FLOAT_RESTART`, since its break-before was served when the list before was read and the edge lies on the page
it starts. Under docx4j's managers, by its recipe, page 6 then draws 1714 characters against the control's 1713.
The forced-break restart of (A) never fires on either shape (the page-top edge's element is the keep penalty after
the forced break, not the break), and is harmless; it stays as a guard.

**11.7 The r34 gate (b241, b242; 2026-10-15): 13544 fixed, the frames withdrawn for now.** Control b241 (r33 to
r34 alone) 0 movers; candidate b242 15 movers, every gain held, 3475 off the list. Left: 13544 (+33 lines but 4
pages against Word's 3) and two frame documents, 3955 and 6603, which the docx4j session has taken out of the
obstacles on its side meanwhile (they keep their measured hidden band copy; gate b243).

13544, reproduced here from `~/fidelity-real/score/b242/fop/16_en-US_tbl_13544.fo`: a column-wide page-anchored
table at page y 513.8, its band 441.8 to 686.1 in a 697.9pt flow, so it clears the column and leaves 12pt below
its foot, where the anchor's first line does not fit and goes to page 2. The clearance to the foot (`PageBreaker.
pendingFloatClearance`, taken by the next part's `addAreas` as its space-before, fop/CR-022) was then applied to
page 2's first part, which began 240pt down ("Bottle Buddy Planning Sheet" at 311 against the control's 70.3 and
Word's 76.0). The clearance belongs to the page the edge lies on: `startPart` now drops it when it opens a new
page, so the content that did not fit below the foot starts the next page at its top (70.5 here). This guards
CR-022's `clear` and a `top-and-bottom` object at a page's foot the same way. Test case 15.

3955 (an address frame whose own lines were drawn 609pt low in b242) does not reproduce here on r34 with either
FOP's stock managers or docx4j's (its recipe's classpath, the frame's lines at 147.4 against Word's 149.8), and
the docx4j session retracted it: its comparison had paired Word's address line with the footer's repeat of the
same text; the frame is at 134.1 in control and candidate alike. Gate b243 (r34, frames out) against b241: 13
movers, every one a gain or neutral. 6603 (seven `top-and-bottom` frames on one page, each its own container at
its own paragraph, two pairs with identical positions; the body 86pt above Word's) is limit (ii) of §11.1,
several obstacles on one page: §11.8.

**11.8 Several obstacles on one page, 2026-10-15 (limit (ii) of §11.1 lifted; 6603's seven frames).** The breaker
took one obstacle at a time: an obstacle met while another was on waited for a later box, and was lost if none
followed. Now the page's obstacles are a set, and the lines at any height are narrowed by every obstacle whose
band holds it.

- `PageBreaker` keeps the obstacles taken up on the page being filled, in its flow coordinates (top, foot, the two
  intrusions, whether it clears the column), cleared when `startPart` opens a new page. `activeObstaclesAt(top, y)`
  gives the narrowing below a height, the largest start and end intrusions over the obstacles whose band begins at
  or above the top the edge was taken for and ends below the height, and whether any clears; `nextObstacleEventAfter`
  the next height at which that set changes, the lowest foot among the active or the lowest top below.
- At a start edge (`startObstacle`) the armed obstacles join the set, the active ones set the intrusions, and the
  float being handled runs to the next event. At an end edge the event is read back from the float's foot
  (`floatHeight + floatYOffset`): if obstacles are still active there, their intrusions replace the old ones and a
  new float is handled from the event to the next one (`handleStartOfFloat` again, or the clearance path when one
  clears the column), so the foot of one obstacle is the start of the rest; only when none is left does the float
  end as before. A list starting mid-page with an obstacle of the set whose top lies below it (`PageBreakingAlgorithm.
  initialize`, `nextObstacleTopAfter`) has the edge pending from that top.
- The event at an obstacle's top differs from one at a foot: the lines change from the first line whose text
  crosses the top, as at a start edge (`nextLineCrosses`), with no clearance, where at a foot they change from the
  first line whose box lies below it (`floatEndsAtTop`, carried from the breaker to the algorithm for the list).
- Arming (`armObstacleHeldBy`) takes every obstacle of the page: a second one while the first is pending joins the
  armed set and the edge is taken for the earlier top; one met while a float or an obstacle is on joins the page's
  set and brings the next event forward to its top, with the list read again from its start when the top lies
  above the box. The deferral of §11.5 and the page-top edge of §11.2 apply to the first obstacle of a page.
- Before the list's first box is handled, the boxes within its first page are scanned for the obstacles they hold
  (`scanObstaclesOfFirstPage`), each armed as if met where it lies: without this an obstacle whose box lay after
  the foot of another was never met in that float's list, which ends at the foot, and the list after the foot,
  starting below the obstacle's top, honoured it a line late (test case 18 found it).

Test cases 16 to 18: two obstacles on opposite sides with overlapping bands, held by one paragraph, by two, and
with the second's box after the first's foot: the lines beside one at 301pt, beside both at 151pt, beside the
other at 301pt, then full, in each. What remains of the limit: an obstacle met after a page break inside a float's
list, or at its edge, waits for a later box; and the scan covers a list's first page only, later pages of a long
list being walked as before.

**11.9 The r35 pair (b244, b245; 2026-10-15): the page-anchored half gated.** Control b244 (docx4j HEAD on r35)
against b241: 0 movers. Candidate b245 (`fox:wrap` written, frames out) against b244: 13 movers, no line lost:
`picture-page-square` +63, `table-floating-page-after` +20, the openers +6, `table-floating-anchor` +7, 13544 +33,
4621 and 4253 to 1.0, 9919 +4, 8362 +1, 13102 +1, 5075 at Word's pages; events equal but for one line overflow each
in 4253 and 5075. 13544 still ends a page over Word (4 to 3): its page 2 begins about 25pt lower than Word's (the
heading at 101.9 against 76.0, an empty line at 85.3, the anchor paragraph that did not fit below the table on page
1); Word's placement of that paragraph is unread. docx4j's side is written up (CR-032 §4.5 as built, its register,
CR-001 §6.6 item 11), uncommitted until Jason says; the merge of r35's commit (42f51bdd1) to `2.11-docx4j.7` is his
word; the frames return with r36 (§11.8) as their own gate.

**11.10 Phase A's remainder, decided by Jason 2026-10-15.** `fox:float-x` (§4.4, §6) is dropped from phase A: with a
line laid in one piece, a paragraph-anchored float at an x inside the column narrows the lines to the column less
the float's far edge, which docx4j already produces with the padding it writes inside the float, and CR-023's gates
measured that one-sided cost; the attribute would add nothing until a line can be laid in segments. The width
provider of §10.2 therefore belongs to phase B, where a line's width varies within a band, and is not built in
phase A. Phase A closes with the frames' gate on r36 (§11.8); phase B waits on Jason's word.

**11.11 The frames' control (b246, 2026-10-15): a regression of §11.8, fixed.** Control b246 (docx4j 597b168c9,
frames out, on r36, which is the merged head) against b245 on r35: 5 movers, all losses: `picture-page-square`
0.9564 to 0.7418 and 9 pages to 17, every case's page preceded by a page holding only the case's first line;
`table-floating-anchor` -4; 5075 -1; 6251 -1; 4253's overflow events up. Reproduced here from
`~/fidelity-real2/score/fidelity-probes-b246/fop/picture-page-square.fo` (17 pages) and traced. The probe's cases
are separated by forced page breaks in one page-sequence, which split the flow into several element lists; a fresh
list after a forced break begins a new page, but the page's obstacle set (§11.8) was cleared only when the breaker
opened that page, in `startPart`, which comes after the list's algorithm has run. So at the top of case 2's list the
set still held case 1's picture, whose top was found "pending" from the list's start (`nextObstacleTopAfter`), a
spurious edge was taken after the page's first line, the breaker started an obstacle that was not active there and
the page ended, and the case's real picture was handled on the page after. Two fixes in
`PageBreakingAlgorithm.initialize`: a fresh list, neither beside a float nor after its foot, drops the page's set
(`clearPageObstacles`), since it begins a new page or column whatever page the breaker has open; and the pending
check from the set applies to a list after a float's foot only, a list beside a float running to the next event
itself (on a float list it had taken a second edge one line below the first, for the obstacle just taken up). The
scan of a list's first page also stops at a forced break, a box after one lying on a later page. Test case 19 (two
cases in one page-sequence separated by a forced break, each with its picture). The probe renders 9 pages again
here, page 2's and page 3's pictures as on r35; the other four movers were not reproduced separately and are read
by the gate on r37.

**11.12 The frames' pair on r37 (b248, b249; 2026-10-15): a crash and two edges, fixed; one form's frames left.**
Control b248 (frames out, r37 against r35): the picture probe back, 6251 and 4253 gone; left
`table-floating-anchor` -4 and 5075 -1. Candidate b249 (frames on) found 5218, a form with eight small
`top-and-bottom` frames in two columns, throwing `NullPointerException` in `PageBreaker.handleFloatLayout`.
Three mechanisms, each reproduced here from the docx4j session's FOs:

- *The crash* (`~/fidelity-cr030/repro/r37-frames-npe-5218.fo`). The edge was taken at a keep penalty after a
  paragraph's last line, whose position is the line manager's own, where the next box with content is a nested
  block of the same paragraph; the restart resolution walked the next box's chain for the line manager, found it
  as the chain's leaf, and read a position below it that is not there. Now a break is an edge only where the
  restart from it resolves (`PageBreakingAlgorithm.edgeRestartable`, mirroring the resolution: a leaf position, a
  list item's, or a manager's own position with the next content box's chain passing through that manager, or
  the nearest of its ancestors, to a child, or the manager itself as the chain's leaf), and `handleFloatLayout`
  resolves the same way, with a fallback to the next block of the flow and a warning. A break before a box without
  content (a block's end marker) is no edge either. The form renders as its control, 4 pages, nothing lost; but
  its page-top frames are not honoured, since the one break before the picture line their band covers is that keep
  penalty of an empty inline run, from which the line manager, restarted at its own position, produces nothing
  new. A limit for now: `top-and-bottom` frames whose band lies in a paragraph's nested picture block.
- *`table-floating-anchor` -4, page 5* (`~/fidelity-real2/score/fidelity-probes-b248/fop/table-floating-anchor.fo`):
  case 5's margin-anchored table is CR-023's offset float, case 6's narrow table a `fox:wrap` obstacle, on one
  page. With the float's intrusion pending, the obstacle's arming took the second-obstacle path of §11.8, whose
  reference top had never been set, and the pending top collapsed to zero: the line at 156, whose text ends above
  the float's top, was narrowed. An obstacle met while an offset float is pending is now left for a later list
  (as r35 did), and the set-driven pending sets the reference top. The line is full width again; page 5's
  remainder (P07 over the float's table) is FOP's stock layout, the same on r35, r37 and now.
- *5075 -1*, two empty lines near a column-wide page-anchored table at the page's foot: unread here (the empty
  lines carry no text to measure by); read on r38 by the gate.

**11.13 The r38 pair (b250, b251; 2026-10-15): the head gated, the frames deferred.** Control b250 (docx4j HEAD
597b168c9, frames out, on r38) against b248: one mover, `table-floating-anchor` +4, back to 0.9000; every event
count equal. Candidate b251 (frames as `fox:wrap`, `both` sides, distances vSpace vSpace hSpace hSpace, replacing
the hidden in-flow copy that reserves the band): 5218 clean; 3955 (1.0 to 0.9804, -2 lines, 3 pages as Word) and
6603 (0.8788 to 0.8182, -2 lines) lose; nothing else moves. docx4j keeps the hidden band copy for frames and its
phase 5 (pictures, text boxes, tables) stays as committed, renderer-gated on `obstacles-in-the-line`. Of 6603's
ten frame containers four have no height (hRule auto), so they are absolute containers without a
block-progression-dimension; the body still ran into their bands. Answered: such a container's extent is known
before any line is broken beside it, its content being laid out on its own breaker and the obstacle registered
from that height (`registerObstacle`), so the cause lies elsewhere (the 5218 limit of §11.12, a box met after a
page break inside a float's list, or the set resolving to the wider side); the FOs are asked for, local only.
With this pair the page-anchored half of phase A, merged at f98834c2c and carried to c79fefc86, is gated on the
fork's head: control b250 against b245 through b248 is 0 movers but the +4 recovery, and the gains of §11.9 hold.

**11.14 6603's frames read (2026-10-15): a clearance over consecutive bands, and `fox:wrap="below"` for frames.**
The two frame documents' FOs (local only, `~/fidelity-cr030/repro/r38-frames-*.fo`) rendered here: 3955's
candidate is identical to its control under FOP's stock managers (its -2 is docx4j's managers' or its pagination,
not read further); 6603's body sat 148pt above its control's, which reserves the frames' band with a hidden copy.
Two things, both measured against Word's PDF of 6603 (`~/fidelity-real-c2/goldens-nofields/`, the body's heading
at 300.2 below the lowest frame's foot at 276.5):

- *The clearance ran to the first event only.* Ten frames with overlapping bands (tops 85.1 to 241.05, the
  tallest to 152.7): the first edge cleared to the small frame's foot at 22.7 in the flow, the next list knew only
  tops below that, and the taller frame still active there was forgotten, so lines were laid under it, and again
  at each later event. `PageBreaker.continueBesideObstacles` now serves both the start edge and the end edge: from
  the content's height it skips every consecutive clearing band as one clearance, then hands over to what is still
  beside the lines there (a float to the next event with the union's intrusions) or, with nothing, reads the next
  list as after a float's foot with the later tops pending; and every band begun by the content's height counts,
  not only those at or above the top the edge was taken for (frames anchored a few points apart). The event
  search after a start edge keeps the edge's top as its reference (an edge lies above its band, and the band's own
  top was being taken for the next event: test case 5 gained a line until that was put back).
- *Word does not lay a frame's text in the gaps.* With `top-and-bottom`, the model laid the heading in the 34pt
  gap between the second and fourth frames (177 against Word's 300): a Word frame that text does not wrap around
  is laid in its band and the text after its anchor continues below it, where a picture with top-and-bottom wrap
  has text up to its top (probe case 8). So a frame's band runs from its anchor to its foot: `fox:wrap="below"`
  (`EN_BELOW`), clearing the column like `top-and-bottom` with the band's top at the box that holds the
  container when the band lies below it. With it 6603's body starts at the lowest foot, 276.5, as Word's; the
  heading itself lands 82pt below Word's through the FO's own spacing (a 68pt heading block and a 12.6pt paragraph
  written for the hidden band copies), the docx4j session's to read. Test case 20 (two frames anchored at the
  page's top with a gap between, the body below the lower one).

**11.15 The frames on r39 (b253 to b255; 2026-10-15): 5218 and 3955 read.** Control b253 (r39 against b250) 0
movers; candidate b254 (frames as `below` for no-wrap frames, `square` otherwise) 3955 -2 and 5218 -3 with a page
over Word; 6603 held, its remaining 81pt the docx4j session's (its footer table's hidden band copy, measured
against the body's foot where the page's foot is right: b255, 0 movers, 6603's maxDy 22.8 to 16.9).

5218 (`~/fidelity-cr030/repro/r39-frames-below-5218-*.fo`, reproduced here, 5 pages to the control's 4): all
twenty-four frames are anchored in the empty paragraphs at the flow's start, the eight form rows at the page's
foot among them, so with `below` every band runs from those anchors and the body after them goes below the
lowest frame, at the page's foot. That is §11.14's rule, and Word's on 6603, but on 5218 Word keeps the body at
the same height as the foot frames ("1. ООО И." at x 118 beside a frame at x 130); both documents carry
`w:wrap="notBeside"`, so Word decides by something else, and which frames get `below` is the writer's choice
from the docx (told). Two renderer fixes found on the way: the deferral of §11.5 estimated what a clearing band
pushes down by the band's height, where it is the content laid within the band (`contentWithin`; a band above
every line pushes nothing); and a pending band wholly above the next line is given up rather than taking an edge
that would clear the page's content down by its height (`armedBottomInList`). Test case 21 (a column-wide frame at
the page's top anchored in the page's last paragraph, the body beginning 300pt down: nothing moves).

3955 (a `square` frame, no `below`): the candidate splits a three-line list item at the page's foot where the
control and Word start it on the next page; widows and orphans are 1, the stock managers render both FOs alike,
and CR-024 changed none of `ListItemLayoutManager`, `BlockLayoutManager` or `BlockStackingLayoutManager`, in
`FlowLayoutManager` only `addChildElements` (§11.6), which docx4j's subclass inherits. The restart at an edge
inside a list item goes through Apache's `ListItemPosition` path and docx4j's `WordListItemLayoutManager`'s
restart variant, where the difference must lie; offered to compare the element lists.

**11.16 The frames gated on r40 (b258, b259; 2026-10-15).** Control b258 (r40 against r39) 0 movers. Candidate
b259 (frames as obstacles): 6603 +4 lines in both corpora, to 1.0000; 3955 -2; nothing else; the probes unchanged.
The docx4j session's writer rule for frames text does not wrap around (`w:wrap` notBeside or none): `below` when
the frame's top lies in the page's upper half, `top-and-bottom` in the lower half (6603's letterhead and 5218's
heading frames `below`, 5218's form rows `top-and-bottom`); 5218 is at Word's 4 pages. The cut is the one its table
band reservation measured, a reading of two documents rather than a Word rule it can cite; a probe could settle
it. 3955: the item has no keep and no break in the docx or the FO, and Word leaves the page's last 77pt empty
though the whole three-line item would fit, so the control's break matches Word by the breaker's luck and the
obstacle path's 2+1 split is legal; the element-list comparison is deferred until Jason has decided what to keep.
With this the frames are in phase A's reach too, on docx4j's uncommitted side.

**11.17 Frames reopened (2026-10-15): the clearance lost in a chain of edges.** Jason reopened frames as obstacles
with a Word probe for the writer's rule (`frame-page-bands`, twelve cases, on the share for his run). The docx4j
session found that with every no-wrap frame written `top-and-bottom` (no `below`), r40 laid 6603's body through
the frames (the heading at 109 over the address frame) while the probe's own top-and-bottom cases held. Traced
here from `~/fidelity-cr030/repro/r40-frames-tb-6603.fo`: each edge after the first on a page lands in a list whose
content up to the edge is added by the float part alone (`PageBreaker.addAreasForFloats`), with no ordinary part
before it, and the clearance pending from the previous edge was taken only in the ordinary parts' loop
(`AbstractBreaker.addAreas`), so in a chain of edges, which frames with gaps between them produce, every clearance
after the first was lost: the first clearance (12.6 to 81.8 in the flow) was in the list's heights and not in the
areas, which then ran 69pt above the breaker's coordinates. The float part now takes the pending clearance as an
ordinary part does. With `below` there is one edge and one clearance, which is why that path was right. With the
fix, plain `top-and-bottom` on 6603 lays the heading at 230.8, in the gap below the 187pt frames and above the
241pt ones, the gap layout the probe's cases pin; Word has it at 300.2, below them all, which `below` gives. The
probe decides which Word applies to a no-wrap frame, and `below` stays or goes by its reading.

The probe's golden (Word's run, read by the docx4j session the same day) decided it: cases 1 to 9 follow plain
top-and-bottom to 0.3pt (text above a frame, in a gap between two, below, overlapping and auto-height frames
alike), `w:wrap="none"` gets no band, and an at-least frame's band is its at-least height. So `below` was dropped
(d89ccf85f: `EN_BELOW` gone, `ENUM_COUNT` back to 212, test case 20 rewritten for top-and-bottom), and
`block-progression-dimension.minimum` on an absolutely or fixed positioned container with an auto height is now the
least of its extent (`BlockContainerLayoutManager.getNextKnuthElementsAbsolute`): its viewport and its obstacle are
the greater of the minimum and the content, which is a word processor's "at least" frame. docx4j writes `.minimum`
for those frames from r42.

**11.18 A band's top inside the first line after an edge (2026-10-15): 6603's heading under docx4j's managers.**
With plain top-and-bottom on r41 and r42, 6603's heading sat at 290.25 under docx4j's managers where Word has
300.2, an empty paragraph of 12pt in the 11.35pt gap between the frames' foot at 229.7 and the next frames' top at
241.05 (the docx4j session's labelled FO, `~/fidelity-cr030/repro/r42-6603-labelled.fo`, whose readings a render
through docx4j's own FOP path reproduces here). Traced at the pending-edge decision: the clearance from the upper
foot stopped at 229.7, the lower band not being active there, and the restarted list began directly with that
paragraph's 12pt box, so the first legal break the algorithm considered came after it, at 241.7, past the pending
top. The band's top fell inside the first line after the edge, and nothing tested that line: the §11.4 boundary
element serves a top inside a paragraph's first line on the first reading, not a list that opens with the line.
Under the stock managers the same line is 841 + 10318 + 841 (the leading split around the text), its text bottom
at 240.86, under the top by 0.19pt, so the stock laid the heading in the gap by the §11.3 rule and showed nothing.
Now `PageBreaker.continueBesideObstacles` tests the first line after an edge against the next band's top below the
clearance (`PageBreakingAlgorithm.firstLineTextBottomAfterEdge`: the space-before plus the line height of the
first box after the edge, by the §11.3 rule, or the box itself where it is no line): a band whose top the line's
text crosses is beside that line, and a clearing one extends the clearance to its foot, consecutive bands as
before; a narrowing one starts its float from the clearance's end. The end-edge branch goes through the same
routine for a real float's foot too (its no-obstacle case is what the branch did). Test case 22 (two
top-and-bottom frames 10pt apart, the 16pt line's text 11.1pt: one clearance of 90pt). 6603 under docx4j's
managers: the two empty paragraphs at 276.5 and 288.5, the heading's glyphs at 302.25, from 290.25; the stock
managers unchanged. Retraction (the docx4j session, the same evening): Word's heading glyph top is 302.2 in
real-c2's golden (302.7 in real's), not the 300.2 carried through §11.14 to §11.17 and the first text of this
section without a re-reading of the golden; r43's 302.3 is at Word's, and the lines after it within 0.1pt. There
is no gap left. A labelled copy (PX15 to PX21) is on the share for Jason's Word run, to confirm each empty line's
place. Gated b268 (r43, the frames rule with docx4j's widows and soft-return fixes, 5f584872c): the frame probe
+32, nothing else moved.

**11.20 A sliver beside a page-anchored object, and a block wider than the room (2026-10-15): m1's tables lost.**
Jason found, testing `.7` in the sample, that a letterhead (m1, a user's document, local only) lost both flow tables
after its page-anchored table, under docx4j's managers; under the stock ones the first. Traced here with the
renderfo harness: the container is 18pt from the left, 566.75pt wide with 9pt distances, in a 576pt column from
x=18, so the room at the end side was 0.25pt, and `Obstacle.intrusionsIn` counted it as room (`<= 0` was none).
The band was a narrowing float with a start intrusion of 575750, not a clearing one; the list restarted at the
start edge began with a small block, a 6.1pt container and the "Attn" table, all laid beside the band at 0.25pt,
the table's content gone; the end edge came only before the next text line, which cannot fit beside a float
(float-overflow-below is a rule for lines), so the clearance to the foot was taken after those three boxes. Not a
restart defect: the table's elements were there, and the restart variants were not involved. The probes had tables
after a band's foot (`table-floating-page-after`), never beside one. Two rules: a free side under 1pt beside an
obstacle is no room (`Obstacle.MIN_ROOM`, a numerical margin, not Word's rule), so the band clears the column as
top-and-bottom does; and a block-level box that asks for more width than the room beside a band, by its FO (a
table's inline-progression-dimension when not auto, else the sum of its fixed-layout columns; a block-container's
width when not auto: `PageBreakingAlgorithm.fixedIPDOf`), goes below the object as a word that does not fit does:
the end edge is taken before it (`overflowsBesideFloatAtNextBox`), and when it is the first box after the edge,
where no legal break precedes it (the §11.18 shape), the clearance runs to the band's foot
(`PageBreaker.continueBesideObstacles`, `firstBoxAfterEdgeFixedIPD`). The widths read are the FO's: a paragraph's
laid width is the column's at the first reading, which would have made every band clear. Test cases 23 (a 300pt
fixed-width table first after an edge beside a 100pt room) and 24 (a 0.5pt sliver). m1 under both manager sets:
Attn at 277, Notice Name at 305, Dear at 415. Against Word (319.9, 347.3, 457.7) everything after the band is 43pt
high: the start edge fell inside the 31.4pt glue after the anchor paragraph (the band's top at 80878 in the flow,
the edge at 110126) and the clearance runs from the edge to the foot, so that space is swallowed, where Word keeps
it below the object; the docx4j session has it as a probe to cut, no content lost. r44 (the two rules as first
written) was gated b270 against b268 and lost: the probe `table-floating-pair`, plain `fo:float` with no `fox:wrap`
and at Word's to 0.2pt on r43, -1 line and its page parity to 0.8732; corpus 8985 -7 lines. The second rule had
been written into the end-edge test of every float, so a fixed-width table beside a text-anchored floating table
went below it where Apache FOP's placement had matched Word. It is confined to an obstacle's band
(`handlingObstacle()`); the first (`Obstacle.MIN_ROOM`) never reached a float. Full suite 3927/0, checkstyle and
spotbugs clean; r45.

**11.19 Widows and orphans beside an obstacle (2026-10-15): 3955's list item.** The docx4j session's 3955 split
(§11.15, §11.16: a three-line list item 2+1 at page 1's foot with the frame written as an obstacle, whole on page
2 in its control and in Word) was read here from its element lists under its managers. Both first readings carry
the item as one box of 34497 (three lines of 11499; the stock list-item merge makes one box where the body has no
legal break); the candidate's re-read after page 1's band edge carries it as three boxes with zero penalties
between, and the page's foot takes the split. Every list-item call goes through docx4j's
`WordListItemLayoutManager.getNextKnuthElements` with null restart arguments, so its restart variant is not the
difference: the body's elements are. The FO's blocks carry no widows or orphans, so FOP's default of 2 holds on
the first reading, the body's soft return counted across by docx4j's `linesAcrossSoftReturns`; on the re-read,
`handlingFloat()` relaxes both to 1 and drops that count, which is Apache FOP's own rule beside a side float, in
the stock `LineLayoutManager` at two sites and copied into docx4j's. It exists so a float's edges can be found at
any line. A word processor keeps widow control beside a frame, and for an obstacle the §11.4 elements already mark
the boundaries, so the fork keeps the FO's widows and orphans beside an obstacle: `PageBreaker.obstacleFloat`,
set where an obstacle's band starts a float and cleared where an fo:float does, read as
`PageSequenceLayoutManager.handlingObstacle()`; the stock line manager relaxes only when `handlingFloat()` and not
`handlingObstacle()`, and its changed-elements path (`getChangedKnuthElements`, the re-read beside a band) now
emits the §11.4 element within the widows and orphans as the first-read path does; the page breaker admits that
element at a band's foot as well as at a pending top, where `handleBox` has found the foot at the box across it or
the foot test fires at the element (`handlePenaltyAt`). Without the admission the foot was one line late where it
fell within a paragraph's last two lines (test case 1 narrowed eight lines for seven). The stock managers split
3955's control too, at the soft return, which the stock does not count across; so under the stock managers this
change is measured by the layout tests only, and 3955 moves when docx4j's copy mirrors the two changes, told.
