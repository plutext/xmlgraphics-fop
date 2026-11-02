# CR-030: Apache FOP 2.12, and the `2.12-docx4j` line

Status: BUILT 2026-11-01 on Jason's word ("start 2.12-docx4j.1"), gated PASS the same day (b298 on r50, §6),
unreleased, local until Jason pushes. Since 2026-11-02 the release also carries `fop/CR-031`, which moves docx4j's
Word layout into the fork (docx4j CR-071 D9(a)); it gated PASS (b303 and b304 on r51 against b302) and is merged to
`2.12-docx4j.1` by fast-forward at e5f0a7d12, and its phase 3 (b306 on r53) at c4ba99965 on Jason's word, the move
alone tagged `CR-031-phase1`; so the release waits only on Jason. Branch `2.12-docx4j.1`, cut from `2.11-docx4j.9` at cc98fa4c0; the merge of Apache's `fop-2_12` at 69e912b39
is 263be37e7; the line commit follows it, after CR-028's re-measurement (d90c25b07). CI's command green (§6). Registry key `fop/CR-030`. The earlier merges are
`fop/CR-009` (`main` at ab5d6eba6) and `fop/CR-027` (`main` at c2d7e0aca); this one takes Apache's 2.12 release.

Not an Enterprise CR-001 §6.6 item: a change of base, which closes or narrows none of them. §6.6 has no item for
SVG resources or PostScript; item 51 (SVG text unkerned) is untouched by Batik 1.20 as far as is known, unmeasured.

## 1. Why

Apache FOP 2.12 is on Maven Central since 2026-10-20, with Batik 1.20 and xmlgraphics-commons 2.12. CLAUDE.md asks
for a merge at every Apache release. CR-009 §6 kept the `2.11` line for four reasons, measured on 2026-10-03:

- Maven ordered `main-docx4j.1` below `2.11-docx4j.3`. Not a reason against `2.12`: Maven's comparator
  (`maven-artifact` 3.9.16, 2026-11-01) orders `2.11-docx4j.8 < 2.11-docx4j.9-SNAPSHOT < 2.12-docx4j.1-SNAPSHOT <
  2.12-docx4j.1`.
- Apache's `main` declared `2.11.0-SNAPSHOT`. It has declared `2.12.0-SNAPSHOT` since f02f34931, and 2.12 is released.
- docx4j's `FopCapabilities` warns unless the line is `BUILT_FOR_LINE = "2.11"`. Still true: the docx4j release that
  takes `2.12-docx4j.1` moves it to `"2.12"` (§4). That is the one cost.
- Metanorma's fork released `v2.11.N` while tracking `main`. A precedent for staying on a line before its release,
  not after it.

Jason chose the new line on 2026-11-01.

## 2. What 2.12 is, against the fork's base

Apache released 2.12 from its branch `fop-2_12`, cut from `main` at 1e2519e23 (2026-10-13), with two commits on top.
The GitHub mirror carries the release branches but no release tags past `fop-2_8`. From c2d7e0aca, the fork's base
since `fop/CR-027`:

| commit | what |
|---|---|
| f1c57556b | bouncycastle 1.85 to 1.86, ant 1.10.14 to 1.10.18 |
| f02f34931 | version 2.12.0-SNAPSHOT, Batik 1.19.0-SNAPSHOT, xmlgraphics-commons 2.12.0-SNAPSHOT |
| 1e2519e23 | `.asf.yaml`: branch protection for `fop*` and `release*` branches |
| af4967fdf | "Update for release": 2.12, Batik 1.20, xmlgraphics-commons 2.12, the NOTICE year, README release notes |
| 69e912b39 | "Fix javadocs": one `@param` line in `GraphicsPainter` |

So 2.12's Java is c2d7e0aca's but for one javadoc line. FOP-3354 (bed50fc76, our pull request #123) reached `main` on
2026-10-09, after the cut, and is not in 2.12; the fork has it as `fop/CR-020`.

Not taken: `main`'s eleven commits past the cut, `main` being at 1301bcf74 on 2026-11-01 and still versioned
2.12.0-SNAPSHOT on Batik 1.19.0-SNAPSHOT: javadoc in CI (bc168eedd, 50a2b173d, ced58ae40), FOP-3354, a release upload
step (82134c045), FOP-3361 and FOP-3363 (Bengali, d019e064c, 7b96ab1a4, e8b7a6666), FOP-3362 (encryption of
`PDFGoToRemote`, 0d351048f), FOP-3366 (PDF 2 encryption, 0fa0a200b) and "Improve tests" (1301bcf74). The base is the
release, so "derived from Apache FOP 2.12" is exact, and nothing past it is needed by docx4j; the next merge of
`main` takes them.

## 3. The merge (263be37e7)

- **Conflicts:** the poms, `fop/build.xml` and `fop/releasedist.xml` only. The fork's modules keep their coordinates,
  their `${revision}` parent and the fork's `release` profile; the unbuilt Apache modules (`fop-sandbox`,
  `fop-servlet`, the transcoders) and the two Ant files take 2.12. The descriptions say "based on Apache FOP 2.12";
  the project `url` is the repository, no longer the `2.11-docx4j.4` tree, stale since `.5`. The `scm` block keeps
  `.8`'s values until the next version commit, as the runbook says.
- **Pins:** Apache 2.12's: Batik 1.20, xmlgraphics-commons 2.12, ant 1.10.18, bouncycastle 1.86. Bouncycastle stays
  `jdk15to18` and `provided`, as Apache has it, so it is still not transitive. docx4j-digsig uses the `jdk18on`
  flavour at 1.86 (the docx4j session, 2026-11-01), so the versions now agree. Switching the fork to `jdk18on` would
  be a difference from Apache made only here; not done.
- **CR-009's three commons workarounds undone.** `fop/CR-009` §3.1 reverted FOP-3311 (afe6d0dfd) and FOP-3326
  (3819745cf), whose PostScript JPEG ratio and compressed graphics call commons API no release then had, put change
  notices on the fourteen files (dba4a8dc8), and kept 2.11's PDF/A extension-schema expectation in
  `PDFAMetadataTestCase` (d9f000cdc, FOP-3304's half that comes from commons). No fork commit has touched those
  fifteen files since, and they are Apache 2.12's again, byte for byte. Without them the fork does not compile
  against commons 2.12 (`ImageEncoder.getAdditionalFilter`, `ImageEncodingHelper.createRenderedImageEncoder(RenderedImage,
  String)`, the `ImageFormGenerator` constructor taking a `PSGenerator`), and `testPDFAExtensionSchema` fails. The
  README's row for the left-out commits goes. PostScript output gains Apache's `jpeg-compression` and
  `compress-streams` options; docx4j never writes PostScript.

## 4. The line (the commit after the merge)

- `revision` `2.12-docx4j.1-SNAPSHOT`; `Docx4jFop.APACHE_FOP_LINE` `"2.12"`, so `version()` falls back to
  `2.12-docx4j.development` off the jar and `Docx4jFopTestCase` checks the new prefix. The jar's
  Implementation-Version carries the revision.
- `.github/workflows/maven.yml`: `2.12-docx4j.1` added to both branch lists; the comment names it the working branch.
- **docx4j:** `FopCapabilities.BUILT_FOR_LINE = "2.11"` warns at start-up on a `2.12-…` renderer ("docx4j-export-fo
  was built for the Apache FOP 2.11 line…"). The docx4j release that depends on `2.12-docx4j.1` sets it to `"2.12"`,
  with `FopCapabilitiesTest`. That is the docx4j session's change. The warning is the only effect on a docx4j built
  for `2.11`, as far as the code shows: its layout managers subclass internals that 2.12 leaves as c2d7e0aca had them,
  which `.7` and `.8` already ship. The gate is what establishes it.
- **docx4j's move, done 2026-11-01 on Jason's order** (the docx4j session; docx4j 27bf221d4 and 3b2e597b0, local and
  unpushed until `2.12-docx4j.1` is on Central and its pom drops -SNAPSHOT): `fo.renderer.version`
  `2.12-docx4j.1-SNAPSHOT`, `-Papache-fop` on Apache FOP 2.12, Batik 1.20 and commons 2.12, `BUILT_FOR_LINE = "2.12"`
  (a FOP of another line warns, not refused, `aFopOfAnotherLineIsWarnedAbout`). Two findings there:
  - Batik 1.20 brings `xml-apis-ext` (the `org.w3c.dom.svg` interfaces) only through `batik-css` and `batik-parser`.
    Past docx4j's exclusions it no longer arrived, and every SVG failed with
    `NoClassDefFoundError: org/w3c/dom/svg/SVGDocument` (13 export-fo-tests errors). `docx4j-export-fo` now declares
    `xml-apis-ext` 1.3.04 itself. The fork's own dependency tree still has it (through `batik-css`, in r50's
    `deps-runtime.txt`), so nothing changes here; the release note warns consumers who exclude.
  - docx4j's `LBP` called `Leader.setRuleStyle(int)` directly, which does not compile against Apache FOP 2.12, where
    FOP-3325 (CR-009 §3.2) is released. It now calls it by reflection everywhere and no longer branches on
    `rule-style-int`. The fork keeps the member and the capability: a name is never removed while its hook exists.
  - Tests: on the fork (built from bb92342d3, classes identical to r50) core-tests 2340/0, export-fo 247/0,
    export-fo-tests 865/0 (9 skipped), digsig 138/0, both bundles built; on Apache FOP 2.12 export-fo 247/0 and
    export-fo-tests 865/0. Gate b299 (that docx4j on r50) against b293: one mover, 5361's clock, events equal.
    b299 is the 2.12 line's baseline (register 7684ccb); the Central jars are gated against it.
- **`2.11-docx4j.9`** (cut 2026-10-30 from `.8`'s version commit, pushed, CI green, nothing on it but docs) never
  ships, like `.3`. Leave it alone; CI's list keeps it.
- **NOTICE** (the root file, packaged into each jar as `META-INF/NOTICE`) says "derived from Apache FOP 2.12" and
  that the changes from 2.12 are listed in the README. Fixed after the gate (r50 carries the 2.11 text), when the
  docx4j session found it on 2026-11-01. A resource only; the classes are r50's.
- **Change notices** keep "derived from Apache FOP 2.11": each says what its file was modified from when it was
  changed. The README's heading "Changes from Apache FOP 2.11" keeps its name because every notice cites it. The text
  under it now names the 2.12 release as the comparison.

## 5. Batik 1.20 and commons 2.12, as they bear on the fork

- `DefaultExternalResourceSecurity` gains a check that the resource's protocol is the document's. It does not close
  the hole `fop/CR-028` closes. Apache FOP 2.12's own binary distribution (sha512 checked) and Apache `main` at
  1301bcf74 both draw CR-028's probe from a file base URI: the `use` fragment, the `tref` text and the `file:` picture,
  inline and external. From an `http:` base, Batik refuses them. So the option still matters. `fop/CR-028` §2
  records the measurement, and a retraction found with it.
- `SVGUseElementBridge` guards against circular references (`xlink.href.circularDependencies`), and
  `SVGColorProfileElementBridge` resolves against the document URL instead of the element's base URI. It still asks
  the user agent. Read in the bytecode, not measured.
- `SVGExternalResourceSecurityTestCase` (CR-028) passes under Batik 1.20, in the full suite below.

## 6. Verification

- CI's command (`mvn -B package checkstyle:check spotbugs:check`) on the line commit's tree, JDK 21: 3936 tests, 0
  failures, 0 errors, 4 skipped; 0 checkstyle violations; 0 spotbugs instances. The `.8` tree had 3934; the two more
  are `PSRendererConfiguratorTestCase`'s JPEG ratio and compress-streams tests, back with FOP-3311 and FOP-3326.
- Gate: r50 to the docx4j session (`~/fop-renderers/r50-CR-030-<commit>/`). A pass is 0 movers on the four corpora
  and the probes against b294 (docx4j on the Central `.8`), every event count equal, 27 hooks. The run must use the
  2.12 dependencies, Batik 1.20 and xmlgraphics-commons 2.12, not 1.19 and 2.11. The core jar alone on `.8`'s
  classpath would leave the PostScript classes linking against the wrong commons, and would not measure Batik 1.20.
  The `FopCapabilities` line warning is expected until docx4j moves `BUILT_FOR_LINE`.
- **PASS 2026-11-01** (the docx4j session; Enterprise attempts.md 3c6dc9b): b298, the r50 jars (sha256 checked) on
  b297's classpath with all nineteen Batik jars at 1.20 and xmlgraphics-commons 2.12, no 1.19 or 2.11 jar left,
  against b297. b297 is b294's jars rescored with docx4j's harness date fix c1e83f95c, and has 0 movers against b295.
  Result: 0 movers on real, real2, real3, real-c2 and the probes; every scoreboard and `classes.txt` identical; every
  event count equal per document (totals linebig 3241, line 17612, clip 1552, bcov 1273, bcovi 514, region 73, body
  19, glyph 63, font 8, other 87). The renderer line: "docx4j-fo-renderer 2.12-docx4j.1-SNAPSHOT", 27 hooks, `.8`'s
  list; the line warning once per JVM. Against b294 itself, one mover, 5361 +4: b294's rendering date, which its old
  harness misread, not the renderer. b298 on r50 is the fork's baseline for later gates. The script is
  `~/fidelity-cr030/gate-b297-b298.sh`, the classpath `~/fidelity-cr030/cp-b298-r50.txt`. docx4j stays on `.8`
  until Jason orders the move, which brings `BUILT_FOR_LINE = "2.12"` and a gate of the Central release.

## 7. Upstream

Nothing to send.
