# CR-001: a `gsub-features` hook, so Word's ligature setting reaches FOP

Status: DRAFT (design only, no code). Raised 2026-09-25 for docx4j CR-020 phase 2
item P2-8, which the classification named as docx4j's own finding rather than a
Metanorma cherry-pick. Registry key `fop/CR-001`.

## 1. The defect, measured

FOP applies OpenType's common-ligature feature unconditionally on the default
script path. `DefaultScriptProcessor.GSUB_FEATURES` is

    ccmp   glyph composition/decomposition
    liga   common ligatures
    locl   localized forms

and `FopFactoryBuilder` initialises `isComplexScript = true`, which docx4j passes
straight through rather than overriding. So every run rendered through a font
carrying a `liga` table gets ligatures applied.

Word does not work that way. Standard ligatures are off unless `w14:ligatures`
asks for them, on the run's `rPr`, on its style chain, or on `docDefaults`.
`docx4j-export-fo` has no handling of that element anywhere: a grep for
`ligature` over its sources returns nothing.

The error runs in both directions, which is worth stating because it is easy to
assume it is only one.

- **Too many.** A document that does not ask for ligatures gets them anyway, so
  `fi`, `fl`, `ff` and `ffi` are drawn as single glyphs with a different total
  advance than Word produces.
- **Too few.** `clig`, contextual ligatures, is absent from FOP's default list
  altogether. When Word asks for `standardContextual`, which newer theme
  defaults commonly do, FOP cannot honour the contextual half at all.

Unlike every other item in the phase 2 queue, this one is visible in rendered
output today. It is the only one that changes what a consumer sees.

## 2. Why the Metanorma approach is not the way

CR-020 §9.2 item M10e gates substitution on the language being `ar` or `dflt`,
having first mapped `xml:lang="ar"` to `dflt`. That is wrong twice over. A
BCP-47 language tag is not an OpenType language-system tag, and the gate would
silence substitution for every tagged language the moment docx4j emits
`language`. It also reaches for language when the property being modelled is a
run property, not a language property.

## 3. The call chain, and where a decision can be made

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
the only place with any knowledge of the run is the `TextFragment` at the top.

## 4. Designs rejected, and why

- **A setter on the font.** Rejected. `ScriptProcessor` instances are cached per
  script in a map owned by `GlyphSubstitutionTable`, which belongs to the font,
  and fonts are themselves cached and shared across renders. Per-document state
  on a font leaks between documents in a server process.
- **A thread-local.** Rejected for the same leak, plus hidden state. The
  classification already flagged a static mutable field in the Metanorma message
  work as a hazard; this would be the same mistake.
- **Registering each physical font twice under two triplets**, one with
  ligatures and one without, selecting by `font-family` in the generated FO.
  This needs no FOP change at all and is the fallback if §5 is rejected
  upstream. Set aside because it doubles font registration, loses FO
  inheritance, and pushes a typographic concern into font naming.
- **Replacing the feature list wholesale.** Rejected on a measured hazard.
  `ArabicScriptProcessor.GSUB_FEATURES` is `calt ccmp fina init isol liga medi
  rlig`: the Arabic shaping features and required ligatures live in the same
  list. A list authored for Latin and applied to an Arabic run would destroy
  Arabic rendering. The value must therefore be a delta, never an absolute list.

## 5. The design, FOP side

A new inherited extension property, `fox:gsub-features`, whose value is a delta
over whatever the script's own processor would use.

    fox:gsub-features="-liga"           do not apply common ligatures
    fox:gsub-features="-liga +clig"     contextual instead of common
    fox:gsub-features="+clig +dlig"     add to the script's own list

1. Register `gsub-features` in `ExtensionElementMapping.PROPERTY_ATTRIBUTES`,
   alongside `alt-text` and the rest, and add the property to
   `FOPropertyMapping` as inherited so FO inheritance gives per-run scope for
   free.
2. `TextFragment` gains `default String[] getGsubFeatures() { return null; }`.
   A default method keeps all four implementors compiling, and Java 8 is the
   floor here so it is available.
3. `FOText` overrides it from the resolved property.
4. `processWordMapping` reads `text.getGsubFeatures()` and threads it through new
   overloads on `Font`, `LazyFont`, `MultiByteFont`,
   `GlyphSubstitutionTable.substitute` and
   `ScriptProcessor.substitute`. Every existing signature is kept and delegates
   with `null`, so nothing that exists today changes shape.
5. `ScriptProcessor.substitute` applies the delta to
   `getSubstitutionFeatures()` before calling `assembleLookups`.

Note for step 4: `LazyFont.realFontDescriptor` is private with no accessor, so
the overload must be added to `LazyFont` itself. A consumer cannot reach past it.

## 6. Inertness, and the capability

With the property absent the delta is `null`, every overload delegates as
before, and `getSubstitutionFeatures()` is used unchanged. Output is bit
identical. That satisfies the hook definition in the README: a change that does
nothing FOP does not already do until something asks.

Capability name `gsub-features`, exposed through `Docx4jFop` so
`FopCapabilities` can probe it and docx4j can gate its rule and degrade on
Apache FOP.

## 7. The design, docx4j side

Owned by the docx4j session, recorded here so the two halves match.

1. Resolve the effective `w14:ligatures` per run: direct `rPr`, then the style
   chain, then `docDefaults/rPrDefault`.
2. Map it to a delta. Absent or `none` gives `-liga`. `standard` gives nothing,
   since `liga` is already in FOP's list. `contextual` adds `+clig`,
   `historical` adds `+hlig`, `discretional` adds `+dlig`, combinations
   accordingly, and `all` adds all three.
3. Emit `fox:gsub-features` on the run's `fo:inline` only when the delta is
   non-empty, and only for runs whose script is Latin, leaving Arabic and Indic
   runs alone for the reason in §4.
4. Gate the rule on the capability.

## 8. Pass criteria

This item is unlike the rest of the queue: **it moves the corpus by design.** A
no-movers expectation would be the wrong gate and would read as a fail when the
change is working.

- **Corpus.** Movement is expected on documents whose fonts carry a `liga` table
  and whose runs do not ask for ligatures. Pass is that every moved document
  scores the same or better against Word, and none scores worse. A document that
  worsens is a fail and wants its scoreboard reading, not an interpretation.
- **Probe pairs.** The same text rendered with and without `w14:ligatures`, in a
  font with a `liga` table, checked for the ligature glyph and the run advance.
  Measurement is visual: ligature substitution changes the glyph and the
  advance, and `ToUnicode` still maps it back, so text extraction would not see
  it.
- **An Arabic probe**, confirming a document with Arabic text is byte identical,
  since no delta should be emitted for it.
- **FOP side.** A test that an absent property leaves output unchanged, and one
  that a delta is applied. Both must fail before the change.

## 9. Upstream

`fox:gsub-features` is a real gap in FOP rather than a docx4j peculiarity: any FO
producer wanting typographic control over substitution needs it, and there is no
configuration surface for it today. It is worth offering upstream as a property,
not kept fork-only. It is larger than the fixes sent so far, so it wants a JIRA
carrying this design before any patch, and it should not block the fork.

## 10. Open questions

- Whether the delta syntax should be the space-separated `-liga +clig` form
  above or a pair of properties. The single-property form is proposed because it
  keeps one registry entry and one getter.
- Whether GPOS deserves the same treatment. Word's kerning setting, `w:kern`,
  has the same shape as the ligature setting, and `kern` sits in every
  processor's GPOS list. Out of scope here; worth its own item if the corpus
  shows kerning movers.
- Whether docx4j should emit the delta per run or hoist it to the block when a
  whole block agrees, which would cut FO size on documents that set it in
  `docDefaults`.
