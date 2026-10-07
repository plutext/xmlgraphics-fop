# FO reproducers posted to Apache JIRA

Each `.fo` here was attached to the JIRA it is named for, with `fop-test-fonts.xconf`, on 2026-10-08,
answering the committer's request for an FO (Joao Goncalves, 2026-10-05 and 2026-10-06). The config
declares two fonts from FOP's own test tree, `fop/test/resources/fonts/ttf/Aegean600.ttf` and
`DejaVuLGCSerif.ttf`, by a `font-base` relative to the config file, so it is copied to the root of
a checkout and run from there:

    fop -c fop-test-fonts.xconf -fo FOP-3346-selector-drift.fo -pdf out.pdf
    pdftotext out.pdf -

Verified on Apache `main` at 5be8c69b6 and on each fix branch before posting. The second batch (2026-10-08, comments
18124651 to 18124660) added `distro-fonts.xconf` (Carlito and DejaVu Sans, since DejaVuLGCSerif's DFLT script carries
`liga` and `kern` and so cannot show FOP-3341 or FOP-3342), `carlito-simulate-style.xconf`, `FOP-1896-fonts.xconf`,
`noto-variable.xconf` with `logging.properties` (the variable Noto Sans from Google Fonts, renamed `NotoSans-VF.ttf`
because FOP rejects brackets in a file URI), and the FOs below. What each shows:

| file | ticket | on `main` | with the fix |
|---|---|---|---|
| `FOP-3346-selector-drift.fo` | FOP-3346 | `A𐌀 B` (the Z lost, selectors drift after U+10300) | `A𐌀BZ` |
| `FOP-3347-format-character.fo` | FOP-3347 | `abcdef ghi` (U+206A elided) | `abc⁪def ghi`, same word boxes |
| `FOP-3340-shared-glyph.fo` | FOP-3340 | `itʼs` (U+02BC: Aegean600 maps it and U+2019 to one glyph) | `it’s` |
| `FOP-3340-cjk-radical.fo` | FOP-3340 | `⽣⽅⼈，牋` (Kangxi radicals) | `生方人，牋`; needs Source Han Sans CN |
| `FOP-3341-language-fallback.fo` | FOP-3341 | Carlito, `language="en"`: no ligature, AVATAR 39.96pt (bare) | `oﬃce`, 37.02pt |
| `FOP-3342-shared-default-langsys.fo` | FOP-3342 | DejaVu Sans, no language: AVATAR 48.48pt (bare) | 45.12pt; `en`/`ro` need FOP-3341 too |
| `FOP-3343-kerning-flag.fo` | FOP-3343 | `kerning="false"` twin still 48.98pt (kerned) | 51.67pt |
| `FOP-3344-letter-spacing-dp.fo` | FOP-3344 | 3pt letter spacing painted nowhere (steps 8.08 7.86 8.02 7.34 8.66) | steps +3pt; with #113 alone `next` overprints, with #118 at 148.78 |
| `FOP-2349-letter-spacing-width.fo` | FOP-2349 | 5 lines ending 22pt past the 280pt edge; `-nocs` 6 lines inside | 6 lines as `-nocs`, within 3.3pt (kerning) |
| `FOP-3345-ligature-tounicode.fo` | FOP-3345 | Carlito `soft title`: ft, ti as U+E000, U+E001 | `<00660074>`, `<00740069>`, ffi `<006600660069>` |
| `FOP-3356-simulate-style.fo` | FOP-3356 | all three faces `2 Tr 0.31543 w`, sheared | italic not re-sheared, bold not re-stroked, `0.68571 w` at 24pt |
| `FOP-1896-ascender-descender.fo` | FOP-1896 | `-at application/pdf`: text areas `bpd="0" baseline="0"` | AndroidEmoji 16692/12888, Devanagari 15648/10752 |
| `FOP-3328-variable-font.fo` | FOP-3328 | FINE log: 2,728 "invalid device table delta count" | 0; 43,755 VariationIndex tables ignored cleanly |

Aegean600's shared pairs are U+2018/U+02BB and U+2019/U+02BC (also U+200C/U+200E, U+200D/U+200F),
found with fontTools; DejaVuLGCSerif has no shared glyph but maps U+200E, U+200F, U+206A, U+2060 and
U+200B to zero-advance glyphs. FOP-3350 got no FO: stock FOP turns `initial-page-number="0"` into 1
(checked on the same `main`), so its reproduction is the pull request's unit test.
