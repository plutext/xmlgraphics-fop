# CR-012: a page numbered 0, behind a user-agent option; and the page label of a page numbered 0

Status: DONE ON BRANCH 2026-10-04, `CR-012-page-number-zero` off `2.11-docx4j.5`; not merged, not gated.
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

Two drafts. `docs/upstream/page-label-zero.txt`: the page-label crash, a bug. `docs/upstream/page-number-zero-option.txt`:
page 0 behind a user-agent option, an improvement. Neither is filed.
