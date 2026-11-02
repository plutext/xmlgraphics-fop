# CR-031: Word layout in the fork's layout managers (docx4j CR-071 phase 1)

Status: DONE 2026-11-02 for phase 1: built at 27ed437d3, gated PASS, merged to `2.12-docx4j.1` by fast-forward
(e5f0a7d12), unreleased, local until Jason pushes. CI's command on JDK 21: 4077 tests, 0 failures, 5 skipped;
checkstyle 0; spotbugs 0. r51 (`~/fop-renderers/r51-CR-031-27ed437d3/`, core sha256 98eb2a20...) passed CR-071
phase 2's gate (§5.1). Phase 3 (§6) is DONE too: gated PASS (b306 on r53) and merged to `2.12-docx4j.1` by fast-forward at c4ba99965 on
Jason's word (2026-11-02: "it is enough to make a git tag for move alone with no change in output"); the move alone
is tagged `CR-031-phase1` at 3d9a41143. In progress since the same day, on Jason's acceptance of docx4j CR-071
("CR-071 D1 to D11 as recommended, except for D9 where we decide on (a)"). docx4j-12 relayed the acceptance; it is recorded in docx4j a72346619. The work
is on branch `CR-031-word-layout`, cut from `2.12-docx4j.1` at a5e22789f. Phase 1 starts from docx4j's copy after
CR-071's phase 0, 9ec0e1045, which added only FOP-3331's and FOP-3333's hunks and `FirstWordBesideFloatTest`
(checked). Under D9(a) the move lands in `2.12-docx4j.1`, whose
Central release waits on this CR (`fop/CR-030`), and docx4j 17.3.6 waits with it. Registry key `fop/CR-031`.

The design is docx4j's CR-071, `../docx4j/docs/developer/change-requests/CR-071-word-layout-in-the-fo-renderer.md`.
§5 has its phases and §6 its decisions D1 to D11. This CR is CR-071's phase 1, the fork's part. Phase 2 is docx4j's:
it switches to the fork's Word mode, gates it, and deletes its copy. Phase 3 is this CR's §6: CR-023 §4.2's mark under
Word mode, gated on its own.

This CR is not a §6.6 item of its own. It serves Enterprise CR-001 §6.6 item 4 (no greedy breaking in FOP, corrected
in Enterprise 6a5bf6f to about 920 lines of breaker and about 3,000 of Word delta). It also re-homes into the fork the
docx4j-side workarounds that live in docx4j's `org.docx4j.fop.wordlayout`, items 14, 16, 19, 22, 27, 28 and 29 among
them. docx4j's phase 2 re-homes those entries in §6.6; until then they stand as written.

## 1. Why

docx4j's Word layout is a set of FOP layout managers that docx4j substitutes for FOP's own through
`FopFactoryBuilder.setLayoutManagerMakerOverride`. Its main piece is a 4,701-line copy of FOP 2.11's
`LineLayoutManager` with greedy breaking. The copy costs a hand re-derivation at each FOP upgrade, and the 2.12 move
missed FOP-3331 and FOP-3333 (CR-071 §1). It also reaches FOP's private members by reflection (`LBP`, 998 lines). On
the fork side, it is why CR-024 §10.2 put the line-width provider below the line manager: anything built inside the
line manager reached the gate only once docx4j had copied it. §6.6 items 36 and 37 were each fixed twice for the same
reason, FOP-3348 and FOP-3349 here, and 2ba434600 and 53b2b5921 in docx4j.

## 2. The source, measured 2026-11-02

docx4j's package `org.docx4j.fop.wordlayout` at `VERSION_17_3_6` (a72346619): 8,206 lines in 13 classes (CR-071 §2).
Phase 1 starts from the copy after CR-071's phase 0, which adds FOP-3331's restart index and FOP-3333's
`completeTraits` and is gated against b301. The fork's own `LineLayoutManager` already has both. When docx4j sends
phase 0's commit, its diff against a72346619 is checked here for anything else.

The copy's `WordLineLayoutManager` against Apache 2.11's `LineLayoutManager`, member by member (scripts in the
session's scratchpad, `cr071/members.py`):

- Of 2.11's 55 members, 38 are byte-identical in the copy, apart from the class name.
- 14 are modified: +272 and −120 lines in all. Most of that is mechanical: `lbp.x` became `LBP.x(lbp)`,
  `new LineBreakPosition(...)` became `LBP.create(...)`, and `log` became `LineLayoutManager.log`. The Word changes in
  them are short branches:
  - the constructor reads the `docx4j:` attributes: line box, baseline, line rule, label ascent, space shrink,
    hyphenation zone, limit and caps, tab stops, the tab default and indents, and justify-soft-return;
  - `Paragraph`'s `textAlignmentLast` is settable (`justifyLastLine`);
  - `collectInlineKnuthElements` runs four element passes (`fixLetterSpaces`, `fixSpaceLetterSpaces`,
    `suppressWordBreaks`, `solidusLeadingBreaks`) and the soft-return justification;
  - `findOptimalBreakingPoints` runs `seamBreaks` and `emergencyBreaks`, then makes one greedy pass in place of FOP's
    three Knuth passes;
  - `makeLineBreakPosition` sizes the line from `wordLine` when a line box is given;
  - `updateData2` uses the tab alignment;
  - `postProcessLineBreaks` and `getChangedKnuthElements` handle the leading glue, widows across soft returns, and
    CR-024's edge-only breaks, written as `relaxedForFloat`;
  - `getHyphenContext` leaves capitals unhyphenated and normalises the word for the pattern lookup;
  - `addInlineArea` handles the tab alignment, a tab kept at a line's ends, the page-number tab fix and the TOC
    leader grid;
  - `getNextKnuthElements` returns nothing to restart from an empty paragraph list.
- About 70 members are new and self-contained, some 2,900 lines with their comments. The `LineBreakingAlgorithm`
  members are the greedy breaker: `findBreakingPoints`, `considerLegalBreak`, `computeDifference`, `handleGlueAt`,
  `wordLine`, `tabWidth` and their helpers, about 920 lines. The line manager's own members are the tabs and leaders,
  the emergency, seam and solidus breaks, the letter-space fixes, the soft returns and the hyphenation zone.

The copy against the fork's `LineLayoutManager` at 36ee05557, after phase 0, differs in three places:
- the inline-access accessors on `LineBreakPosition`, which `LBP` stands in for: no behaviour change;
- CR-024's `handlingObstacle` and edge-only break: equivalent by reading;
- CR-023 §4.2's mark: `updateData2` sets `LineBreakPosition.setOverflowsBesideFloat`, and the copy never does (D11,
  §6).

The other managers are subclasses in docx4j:
- `WordBlockLayoutManager` re-creates `BlockLayoutManager`'s private line-manager creation.
- `WordFlowLayoutManager` moves the leading glue behind breaks and bounds keep chains, with four settings of its own.
- `WordListItemLayoutManager` keeps the last line's leading discardable and puts the label's suffix space in the text
  layer.
- `WordPageNumberCitationLayoutManager` measures an unresolved citation as digits.

Apache's churn in the five classes since 2020-01-01: `LineLayoutManager` 13 commits (two of them the fork's, FOP-3348
and FOP-3349), `BlockLayoutManager` 2, `ListItemLayoutManager` 10, `FlowLayoutManager` 3,
`PageNumberCitationLayoutManager` 0. None has changed since 2.12.

## 3. Design

### 3.1 The switch and the settings (CR-071 D3, D4, D5)

- `FopFactoryBuilder.setWordLayout(Map<String, String> settings)` sets the switch.
  - A map turns Word mode on; `null`, the default, leaves it off. Off, every branch below is skipped and the fork
    lays out as Apache FOP.
  - The keys are docx4j's property names, `docx4j.convert.out.fo.wordLayout.*`, and the values are the strings docx4j
    resolved (a system property first, then `docx4j.properties`).
  - The fork reads the 23 keys its managers use and ignores the rest. docx4j's FO writer reads its own keys.
  - The switch key itself, `docx4j.convert.out.fo.wordLayout`, is docx4j's: docx4j passes the map only when it is
    true.
  - The method takes only JDK types, so docx4j can reach it by reflection. Its source must compile against Apache FOP
    2.12 under `-Papache-fop`.
- The map is parsed once, into an immutable `org.apache.fop.layoutmgr.word.WordLayoutSettings`. That class carries
  docx4j's defaults and parses as `WordLayoutCustomizer` does today: `Boolean.parseBoolean` and `Double.valueOf` on
  the trimmed value, and an unparseable number counts as absent.
- The plumbing is CR-028's option's: `FopFactoryConfig.getWordLayout()`, `FopFactory.getWordLayout()` and
  `FOUserAgent.getWordLayout()`. The managers read it as `fobj.getUserAgent().getWordLayout()`, as Apache's FOP-2860
  option is read in `LineLayoutManager`. There is no configuration-file element (D4(a)).
- The settings are factory-scoped. docx4j builds a factory per conversion (`FORendererApacheFOP`), and today's
  call-time reads come to the same thing.
- Capability `word-layout`, `Docx4jFop.WORD_LAYOUT`, the twenty-eighth. It also covers D6's break-opportunity entry
  point.
- **Font metrics, a second setter (found in the port, agreed with docx4j-12, docx4j c0e6bf6c1).** The copy's
  `wordLine` called docx4j-core at runtime, `PhysicalFonts.get` and `WordLineMetrics.get`, for a run's ascent share
  `(winAscent + externalLeading) / lineHeightFactor()`. The fork cannot depend on docx4j, so docx4j passes the lookup:
  - `FopFactoryBuilder.setWordLayoutFontMetrics(BiFunction<String, String, Double>)` takes (the run's `docx4j:font`
    or `null`, FOP's full font name) and returns the share or `null`;
  - it is read as `WordLayoutSettings.ascentShare`, and the block-text check stays in the fork;
  - JDK types only, it takes effect only with Word mode on, and the order of the two calls does not matter;
  - without it, the block font's ratio is used, as the copy does when it finds no metrics.

### 3.2 The `docx4j:` namespace

- `org.apache.fop.layoutmgr.word.WordLayoutElementMapping` holds the namespace `http://docx4j.org/fop/word-layout`
  and its attribute names, the same constants as docx4j's class. It is registered in fop-core's
  `META-INF/services/org.apache.fop.fo.ElementMapping` and defines attributes only, no elements.
- It is registered whether Word mode is on or off: docx4j writes the attributes only when the renderer advertises
  `word-layout`.
- While docx4j's own mapping is still on the classpath (phase 2's transition), FOP registers both for the one
  namespace. The last one registered wins (`ElementMappingRegistry.addElementMapping`), and neither defines elements,
  so that is harmless.

### 3.3 The line manager (D2)

- **Placement, as built.** The class beside the line manager is a subclass,
  `org.apache.fop.layoutmgr.inline.WordLineLayoutManager extends LineLayoutManager`. `BlockLayoutManager` makes it in
  Word mode. It holds only the Word members: the 38 unchanged members, the copy's duplicated state, and Apache's code
  in the 14 are the superclass's.
  - Its greedy breaker is an inner class, `WordLineBreakingAlgorithm extends LineBreakingAlgorithm`, so the copied
    greedy members keep their implicit access to the line manager's state.
  - The copy's 4,725 lines become 3,100. The other 1,600 were the copy's FOP code and `LBP`'s accessors.
- **The edit to Apache's `LineLayoutManager.java`** is seams (each commented "Word mode's seam"), the inline
  postprocessing, and visibility. Off, the seams return Apache's values and the postprocessing's Word branches are
  neutral (`linesBefore` null, `pendingLeading` 0).
  - The breaker's seams: `lineTextAlign`, `lineAlignDifference`, `hasWordLineBox` and `makeWordLineBreakPosition`,
    plus `marksOverflowBesideFloat`, which is D11.
  - The manager's seams: `processInlineElements` and `endingParagraph` in `collectInlineKnuthElements`;
    `hasLeadingBelowLines` and `linesAcrossSoftReturns` in `postProcessLineBreaks`, whose leading glue and widows
    across soft returns are inline; `hyphenatable` and `forPatternLookup` in `getHyphenContext`; and `lineAlignment`,
    `isTab` and `lineAreasAdded` in `addInlineArea`.
  - `findOptimalBreakingPoints` is overridden whole: it is Word's driver, one greedy pass. The restart guard for an
    empty paragraph list overrides `getNextKnuthElements` and then calls `super`.
  - Visibility: the manager's state, `Paragraph`, the inner breaker with its state, `findOptimalBreakingPoints`,
    `findHyphenationPoints`, `handlingObstacle` and `pageSequenceHasObstacles` are package-private. `Paragraph` gains
    `justifyLastLine()`.
  - The member diff against the copy, normalised for `LBP`, matches but for the seams and D11
    (`cr071/norm.py` in the scratchpad).
- **`LBP` goes.** Its line-position accessors become the fork's getters (`inline-access`). Its leader logic is now
  `org.apache.fop.layoutmgr.inline.WordLeaders`, calling the `leader-placement` and `inline-access` accessors
  directly, with `Leader.setRuleStyle(int)` (`rule-style-int`).

### 3.4 The other managers

- **`BlockLayoutManager`:** in Word mode, `createLineManager` makes `WordLineLayoutManager`, and the
  `justify-soft-return` follow-up of `WordBlockLayoutManager.createWordLineManager` goes with it. That is a branch,
  since FOP's method is private; `WordBlockLayoutManager` goes.
- **The flow, list-item and page-number citation managers move as subclasses, as built.** Each of docx4j's overrides
  calls `super` and then adds to the result, so nothing of FOP's is copied:
  - `WordFlowLayoutManager`, in `org.apache.fop.layoutmgr`, with its four keep-chain settings read from the settings;
  - `WordListItemLayoutManager`, in `org.apache.fop.layoutmgr.list`, through `getBodyList()` (`inline-access`);
  - `WordPageNumberCitationLayoutManager`, in `org.apache.fop.layoutmgr.inline`.
  `LayoutManagerMapping` makes them where the FO's user agent has Word mode, in `makeFlowLayoutManager` and the
  list-item and citation makers, so no maker override is needed. docx4j stops setting one in phase 2.
  `LayoutManagerMapping` is the only place FOP builds the five managers, so Word mode reaches the same blocks the
  copy did.
- **`LeadingGlue`:** moves to `org.apache.fop.layoutmgr.word`.

### 3.5 Break opportunities (D6)

`org.apache.fop.layoutmgr.word.WordBreakOpportunities` holds docx4j's class's rules with a public static entry point,
`breakBefore(CharSequence)`. Its other public statics are unchanged: `noBreakAfter`, `noBreakBetween`,
`noBreakBeforePerCent`, `breakBetween`, `breakAtSeam`, `startsSolidusLedWord` and `applyWordPairTable`. docx4j's
`TableWriter` calls `breakBefore` by reflection while it writes the FO, before any factory exists.

The UAX #14 pair-table flip (§6.6 item 29) goes through the fork's existing `LineBreakUtils.setLineBreakPairProperty`
(`pair-table`), with no reflection. `FopFactory`'s constructor applies it when the factory has Word mode on and
`breakOpportunities` is true.

The flip is process-wide, as it is today in docx4j. It reaches any FOP layout in the same JVM after the first Word-mode
factory is built. That is a recorded limit, unchanged by the move.

fop-core's own suite measured that reach on 2026-11-02. CI's command failed one test,
`LineBreakStatusTestCase.testNextChar`, case `"EX CL ! ) break"`. FOP's table allows no break at the space before
`!`, and Word's correction (`breakAfterSpacesBeforePunctuation`) allows one, and the Word tests had run earlier in the
same JVM.

The product's behaviour stays docx4j's. For the suite, `WordBreakOpportunities.restoreFopPairTable()` puts back the
cells the flip changed, which it now records, and each Word test class calls it `@AfterClass`. A host that needs FOP's
table back after Word mode can call it too.

### 3.6 What else changes in the moved code

- Logging becomes commons-logging (D8).
- docx4j references are replaced by fork constants or direct calls:
  - `org.docx4j.model.HyphenationSettings.DEFAULT_ZONE_TWIPS` becomes a fork constant;
  - the `FopCapabilities.has(...)` checks become direct, since the code is now in the renderer that has the hooks;
  - `WordLayoutCustomizer.x()` becomes a read of the settings.
- Checkstyle: tabs become spaces, long lines are wrapped, the star import is expanded, and FOP's import order is
  applied.
- Each modified Apache file carries the change notice; new files carry the licence header.

## 4. Tests (D7)

- docx4j's 19 manager-level tests at phase 0 (9ec0e1045) move, renamed `*TestCase` for surefire. A test that calls a
  package-private static lives in that class's package: `DeclaredBaselineLine`, `LetterSpacingWidth`,
  `NumberingLabelLeaderSpace` and `TabStop` in `layoutmgr.inline`; `KeepChainBound` and `WordLeading` in `layoutmgr`.
  The rest are in `layoutmgr.word`.
- Each builds its factory with `setWordLayout(WordLayoutTestSupport.settings())`. That test class gathers the
  `docx4j.convert.out.fo.wordLayout.*` system properties into the map, which is docx4j's resolution without its
  `docx4j.properties` file. So the tests that set a property keep doing so.
- `LetterSpacingWidth` took Liberation Serif from docx4j's fonts module. It now uses fop-core's own DejaVu LGC Serif:
  any font with GSUB and GPOS tables takes the complex-script path, which is what the test compares.
- `FopHooksTest` has no counterpart: `FopHooks` goes.
- Measured 2026-11-02: 141 run (with `WordLayoutSettingsTestCase`), 0 failures, 1 skipped.
  `fopItselfStillHasTheDefect` skips on its own `Assume`: the fork fixed that defect in CR-010.
- FOP's own suite, run with Word mode off, is the test that off means Apache FOP.
- `Docx4jFopTestCase`'s capability count goes to 28.

## 5. Pass criteria

1. CI's command (`mvn -B package checkstyle:check spotbugs:check`) is green on JDK 21: FOP's suite with no failures,
   the 19 ported tests green, checkstyle 0 and spotbugs 0.
2. A drop, `~/fop-renderers/r51-CR-031-<commit>/`, goes to docx4j-12 for phase 2. docx4j's copy is switched off and
   the fork's Word mode switched on, against phase 0's baseline. A pass is 0 movers, every event count equal and 28
   hooks.
3. On a pass, `CR-031-word-layout` is merged to `2.12-docx4j.1` by fast-forward, and the README's hooks and changes
   tables get their rows. Then comes the release runbook for `2.12-docx4j.1` on Jason's word.

### 5.1 The gate, 2026-11-02 (docx4j-12)

PASS. Two gates on r51's core, events and util with b302's other jars, against b302:

- b303: phase 2's `docx4j-export-fo`, docx4j's copy still in the jar but unused;
- b304: the copy deleted, docx4j 69bbd4dc0 (unpushed), the committed code; its bytecode is b304's but for line
  numbers.

Both had 0 movers. Every scoreboard and `classes.txt` is byte-identical to b302's in real, real2, real3, real-c2 and
the probes. The event counts are equal (linebig 3243, line 17776, clip 1552, bcov 1276, bcovi 516, region 73, body 18,
glyph 63, font 8, other 87), and the renderer reports 28 hooks with `word-layout`.

docx4j's side, as phase 2 built it:

- `setWordLayout` gets every `docx4j.convert.out.fo.wordLayout.*` key, a system property over `docx4j.properties`;
- `setWordLayoutFontMetrics` gets the lookup as §3.1 specifies;
- no maker override and no `applyWordPairTable` call; `TableWriter` calls `WordBreakOpportunities.breakBefore`
  through a method handle;
- docx4j's own `ElementMapping` registration is gone, so the fork's is the only one for the namespace;
- docx4j's `WordLayoutAttributesTest` checks that the 19 attribute constants of `WordLayoutElementMapping` equal
  docx4j's.

`docx4j-export-fo`'s 110 tests and `docx4j-export-fo-tests`' 872 pass on r51, Word and non-Word FOP mixed in one JVM.
docx4j-12 saw no pair-table interference there, and docx4j calls `restoreFopPairTable` nowhere. On Apache FOP 2.12,
51 `docx4j-export-fo-tests` methods need Word mode and skip themselves. docx4j's 20 test classes for the copy are
deleted. Every test method of its 19 manager tests is among the cases ported here (§4), by name; `FopHooksTest`'s six
tested the deleted class. The fork's code was built from `git archive` of 27ed437d3 into docx4j-12's scratch Maven
repository, so nothing went into `~/.m2`. Enterprise register a99d2b8 records the gates and notes the move under
§6.6 items 16, 22, 27, 28, 29, 36, 37, 38, 40 and 50.

## 6. Phase 3: CR-023 §4.2's mark under Word mode (D11)

In phase 1, `updateData2` does not set `overflowsBesideFloat` when Word mode is on, so phase 2 is a pure move.

Phase 3 turns it on as a separate commit with its own gate. It also re-measures Enterprise CR-001 §6.6 item 8's
"float-overflow-below measured inert on docx4j's tables". That reading went through docx4j's managers, which never set
the mark, so it may not say what the rule does under Word layout.

### 6.2 The r52 gate, and r53 (2026-11-02)

**Gate b305, r52 against b304: FAIL** (docx4j-12; the run used b304's classpath with r52's core). It found three
things.

1. **One mover, the wrong way.** real3 `12_es-ES_tradnl_sdt_fields1_num_tbl_8985` lost 7 matched lines, parity
   0.9609 to 0.9569, pages 13 to 47 different.
   - On page 13, b304 matches Word. b305 ended that page four lines into the paragraph after two side-by-side tables.
   - The "Adyacencias" table looked about 3 characters further left in b305, but that came from pdftotext's columns.
     Read with `mutool`, both tables are at the same x and y in b304 and b305.
2. **One change that is not a mover.** In real3 `14_es-DO_tbl_4253`, a line beside a table was no longer split into
   fragments. That text is a docx4j duplicate, which Word does not paint.
3. **Events.** `line` rose 17776 to 18822 and `linebig` 3243 to 3304, in 9 documents. Seven of the nine render
   identically. 13986 alone added 854. docx4j-12 confirmed that `line` and `linebig` are both FOP's
   `lineOverflows`, split at its 50pt message.

**Traced here** with the RenderFo harness (`~/fop-session-tools/2026-10-15-renderfo/`) on the gate's classpath
(`~/fidelity-cr030/cp-b305.txt`). One-pass renders of r51 and r52 reproduce b304 and b305 page for page. Each of
r52's two changes was then laid alone over the r52 jar.

- **8985 is the mark alone.** The emergency change alone renders b304.
  - Page 13's two floating tables, 223.9pt left and 191.5pt right, take the whole column: the lines beside them have
    0pt.
  - Ten empty paragraphs (`white-space-treatment="preserve"`, one space) follow the tables. In b304, as in Word's
    page, nine of them are laid beside the floats and the tenth below.
  - Under the mark, the second empty paragraph's line overflowed its 0pt by the space's 3.336pt and was marked. The
    page breaking took the float's edge there and set the rest below the floats at full width, 20.7pt each. That
    pushed the paragraph after them 165pt down, off the page.
- **The events are the emergency change alone.** 13986 logged 173 overflow warnings on r51 and 1,051 on r52. The
  float layout lays the rest of the flow beside the float in a pass whose result it discards. Measured against the
  column, a word wider than the narrowed line is no longer split in that pass, so it overflows there and is
  reported. 8985's warnings on r52 differ from r51's by the same -27 the gate saw.

**r53, as built:**

- **The emergency change is reverted.** Word mode again splits a word too wide for the room beside the float, against
  that room, as docx4j's copy did. It cost about a thousand overflow events per gate, from passes that are thrown
  away, and measured no gain: 4253's changed text is docx4j's duplicate. Whether Word moves such a word below the
  float, as CR-023 assumes from tables, or breaks it beside the float has not been read from Word. A Word probe
  would settle it.
- **The mark is set under Word mode, but not on a line that holds nothing but white space.** The seam is now
  `marksOverflowBesideFloat(par, from, to)` in the base, returning true. `WordLineBreakingAlgorithm` returns true
  only where the line holds a box of nonzero width that is not auxiliary and is not a space mapping.
- **Measured on the gate's classpath:**
  - 8985 renders as b304 on every page, with r51's 12,645 overflow warnings;
  - 4253 renders as b304;
  - 13986 gives r51's 173 warnings.
- **`OverflowBesideFloatTestCase` has three cases; each fails without the change it tests.**
  1. A 228pt SVG, which no break can split, beside a float leaving 151pt in a 451pt column. Its line and the rest of
     its paragraph go to the float's foot at full width, after the 18pt clearance. This fails on the merged code.
  2. A word wider than the column stays beside the float, as in FOP's layout.
  3. Two floats that fill the column, then five empty paragraphs. The anchor's line and the four empty paragraphs
     above the floats' foot stay beside them at 0pt. This fails with the mark on every line.
- **FOP's own layout is unchanged by r53.** It keeps CR-023's rule as released. On case 3 it moves the empty
  paragraphs below the floats, keeping one beside, which on 8985's evidence is not what Word does. That is recorded
  here, not changed: FOP's layout is not docx4j's path.

**Pass criteria for r53's gate** (against b304 on r51):

- movers only on pages with a side float, each a line or a paragraph's tail moved from beside the float to its foot;
- no text lost;
- every event count equal outside the documents that move: the mark changes no report, and a line it moves is an
  overflow b304 already reported;
- 28 hooks.

The likeliest reading is 0 movers, meaning the mark is inert under Word layout on docx4j's corpora, as §6.6 item 8
says of the stock managers.

**Gate b306, r53 (61b7e71e1, core sha256 e90c79a8...) against b304: PASS** (docx4j-12, 2026-11-02).

- 0 movers, and every scoreboard and `classes.txt` identical to b304's in real, real2, real3, real-c2 and the probes.
- Events equal: linebig 3243, line 17776, clip 1552, bcov 1276, bcovi 516, region 73, body 18, glyph 63, font 8,
  other 87.
- 28 hooks.
- 8985, 4253 and 13986 render page for page as b304 at 30 dpi.

The mark under Word layout, with the white-space exception, is inert on docx4j's corpora. Enterprise CR-001 §6.6
item 8 records it (875c24f), and the fidelity register records b305 and b306 (129fa78, bc2da5b).

Jason decided it ships in `2.12-docx4j.1`, with a tag for the move alone (2026-11-02: "it is enough to make a git tag
for move alone with no change in output"). The tag is `CR-031-phase1`, annotated, at 3d9a41143: `2.12-docx4j.1`
before phase 3, its code 27ed437d3 (r51). `CR-031-phase3` was merged by fast-forward at c4ba99965. The head's code
is r53's.

### 6.1 r52, as first built (db7f31150): gated FAIL, superseded by §6.2

Turning the mark on alone would not have reached CR-023's own case under Word mode. This was measured on the first
two sequences of `float_overflow-below.xml`, laid out in Word mode on `2.12-docx4j.1` at e5f0a7d12. The float is
300pt wide, leaving 151pt beside it in a 451pt column.

- The 228pt word fits the column but not the room beside the float. Word mode's emergency break split it beside the
  float, as `Wwwwwwwwwwwwwwwww` and `wwwwwwwww`. The line therefore never overflowed, and the mark had nothing to
  mark. FOP's own layout sets that word's line at the float's foot, full width.
- The emergency break measured the word against the line manager's `ipd`. That is the flow's width less the float's
  intrusion (`PageSequenceLayoutManager.getCurrentColumnWidth`). Yet its javadoc calls the measure "the widest line
  the paragraph can have: a word narrower than that can always be moved to a line where it fits".

So phase 3 is two changes:

1. **The mark is set under Word mode.** The seam `marksOverflowBesideFloat` and its Word override are removed, and
   `updateData2` is CR-023's again.
2. **`emergencyBreaks` takes `widestLine()`.** Where the block's measure is the flow's, that is the column's width:
   `ipd` plus the start and end intrusion. Otherwise it is `ipd`.
   - `widthFromFlow` decides which, once per block. It walks up to the flow through blocks, lists and wrappers.
   - A table cell, block-container, float, footnote or inline container ends the walk with `ipd`. The float does not
     narrow those; their measure is their own.

With both, Word mode lays these sequences as FOP's own layout does:

- the first line beside the float, then the word's line and the rest at the float's foot, 451pt wide, after the
  18pt clearance;
- a word wider than the column stays beside the float, as in FOP's layout;
- a word in a table cell beside the float is still broken against the cell's 151pt.

`OverflowBesideFloatTestCase` has these three cases. Without the change its first case fails and the other two pass.

**Events.** A moved line is reported once as overflowing (`lineOverflows`). The report comes from the breaking pass
beside the float, before the page breaking moves the line, and FOP's own layout under CR-023 does the same. On these
sequences, Word mode reported one event before (the 528pt word) and two after (that word, plus the 77pt overflow of
the moved line). Not reporting a marked line would also hide a real overflow wherever the page breaking cannot take
the edge, so the report stays as CR-023 has it.

The pass criteria r52 went to the gate with allowed `lineOverflows` to rise by the lines moved, and nothing else.

Noted, unchanged and not traced: a word wider than the column, beside a float, is not emergency-split in Word mode,
before this change or after it. The first pass splits it at the column's width. On the float's re-layout that split
is not taken, and the line overflows by 528pt. docx4j's copy did the same, since phase 2 was a pure move (b303, b304).

## 7. Upstream

Nothing is sent. Word mode is docx4j's. Greedy breaking as an option alone could be offered to Apache later, with
FOP-2860 as the precedent for such an option (CR-071 §7); it is not drafted.

## 8. Increments

1. The settings, switch, capability, element mapping and `LeadingGlue`. Word mode can be reached but does nothing
   yet; FOP's suite and a settings test are green. Committed 2b8758511.
2. The line manager, the block manager's branch and the break opportunities: this CR's bulk.
3. The list item, flow and page-number citation managers.
4. The 19 tests, checkstyle, spotbugs, the full suite and the drop.

Increments 2 to 4 were built together on 2026-11-02 and committed as one. The capability is advertised from that
commit, which is when Word mode is whole.

## 9. A paragraph laid twice at a restart after a Word line's leading (2026-11-02)

**Reported** by docx4j-12, at Jason's wish that it be fixed. real3 `14_es-DO_tbl_4253`, page 1:

- The client line ("Cliente: Universidad Iberoamericana – Cap Cana.") is laid at Word's place, above a
  page-anchored table that has `fox:wrap="square"`.
- The same paragraph is then laid again, broken down the 23.75pt gap beside the table, with its tail below the
  table.
- The second copy starts 10pt (the paragraph's space-after) below the first.
- docx4j's copy did the same (b302, b304), so the defect came across in the move. FOP's own layout lays the
  paragraph once, and so does Word mode without the `fox:wrap` attributes.

**Traced** on the gate's classpath (`~/fidelity-cr030/cp-b306.txt`, r53) with an overlay of `PageBreaker` that
prints each edge restart.

- In Word mode a paragraph's last line carries its leading below it as a `LeadingGlue`, 2.93pt here. The flow
  manager moves that glue behind the break possibility after the paragraph, so a page break taken there discards
  it (§3.3, `LeadingGlue`).
- The glue's position chain is the paragraph's own block: `NonLeafPosition[flow] > NonLeafPosition[block]`.
- The obstacle's edge fell at that break, a flow-level penalty with index -1. `PageBreaker.handleFloatLayout`
  chooses the block to read again from the next element after the break with a position. That loop is Apache's
  float restart, which CR-024 also uses for obstacles.
- That element was the glue. The restart resolved to the client paragraph's block, which was read again from its
  start, beside the obstacle.
- In FOP's own layout the next such element is the following block's line, so the restart is right.
- The page breaking's own checks (`edgeRestartable`, `positionOfNextContentBox`) look at the next box with content.
  They saw the right block, so the edge was allowed.

FOP's restart for a change of width (`RestartAtLM`) has the same loop. A probe showed it reached: Word-mode
paragraphs on a wide first page, followed by narrower pages. The last paragraph of page 1 was laid again at the top
of page 2, and the run gained a page. FOP's own layout was right.

**The fix.** Both loops skip a `LeadingGlue`, which exists only in Word mode, so FOP's own layout is unchanged.
`PageBreaker` and `RestartAtLM` carry the change notice; `RestartAtLM` is a modified Apache file from here on.

- 4253, rendered with the fix as an overlay on the gate's classpath: one page, its text identical to docx4j-12's
  render without `fox:wrap` (`nowrap.pdf`), the client line once at Word's place.
- The page's three empty paragraphs lie at the same y in the gap beside the table, where `nowrap.pdf` has them at
  the left margin. They draw nothing.
- `LeadingGlueRestartTestCase` has two cases, an obstacle's edge after a paragraph and a page of another width.
  Both fail without the fix and pass with it.

**Pass criteria** for the gate, against b306 on r53:

- 4253 moves toward Word: one copy, nothing drawn in the gap;
- other movers only where a Word-mode paragraph was laid twice, each losing its duplicate: at an obstacle's edge, at
  a change of page width, or at a side float's edge, which takes the same `handleFloatLayout` path (not probed
  here);
- no text lost;
- events equal outside the documents that move;
- 28 hooks.

**Gate b307, r54 (531bf4f9c, core sha256 b23356e8...) against b306: PASS** (docx4j-12, 2026-11-02).

- 28 hooks, the probes identical, and events equal (linebig 3243, line 17776, clip 1552, bcov 1276, bcovi 516,
  region 73, body 18, glyph 63, font 8, other 87).
- Three documents changed, each toward Word, with no text lost:
  - real3 `14_es-DO_tbl_4253`: the doubled client line is gone. Candidate lines went from 22 to 16, all 16
    matched, and its words are exactly Word's (54 to 45; Word 45).
  - real `16_en-US_tbl_13544`, an obstacle's edge: a doubled "Name" line is gone. +1 line, 4 pages to Word's 3,
    parity 0.9815 to 0.9907.
  - real `15_en-IN_num_10598`, unequal columns, so a change of width at each column boundary. Its duplicate was a
    blank paragraph, so its words are unchanged at Word's 2,129. +80 lines, 9 pages to Word's 8, parity 0.7527 to
    0.9654. This is the blank "kept at the restart" of CR-026 §8, and Enterprise §6.6 item 49 retracts it as a
    pitch question.

Merged to `2.12-docx4j.1` by fast-forward at 531bf4f9c. **b307 on r54 is the fork's baseline for later gates.**
Enterprise §6.6 items 49 and 11 record the fix (a46b319), the fidelity register records it (a695703), and docx4j
17.3.6's CHANGELOG notes it, since docx4j 17.3.5 has the defect through its own copy.


## 10. Word never breaks a word in the room beside a float (2026-11-02)

**Word's rule.** docx4j-12's probe `float-gap-words` (docx4j 83edc5fac; Jason's golden, Word 365, recorded in
docx4j's word-layout-rules §6.8 at 19a1ec1dc) answers §6.2's open question. Word never breaks a word in the room
beside a float or a page-anchored object. The first word that does not fit takes the rest of its paragraph below the
object.

- Case 3: a page-anchored table leaves 24pt, 4253's shape. A full-width line, then "in" in the room, then
  "reprehenderit" and the rest below.
- Case 5: a square-wrapped picture leaves 60pt. The 150pt word starts the first line below the picture.
- Case 4: a word wider than the column goes below the float and is broken there at the column's width.

On r54, Word mode broke case 3's paragraph down the 24pt gap and case 5's word beside the picture. So r52's
emergency-break change (§6.1) was Word's rule after all.

Jason, 2026-11-02: build it now, and ship it in `2.12-docx4j.1`. docx4j-12 asked that the discarded passes'
overflow reports be kept out of the event counts.

**As built**, on branch `CR-031-gap-words`, cut from `2.12-docx4j.1` at cef1f6b5e:

1. **The emergency break measures a word against the column.** This is r52's `widestLine()` and `widthFromFlow()`
   (§6.1). Where the block's measure is the flow's, the width is the column's; inside a table cell, block-container,
   float, footnote or inline container it is the block's own measure. A word that fits the column overflows the
   room beside the float, is marked, and goes with the rest of its paragraph to the float's foot. Word mode only.
2. **A marked line's overflow is reported only when it is laid out as it is.**
   - `updateData2` holds the report in the `LineBreakPosition` (`overflowToReport`, `overflowLine`), and
     `addInlineArea` reports it once.
   - A line the page breaking moves is laid out again at full width, so its marked position never reaches
     `addInlineArea`. A line laid in a pass the page breaking discards never does either.
   - A marked line kept where it is, because the edge could not be taken there, is still reported.
   - Unmarked overflows are reported as before. This is the base line manager's code, so FOP's own layout reports
     a moved line no more either.
3. **Beside an obstacle, a marked line ends the band at a boundary that the orphans keep.**
   - After an obstacle's start edge, the paragraph is read again. Its first lines beside the band keep its orphans
     (CR-024 §11.19), so the break between "in" and "reprehenderit" was an infinite penalty, and the edge waited for
     the next ordinary break after the third line.
   - `PageBreakingAlgorithm.handlePenaltyAt` already took such a boundary as an obstacle's edge at its foot or at
     the next band's top. It now does so before a marked line too.
   - Side floats need no such rule: FOP relaxes widows and orphans to 1 beside them.
   - This applies to FOP's own layout too.

**Measured here** with the gate's classpath (`~/fidelity-cr030/cp-b307.txt`, r54) and the changed classes as an
overlay. The probe was rendered by the gate's own `Fidelity.renderOne`; the corpus documents by RenderFo from
b307's FOs.

- **`float-gap-words`:**
  - Case 3 is Word's: "in" alone at y 215.4 (Word 215.3), then "reprehenderit in voluptate..." full width at 428.6
    (Word 428.7).
  - Case 5 is Word's: the long word at y 260.3 (Word 260.2), x 72, full width.
  - Pages 1, 2 and 4 are unchanged. Cases 1, 2 and 4 keep their tables in the flow, which is docx4j's
    `FLOAT_MAX_SHARE`, docx4j-12's next step.
- **Corpus documents:** 13986, 8985, 4253, 13544 and 10598 have text and page counts identical to r54's.
- **Overflow warnings fall:** 13986 from 173 to 1, and 8985 from 12,645 to 276. 4253, 13544 and 10598 are
  unchanged. The gone reports are marked lines in passes beside a float that the page breaking discarded; 8985's are
  lines at 0pt beside the two floats that fill its column.
- **`OverflowBesideFloatTestCase` now has six cases.** Three are new, and each fails without the change:
  - a 228pt word beside the float goes below it, whole;
  - a moved line reports no overflow;
  - beside an obstacle, "in" stays in the 24pt room and "reprehenderit" goes below whatever the orphans.

**Open, recorded and not traced:**

- A word wider than the column, beside a float, stays there, overflowing. The first pass splits it into one mapping
  per character, which the float's re-layout cannot split again. Word sets it below and breaks it at the column's
  width (case 4). The test pins today's behaviour so that a fix changes it deliberately.
- At an obstacle's start edge inside a paragraph that has a preserved linefeed (`linefeed-treatment="preserve"`),
  the line before the linefeed is laid twice, in both modes. This was found in a probe, not on the corpus: docx4j
  writes a soft return as a block, not a linefeed.

**Pass criteria** for the gate, against b307 on r54 (docx4j-12's):

- `float-gap-words` cases 3 and 5 are at Word's lines;
- corpus movers only where a word was broken in the room beside a float or an obstacle, each moving that word and the
  rest of its paragraph below;
- no text lost;
- 28 hooks;
- events equal except `line` and `linebig`, which fall by the marked lines reported in discarded passes (8985 and
  13986 measured above).
