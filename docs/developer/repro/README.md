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
