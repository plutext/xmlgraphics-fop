# CR-011: a nested block's positions are not wrapped twice when line breaking runs again

Status: DONE, merged to `2.11-docx4j.4` 2026-10-04 by fast-forward at Jason's word, with item 37 (§7), and without
a corpus gate. Through docx4j with Word layout on (the default), neither change can alter output by
construction: docx4j's `WordLineLayoutManager` carries its own copies of both methods, already fixed there
(2ba434600 and 53b2b5921). The full build passes: 3748 tests. Unreleased.
Registry key `fop/CR-011`. Upstream-bound: a FOP defect present in 2.11 and in Apache `main`. Its page-width
form is the scenario of FOP-1912 (Open since 2011). Filed as FOP-3348 (item 36, pull request #120) and FOP-3349
(item 37, #121) on 2026-10-05; see §6 and §7. Draft:
`docs/upstream/nested-block-rewrap.txt`. No capability: docx4j carries its own copy of the method (§5), so
the fix reaches docx4j through a docx4j change, not through a probe.

Enterprise CR-001 §6.6 item 36 (the `TraitSetter.setVisibility` NPE after a float, with a block inside an
inline), found by the bounded pass of 2026-10-03. docx4j works around it with `WordLayoutFixups.hoistFloats`.

## 1. The mechanism

Traced on the fork with debug copies of `LineLayoutManager` and `BlockLayoutManager`, on the reproduction in
`docs/developer/repro/float-then-block-in-inline-npe.fo`:

1. A block nested in an inline makes the line manager's `knuthParagraphs` hold three sequences: the inline
   text before the nested block, the nested block's own (block-level) sequence, and the inline text after it.
2. `postProcessLineBreaks` passes the nested block's elements upward as the same objects it keeps in
   `knuthParagraphs`, and wraps each in place: `tempElement.setPosition(new NonLeafPosition(this, ...))`.
   The enclosing block and flow managers then wrap the same objects in place again
   (`BlockStackingLayoutManager.wrapPositionElements`).
3. When line breaking runs again over the stored paragraphs, the elements still carry the first pass's
   wrapping and are wrapped a second time. Two paths rerun it: the float re-layout
   (`PageBreaker.handleFloatLayout`, which restarts after the float) and the IPD-change restart (a page of a
   different width). With the float, the nested block's chain read LLM > Flow > Block > LLM > Inline > Block,
   where it should read LLM > Inline > Block.
4. `addBlockArea` unwraps one level, lands on the flow manager, and so re-enters the enclosing block's
   `addAreas`. The inner call flushes the block's area and sets `curBlockArea` to null. Back in the outer
   call, the line after the nested block reaches `addChildArea`, which drops it silently while the area is
   null. Then `TraitSetter.setVisibility(curBlockArea, ...)` throws.

So the NPE is a symptom, and a null guard alone would turn a crash into lost text. Measured: with the guard,
"after the break and on." is missing from the PDF; without the float, it is present.

## 2. The change

`LineLayoutManager.unwrapEarlierPass(Position)`: before a nested block's element is wrapped in
`postProcessLineBreaks`, walk its position chain. If it holds a `NonLeafPosition` belonging to this manager,
start again from the position inside it. On a first pass nothing in the chain belongs to this manager (the
positions come from managers below it), so nothing changes there.

Only `NonLeafPosition`s are walked, since the earlier pass's wrapping is made of them. The first version
walked any position until `getPosition()` returned null, and it looped forever: `TableContentPosition`
returns itself. The full build caught it (it stalled for an hour in `LayoutEngineTestCase`), and a per-file
harness over the 778 layout-test FOs, 20s each, pinned it to `inline_block-level_nested_1.xml`, a table
inside an inline. After the correction, all 778 complete. The docx4j session had copied the first version
into docx4j; it was sent the corrected one the same day.

## 3. Tests

- `inline_block_nested_float_relayout.xml`: the float form; the paragraph's first line and the line after
  the nested block must both be laid out.
- `inline_block_nested_ipd_relayout.xml`: the page-width form; two pages, the paragraph broken onto the
  narrower second page, with the text after the nested block and the following block both present.

Both pass with the change and fail without it, with the `NullPointerException`.

## 4. Measured on the command line

| reproduction | before (2.11 and `main`) | after |
|---|---|---|
| float, then a block in an inline | NPE in `TraitSetter.setVisibility` | complete: "Before the run" and "after the break and on." beside the float |
| the same, with only a null guard (not shipped) | no exception, "after the break and on." lost | |
| a block in an inline across a change of page width | NPE in `TraitSetter.setVisibility` (on 2.11, on the fork's merged build, and on `main` with FOP-3344 and FOP-2349, which do not touch line breaking) | complete |
| a block in an inline in a balanced two-column flow, four variants | fine | fine (item 21 is not shown to be this) |

## 5. docx4j

docx4j's `WordLineLayoutManager` has its own `postProcessLineBreaks`, with the same in-place wrap at line
3565. With Word layout on (the default), the fork's change does not reach docx4j's path, so docx4j needs the
same unwrap in its copy. The defect is FOP's own (it fails on 2.11 and on `main`), so the docx4j change is
not capability-gated.

The docx4j session made that change the same day: `unwrapEarlierPass` verbatim in
`WordLineLayoutManager.postProcessLineBreaks`. It is uncommitted, Jason's call, and the CHANGELOG cites
FOP-1912. Its readings:
- `NestedBlockRelayoutTest`: both cases through docx4j's line manager. Both threw the NPE before the change
  (the float case through `PageBreaker.addAreasForFloats`) and are complete after it.
- docx4j-export-fo: 237 tests, 0 failures, on 2.11-docx4j.2, on Apache FOP 2.11 and on the `.4` snapshot.
- Corpora and probes against .2, on the `.4` snapshot without this CR: 0 changed with the change, and 0 changed
  with the change and `hoistFloats` skipped.
- `hoistFloats` acts in seven corpus documents. With hoisting skipped, released 17.3.0 throws the NPE on all
  seven; with the change, all seven render. Five lay out differently (floats anchor lower), with no
  scoreboard change.

The docx4j session's decision: `hoistFloats` stays. It buys nothing measurable with Word layout on, and it
still protects FOP's own line manager: Word layout off, Apache FOP, or a fork without this CR.

Through docx4j with Word layout on, this CR changes nothing by construction, since docx4j's line manager
uses its own copy of the method.

## 6. Upstream

Filed 2026-10-05 at Jason's word as [FOP-3348](https://issues.apache.org/jira/browse/FOP-3348), linked "relates to"
FOP-1912 (Open since 2011, "Block elements inside inline elements that cause a page break where the next page has a
different IPD causes NullPointerException"). FOP-1912 is this scenario's page-width form, but its 2011 trace is at
`AbstractBreaker.getNextBlockListChangedIPD`, a different site, so it may be a different fault in the same scenario,
which `main` now reaches as this NPE. Branch `FOP-3348` against Apache `main` (1a86a5946): the fop-core suite passes,
3665 tests, checkstyle clean, and both layout tests throw without the change. Pull request
[#120](https://github.com/apache/xmlgraphics-fop/pull/120). Porting it found `unwrapEarlierPass` placed between
`postProcessLineBreaks` and its javadoc; moved, on the branch and in the fork (f798b14d5).

## 7. Item 37 on the same branch (Jason, 2026-10-04)

Enterprise CR-001 §6.6 item 37: a float wider than the measure, then a block whose line overflows. On the float
re-layout pass, `LineLayoutManager$LineBreakingAlgorithm.updateData2` reports the overflow through
`curChildLM.getFObj()`. `curChildLM` is null on that pass, so FOP threw a `NullPointerException` instead of
reporting the overflow. Reproduced on 2.11 and on Apache `main` (`docs/developer/repro/wide-float-overflow-npe.fo`).

The change: `curChildLM == null ||` added to the existing `getFObj() == null` test, so the overflow is
reported against the line manager's own FO, as it already was where the child had no FO. The code only
reports; layout does not change. docx4j's `WordLineLayoutManager` already carries the same guard
(53b2b5921), so nothing changes through docx4j.

Test: `float_overflow_relayout.xml` (one page; the overflowing word laid out). It fails without the change
with the `NullPointerException`. No JIRA matches (searched `updateData2`, `lineOverflows`, and float with
`LineLayoutManager`). Filed 2026-10-05 as [FOP-3349](https://issues.apache.org/jira/browse/FOP-3349)
(`docs/upstream/overflow-report-null-child.txt`); branch `FOP-3349` (3bfb3d376), the fop-core suite passing on `main`
(3662 tests); pull request [#121](https://github.com/apache/xmlgraphics-fop/pull/121).
