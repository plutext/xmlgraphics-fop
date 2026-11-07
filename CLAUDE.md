# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`docx4j-fo-renderer`: an upstream-tracking fork of Apache FOP 2.12 (of 2.11 up to `2.11-docx4j.8`), maintained for
docx4j's docx-to-PDF path. The Java packages stay `org.apache.fop`; the Maven
coordinates are `org.docx4j:docx4j-fo-renderer[-core|-events|-util]`, versions
`2.12-docx4j.N` (`2.11-docx4j.N` before; `fop/CR-030`). `README.md` is the authoritative description: the coordinates, the
table of changes from Apache FOP (its heading still says 2.11, which the change notices cite) with their upstream status, and the hooks with
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

- **`2.12-docx4j.2` is the fork's branch. Work on it; release from it.** It carries the
  `revision` property that sets the version (`2.12-docx4j.2-SNAPSHOT`), so a release changes one
  line there; see `docs/developer/releasing.md`. Do not count it against `trunk`, which tracks
  Apache's `main` and so diverges from the fork's base.
- **One branch per release line, named for the version it will ship** (Jason, 2026-10-03, replacing
  his 2026-09-25 rule of one long-lived branch whatever the version). When `<line>-docx4j.N` ships,
  cut `<line>-docx4j.N+1` from it, move the `revision` there, and add the new name to
  `.github/workflows/maven.yml`. The branch is not the tag: the release is `v<line>-docx4j.N`. A new Apache
  release starts a new line at `.1` (`fop/CR-030`: `Docx4jFop.APACHE_FOP_LINE` moves with it, and docx4j's
  `FopCapabilities.BUILT_FOR_LINE` must follow).
- `2.12-docx4j.2` was cut 2026-11-07 from the release commit c69e89ffb.
- `2.12-docx4j.1` is the previous released branch: the release was tagged `v2.12-docx4j.1` on it at c69e89ffb
  (2026-11-07). Leave it alone. It was cut 2026-11-01 from `2.11-docx4j.9` (cc98fa4c0), Apache 2.12 merged into it by `fop/CR-030`
  (263be37e7). `2.11-docx4j.9` (cut from b7f6ad8bf on 2026-10-30, pushed, docs only) never ships, like `.3`. Leave it
  alone.
- `2.11-docx4j.8` is the previous released branch: the release was tagged `v2.11-docx4j.8` on it at b7f6ad8bf
  (2026-10-30). Leave it alone. `.9` was cut from that commit.
- `2.11-docx4j.7` is the branch before that, tagged `v2.11-docx4j.7` at ecaa37c40 (2026-10-16); `.8` was cut
  from that commit. Leave it alone.
- `2.11-docx4j.6` is the branch before that, tagged `v2.11-docx4j.6` at 76a413acf (2026-10-09); `.7` was cut from
  that commit. Leave it alone.
- `2.11-docx4j.5` is earlier still, tagged `v2.11-docx4j.5` at 724e92d1c
  (2026-10-07); `.6` was cut from that commit. Leave it alone. The `2.11` line is kept on purpose:
  CR-009 §6 gives the reasons.
- `2.11-docx4j.4` is earlier still, tagged `v2.11-docx4j.4` at 75f9b0262 (2026-10-04); `.5` was cut
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
  repository: `gh run list -R plutext/xmlgraphics-fop --branch 2.12-docx4j.2`; a bare `gh run list`
  here resolves to Apache's repository through the `upstream` remote and shows Apache's runs.
- `trunk` tracks Apache's `main`. Remotes: `origin` = plutext/xmlgraphics-fop,
  `upstream` = apache/xmlgraphics-fop, `metanorma` and `chunlin` = the two forks whose
  commits CR-020 §9 classified.
- The upstream-facing branches are one fix each, cut against Apache `main`, named for their JIRA:
  `FOP-3328` and `FOP-packed-glyph-bboxes` (FOP-3330), whose pull requests #106 and #107 are open, and,
  cut 2026-10-03, `FOP-2918` and `FOP-3339` to `FOP-3347`. `FOP-3345` is stacked on `FOP-3346` and
  `FOP-3340`. Cut 2026-10-07 on `main` at 5be8c69b6: `FOP-3354`, `FOP-3355` stacked on it, and `FOP-3356`. Cut 2026-10-08:
  `FOP-3353` (pull request #126), and `FOP-edge-walked-past` and `FOP-edge-under-keep` (unfiled, CR-022 §3.5's two
  defects of Apache's own). `trunk` is at `upstream/main` c2d7e0aca (2026-10-08), merged into the fork by `fop/CR-027`. The older names `FOP-cjk-radical-tounicode`, `FOP-empty-glyph-not-composite` and
  `FOP-surrogate-pair-word-split` were deleted on 2026-10-03 (local only, never pushed; their commits
  are in `FOP-3340`, `FOP-3339` and `FOP-2918`). The branches are worked on in a
  worktree at `../fop-upstream-wt`; the pull request texts are in `../fop-upstream-prs/`. On Apache
  `main` the import order differs from 2.11's (static imports directly under the others, no blank
  line) and no file carries the fork's change notice.
- An upstream-bound fix is done twice: on its own `FOP-####` branch against `trunk`
  for the PR, and on `2.12-docx4j.2` for the fork. A docx4j-only hook goes on
  `2.12-docx4j.2` only.
- Merge `upstream/main` into `2.12-docx4j.2` at least at every Apache release and
  whenever a fix sent from here lands upstream.

## Build and test commands

Apache FOP's own Maven build, Java 8 and later (Java 11 or 21 here):

```bash
mvn -B package checkstyle:check spotbugs:check     # what CI runs on every push (.github/workflows/maven.yml)
mvn install -DskipTests                            # the snapshot docx4j consumes (2.12-docx4j.2-SNAPSHOT)
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
4. On a pass, merge to `2.12-docx4j.2`; it records the item in CR-020 §8. Where the change
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
docx4j CR's Status line). Since 2026-10-17 (Jason): do not edit `tasks.yaml`,
`scripts/status-snapshots.yaml` or `docs/dependencies.md` in that repository directly. Send
the change to the docx4j-portfolio session (working directory `~/git/docx4j-portfolio`;
`ListAgents` shows it as `docx4j-portfolio-<suffix>`, the suffix changing): the entry id, the
exact new status, depends_on and notes, and any CR Status line to re-baseline. It applies,
runs `tasks.py check`, `graph` and `accept`, and commits, naming this session. Only if no
docx4j-portfolio row is in `ListAgents` may this session edit directly, committing its own
hunks alone through a temporary index from HEAD, never the shared index.

## AI attribution in commits

- Stamp your own session's model (`Co-Authored-By: Claude <model>`, per the harness
  default). Never copy the model name from trailers in git history.
- If another model materially contributed in this session, add a second
  `Co-Authored-By` line for it.
- New files carry the licence header Apache's files carry (the change-notice form for a
  modified Apache file; this is an Apache-2.0 derivative, not a Plutext original).

## Start here

Last updated 2026-11-07, later. **`2.12-docx4j.1` is RELEASED: on Maven Central since 2026-11-07, Jason's deploy from the pushed branch at the version
commit c69e89ffb, verified from Central (68 files, 17 signatures by C641C2A9AFA87A3B, 34 capabilities; core sha256 979ce48e...; the published classes
byte for byte r70's, the gated jar), tagged `v2.12-docx4j.1` there (made here, pushed by name by Jason). Registry `fop/release-2.12-docx4j.1` done
(portfolio 1e1e629). Work is now on `2.12-docx4j.2` (snapshot `2.12-docx4j.2-SNAPSHOT`), cut from c69e89ffb on Jason's go-ahead and added to CI's branch
list. Nothing is ordered for `.2`. Waiting on Jason: docx4j's read of the Central jars against b372 on r70 and its move of `fo.renderer.version`; the
upstream questions; item 57 and its bordered reading; item 62. The paragraph that follows is the state before it, kept.**

Last updated 2026-11-07. **`2.12-docx4j.1` (CR-030 to CR-038, CR-023.1) is PUSHED and in release: Jason's word "push" (2026-11-05) took origin to
dd756f0d2, CI green on Java 8, 11, 17, 21 and 25 (run 41997950698); then "make the version commit and tag; follow docs/developer/releasing.md up to my
deploy" (2026-11-07). The version commit is the next commit after this one; the deploy is Jason's, and the tag comes after Central serves the artifacts
(`docs/developer/releasing.md`, Tagging). docx4j-67 (2026-11-07) wants nothing changed in `.1`. The note keeps docx4j 9e3100351, which is on origin now
(VERSION_17_3_6 at f960df59e), so "unpushed there" below is stale. **b372 on r70 is the baseline** (`cp-b372.txt`; b370 to b372 0 movers on the same
jars), replacing b369. After the deploy docx4j-67 needs the core, util and events jars on Central, a statement of any code change since 45594ce31 beyond
the version commit, and the core sha256 beside r70's (20232efb...); it gates against b372 on Jason's word, then moves `fo.renderer.version`. Then the
next branch, with the release note closed there. The paragraph that follows is the state before it, kept.**

Last updated 2026-11-05, latest. **`fop/CR-038` (corpus 4083's marks and rows, Enterprise §6.6 items 58 to 61) is DONE: gated PASS (b368 on r70, 4083 +1,
no other corpus mover; b369 on r70, 4083 +3 to 0.9870, 63 of 63 moved lines closer; suites pass, docx4j-67) and MERGED to `2.12-docx4j.1` by fast-forward
from `CR-038-box-and-tab`, code 45594ce31, for `.1`, ordered by Jason through docx4j-67 ("r70 (CR-038) goes into 2.12-docx4j.1; commit; Accept 3229 -2
and 12147 -1; accept the known limit (but document it)"); unreleased, local, the push Jason's. **b369 on r70 is the fork's baseline for later gates**
(`~/fop-renderers/r70-CR-038-45594ce31/`, sha256 20232efb...; `cp-b369.txt`). A float's end edge taken at a class-line keep and before a box's empty
holder (a fix); `box-x-from-column`, `box-top-space`, `tab-past-cell` (capabilities 32 to 34). KNOWN LIMIT, accepted and documented: text after a tab
past a cell's edge is drawn over the next cell (CR-038 §6). Open for Jason: the push and release of `.1` (CR-030 to CR-038, CR-023.1), which ships with
docx4j 9e3100351 on VERSION_17_3_6 or later (holding b267f24ce, writing CR-038's attributes; unpushed there); the upstream questions; item 57 and its bordered reading; item 62, boxes drawn past the page's edge (1616's `PRO HART`, 12147's
captions unread, what Word does with a really low anchor unmeasured). Tools `~/fop-session-tools/2026-11-05-box-float-edge/`. The paragraph that
follows is the state before it, kept.**

Last updated 2026-11-05, end of the night. **`fop/CR-037` (Enterprise §6.6 item 56, the doubled `w:br` paragraph) is DONE: gated PASS (b362 on
r69 against b360 on r68, docx4j-67, docx4j 7f6162ea2; 0 corpus movers, events equal; `float-foot-edges` exactly B-1, B+1, B+3) and MERGED to
`2.12-docx4j.1` by fast-forward from `CR-037-restart-at-soft-return`, code a260b3bd3, ordered by Jason through docx4j-67 ("take the doubled
w:br paragraph (item 56)"); unreleased, local, the push Jason's. b362 on r69 is the fork's baseline for later gates
(`~/fop-renderers/r69-CR-037-a260b3bd3/`, sha256 5a0447ee...). It is wider than item 56 and Apache's too: any restart between two sequences of
a paragraph, lines repeated at float edges and obstacles, a line LOST at a change of page width, and on Apache `main` an NPE as well (CR-037
§1; tools `~/fop-session-tools/2026-11-05-cr037-restart/`). Open for Jason: the push and release of `.1` (CR-030 to CR-037, CR-023.1) with the
docx4j commit to name; the upstream question for CR-037; item 57; CR-036 §8's bordered-line gap; CR-037 §4.**

Last updated 2026-11-05, late night. **The fix for the defect below is DONE: gated PASS (b360 on r68 against control b359 on r67,
docx4j-67; 0 corpus movers, events equal, `float-foot-edges` exactly the five cases, CR-036 §8) and MERGED to `2.12-docx4j.1` by
fast-forward from `CR-036-edge-space-once`, code 792823785, ordered by Jason through docx4j-67 ("Order the fork's fix"); unreleased,
local, the push Jason's. Drop `~/fop-renderers/r68-CR-036-792823785/` (sha256 85333f7d...). b360 on r68 is the fork's baseline once
docx4j-67 commits the code it ran on (6049e9c00 plus its below-15 float, uncommitted at the gate). Enterprise item 55 closed again.
Still Jason's: the push and release of `.1`, tracing item 56 (the doubled `w:br` paragraph), a probe for item 57, and the
bordered-line gap (CR-036 §8).**

Last updated 2026-11-05, night. **A DEFECT IN `.1`, UNFIXED: CR-036 §6. `firstBoxAfterEdgeCrossesPageFoot` counts the glue after the
edge twice, so a line that fits within its space of the foot is taken for crossing it: after a start edge it moves to the next page,
after an end edge it stays 6pt high, a regression from r66. Confirmed through docx4j's writer, both modes, a space-after as well; no
corpus document has the shape (b349). A candidate is in `~/fop-session-tools/2026-11-05-cr036-probe/overlay-src/`, not in this tree;
the fix waits on Jason's word, then a drop and a gate, and `.1` should not go out before he rules. Also, older than CR-036 and unfixed:
a `w:br` paragraph meeting a float's edge is drawn twice (r66 and r67, CR-036 §6; no corpus document has hit it, cause untraced); Jason
to rule. Word's probe of the two unmeasured shapes, ordered by Jason ("do the Probe for the two unmeasured shapes"), is READ: CR-036
§7, `float-foot-edges`, modes 15 and 12. Both shapes are Word's; at mode 15 r67 39 of 47, the candidate 41 of 47. Enterprise item 55
reopened, 56 (the doubled paragraph) and 57 (a line's own space-before at a float's foot, one shape, against item 44's rule) added.**
The paragraph that
follows is the state before it, kept.

Last updated 2026-11-05, evening. **`fop/CR-036` (Enterprise §6.6 item 55) is DONE: gated PASS (b349 on r67 against b347 on r66,
docx4j-67) and MERGED to `2.12-docx4j.1` by fast-forward at 4d7e190cc, ordered by Jason here ("fix Item 55, the foot defect in .1");
unreleased, local: `.1` is 19 commits past origin's 4fe468d68 and more with this one, and the push is Jason's. b349 on r67 is the
fork's baseline for later gates (`~/fop-renderers/r67-CR-036-cc06737cd/`, sha256 9a8f2893...; the merged code differs from r67 in two
comments only). Item 55 closed (Enterprise 5097e03); registry `fop/CR-036` done (portfolio b61e8d4, local there; `fop/release-2.12-docx4j.1` lists as ready).** Nothing is
ordered: `.1` (CR-030 to CR-036, CR-023.1) waits only on Jason's push and release, with docx4j b267f24ce shipping alongside. A list
beside a float measures from the float's edge, so the page's foot was known only as the cap on the float's foot, which a box
beginning above it never reached. The edge is taken before such a box (`nextBoxCrossesPageFoot`), Word's measured rule
(`float-foot-empties` 19 of 19, 13 on r66; no corpus document moved). UNMEASURED against Word, FOP's own: a line crossing the foot
straight after a start edge (`firstBoxAfterEdgeCrossesPageFoot`) and no space-before kept at such an edge (`edgeAtPageFoot`); the
layout test `float_page-foot.xml` pins FOP's behaviour there, and docx4j-67 offers to add both shapes to a probe, not ordered. Not sent
upstream: Apache `main` has the cap but lays the plain FO differently. Tools `~/fop-session-tools/2026-11-05-cr036-foot/`. The
paragraph that follows is the state before it, kept.

Last updated 2026-11-05. **Item 54 is DONE: `fop/CR-023` §10 and §11 gated PASS (b347 on r66 against b344 on r62, docx4j-67) and
MERGED to `2.12-docx4j.1` by fast-forward at 506deb24e; unreleased, local: `.1` is 14 commits past origin's 4fe468d68, and the
push is Jason's. b347 on r66 is the fork's baseline for later gates (`~/fop-renderers/r66-CR-023-8e870178d/`, sha256 f6d39545...;
506deb24e's code is 8e870178d's, docs after it). It ships with docx4j b267f24ce, unpushed there, its fix for auto spacing after a
floating table. Enterprise item 54 closed (b291810), registry `fop/CR-023.1` done with the release's corrected title (portfolio
7de241a, local there); `fop/release-2.12-docx4j.1` lists as ready.** CR-023 §11 has the route. A block read again after a float's edge keeps
its space-before (CR-020), counted at an end edge and not at a start edge, so later heights on the page lay that far above the
flow; a list beside a float now starts its height at that space (`getListStartAtFloatEdge`). It reaches every side float followed
by a block keeping a space. r63 failed b339 (4083's B.6). r64 failed b340 on 6293; I read that as an unmeasured Word rule for
empty paragraphs and narrowed the fix to r65, which passed b343; both readings were wrong (docx4j wrote 14pt Word drops, r62's
under-count hid it, and with docx4j's fix r64's form beat r65's), and r65 is undone. Lesson: when a right fix loses a document,
check that document's input against Word before narrowing the fix. Open for Jason: the push and release of `.1`, and the earlier
questions below.
Tools: `~/fop-session-tools/2026-11-05-item54-gate/`. The paragraph that follows is the state before the gates, kept.

Last updated 2026-11-04, after the forced reboot. **`fop/CR-023` §10 (Enterprise §6.6 item 54: a bordered anchor's
space-before left out of `fox:float-offset`'s reference) is BUILT, not gated, not merged: branch
`CR-023-offset-bordered-anchor` (cut from `2.12-docx4j.1` at 4fe468d68), code f785f3f7c, resent by docx4j-37 and ordered
here by Jason ("go ahead and finish the item 54 fix now"). Drop r63 at `~/fop-renderers/r63-CR-023-f785f3f7c/` (sha256
d8cfc9f0...), received by docx4j-37 with the sha256 matching. It names the baseline and runs its control on r62, so that
only item 54 shows: docx4j's own is b335 on r61, not b333 on r62, b337's two changes are uncommitted there, and the JDK
upgrade may need a fresh control. No reply is owed and nothing is held open for it; it reports when the gate is ready. CI's
command 4099/0, checkstyle 0, spotbugs 0. The reference is the box less the anchor block's own border and padding, less its own space-before; the version cut
off by the reboot took an enclosing block's too (19.5pt high, caught on the rebuilt reproducer, layout case 12 pins it).
On b337's FO for 4083 through RenderFo, page 2 alone differs, B.2's options, B.3 and its note at Word's within 1pt; two X
marks there (190.4 and 273.8, Word's 196.4 and 279.9) do not move with this fix and are docx4j-37's to read.
Enterprise's Fork line is in (item 54), with its zero-height claim withdrawn as agreed with docx4j-37. Open: docx4j-37's
baseline, which waits on its two uncommitted changes (Jason's go-ahead there) and a fresh control after the JDK upgrade;
the registry is done (portfolio d18c8ce, 2026-11-05: `fop/CR-023.1` in_progress, `fop/release-2.12-docx4j.1` blocked on it; its title still names only CR-030 and CR-031, and a change to CR-023's Status paragraph needs a re-baseline request). **Jason decided 2026-11-05, here: item 54 ships in `.1`, so its release note entry stays and the gate now holds the release.** docx4j-37 and docx4j-portfolio-cd have left `ListAgents`. docx4j-67 holds r63's gate (its word, 2026-11-05, started on Jason's word there): it commits its two changes, runs a fresh control on r62 on JDK 21.0.12.1, gates r63 against that and names the baseline when cut, not b335 or b333; the two X marks are its own, a separate docx4j item. No fast-forward before its reading. On the pass: fast-forward to `2.12-docx4j.1`, close the release note's gate line and §10, item 54
closed, the merge hash to docx4j-portfolio for `fop/CR-023.1` done. `.1` IS PUSHED: origin was at 4fe468d68 on 2026-11-04, so the paragraphs below that leave that push to Jason are
history. Tools: `~/fop-session-tools/2026-11-04-item54/` (`floatoff3.fo`, `baselines.py`, the `4083/` renders). The
paragraph that follows is the state before the reboot, kept.**

Last updated 2026-11-04, late. **`fop/CR-035` (Enterprise §6.6 item 53: a block's first line loses its `text-indent`
where a block-level child precedes its inline content) is DONE: built on branch `CR-035-text-indent-after-block` (cut
from `2.12-docx4j.1` at e096fb41f; code ad739af01), ordered by Jason the same day through docx4j-12 (the relay is his
order for a fork fix; the JIRA post and the pull request still need his word here), gated PASS by docx4j-12 (b333 on
r62 against b330, CR-035 §5.1: docx4j's two writer workarounds keyed off on the new capability
`first-line-indent-after-block`, the thirty-first, plus its `anchoredContentTakesNoTextIndent` pass, docx4j c559809dc;
no mover, events equal; 4083's headings at Word's x 42.5 and 14236 at Word's 2 pages from the renderer alone) and
MERGED to `2.12-docx4j.1` by fast-forward at 4c46120a4; unreleased, local: `.1` on origin is still at e096fb41f, so the
push of this merge is Jason's. b333 on r62 is the fork's baseline for later gates. Item 53 closed (Enterprise
657bca0 on docx4j-12's 92f3fe7); registry done requested of docx4j-portfolio-10. Drop r62 is at
`~/fop-renderers/r62-CR-035-ad739af01/` (sha256 1cedf99f...). As built:
`LineLayoutManager.initialize` sets `isFirstInBlock` when no earlier child manager of the block is a line manager
(Apache tested "first child"); layout test `block_text-indent_after-block.xml`, six cases; CI's command 4099/0,
checkstyle 0, spotbugs 0. Read honestly, XSL 1.1 §7.16.11's letter ("the first child L of the first block-area ...
provided L is a line-area") supports Apache's old behaviour, so this is Word's rule beyond the letter, as item 48's
(CR-035 §2); the JIRA draft `docs/upstream/text-indent-after-block.txt` is an Improvement, not filed, and docx4j-12
was asked to reword item 53's XSL sentence (my Fork line is in, Enterprise 0d20510). Upstream branch
`FOP-text-indent-after-block` in `../fop-upstream-wt` on main 1301bcf74, one commit 297234f16 (3732/0, checkstyle
clean), local, not pushed.
Nothing is ordered after CR-035; open for Jason: the push of `.1`'s new head, its release, the CR-035 JIRA and
pull request, and the earlier questions below.** The paragraph that follows is the night's state, kept.**

Last updated 2026-11-04, night. **Two follow-up fixes to `fop/CR-032` for 4083's page 2 under docx4j's step 2
(branch `CR-032-4083-page2`, code e27cccea9, r61 at `~/fop-renderers/r61-CR-032-e27cccea9/`) are GATED PASS by
docx4j-12 (b327 against b326: 4083 +4, page 2 Word's, no other mover; register Enterprise 3d61827) and MERGED to
`2.12-docx4j.1` by fast-forward at f7498ef54; unreleased. docx4j's own anchor fix (e005410ff) gated b328: 4083 +5
to 0.9055, above its score before step 2. b328 is the fork's baseline for later gates. Enterprise §6.6 item 8 has
the reading. Nothing is ordered. **`2.12-docx4j.1` was PUSHED to origin for the first time on 2026-11-04 at Jason's
word ("push"), at 8e7ae66c8 (CR-030 to CR-034 and this follow-up, with `.9`'s two docs commits it carries); the
release itself is still Jason's (`docs/developer/releasing.md`).** Open for Jason: quieting the four sliver
documents' overflow events, the upstream question (CR-033, CR-034, and the stale-keep fix of CR-032 §8).** CR-032 §7 (the corrected reading; my first
reading blamed docx4j's writer and was wrong, docx4j-12 corrected it from the source) and §8 (the fixes): the
space resolver's auxiliary box before a table's border hid the table from `nextBoxHasContent` and
`nonRestartableLMOfNextBox`, so a keep-with-next heading with space-after before a table reaching an offset float's
top was never the start edge (`isSpaceResolverBox` reads past it); and the flow's `LayoutContext` kept a
keep-with-next pending across a float's edge or a forced break, making the first in-between break of the re-read
list infinite (`FlowLayoutManager.getNextKnuthElements` clears the pending keeps on entry). 4083's page 2 is Word's
with both (201 words to 201); its page 3 still differs by the anchor docx4j gives B.7's yes/no float (the "section C"
paragraph with break-before, docx4j-12's to read). The other step 2 losers (8236, 9832, 10187, 14776) are overflow
events of 1.7 to 3pt with the page as Word's; quieting them is Jason's call (it changes `.1` before release); 1616
is upstream drift. Layout suite and Word tests 824/0, CI's command 4096/0, checkstyle 0, spotbugs 0; Word's three
float probes unchanged. On the pass: fast-forward, release note's line stands, §6.6 item 8 a line. Scratch tools:
`WordModeAT` (both-mode area tree of an FO, `scratchpad/s2/wm/`), the reduced-FO bisection recipe in memory. The
paragraph that follows is the evening's state, kept.**

Last updated 2026-11-04, evening. **T is DONE: `fop/CR-034` (code e0baa4cb7,
`docs/developer/change-requests/CR-034-floating-table-split.md`) gated PASS by docx4j-12 (b324 on r60 against b323,
CR-034 §5.1: `float-foot-crossing` 1.0000, `float-tall-beside` 0.9880, `float-three-pages` 0.9971, all at Word's
pages; events equal; one corpus mover, 1616 -4, read as toward Word's rule; 6705 under step 2 keeps its rows, b325)
and MERGED to `2.12-docx4j.1` by fast-forward at 9b0a91d59; unreleased. b324 on r60 is the fork's baseline for later
gates. Enterprise §6.6 item 8 case (b) closed (0ff95d7); registry `fop/CR-034` done requested of docx4j-portfolio.
With B, A1 and T done, nothing is ordered: `2.12-docx4j.1` (CR-030 to CR-034) is ready for Jason's push and release,
which waited for T; docx4j 17.3.6 follows it. **Later that evening Jason decided step 2 goes into docx4j (via
docx4j-12; docx4j 4f7128174, gate b326 equal to b325, b326 now the baseline), and its six losers were traced here
against the b325 FOs: 4083 -8 is the writer's (the B.6 date table's float written at the group box's top, not in its
anchor paragraph, so the offset is measured 48pt too high; CR-032 §7, commit a2697ad60); 8236, 9832, 10187 and 14776
are overflow events of 1.7 to 3pt in slivers with the page rendered as Word's (10187's anchor overlaps the table's
empty cell in Word too, so the "first word below the float" rule would move it away from Word); 1616 -3 is text
boxes, not the float. Proposed to docx4j-12: quiet such events (an overflow beside a float under a space's width, or
a line of white space only), no line moved; not ordered.** The upstream question for CR-033 and CR-034 (Apache FOP
2.12 loses the same content) is still Jason's. The paragraph that follows is the afternoon's state, kept.**

Last updated 2026-11-04, afternoon. **T is BUILT: `fop/CR-034` on branch `CR-034-floating-table-split` (cut from
`2.12-docx4j.1` at 59f249323), code e0baa4cb7, handed to docx4j-12 as r60 (`~/fop-renderers/r60-CR-034-e0baa4cb7/`,
core sha256 a55680cb...) for the gate against b322 on r59; not merged.** Jason's three decisions (CR-034 §6):
`2.12-docx4j.1` waits for T; default on (every `fo:float` whose table crosses the page foot splits, both modes, no
attribute; capability `float-table-split`, the thirtieth, tells docx4j to lift its step 2 guard); in-row splits in
version 1. As built (CR-034 §3.1), not the refusal of §3 but the float's START EDGE taken BEFORE the anchor (the last
legal break, or the page's start when the anchor opens the page), the parts drawn page by page in
`PageBreaker.drawSplitParts` (a new page per middle part), the anchor's block read again from its start on the page
where the table ends; the `FloatContentLayoutManager` with the table's list, range start and state is kept on the
`fo:float` FO across the re-read (`Float.setLayoutContinuation`), since a restart makes new layout managers.
Measured (§3.2): Word's `float-three-pages` 21 pages, all four cases at Word's rows, the anchor at 74.9/323.3 to
Word's 75.0/323.9; `float-tall-beside` 16 and `float-foot-crossing` 13, cases 1 and 2 split as Word's; the one
difference is tall-beside case 3, the sliver (CR-031 §11's open item). Layout test `float_split.xml` (eight
sequences), `TableBesideFloatTestCase` 8/8, `Docx4jHooksTestCase` 30. CI's command 4096/0, checkstyle 0, spotbugs 0.
Pass criteria CR-034 §5. On the pass: fast-forward to `2.12-docx4j.1`, Enterprise §6.6 item 8 case (b) closed
(`git status` first), registry to done via docx4j-portfolio, release note's "gate pending" closed; then `.1` is
ready for Jason's push and release. Artefacts `~/fop-session-tools/2026-11-03-float-defects/t1-split/`. The
paragraph that follows is the night's state, kept.

Last updated 2026-11-03, night. **T's probe is DONE and T is SIZED, not ordered:
`docs/developer/change-requests/CR-034-floating-table-split.md`.** A float's table adds its areas by position range
(a patched `FloatLayoutManager.processAreas` drew a 30-row float as rows 1 to 16 and 17 to 30, two `SideFloat`s, from
two `KnuthPossPosIter` ranges of the float's own list; patch reverted, artefacts in
`~/fop-session-tools/2026-11-03-float-defects/t-probe/`). Design: CR-033's refusal breaks the page before the anchor
as today, the first range is drawn on page 1 at the anchor's top plus the offset with nothing beside, and the anchor's
floats on page 2 process the remaining range with the anchor beside (CR-034 §3). Size five to eight days; capability
`float-table-split`. **Waiting on Jason's word to build it; the gate baseline is b322 on r59.** Later that night, at
Jason's word, docx4j-12 cut and read Word's `float-three-pages` golden (docx4j ad51efa67, Enterprise item 8 at
593b5b1): a floating table that splits more than once sets its anchor on its LAST page, nothing beside the table on
the first or any middle page, the offset kept on page 1 only, a `w:tblHeader` row repeated at each continuation.
CR-034 §1, §3.3, §3.4 and §5 carry it; the size holds (a middle page is page 1's shape with an advancing range
start). b322 scores the probe 0.8927 at 12 pages to Word's 21: T's third baseline. The paragraph that follows is A1's
state, kept.

Last updated 2026-11-03, evening. **A1 is DONE: `fop/CR-033` (code d972d78fe,
`docs/developer/change-requests/CR-033-float-move-any-offset.md`) gated PASS by docx4j-12 (b322 on r59 against b321:
`float-foot-crossing` 0.9620 to 0.9734 at Word's 13 pages, 0 corpus movers, events equal; inert on the four corpora,
the probe the only measure) and MERGED to `2.12-docx4j.1` by fast-forward at 2e4f3b2a6; unreleased. b322 on r59 is
the fork's baseline for later gates. Next: T's one-day probe (can a float's table add its areas by position range:
page 1's rows, the rest as a side float on the next page with the anchor beside), then a size for Jason; docx4j-12
gates T against b322.** What A1 is: `PageBreakingAlgorithm.handleBox` arms
CR-023's move for every side float, offset 0 included, so a page that cannot hold the float's foot below its anchor
breaks before the anchor and both open the next page; placement at offset 0 unchanged; a float taller than a page
still not moved (T). Measured: `low-plain` 1 page with rows lost to 2 pages whole; Word's `float-foot-crossing` case 4
(no-offset picture) at Word's page 8 and lines, case 1's no-offset table moved whole (Word splits: T), the rest
unchanged. Layout test `float_offset.xml` case 10 added; Apache's `float_11.xml` expectation changed (it pinned the
anchor's lines kept on page 1 beside a float drawn above its anchor). CI's command green (4092/0, checkstyle 0,
spotbugs 0); code d972d78fe; r59 at `~/fop-renderers/r59-CR-033-d972d78fe/` handed to docx4j-12 for the gate against
b321; registry `fop/CR-033` in_progress sent to docx4j-portfolio-10. On the pass: fast-forward, §6.6 item 8, registry
to done. Then T's one-day probe (can a float's table add areas by position range), then a size for Jason.** The
paragraph that follows is B's state, kept.

Last updated 2026-11-03, afternoon. **B is DONE: `fop/CR-032` (code 9b9f63f91,
`docs/developer/change-requests/CR-032-table-below-float-top.md`) gated PASS by docx4j-12 (b321 on r58 against the
fresh control b320: `float-top-crossing-table` 0.9006 to 1.0000 at Word's four pages, 0 corpus movers, events equal,
29 hooks) and MERGED to `2.12-docx4j.1` by fast-forward at 252b4e246; unreleased. b321 on r58 is the fork's baseline
for later gates; `float-tall-beside` 0.8743 (13 pages to Word's 16) and `float-foot-crossing` 0.9620 are the baselines
for A1 and T. Next: A1, on its own branch from `2.12-docx4j.1`.** r58 is at
`~/fop-renderers/r58-CR-032-9b9f63f91/`. Two mechanisms, both modes: a
keep-with-next (an infinite penalty of class `line`) before a table that would reach the pending float's top is a start
edge (`PageBreakingAlgorithm.handlePenaltyAt`, `nonRestartableReaches`), and at the start edge a first box fixing a
width greater than the room clears to the float's foot instead of narrowing (`PageBreaker.resolvePendingIntrusion`, the
obstacle rule of `continueBesideObstacles` for `fo:float`); `firstBoxAfterEdgeFixedIPD` skips the auxiliary box. Word's
`float-top-crossing-table` at Word's lines within 1pt on all four cases through render.sh; p2 probes all at the
float's foot; paragraph probes unchanged; 4083 as the gate renders it unchanged (its page-2 case exists only under
docx4j's step 2 FO). No new capability. Full suite 4092/0, checkstyle and spotbugs clean. On the pass: fast-forward,
§6.6 item 8 (Enterprise file, `git status` first), then A1 on its own branch. The paragraph that follows is the
morning's order, kept.

Last updated 2026-11-03. **Next, on Jason's word ("please go ahead"), in this order: B, A1, then a one-day probe for
T.** docx4j's step 2 (floating wide tables, gate b317 on r57) was blocked by three renderer defects. They are traced
here and recorded with Word's three probes in Enterprise CR-001 §6.6 item 8 (47f2aff, 377517c, bf44357; read it
first).
- **B:** a table that crosses an offset float's top goes wholly below the float, row 1 too, even across a
  keep-with-next heading, which stays put (Word's `float-top-crossing-table`). Today it is set beside the float or
  over it. §11's rule acts only in the end-edge search.
- **A1:** CR-023's move of a float and its anchor to the next page is armed for every side float that cannot split
  (pictures, text boxes, groups), offset 0 included. `PageBreakingAlgorithm.handleBox` arms it only for an offset
  above 0, so a no-offset picture crossing the foot is drawn into the margin, and past the page edge it is lost.
- **T:** Word splits a floating table that crosses the page foot, offset or not, and whether it fits a page or not
  (6705). Nothing is set beside it on its first page, and the anchor begins beside the continuation on the next
  page (`float-foot-crossing`, `float-tall-beside`). Until T exists, floating tables keep CR-023's move.
  The probe asks whether a float's table can add its areas by position range. Then size T for Jason.

Each item goes on its own branch from `2.12-docx4j.1`, gated by docx4j-12 against b314 on r57. The probes and tools
are in `~/fop-session-tools/2026-11-03-float-defects/` (README). The paragraph that follows is the state of
2026-11-02, kept as history.

Last updated 2026-11-02, evening. **`fop/CR-031` (Word mode, docx4j CR-071 phase 1) is MERGED to `2.12-docx4j.1` by
fast-forward at e5f0a7d12** (code 27ed437d3; `docs/developer/change-requests/CR-031-word-layout.md`). Jason accepted
CR-071 ("D1 to D11 as recommended, except for D9 where we decide on (a)"), so the move ships in `2.12-docx4j.1`.
docx4j's copy of FOP's layout managers is now the fork's: `WordLineLayoutManager extends LineLayoutManager` behind
`FopFactoryBuilder.setWordLayout(Map)` and `setWordLayoutFontMetrics(BiFunction)`, capability `word-layout`, the
twenty-eighth. Gated PASS by docx4j-12 (CR-031 §5.1): b303 and b304 on r51 (`~/fop-renderers/r51-CR-031-27ed437d3/`)
against b302, 0 movers, scoreboards byte-identical, events equal, 28 hooks. docx4j 69bbd4dc0 deleted its copy
(unpushed); Enterprise a99d2b8. b304 on r51 was the baseline until phase 3's b306. The UAX #14 pair-table flip is
process-wide: Word tests here call `WordBreakOpportunities.restoreFopPairTable()` `@AfterClass`, or FOP's
`LineBreakStatusTestCase` fails. A change to `LineLayoutManager` now reaches Word mode through the seams marked "Word
mode's seam"; run the 19 ported Word tests with it. Phase 3 (D11, CR-031 §6.2), CR-023 §4.2's mark under Word mode
with no mark on a line of white space only, is MERGED too at c4ba99965 on Jason's word. r52 failed b305: an empty
paragraph beside two column-filling floats was marked, and measuring the emergency break against the column put about
1,100 overflows into discarded passes. r53 passed b306 against b304 with 0 movers, events equal. Then §9, at docx4j-12's report and Jason's wish: Word mode
laid a paragraph twice at a restart after it. Its last-line `LeadingGlue` sits behind the break with the paragraph's
block as position, and `PageBreaker.handleFloatLayout` and `RestartAtLM` read again from the next positioned element.
Both loops now skip the glue, merged at 531bf4f9c. Gate b307 on r54: 4253 and 13544 at an obstacle's edge, and 10598
(CR-026 §8's "blank kept at the restart") to Word's 8 pages, nothing else moving. Then §10, on Word's
`float-gap-words` probe and Jason's "build now, ship in .1": Word never breaks a word in the room beside a float. Word
mode's emergency break now measures against the column; a marked line's overflow is reported only from
`addInlineArea`; and beside an obstacle, a boundary that the orphans keep is an edge before a marked line. Merged at
2fe0ff3dd. Gate b309 on r55: 0 corpus movers, the probe +7, `line`/`linebig` falling only (17776 to 8382, 3243 to 61).
Then §11, on Word's `float-gap-tables` probe and Jason's ruling via docx4j-12 that the fork does it for `fo:float`: a
table or block-container whose FO fixes a width greater than the room beside a side float goes below it (CR-024 §11.20's
rule, no longer confined to obstacles; the width read past the space resolver's auxiliary box; a block that fixes no
width never wider, since two overlapping floats leave a negative room, 8985 in b312); and a float's intrusion ends at a
forced page break, where it had narrowed the next page (the probe loss that kept the rule from floats in b270).
Capability `wide-block-below-float`, the twenty-ninth. Merged at a7fff982b. Gate b314 on r57 against b311: 6 movers, all
toward Word. **b314 on r57 is now the baseline.** docx4j's step 2 (floating wide tables) is not kept; its guards are
docx4j's. Open: a float's anchor's first word in a sliver stays beside it (pinned in `TableBesideFloatTestCase`); a
second floating table beside a first runs past the margin. The move alone is tagged `CR-031-phase1` (3d9a41143; local, not pushed). To trace a gate mover here,
run RenderFo on the gate's own classpath file (`~/fidelity-cr030/cp-b30N.txt`), one pass, with the changed classes as
a javac overlay. Read positions with mutool, not pdftotext's columns. docx4j 17.3.6 waits on `2.12-docx4j.1` on
Central, which is Jason's call, as is the push of `2.12-docx4j.1` and of the tag. The paragraph that follows is the state of 2026-11-01, kept
as history.

Last updated 2026-11-01. **Work is on `2.12-docx4j.1` (snapshot `2.12-docx4j.1-SNAPSHOT`), on Jason's word "start
2.12-docx4j.1": cut from `2.11-docx4j.9` at cc98fa4c0, Apache's 2.12 release (`fop-2_12` at 69e912b39) merged at
263be37e7, then the line commit (revision, `Docx4jFop.APACHE_FOP_LINE = "2.12"`, CI's branch lists, docs); `fop/CR-030`
(`docs/developer/change-requests/CR-030-apache-fop-2.12.md`) records it. Pins are Apache 2.12's: Batik 1.20,
xmlgraphics-commons 2.12, ant 1.10.18, bouncycastle 1.86 (`jdk15to18`, provided). CR-009's three commons
workarounds are undone (the FOP-3311 and FOP-3326 reverts and d9f000cdc's PDF/A test expectation): fifteen files are
Apache's again. CI's command green on JDK 21: 3936/0, 4 skipped, checkstyle 0, spotbugs 0. GATED PASS: b298 on r50
(`~/fop-renderers/r50-CR-030-bb92342d3/`) against b297, run with Batik 1.20 and commons 2.12: 0 movers, events equal, 27
hooks; b298 on r50 is the baseline for later gates. Registry `fop/CR-030` in_progress (portfolio 5b9c9dc). docx4j stays on
`.8` until Jason orders the move, which brings `FopCapabilities.BUILT_FOR_LINE = "2.12"`. `2.11-docx4j.9` never ships. Everything is local until Jason pushes
`2.12-docx4j.1` (and `.9`'s two docs commits, da6c8a00a and cc98fa4c0, which it carries). Release note draft
`docs/release-notes/2.12-docx4j.1.md`. **CR-028 retraction, found re-measuring for the JIRA draft on Apache FOP 2.12
(its binary, Batik 1.20) and `main` 1301bcf74:** the hole stands (from a file base URI the `use` fragment, `tref` text and
`file:` picture are drawn; Batik 1.20's protocol check does not close it), but the "second cause", FOP's
`AbstractFOPImageElementBridge` bypassing the user agent's rule, was wrong: Batik's `SVGImageElementBridge`
checks in `buildImageGraphicsNode` before FOP's `createImageGraphicsNode` override, in 1.19 and 1.20 (bytecode, and
measured on 2.12, `main` and r43). The fork's bridge check is redundant, harmless. CR-028 §2, the README row, the
`.8` correction in the `2.12-docx4j.1` draft note and the JIRA draft say so; the upstream branch
`FOP-svg-external-resources` still carries that hunk, to drop before the PR. The JIRA's public-or-security@ question is
still Jason's.** The paragraph that follows is the state of 2026-10-30, kept as history.

Last updated 2026-10-30. **`2.11-docx4j.8` is RELEASED: on Maven Central since 2026-10-30, Jason's deploy (JDK 21, on
this machine) from the pushed branch at the version commit b7f6ad8bf, verified from Central (68 files, checksums and
signatures good, 27 capabilities; core sha256 1292e531...; the published classes byte for byte r49's, the gated jar),
tagged `v2.11-docx4j.8` there (made here, pushed by name by Jason the same day). It carries `fop/CR-028` and
`fop/CR-029`; the release note `docs/release-notes/2.11-docx4j.8.md` is closed: the docx4j session read the Central
jars as gate b294 against b288 (0 movers, events equal, 27 hooks) and moved `docx4j-export-fo` to `.8` at docx4j
9d6952973 on VERSION_17_3_5, for docx4j 17.3.5 (Jason's next release, ImportXHTML 17.3.5 after it). Registry `fop/release-2.11-docx4j.8` done (portfolio 0c7afbd). Work is now on
`2.11-docx4j.9` (snapshot `2.11-docx4j.9-SNAPSHOT`), cut from b7f6ad8bf and added to CI's branch list; pushed by
Jason 2026-10-30, CI green on it (run 41014099946). Nothing is ordered for `.9`. Still waiting on Jason: his OK on the two JIRA texts and his push of
the two upstream branches, as below; and for CR-028, whether the report goes to Apache as a public JIRA or privately
to security@apache.org (raised 2026-10-30; the fork's own docs describing it went public with the `.8` push). Apache
`main` is two commits past the upstream branches' base 7b96ab1a4 (FOP-3363, FOP-3366), no Apache release; re-check
both branches on it before the pull requests. The runbook's Signing section now says which key signs (C641C2A9AFA87A3B,
not 873D9B7B).** The paragraph that follows is the state of 2026-10-29, kept as history.

Last updated 2026-10-29. **The working tree is on `2.11-docx4j.8`, which carries two CRs merged 2026-10-29 on Jason's word, both unreleased and local until Jason pushes (the branch was 7 commits ahead of origin before the second): `fop/CR-029` (a solid border is drawn without a clip where its corners need none: Jason saw the docx4j pricing document's table rules vanish in Okular until zoomed; `BorderPainter` clipped each side to its corner share, half the width for a collapsed inner border, and poppler snaps thin lines but not clips; no option, no capability; `docs/developer/change-requests/CR-029-border-hairlines.md`; fast-forward to a14709f7b; gated PASS b286 on r48; Enterprise CR-001 §6.6 item 52 added and closed, 7de78cd and 6fd9002; registry done, portfolio 8a29d92) and `fop/CR-028` (an SVG's external resources refused unless `data:`, option `svg-embedded-resources-only`, on by default in the fork, off upstream, the twenty-seventh capability; `docs/developer/change-requests/CR-028-svg-external-resources.md`; code 63a08b0a2, merged by a merge commit since `.8` already had CR-029, the README rows and the `.8` release note's two entries combined; gated PASS b287 on r47 against b285, 27 hooks). The merged head's jar r49 (`~/fop-renderers/r49-merge-CR-028-1cdf26fab/`, sha256 eee3cba6...) gated PASS b288 against b286: 0 movers, events equal, 27 hooks; b288 on r49 is the fork's baseline for later gates. The `.8` release note is `docs/release-notes/2.11-docx4j.8.md`. Waiting on Jason, for each CR: his OK on its JIRA text (`docs/upstream/svg-external-resources.txt`, `docs/upstream/border-hairlines.txt`), then post it and rename the upstream branch to its number; his push of the upstream branches `FOP-svg-external-resources` (b6bfdc227) and `FOP-border-hairlines` (533c0c0e4) in `../fop-upstream-wt`, then the pull requests from `../fop-upstream-prs/`. Incidental: `RowFirstPartRoomTestCase`'s change notice names CR-012's page-number-zero hook, a copy-paste slip, unfixed. Measuring tools for CR-029: `~/fop-session-tools/2026-10-29-cr029-border-hairlines/`.** The paragraph that follows is the state of 2026-10-16, kept as history.

Last updated 2026-10-16, morning. **`2.11-docx4j.7` is RELEASED: on Maven Central since 2026-10-16, Jason's deploy from
the pushed branch at the version commit ecaa37c40, tagged `v2.11-docx4j.7` there (the tag local until he pushes it);
verified from Central (core sha256 4161643c...; `docs/developer/releasing.md`, the sixth release). Work is now on
`2.11-docx4j.8` (snapshot `2.11-docx4j.8-SNAPSHOT`), cut from ecaa37c40 and added to CI's branch list; the branch is
local until Jason pushes it. `.7` carries all of CR-024 phase A, `.6`'s content and `fop/CR-027`; the release note
`docs/release-notes/2.11-docx4j.7.md` is closed: the docx4j bump line is in (docx4j 0dd167d8e on VERSION_17_3_3,
for 17.3.3; its gate b281 of the Central jars 0 movers, every event count equal). Two fixes went in after the branch was first pushed, both from Jason's
testing of m1 in the sample (a user's letterhead, local only): CR-024 §11.20 (1dce69b21, r44 and r45: a sliver under
1pt beside an object is no room, `Obstacle.MIN_ROOM`, and a table or container whose FO fixes a width greater than the
room goes below the object, `fixedIPDOf`; r44's rule had reached `fo:float` and lost a floating-table probe and 8985 in
gate b270, so it is confined with `handlingObstacle()`; r45's b271 0 movers) and §11.21 (2adfbfc2c, r46: a block's last
line is fitted above a band with the border and padding its block closes with, `textBottomOfBox`,
`SpaceResolver.borderAndPaddingBeforeBreak`; the border's width counts, space-after does not, that line alone goes
below whatever the widows; from the docx4j session's two Word probes `table-floating-page-border-above` and `-2`, all
eighteen cases at Word's lines here and in its gate b273, 0 movers on the corpora; m1's 42pt gone). Because each fix
had to go under the version commit, that commit was reset and re-applied three times and the pushed branch replaced
with `--force-with-lease` at Jason's choice (he ran it; the session's permissions deny a force push); the runbook's
sixth-release section records it. The docx4j side of the border rule needs nothing. Open items for `.8`: nothing
ordered; phase B of CR-024 on Jason's word; m1's remaining offsets (its letterhead top about 4pt, page 2's start about
22pt) are the same on r45 and unread. Harness for a docx4j FO: `~/fop-session-tools/2026-10-15-renderfo/` (compile
`RenderFo.java` into a scratch dir with the renderer's classes first on the docx4j session's classpath file, the
latest `~/fidelity-cr030/repro/*-cp.txt`); a probe's lines are read with `mutool draw -F stext` (the glyph-box bottom
is the docx4j session's measure) and its rules with `mutool draw -F trace`.** The paragraph that follows is the state
at the evening of 2026-10-15, kept as history. **r43 at `~/fop-renderers/r43-CR-024-a367207fb/` (sha256 10ce70b6...) IS the fork's
head and was handed to the docx4j session (full suite 3927/0, checkstyle and spotbugs clean under CI's command).** It
carries CR-024 §11.18 (a band's top inside the first line after an edge is beside that line; 6603's heading from 290.25
to 302.25 under docx4j's managers, at Word's 302.2: the 300.2 in the earlier sections was a misreading, retracted in §11.18)
and §11.19 (widows and orphans kept beside an obstacle, `PageSequenceLayoutManager.handlingObstacle()`; 3955's 2+1
split was Apache's relaxation to 1 beside a float on the re-read, copied in docx4j's line manager, which the docx4j
session mirrored and, with a second cause of its own found on r43, has 3955's item whole on page 2: its soft-return
test took the first fo:block on a position chain, which on the re-read is the paragraph's, not the nested preserved
block's; fixed there, gated on `.6` too, b269). r42's gates: control b264 0 movers; candidate b265 finishing. r43's
gates: candidate b268 the frame probe +32, nothing else moved; docx4j's frames committed unpushed on VERSION_17_3_3
(dd55984bc, 5f584872c); b269 (`.6` with those line-manager changes, against b266) 0 movers, so the released renderer's
output is unchanged but for `w:wrap="none"` frames, which reserve no band. Nothing open between the sessions on frames. Both
traces were made with `~/fop-session-tools/2026-10-15-renderfo/` (lays a docx4j FO out through docx4j's own FOP path
with its managers, the renderer's classes first). r42 (70329626b, checkstyle only over d89ccf85f) was the head before. 70329626b is checkstyle only: CI's own command
(`mvn package checkstyle:check`) found seven violations that the phase A commits 2c4d33c62, c79fefc86, bf0a439c3 and
98daf581f had recorded as clean (empty `if` shells left where trace prints were stripped, and long comment lines),
so run CI's exact command, not a bare `checkstyle:check`, before calling a commit clean; origin's tip of the branch
is 3fa338c0e, before all of CR-024, so CI never saw them.** **`fop/CR-024` phase A, the page-anchored half, is MERGED to `2.11-docx4j.7`**
(fast-forward on Jason's word, tip f98834c2c; the branch `CR-024-obstacles` is kept at the same commit; built on it
the same day, cut off `2.11-docx4j.7`; Jason's order of 2026-10-15: phase A alone, the page-anchored half first, since the docx4j session's
CR-032 phase 4 waits on it; the design is CR-024 §4 as corrected by §10, which overrides §4 where they differ; §11 is
the as-built record). Increments 1 and 2 are built and committed (bf0a439c3, 61f53844a): `fox:wrap`, `fox:wrap-sides`,
`fox:wrap-distances` on an absolutely positioned `fo:block-container`, capability `obstacles-in-the-line` (the 26th),
the obstacle registry, the obstacle as a virtual float through CR-023's pending-intrusion mechanism, and the restart
from the page's first break (§10.1); layout test `block-container_fox-wrap.xml`, nine cases; full suite 3927/0,
checkstyle clean. The narrowing reaches docx4j's line manager through the reference IPD, so its side is the
attributes only for this half (no `LineLayoutManager` or `LineBreakPosition` signature changed; tell it before any).
Then, from Word's readings of the nine `picture-page-square` probes (the docx4j session cut them and read Word's PDF
the same day; `~/fidelity-cr030/readings/2026-10-15-picture-page-square-word.txt`): every phase A case matches by
construction, case 2 (both sides) is phase B, and one rule the fork lacked was built (70eccedfc, CR-024 §11.3): a
line is beside an object, or a float, when its text crosses the band, not when its leading alone does; the float
edge moves by one line where the leading alone crossed (`float_overflow-below.xml`, one expectation updated; the
rest of the suite unchanged). Then the docx4j session ran r31 on the probes with `fox:wrap` written and found two defects, both fixed
(3a33174a9, CR-024 §11.4): the band started a line late where its top fell inside a paragraph's first line
(orphans leave no break element there; `LineLayoutManager` now emits one of infinite penalty, class line, only in
a page-sequence that carries an obstacle, which only a pending band's edge may take), and a container with no room
on either side left zero-width lines (it now clears the column). Its readings also corrected the foot rule: Word's
line box carries its leading above the text, so the foot test is the pitch top less the half-leading below. Its
control gate b233 (r31, no `fox:wrap`) had 0 movers: the float edge change is inert on the corpus. Then its r32 corpus gate (b236: the probes to 0.9564, 13544, 4621, 4253 and 5075 to Word's; control b235 one
mover, 2189 -2, the foot rule, unread) found two defects losing content, both fixed (e673e56f2, CR-024 §11.5):
the page-top edge at a forced page break restarted one level inside the block carrying the break, which docx4j's
line manager did not survive (now the block itself, 3475); and a clearing band that pushed its own anchor box past
the page's foot had cleared the page for nothing (8362): the obstacle is now deferred, the page breaks before the
box as CR-023 breaks before an anchor, and the obstacle is taken up on the page the box lands on. Handed to the
gate: r33 at `~/fop-renderers/r33-CR-024-e673e56f2/` (sha256 e0a757b2...; r29 to r32 earlier). r33's gate (CR-024
§11.6): control 0 movers; candidate b239 17 movers, 8362 fixed (+1), the probes and corpus gains hold, left 3475
-30 (traced here under docx4j's managers with its recipe, twice: not docx4j's, but FOP's restart at a block
carrying break-before, which served the break again and lost the block; fixed 98daf581f, a block read again after
an edge gets `SUPPRESS_BREAK_BEFORE`; under docx4j's managers page 6 draws 1714 characters against the control's
1713; r34 at `~/fop-renderers/r34-CR-024-98daf581f/`, sha256 e283ae5b..., handed to the gate), 3955 -2 and
6603 -2. The r34 gate (CR-024 §11.7): 3475 fixed; left 13544 (4 pages to Word's 3: the clearance to a band's
foot leaked across the page break, fixed 42f51bdd1, `PageBreaker.startPart` drops it on a new page; r35 at
`~/fop-renderers/r35-CR-024-42f51bdd1/`, sha256 00455a81..., handed to the gate), 3955 (a frame's own lines 609pt
low in the gate; does not reproduce here with either set of managers) and 6603 (seven top-and-bottom frames on
one page: limit (ii) of §11.1). 3955 was retracted by the docx4j session (its script had paired Word's address
line with the footer's repeat). Several obstacles on one page is BUILT (2c4d33c62, CR-024 §11.8: the page's
obstacles a set on the breaker, the lines narrowed by every obstacle whose band holds them, the foot of one the
start of the rest, a top an event too, the list's first page scanned for its obstacles before the walk; test
cases 16 to 18; r36 at `~/fop-renderers/r36-CR-024-2c4d33c62/`, sha256 7be13ae9...), for when the docx4j session
brings the frames back (it took them out meanwhile, gate b243). **The r35 pair is in (CR-024 §11.9): control b244 0 movers;
candidate b245 13 movers, no line lost, the probes and corpus gains as listed there.** MERGED 2026-10-15 on Jason's
word: `2.11-docx4j.7` is at f98834c2c (r35's gated code plus the several-obstacles commit 2c4d33c62, which gets its
own gate with the frames on r36); the `.7` release note carries CR-024's entries (draft). The fork's baseline for
later gates is b245 on r35 until r36 is read. Registry `fop/CR-024` stays in_progress (phase B waits on Jason). **Jason accepted 2026-10-15: `fox:float-x` and the width provider are dropped from phase A (CR-024 §11.10); phase A closes with the frames' gate on r36; phase B, text on both sides, on his word.** 13544 still ends a page over Word (its page 2 opens 25pt low,
the anchor paragraph that did not fit below the table; Word's placement unread). docx4j's side is committed (2026-10-15): docx4j 597b168c9 (CR-032 phase 5, `fox:wrap` on
page-anchored pictures, text boxes and tables, and `WordLineLayoutManager`'s edge-only breaks; frames still out),
Enterprise ebde2ed (§6.6 item 11 closed with the fork's line: merged at f98834c2c, hook `obstacles-in-the-line`,
CR-024 §11, gated b245 on r35), portfolio b4fe7f6 (the `fop/CR-024` entry and its baseline). The frames' r36 pair ran
(2026-10-15): its control b246 (frames out, r36 against r35) had 5 movers, all losses, the picture probe 9 pages to
17, a regression of several-obstacles: a fresh element list after a forced page break kept the page before's
obstacle set, cleared only when the breaker opened the page, after the list's algorithm had run. FIXED on
`2.11-docx4j.7` (9ebb77034, CR-024 §11.11: a fresh list drops the set; the pending check from the set only after a
float's foot; the first-page scan stops at a forced break; test case 19); The r37 pair (CR-024 §11.12): control b248 clean but for
`table-floating-anchor` -4 and 5075 -1; candidate b249 found 5218 (a form with eight small top-and-bottom frames)
throwing in `handleFloatLayout`. FIXED on `2.11-docx4j.7` (c79fefc86): a break is an edge only where its restart
resolves (`edgeRestartable`, mirroring the resolution, which walks the break's manager up to the next box's chain),
never before a box without content; and an obstacle met while an offset float (CR-023) is pending waits for a later
list (the probe's page 5 had both, and the pending top had collapsed to zero). 5218 renders as its control; its
page-top frames are a recorded limit (their band inside a paragraph's nested picture block, the only break before
it a keep penalty of an empty run). 5075's two empty lines are unread (no text to measure by here). r38 at
`~/fop-renderers/r38-CR-024-c79fefc86/` (sha256 b0c25774...) IS the fork's head. **Its pair is in (CR-024 §11.13):
control b250 one mover, the anchor probe's +4 recovery, events equal; candidate b251 (frames on) 5218 clean, 3955
and 6603 -2 lines each, so docx4j keeps its hidden band copy for frames and its phase 5 stays as committed. The
head is gated.** Then 6603's frames read against Word's PDF (CR-024 §11.14): the clearance from an edge ran to the
first event only, forgetting a taller band still active there (fixed: `PageBreaker.continueBesideObstacles`, one
routine for both edges, every consecutive clearing band one clearance); and Word lays a frame's following text
below the frame, not in the gaps between frames, so `fox:wrap="below"` (`EN_BELOW`) was added for frames, the
band from the anchor to the foot. ba77807a7, r39 at `~/fop-renderers/r39-CR-024-ba77807a7/` (sha256 f2628a93...)
was handed over; the docx4j session reran the frames (CR-024 §11.15: control 0 movers; with `below` 6603 held,
its last 81pt its own footer-table band copy, fixed on its side; 5218 a page over Word, since all its frames are
anchored at the flow's start and `below` sends the body below the lowest, at the page's foot, where Word keeps the
body at the frames' height: which frames get `below` is the writer's choice from the docx, told; 3955's -2 is a list
item split at a page's foot under docx4j's managers only, no CR-024 hunk in the managers it copies, its
`WordListItemLayoutManager`'s restart to compare). Two renderer fixes from 5218 (7bf13d689): the deferral's push
estimate from the content within a band, and a band above every line given up; test case 21. r40 at
`~/fop-renderers/r40-CR-024-7bf13d689/` (sha256 2b4c21e8...) IS the fork's head; `.7` goes nowhere without
7bf13d689. 5075's two empty lines unread. **r40 gated (§11.16): control 0 movers; the frames as obstacles under docx4j's
writer rule (`below` in the page's upper half, `top-and-bottom` in the lower) put 5218 at Word's pages and 6603 at
1.0; only 3955 -2 is open, a legal split Word avoids by luck, its element-list comparison deferred.** Jason reopened the frames (a Word probe, `frame-page-bands`, twelve cases, on the share for his run): with
plain `top-and-bottom` 6603's body went through its frames, a clearance lost in a chain of edges (the float part
never took the clearance pending from the edge before; fixed 3a572c729, CR-024 §11.17; r41 at
`~/fop-renderers/r41-CR-024-3a572c729/`, sha256 4c4be43f..., IS the fork's head). With the fix, top-and-bottom on
6603 gives the gap layout under the stock managers (the heading fits an 11.35pt gap by 0.08pt on FOP's text box)
and should give Word's 300.2 under docx4j's 12pt line boxes. **The probe golden came in: Word's rule is plain
top-and-bottom (cases 1 to 9 to 0.3pt; `w:wrap="none"` gets no band; an at-least frame's band is its at-least
height), so `below` is DROPPED (the last commit; test case 20 rewritten) and `block-progression-dimension.minimum`
on a positioned container is now honoured as the least of its extent (the docx4j session will switch to `.minimum`
for at-least frames once r42 shows it). Open: under docx4j's managers on r41 an empty paragraph of 6603 lands in the
11.35pt gap at 238.6 (its FO `~/fidelity-cr030/repro/r41-frames-tb-6603.fo`, classpath `r41-frames-cp.txt`; the
docx is `~/fidelity-real-c2/resaved-nofields/14_de-DE_num_tbl_6603.docx`, run by the recipe in
`r33-3475-howto.txt` with the renderer's classes first); a trace print at the pending-edge decision was being added
to `PageBreakingAlgorithm.considerLegalBreak` (not in the tree) to read which paragraph and its line box. Gates
running on the docx4j side: b261 (r41 control), b262 (candidate), b263 (`.6`).** Phase A is
closed but for that; phase B on Jason's word. Jason's earlier decision on the frames (2026-10-15, via the docx4j session): the frame
grouping is committed (docx4j 565895f29, renderer-independent, gated on `.6`: 6603 +4, nothing else; Enterprise
3b6b38a); frames as obstacles (the writer rule) and the page-foot table rule are held uncommitted until 3955's
list-item split is read, against r40, when the element-list comparison offered here is taken up. Nothing needed
from the fork meanwhile. The docx4j session's
own status (16:25): CR-040 phase 2 committed unpushed, its frame fixups uncommitted until its gates are read, nothing
needed from Jason yet. 2189 -2 in the r32 control is the float area's height in docx4j's render, not the foot rule
(§11.5), docx4j's to measure. On its PASS: merge `CR-024-obstacles` to `2.11-docx4j.7` by fast-forward, release note
`docs/release-notes/2.11-docx4j.7.md` (not yet written), §6.6 item 11 narrowed in the Enterprise file (Jason's;
`git status` first), registry. Then increment 3 (the width provider and `fox:float-x`). **docx4j's
`WordLineLayoutManager` needs the same boundary break element in its copy** (told 2026-10-15, gated on the
capability and `PageSequenceLayoutManager.hasObstacles()`); until it is in, its own managers still start the band a
line late in that shape. The docx4j session (2026-10-15): page-anchored objects stay at their anchor mid-flow
(decision 4: no); the corpus has few of them (two square pictures, a handful of frames, one top-and-bottom shape),
so the probes are the measure (`~/fidelity-cr030/readings/2026-10-15-picture-page-square-*.txt`, reproducers under
`~/fidelity-cr030/repro/r31-*.fo`, which render here through the harness under `~/fop-session-tools/2026-10-08-cr025-row-foot/`);
its candidate gate b234 (writing `fox:wrap`, on r31) is running. **Next:** nothing in
phase A but the frames' gate on r36 (increment 3, `fox:float-x` with the width provider, was dropped by Jason, CR-024
§11.10: docx4j's padding gives the same one-sided intrusion; the provider is phase B's); phase B on Jason's word. Registry `fop/CR-024` in_progress (tasks.yaml edited and accepted
here, uncommitted: it carries the docx4j session's CR-032 entries and enterprise edits, so the commit is its). On the
gate's PASS: merge to `2.11-docx4j.7` by fast-forward, §6.6 item 11 narrowed in the Enterprise file (Jason's;
`git status` first), release note. **The next merge of Apache `main` waits** for the next batch of our pull requests to land or
an Apache release, whichever first (today `main` is seven commits past CR-027's base: FOP-3354, which the fork
has, and a 2.12.0-SNAPSHOT bump with snapshot Batik and commons; a day's merge, pom and workflow conflicts only). Read Enterprise CR-001 §6.6 before
proposing anything, as the section above says.

**State.** Work is on `2.12-docx4j.1` (2026-11-01, `fop/CR-030`: the base is the Apache FOP 2.12 release); there is no
`2.11-docx4j.9`. `2.11-docx4j.8` is on Maven Central (2026-10-30, released by Jason from the pushed branch at b7f6ad8bf,
`fop/CR-028` and `fop/CR-029`; the seventh release in `docs/developer/releasing.md`).
What follows is the `.7` state, kept as history. `2.11-docx4j.7` is on Maven Central (2026-10-16, released by Jason from the pushed branch), tagged
`v2.11-docx4j.7` at `ecaa37c40`, verified from Central (the sixth release in `docs/developer/releasing.md`; core sha256
4161643c...); `docs/release-notes/2.11-docx4j.7.md` says what it carries: `fop/CR-027` (Apache `main` at c2d7e0aca)
and `fop/CR-024` phase A (`obstacles-in-the-line`, the twenty-sixth capability, §11.1 to §11.21), gated by the docx4j
session through b273. Work is on branch `2.11-docx4j.8` (snapshot `2.11-docx4j.8-SNAPSHOT`), cut from the release
commit. What follows is the `.6` state, kept as history. `2.11-docx4j.6` is on Maven Central (2026-10-09, released by Jason from the pushed branch), tagged
`v2.11-docx4j.6` at `76a413acf`, the version commit, verified from Central (`docs/developer/releasing.md`, the fifth
release; core sha256 c0a8f9e4...); `docs/release-notes/2.11-docx4j.6.md` says what it carries: `fop/CR-022`
(`clear-after-side-float`, the three edge-search fixes), `fop/CR-023` (`float-offset`, `float-overflow-below`),
`fop/CR-025` (`row-first-part-room`), `fop/CR-026` phases A and B (`column-widths`, `column-balancing`), twenty-five
capabilities, each with its CR under `docs/developer/change-requests/` and all gated PASS by the docx4j session, whose
read of the release code (b218 = its cand120 on r27 against b215) had one mover, 12301 +5, its own keep-chain change.
The docx4j session reads the published jar against b218 and then moves `docx4j-export-fo` to `.6`; docx4j 17.3.1
(2026-10-07) depends on `.5`. Work is on branch `2.11-docx4j.7` (snapshot `2.11-docx4j.7-SNAPSHOT`), cut from the
release commit, added to CI's branch list and pushed (CI ran on it: the list change works); it carries the
post-release commit and `fop/CR-027`, the merge of Apache `main` at c2d7e0aca (gated PASS b220, unreleased; the
merge commit and its docs are local until Jason pushes the branch). `.5` (2026-10-07,
`724e92d1c`), `.4` (2026-10-04, `75f9b0262`), `.2` (2026-10-02) and `.1` (2026-09-25) are the earlier releases;
`.3` never shipped. Jason pushed the tag `v2.11-docx4j.6` and the branch by name on 2026-10-09. `fop/CR-019` (ActualText per cluster) is designed and waits on Jason.

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
FOP-3353 (filed 2026-10-07) reports a regression on `main` from FOP-3331: a float whose own child is a block-container is
put in the flow; pull request #126 (2026-10-08, branch `FOP-3353` at 57cc8a40b, the in-flow test against the width
available to the float) fixes it, its JIRA comment waiting on Jason's OK. This session posts to ASF JIRA directly since 2026-10-05, on Jason's OK per
item (see the memory note). The drafts under `docs/upstream/` are stamped with both numbers. If a reviewer asks for
changes, work in the worktree at `../fop-upstream-wt`; when #114 and #109 merge, rebase `FOP-3345` to its one
commit; when #123 merges, rebase `FOP-3355` likewise. `trunk` is at `upstream/main` (5be8c69b6, 2026-10-07).

**Upstream, checked 2026-10-15.** Apache applies our pull requests as its own commits ("by Jason Harrop" in the
subject) and leaves the requests open: #120 (FOP-3348, 5be8c69b6), #121 (FOP-3349, a36b42fab), #125 (FOP-3356,
c2d7e0aca) and, since the CR-027 merge, #123 (FOP-3354, bed50fc76 on 2026-10-09, our Java byte for byte, the test
reworded; JIRA closed Fixed, fix version `main`). Those four were closed here with a note on 2026-10-15 at Jason's
word, and #122 (FOP-3350, the page label 0, declined 2026-10-08 as a path FOP itself never reaches) with them. #108 closed
2026-10-07 (FOP-3352). The sixteen others are open and untouched since our FO reproducers of 2026-10-07: #106, #107,
#109 to #119, #124, #126; one comment from Glenn Adams on #116, encouragement. FOP-3355 (#124) was rebased 2026-10-15 at Jason's word
onto `main` at ced58ae40 as its one commit (765487a77; layout suite 748/0 and checkstyle clean on `main`; pushed by
name with `--force-with-lease`, the request told); FOP-3345 (#116) still waits on #114 and #109.
Apache `main` has moved seven commits past CR-027's base c2d7e0aca: FOP-3354, a version bump to 2.12.0-SNAPSHOT with
Batik 1.19.0-SNAPSHOT and xmlgraphics-commons 2.12.0-SNAPSHOT (snapshot pins again, as CR-009 §3.1 found), javadoc in
CI, "Allow branching" (the workflow), bouncycastle and ant bumps; a follow-up merge is a day and conflicts on the
pom and the workflow only. `trunk` is still at c2d7e0aca.

**Open, in order.**
1. *After the `.6` release* (tag and branch pushed 2026-10-09): the docx4j session reads the published core jar (coordinates and sha256 sent 2026-10-09)
   against b218 and bumps `docx4j-export-fo`; its gate line and commit go into the `.6` release note's header ("Reached
   users in docx4j 17.3.N") when they arrive. The items 2 to 6 below are history now: all of them shipped in `.6`.
2. *`fop/CR-022`, `clear` after a side float: DONE 2026-10-08*, gated PASS (b186b: band withheld, 0 movers; b191: band
   restored and `clear` on the table after it, 10855 at Word's 9 pages with its rubric table 1.4pt from Word's y, the
   text-box probes 1.0000) and merged to `2.11-docx4j.6` by fast-forward; unreleased, in `docs/release-notes/2.11-docx4j.6.md`.
   Capability `clear-after-side-float`, the twentieth. With it, CR-022 §3.5: three defects of the float edge search found
   on 4083 (the edge walked past on a page within its ratio, a kept break never offered as the edge, a deferred edge at a
   forced break inside a table throwing), §6.6 item 47; 4083 renders. Upstream not sent: `clear` on block-level FOs is an
   extension of XSL (draft `docs/upstream/clear-after-side-float.txt`, Jason's call); the edge-search fixes are Apache's
   defects too and would stack on FOP-3355 and FOP-3354 (not drafted yet). The docx4j side commits follow its second gate
   on the released `.5`.
3. *`fop/CR-023`: the offset float, and a line that does not fit beside a float set below it* (docx4j CR-032 §4.2):
   DONE 2026-10-08, gated PASS (b196 to b202 on r19 to r21; the fork's baseline b202 on r21) and merged to `2.11-docx4j.6`
   by fast-forward, the code at 989280390; unreleased. Was in progress on branch `CR-023-float-offset`. The design is
   `docs/developer/change-requests/CR-023-float-offset-and-overflow.md`; the first half, `fox:float-offset`
   (capability `float-offset`, the twenty-first), is built and measured (§4.1 as built; layout test `float_offset.xml`,
   four cases, committed 1760e33c2); the second, a line that does not fit beside a float set at its foot
   (`float-overflow-below`, the twenty-second capability), is built and measured (§4.2 as built; layout test
   `float_overflow-below.xml`, committed 63fdc0aee); both-sides wrap is sized only (§4.3). Full suite 3870/0, checkstyle
   clean. r18 (63fdc0aee) drew the float above Word's by the previous paragraph's space-after (the docx4j session's
   probes): the reference is now the anchor's own space-before only (5afcfc657, a fifth layout-test case); the jar is r19
   at `~/fop-renderers/r19-CR-023-5afcfc657/` (sha256 2cfbbf41...), handed to the docx4j gate 2026-10-08 with CR-023 §6's
   pass criteria and the registry text. Gate b196 (2026-10-08): the offset at Word's y on every probe shape, 6293 to
   Word's pages; two defects fixed since (two floats on one anchor crashed on a restart inside a table; a float the page
   cannot hold below its anchor was carried to the next page without it) and `float-overflow-below` measured inert on
   docx4j's tables (the moved line re-flows with its paragraph where Word's table precedes the paragraphs), so docx4j
   keeps its 2in bound and the capability may serve pictures (docx4j gates picture floats on it, and on float-offset,
   separately). Both fixed in 2f5db13ff (full suite 3870/0, checkstyle clean; `float_offset.xml` eight cases): r20 at
   `~/fop-renderers/r20-CR-023-2f5db13ff/` (sha256 c6054c41...) handed to the gate 2026-10-08, b197 (r19, docx4j's
   interim: single floats and bands with the offset, 6293 +16 to Word's pages, 6131 +2, 1616 +1, 9775 -5, 4083 -1) its
   baseline. docx4j's side is committed on VERSION_17_3_2 gated on float-offset; fop/CR-023 is in the registry.
   Gates b198 and b199 on r20 (2026-10-08): phase 2 whole +34/-8 lines against the renderer-alone control, 6293 +16, the
   pair probe 0.70 to 0.92, 3229 and 3640 right; one regression, 13419 -2 and a page, the page-end refusal firing on a
   page whose first-page master differs: the check now uses the page's own height, 989280390, r21 at
   `~/fop-renderers/r21-CR-023-989280390/` (sha256 a833d5d9...) handed to the gate. On r21 13419 is still refused, and rightly: its float's
   foot would lie 28pt past the body's foot (the docx4j session's numbers); r19 had drawn it into the footer region. The
   cause is docx4j's gap fill, fixed there; r21 is the gate renderer from here: b201 (r21 against r20) 0 movers; 9775's -5
   read as a docx4j table-height deficit the old padding had cancelled, the hook's placement being Word's; b202 (13419's
   docx4j fix, at Word's 3 pages) running. 9775 -5 unread
   by the docx4j session. b200 (picture and framePr floats on float-offset) running. On PASS: merge to `2.11-docx4j.6` by fast-forward,
   release notes closed, §6.6 item 8 narrowed in the Enterprise file (Jason's; `git status` first). Jason confirmed D2 here on 2026-10-08. Phase 0 (nine Word probes, the docx4j session, 2026-10-08, CR-032 §3 and §4 at cae13d3d1)
   settled it: `float-offset` stands (`fox:float-offset` on `fo:float`: the intrusion begins N pt below the top of the
   anchor block, space-before included; the lines that fit in the gap are full width above the float), `float-band` is
   WITHDRAWN (Word does not lay empty paragraphs behind a full-width table; FOP's zero-ipd lines beside a column-wide
   float already match Word for the narrower case), and the second half is both-sides wrap, §6.6 item 10, for this
   session to size: Word runs text down both sides of a centred float, with no minimum width. Serves §6.6 item 8. Back
   in from the band's withdrawal, measured (b189, 2026-10-08): a line whose content does not fit beside a narrow float
   overflows where Word moves the word below the float (6705, 1616, 9832, 8236, 14776 lose 1 to 11 lines on `.5` and r17
   alike), so "defer a line that does not fit to the float's foot" is CR-023's other half, separate from CR-022. The
   clearest corpus case for float-offset (docx4j session, phase 1, 2026-10-08): 4083's table, tblpY 51pt at the page's left
   edge; floated from its first line, FOP cuts the question above it into a column beside the table (-2 lines, a garbled
   page) where Word runs those lines full width and puts the table below them. docx4j guards that shape (an offset over a
   line and a half stays in the flow) until the hook exists.
4. *`fop/CR-024`, obstacles in the line (both sides and page-anchored wrap)*: DESIGN only, written 2026-10-08 at Jason's
   word (`docs/developer/change-requests/CR-024-obstacles-in-the-line.md`) from Word's own layout dialogs: the floating-table
   work is the one-sided corner of one model, a rectangle in the column with an anchor, offsets, a wrap mode, a side rule
   and distances; tables, pictures, text boxes and charts are the same input. Phase A a width per line (TeX's parshape:
   `BreakingAlgorithm.getLineWidth(int line)` is the hook, the line areas already take a width per line) with an obstacle
   registry and page-anchored objects as virtual floats, about two weeks; phase B a line in segments, two to three; §6.6
   items 10 and 11. Reviewed 2026-10-15 at Jason's word (CR-024 §10): the obstacle is known mid-list, so the
   breaker restarts from the page's first break when a band starts above it; the width per line comes from a
   provider on the layout context, below the line manager, since docx4j's is a private copy with its own greedy
   breaker; the pitch is a fixed point; no trial layout; it folds FOP-3331's in-flow path, float-overflow-below and
   the page-anchored question. Phase A 2.5 to 3 weeks. **ORDERED 2026-10-15 (Jason): phase A, see the top of this
   section; IN PROGRESS, increments 1 and 2 built the same day.**
5. *`fop/CR-025`, a table row at a page's foot needs room for its bottom margin*: DONE 2026-10-08, gated PASS (b208:
   11657's page 30 ends at row 53, row 54 opens page 31 whole, row 34 still split; 2451 +19 lines, 13383 +4, 2048 −1,
   nothing else moved) and merged to `2.11-docx4j.6` by fast-forward; unreleased, in the `.6` release notes. Hook
   `row-first-part-room` (`FOUserAgent.setRowFirstPartRoom(true)`, off by default: on by default it failed Apache's
   `table_empty-cells.xml`), the twenty-third capability; `TableStepper` adds to the first break element inside a row
   the after padding and border its cells give up at a split, `RowFirstPartRoomTestCase`. r22 failed the gate because
   the margin was combined by `max` with the split's carved-out width, which docx4j's retained padding-before made the
   larger (CR-025 §3); §6.6 item 48 records the rule, the mechanism and the fork commit. docx4j's side 2b914e69a. A
   harness caveat, the docx4j session's: the scoreboard's in-order line parity misreads this pagination fix on 11657
   (−55) where a strict same-page pairing rises 4012 to 5347 lines; an open harness question, not the fork's.
6. *`fop/CR-026`, columns of unequal width in the region body* (§6.6 item 49, the docx4j session's measurement on
   10598, 6116, 1137 and 11092, 2026-10-08): PHASE A DONE 2026-10-08, gated PASS (b211 on r24, b212 on r25 0 movers)
   and merged to `2.11-docx4j.6` by fast-forward. **Phase B DONE 2026-10-08: built on branch `CR-026-balancing`
   (commits 1d6dd8be6 and 0d14ae993, capability `column-balancing`, the twenty-fifth), gated PASS on r27 (b214 0
   movers; b215, docx4j writing near-equal widths under the capability: 11126 to 1.0000 and Word's page, 6116 +65,
   394 +50, all at Word's page counts, nothing else moved) and merged to `2.11-docx4j.6` by fast-forward; the
   fork's baseline for later gates is b215 on r27. §6.6 item 49 carries it (Enterprise 9c438e4, item open for the
   remainder); the registry has `fop/CR-026` done at aff684a6f. Nothing open on phase B but Jason's push.** r26's reading (CR-026 §9, end): 6116, 1137 and 11092 identical to r25. 6116 because
   a list beginning at a `break-before="column"` block was re-read as the break alone (fixed in 0d14ae993) and,
   with the fix, because each of its two-column stretches holds an explicit column break, so nothing balances;
   1137 because its unequal sequence's two flow children are both `span="all"` (docx4j's writer, nothing in the
   columns; 11092 asked to be checked). So the corpus has no balancing measurement yet: 11126 (its FO balances
   82/83 here where it was 66/4) would be one if docx4j wrote its near-equal widths.
   Gate (2026-10-08): b213 (r26 against b212) 0 movers; 11126 read on r27 with its near-equal widths 0.9560 to
   1.0000, so docx4j lifted its 5% rule under `column-balancing` (cand118, 824a384d6); b214 and b215 as above. Not the §3 design (trial restarts on the breaker's own managers, which a restart
   inside a paragraph mutates destructively and which column 1's later `addAreas` reads): a height cap found by
   bisection (`PageBreakingAlgorithm.setBalancingCap`), each trial laying the list out column by column on a
   throwaway flow layout manager made by the maker for the same `fo:flow`, positioned where the breaker's list
   began (`UnequalColumnBalancer`, CR-026 §9), so docx4j's own managers lay the trial out and need nothing exposed.
   Measured: 33/31 with the heading on page 1 where it was 58/21 and the heading on page 2; the restarted page
   49/47; three columns 33/33/31; 11126's FO 82/83; 10598 unchanged. Full suite 3882/0, checkstyle clean, layout
   tests `region-body_column-widths_balance.xml` (new) and `_span.xml` (now balanced). On PASS: merge by
   fast-forward, release note's "Gate: pending" closed, §6.6 item 49 narrowed further in the Enterprise file
   (Jason's; `git status` first), registry. The §3 text that follows is the design as first written.
   Start from CR-026 §3 phase B (balance by trial: a candidate break in
   the first column, the remainder re-laid from it at the second column's width, the heights compared, a bounded
   search, the first column's areas added only when the search ends) and §5's mechanism (the changing-IPD restart
   adds column 1's areas and notes the committed position before column 2 is laid, so the restarted list begins in
   column 2, `getStartingPartIndexForLastPage` returns −1 and `BalancingColumnBreakingAlgorithm` sees column 2
   alone; a span="all" block then opens the next page behind an empty span, as `region-body_column-widths_span.xml`
   pins today: 66 and 4 lines, the block on page 2). The probes are `a-span-all.fo` (10598's widths, a heading after
   nine paragraphs; the equal control balances 30/29 and keeps the heading on page 1) and `over-span-70.fo` under
   `~/fop-session-tools/2026-10-08-cr026-probe/`; a balancing guard in `PageBreaker.redoLayout` for the −1 case was
   tried and withdrawn (Apache's last-page redo, `basic_link_to_last_page`, relies on that path), so the design must
   not simply skip the redo. Cut `CR-026-balancing` off `2.11-docx4j.6`. Gate: 6116 (twenty sections, span blocks
   throughout, the balancing measurement), then 1137 and 11092 at their span blocks; the baseline is b212 on r25;
   docx4j writes one set of widths per continuous run (its rules §7), which phase B may make worth refining per part
   master. Word balances a continuous section's unequal columns (CR-026 §3). `docs/developer/change-requests/CR-026-unequal-columns.md`;
   the history that follows is how phase A went. `fox:column-widths` and `fox:column-gaps` on `fo:region-body`, a width per column in `Span` and
   `BodyRegion`, `PageSequenceLayoutManager.getCurrentColumnWidth()` per column, and `PageProvider.compareIPDs`
   returning the difference between consecutive columns so FOP's own changing-IPD restart reflows column by column;
   the renderers step by each column's width. About a week for the widths, three to five days for balancing; one
   afternoon's probe of the restart path within a page (CR-026 §5) before trusting it. Jason asked 2026-10-08 whether
   anything stops it: nothing does (the Apache `main` merge waits a week by agreement and touches floats, not the
   column or IPD path; `.6`'s release needs only his push; CR-024 is his ordering). The §5 probe was run 2026-10-08
   on branch `CR-026-unequal-columns` (cut from `2.11-docx4j.6`): phase A is as sized, the restart path laying the
   second column at its own width on the same page (one page, two pages, three columns, reversed widths; a table
   across the boundary keeps column 1's width, the known limit); `compareIPDs` must keep Apache's body-region
   comparison under `span="all"` (six layout tests). Phase B is a week, not three to five days: the restart commits
   column 1 before column 2 is laid, so the balancing algorithm never sees it and a `span="all"` heading goes to the
   next page behind an empty span, as Apache's own unbalanced output does. The probe code (a system property in
   `Span`) stays on the branch for phase A to replace. The docx4j session (2026-10-08): continuous sections share one
   page-sequence with `span="all"` blocks, so phase A alone serves 10598 (next-page breaks) and phase B's balancing
   is what 6116, 1137 and 11092 need at their span blocks. Phase A BUILT 2026-10-08 on the branch at Jason's word
   (CR-026 §7): `fox:column-widths` and `fox:column-gaps` on `fo:region-body`, capability `column-widths`, the
   twenty-fourth; layout test `region-body_column-widths.xml`; the warning `columnWidthsIgnored`; full suite 3876/0,
   checkstyle clean; commit 872d45020. Not yet gated: r24 at `~/fop-renderers/r24-CR-026-872d45020/` (sha256
   e23002a1...) handed to the docx4j session 2026-10-08 with CR-026 §4's pass criteria (10598 to Word's pages and its
   column origins; 6116, 1137, 11092 their column origins; nothing else moves). Gate FAIL on 10598 (2026-10-08, CR-026
   §8): the second column at Word's x, but its page 3's column 2 empty and the text shifted a column; not reproduced
   through FOP's command line with the gate's fonts, which gives Word's pages (the config and reader under
   `~/fop-session-tools/2026-10-08-cr026-probe/`). Bisected by the docx4j session: FOP's stock managers give Word's
   columns on r24, docx4j's own managers do not, the column 8.4pt over at its head: the blank paragraph that spilled
   past page 2's column 2, which Apache's IPD restart drops and docx4j's managers keep (CR-026 §8, probes `spill2-*`,
   `pagewidth-*`); the 1 to 4pt Word gives those blanks is docx4j's pitch question, open in its register; the fork
   keeps Apache's drop until it is settled. r24 stands. Corpus reading b210 against b209 (CR-026 §8): 10598 +203 to
   0.7527, 394 +49, 6116 +25; losses routed on docx4j's side (cand117, b211 running: table where the stretch fits a
   page, widths only where columns differ by more than 5%); 11126's 25 overflow events on near-equal widths are the
   one open item for the fork, found and fixed (a list read again after a column boundary keeps its span,
   `LayoutContext.restoreSpan`; layout test `region-body_column-widths_span.xml`; a balancing guard tried first
   failed `basic_link_to_last_page` and is out). Gate PASS on r24, b211 (cand117 against b209: 10598 +203 to
   0.7527 at 9 pages, 6116 +14 at Word's 5, nothing else moved; docx4j's side committed 5eb37c818); r25 with the
   fix, at `~/fop-renderers/r25-CR-026-91f3527db/` (sha256 13970471...): b212 (cand117 unchanged on r25 against
   b211) 0 movers on the four corpora, probes and errors unchanged, 11126's events gone; the fork's baseline for
   later gates is b212 on r25. Merged 2026-10-08. Still to do after the merge: merge to `2.11-docx4j.6` by
   fast-forward, the release note's "Gate: pending" closed, §6.6 item 49 narrowed in the Enterprise file (Jason's;
   `git status` first). Phase B after.
7. *`fop/CR-019`, ActualText per cluster*: §9 answered by Jason (tier A, all scripts, on in the fork and offered
   off upstream; PDF/UA matters, so veraPDF is installed and a tagged sample tested first). Not to start until he
   says.
8. *Merging Apache `main`* into the fork: BUILT 2026-10-09 at Jason's word on branch `merge-main-2026-10-09`
   (cut from `2.11-docx4j.7`), `fop/CR-027`, `main` at c2d7e0aca: eight commits, three ours come back identical,
   FOP-3357 Bengali, FOP-3351 AFP, FOP-3352 (the fork keeps FOP-3339's general form), FOP-3331 floats with
   FOP-3353's fix applied and a guard that a float with `fox:float-offset` written never takes FOP-3331's in-flow
   path (CR-027 §3). Seven conflicts resolved as CR-027 §3 says. DONE: gated PASS (b220, 0 movers; one event
   count moved, FOP-3357 supplying a Bangla syllabizer) and merged to `2.11-docx4j.7` by fast-forward; the fork's
   baseline for later gates is b220 on r28. Registry `fop/CR-027` done (portfolio b7e3be3f5, by the docx4j
   session); `fop/CR-024`'s registry status corrected there to "proposed" (6e82b9910).
9. *Upstream pull requests*: on a reviewer's request, work in `../fop-upstream-wt`; rebase `FOP-3345` when #114
   and #109 merge, and `FOP-3355` when #123 merges.
10. *Known and unfixed, recorded in the CRs:* the second glyph of a one-character cluster keeps a
   private-use code point in ToUnicode (CR-002 §10.2; ActualText per cluster is the follow-up); a format
   character the font has no glyph for is still lost (CR-007 §4); the position-adjustments paint path
   indexes its adjustments by UTF-16 unit, wrong after a supplementary character (CR-005, noted to the
   docx4j session); FOP embeds a single-byte TrueType font whole (`PDFFactory.makeFontFile`), moot for
   docx4j since it retired its `+noliga` twin under the fork; per-font `advanced="false"` is ignored by
   FOP's stock font collection (CR-003 §10).
11. *Backlog, measured by the docx4j session (2026-10-09), not to start without Jason's word:* 12301 (311 pages),
   every section a keepNext heading, a 481pt picture with keepNext, a second keepNext heading, then a 36-column
   table with a repeating header row. Word drops the keeps it cannot satisfy and breaks before the table (the
   heading at page 11's foot, page 12 opening with the repeated header); the renderer puts the heading and the
   table's first step on page 11 and the body overflows, 119pt under docx4j's managers, 449pt under FOP's stock
   ones, at every section. By the flow's element list the chain fits (560839 of 615271): the repeated header's
   height is not in the list, only in the header/footer penalty's width (21661), so the breaker lays the first
   step with the header unaccounted. First measurement here when ordered: where the table stepper puts the
   repeated header's height (the first step's box, or the penalty only) and whether the stock overflow is Apache's
   own, upstream-bound. Reproducer `~/fidelity-cr030/repro/keep-chain-table-header-12301.fo` (not redistributable);
   docx4j's register `documents/12301.md`, cause `keep-chain-table-header`; docx4j's `boundKeepChains` does not
   reach it (its gate b216). **Measured here 2026-10-09 (Jason's word):** the stock overflow is not the table
   header but the picture paragraph's line: `LineLayoutManager.makeLineBreakPosition` takes the half-leading of the
   explicit 482.958pt line-height against the 8pt strut (237779 each side) as the line's space-before and -after,
   then sizes the line to the 481.5pt graphic, so the block is 958714 for a 483156 line; the chain then fits no
   page and FOP lays it overflowing (440pt on my page 10 with a stand-in image, their 449). XSL max-height would
   give about 721pt, Word's exact rule 483; `line-stacking-strategy="font-height"` gives 482958 exactly (a
   docx4j-side attribute for the stock path; its own line manager already gives the box). The table header is
   accounted (second-to-last box, every break penalty's width). Apache's code, upstream-bound, not drafted.
   Probes and README: `~/fop-session-tools/2026-10-09-12301-picture-line/`. docx4j's 119pt under its own
   managers was its own `boundKeepChains` (a chain summed without its closing break's width, so the stepper's
   first penalty, 178195 wide, put 729034 on the page; fixed there, cand120, gate b217), no renderer item. §6.6
   item 50 agreed and added by the docx4j session (uncommitted there until its gate closes). Nothing for the fork
   but the upstream draft when Jason orders it; the backlog item above is closed.
12. *Page-anchored floating tables (docx4j's proposed CR-032 phase 4; Jason asked for a view 2026-10-15, answered
   to docx4j-25):* measured here that a retained negative space-before on a flow's first block draws it above the
   body top with the flow laid after it, nothing clipped (probes `~/fop-session-tools/2026-10-15-page-anchored-probe/`),
   so a table that opens its section needs nothing from the fork; for a table after content, recommended against a
   docx4j two-pass and for either CR-024 phase A (the obstacle registry, §6.6 item 11, two weeks, Jason's
   ordering) or a three-to-five-day hook `fox:page-y` on a block-level FO, honoured in
   `PageBreakingAlgorithm.computeDifference` per candidate part as float-offset is. Nothing ordered.
13. CR-020 phases 2 and 3 remain in docx4j's CR; phase 2 is much smaller than its §8 describes (P2-2
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
- A core-only jar must be first on the classpath and the aggregate launcher jar (`docx4j-fo-renderer-<v>.jar`, or
  `fop-<v>.jar` upstream) left off: that jar has no classes but a manifest `Class-Path` naming its sibling core jar,
  which the JVM appends right after it, ahead of the drop. It voided a gate run (r17, 2026-10-08) and this session's
  own runs the same morning, through `fop/target`'s stale core copy; check the renderer line's version and hook count
  before reading anything.
- Before filing upstream, `git fetch upstream` and reproduce on Apache's current `main`, not on reading: on
  2026-10-07 `main` had moved six commits, and FOP-3331 had changed floats under CR-020's drafts.
- The working tree is shared with other fork sessions: `git branch --show-current` before every commit and every
  statement naming a branch (the memory note on it).
- A probe must carry the consumer's FO shape, not the rule's minimum: CR-025's r22 passed its probe and failed the
  gate on the retained padding-before docx4j writes on every cell. Before handing a jar over, render the docx4j
  session's reproducer here with the switch on (a small harness sets the user agent; the fonts are under
  `~/fidelity-fonts/vm-common`, a config built from them; `docx4j:` attributes stripped) and diff the pages against
  the switch off; the pagination will not be the docx4j session's (its two-pass render measures the region extents),
  but whether the rule fires at all shows.

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
