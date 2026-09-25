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
2. Set the version by editing the `revision` property in the root pom, and nothing else:
   `2.11-docx4j.N`, with no `-SNAPSHOT`.
3. Tag and make sure `<scm><tag>` matches.
4. Dry run first, which builds and signs but publishes nothing:

        export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
        mvn -Prelease clean verify -Dgpg.passphrase=…

   `verify` is the phase signing binds to, so this is the first step that proves the
   key works. The release profile also skips SpotBugs, which the core module otherwise
   binds to that same phase; CI runs it on every push, so a release built from a
   committed tree has already been analysed. Check that each of the four modules
   produced a main, a sources and a javadoc jar, each with a `.asc` beside it.
5. Then publish:

        mvn -Prelease clean deploy -Dgpg.passphrase=…

   `autoReleaseAfterClose` means the staging repository closes and releases without a
   visit to the web interface. Drop that flag if you would rather inspect first.
6. Confirm the five artifacts appear, then tell the docx4j session so it can move
   `docx4j-export-fo` off the snapshot.

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
