# CR-031: Word layout in the fork's layout managers (docx4j CR-071 phase 1)

Status: IN PROGRESS from 2026-11-02, on Jason's acceptance of docx4j CR-071 ("CR-071 D1 to D11 as recommended,
except for D9 where we decide on (a)"). docx4j-12 relayed the acceptance; it is recorded in docx4j a72346619. The work
is on branch `CR-031-word-layout`, cut from `2.12-docx4j.1`. Under D9(a) the move lands in `2.12-docx4j.1`, whose
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

- **Placement.** The branches go into the 14 members of the fork's `LineLayoutManager.java`. The new members go
  beside it, in `org.apache.fop.layoutmgr.inline`, in the same package so they need no reflection:
  - `WordLineLayout` holds a block's Word state, read in the constructor, and the line manager's new members;
  - `WordLineBreakingAlgorithm extends LineLayoutManager.LineBreakingAlgorithm` is the greedy breaker. It overrides
    `BreakingAlgorithm`'s methods, constructed with a qualified `llm.super(...)`.
- **The edit to Apache's file** is the branches, plus the visibility that `WordLineLayout` and the breaker need: the
  inner breaker, `Paragraph` and the fields they read become package-private. `LBP`'s accessors become direct field
  reads.
- **The 38 unchanged members and Apache's code in the 14 stay the fork's own**, so Apache's changes to them arrive by
  merge.

### 3.4 The other managers

- **`BlockLayoutManager`:** in Word mode, `createLineManager` makes the line manager with Word's state, and the
  `justify-soft-return` follow-up of `WordBlockLayoutManager.createWordLineManager` goes with it.
- **`FlowLayoutManager`:** in Word mode, `getNextKnuthElements` runs `moveLeadingBehindBreaks` and `boundKeepChains`.
  Their bodies are static helpers in `org.apache.fop.layoutmgr.word`, and their four keep-chain settings join the map.
- **`ListItemLayoutManager`:** the Word overrides become branches where they change FOP's code. Where they add to it,
  they become helpers.
- **`PageNumberCitationLayoutManager`:** a branch in `getAllocationIPD`.
- **`LayoutManagerMapping`:** stays Apache's. The switch is read from each FO's user agent, so no maker override is
  needed. docx4j stops setting one in phase 2.
- **`LeadingGlue`:** moves to `org.apache.fop.layoutmgr.word`.

### 3.5 Break opportunities (D6)

`org.apache.fop.layoutmgr.word.WordBreakOpportunities` holds docx4j's class's rules with a public static entry point,
`breakBefore`. docx4j's `TableWriter` calls it by reflection while it writes the FO, before any factory exists. The
UAX #14 pair-table flip (§6.6 item 29) goes through the fork's existing `LineBreakUtils.setLineBreakPairProperty`
(`pair-table`), with no reflection. It is applied when a factory is built with Word mode on and `breakOpportunities`
true.

The flip is process-wide, as it is today in docx4j. It reaches any FOP layout in the same JVM after the first Word-mode
factory is built. That is a recorded limit, unchanged by the move.

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

- docx4j's 19 manager-level tests move to `fop-core/src/test/java/org/apache/fop/layoutmgr/word/`. Each builds its
  factory with `setWordLayout`, and the 7 that set system properties pass the map instead.
- `FopHooksTest` has no counterpart: `FopHooks` goes.
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

## 6. Phase 3: CR-023 §4.2's mark under Word mode (D11)

In phase 1, `updateData2` does not set `overflowsBesideFloat` when Word mode is on, so phase 2 is a pure move.

Phase 3 turns it on as a separate commit with its own gate. It also re-measures Enterprise CR-001 §6.6 item 8's
"float-overflow-below measured inert on docx4j's tables". That reading went through docx4j's managers, which never set
the mark, so it may not say what the rule does under Word layout.

## 7. Upstream

Nothing is sent. Word mode is docx4j's. Greedy breaking as an option alone could be offered to Apache later, with
FOP-2860 as the precedent for such an option (CR-071 §7); it is not drafted.

## 8. Increments

1. The settings, switch, capability, element mapping and `LeadingGlue`. Word mode can be reached but does nothing
   yet; FOP's suite and a settings test are green.
2. The line manager, the block manager's branch and the break opportunities: this CR's bulk.
3. The list item, flow and page-number citation managers.
4. The 19 tests, checkstyle, spotbugs, the full suite and the drop.
