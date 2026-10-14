# CR-024: obstacles in the line - text on both sides of a float, and around page-anchored objects

Status: PHASE A IN PROGRESS, ordered by Jason 2026-10-15; branch `CR-024-obstacles` off `2.11-docx4j.7`; increments 1
and 2 (the page-anchored half, with the restart from the page's first break, §11) built 2026-10-15, not yet gated. Designed 2026-10-08, reviewed
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
