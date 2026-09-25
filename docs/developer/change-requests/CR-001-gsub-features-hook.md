# CR-001: a `gsub-features` hook, replacing docx4j's no-ligature font twin

Status: DRAFT (design only, no code). Raised 2026-09-25 for docx4j CR-020 phase 2
item P2-8. Registry key `fop/CR-001`. Revised the same day after the docx4j
session reviewed it; §1 corrects the premise the first draft was built on.

## 1. Correction to the first draft

The first draft of this CR claimed docx4j has no handling of `w14:ligatures` and
that FOP is therefore applying ligatures Word would not apply, across the corpus,
today. **That was wrong.** The claim came from a grep scoped to
`docx4j-export-fo`, and the handling lives in `docx4j-core`, in
`RunFontSelector.noLigatures`, since 17.0.5.

What docx4j already does. `RunFontSelector` reads the run's effective `rPr`
through `PropertyResolver`, which is direct properties, then the style chain,
then `docDefaults`. When a run asks for no ligatures and no kerning and its span
is Latin-only, it rewrites the span's `font-family` to `<family>+noliga`.
`FopConfigUtil.noLigaTwin` declares every TrueType-flavoured font a second time
under that triplet with `encoding-mode="single-byte"`, and a simple TrueType font
in FOP implements neither `Substitutable` nor `Positionable`, so it gets no GSUB
and no GPOS at all. Suffixes stack, `PhysicalFonts` strips them.

So on the common path, a TrueType font with Latin text and no kerning asked,
Word's no-ligature setting is **already honoured today, on Apache FOP**. The
corpus is not getting spurious ligatures there, and this item is not the live
fidelity defect the first draft advertised. My recommendation to Jason that P2-8
was the one item visible in rendered output rested on that error.

## 2. What the twin does not reach: the actual case for the hook

Four gaps, all measured by the docx4j session.

- **CFF and OpenType-flavoured fonts get no twin at all**, because FOP would
  write an OTTO file as `/Subtype /TrueType` inside `/FontFile2`, which is
  invalid PDF. So the substitutes for Arial Narrow and Segoe UI Light still
  receive common ligatures. docx4j's own font jars are all TrueType, so this is
  the substitute set only.
- **Runs that ask for kerning but not ligatures** go to the `+kern` twin, which
  stays advanced, so they receive ligatures.
- **Spans containing any non-Latin character** keep substitution by design,
  because a single-byte font would lose text extraction for a whole non-Latin
  alphabet.
- **The twin can only ever subtract.** Word 365's Normal template sets
  `standardContextual` in `docDefaults`, so every new Word 365 document asks for
  contextual ligatures, and FOP's default list is `ccmp liga locl` with no
  `clig`. Those documents get common ligatures only and can never get contextual
  ones. The twin cannot express an addition; the hook can.

The costs §5 of the first draft held against the twin approach, doubled font
registration, a typographic concern pushed into font naming, and lost FO
inheritance, are real, and they go away on the fork. But they are a
simplification argument, not a fidelity one. The fidelity argument is the four
gaps above, and chiefly the fourth.

## 3. The design is a replacement, with the twin kept as fallback

On the fork, docx4j emits the property instead of the `+noliga` suffix. On Apache
FOP the capability probe fails and docx4j keeps the twin exactly as it ships
today. That is what makes this a hook rather than a fix: it changes nothing until
something asks, and there is a working path when nothing does.

## 4. Why the Metanorma approach is not the way

CR-020 §9.2 item M10e gates substitution on the language being `ar` or `dflt`,
having first mapped `xml:lang="ar"` to `dflt`. That is wrong twice over. A
BCP-47 language tag is not an OpenType language-system tag, and the gate would
silence substitution for every tagged language the moment docx4j emits
`language`. It also reaches for language when the property being modelled is a
run property.

## 5. The call chain, and where a decision can be made

    GlyphMapping.doGlyphMapping(TextFragment text, ..., Font font, ...)   public static
      -> GlyphMapping.processWordMapping(text, ..., font, ...)            script and language come from text
        -> Font.performSubstitution(cs, script, language, assoc, retainControls)
          -> LazyFont.performSubstitution(...)                            delegates to the real font
            -> MultiByteFont.performSubstitution(...)
              -> GlyphSubstitutionTable.substitute(gs, script, language)
                -> ScriptProcessor.getInstance(script, processors)
                -> ScriptProcessor.substitute(gsub, gs, script, language, lookups)
                  -> assembleLookups(gsub, getSubstitutionFeatures(), lookups)

The feature list enters at exactly one point, `getSubstitutionFeatures()`, and
the only place with knowledge of the run is the `TextFragment` at the top.

## 6. Designs rejected, and why

- **A setter on the font.** Rejected. `ScriptProcessor` instances are cached per
  script in a map owned by `GlyphSubstitutionTable`, which belongs to the font,
  and fonts are themselves cached across renders. Per-document state on a font
  leaks between documents in a server process.
- **A thread-local.** Rejected for the same leak, plus hidden state.
- **Replacing the feature list wholesale.** Rejected on a measured hazard.
  `ArabicScriptProcessor.GSUB_FEATURES` is `calt ccmp fina init isol liga medi
  rlig`: the Arabic shaping features and required ligatures sit in the same list.
  A list authored for Latin and applied to an Arabic run would destroy Arabic
  rendering. The value must be a delta, never an absolute list.

## 7. The design, FOP side

A new inherited extension property, `fox:gsub-features`, whose value is a delta
over whatever the script's own processor would use.

    fox:gsub-features="-liga"           do not apply common ligatures
    fox:gsub-features="-liga +clig"     contextual instead of common
    fox:gsub-features="+clig"           add to the script's own list

1. Register `gsub-features` in `ExtensionElementMapping.PROPERTY_ATTRIBUTES`,
   alongside `alt-text`, and add the property to `FOPropertyMapping` as inherited
   so FO inheritance gives per-span scope for free.
2. `TextFragment` gains `default String[] getGsubFeatures() { return null; }`. A
   default method keeps all four implementors compiling, and Java 8 is the floor.
3. `FOText` overrides it from the resolved property.
4. `processWordMapping` reads it and threads it through new overloads on `Font`,
   `LazyFont`, `MultiByteFont`, `GlyphSubstitutionTable.substitute` and
   `ScriptProcessor.substitute`. Every existing signature is kept and delegates
   with `null`.
5. `ScriptProcessor.substitute` applies the delta to `getSubstitutionFeatures()`
   before calling `assembleLookups`.

`LazyFont.realFontDescriptor` is private with no accessor, so step 4's overload
must be added to `LazyFont` itself; a consumer cannot reach past it.

With the property absent the delta is `null`, every overload delegates as before,
and output is bit identical. Capability name `gsub-features`, exposed through
`Docx4jFop`.

## 8. The design, docx4j side

Owned by the docx4j session; recorded here so the halves match. Corrections in
this section are its review, not mine.

1. The effective `w14:ligatures` is already resolved by `PropertyResolver` and
   already feeds `RunFontSelector`. Absent **after** resolution means none.
   Absent on the run alone means nothing, because Word 365 documents inherit
   `standardContextual` from `docDefaults`.
2. Map the 16 values of `ST_Ligatures` to a delta. Because FOP applies `liga` by
   default, **any value without `standard` in it must subtract it**:

        none, absent after resolution     -liga
        standard                          (nothing)
        contextual                        -liga +clig
        historical                        -liga +hlig
        discretional                      -liga +dlig
        contextualHistorical              -liga +clig +hlig
        standardContextual                +clig
        all                               +clig +hlig +dlig

   and so on for the remaining combinations. `ccmp` and `locl` stay in every
   case, since Word always applies them.
3. Emit on the run's `fo:inline`, the same element that carries the `+noliga`
   suffix today, per span rather than per block. `RunFontSelector` already emits
   one inline per stretch of the same font and script and already decides
   Latin-only per span.
4. Do not let `w:pPr/w:rPr` reach the runs: it formats the paragraph mark only.
5. Gate on the capability; keep the twin for Apache FOP.

Two further Word switches have the same shape and the property could carry them:
`w14:cntxtAlts`, which maps to `calt` and is an addition because FOP's default
list omits it, and `w14:stylisticSets`, which maps to `ss01` through `ss20`.
Word's theme carries no ligature setting, so no path is missing there.

## 9. Pass criteria

This item moves the corpus by design, but that is not a reason to suspend the
gate: a gate switched off for one change is a gate somebody forgets to switch
back on. Instead the corpus is partitioned in advance and the gate checks a
prediction, so the pass condition stays positive.

**The partition must be twin-aware.** The baseline is the fork as it stands with
docx4j using the twin; the candidate is the fork with the hook and docx4j
emitting the delta. So a document whose no-ligature runs are Latin-only, in a
TrueType-flavoured font, with no kerning asked, is **expected still**: the twin
already produces no ligatures there and the hook must reproduce that byte for
byte. A predicate of "asks for no ligatures and the font has `liga` coverage"
would wrongly predict movers the twin has already made still.

**Expected movers** are only the four gaps in §2: no-ligature runs in a CFF
substitute, no-ligature runs that also ask for kerning, no-ligature spans holding
a non-Latin character, and documents resolving to a value with `clig`, `hlig` or
`dlig` in a font carrying that feature, the Word 365 `docDefaults` case being the
common one.

The partition needs the effective `rPr` per run, the font each span resolves to,
its flavour and its GSUB feature coverage. All four are docx4j-side facts, so the
docx4j session computes the partition when the item starts and supplies the list
with a reason per document. This side holds the pass statements over it.

**Pass is all five of these.**

- Every expected-still document is byte identical. A mover here is the
  interesting failure: a delta reached a run that should not have had one.
- Every expected-mover that moved scores the same or better against Word. One
  that scores worse is a fail and wants its scoreboard reading.
- An expected mover that did not move is recorded, not failed. It means the font
  lacked coverage for the sequences present, and it sharpens the next prediction.
- An Arabic probe is byte identical, for the reason in §6.
- The global override `docx4j.convert.out.fo.ligatures=true`, which lets FOP
  ligate everywhere, still wins.

**FOP side.** A test that an absent property leaves output unchanged, and one
that a delta is applied. Both must fail before the change.

Measurement is by text extraction, not only visually, for the reason in §10.

## 10. A larger defect found on the way: ligature text extraction

The first draft asserted that a ligature's `ToUnicode` maps back to its component
characters, so extraction would not see the change. **That is false**, and the
consequence is a worse bug than the one this CR addresses. Verified here from the
code after the docx4j session measured it in output.

`MultiByteFont.mapGlyphsToChars` takes each glyph's character from
`findUnsubstitutedCharacter` and, when substitution did produce the glyph, falls
through to `findCharacterFromGlyphIndex`, which for a glyph with no cmap entry
mints a private-use code point through `createPrivateUseMapping`.
`nextPrivateUse` is initialised to `0xE000`. That code point is what the painter
hands to `CIDSubset.mapChar`, which records it as the glyph's unicode, and
`CIDSubset.getChars` is exactly what `PDFToUnicodeCMap` is built from.

So a ligature glyph's `ToUnicode` entry is U+E000 and upward, not `fi`. Search,
copy and paste, and screen readers all get private-use characters. The docx4j
session found it as a third of the lines of a French corpus document extracting
`ti` as U+E000, which is how their corpus line-parity score noticed at all.

This affects every FOP user with ligatures enabled, which is the default, and it
is invisible until someone selects text, so it goes unreported. It is a PDF/UA
and searchability defect rather than a fidelity one.

It is not fixable by the same mechanism. `mapGlyphsToChars` returns one character
per glyph and cannot express "this glyph is two characters", whereas the PDF
`ToUnicode` format can: a `bfchar` destination may be a string. The information
needed already exists in the `CharAssociation` of the `GlyphSequence`, which is
the same mechanism the Kangxi radical fix used. The fix therefore belongs in how
the `ToUnicode` CMap is built, not in the glyph-to-character mapping.

Recorded as its own item rather than folded in here, because it is independent of
the feature switch, it is worth more to more people, and it wants its own JIRA.
See `CR-002`.

## 11. Open questions

- Whether the delta syntax should be the space-separated form above or a pair of
  properties. One property keeps one registry entry and one getter.
- Whether GPOS deserves the same treatment. Word's `w:kern` has the same shape
  and `kern` sits in every processor's GPOS list. Out of scope here.
