# CR-028: an SVG graphic loads only the resources embedded in it

Status: BUILT 2026-10-24 on branch `CR-028-svg-external-resources` off `2.11-docx4j.8` (code 63a08b0a2, full suite 3932/0,
checkstyle and spotbugs clean under CI's command; r47 at `~/fop-renderers/r47-CR-028-63a08b0a2/`, sha256 f15ec867...), at Jason's word relayed by the
docx4j session ("harden its SVG handling", after Apache POI's apache/poi#1452). Option `svg-embedded-resources-only`,
on by default in the fork, the twenty-seventh capability. Test `SVGExternalResourceSecurityTestCase` (§5). Gated PASS
2026-10-29 (§8). MERGED 2026-10-29 to `2.11-docx4j.8` on Jason's word, by a merge commit (1cdf26fab) since `.8` already
carried `fop/CR-029`; full suite on the merged tree 3934/0, CI's command clean; unreleased, in
`docs/release-notes/2.11-docx4j.8.md`; the merged tree's jar r49 at `~/fop-renderers/r49-merge-CR-028-1cdf26fab/`
(sha256 eee3cba6...). JIRA and pull request drafted, not filed (§7). Registry key `fop/CR-028`. Not an Enterprise CR-001 §6.6
item: nothing in Word's layout is involved.

## 1. The exposure

Batik loads the external resources an SVG names, the `image`, `use` and `tref` references, `@font-face` sources and
stylesheets, through its user agent's `getExternalResourceSecurity(resourceURL, docURL)`. The default,
`DefaultExternalResourceSecurity` (Batik 1.19, read from the bytecode): a `data:` resource is always admitted; a null
document URL refuses everything else; otherwise the resource is admitted when its host equals the document's host,
and the host of a `file:` URL is null on both sides. So a document whose URL is a file may read any file on the
machine: `<use xlink:href="file:///home/user/private.svg#g"/>` draws that file's fragment,
`<image xlink:href="file:///home/user/photo.png"/>` draws that picture, `<tref>` copies text, into the output.

POI's `SVGUserAgent` extended Batik's `UserAgentAdapter` without overriding this, loaded the SVG with an empty
document URI (a null host too), and so let a slide's SVG read local files. POI's fix (merged 2026-10-20): the user
agent returns `EmbededExternalResourceSecurity(resourceURL)`, which admits `data:` URLs only, and a refused picture is
skipped rather than failing the slide; its test `TestSVGImageRenderer`.

## 2. FOP today

Three user agents, none overriding the rule: `SimpleSVGUserAgent` (extends `UserAgentAdapter`; used by
`ImageConverterSVG2G2D` for an external SVG graphic on a renderer without native SVG, and by `PreloaderSVG` to size
one), `SVGUserAgent` (extends it, with the FO user agent's events; used by `AbstractGenericSVGHandler` for inline SVG
on every renderer and by `PDFImageHandlerSVG` and `PSImageHandlerSVG` for both inline and external SVG) and the
transcoders' `FOPTranscoderUserAgent` (Batik's `SVGAbstractTranscoderUserAgent`, which has its own
`KEY_ALLOW_EXTERNAL_RESOURCES` hint).

The document URL Batik tests is the FO's base URI for an inline SVG (`SVGElement.getDimension` sets it from the
resource resolver's base URI; `BatikUtil.cloneSVGDocument` carries it onto the clone) and the file's own URI for an
external SVG graphic. The command line and most embeddings lay out from a file base URI. Measured here on the fork at
f436bf7cd with a probe FO carrying `use`, `tref` and `image` references to files in another directory, in an inline
SVG and in an external one (scratch `svg/doc/probe.fo`):

| base URI | inline SVG | external SVG graphic |
|---|---|---|
| the FO's directory (`file:`) | the fragment, the text and the picture all drawn | all drawn |
| `http://example.com/` | the whole SVG refused at the `use`, `svgNotBuilt` | not resolved |
| `PDFTranscoder` on the external SVG | | all drawn, with and without `KEY_ALLOW_EXTERNAL_RESOURCES` |

A second cause, FOP's own: `AbstractFOPImageElementBridge.createImageGraphicsNode` (the `image` bridge of
`PDFBridgeContext`, `PSBridgeContext`, `GenericFOPBridgeContext`) loads the picture through FOP's image manager and
never calls `checkLoadExternalResource`, which Batik's own `SVGImageElementBridge` calls first; only its fallback to
the superclass does. So on the PDF and PostScript paths an SVG `image` was admitted whatever the user agent's rule,
and whatever the document URL.

docx4j's exposure today: none found by the docx4j session. docx4j never hands a document's own SVG to the renderer;
the SVG it renders is its own (metafiles replayed as SVG with their bitmaps as `data:` URLs, charts). It becomes
reachable when docx4j adds SVG picture support (docx4j issue #699), so this is that work's precondition.

## 3. The change

One option, `svg-embedded-resources-only`, in the factory configuration like `table-border-overpaint`:
`FopFactoryConfig.isSvgEmbeddedResourcesOnly()` (default `DEFAULT_SVG_EMBEDDED_RESOURCES_ONLY`, true in the fork),
`FopFactoryBuilder.setSvgEmbeddedResourcesOnly(boolean)`, the configuration element
`<svg-embedded-resources-only>`, read by `FopConfParser`; `FopFactory.isSvgEmbeddedResourcesOnly()` public and
`FOUserAgent.isSvgEmbeddedResourcesOnly()` delegating to it.

- `SimpleSVGUserAgent.setEmbeddedResourcesOnly(boolean)`: when set, `getExternalResourceSecurity` returns Batik's
  `EmbededExternalResourceSecurity(resourceURL)`, `data:` only; otherwise the superclass's default. The constructors
  are unchanged (docx4j subclasses no SVG class, but the signatures stay).
- `SVGUserAgent` sets it from the FO user agent in its constructor.
- `ImageConverterSVG2G2D.createBatikUserAgent(hints)` sets it from the factory behind the image session context in
  the hints (`getParentContext()` is the `FopFactory`), so the external-graphic path on a Java2D-based renderer
  follows the same option without a change to the five places that build image hints.
- `AbstractFOPImageElementBridge.createImageGraphicsNode` calls `checkLoadExternalResource(purl, docURL)` before
  loading, exactly as Batik's bridge does, and throws Batik's `BridgeException` with `ERR_URI_UNSECURE` on refusal.
  This applies under either rule: with the option off, a `file:` picture in an SVG whose document has an `http:` URL
  is now refused on the PDF path as Batik refuses it elsewhere.
- `PreloaderSVG`'s user agent only sizes the document; no resource is loaded through it, so it is left.
- The transcoders are left on Batik's rule and hint: their input is an SVG file whose relative pictures are the
  normal case, and docx4j does not use them. Recorded as open.

A refusal is reported as the existing `svgNotBuilt` event (ERROR, not fatal), with Batik's message naming the URL,
and the graphic is not drawn; the page is. POI skips the one picture: in FOP a refused `use` or `tref` stops Batik's
GVT build anyway, so the whole graphic going is the consistent outcome, and docx4j's SVG never has such a reference.

Capability `svg-embedded-resources-only` (`Docx4jFop.SVG_EMBEDDED_RESOURCES_ONLY`), the twenty-seventh, so that
docx4j's `FopCapabilities` can tell a renderer that refuses external SVG resources from one that does not, when
docx4j issue #699 makes the question live.

Two of Apache's own tests reference pictures from SVG otherwise than as `data:` URLs and run with the option off:
`PDFAConformanceTestCase` (`svg-with-transparent-image.fo`, a PNG relative to the FO; `fop/test/test.xconf` now says
`<svg-embedded-resources-only>false</svg-embedded-resources-only>`) and `URIResolutionTestCase` (an SVG `image` by a
custom scheme, resolved by the test's resolver; set on its builder). Both pass.

Not in scope: external entities in the SVG's XML (Batik's parser, the FO's parser), scripts (FOP builds a static GVT
tree; Batik runs none without a scripting environment), and the `fo:external-graphic` or `fo:basic-link` URLs of the
FO itself, which FOP's resource resolver governs.

## 4. Measured

The command-line probe of §2, re-run on the branch (each of its two SVGs carries all of `use`, `tref`, the `file:`
picture, a `data:` picture and inline text): with the default both SVGs are refused at the `use` and reported as
`svgNotBuilt`, so the page carries the FO's text alone and no picture; with
`<svg-embedded-resources-only>false</svg-embedded-resources-only>` in the configuration the text layer and the four
pictures are those of f436bf7cd. The test of §5 puts each reference in an SVG of its own, which is how the `data:`
and local rows are seen to survive.

## 5. Tests

`org.apache.fop.svg.SVGExternalResourceSecurityTestCase`, five tests, in POI's style: a temporary directory holds a
red 100 by 20 PNG, an SVG with a blue fragment, five external SVGs and an FO laid out from that directory, with ten
rows of 100 by 20 points, inline then external: a plain rectangle, a `data:` image, a `use` of a fragment in the
same document, a `file:` image, a `use` of the other file's fragment. The page is rendered to PDF (read back through
PDFBox's renderer; FOP's own image bridge) and to PNG (the Java2D renderer; Batik's bridges and the converter), and
each row's centre pixel is read. By default rows 3, 4, 8 and 9 are blank and the rest drawn, with four `svgNotBuilt`
events on the PDF path; with the option off all ten are drawn and no event is raised, which is the bypass. The fifth
test checks the user agent alone: Batik's rule admits a `file:` resource beside a `file:` document, the option
refuses it, `data:` passes under both. `Docx4jHooksTestCase` counts twenty-seven capabilities.

Full `fop-core` suite under CI's command (`mvn -B package checkstyle:check spotbugs:check`): 3932 tests, 0 failures,
4 skipped; 0 checkstyle violations; 0 spotbugs instances.

## 6. docx4j

Nothing to write and nothing to set: the fork's default is the hardened one, and docx4j's SVG is `data:` only. A
consumer whose SVG references pictures relative to the FO sets the option off in its configuration or on the
builder. `FopCapabilities` may read the capability for docx4j issue #699. docx4j-export-fo picks the change up with
the next released fork version, `2.11-docx4j.8`.

## 7. Upstream

The same code with the default off, so that Apache FOP's behaviour is unchanged until a user opts in; whether a
later major release should turn it on is Apache's call and the JIRA says so. The `image` bridge check applies under
the default too, which is the one behaviour change: a picture Batik's own bridge would refuse (a `file:` picture in an
`http:` document) is now refused by FOP's bridge as well. JIRA text and pull request description in
`docs/upstream/svg-external-resources.txt`, to be filed on Jason's word; the upstream branch is cut against Apache
`main` in `../fop-upstream-wt` when the number exists.

## 8. Gate (the docx4j session)

Pass: 0 movers on the four corpora and the probes, every event count equal. docx4j's SVG carries only `data:`
references, so a mover would mean the option reached something it should not have (a chart's font, a metafile's
bitmap). Handed over as the jar's path and sha256 under `~/fop-renderers/`.

PASS 2026-10-29 (the docx4j session; its register's attempts.md 1a9e336): control b285 (docx4j bc842ac66 on the
released `.7` core) against candidate b287 (the same jars on r47, sha256 checked; 27 hooks against b285's 26, so the
capability is on), over real, real2, real3, real-c2 and the probes: 0 movers, every event count equal. No corpus
document references an external SVG resource, so nothing was refused: the option costs nothing where it does not
fire.
