# Releasing `docx4j-fo-renderer` to Maven Central

The mechanism is deliberately the same as docx4j's: `nexus-staging-maven-plugin`
against the OSSRH Staging API, the `ossrh` server id in `~/.m2/settings.xml`, and a
`release` profile carrying the sources, javadoc and signing plugins. Matching docx4j
means a first release from here fails or succeeds the way docx4j's releases already
do, rather than introducing a second mechanism to debug at the same time.

docx4j's pom carries a TODO to migrate to the Central Publisher Portal plugin. That
migration is one task across both repositories; do not start it here alone.

## The version is written in one place

The root pom's version is `${revision}`, set by a property of that name, and every module takes
it through `<parent><version>${revision}</version>` while declaring no version of its own. So a
release changes one line. Sibling dependencies use `${project.version}` and need no attention.
This is docx4j's arrangement; compare `../docx4j/docx4j-core/pom.xml`.

**It only works because of `flatten-maven-plugin`.** Maven publishes the original pom, not the
effective one, so without flattening a consumer would receive a literal `${revision}` it cannot
resolve, and could not resolve the parent either to discover what it means. The plugin writes a
`.flattened-pom.xml` with the parent element removed, inherited values inlined and the version
resolved to a literal, and that is what installs and deploys. Verify after any change to it:

    grep -c revision .flattened-pom.xml */.flattened-pom.xml    # every count must be 0

Two deliberate differences from docx4j. Its `flattenDependencyMode` is `all`, which it needs
because its parent declares dependencies for every module; ours is left at the default, because
this parent declares no dependencies and no dependency management and every module dependency
carries an explicit version. Copying `all` would hoist every transitive into a direct dependency
and stop consumers pruning subtrees they exclude. And `updatePomFile` is set here, which docx4j
leaves off: without it the plugin skips pom-packaged projects, so the parent would publish
carrying the raw property. Nothing consumes the parent, since the children come out
self-contained, but a permanently broken artifact on Central is worth one line to avoid.

Do not hand-edit a version. Changing only the root, as happened on 2026-09-25, used to leave the
modules pointing at a parent version that no longer existed in the reactor: they then resolved
the parent from the local repository instead, silently losing the release profile, so no sources,
javadoc or signatures were produced and the build still reported success. The single property
removes that failure mode.

## What the pom now does

- **`<developers>`** — Central rejects a release without at least one.
- **`nexus-staging-maven-plugin`** in the root build, `extensions=true`, so it
  replaces the default deploy. Server `ossrh`, `autoReleaseAfterClose=true`.
- **No `distributionManagement` repository.** Deliberate: fork snapshots are never
  published, docx4j consumes them from the local repository. A `mvn deploy` on a
  `-SNAPSHOT` version therefore fails fast, which is what we want.
- **A `release` profile** adding sources, javadoc and PGP signatures.
- **A `release` profile in `fop/pom.xml`** attaching *empty* sources and javadoc
  jars. That module is a launcher with no Java sources: its manifest carries
  `Main-Class` and the classpath, and the jar holds nothing else. Apache's own `fop`
  artifact is the same shape and publishes with neither jar, but rather than rely on
  that surviving the Portal's stricter validation, both classifiers are present.

## Two environment details that will bite

1. **`JAVA_HOME` must be set.** It is not set in every shell here, and the javadoc
   plugin fails with "Unable to find javadoc command" when it is missing. The build
   otherwise succeeds, so this only appears under `-Prelease`.

        export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

2. **Always `clean` first.** The module target directories accumulate jars under both
   coordinate sets, because the upstream-facing branches build as
   `org.apache.xmlgraphics:fop`. A release built without cleaning can pick up a jar
   from the other identity.

## The release

1. Phase 2 and the release decision are Jason's; see docx4j CR-020 §4 and §8.
2. Set the version by editing the `revision` property in the root pom, and the `<scm>` url and tag
   so they name the branch, and nothing else: `2.11-docx4j.N`, with no `-SNAPSHOT`. Commit and push;
   the release is built from the pushed branch, and CI should be green on that commit.
3. Dry run first, which builds and signs but publishes nothing:

        export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
        mvn -Prelease clean verify -Dgpg.passphrase=…

   `verify` is the phase signing binds to, so this is the first step that proves the
   key works. The release profile also skips SpotBugs, which the core module otherwise
   binds to that same phase; CI runs it on every push, so a release built from a
   committed tree has already been analysed. Check that each of the four modules
   produced a main, a sources and a javadoc jar, each with a `.asc` beside it.
4. Then publish:

        mvn -Prelease clean deploy -Dgpg.passphrase=…

   `autoReleaseAfterClose` means the staging repository closes and releases without a
   visit to the web interface. Drop that flag if you would rather inspect first.
5. Confirm the five artifacts appear on Central, each with sources, javadoc, `.asc`, `.md5` and
   `.sha1`, and that the core manifest's `Implementation-Version` is the release.
6. Only then tag the commit the deploy was built from: `git tag -a v2.11-docx4j.N <commit>`. See
   Tagging below; the `v` prefix avoids a collision with the branch name. Push the tag by name.
7. Tell the docx4j session so it can score the released artifact and move `docx4j-export-fo`
   off the snapshot. Then cut `2.11-docx4j.N+1` from the release commit (CLAUDE.md, Branches).

## Tagging

Release tags are the Maven version with a `v` prefix: **`v2.11-docx4j.1`**. Annotated, not
lightweight, with the artifact list and what the release contains; see that tag for the
shape.

**The prefix is not decoration.** The long-lived branch is named for a version, so a tag
named exactly `2.11-docx4j.2` would collide with the branch of that name, and git does not
silently prefer one: it refuses, with `error: refname ... is ambiguous`. Every later
command naming that ref breaks. The prefix keeps the two namespaces apart by construction.

**Never push with `--tags`.** This repository holds tags from the other forks, fetched
with their remotes, including Metanorma's own `v2.11.1` through `v2.11.5` release tags.
Pushing all tags would publish those to plutext's repository. Push the one tag by name:

    git push origin v2.11-docx4j.N

Tags here are unsigned. Signing one needs the key passphrase; the published artifacts are
signed regardless, which is what a consumer verifies.

**Tag after the deployment is confirmed, not before** (Jason, 2026-10-07). A tag made before the
deploy marks a commit that may not ship: `v2.11-docx4j.2` was placed at the version commit, a test
fix then followed, and the deploy went from the fix. Tagging once Central serves the artifacts
marks exactly the commit that was built, and a deploy that fails leaves no tag to move. The
version commit is the only commit at the release version, so there is nothing to reconstruct:
`git tag -a v2.11-docx4j.N <that commit>`. This replaces the earlier rule of tagging first,
written after `2.11-docx4j.1` was tagged a day late on 2026-09-26.

## Signing

Three secret keys are on this machine; only one is ultimately trusted:

    rsa4096/873D9B7B  Jason Harrop (gpg2) <jharrop@gmail.com>

`maven-gpg-plugin` uses gpg's default key unless told otherwise, so pass
`-Dgpg.keyname=873D9B7B` if the default is not that one. The `--pinentry-mode
loopback` argument is already configured, which is what lets `-Dgpg.passphrase=`
work without a prompt.

## Proven by the first release, 2.11-docx4j.1 on 2026-09-25

Everything in this document worked end to end. What had been unknown is now answered:

- **Signing works.** The loopback pinentry configuration lets `-Dgpg.passphrase=` sign
  without a prompt, across all five modules.
- **The namespace authorises these artifacts.** Nexus reported `Using staging profile
  ID "org.docx4j" (matched by Nexus)`, so authorisation is by namespace as expected and
  the new artifact names needed no separate registration.
- **The `ossrh` credentials work** against `ossrh-staging-api.central.sonatype.com`.
- **`autoReleaseAfterClose` closed and released without a visit to the web interface**,
  and the artifacts were resolvable from Central within minutes.
- **Flattening produced the right poms.** Verified against what Central serves, not
  just what was generated: every published pom carries a literal `2.11-docx4j.1`, no
  property reference and no parent element, and the parent is the 2422-byte flattened
  form rather than the original.

The staging repository was `org.docx4j--a01c8c30-1e51-4bf1-9cfd-f587bc11ce8a`. Note
that the URLs differ from pre-migration releases, which named `oss.sonatype.org` and
numbered staging repositories like `orgdocx4j-1095`.

## Proven by the second release, 2.11-docx4j.2 on 2026-10-02

- **The one-line version change works.** `revision` went to `2.11-docx4j.2` in one commit
  (ae4d4bc59) and every module followed; the published poms carry the literal, no property, no
  parent, as Central serves them.
- **The release built on another host from the pushed branch**, not from this working tree, so
  what shipped is exactly what the branch said. The full suite there failed once, on a test this
  session had not run after adding a capability; one commit fixed it (3a68b4c57) and the deploy
  went from that. Run the full `fop-core` suite before every merge.
- **Tagged before deploying this time**, as this document asked: Jason's annotated `v2.11-docx4j.2`
  at ae4d4bc59, the version commit, 09:43 local, deploy after. The test fix that followed changed no
  shipped class. The tag is local until pushed by name (`git push origin v2.11-docx4j.2`).
- **Verified from Central after publication**: five artifacts, each with sources, javadoc, `.asc`,
  `.md5`, `.sha1`; the core manifest's `Implementation-Version` is `2.11-docx4j.2`, so
  `Docx4jFop.version()` reports it.
- The next development version is `2.11-docx4j.3-SNAPSHOT`, set right after the release so a local
  `mvn install` cannot shadow the published `2.11-docx4j.2` in `~/.m2`.

## Proven by the third release, 2.11-docx4j.4 on 2026-10-04

- **The first release on Apache `main`'s base** (`fop/CR-009`). It still builds against the released
  `xmlgraphics-commons` 2.11 and Batik 1.19, where `main` pins snapshots. The two commits that needed
  the snapshot API (FOP-3311, FOP-3326) are left out.
- **Tagged before deploying**: `v2.11-docx4j.4`, annotated, on the version commit `75f9b0262`. That commit
  also carries the final release notes, the project url and the five artifact descriptions. The branch
  and the tag were pushed by name. CI was green on that commit on JDK 8, 11, 17, 21 and 25.
- **Verified from Central after publication**: the five artifacts, each with sources, javadoc, `.asc`,
  `.md5` and `.sha1` (the parent a pom only). The core manifest's `Implementation-Version` is
  `2.11-docx4j.4`, and the published core carries `fop/CR-010` (`processWordMapping` takes the letter
  space).
- The next development version is `2.11-docx4j.5-SNAPSHOT`, on branch `2.11-docx4j.5`, cut from the
  release commit and added to CI's branch list.

## Proven by the fourth release, 2.11-docx4j.5 on 2026-10-07

- **Released by Jason from the pushed branch** at the version commit `724e92d1c`, which changes the `revision`
  and the `<scm>` lines and nothing else; CI was green on it (run 37591001572). The release notes were still
  marked draft at that commit and were closed on the next branch, so a reader of the tag finds the draft
  wording; the content is the same.
- **Tagged after the deployment was confirmed**: `v2.11-docx4j.5`, annotated, created at `724e92d1c` once
  Central served the artifacts. Jason made that the rule the same day (Tagging above): the tag marks the
  commit that was built, and nothing is tagged that did not ship.
- **Verified from Central after publication**: the five artifacts, each with sources, javadoc, `.asc`, `.md5`
  and `.sha1` (the parent a pom only). The core manifest's `Implementation-Version` is `2.11-docx4j.5`; the
  published poms carry no property reference and no parent element; `Docx4jFop` in the published core names
  the four capabilities new in `.5` (`page-number-zero`, `measured-region-extents`, `side-float-edges`,
  `simulate-style-per-face`). Core jar sha256
  `dff5ffe1b3ef5425a20f6fe392729e9c69faa86c11c163e175a46b3cf11f2af3`.
- The next development version is `2.11-docx4j.6-SNAPSHOT`, on branch `2.11-docx4j.6`, cut from the
  release commit and added to CI's branch list.

## Proven by the fifth release, 2.11-docx4j.6 on 2026-10-09

- **Released by Jason from the pushed branch** at the version commit `76a413acf` (the `revision` and the `<scm>`
  lines, nothing else), CI green on it (run 37858658826). The branch had never been pushed before that day, so the
  fork session ran the CI steps locally first (`mvn -B package checkstyle:check spotbugs:check`, clean) beside the
  full `fop-core` suite on the last code commit; and the docx4j session read the release code before the release
  (b218 on r27, the last code commit's jar): one mover, its own.
- **Tagged after the deployment was confirmed**: `v2.11-docx4j.6`, annotated, at `76a413acf` once Central served
  the artifacts; pushed by name.
- **Verified from Central after publication**: the five artifacts, each with sources, javadoc, `.asc`, `.md5` and
  `.sha1` (the parent a pom only), every file answering 200; the core jar's sha1 as Central's `.sha1` states; the
  core manifest's `Implementation-Version` is `2.11-docx4j.6`; the published core pom carries no property
  reference and no parent element; `Docx4jFop` in the published core names the twenty-five capabilities, the six
  new in `.6` among them (`clear-after-side-float`, `float-offset`, `float-overflow-below`, `row-first-part-room`,
  `column-widths`, `column-balancing`). Core jar sha256
  `c0a8f9e48206e45da5175ba4b584d05c545163d115ba880bbdecacd2ab9e2900`.
- The release notes were still marked draft at the version commit, as for `.5`, and were closed on the next
  branch. The next development version is `2.11-docx4j.7-SNAPSHOT`, on branch `2.11-docx4j.7`, cut from the
  release commit and added to CI's branch list.

## Two things that look wrong and are not

**The published pom lists fewer dependencies than the module's own pom.** For the core
module it is 19 against 25. Five of the six are test-scoped, which a published pom has
no reason to carry. The sixth is `net.sf.saxon:saxon`, which is not a project dependency
at all: it sits inside a build plugin, supplying Saxon to the stylesheet code generation.
Apache's own published pom has it in exactly the same place. Nothing consumer-facing is
missing. Do not "fix" this.

**Never write the upstream part as `2.11.0`.** Maven normalises the trailing zero, so
`2.11-docx4j.1` and `2.11.0-docx4j.1` compare as exactly equal. Writing it the other way
would produce a version Maven considers identical to one already published, which cannot
be undone. Keep quoting `2.11` as Apache tagged it.

## The name

No Apache mark in the product name, and the README's modified-distribution notice
stays. CR-020 §2.2. Apache FOP is a trademark of the Apache Software Foundation, and
this is a modified distribution derived from it, not Apache FOP.
