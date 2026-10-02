# CR-008: both units of a surrogate pair resolve to one bidi level

Status: DONE 2026-10-03 on `2.11-docx4j.3`, committed there directly at Jason's request (no CR branch);
gated the same day by the docx4j session, PASS (§6); unreleased. Registry key `fop/CR-008`. Upstream-bound:
[FOP-2918](https://issues.apache.org/jira/browse/FOP-2918), pull request
[#115](https://github.com/apache/xmlgraphics-fop/pull/115), which carries both halves of §2 (the second
added 2026-10-03, §5). No capability: docx4j has no workaround to drop.

No Enterprise CR-001 §6.6 item. Item 31 records that characters outside the BMP occur in no corpus
document, only in probes, so this is not a fidelity fix for the corpus. The fork carries it because
the word-splitting guards it already has (P2-1, CR-020 §8) move a right-to-left character outside the
BMP from one failure to another, and the fork should not lag the pull request it sent.

## 1. The mechanism

`UnicodeBidiAlgorithm.resolveLevels(CharSequence, Direction)` converts the text to scalar values and
leaves `-1` in the slot of each low surrogate, so the level array keeps one entry per UTF-16 unit. Its
javadoc says the two units of a pair come back with the same level. Two things broke that:

- `getClasses` gave the placeholder a class of its own, `SURROGATE`, which no rule resolves, so the
  placeholder stayed at the embedding level while its character took the level of its own class. A
  right-to-left character outside the BMP (U+10826, Cypriot) came back as levels 1 and 0.
- `SURROGATE` is neither strong, nor neutral, nor retained formatting, so rule N1's look-ahead stops at
  it. A neutral outside the BMP inside right-to-left text (U+1F300 between two Hebrew words, in a
  left-to-right paragraph) therefore did not see the strong text after it, fell to the embedding
  direction under N2 and resolved to level 0, where the same text with U+263A resolves wholly to 1.

Layout consequences, before this change, on the fork with P2-1's guards:

- `TextLayoutManager` ends a word where the level changes. The guard keeps the pair whole, so the word
  carries two levels, and `InlineRun.split` asserts "heterogeneous inlines not yet supported!!". With
  assertions off, as docx4j runs in production, the line is drawn wrong instead (§4).
- The neutral case asserts nothing: the pair's two units agree (both 0). But the right-to-left run is
  cut in two around it, so its words are not reordered.

## 2. The change

`UnicodeBidiAlgorithm`, two parts:

1. `resolveLevels(int[], int, int[])`: after resolution, each placeholder takes the level of the
   character before it. This is FOP-2918's branch as sent in #115 (18b437066 there), and it is what
   the javadoc promises.
2. `getClasses`: the placeholder takes the class of the character it belongs to (`classes[i - 1]`),
   so that the rules see the pair as the one character it is. `SURROGATE` remains only for a
   placeholder at index 0, which `convertToScalar` never produces.

Part 1 alone does not fix the neutral case, since it copies a level that is already wrong. Part 2 does.
With part 2, part 1 is redundant for every character Unicode assigns: outside the BMP the only bidi
classes are L, R, AL, EN, AN, ET, NSM, BN and ON (counted with Python's `unicodedata` over every
assigned code point above U+FFFF). There is no ES, CS, WS, S, B or explicit embedding, so no rule
treats a repeated class differently from a single one: the W4 "single separator" rule needs an ES or a
CS, and N1 and N2 take a run of neutrals as a run. Part 1 stays as the guarantee the javadoc makes.

`resolveLevels` is also reached from `BidiAttributedCharacterIterator` (SVG text), so the fix applies
there too.

## 3. Tests

- `SurrogatePairLevelsTestCase` (new): `testPairAlone`, `testPairBetweenLatinLetters` and `testTwoPairs`
  with U+10826 (from #115); and, new here, `testNeutralPairInsideRightToLeftText` (U+1F300 inside
  Hebrew, with and without a space before it, beside the same text with U+263A) and
  `testNeutralPairBetweenDirections` (N2, both paragraph directions; it guards part 2 against
  over-reaching and passes on every version).
- `wordbreak_surrogates.xml` (layout test, new to the fork): taken unchanged from `2918.patch` on the
  JIRA (kwilkerson, 2020).

Control runs, the same tests against three versions of `UnicodeBidiAlgorithm`:

| version | failing |
|---|---|
| the fork before CR-008 | 5 of 6: the four level tests, and the layout test with "heterogeneous inlines not yet supported!!" |
| part 1 only (#115) | 1: `testNeutralPairInsideRightToLeftText`, element 4, expected 1, was 0 |
| parts 1 and 2 | none |

The full build (`mvn -B package checkstyle:check spotbugs:check`) passes on parts 1 and 2: §7.

## 4. Measured on the command line

`org.apache.fop.cli.Main` on the fork's classes, with the version before CR-008 compiled separately and
put first on the classpath. Glyph positions are from `mutool draw -F stext`, levels from the area tree
(`-at`). The fonts are DejaVu Sans with Noto Sans Cypriot behind it, 14pt.

| text | before, assertions off | before, `-ea` | after (either) |
|---|---|---|---|
| `ab𐠦cd ef𐠦𐠪𐠦gh` | drawn `dc#ba ef` + two Cypriot glyphs + `hg#`: the mixed words are reversed and a Cypriot glyph is replaced by `#` | `AssertionError` | `ab𐠦cd`, then `ef`, the three Cypriot glyphs right to left, `gh` |
| `ab 𐠦𐠪 cd` | correct | `AssertionError` | identical to before, assertions off |
| `שלום🌀 עולם` (area tree) | the run is cut: `םולש` at level 1, the emoji at level 0, `םלוע` at level 1, so the two Hebrew words stay in logical order | the same | one level-1 run, `םלוע ␠ 🌀םולש`, the structure FOP gives `שלום☺ עולם` |

Where the pair's own levels were the only thing wrong, assertions off drew the line correctly, so in
production the first row is the visible defect and the second only a failure under `-ea`. The Hebrew
lines with mathematical alphanumerics (`𝐀𝐁`, class L) were glyph-identical before and after and passed
under `-ea` before.

## 5. What this does not fix, found on the way

- **FOP's bidi class table is older than Unicode 6.1.** `BidiClass` was generated around 2010
  (`GenerateBidiClass`), and it classes U+1F600 (Unicode 6.1) and U+1F900 as L where Unicode says ON.
  U+1F300 (6.0) and U+263A it has right. So the commonest emoji inside right-to-left text still splits
  its run, as a strong left-to-right character would, with or without this change. Measured here, at
  font-free level (`BidiClass.getBidiClass`) and on the command line (`שלום😀 עולם` cuts the run where
  U+263A does not). Over the whole table, against Python's Unicode 16.0 database: 3,683 of 155,063
  assigned code points (surrogates and private use excluded) differ. 3,143 are outside the BMP (1,137
  of the 2,361 in U+1F000 to U+1FAFF), and 2,317 differences overall are ON read as L. 540 are in the
  BMP: U+058F and U+20BA to U+20C0 (currency signs, ET read as L), U+0860 to U+086A (AL read as R),
  combining marks, and U+180E. FOP implements no isolates; U+2066 to U+2069 are BN to it. The table
  is the same on Apache `main`. Not fixed and not filed. The same kind of defect as Enterprise CR-001
  §6.6 item 29's line-break pair table, which predates Unicode 8.0. The docx4j session agreed it as
  an item, numbered 35, which it keeps.
- Pull request #115 had part 1 only. Part 2 and the two neutral tests are on branch `FOP-2918` as
  b401de0d9, pushed 2026-10-03 at Jason's word, with #115's body updated (item 7) and a comment posted
  (issuecomment-5962990508). On Apache `main` the neutral test fails on part 1 alone, and the full build
  passes (3669 tests, 0 failures, 4 skipped; checkstyle and spotbugs clean).
- Unrelated, already recorded: the position-adjustments paint path indexes its adjustments by UTF-16
  unit (Enterprise CR-001 item 33's note).

## 6. What moves, and the gate

By construction nothing moves in text without a character outside the BMP: both parts touch only
placeholder slots, and a placeholder exists only for such a character. Bidi levels are resolved only
for a range that holds R, AL, AN, RLE or RLO, or for a right-to-left paragraph
(`convertToScalar`'s trigger). So the movers are text that holds a character outside the BMP within
such a range, and Enterprise CR-001 item 31 says the corpus has none.

CR-020's P2-1 record said the bidi-level guard was "undemonstrated: no case was found in which the
two halves of a pair take different levels". U+10826 is such a case (§1). The docx4j session has
corrected that record in CR-020, which is its document.

### The gate, 2026-10-03: PASS

Run by the docx4j session through docx4j 17.3.0. The candidate was `2.11-docx4j.3-SNAPSHOT` as Jason
installed it from 9fab0a7c9 (the installed core's `getClasses` has three `iaload`, the released one
two); the baseline was the released `2.11-docx4j.2`. Three probes: `surrogate-pairs` (P2-1's),
`fonts-symbol-and-emoji`, and `surrogate-pairs-bidi`, new for this gate, built from the cases
proposed here in plain runs (no `w:rtl`), each paragraph labelled `P0n`. The record is in
`~/fidelity-cr008/` (`BASELINE.txt`, `GATE.txt`, `order.py`), local only.

The baseline shows that docx4j documents reach the defect. With `-ea`, `surrogate-pairs-bidi` aborts
with "heterogeneous inlines not yet supported!!", from `InlineRun.split` by way of
`BidiResolver.reorder` and docx4j's `WordLineLayoutManager.addInlineArea`. The other two probes render.
Visual order, read from `mutool draw -F stext`; Hebrew appears letter-reversed, as drawn:

| line | text | baseline (no `-ea`) | candidate (`-ea`) |
|---|---|---|---|
| P01 | `ab𐠦cd` | correct: docx4j splits the run at the font change | identical |
| P02 | `שלום 𐠦 עולם` | `𐠦 םולשםלוע`: the Cypriot letter at the left edge, the Hebrew words run together | `םלוע 𐠦 םולש` |
| P03 | `שלום👍 עולם` | `םולש👍םלוע`: the run cut, logical order kept | `םלוע 👍םולש`, P04's shape |
| P04 | `שלום☺ עולם` (reference) | `םלוע ☺םולש` | identical |
| P05 | `שלום😀 עולם` (control, item 35) | logical order kept; 😀 drawn as `#` | identical |
| P06, P07 | P03 in a `w:bidi` paragraph, and the rest | right-to-left order already | identical |

`surrogate-pairs` (6 lines) and `fonts-symbol-and-emoji` (6 lines) were identical to the glyph
position. No exception on any probe with `-ea`. The corpora are identical by construction: the
docx4j session's scan the same day found no character outside the BMP in any of the 454 corpus
documents (Enterprise CR-001 item 31 holds), and CR-008 changes nothing else. The two moving lines
are the two halves of §2: P02 is part 1 (an R character's pair), P03 part 2 (an ON character's
placeholder class).

## 7. Record

- Cherry-picked from branch `FOP-2918`: 18b437066 (part 1, `SurrogatePairLevelsTestCase`) and
  6f80646f6 (the layout test), with `-x`. On the fork, the test's static import moved below the others
  after a blank line (2.11's order), and the change notice was added.
- Part 2 and the neutral tests are new on the fork.
- Full build on the final tree: 3611 tests in fop-core, 0 failures, 0 errors, 4 skipped; checkstyle
  clean; spotbugs 0. (On part 1 alone, before the neutral tests: 3609, likewise clean.)
