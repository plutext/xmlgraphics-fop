# CR-012: a page numbered 0, behind a user-agent option; and the page label of a page numbered 0

Status: DONE 2026-10-04, gated PASS by the docx4j session (§6) and merged to `2.11-docx4j.5`; unreleased.
Registry key `fop/CR-012`. Capability `page-number-zero`.

Enterprise CR-001 §6.6 item 38, the docx4j session's (batch 50, found 2026-10-04):
- Word's `w:pgNumType w:start="0"` (a cover page numbered 0) is written as `initial-page-number="0"`.
- FOP makes it 1, so every PAGE and PAGEREF in the section prints one high.
- Honouring 0 exposes a crash in the PDF page labels.

## 1. Where FOP makes 0 into 1

Three places, not the two the item names:
1. at parse, `NumberProperty.PositiveIntegerMaker`, the maker `FOPropertyMapping` gives the property;
2. `AbstractPageSequence.initPageNumber`: `pageStart > 0 ? pageStart : 1`;
3. `AbstractPageSequenceLayoutManager.doForcePageCount`, which reads the next sequence's
   `initial-page-number` for `force-page-count="auto"` and applies the same clamp. Found by this CR's
   test.

XSL 1.1 makes the property a positive integer and allows 1 as error recovery, so FOP is conformant. Hence an
option rather than a default change.

## 2. The change

- **The option.** `FOUserAgent.setPageNumberZeroAllowed(boolean)` / `isPageNumberZeroAllowed()`, off by
  default. With it off, FOP behaves exactly as before.
- **The maker.** `NumberProperty.InitialPageNumberMaker` (used for `initial-page-number`) keeps 0 where the
  user agent allows it, and otherwise acts as `PositiveIntegerMaker`.
- **The two layout readers.** `initPageNumber` and `doForcePageCount` both accept 0 under the option, so
  the sequence and the previous sequence's `force-page-count` agree.
- **The page labels.** `PDFPageLabels.addPageLabel` writes an all-zero decimal label as a prefix label
  (`/P (0)`), since `/St` must be at least 1. It used to count leading zeros with a `do/while` that ran
  past the end of "0" (`StringIndexOutOfBoundsException`). This fix is unconditional. It is reachable only
  through a page numbered 0, which the option now makes possible.
- **The capability.** `Docx4jFop.PAGE_NUMBER_ZERO = "page-number-zero"`, the eleventh.

The docx4j session's draft proposed accepting 0 unconditionally. It is behind the option here because a
change to FOP's default would make the fork number pages differently from Apache FOP for any FO that says
`initial-page-number="0"`. The fork's rule is that a docx4j-specific change "changes nothing FOP does on its
own" (README, Hooks). The option is also the shape the session's own upstream draft proposes.

## 3. Tests

- `PDFPageLabelsTestCase.testPageNumberedZero`: "0", "1", "2" label as `[0 << /P (0) >> 1 << /S /D >>]`.
  Without the label fix it throws `StringIndexOutOfBoundsException: Index 1 out of bounds for length 1`.
- `PageNumberZeroTestCase`:
  - `initial-page-number="0"` gives pages 1, 2 by default and 0, 1 with the option.
  - With `force-page-count="auto"` on a one-page sequence before it, the default pads it (1, 2, then 1, 2)
    and the option does not (1, then 0, 1).
  - A PDF with the option on carries `/PageLabels` with `/P (0)`.
- `Docx4jHooksTestCase`: eleven capabilities.

## 4. What docx4j does with it

With `page-number-zero` present, docx4j:
- calls `FOUserAgent.setPageNumberZeroAllowed(true)` on the user agent it renders with;
- stops `LayoutMasterSetBuilder.foliosInverted`'s odd/even master swap, since FOP's folio then equals Word's
  number.

The docx4j session measured the effect in its harness, simulated on `.4`: 13347 0.8981 to 0.9444, 6083
0.8904 to 0.9324, 4899 0.8983 to 0.9267, 6251 0.9569 to 0.9589; nothing else moves. Its gate on this branch
is to follow.

## 5. Upstream

Two drafts. `docs/upstream/page-label-zero.txt`: the page-label crash, a bug. Filed 2026-10-05 at Jason's word as
[FOP-3350](https://issues.apache.org/jira/browse/FOP-3350); branch `FOP-3350` (014daf810), the fop-core suite
passing on `main` (3660 tests); pull request [#122](https://github.com/apache/xmlgraphics-fop/pull/122). `docs/upstream/page-number-zero-option.txt`: page 0 behind a user-agent option, an
improvement. **Dropped** (Jason, 2026-10-05): Apache will not accept a deliberate departure from XSL, which makes
`initial-page-number` a positive integer. The option stays a docx4j hook in the fork.

## 6. The docx4j gate, 2026-10-04: PASS

Run by the docx4j session on Jason's install of `fab8f6268` (copied aside with its md5s, so no later install
could move it). Basis b74-resaved-nofields: the three corpora and 173 probes.
- **Control**, docx4j without the setter call, against the same docx4j on `.4`: 0 documents and 0 probes
  moved in all three corpora. The renderer alone changes nothing, as the option's default promises.
- **Measurement**, docx4j b629f2be3 (the setter on both passes, `getFOUserAgent` and `calcResults`, and
  `foliosInverted`'s parity swap off under `page-number-zero`), against the same docx4j on `.4`: exactly four
  documents move, all up. 13347 0.8981 to 0.9444 (+10 lines), 6083 0.9207 to 0.9627 (+18), 4899 0.8983 to
  0.9267 (+71), 6251 0.9740 to 0.9760 (+3). Nothing else moves in the corpora or the probes. 278 and 2189,
  the other two corpus documents with `start="0"`, are unchanged.
- **Tests**: docx4j-export-fo and docx4j-export-fo-tests on the snapshot ran 240 and 682 with 0 failures
  (`PageMastersParityTest` took the page-zero branch); on `.4`, 682/0.

docx4j's side is committed locally on VERSION_17_3_1 as b629f2be3. It is inert until docx4j depends on a
release that carries this CR.

