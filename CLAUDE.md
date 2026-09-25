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

- **`2.11-docx4j.2` is the fork's branch. Work on it; release from it.** It carries the
  `revision` property that sets the version, so a release changes one line there; see
  `docs/developer/releasing.md`. Do not count it against `trunk`, which tracks Apache's
  post-2.11 `main` and so diverges from the fork's base.
- `docx4j-2.11` is the previous long-lived branch, and since nothing was tagged it is
  what identifies the `2.11-docx4j.1` release: that shipped from `2f5030172` on it. Leave
  it alone.
- The name will stop matching once `2.11-docx4j.2` ships and the `revision` moves on. It
  is still the long-lived branch at that point (Jason, 2026-09-25), so do not cut a new
  one per version; rename this entry rather than the branch if it becomes confusing.
- `trunk` tracks Apache's `main`. Remotes: `origin` = plutext/xmlgraphics-fop,
  `upstream` = apache/xmlgraphics-fop, `metanorma` and `chunlin` = the two forks whose
  commits CR-020 §9 classified.
- `FOP-3328`, `FOP-cjk-radical-tounicode`, `FOP-empty-glyph-not-composite`,
  `FOP-packed-glyph-bboxes` are the upstream-facing branches: one fix each, cut
  against `trunk`, for a JIRA and a PR on Apache's GitHub.
- An upstream-bound fix is done twice: on its own `FOP-####` branch against `trunk`
  for the PR, and on `2.11-docx4j.2` for the fork. A docx4j-only hook goes on
  `2.11-docx4j.2` only.
- Merge `upstream/main` into `2.11-docx4j.2` at least at every Apache release and
  whenever a fix sent from here lands upstream.

## Build and test commands

Apache FOP's own Maven build, Java 8 and later (Java 11 or 21 here):

```bash
mvn -B package checkstyle:check spotbugs:check     # what CI runs on every push (.github/workflows/maven.yml)
mvn install -DskipTests                            # the snapshot docx4j consumes (2.11-docx4j.1-SNAPSHOT)
mvn -pl fop-core test -Dtest=SomeTestCase           # one test class
mvn -pl fop-core test -Dtest=LayoutEngineTestSuite  # FOP's layout tests (fop-core/test/layoutengine/standard-testcases) - slow
```

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
4. On a pass, merge to `2.11-docx4j.2`; it records the item in CR-020 §8. Where the change
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

**`2.11-docx4j.1` is released to Maven Central** (2026-09-25, from commit `2f5030172`,
unreleased and untagged before that). It carries the four font fixes, the phase 1 hooks
and P2-1. `docs/developer/releasing.md` is the runbook and records what the first
release proved. There is no git tag for it.

**Phase 2 is much smaller than CR-020 §8 describes.** A reachability pass on 2026-09-25
asked of each queued item whether docx4j can reach the code at all, and most cannot:

- **P2-1 surrogate pairs** is done, gated at 449 of 449 documents and 148 probes, merged
  and released. It is inert on the corpus, which holds no astral character.
- **P2-2** is worked around twice in docx4j already, so the fork change only lets those
  workarounds go. **P2-3** is a nine-line diagnostic. Both clean, both small.
- **P2-4's** Arabic half is unobservable across the corpus; its zero-width-space half
  sits behind accessibility mode, which docx4j never enables. **P2-6** and **P2-7** are
  inert because docx4j emits no trigger. The structure-tree half of **P2-5** is
  superseded by FOP-3165 and FOP-3283, already in Apache `main`, and what remains of it
  would not pass checkstyle here.
- **P2-8 ligatures** is `fop/CR-001`, design only. Its premise needed correcting: docx4j
  already handles Word's setting with a font twin in `docx4j-core`, so the common path is
  right today. What remains is the twin's four gaps.

**Recommended order, both design only: `fop/CR-002` first, then `fop/CR-001`.** CR-002 is
Enterprise CR-001 §6.6 item 30: a ligature's `ToUnicode` publishes a private-use code
point instead of its letters, so search, copy and paste and screen readers break wherever
a ligature is drawn. It affects every FOP user on default settings and the ligature hook
cannot reach it.

**Be honest about where the fork stands.** Gated against Apache FOP on the corpus, the
measured fidelity difference today is nil. Its value so far is the font fixes preventing
failures, and the hooks replacing reflection. That is why Apache FOP stays docx4j's
default through 17.2.1, with a switch at 17.3.0 at the earliest, waiting on a release
carrying CR-002 and P2-8.

**Upstream is the bottleneck, and it is not our latency.** Three JIRAs are drafted and
unfiled; only Jason can file them, and the drafts live in their commit messages and under
`docs/upstream/` (the README's "Tracking upstream" says which is where). FOP-3328 and
FOP-3330 are filed with pull requests 106 and 107 open, unreviewed since July. Apache has
twenty open pull requests, the oldest from 2020, and runs no CI on requests from forks, so
do not plan around review. A fix the fork needs, the fork carries.
