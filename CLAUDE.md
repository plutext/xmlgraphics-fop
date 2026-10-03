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

- **`2.11-docx4j.4` is the fork's branch. Work on it; release from it.** It carries the
  `revision` property that sets the version (`2.11-docx4j.4-SNAPSHOT`), so a release changes one
  line there; see `docs/developer/releasing.md`. Do not count it against `trunk`, which tracks
  Apache's `main` and so diverges from the fork's base.
- **One branch per release line, named for the version it will ship** (Jason, 2026-10-03, replacing
  his 2026-09-25 rule of one long-lived branch whatever the version). When `2.11-docx4j.N` ships,
  cut `2.11-docx4j.N+1` from it, move the `revision` there, and add the new name to
  `.github/workflows/maven.yml`. The branch is not the tag: the release is `v2.11-docx4j.N`.
- `2.11-docx4j.3` will not ship (Jason, 2026-10-03). It is Apache 2.11 plus `fop/CR-008`, and `.4` was cut
  from it to take Apache `main` (`fop/CR-009`); `.4` ships when ready, carrying both. Leave `.3` alone.
  `.4` keeps the `2.11` line on purpose: CR-009 §6 gives the reasons.
- `2.11-docx4j.2` is the previous branch; `2.11-docx4j.2` the release was tagged `v2.11-docx4j.2` on
  it at ae4d4bc59. On origin it runs three commits past the release, ending at f7a1bdcd9, the first
  of which already moved the `revision` to `.3-SNAPSHOT`; those three are on `2.11-docx4j.3` as well.
  Leave it alone. `docx4j-2.11` is the branch before that, and what identifies the `2.11-docx4j.1`
  release: that shipped from `2f5030172` on it, tagged `v2.11-docx4j.1`.
- `.github/workflows/maven.yml` names the branches twice and must gain each new one. A stale list there fails silently: no runs at all looks exactly like no
  failures. It was missed in the 2026-09-26 rename for that reason. To read the runs, name the
  repository: `gh run list -R plutext/xmlgraphics-fop --branch 2.11-docx4j.4`; a bare `gh run list`
  here resolves to Apache's repository through the `upstream` remote and shows Apache's runs.
- `trunk` tracks Apache's `main`. Remotes: `origin` = plutext/xmlgraphics-fop,
  `upstream` = apache/xmlgraphics-fop, `metanorma` and `chunlin` = the two forks whose
  commits CR-020 §9 classified.
- The upstream-facing branches are one fix each, cut against Apache `main`, named for their JIRA:
  `FOP-3328` and `FOP-packed-glyph-bboxes` (FOP-3330), whose pull requests #106 and #107 are open, and,
  cut 2026-10-03, `FOP-2918` and `FOP-3339` to `FOP-3347`. `FOP-3345` is stacked on `FOP-3346` and
  `FOP-3340`. The older names `FOP-cjk-radical-tounicode`, `FOP-empty-glyph-not-composite` and
  `FOP-surrogate-pair-word-split` were deleted on 2026-10-03 (local only, never pushed; their commits
  are in `FOP-3340`, `FOP-3339` and `FOP-2918`). The branches are worked on in a
  worktree at `../fop-upstream-wt`; the pull request texts are in `../fop-upstream-prs/`. On Apache
  `main` the import order differs from 2.11's (static imports directly under the others, no blank
  line) and no file carries the fork's change notice.
- An upstream-bound fix is done twice: on its own `FOP-####` branch against `trunk`
  for the PR, and on `2.11-docx4j.4` for the fork. A docx4j-only hook goes on
  `2.11-docx4j.4` only.
- Merge `upstream/main` into `2.11-docx4j.4` at least at every Apache release and
  whenever a fix sent from here lands upstream.

## Build and test commands

Apache FOP's own Maven build, Java 8 and later (Java 11 or 21 here):

```bash
mvn -B package checkstyle:check spotbugs:check     # what CI runs on every push (.github/workflows/maven.yml)
mvn install -DskipTests                            # the snapshot docx4j consumes (2.11-docx4j.4-SNAPSHOT)
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
4. On a pass, merge to `2.11-docx4j.4`; it records the item in CR-020 §8. Where the change
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

Last updated 2026-10-03, at hand-over. Read Enterprise CR-001 §6.6 before proposing anything, as the
section above says.

**State.** `2.11-docx4j.2` is on Maven Central (2026-10-02), tagged `v2.11-docx4j.2` at `ae4d4bc59`, and
docx4j 17.3.0 depends on it by default. It carries `fop/CR-001` to `CR-007`; what each does is in
`docs/release-notes/2.11-docx4j.2.md`, and the detail, measurements and gate readings are in the CR
documents under `docs/developer/change-requests/`. Work continues on branch `2.11-docx4j.4`, snapshot
`2.11-docx4j.4-SNAPSHOT`, cut from `2.11-docx4j.3`, which will not ship; unreleased on it: `fop/CR-008`
(2026-10-03, gated PASS) and the merge of Apache `main` (`fop/CR-009`); draft notes in
`docs/release-notes/2.11-docx4j.4.md`. `2.11-docx4j.1` was the
first release, tagged at `2f5030172`. `docs/developer/releasing.md` is the runbook and records what each
release proved.

**Upstream.** Every fix has a JIRA and an open pull request on apache/xmlgraphics-fop, cut against Apache
`main` on a branch named for its number, each measured on `main` and passing the full suite and
checkstyle (2026-10-03): #108 FOP-3339 (empty glyph), #109 FOP-3340 (radical), #110 FOP-3341 (lookup
fallback), #111 FOP-3342 (shared default langsys), #112 FOP-3343 (kerning flag), #113 FOP-3344 (letter
spacing on the DP path), #114 FOP-3346 (selector drift), #115 FOP-2918 (surrogate pair), #116 FOP-3345
(ToUnicode for substituted glyphs, stacked on #114 and #109), #117 FOP-3347 (format characters); and from
before, #106 FOP-3328 and #107 FOP-3330. The drafts under `docs/upstream/` are stamped with both numbers.
If a reviewer asks for changes, work in the worktree at `../fop-upstream-wt`; when #114 and #109 merge,
rebase `FOP-3345` to its one commit. `trunk` is at `upstream/main`, 92 commits past `2_11`.

**Open, in the order I would take them.**
1. *`fop/CR-008` (FOP-2918's bidi levels) is on `2.11-docx4j.4` (from `.3`), gated PASS 2026-10-03.* It found that
   #115 is half the fix: the low surrogate's placeholder must also take its character's bidi class
   (CR-008 §2), or a neutral outside the BMP inside right-to-left text still cuts the run. The gate
   showed docx4j documents reach the defect (an assertion under `-ea`, wrong order without). Part 2 is
   on #115 since 2026-10-03 (b401de0d9, green on `main`; body item 7 and a comment).
   FOP's bidi class table predating Unicode 6.1 is now Enterprise CR-001 item 35 (no corpus reach).
2. *Apache `main` is merged* into `2.11-docx4j.4` (2026-10-03, `fop/CR-009`, merge 4d6c9d981): full build
   green, not gated. FOP-3311 and FOP-3326 are reverted (unreleased xmlgraphics-commons API); hook
   `rule-style-int` added; `gsub-features` is property id 296. `fop/CR-010` (item 16's width fix,
   capability `letter-space-width`) is on the branch too (ab8fcaa48), not yet installed. Its upstream
   branch is `FOP-2349` (FOP-2349 upstream, open since 2014; comment posted 2026-10-03), stacked on
   `FOP-3344`; pull request #118 open since 2026-10-03. The docx4j
   session's gate on the merge: steps 1 (compile, 17.3.0 binary) and 2 (image cache) PASS; corpus,
   hyphenation and tagged output to come. Letter spacing is judged after a reinstall with CR-010. The per-file change
   notices still say "derived from Apache FOP 2.11"; a mechanical pass is open.
3. *Known and unfixed, recorded in the CRs:* the second glyph of a one-character cluster keeps a
   private-use code point in ToUnicode (CR-002 §10.2; ActualText per cluster is the follow-up); a format
   character the font has no glyph for is still lost (CR-007 §4); the position-adjustments paint path
   indexes its adjustments by UTF-16 unit, wrong after a supplementary character (CR-005, noted to the
   docx4j session); FOP embeds a single-byte TrueType font whole (`PDFFactory.makeFontFile`), moot for
   docx4j since it retired its `+noliga` twin under the fork; per-font `advanced="false"` is ignored by
   FOP's stock font collection (CR-003 §10).
4. CR-020 phases 2 and 3 remain in docx4j's CR; phase 2 is much smaller than its §8 describes (P2-2
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

**Be honest about the fork.** Gated against Apache FOP, the measured fidelity difference was nil until
CR-003; since then the gates show real movement toward Word (kerning and ligatures for Calibri text,
Arabic shaping in DejaVu Sans, letter spacing painted), each recorded with its scoreboard reading and
its losses. docx4j 17.3.0 made the fork the default on the gated fixes and the hooks. Do not oversell
it; I did, twice, about P2-8.

**Upstream is not something to plan around.** Apache has 32 open pull requests (twelve of them ours), the
oldest from 2018, and runs no CI on requests from forks. A fix the fork needs, the fork carries.

**The peers.** docx4j has more than one session; `ListAgents` shows them. The one that runs the
fidelity gate holds Enterprise CR-001 and usually `tasks.yaml`; send it text rather than editing either
when the tree is dirty. Its partitions and gate readers are under `/home/jharrop/fidelity-gsub-partition/`,
local only, not redistributable. Tell it before asking Jason for any `mvn install`.
