# CR-027: the second merge of Apache `main` into the fork

Status: BUILT 2026-10-09 on branch `merge-main-2026-10-09` (cut from `2.11-docx4j.7` at 5b8061c09), the merge commit
6a4664dc4 of Apache `main` at c2d7e0aca (2026-10-08); not yet gated. Registry key `fop/CR-027`. The first merge is `fop/CR-009`
(`main` at ab5d6eba6, 2026-09-29); this one takes the eight commits since.

## 1. Why now

CLAUDE.md asks for a merge at every Apache release and whenever a fix sent from here lands upstream. Three have:
FOP-3348 and FOP-3349 (pull requests #120 and #121, merged 2026-10-05) and FOP-3356 (#125, merged 2026-10-07).
Jason ordered the merge on 2026-10-09, after the `2.11-docx4j.6` release.

## 2. The eight commits, oldest first

| commit | JIRA | what | verdict |
|---|---|---|---|
| b4d2c3d0b | FOP-3351 | `afp:page-group=false` allowed on the last page (AFP) | TAKE, merged clean |
| 744281b1e | FOP-3331 | a float's text positioning: a float as wide as its containing block goes in the flow (`addAreaInFlow`), and a float inside a block-container is placed by the flow's height | TAKE with two changes (§3) |
| db3602d17 | FOP-3331 | the first word of a paragraph restarted beside a left float is not dropped (`LineLayoutManager`'s restart no longer clears element 0 when it is not auxiliary) | TAKE, merged clean |
| b1675d877 | FOP-3352 | `GlyfTable.isComposite` false for an empty last glyph | RESOLVE: the fork keeps its general form (§3) |
| a36b42fab | FOP-3349 | ours: the overflow report on the float re-layout pass | identical to the fork's (`fop/CR-011`), merged clean |
| 5be8c69b6 | FOP-3348 | ours: a nested block's positions not wrapped twice | identical to the fork's (`fop/CR-011`), merged clean |
| 4b595b60d | FOP-3357 | Bengali script processor, with Hebrew and Hindi test cases | TAKE, new files |
| c2d7e0aca | FOP-3356 | ours: simulate-style per face | identical to the fork's (`fop/CR-021`), merged clean |

Nothing is left out. Both of `main`'s dependency pins stay as CR-009 left them (the released
`xmlgraphics-commons` 2.11 and Batik 1.19); none of the eight touches them.

## 3. The conflicts and how they were resolved

Seven conflicts, four on files where upstream merged the fork's own fixes beside the fork's copies:

- **`FloatContentLayoutManager`** (FOP-3331 against hook `float-offset`, `fop/CR-023`). Both sides are kept at the
  placement: upstream's addition of the flow's height when the reference area is a block-container, then the hook's
  shift to the float's target top. Two changes to upstream's new in-flow path:
  - **FOP-3353's fix** (the fork's pull request #126, branch `FOP-3353` at 57cc8a40b): the in-flow test compares the
    content's width with the width available to the float (`getContentAreaIPD()`), not with the content area's own
    width, which for a block-container child is the container's and so always held, putting every such float in
    the flow (the regression CLAUDE.md asked to fix with the merge). Its layout test `float_block-container-child.xml`
    comes with it.
  - **A float with `fox:float-offset` written, even as 0, never goes in the flow.** In the flow it could not be set
    below its anchor's top as the hook promises, and a word processor places a column-wide floating table by its
    offset. docx4j writes the offset on every float it gates on the hook (floating tables, text-box bands, anchored
    pictures, frames), so its floats keep `.6`'s placement; Apache's users, and an FO without the attribute, get
    FOP-3331's in-flow placement.
- **`GlyfTable`**: the fork's `isEmpty` (any empty glyph, FOP-3339) subsumes upstream's `isLastGlyphEmpty` (the last
  glyph only, FOP-3352); the fork's form is kept with a comment naming both, and both test sets stay in
  `GlyfTableTestCase` (upstream's `testIsCompositeOnEmptyGlyph` passes against the general form).
- **`PDFPainterTestCase`**: javadoc only; the fork's wording, which cites `fop/CR-021`.
- **The three layout tests added on both sides** (`float_overflow_relayout.xml`, `inline_block_nested_float_relayout.xml`,
  `inline_block_nested_ipd_relayout.xml`): upstream's copies, which are the merged pull requests' files and now
  Apache's; the fork's carried the same cases with the fork's own wording.

Merged clean and worth knowing: `LineLayoutManager` gains db3602d17's four lines in the restart (a restart at
index 0 of a paragraph whose first element is not auxiliary now clears nothing), which the balancing trial's replay of
a restart (`fop/CR-026` §9) runs through the same code on both the breaker's and the throwaway managers.

## 4. Verification

- Full `fop-core` suite and checkstyle on the merge (§5 records the counts).
- The gate (the docx4j session, baseline b218 on r27): 0 movers expected on the four corpora and the probes, since
  every float docx4j gates on the hook carries the offset and keeps its placement, and the three fixes of ours that
  came back are byte-identical. Where FOP-3331's in-flow path does fire, it is on a float without `fox:float-offset`
  whose content is as wide as the column, which docx4j does not write.

## 5. As done

The merge commit is 6a4664dc4. The in-flow guard first tested the offset's value, and FOP-3353's own test failed on it:
the property defaults to 0pt, so no float ever took the in-flow path. `Float.hasFloatOffset()` now reads the
explicit property at bind time. Full `fop-core` suite on the resolved merge 3924 tests, 0 failures, 4 skipped (42
more than `.6`: the Bengali, Hebrew and Hindi cases, FOP-3331's two float tests, FOP-3353's); layout engine suite
784/0 and checkstyle clean after the guard's fix; the full suite on the final state (389154e8a) 3924 tests, 0
failures, 4 skipped.

## 6. Gate

