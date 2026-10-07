# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`docx4j-fo-renderer`: an upstream-tracking fork of Apache FOP 2.11, maintained for
docx4j's docx-to-PDF path. The Java packages stay `org.apache.fop`; the Maven
coordinates are `org.docx4j:docx4j-fo-renderer[-core|-events|-util]`, versions
`2.11-docx4j.N`. `README.md` is the authoritative description: the coordinates, the
table of changes from Apache FOP 2.11 with their upstream status, and the hooks with
their `Docx4jFop` capability names. Keep that README current; it is what docx4j's
`FopCapabilities` probe and a consumer's support question rely on.

The design is docx4j's CR-020, `../docx4j/docs/developer/change-requests/CR-020-fo-renderer-fork.md`:
§2 the decisions (fork, tracking upstream; a distinct identity with no Apache mark in
the product name; packages kept; graceful degradation), §3 the design, §4 the phases,
§8 progress, §9 the classification of the Metanorma and Chunlin commits. That CR stays
in docx4j; this repository holds the code, and any fork-side CR of its own under
`docs/developer/change-requests/` (`CR-001` the gsub-features hook, `CR-002` the
ligature ToUnicode defect).

## Read §6.6 before proposing any work

`../Plutext-Enterprise-Java-11/docs/developer/change-requests/CR-001-word-layout-fidelity.md`
is the measured record of what FOP cannot do and what docx4j does about it. §6.6 is
the numbered running list of FOP limitations, each with its docx4j-side workaround.
The README's hooks table and CR-020 both cite its item numbers.

**Read §6.6 before proposing, planning, ordering or estimating any work here, and
cite the item number in what you propose.** Read the section, not the README's
second-hand summary of it. Three things went wrong on 2026-09-25 for want of this:
the ligature ToUnicode defect was reported to Jason as a new finding when it was
already item 30; P2-8 was recommended as the only queue item visible in rendered
output when item 30 records the workaround that had already made it invisible; and
item 15 shows the font twin P2-8 would replace is load-bearing for kerning too, so
the change is wider than proposed.

Keep it current, and only with what is confirmed:

- a finding measured here and agreed with the docx4j session - the mechanism, not
  just the symptom, since the symptom is usually already there;
- something implemented here: the item's status, the fork commit, the fork CR key;
- a retraction. A claim that turned out wrong is recorded as wrong rather than
  quietly deleted. The existing entries do this, and it is what makes the list
  worth trusting.

Nothing goes in on inference. If it was not measured here or agreed with the docx4j
session, it is not an item yet; where the evidence is partial, say so in the entry.

That file is Jason's and other sessions edit it. Check `git status` on it first.
Clean: edit it and commit that edit alone, naming the item number. Dirty: send the
exact text to the session holding it instead. A dirty file says a file is contended
and nothing about who holds it - the `tasks.yaml` episode of 2026-09-25, where this
session routed an edit to the wrong peer on that inference, is why.

## Branches and remotes

- **`2.11-docx4j.6` is the fork's branch. Work on it; release from it.** It carries the
  `revision` property that sets the version (`2.11-docx4j.6-SNAPSHOT`), so a release changes one
  line there; see `docs/developer/releasing.md`. Do not count it against `trunk`, which tracks
  Apache's `main` and so diverges from the fork's base.
- **One branch per release line, named for the version it will ship** (Jason, 2026-10-03, replacing
  his 2026-09-25 rule of one long-lived branch whatever the version). When `2.11-docx4j.N` ships,
  cut `2.11-docx4j.N+1` from it, move the `revision` there, and add the new name to
  `.github/workflows/maven.yml`. The branch is not the tag: the release is `v2.11-docx4j.N`.
- `2.11-docx4j.5` is the previous branch: the release was tagged `v2.11-docx4j.5` on it at 724e92d1c
  (2026-10-07). Leave it alone. `.6` was cut from that commit. The `2.11` line is kept on purpose:
  CR-009 §6 gives the reasons.
- `2.11-docx4j.4` is the branch before that, tagged `v2.11-docx4j.4` at 75f9b0262 (2026-10-04); `.5` was cut
  from that commit. Leave it alone.
- `2.11-docx4j.3` never shipped (Jason, 2026-10-03). It is Apache 2.11 plus `fop/CR-008`; `.4` was cut
  from it to take Apache `main` (`fop/CR-009`) and shipped both. Leave `.3` alone.
- `2.11-docx4j.2` is an earlier branch; the `2.11-docx4j.2` release was tagged `v2.11-docx4j.2` on
  it at ae4d4bc59. On origin it runs three commits past the release, ending at f7a1bdcd9, the first
  of which already moved the `revision` to `.3-SNAPSHOT`; those three are on `2.11-docx4j.3` as well.
  Leave it alone. `docx4j-2.11` is the branch before that, and what identifies the `2.11-docx4j.1`
  release: that shipped from `2f5030172` on it, tagged `v2.11-docx4j.1`.
- `.github/workflows/maven.yml` names the branches twice and must gain each new one. A stale list there fails silently: no runs at all looks exactly like no
  failures. It was missed in the 2026-09-26 rename for that reason. To read the runs, name the
  repository: `gh run list -R plutext/xmlgraphics-fop --branch 2.11-docx4j.6`; a bare `gh run list`
  here resolves to Apache's repository through the `upstream` remote and shows Apache's runs.
- `trunk` tracks Apache's `main`. Remotes: `origin` = plutext/xmlgraphics-fop,
  `upstream` = apache/xmlgraphics-fop, `metanorma` and `chunlin` = the two forks whose
  commits CR-020 §9 classified.
- The upstream-facing branches are one fix each, cut against Apache `main`, named for their JIRA:
  `FOP-3328` and `FOP-packed-glyph-bboxes` (FOP-3330), whose pull requests #106 and #107 are open, and,
  cut 2026-10-03, `FOP-2918` and `FOP-3339` to `FOP-3347`. `FOP-3345` is stacked on `FOP-3346` and
  `FOP-3340`. Cut 2026-10-07 on `main` at 5be8c69b6: `FOP-3354`, `FOP-3355` stacked on it, and `FOP-3356`. The older names `FOP-cjk-radical-tounicode`, `FOP-empty-glyph-not-composite` and
  `FOP-surrogate-pair-word-split` were deleted on 2026-10-03 (local only, never pushed; their commits
  are in `FOP-3340`, `FOP-3339` and `FOP-2918`). The branches are worked on in a
  worktree at `../fop-upstream-wt`; the pull request texts are in `../fop-upstream-prs/`. On Apache
  `main` the import order differs from 2.11's (static imports directly under the others, no blank
  line) and no file carries the fork's change notice.
- An upstream-bound fix is done twice: on its own `FOP-####` branch against `trunk`
  for the PR, and on `2.11-docx4j.6` for the fork. A docx4j-only hook goes on
  `2.11-docx4j.6` only.
- Merge `upstream/main` into `2.11-docx4j.6` at least at every Apache release and
  whenever a fix sent from here lands upstream.

## Build and test commands

Apache FOP's own Maven build, Java 8 and later (Java 11 or 21 here):

```bash
mvn -B package checkstyle:check spotbugs:check     # what CI runs on every push (.github/workflows/maven.yml)
mvn install -DskipTests                            # the snapshot docx4j consumes (2.11-docx4j.6-SNAPSHOT)
mvn -pl fop-core -am test -Dtest=SomeTestCase       # one test class; -am is needed, see below
mvn -pl fop-core test -Dtest=LayoutEngineTestSuite  # FOP's layout tests (fop/test/layoutengine/standard-testcases) - slow;
                                                    # one file: -Dfop.layoutengine.single=name.xml
```

`-am` is not optional on a single-module command: the version comes from the `revision`
property and no artifact at that version is installed, so `-pl fop-core` alone cannot
resolve its siblings and fails before compiling.

`fop-sandbox`, `fop-servlet` and the transcoders are in the tree but not built.
Every modified file carries a change notice under its licence header (the form in the
existing ones); checkstyle enforces FOP's style, so run it before offering a commit.
`LineBreakUtils` is generated from Unicode data by
`fop-core/src/main/codegen/unicode/java/.../GenerateLineBreakUtils.java`; the `pair-table`
hook must be re-added after any regeneration.

## The split with the docx4j session

This repository has its own Claude session; docx4j has another (`ListAgents` shows it
as `docx4j-<n>`). Messages go between them with `SendMessage`.

| this session owns | the docx4j session keeps |
|---|---|
| the cherry-picks (CR-020 §8, P2-1 to P2-8), one branch each, FOP's own tests green | which items go to the fork, and in what order (Jason decides; the CR records it) |
| the phase 3 structural items in the layout engine | the consumer side: `docx4j-export-fo`'s `FopCapabilities`, the gating of each rule on a hook, the `-Pfo-renderer-fork` profile |
| upstream tracking, JIRA text drafted for Jason to file, PRs titled `FOP-####: ...` | the fidelity gate: `docx4j-export-fo` tests, then the corpus scored against Word (Enterprise CR-001, run from `../Plutext-Enterprise-Java-11`) |
| the first release `2.11-docx4j.1` to Maven Central (signed, sources, javadoc; the `org.docx4j` credentials) | the docx4j-side workaround recorded against each Enterprise CR-001 §6.6 item, and CR-020 itself. §6.6 itself is shared: this session reads it before proposing work and records what it confirms or retracts (see "Read §6.6 before proposing any work") |

The gate lives in docx4j because only the Word-scored corpus says whether a FOP change
helped. So one round trip per item:

1. Cherry-pick or write the change on its branch; change notice; README table row (and
   the hooks table if it adds a capability, with the `Docx4jFop` constant); FOP's tests
   and checkstyle green.
2. Before `mvn install`, ask the docx4j session whether a gate is running: a snapshot
   install replaces the jars under `~/.m2` that its running gate is reading (this has
   bitten before). Install only on its word, or when it is idle.
3. Message it: branch, commit, what changed in one paragraph, the §6.6 item or JIRA it
   serves, and what a pass would look like. It runs the gate and replies pass or fail with
   the measurement; a fail comes back with the scoreboard reading, not a guess.
4. On a pass, merge to `2.11-docx4j.6`; it records the item in CR-020 §8. Where the change
   closes or narrows a §6.6 item, update that item here too, with the fork commit and the
   mechanism, and tell the Enterprise session. On a fail, the change stays on its branch,
   and if the fail taught something about FOP, that goes in §6.6 as well.

Never run a Maven build inside `../docx4j` or `../Plutext-Enterprise-Java-11` from here:
each has one session, and a concurrent build races their `target/classes`.

## Hazards

- **The fork and Apache FOP must never both be on one classpath**: same packages, so
  classpath order decides which wins. docx4j warns when it sees both; do not create the
  situation in a test here either.
- **No Apache mark in the product name**, and the README's "modified distribution"
  notice stays: CR-020 §2.2. Apache FOP is a trademark of the Apache Software Foundation.
- A hook is a public accessor or one setter that changes nothing FOP does on its own
  (README, "Hooks"). If a change alters FOP's behaviour it is a fix, and goes upstream
  first unless it is docx4j-specific.
- `docx4j-export-fo` subclasses 2.11 internals; a change to a layout manager's shape
  breaks docx4j's layout managers. Tell the docx4j session before changing a signature
  its managers use (the `inline-access` members are the list).

## Registry

`../docx4j-portfolio/tasks.yaml` indexes the change requests across the docx4j
repositories; this repository's key is `fop`. CR-020 and its phases are registered as
`docx4j/CR-020.*` because the CR lives in docx4j; a fork-side CR would be `fop/CR-001`
with its doc here. When a phase's status changes, tell the docx4j session (it edits the
docx4j CR's Status line), or, for a fork-side CR, edit the entry and run
`python3 ../docx4j-portfolio/scripts/tasks.py check` then `accept`.

## AI attribution in commits

- Stamp your own session's model (`Co-Authored-By: Claude <model>`, per the harness
  default). Never copy the model name from trailers in git history.
- If another model materially contributed in this session, add a second
  `Co-Authored-By` line for it.
- New files carry the licence header Apache's files carry (the change-notice form for a
  modified Apache file; this is an Apache-2.0 derivative, not a Plutext original).

## Start here

Last updated 2026-10-07, after the `2.11-docx4j.5` release. Read Enterprise CR-001 §6.6 before proposing anything,
as the section above says.

**State.** `2.11-docx4j.5` is on Maven Central (2026-10-07, released by Jason from the pushed branch), tagged
`v2.11-docx4j.5` at `724e92d1c`, the version commit; `docs/release-notes/2.11-docx4j.5.md` says what it carries and
`docs/developer/releasing.md` what the release proved (five artifacts verified from Central, core manifest
`2.11-docx4j.5`). docx4j 17.3.1 (Maven Central, 2026-10-07, release commit 5cf47d752 on `VERSION_17_3_1`) depends on `.5`; 17.3.0
depended on `2.11-docx4j.2`. Work is on branch
`2.11-docx4j.6` (snapshot `2.11-docx4j.6-SNAPSHOT`), cut from the release commit. What `.5` carries, all gated PASS
by the docx4j session, each with its CR under `docs/developer/change-requests/`:
- `fop/CR-012` (page numbered 0, hook `page-number-zero`) and `fop/CR-013` (`continuation-display-align`);
- `fop/CR-014` (`to-unicode-map`), `fop/CR-015` (item 40, `ascender-descender`), `fop/CR-016` (item 42, a
  decomposition's ToUnicode, correcting `fop/CR-002`'s regression);
- `fop/CR-017` (`page-master-by-content`) and `fop/CR-017.2` (`page-number-restart`), for docx4j CR-031 phases 2
  and 3; `fop/CR-018` (`measured-region-extents`, phase 5);
- the review of `.5` (CR-017 §13, CR-018 §10) and the hooks' warnings as FOP events of `BlockLevelEventProducer`;
- `fop/CR-020` (side floats, items 44 and 45, `side-float-edges`), with the `FLOAT_RESTART` bit fix (CR-020 §9);
- `fop/CR-021` (simulated italic and bold per face, item 46, `simulate-style-per-face`).

Nineteen capabilities. `fop/CR-019` (ActualText per cluster) is designed and waits on Jason. `2.11-docx4j.4`
(2026-10-04, at `75f9b0262`), `2.11-docx4j.2` (2026-10-02) and `2.11-docx4j.1` (2026-09-25, tagged at `2f5030172`)
are the earlier releases; `.3` never shipped. `docs/developer/releasing.md` is the runbook and records what each
release proved. Nothing is unreleased on `2.11-docx4j.6` beyond the branch cut.

**Upstream.** Every fix has a JIRA and an open pull request on apache/xmlgraphics-fop, cut against Apache `main` on
a branch named for its number, each measured on `main` and passing the full suite and checkstyle (2026-10-03): #108
FOP-3339 (empty glyph), #109 FOP-3340 (radical), #110 FOP-3341 (lookup fallback), #111 FOP-3342 (shared default
langsys), #112 FOP-3343 (kerning flag), #113 FOP-3344 (letter spacing on the DP path), #114 FOP-3346 (selector
drift), #115 FOP-2918 (surrogate pair), #116 FOP-3345 (ToUnicode for substituted glyphs, stacked on #114 and #109),
#117 FOP-3347 (format characters); and from before, #106 FOP-3328 and #107 FOP-3330. Since then: #118 FOP-2349
(`fop/CR-010`, stacked on #113), #119 FOP-1896 (`fop/CR-015`), #120 FOP-3348 and #121 FOP-3349 (`fop/CR-011`, items
36 and 37; both merged to Apache `main` 2026-10-05, the first of ours to land), #122 FOP-3350 (`fop/CR-012`'s page
label; the page-number-zero option itself is not sent, Apache keeping to XSL), and, 2026-10-07, #123 FOP-3354 and
#124 FOP-3355 (`fop/CR-020`, items 45 and 44; #124 stacked on #123), and #125 FOP-3356 (`fop/CR-021`, item 46).
Review began: Joao Goncalves (committer) merged #120 and #121 as the two with layout tests, closed FOP-3339 as a duplicate of
his FOP-3352 (his fix guards the last glyph only, ours any empty glyph; the crash case is his; #108 closed 2026-10-08 with a note
offering the general form as a follow-up), and asked on FOP-3340, FOP-3346, FOP-3347 and FOP-3350 for an FO, on FOP-3340 "for all
the other tickets". Answered 2026-10-08 on every ticket that can have one (comments 18124627 to 18124630 and 18124651 to 18124660;
the FOs, configs and a README under `docs/upstream/repro/`), each verified on `main` 5be8c69b6 and on its fix branch: test-tree fonts
for FOP-3343, FOP-3344, FOP-2349, FOP-1896; Carlito or DejaVu Sans for FOP-3341, FOP-3342, FOP-3345, FOP-3356 (DejaVuLGCSerif's
DFLT script carries liga and kern, so it cannot show the first two); the variable Noto for FOP-3328. FOP-3350 has no FO in stock
FOP, FOP-3330 none to give. Two things learnt: #113 alone makes a letter-spaced word overprint the next (FOP-2349's half), so #113
and #118 belong together; and FOP-3342's 2.11 measurement for language="ro" does not hold on `main`, untraced.
FOP-3353 (filed 2026-10-07, no pull request) reports a regression on `main` from FOP-3331: a float whose own child
is a block-container is put in the flow. This session posts to ASF JIRA directly since 2026-10-05, on Jason's OK per
item (see the memory note). The drafts under `docs/upstream/` are stamped with both numbers. If a reviewer asks for
changes, work in the worktree at `../fop-upstream-wt`; when #114 and #109 merge, rebase `FOP-3345` to its one
commit; when #123 merges, rebase `FOP-3355` likewise. `trunk` is at `upstream/main` (5be8c69b6, 2026-10-07).

**Open, in order.**
1. *After the `.5` release*: Jason pushes the tag `v2.11-docx4j.5` and the branch `2.11-docx4j.6` by name (both
   local until he does; never `--tags`). The docx4j side is done (2026-10-07, docx4j 2de765a77, unpushed): Central's
   jars sha1-verified, gate b179 on them against r15 0 movers on all four corpora and the 260 probes, the reactor
   green, `docx4j-export-fo` on `2.11-docx4j.5`, CR-020 §8, CR-031 (DONE), `tasks.yaml` (portfolio dd065e3) and
   Enterprise §6.6 items 38 to 42 and 44 to 46 all record the release. Its harness's fork profile is
   `2.11-docx4j.6-SNAPSHOT`. One thing it learnt: on `.5` the FO carries placeholder region extents plus
   `fox:extent="measured"`, so two of its tests that read the pre-pass extents now force the pre-pass.
2. *Honour `clear` after a side float*, `fop/CR-022`: IN PROGRESS 2026-10-08 on branch `CR-022-clear-after-side-float`
   (Jason's yes to start, 2026-10-08). `clear` on `fo:block`, `fo:block-container`, `fo:table`, `fo:list-block` ends a side
   float on its side at the break before the FO, and the FO starts at the float's foot (the clearance through the
   layout context's space-before, as display-align does). Capability `clear-after-side-float`, the twentieth. Committed
   0fc1fd40e on the branch: measured on the command line (CR-022 §4), layout test `float_clear.xml`, full suite 3864/0,
   checkstyle clean. The jar is at `~/fop-renderers/r16-CR-022-0fc1fd40e/` (sha256 03f2949a...); the docx4j session gates
   it (10855's rubric table at Word's 155.35 with the band restored; 0 movers with it withheld), queued behind docx4j
   CR-032 phase 1's gates (2026-10-08); the band for 10855's shape is the docx4j session's own step first. On PASS: merge to
   `2.11-docx4j.6` by fast-forward, release notes, §6.6 item 45's text updated (the Enterprise file is Jason's; check
   `git status` first), the registry text sent. Found on the way (2026-10-08, the docx4j session's gate b182 on 4083, CR-022
   §3.5) and fixed on the branch: the edge search walked past an edge on a page within its adjustment range, never offered
   a kept break as the edge (INFINITE is 1000), and threw NullPointerException in handleFloat when a deferred edge met a
   forced break inside a table; a table the page cannot hold now starts at the float's foot. Committed bf3a5d41c (full suite
   3864/0 again); the jar for the gate is r17 at `~/fop-renderers/r17-CR-022-bf3a5d41c/` (sha256 b81a343b...), r16
   superseded; §6.6 item 47 written and committed in the Enterprise file (ba52a7b). 4083's reproducer FO is the docx4j
   session's, under `~/fidelity-cr030/repro/`. The three recorded float NPE reproducers (items 20, 36, 37) render on
   this build with `-ea`, told to the docx4j session for its br-anchor decline.
3. *`fop/CR-023`, after CR-022: the offset float and both-sides wrap for docx4j CR-032 (floating tables)*. Jason confirmed
   D2 here on 2026-10-08. Phase 0 (nine Word probes, the docx4j session, 2026-10-08, CR-032 §3 and §4 at cae13d3d1)
   settled it: `float-offset` stands (`fox:float-offset` on `fo:float`: the intrusion begins N pt below the top of the
   anchor block, space-before included; the lines that fit in the gap are full width above the float), `float-band` is
   WITHDRAWN (Word does not lay empty paragraphs behind a full-width table; FOP's zero-ipd lines beside a column-wide
   float already match Word for the narrower case), and the second half is both-sides wrap, §6.6 item 10, for this
   session to size: Word runs text down both sides of a centred float, with no minimum width. Serves §6.6 item 8. The
   clearest corpus case for float-offset (docx4j session, phase 1, 2026-10-08): 4083's table, tblpY 51pt at the page's left
   edge; floated from its first line, FOP cuts the question above it into a column beside the table (-2 lines, a garbled
   page) where Word runs those lines full width and puts the table below them. docx4j guards that shape (an offset over a
   line and a half stays in the flow) until the hook exists.
4. *`fop/CR-019`, ActualText per cluster*: §9 answered by Jason (tier A, all scripts, on in the fork and offered
   off upstream; PDF/UA matters, so veraPDF is installed and a tagged sample tested first). Not to start until he
   says.
5. *Merging Apache `main`* into the fork, which this file asks for now that FOP-3348 and FOP-3349 have landed:
   `main` at 5be8c69b6 also carries FOP-3331's float regression (FOP-3353: a float whose own child is a
   block-container goes in the flow). Fix or revert it in the fork with the merge, and gate the merge. Committed
   docx4j wraps float content in an `fo:block`, which is unaffected. Also check Apache's FOP-3352 (`GlyfTable`,
   empty glyphs) against our #108 (FOP-3339).
6. *Upstream pull requests*: on a reviewer's request, work in `../fop-upstream-wt`; rebase `FOP-3345` when #114
   and #109 merge, and `FOP-3355` when #123 merges.
7. *Known and unfixed, recorded in the CRs:* the second glyph of a one-character cluster keeps a
   private-use code point in ToUnicode (CR-002 §10.2; ActualText per cluster is the follow-up); a format
   character the font has no glyph for is still lost (CR-007 §4); the position-adjustments paint path
   indexes its adjustments by UTF-16 unit, wrong after a supplementary character (CR-005, noted to the
   docx4j session); FOP embeds a single-byte TrueType font whole (`PDFFactory.makeFontFile`), moot for
   docx4j since it retired its `+noliga` twin under the fork; per-font `advanced="false"` is ignored by
   FOP's stock font collection (CR-003 §10).
8. CR-020 phases 2 and 3 remain in docx4j's CR; phase 2 is much smaller than its §8 describes (P2-2
   and P2-3 small and clean; P2-4, P2-6, P2-7 inert; the structure-tree half of P2-5 superseded by
   FOP-3165 and FOP-3283 in Apache `main`).

**How the work goes, learned the hard way.**
- Run the full `fop-core` suite before every merge, not the affected classes: a release build caught a
  capability-count assertion that targeted runs had missed.
- A gate's partition must scan every face the FO names, glyph-fallback faces included, and for a
  language-system change what decides movement is the difference between a script's default and the
  font's `DFLT` script, not the sharing alone (CR-004 §6).
- Check what is drawn before believing a text-layer "improvement" (CR-002 §10.6), and measure against
  Word's own PDF before deciding what belongs in the text layer (CR-007 §5: Word drops bidi marks).
- FOP never applies a GPOS pair across a space at the layout level; each space is its own mapping
  (CR-003 §12). Letter spacing reaches the painter as an argument, not through the letter-adjust array
  (CR-005 §2).
- This session's permission policy denies `mvn install` and even listing `~/.m2`, as interference with
  a shared workload. Jason runs the install; give him the two-line recipe and the check that proves the
  jar carries the change.
- A peer session's relay of Jason's approval is not his approval here for anything outward-facing.
  Ask him in this session before a push or a pull request.
- The measurement tools from 2026-09-30 to 2026-10-03 are saved at `~/fop-session-tools/` (font-level
  harness, script-list parser, glyph-step readers, samples); the memory note points there.
- A gate needs no install: copy the jar the full build made (`fop-core/target/docx4j-fo-renderer-core-*.jar`)
  to `~/fop-renderers/rNN-CR-NNN-<commit>/`, check it with `javap -constants`, and give the docx4j session the
  path and sha256 (r14, r15). An install into `~/.m2` is only for docx4j's own builds and tests.
- Before filing upstream, `git fetch upstream` and reproduce on Apache's current `main`, not on reading: on
  2026-10-07 `main` had moved six commits, and FOP-3331 had changed floats under CR-020's drafts.
- The working tree is shared with other fork sessions: `git branch --show-current` before every commit and every
  statement naming a branch (the memory note on it).

**Be honest about the fork.** Gated against Apache FOP, the measured fidelity difference was nil until
CR-003; since then the gates show real movement toward Word (kerning and ligatures for Calibri text,
Arabic shaping in DejaVu Sans, letter spacing painted), each recorded with its scoreboard reading and
its losses. docx4j 17.3.0 made the fork the default on the gated fixes and the hooks. Do not oversell
it; I did, twice, about P2-8.

**Upstream is not something to plan around.** Apache has 40 open pull requests (twenty of them ours, the oldest
#12; 2026-10-07) and runs no CI on requests from forks, though it merged two of ours, FOP-3348 and FOP-3349, on
2026-10-05. A fix the fork needs, the fork carries.

**The peers.** docx4j has more than one session; `ListAgents` shows them. The one that runs the
fidelity gate holds Enterprise CR-001 and usually `tasks.yaml`; send it text rather than editing either
when the tree is dirty. Its partitions and gate readers are under `/home/jharrop/fidelity-gsub-partition/`,
local only, not redistributable. Tell it before asking Jason for any `mvn install`.
