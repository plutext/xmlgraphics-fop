# CR-023: the offset float, and a line that does not fit beside a float set below it

Status: IN PROGRESS 2026-10-08, on branch `CR-023-float-offset` off `2.11-docx4j.6` (after `fop/CR-022`, 13a3fbec7).
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

- The offset is measured from the top of the anchor paragraph's block including its space-before: 24pt of
  space-before on the anchor moves its first line 24pt down and leaves the table where it was (case 4).
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
`FloatContentLayoutManager` through the `Float` node. Only a positive offset does anything (§8).

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

- Layout tests `float_offset.xml` (the lines above the float full width; the float drawn at the offset; the foot
  honoured; an offset of 0 unchanged; an anchor with space-before) and `float_overflow-below.xml` (a word that does
  not fit beside a 300pt float goes below it with the rest of its paragraph; a word wider than the column stays).
- `Docx4jHooksTestCase`: twenty-two capabilities.
- The full `fop-core` suite and checkstyle before the gate.

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
- An offset that puts the float past the end of the page: the float goes to the page's end, as a tall float does.
- Both sides (§4.3); two floats on one anchor; a float anchored in a table cell.

## 9. Gate (the docx4j session)

Pending.
