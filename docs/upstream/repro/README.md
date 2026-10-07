# FO reproducers posted to Apache JIRA

Each `.fo` here was attached to the JIRA it is named for, with `fop-test-fonts.xconf`, on 2026-10-08,
answering the committer's request for an FO (Joao Goncalves, 2026-10-05 and 2026-10-06). The config
declares two fonts from FOP's own test tree, `fop/test/resources/fonts/ttf/Aegean600.ttf` and
`DejaVuLGCSerif.ttf`, by a `font-base` relative to the config file, so it is copied to the root of
a checkout and run from there:

    fop -c fop-test-fonts.xconf -fo FOP-3346-selector-drift.fo -pdf out.pdf
    pdftotext out.pdf -

Verified on Apache `main` at 5be8c69b6 and on each fix branch before posting. What each shows:

| file | ticket | on `main` | with the fix |
|---|---|---|---|
| `FOP-3346-selector-drift.fo` | FOP-3346 | `A𐌀 B` (the Z lost, selectors drift after U+10300) | `A𐌀BZ` |
| `FOP-3347-format-character.fo` | FOP-3347 | `abcdef ghi` (U+206A elided) | `abc⁪def ghi`, same word boxes |
| `FOP-3340-shared-glyph.fo` | FOP-3340 | `itʼs` (U+02BC: Aegean600 maps it and U+2019 to one glyph) | `it’s` |
| `FOP-3340-cjk-radical.fo` | FOP-3340 | `⽣⽅⼈，牋` (Kangxi radicals) | `生方人，牋`; needs Source Han Sans CN |

Aegean600's shared pairs are U+2018/U+02BB and U+2019/U+02BC (also U+200C/U+200E, U+200D/U+200F),
found with fontTools; DejaVuLGCSerif has no shared glyph but maps U+200E, U+200F, U+206A, U+2060 and
U+200B to zero-advance glyphs. FOP-3350 got no FO: stock FOP turns `initial-page-number="0"` into 1
(checked on the same `main`), so its reproduction is the pull request's unit test.
