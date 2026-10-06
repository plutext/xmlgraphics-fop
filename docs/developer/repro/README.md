# Minimal reproductions of open FOP defects

Synthetic FO, cut 2026-10-03 in the bounded pass over Enterprise CR-001 §6.6 items 18, 20, 21, 24 and 25.
Each fails on the fork's 2.11-based build (2.11-docx4j.2) and on `2.11-docx4j.4` (Apache `main`), with
Java assertions on and off. Run: `org.apache.fop.cli.Main -fo <file> -pdf out.pdf`.

- `float-then-block-in-inline-npe.fo` (Enterprise CR-001 §6.6 item 36): a float nested in a block, then a block nested in an inline (how
  docx4j writes a line break inside a run). `NullPointerException` in `TraitSetter.setVisibility`
  (`area` is null), from `BlockLayoutManager.addAreas`. A null guard stops the crash but the text after
  the inline's block is then lost ("after the break and on." is missing; present without the float),
  so the NPE is a symptom of content dropped on the float re-layout (`PageBreaker.handleFloatLayout`),
  not the defect. docx4j works around it with `WordLayoutFixups.hoistFloats`.
- `wide-float-overflow-npe.fo` (item 37): a float wider than the measure, then a block whose line overflows.
  `NullPointerException` in `LineLayoutManager$LineBreakingAlgorithm.updateData2` (`curChildLM` is null
  on the float re-layout pass), in the code that only reports the overflow. A `curChildLM == null` guard,
  as docx4j's own line manager has, is the whole fix: no layout effect.

Not reproduced from their descriptions (variants tried, none fails): item 20's NoSuchElementException
(a float followed by content that overflows the page), item 21's NPE from
`BlockContainerLayoutManager.addAreas` in a balanced multi-column flow, and item 24's assertion
(`initialColumns.size() == columnCount - 1` in `BalancingColumnBreakingAlgorithm.getInitialBreaks`, which
fails when the balanced content has no legal break past the ideal column length). Each needs the FO
docx4j wrote with its workaround off.

Added 2026-10-07 (fop/CR-020), from the docx4j session's findings, both fixed on branch `CR-020-float-edge-space`:

- `float-edge-after-space.fo` (Enterprise CR-001 §6.6 item 44): a 20pt side float in a 16pt heading line with
  `space-after="10pt"`, then a paragraph. The float's foot is at y 92; the paragraph's first line (98 to 114) is
  laid out beside the float at x 372 instead of 72, because `PageBreakingAlgorithm.handleBox` alone tested the
  foot and glue never did. With the fix the line is full width and the 10pt space is kept; before, a float edge
  falling on a paragraph boundary also discarded the space, as at a page break.
- `float-edge-inside-table.fo` (item 45, which is item 20's signature reproduced: `NoSuchElementException` from
  `LMiter.next` in `PageBreaker.handleFloatLayout`): a 40pt float outlasting a 14pt heading and its 10pt
  space, then a five-row table. The float's end fell between rows, and the re-layout from there asked the
  table's layout manager, which is not restartable, for a child it has no more of. With the fix the float ends
  at the first legal break after the table.
