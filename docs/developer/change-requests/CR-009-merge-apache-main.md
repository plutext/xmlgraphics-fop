# CR-009: merging Apache `main` into the fork, and the triage that comes first

Status: PROPOSED 2026-10-03. The triage (§2 to §5, §8) is done, read-only. Nothing is merged. The merge
goes on a new branch (Jason, 2026-10-03), whose name is his decision (§6). Registry key `fop/CR-009`.

After the merge the fork is Apache `main` plus fixes, not Apache FOP 2.11 plus fixes. Apache has
released nothing after 2.11, and its `main` still declares `2.11.0-SNAPSHOT`.

## 1. Scope and method

`main` at ab5d6eba6 (2026-09-29) against 64c46d19f, the merge base of the 2.11 tag: 91 non-merge
commits; 233 main-source files, +3576 / -1558. docx4j's CR-020 §9.4 grouped 89 of them by subject on
2026-09-19. This triage reads them commit by commit.

Four readers each took about 23 commits, oldest first. Each read every main-source hunk (test and
build hunks skimmed) against one rubric:
- what the commit does;
- whether it can move layout;
- its effect on what docx4j subclasses, calls or reads by reflection (`LBP.java`);
- overlap with the fork's own changes, textual or semantic;
- the Enterprise CR-001 §6.6 items it touches;
- the FO construct a gate would need.

The five findings in §3 that change the plan were then checked by hand against the code, and each is
marked so. Anything a reader inferred and nobody checked is marked unverified.

A dry-run merge (`git merge-tree`) of `upstream/main` into `2.11-docx4j.3` conflicts textually in five
files: `.github/workflows/maven.yml`, `pom.xml`, `fo/Constants.java`, `TextLayoutManager.java` and
`GlyfTableTestCase.java`. Every file CR-001 to CR-008 touched auto-merges apart from those two source
files.

## 2. Verdicts

| verdict | commits | meaning |
|---|---|---|
| TAKE | 75 | Inert for docx4j or clearly safe: build, CI, other output formats, memory, opt-in options, constructs docx4j never emits. Seven carry a check (tagged output, hyphenation, a sectioned document, one test expectation). |
| RESOLVE | 11 | A conflict or a build decision. Eight are build files (`pom.xml`, `maven.yml`, scm, the snapshot repository, the checkstyle and spotbugs upgrades). Two are code: `Constants` (§3.5) and `TextLayoutManager` (keep both method blocks). One cannot be built from released dependencies (§3.1). |
| GATE | 3 | Behaviour docx4j will see: the image cache (§3.4) and two PDF/UA table-tagging changes. |
| DOCX4J | 2 | docx4j code must change or be checked: `setRuleStyle(int)` removed (§3.2) and the letter-space count (§3.3). |

## 3. The findings that decide the plan

### 3.1 `main` builds on SNAPSHOT dependencies, and one commit needs them (checked)

- `main`'s root pom pins `xmlgraphics-commons` `2.11.0-SNAPSHOT` and Batik `1.18.0-SNAPSHOT`. The 2.11
  release and the fork use the released `2.11` and `1.19`.
- Maven Central has no `xmlgraphics-commons` after 2.11 and no Batik after 1.19 (metadata read
  2026-10-03; both last updated 2025-05-06).
- A release to Central cannot depend on a snapshot, so the fork keeps 2.11 and 1.19.
- FOP-3311 (0eb213c0f) calls `PSGenerator.setJPEGCompressionRatio` and
  `ImageEncodingHelper.createRenderedImageEncoder(RenderedImage, ...)`. `javap` on the released
  `xmlgraphics-commons-2.11.jar` finds no JPEG member on `PSGenerator`. It is PostScript output, which
  docx4j never uses. Its PostScript hunks are left out of the merge, or adapted. The build is what
  says whether anything else needs post-2.11 API; one reader suspects `PDFAMetadataTestCase`
  (7c028337d, unverified).

### 3.2 FOP-3325 removes a method docx4j calls (checked)

3791b9253 deletes `RuleStyle`. `area.inline.Leader` keeps only `setRuleStyle(BorderStyle)` and
`setRuleStyle(String, int)`. docx4j's `LBP.java:535` calls `rule.setRuleStyle(fobj.getRuleStyle())`
with an `int`, so:
- docx4j-export-fo would not compile against the merge;
- docx4j 17.3.0's jar on a merged fork would throw `NoSuchMethodError` the first time it builds a
  rule leader.

docx4j must still compile against Apache FOP 2.11, where `int` is the only form, so it cannot simply
switch. Proposed: the fork keeps `setRuleStyle(int)` as a compatibility member delegating to
`BorderStyle.valueOf(int)`, listed with the hooks. This bears on the version name (§6): a fork that
docx4j 17.3.0 cannot run on is not the same line.

### 3.3 FOP-2722 counts letter spaces on the complex-script path without adding their width (checked by reading; not measured)

2a8efc165 makes `GlyphMapping.processWordMapping` return a letter-space count of n-1 (n before a
non-space break), where it returned 0. It adds nothing to the word's width. The plain path,
`processWordNoMapping`, adds `letterSpaceIPD × count` to the width (`wordIPD.plus(...)`). So on `main`
the two paths disagree, and §6.6 item 16 is still there: touched, not closed.

docx4j's workaround, `WordLineLayoutManager.fixLetterSpaces`, adds `wordLength - letterSpaceCount`
letter spaces to the width. That assumes the counted ones are already in it, which holds on the plain
path. After the merge it would add 1 where n are missing; a word whose count already equals its length
(CJK, `-`, `/`) would be skipped. Letter-spaced lines would overrun again, as in item 16.

Proposed: the fork adds `letterSpaceIPD × count` to the width in `processWordMapping`, as the plain
path does. That is item 16's "two-line fix", an upstream JIRA in its own right. It makes docx4j's
assumption true on both paths, so the workaround adds one space, Word's trailing one. It would be its
own change request, gated on the `spacing-char` probe and the corpus's letter-spaced runs. The
alternative is a docx4j-side change that stops reading the count. Either way the merge is not gated
without one.

### 3.4 FOP-3293 caches images per `FopFactory`, on by default (checked)

401897a35 adds a `FopFactory`-wide PDF image cache (`imageCache = true`). Through it,
`PDFPainter.drawImage` reuses a `PDFImage` from an earlier document. docx4j's
`FORendererApacheFOP` uses one factory for both passes of a two-pass conversion (`:123` to `:179`):
pass 1 to a null stream, pass 2 to the output. Every "Page X of Y" document therefore draws pass 1's
image objects in pass 2. A caller who reuses a factory, which docx4j supports, shares them across
documents. One reader questions the PNG alpha path and concurrent use (both unverified). Each image's
encoded bytes are now held as long as its XObject is.

Gate it, or have docx4j turn it off: `FopFactoryBuilder.setImageCache(false)`, or `<image-cache>` in
the configuration.

### 3.5 A property-id collision (checked)

786d73c58 (FOP-3327) adds `PR_X_RULE_STYLE = 295`, the id the fork's `PR_X_GSUB_FEATURES` (CR-001)
already has. `PROPERTY_COUNT` is 295 in both. Left at one id, the later `addPropertyMaker` would take
the slot silently. Resolve as `PR_X_RULE_STYLE = 295`, `PR_X_GSUB_FEATURES = 296`,
`PROPERTY_COUNT = 296`. docx4j probes `gsub-features` by name, not by id, so it is unaffected.

### 3.6 Build tooling

- ec1cc67e6 moves checkstyle to plugin 3.6.0 with a new configuration, and removes the blank line
  before static imports across about 250 files.
- 38fbe9421 moves spotbugs to 4.8.6.7 on JDK 11 and later. b2b5b0ca6, ae5289ad7 and 2ee873283 drop
  exclusions.
- The fork's own code must pass both. A reader expects nine fork-added test files to fail
  `ImportOrder` (unverified until the build runs) and found no fork main code using the newly
  flagged patterns.
- e5bd590c4 adds Apache's snapshot repository and a `publish-snapshot.yml` for `main`. The fork keeps
  its own `distributionManagement`; the workflow is inert on the fork's branches and can be dropped.

## 4. Enterprise CR-001 §6.6

The merge closes no §6.6 item.

- **16:** touched, not closed (§3.3).
- **22:** touched, not narrowed. FOP-3270 finds country-specific patterns; FOP-2880 paints a soft
  hyphen, which never reaches docx4j's painting; FOP-3332 checks the classes in a hyphenation tree.
- **27:** GI-9484 clamps a leader's optimum to its maximum (a crash path docx4j's leaders cannot
  reach). FOP-3325, FOP-3306 and FOP-3327 change how rules are painted, not where leaders are placed.
- **18, 20, 21, 24, 25:** no commit touches their sites. FOP-3253 and FOP-3256 change only the
  non-balancing branch of the last-page redo, not `BalancingColumnBreakingAlgorithm`.

What the merge brings is the rest:
- memory: traits, the structure tree, `MinOptMax`, image caching;
- accessibility: tagging past the first page-sequence in the artifact case, footnote and table
  tagging, link `/Contents`;
- security: temp files, SVG glyph DTDs, hyphenation deserialisation;
- Java 25.

It also cuts the distance to our twelve open pull requests, which are cut against `main`.

## 5. The gate partition

| class | commits | probe or corpus |
|---|---|---|
| letter-spaced text in a font FOP shapes | FOP-2722 | after §3.3's fix: `spacing-char`, `kern-title`, the corpus's `w:spacing` runs |
| two-pass documents with raster images | FOP-3293 | a NUMPAGES document with an RGBA PNG and a JPEG, cache on and off, image XObjects and soft masks compared |
| leaders | FOP-3325, FOP-3306, FOP-3327, GI-9484 | TOC and tab leaders (dot, hyphen, underscore, rule); docx4j's rule leaders through `LBP.ruleArea` |
| tagged output | FOP-3165, FOP-3283, FOP-3264, FOP-3122, FOP-3306, FOP-3322 | veraPDF or PAC on a footnoted document with a repeated, spanned table header and links |
| hyphenation | FOP-3332, FOP-3270 | docx4j's `HyphenationTest` |
| page masters | FOP-3181 | a sectioned document; expected unmoved, since docx4j keeps the body width constant within a section |
| everything else | | corpora glyph-identical; any other mover is a finding |

## 6. The branch and the version (Jason's decision)

Recommended: `2.11-docx4j.4`, on a branch of that name cut from `2.11-docx4j.3`. Not `main-docx4j.1`,
and not `2.12-docx4j.1`. The reasons, each measured on 2026-10-03:

- Maven's own comparator (`maven-artifact` 3.9.16) orders `main-docx4j.1` below `2.11-docx4j.3`. Every
  tool that compares versions would read it as a downgrade.
- Apache's `main` declares `2.11.0-SNAPSHOT`: by Apache's own naming it is still the 2.11 line, and the
  next release is unnamed.
- docx4j's `FopCapabilities` takes the line (major.minor) from the renderer's version and warns when it
  is not `BUILT_FOR_LINE = "2.11"`. Under docx4j 17.3.0, `2.12-...` would warn at every start-up and
  `main-...` would give an unknown line.
- Metanorma's fork, tracking `main` past 2.11, released as `v2.11.1` to `v2.11.5`.

What changes is the wording: the README's "derived from Apache FOP 2.11" becomes "derived from Apache
FOP's `main` after 2.11, at commit X", and `.4`'s release notes lead with the change of base.

The condition: docx4j's line check exists because it subclasses 2.11 internals, and `main` changes some
of what it uses (§3.2, §3.3). Keeping "2.11" is honest only if docx4j 17.3.0 still runs on the merged
fork, which is what §3.2's compatibility member and the gate establish. If it cannot be made to, that
is the real case for a new line, with a docx4j release to match.

`2.11-docx4j.3` (2.11 base plus CR-008) stays releasable on its own as the last 2.11-based version, if
wanted.

## 7. The merge, once the name is settled

1. Cut the branch; merge `upstream/main`.
2. Resolve the five conflicts:
   - `maven.yml`: the fork's branches, the newer actions, JDK 25 optional;
   - `pom.xml`: the fork's coordinates, `scm` and `distributionManagement`; released xmlgraphics-commons
     and Batik; both the fork's `release` profile and upstream's `spotbugs-java11`;
   - `Constants`: §3.5;
   - `TextLayoutManager`: keep `getLetterSpaceAdjustment` beside the `inline-access` methods;
   - `GlyfTableTestCase`: keep both sets of tests.
3. Leave out or adapt FOP-3311's PostScript hunks (§3.1).
4. Add `setRuleStyle(int)` (§3.2).
5. §3.3's width fix as its own fork change request with a JIRA, or the docx4j-side alternative. Agree
   it with the docx4j session first.
6. Full build with `main`'s checkstyle and spotbugs; fix the fork's code where they object.
7. The docx4j session compiles docx4j-export-fo against the snapshot, decides on §3.4, then gates per
   §5.
8. The README's base wording, the hooks table (`setRuleStyle(int)`), and the release notes.

## 8. Commit by commit (oldest first)

"Kind" is the class of change; "tagged check" means a PDF/UA validator run, not a corpus gate.

| commit | date | JIRA | kind | verdict | note |
|---|---|---|---|---|---|
| e5fd6c289 | 2025-04-09 | FOP-3252 | LAYOUT (new FO only), tagging | TAKE | fo:table-and-caption implemented; docx4j never emits it |
| 5f6ed6cd7 | 2025-04-28 | - | build | TAKE | transcoder assembly |
| cc6c162fc | 2025-05-08 | FOP-3253 | LAYOUT | TAKE | last-page redo, non-balancing branch; docx4j sets no last/only master |
| b2b5b0ca6 | 2025-05-12 | - | build | TAKE | explicit UTF-8; a spotbugs exclusion dropped |
| ae5289ad7 | 2025-05-12 | - | API (nested classes static) | TAKE | incl. LeafNodeLM.AreaInfo; LBP.java:288 reflection by name survives |
| ab76f7336 | 2025-05-23 | FOP-3256 | LAYOUT | TAKE | follow-up to FOP-3253 |
| 2ee873283 | 2025-06-04 | - | memory | TAKE | try-with-resources in ImageRawPNGAdapter |
| efb7175d2 | 2025-04-25 | FOP-3251 | PDF | TAKE | URI action ids by MD5: colliding URIs no longer share a link action |
| df76c09b8 | 2025-06-06 | FOP-3257 | error path | TAKE |  |
| afeb4d730 | 2025-06-13 | FOP-3258 | PDF | TAKE | link alt text encrypted |
| e5bd590c4 | 2025-06-19 | - | build | RESOLVE | keep the fork distributionManagement; Apache snapshotRepository would merge in silently |
| 25bb71648 | 2025-06-20 | - | refactor | TAKE | StandardCharsets |
| 1620aac43 | 2025-07-09 | FOP-3261 | API, PDF (merge-fonts) | TAKE | removes PDFDocument.is/setMergeFontsEnabled; no callers in docx4j or the fork |
| b077abbad | 2025-07-15 | FOP-3264 | tagging | TAKE + tagged check | footnote Reference/Note, Note gets /ID |
| 35a432760 | 2025-07-23 | FOP-3181 | LAYOUT, API | TAKE + sectioned check | restart at LM on IPD change (new default); docx4j keeps IPD constant per section |
| 062592dc9 | 2025-08-05 | FOP-3087 | other format | TAKE | transcoder |
| 12e38b076 | 2025-08-15 | - | build | TAKE | bouncycastle, provided |
| 71e5bfc71 | 2025-08-27 | FOP-3268 | other format | TAKE | AFP |
| 2971201e3 | 2025-08-27 | FOP-3269 | memory (tagging) | TAKE |  |
| ea5d9b585 | 2025-08-11 | FOP-3245 | tagging | TAKE | fox:external-document; not emitted by docx4j |
| aed8a7212 | 2025-09-02 | FOP-3270 | LAYOUT (hyphenation) | TAKE | country-specific pattern lookup; item 22 touched |
| 794684aa3 | 2025-09-02 | FOP-2880 | API, PDF | TAKE | IFPainter.supportsSoftHyphen; U+00AD never reaches docx4j painting |
| 6779aa1ec | 2025-09-16 | FOP-3272 | memory | TAKE | IF structure tree recorder |
| f38d7bab4 | 2025-09-23 | FOP-3275 | test | TAKE |  |
| 38fbe9421 | 2025-09-25 | - | build | RESOLVE | spotbugs-java11 profile; pom conflict beside the fork release profile |
| 9ac1de192 | 2025-09-08 | FOP-3165 | tagging | GATE (PDF/UA) | THead/TBody/TFoot under PDF/UA; supersedes P2-5 tree half |
| 63e8425c7 | 2025-10-02 | - | test | TAKE |  |
| 8736bba83 | 2025-10-10 | FOP-3278 | other format | TAKE | PostScript |
| 1acd5d519 | 2025-10-10 | FOP-3277 | other format | TAKE | transcoder |
| a8fb5df66 | 2025-10-13 | FOP-3280 | memory | TAKE | MinOptMax ZERO shared, hashCode changes; no identity or hash use in docx4j |
| e0fca8b60 | 2025-10-13 | FOP-3277 | test | TAKE |  |
| 3de713f97 | 2025-10-16 | FOP-3280 | memory | RESOLVE | letterSpaceAdjustArray lazy; TLM conflict beside inline-access methods: keep both |
| c0b02f0e2 | 2025-10-19 | - | ASF infra | TAKE |  |
| 36c3a3907 | 2025-10-22 | FOP-3279 | LAYOUT (new values) | TAKE | force-page-count doubly-*; not emitted |
| fb908e19a | 2025-10-27 | FOP-3273 | tagging, API | TAKE | tagging no longer stops after the first page-sequence (role=artifact case) |
| ec1cc67e6 | 2025-11-03 | FOP-3281 | build | RESOLVE | checkstyle 3.6.0 / new config; fork code must pass it |
| bba27e4a3 | 2025-11-03 | FOP-3281 | build | TAKE |  |
| 1b847a157 | 2025-11-03 | FOP-3281 | build | TAKE |  |
| 1927bfae9 | 2025-11-14 | FOP-3284 | security | TAKE | temp files owner-only; needs f4a2e2a70 |
| f4a2e2a70 | 2025-11-14 | FOP-3284 | security | TAKE |  |
| d1fe1f19f | 2025-11-26 | FOP-2763 | LAYOUT | TAKE | retrieve-table-marker; not emitted |
| c434f642f | 2025-12-05 | FOP-3287 | other format | TAKE | AFP |
| a4da655e8 | 2025-11-18 | FOP-3283 | tagging | GATE (PDF/UA) | Scope on spanned TH; a TH may get Column and Row (unverified) |
| 4438d40a1 | 2025-12-09 | - | build | RESOLVE | keep the fork scm |
| 9cdb7a3dd | 2025-12-12 | - | build | RESOLVE | keep the fork scm |
| 1d1c280ba | 2025-12-17 | FOP-3288 | other format, API | TAKE | AFP character-set caches per factory |
| d5ea4d1d8 | 2025-12-12 | FOP-3282 | tagging (opt-in) | TAKE |  |
| c5f55eb20 | 2026-01-08 | FOP-3286 | PDF | TAKE | border radius order |
| 6d2fb2d92 | 2026-01-22 | FOP-3291 | PDF | TAKE | keyless images not cached |
| 0e03314aa | 2026-02-04 | - | test | TAKE |  |
| 401897a35 | 2026-02-09 | FOP-3293 | PDF, memory, API | GATE + DOCX4J | FopFactory image cache ON by default; docx4j two-pass shares the factory |
| 5bf1e1f9b | 2026-02-09 | FOP-3293 | test | TAKE |  |
| 28193786d | 2026-02-11 | FOP-3293 | test | TAKE |  |
| 4eb63043a | 2026-01-30 | FOP-3292 | PDF | TAKE | PDF names escaped as UTF-8 |
| 207ed8ec0 | 2026-02-26 | FOP-3298 | security | TAKE | no DOCTYPE in OpenType-SVG glyphs |
| 0c7d1c4eb | 2026-02-25 | FOP-3122 | PDF (tagging) | TAKE + tagged check | trailer objects drained; late objects now written |
| 3d6e7c5d6 | 2026-03-04 | FOP-3290 | PDF (opt-in) | TAKE |  |
| 89a2564fe | 2026-03-05 | FOP-2872 | SVG | TAKE | em units on an SVG root |
| e355d4ea8 | 2026-03-24 | FOP-3299 | signing | TAKE |  |
| de35736ea | 2026-03-26 | FOP-3302 | servlet | TAKE |  |
| 2a31b84c8 | 2026-04-08 | FOP-3307 | LAYOUT (opt-in), API | TAKE | parent IPD image scaling, off by default |
| 5cfa5e1ed | 2026-04-16 | FOP-3308 | build | TAKE | bouncycastle, provided |
| 7ecc17aa6 | 2026-04-28 | FOP-3309 | PDF | TAKE | first XMP only |
| b8644fdad | 2026-05-06 | GI-9484 | LAYOUT (crash path) | TAKE | leader opt clamped to max; item 27 touched, placement unchanged |
| 0eb213c0f | 2026-05-06 | FOP-3311 | other format | RESOLVE: cannot build | PS JPEG ratio: needs xmlgraphics-commons API absent from released 2.11 |
| 3a91307cb | 2026-05-13 | - | ASF infra | TAKE |  |
| 1a3d8629c | 2026-05-13 | - | ASF infra | TAKE |  |
| af2c226bb | 2026-05-13 | - | ASF infra | TAKE |  |
| b042e058a | 2026-05-13 | - | ASF infra | TAKE |  |
| 0b9b52aad | 2026-03-25 | FOP-2758 | error path | TAKE |  |
| 58b4c79f7 | 2026-05-18 | FOP-3317 | build | TAKE | qdox scope |
| 32930c75a | 2026-05-15 | FOP-3316 | tagging (opt-in) | TAKE |  |
| 7c028337d | 2026-05-19 | FOP-3304 | test | TAKE (check) | PDFAMetadataTestCase expectation may need XGC snapshot (unverified) |
| 631200f5c | 2026-05-22 | - | build | RESOLVE | maven.yml: take actions v6, keep fork branches |
| 32c8c7176 | 2026-05-22 | - | build | RESOLVE | maven.yml: setup-java v5 |
| 6361eaf65 | 2026-05-28 | FOP-3306 | PDF (paint) | TAKE | dotted rule leaders; docx4j sets no rule-style |
| ece9fa285 | 2026-06-03 | FOP-3321 | other format | TAKE | PostScript |
| 73ebfb18d | 2026-06-05 | FOP-3306 | tagging | TAKE + tagged check | rule as Artifact whenever accessibility is on |
| 1b7d4bae8 | 2026-06-19 | FOP-3326 | other format | TAKE | PostScript |
| 8e4a19cf2 | 2026-06-18 | FOP-3323 | error path | TAKE | fo:title id NPE |
| 5cae49dee | 2026-05-29 | FOP-3322 | tagging | TAKE + tagged check | link /Contents from /Alt |
| 3791b9253 | 2026-06-04 | FOP-3325 | API, PDF | DOCX4J + RESOLVE | removes Leader.setRuleStyle(int), called by docx4j LBP.java:535 |
| 7a164b172 | 2026-08-06 | FOP-3332 | security | TAKE + hyphenation check | hyphenation tree classes verified |
| 39efab15e | 2026-08-06 | FOP-3332 | build | TAKE |  |
| 7bf115c4a | 2026-08-07 | - | build | TAKE | bouncycastle, provided |
| 786d73c58 | 2026-07-20 | FOP-3327 | API, PDF | RESOLVE | PR_X_RULE_STYLE = 295 collides with the fork PR_X_GSUB_FEATURES = 295 |
| f84f61fe5 | 2026-08-19 | FOP-3333 | memory, API | TAKE | area traits deduplicated; getTraits() may be unmodifiable; docx4j never reads it |
| 0fcf99423 | 2026-08-19 | FOP-3333 | memory | TAKE |  |
| 2a8efc165 | 2026-07-08 | FOP-2722 | LAYOUT | DOCX4J + GATE | letter-space count on the complex-script path, width unchanged: item 16 touched, docx4j fixLetterSpaces under-measures |
| feb2323ca | 2026-09-25 | FOP-3337 | other format | TAKE | AFP; adds MultiByteFont.hasPrivateUseSubstitutions beside CR-002 |
| ab5d6eba6 | 2026-09-29 | FOP-3275 | runtime, build | RESOLVE | maven.yml JDK 25; SAX parsing of event models |
