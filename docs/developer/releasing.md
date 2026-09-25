# Releasing `docx4j-fo-renderer` to Maven Central

The mechanism is deliberately the same as docx4j's: `nexus-staging-maven-plugin`
against the OSSRH Staging API, the `ossrh` server id in `~/.m2/settings.xml`, and a
`release` profile carrying the sources, javadoc and signing plugins. Matching docx4j
means a first release from here fails or succeeds the way docx4j's releases already
do, rather than introducing a second mechanism to debug at the same time.

docx4j's pom carries a TODO to migrate to the Central Publisher Portal plugin. That
migration is one task across both repositories; do not start it here alone.

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
2. Drop `-SNAPSHOT`: the version is `2.11-docx4j.N` across the five poms.
3. Tag and make sure `<scm><tag>` matches.
4. Dry run first, which builds and signs but publishes nothing:

        export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
        mvn -Prelease clean verify -Dgpg.passphrase=…

   `verify` is the phase signing binds to, so this is the first step that proves the
   key works. Check that each of the four modules produced a main, a sources and a
   javadoc jar, each with a `.asc` beside it.
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

## Not yet verified

- **Signing has never been run here.** Everything up to it is proven: a clean
  `-Prelease package` builds all twelve jars with no errors. Step 4 above is the
  first real test and it needs the passphrase, so it needs Jason.
- **Whether the `org.docx4j` namespace authorises this artifactId.** Central
  authorises namespaces rather than individual artifacts, so it should, but
  `docx4j-fo-renderer*` has never been published and only a real deploy proves it.
- **Whether the `ossrh` credentials in `settings.xml` are a Portal token.** The same
  entry works for docx4j today, which is the best evidence available short of trying.

## The name

No Apache mark in the product name, and the README's modified-distribution notice
stays. CR-020 §2.2. Apache FOP is a trademark of the Apache Software Foundation, and
this is a modified distribution derived from it, not Apache FOP.
