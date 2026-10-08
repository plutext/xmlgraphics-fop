# CR-023: the offset float, and a line that does not fit beside a float set below it

Status: IN PROGRESS 2026-10-08, on branch `CR-023-float-offset` off `2.11-docx4j.6` (after `fop/CR-022`, 13a3fbec7):
both halves built and measured (§4.1 and §4.2 as built); full `fop-core` suite 3870 tests, 0 failures, 4 skipped,
checkstyle clean; the docx4j gate pending (§9).
Registry key `fop/CR-023`. Two hooks for docx4j CR-032 (floating tables as Word lays them), §4.2 of that CR as
revised after its phase 0 Word probes (2026-10-08, commit cae13d3d1 and after), decided by Jason as D2 there and
confirmed in this session on 2026-10-08. Enterprise CR-001 §6.6 item 8 (no floating tables), and item 10 for the
part sized but not built (§4.3).

Both halves are measured, not inferred: the offset by docx4j's phase 0 probes (§2), the overflow by its gate b189 on
r17 (§1.2), where five corpus documents lose 1 to 11 lines each on `.5` and the fork alike.

## 1. What docx4j needs

### 1.1 The offset

Word places a text-anchored floating table `tblpY` below the top of the paragraph it precedes (the anchor), and that
paragraph's lines above the table run full width. docx4j writes `tblpY` today as `padding-top` inside the
`fo:float`, so the table lands where Word puts it but FOP, which narrows every line from the float's anchor, narrows
the anchor paragraph's lines above the table too. The probe `table-floating` scores 0.6875 for that alone, and 38 of
the 180 text-anchored floating tables in the corpora state an offset over 15pt. Document 4083 is the clearest case
(the docx4j session, phase 1): a table 51pt below its anchor at the page's left edge, whose anchor's lines FOP cut
into a column beside it, two lines lost and the page garbled, where Word runs them full width above the table.

### 1.2 A line that does not fit beside the float

Word puts a word that does not fit beside a float below it, and the rest of the paragraph follows below. FOP sets
the line at the narrowed width and lets it overflow the column (the `lineOverflows` event, "exceed the available
area in the inline-progression direction"). Measured by the docx4j session, gate b189 (2026-10-08, lines matched
against Word, with the table floated against in the flow): 6705 240 against 249 (its 36pt and 54pt slivers), 1616
747 against 758 (a 22pt sliver), 9832 47 against 58 (142pt), 8236 54 against 56, 14776 110 against 111; identical
on `.5` and on r17, so none of it is CR-022 §3.5's walked-past edge. docx4j therefore floats a table only with 2in
of room beside it until this lands.

### 1.3 Both sides

Word runs text down both sides of a centred float and between two floats, taking any segment a word fits, with no
minimum width (probes `offset-sides` cases 1 and 3, 96pt and 54pt a page). `fo:float` is single-sided and FOP's
line breaking takes one width per paragraph. Sized in §4.3, not built here.

## 2. Word's rules (the docx4j session's phase 0 probes, CR-032 §3)

- The offset is measured from the top of the anchor paragraph's block including its own space-before, and not
  the previous paragraph's space-after: 24pt of space-before on the anchor moves its first line 24pt down and leaves
  the table where it was (case 4); r18, which measured from the top of the resolved gap (the larger of the two
  spaces, where FOP's space resolution puts it), drew the table above Word's by exactly the previous paragraph's
  space-after (13.0pt on `table-floating`, 8.9pt on the four `offset-sides` cases; the docx4j session, 2026-10-08).
- The lines that fit in the offset are laid full width above the table, and the anchor paragraph's own text
  continues below the table: with `tblpY` 30pt the anchor's first two lines are above the table and the rest below
  (`wide-anchor-text` case 2); with `tblpY` 36pt and eight empty paragraphs, two are above the table and six below.
- A table that would be beside the anchor's text takes the text beside it word by word, at any width.
- A negative offset pulls the table above its anchor into the preceding paragraph and into the top margin; not
  addressed here (§8).

## 3. FOP's mechanism today

A side float anchored in a line is laid out in three passes of the page breaking algorithm (the terms of CR-020 and
CR-022 §3.5). The `LineLayoutManager` puts a zero-height `KnuthBlockBox` carrying the float's content manager before
the anchor line's box. In the first pass `handleBox` sees it and sets `handlingStartOfFloat`; the next legal break
is the float's start edge; `PageBreaker.addAreasForFloats` adds the areas before it, places the float's area at the
flow's current height (`FloatContentLayoutManager.addChildArea`: `yOffset` is the flow's `bpd` then, and the start
or end intrusion adjustment is set to the float's width), and `handleFloatLayout` reads the content again from the
edge with the intrusion on, every line narrowed. In that second pass `floatHeight` is the float's height (clamped to
the page), the edge is the first legal break after which the content and the glue up to the next box reach the foot
(CR-020), or the first FO with `clear` on the float's side (CR-022), and the content from the edge is read a third
time with the intrusion off, starting at the foot. The float's area is rendered absolutely at the flow position it
was added at plus its `yOffset` trait (`AbstractRenderer.renderBlock`), which is 0.

So the float's top is the start edge, which is the first legal break after the anchor box: the top of the anchor's
first line. Nothing lets the top lie lower, and nothing lets a line beside the float that cannot hold its content go
below it.

## 4. Design

### 4.1 `fox:float-offset`, capability `float-offset`

**The property.** `fox:float-offset` on `fo:float`, a length, default 0pt, not inherited; read by `Float` into the
`FloatContentLayoutManager` through the `Float` node. Only a positive offset does anything (§8). The reference is the
top of the anchor block including its own space-before and excluding the previous block's space-after: in the list,
the anchor box's position less the smaller of the resolved gap before it and the block's own space-before optimum
(`anchorSpaceBefore`, read from the anchor `Block` up the float's FO chain).

**The start edge moves to the offset.** In the first pass, `handleBox` no longer sets `handlingStartOfFloat` at
the anchor box when the float has an offset; it records the anchor block's top (the box's position less the glue
immediately before it, which is the resolved space-before) and the float top as that plus the offset. Each legal
break after it then asks whether the next box would cross the float top (`widthUpToNextBox + the box's width >
floatTop`): the last break whose next box does not cross it is the start edge, taken by setting
`handlingStartOfFloat` there, which the node loop of CR-022 §3.5 turns into the edge node. The lines between the
anchor box and that break are the first pass's, full width. A line crossing the float top is set beside the float
(it starts above the top, as a line Word narrows does when the table's top falls inside it).

**The float's area at the offset.** `PageBreaker.addAreasForFloats` places the float at the start edge's height;
the difference between the float top and that height (less than a line) is set as the `SideFloat` area's `yOffset`
trait, which the renderer adds (`AbstractRenderer.renderBlock`: `currentBPPosition += block.getYOffset()`), and is
passed on with the float's height to `recordStartOfFloat`, so that the second pass's `floatHeight` is the height
plus the shift: the foot is where the drawn float ends.

**As built (2026-10-08).** The float's area keeps its start edge at the break after its anchor box, where FOP has
always placed it, since an edge after the anchor's first line puts the float's own break element inside the part
being added and that line is lost; the offset is realised in two steps instead. At placement
(`PageBreaker.addAreasForFloats`) the algorithm hands `FloatContentLayoutManager` the float's top in the page (the
anchor block's top in list coordinates plus the offset, less the page's start), the area is given the difference
from the flow's height as its `yOffset` trait (`top-offset` in the area tree), clamped to the page, and
`recordStartOfFloat` carries that shift. If the shift is less than the anchor's first line (`floatAnchorLineHeight`,
read from the list), the top falls in that line and the intrusion starts at once, the foot the height plus the
shift. Otherwise the intrusion is held back: the page breaker zeroes the intrusion adjustments the area set, the
next pass's lines are full width, `PageBreakingAlgorithm.initialize` takes the shift as the pending top, and the
first legal break after which a line would cross it (`nextLineCrosses`) is a start edge; `handleFloatLayout` then
finds no anchor in that part and `resolvePendingIntrusion` restores the adjustments and records the start again
with the float's top less the flow's height there as the shift, exact whichever block the resolved space at the
break was attributed to (a shift taken from list coordinates was one space off). A too-long break while the
intrusion is pending starts it at the page's end.

**After gate b196 (2026-10-08, §9).** Three more pieces, each measured on the command line and in the layout test:
- *A pending intrusion never starts inside a table.* The start edge of the pending intrusion fell on a break inside
  a table on 3229 (two floats on one anchor), and the restart from a row threw `NoSuchElementException` as item 45's
  did; `considerLegalBreak` and the forced-break trigger now skip breaks inside a non-restartable manager, and a
  table starting at a break whose extent reaches the float's top starts the intrusion there, before it, as
  CR-020 and CR-022 treat the end edge.
- *Several floats on one anchor.* A line's floats share one anchor box. Each float's target top is now the
  anchor block's top plus its own offset (`getFloatAnchorTopInPage`), and the floats of one anchor are recorded as
  one, from the first top to the lowest foot: with offsets 32 and 120pt the floats draw at 32 and 120 and the lines
  from 26 to 160pt are beside them. The intrusion's width is the last float's, as FOP's own two-float handling has
  it. docx4j writes no offset for a pair until it has read the shape (§9).
- *A float the page cannot hold below its anchor goes to the next page with its anchor.* At the float's start edge,
  `createForcedNodes` refuses the edge from a page start that cannot hold the float's foot (the content height from
  `FloatLayoutManager.getFloatContentHeight`, the elements being made before the areas), making the node too long
  as usual; with no edge node, `BreakingAlgorithm`'s loop (`floatEdgeFound`, a one-line hook) runs its ordinary
  recovery instead of `handleFloat`, restarting from the last too-short break, which is the one before the anchor,
  and `restartFrom` clears the float's start so the anchor box is handled again on the next page. A float that would
  not fit an empty page either is not refused (it is placed and clamped, as before). The page's own height is the
  measure (`getLineWidth(node)`, which honours a first page's master and `page-master-by-content`), not the
  algorithm's nominal width: gate b198 found 13419 refused, a 2.25pt-offset table 64pt tall anchored near the foot
  of a page whose first-page master differs, where r19 had drawn it within the body (r21). Measured: after 38 lines, an
  anchor with a 60pt offset and a 60pt float breaks the page before it and starts the next page with the float 60pt
  down and the anchor's lines full width above; the same anchor with a float that fits (20pt offset, 40pt) stays,
  the float at 628pt. On r19, 4083's two such floats were carried to the next page's top split from their anchors
  (−10 lines, a page lost).

Measured on the command line (a marker word in the float; positions from the PDF, body top at 72pt): a 40pt float
with a 32pt offset in a heading with 10pt space-after draws at 32pt, the heading full width, the three lines from
26 to 74pt beside it (foot 72) and the next full; offset 0 as before; an anchor with 24pt space-before after a
one-line paragraph draws the float at 48pt (16 + 32), inside the heading's line, which is beside it with the two
below; a 60pt float 200pt down after seven short paragraphs draws at 200pt with all seven full width and the
eighth beside it. CR-022's seven cases unchanged. r18 took the reference at the top of the resolved gap; corrected
for r19 (5afcfc657) after the docx4j session's probe reading (§2): a fifth case, a paragraph with 12pt space-after
before an anchor with none and a 32pt offset, draws the float at 16 + 12 + 32 = 60pt, not 48.

**What docx4j writes.** `fox:float-offset` = `tblpY` on the `fo:float`, and no `padding-top` inside it; the anchor
block keeps its own space-before, which the offset is measured from. Gated on `float-offset`.

### 4.2 A line that does not fit beside a float is set below it, capability `float-overflow-below`

**Detection.** `LineLayoutManager.LineBreakingAlgorithm.updateData2` knows a line overflows (`lack < 0`, the
`lineOverflows` event). While the flow is handling a float (`FlowLayoutManager.handlingFloat()`), the line's
`LineBreakPosition` is marked `overflowsBesideFloat` when, in addition, the line's content would fit the column
without the intrusion (`getPSLM().getCurrentPV().getCurrentSpan().getColumnWidth()`): a word wider than the column
overflows whatever is done with it, and stays where it is.

**The edge.** In the second pass, `PageBreakingAlgorithm.considerLegalBreak` treats the legal break before a box
whose position chain carries a marked line as the float's edge, exactly as CR-022 treats the break before an FO with
`clear`: the edge is here, the float's foot stays the height the content restarts at, and the clearance of CR-022 §3.3
puts the line, and the rest of its paragraph, at the foot, full width. A marked line that is the paragraph's first
is the same case as a cleared block; one in the middle of a paragraph restarts the paragraph's remaining lines at
the foot, which the float restart does already (the edge of CR-020 falls mid-paragraph in its own tests).

**As built (2026-10-08).** `LineLayoutManager.LineBreakingAlgorithm.updateData2` marks the line's
`LineBreakPosition` (`setOverflowsBesideFloat`, a setter rather than a constructor argument, since the constructor
is the `inline-access` hook docx4j's line manager calls) when `lack < 0`, the page sequence's intrusion adjustments
are not both zero, and the overflow is no more than their sum. `PageBreakingAlgorithm.considerLegalBreak` adds
`overflowsBesideFloatAtNextBox` to the edge conditions beside `clear`'s, walking the next box's position chain for
a marked line; the clearance of CR-022 §3.3 then sets the line at the foot. Capability `float-overflow-below`, the
twenty-second.

Measured on the command line (a 300pt float 60pt tall, 151pt beside it; positions from the PDF): a paragraph whose
third word is about 290pt wide has its first line (two short words) beside the float at 26pt and the word's line
with the rest of the paragraph at the foot, 60pt, full width, where before the word's line was set beside the
float and overflowed the column by more than 50pt with FOP's warning; a word wider than the column stays beside
the float and overflows, as before; without a float nothing changes. Layout test `float_overflow-below.xml`.

**Where Word differs.** Word sets the words that fit beside the float and moves the first that does not; FOP's line
breaking at the narrowed width has already made the overflowing line, so the line is moved whole. For a sliver that
holds one or two words a line, the difference is a word or two per line. docx4j's 2in rule can then go (§6).

### 4.3 Both sides: sized, not built

Text down both sides of a float, or between two floats, needs a line laid as two segments around an obstacle:
`LineLayoutManager`'s breaking takes one width per paragraph (`lineWidth` in its `LineBreakingAlgorithm`), with
the first line's indent the only per-line variation, and an obstacle needs a width per line and a second segment
per line that shares its baseline and alignment. That is a new line manager or a deep change to this one (§6.6
item 10's project), in the fork and in docx4j's subclass of it. Estimate: three to four weeks of this session's
time with docx4j's gate, after the two halves above; not started without Jason's word. The measured prize is 96pt
and 54pt a page on the two probes, and the sliver cases of §1.2 once a word can go on either side.

## 5. Tests

- Layout tests `float_offset.xml` (eight cases, 36 checks: the pair and the page-end cases added after b196; the lines above the float full width; the float drawn at
  the offset, `top-offset` in the area tree; the foot honoured; an offset of 0 unchanged; an anchor with space-before)
  and `float_overflow-below.xml` (three cases, 11 checks: a word that does not fit beside a 300pt float goes below it
  with the rest of its paragraph; a word wider than the column stays; no float unchanged). Both green.
- `Docx4jHooksTestCase`: twenty-two capabilities. CR-022's and CR-020's float tests unchanged.
- The full `fop-core` suite: 3870 tests, 0 failures, 4 skipped; checkstyle 0 findings (2026-10-08).

## 6. docx4j

Writes `fox:float-offset` for `tblpY` on `float-offset`, dropping the `padding-top`; lifts the 2in rule on
`float-overflow-below`. A pass: `table-floating` from 0.6875 to 1.0 and the `table-floating-offset-sides` cases
at Word's positions; 4083's three remaining floats on Word's lines; the five documents of §1.2 recovering their
lines (6705 toward 249, 1616 toward 758, 9832 toward 58); nothing moving with neither attribute written.

## 7. Upstream

`fox:float-offset` is a `fox:` extension, the fork's. The overflow rule is an improvement to FOP's floats (Word's
behaviour, and no worse than overflowing the column); draft when measured, Jason's call, stacked on FOP-3355.

## 8. Not addressed

- A negative offset (Word pulls the table into the preceding paragraph and the top margin).
- A float taller than a page with its offset: placed and clamped to the page's end, as before (§4.1).
- Two floats on one anchor are one band from the first top to the lowest foot (§4.1); Word sets text between two
  same-side floats. Both sides (§4.3); a float anchored in a table cell.

## 9. Gate (the docx4j session)

Pending on the corpora: r19 (5afcfc657; r18, with the reference at the resolved gap, superseded). The probes on r19
(the docx4j session, 2026-10-08): `table-floating`'s float lands at Word's y (its cell text 183.5 against 184.5) with the
anchor paragraph's five lines full width and the next paragraph at 188.0 against 189.1, 36 of 37 lines equal, from
0.6875; `table-floating-offset-sides` cases 1 to 3 at 179.5 against 180.4, case 2's next paragraph 266.8 against 267.8.
Case 4's residual was docx4j's: in document order the table lies between the previous paragraph and its anchor, so Word
applies the previous paragraph's 8pt space-after and the anchor's 24pt space-before both, where the FO with the table
moved into the float has the two spaces adjacent and FOP takes the larger; docx4j now forces the anchor's space-before
to the sum across a floated table and writes the offset as `tblpY` plus that space-after, and case 4's float is at
179.5 too. Cases 1, 3 and 4 still differ after the float: both-sides wrap (§4.3). The overflow rule's renderer-alone
control (b195, r18 renderer alone against b193) moved nothing. Gate b196 (docx4j writing both hooks on r19 against
b195, 2026-10-08): `float-offset` lifts the probes (`table-floating` 0.6875 to 0.9792, `offset-sides` 0.50 to 0.69 with all
four tables at Word's y, `wide-anchor-text` 0.78 to 0.97) and 6293 to Word's 9 pages (+16 lines); and shows two defects
of the hook and one finding against the overflow rule:
1. two offset floats on one anchor crash the layout: 3229 (offsets 43.3pt and 339.25pt in one paragraph) throws
   `NoSuchElementException` in `LMiter.next` from `BlockStackingLayoutManager.getNextKnuthElements` on the restart;
   reproducer `~/fidelity-cr030/repro/float-offset-lmiter-3229.fo`. docx4j's interim: no offset written for a pair on
   one anchor (padding as before);
2. an offset float whose top falls past the page's end is carried to the next page's top, split from its anchor
   paragraph: 4083's two floats at 51.55 and 47.45pt near a page foot, -10 lines and a page lost (11 to 10), where Word
   keeps anchor and table together on the next page; 9775 -5 (15pt offset) and 3640 -2 (4.2pt) not yet read;
3. `float-overflow-below` changes nothing for the sliver documents: with the 2in bound lifted, 6705, 1616, 9832, 8236
   and 14776 land exactly where b182 had them (240, 747, 47, 54 and 110 lines), since a line set at the float's foot
   re-flows with its paragraph where Word's in-flow table simply precedes the paragraphs, and the line events climb as
   before. docx4j keeps the 2in bound and does not use the capability for tables; it may still serve pictures.
b197 (docx4j's interim build on r19 against b195: single floats and text-box bands with the offset, pairs padded, the
2in bound kept): 6293 +16 to Word's 9 pages, 6131 +2, 1616 +1; 9775 -5 and 4083 -1 (the page-end case); the probes as
before. r20 (2f5db13ff) read on the four documents before its gate: 3229 renders its twelve pages with its pair offset;
4083 at Word's 11 pages with the B.2 table back under its question on page 2; 3640's paragraph back in the body (its
"Comparativement" line at 549.0 against Word's 550.0, where r19 drew it into the footer region at the page's foot, a
shape that arises only with docx4j's fonts and line counts); 9775 at 2 pages. Gates b198 (docx4j as committed, pairs
padded, r20 against r19), b199 (the pair guard dropped) and b200 (anchored-picture and framePr floats given
`fox:float-offset` for their padding-top, gated on `float-offset`, with the picture floats' overflow rule read) follow.
