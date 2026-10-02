# CR-006: a capability for the shared-glyph ToUnicode fix, so docx4j can lift its CJK exclusion

Status: DONE 2026-10-01, merged to `2.11-docx4j.2` after the docx4j session confirmed the name is what its
`FopCapabilities` probe reads; unreleased. The constant, the test line and the README rows of §3. No FOP
behaviour changes, so no fork gate; docx4j's own gate for flipping its default follows the release (§4).
Registry key `fop/CR-006`. Not upstream-bound: the fix it names is
(`FOP-cjk-radical-tounicode`, JIRA drafted in its commit message); the capability is the fork's.

Enterprise CR-001 §6.6 item 26.

## 1. What the fix promises

The fix is on `2.11-docx4j.2` since phase 0 (d3dbe8ad9, released in `2.11-docx4j.1`) and survived
CR-002's rewrite as `MultiByteFont.findUnsubstitutedCharacter`: for a glyph the substitution left
alone, whose `CharAssociation` covers exactly one character and whose cmap maps that character to
this glyph, ToUnicode carries that character. Without it `findCharacterFromGlyphIndex` hands back the
lowest code point mapped to the glyph, which for a CJK font is the Kangxi radical (U+2F63 for U+751F)
and for Tinos U+2009 for U+202F. Painted right, extracted wrong.

What it does not promise, so that docx4j does not over-read it: a substituted glyph's entry is
CR-002's business (its own promise, no capability, since docx4j keeps the `+noliga` twin for fidelity
reasons); the second glyph of a one-character cluster keeps its private-use code point (CR-002 §10.2).

## 2. Why a capability

docx4j's workaround is per font and costs rendering: `FopConfigUtil.mustNotUseOpenTypeLayout` declares
`advanced="false"` for any font whose cmap aliases a radical-block glyph onto an ideograph
(`GlyphCheck.reverseLookupTakesACjkRadical`), and `WordWidthsFontCollection` honours it on the PDF
path (the docx4j session confirmed 2026-09-27 that its collection passes `useComplexScripts &&
getAdvanced()`, where FOP's stock collection ignores the per-font flag, CR-003 §10). With the tables
off, the font gets no GSUB or GPOS at all: no `kern` for Latin in a CJK font, no `ccmp`, no `vert`. The
property `docx4j.convert.out.fo.cjkAdvancedFeatures=true` restores them; the docx4j session wants to
default it on under the fork, which needs a probe, which is what a capability is.

## 3. The design

- `Docx4jFop.SHARED_GLYPH_TOUNICODE = "shared-glyph-tounicode"`, added to the capability set, in the
  order of the table. Named for the defect it closes, like `glyf-empty-glyph`, since it is a fix
  with no member of its own; the string is the contract.
- `Docx4jFopTestCase.testCapabilitiesAreReadOnly` gains `assertTrue(Docx4jFop.has(SHARED_GLYPH_TOUNICODE))`.
- README hooks table row: `shared-glyph-tounicode` | (the Kangxi radical fix above) | docx4j may leave
  a CJK font's layout tables on (`cjkAdvancedFeatures` defaults to true) and drop
  `mustNotUseOpenTypeLayout`; without it, a font whose cmap shares a glyph between a radical and an
  ideograph must be declared `advanced="false"` or its text layer reads as radicals.
- README changes table: the radical row gains "capability `shared-glyph-tounicode`".
- No behaviour change in FOP; the fix is already in. So no fork gate for the capability itself.

## 4. What docx4j gates when it defaults `cjkAdvancedFeatures` on

Item 26 already says it: a rendering change, a batch item with probes. Turning the tables on for
CJK fonts gives them FOP's default script processor (there is no CJK processor): `liga`, `ccmp` on
the GSUB side, `kern`, `mark`, `mkmk` on the GPOS side, under the language rules CR-003 sets. Movers
to expect: Latin text in a CJK font on a `+kern` run (Source Han Sans carries Latin kerning), `ccmp`
in fonts that decompose, and nothing vertical (FOP applies `vert` only in vertical writing mode,
which docx4j does not emit). The text-layer assertion is the one the docx4j session already measured
on one document: 0 radicals with the tables on under the fork, 156 on Apache FOP.

## 5. Order

After CR-003's re-gate, since the movers above depend on CR-003's language fallback and kerning
flag; and after Jason's yes. Two commits then: the constant and test here, the docx4j default there.

## 6. docx4j's gate, 2026-10-02: PASS, the workaround lifted by default

Run by the docx4j session on the released `2.11-docx4j.2` from Central. First a release check: the
Central jars with the property unset reproduce the 2026-09-30 snapshot scoreboards exactly (real, real2,
real3 and probes all 0 changed). Then `cjkAdvancedFeatures=true`: the flag reaches only the fonts
`GlyphCheck.reverseLookupTakesACjkRadical` names, here Source Han Sans CN, JP and KR (Droid Sans
Fallback has radical glyphs of its own). Three corpus documents and two probes name such a face; all
identical with the tables on: same PDF sizes, same glyph sequences and positions, same text, Kangxi
count 0 both sides. None of them sets a Latin letter in Source Han Sans, and the tables do nothing to
horizontal CJK text of the font's own region. A kern probe proved the flag in force: "AVATAR To Ye
WAVE" in Source Han Sans CN with `w:kern` closes its pairs with the tables on (26 of 27 positions move)
and not off, the unkerned line and the ideographs unmoved either way: §4's mover class exactly, absent
from the corpora.

Landed in docx4j (uncommitted there at the time of writing): `FopConfigUtil.cjkAdvancedFeatures()` is
the property if set, else a reflection probe of `Docx4jFop.capabilities()` for
`shared-glyph-tounicode`; `keepsCjkLayoutTables()` exposes it; `CjkLayoutDefaultTest` holds that it
agrees with `FopCapabilities` and that the property overrides either way. Enterprise item 26: the
workaround is lifted by default on the docx4j renderer and kept on Apache FOP. Registry entry
`fop/CR-006` sent to the docx4j session, which holds `tasks.yaml`.
