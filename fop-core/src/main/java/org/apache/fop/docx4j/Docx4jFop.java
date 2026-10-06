/*
 * Copyright 2026, Plutext Pty Ltd.
 *
 * This file is part of the docx4j FO renderer (docx4j-fo-renderer), a modified
 * distribution derived from Apache FOP 2.11. It is not part of Apache FOP.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.fop.docx4j;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The marker of the docx4j FO renderer: its presence on the classpath tells docx4j
 * (docx4j-export-fo's {@code FopCapabilities}) that the renderer is this fork rather than
 * Apache FOP, and {@link #capabilities()} names the hooks it carries, so that docx4j can
 * gate each hook-dependent rule and fall back where the hook is absent.
 *
 * <p>Capability names are stable strings, one per hook; a name is added when the hook lands
 * and never removed while the hook exists. docx4j reads this class reflectively, so its
 * shape (a {@code version()} and a {@code capabilities()} static, both without arguments)
 * is a contract.</p>
 *
 * @see <a href="https://github.com/plutext/xmlgraphics-fop">the fork</a>
 */
public final class Docx4jFop {

    /** The Apache FOP line this renderer is based on. */
    public static final String APACHE_FOP_LINE = "2.11";

    /** {@code LineBreakUtils.setLineBreakPairProperty}: one cell of the line-break pair table overridden. */
    public static final String PAIR_TABLE = "pair-table";

    /**
     * {@code FilledArea.getUnitAreas}, {@code LeafNodeLayoutManager.getCurrentArea/setAreaInfoIPD}
     * (its {@code setCurrentArea} is Apache's own), {@code LeaderLayoutManager.getFont}: a placed
     * leader's unit, and the leaf manager's area and width, for a line manager that grids and
     * phases leaders.
     */
    public static final String LEADER_PLACEMENT = "leader-placement";

    /**
     * {@code TextLayoutManager.getMappings/getLetterSpaceIPD/getSpaceCharIPD/getFOText},
     * {@code AlignmentContext(Font,int,WritingMode)} and {@code getLineHeight},
     * {@code InlineLayoutManager.getFont}, {@code LeafPosition.setLeafPos},
     * {@code LineLayoutManager.LineBreakPosition} (public constructor and getters),
     * {@code ListItemLayoutManager.getBodyList}: what a consumer's line and list managers read.
     */
    public static final String INLINE_ACCESS = "inline-access";

    /** {@code GlyfTable.isComposite} is false for an empty glyph: a font whose last glyph is empty embeds. */
    public static final String GLYF_EMPTY_GLYPH = "glyf-empty-glyph";

    /**
     * {@code fox:gsub-features}, an inherited property carrying a delta over the GSUB features
     * the script's processor would apply: {@code -liga} drops one, {@code +clig} adds one,
     * space separated. Lets a producer express Word's {@code w14:ligatures} per run. Absent,
     * FOP applies exactly what it applies today.
     */
    public static final String GSUB_FEATURES = "gsub-features";

    /**
     * {@code GlyphTable.matchLookups} falls back from (script, language) to (script, dflt) before
     * (DFLT, dflt), as OpenType layout engines do. A producer may write a language on every block
     * without losing the font's substitution and kerning where the font has no language system for
     * it, and a font with no DFLT script table shapes and kerns under its own script.
     */
    public static final String LOOKUP_FALLBACK = "lookup-fallback";

    /**
     * A font declared with {@code kerning="false"} is positioned without the GPOS {@code kern}
     * feature, as it already was without the legacy kern table; marks are still positioned. A
     * producer that declares a font twice, plain and kerned, gets kerning only where it asked.
     */
    public static final String KERNING_FLAG = "kerning-flag";

    /**
     * A glyph that two code points share (a Kangxi radical and its ideograph; U+2009 and U+202F in
     * Tinos) publishes to ToUnicode the character the document wrote, not the lowest code point the
     * character map gives the glyph. docx4j may leave a CJK font's layout tables on where it had to
     * declare {@code advanced="false"} to keep radicals out of the text layer (Enterprise CR-001 item
     * 26, fop/CR-006).
     */
    public static final String SHARED_GLYPH_TOUNICODE = "shared-glyph-tounicode";

    /**
     * {@code area.inline.Leader.setRuleStyle(int)}: the rule style from its XSL enumeration value,
     * the form Apache FOP 2.11 had and FOP-3325 replaced with {@code BorderStyle}. Code compiled
     * against 2.11 that builds rule leaders runs on this renderer (fop/CR-009).
     */
    public static final String RULE_STYLE_INT = "rule-style-int";

    /**
     * On the path for fonts with substitution or positioning tables ({@code GlyphMapping}'s mapping
     * path) a word's counted letter spaces are in its width, as they always were on the other path.
     * A consumer may take {@code GlyphMapping.letterSpaceCount} as the letter spaces already in
     * {@code areaIPD} on both paths (Enterprise CR-001 item 16, fop/CR-010).
     */
    public static final String LETTER_SPACE_WIDTH = "letter-space-width";

    /**
     * {@code FOUserAgent.setPageNumberZeroAllowed(boolean)}: with it set, initial-page-number="0"
     * numbers the first page 0 (Word's cover page, {@code w:pgNumType w:start="0"}), where FOP's
     * default, the XSL error recovery, makes it 1. The PDF page labels write such a page as a
     * prefix label (Enterprise CR-001 item 38, fop/CR-012).
     */
    public static final String PAGE_NUMBER_ZERO = "page-number-zero";

    /**
     * {@code fox:continuation-display-align} on fo:table-cell (TableCell.getContinuationDisplayAlign):
     * the display-align of a cell's parts after the first when it is broken across pages, where a word
     * processor sets them from the top whatever the cell's vertical alignment; auto, the default, keeps
     * display-align (Enterprise CR-001 item 39, fop/CR-013).
     */
    public static final String CONTINUATION_DISPLAY_ALIGN = "continuation-display-align";

    /**
     * {@code <to-unicode code-point="F04A" unicode="263A"/>} in a font's configuration entry: the text
     * the ToUnicode CMap publishes for a glyph reached through that code point, so a symbol font's
     * private-use text (Wingdings, Symbol, Webdings) extracts as its Unicode equivalent. The text layer
     * alone; glyphs and layout are unchanged (fop/CR-014).
     */
    public static final String TO_UNICODE_MAP = "to-unicode-map";

    /**
     * A TrueType or OpenType font's ascender and descender are not taken from an OS/2 typo descender
     * above the baseline (Wingdings, the Lucida faces: a sign error in the font), and are not made 0
     * for a font without the 'd' and 'p' glyphs whose ascender and descender together exceed the em
     * (symbol fonts, and many fonts for other scripts): the hhea values stand there (Enterprise
     * CR-001 item 40, fop/CR-015).
     */
    public static final String ASCENDER_DESCENDER = "ascender-descender";

    /**
     * {@code fox:page-sequence-master-reference} on a block-level FO in the main flow names a
     * page-sequence-master (one unbounded repeatable-page-master-alternatives): the pages whose first line
     * lies at or after it, up to the next such FO, take their masters from it, as a word processor takes a
     * page's margins and header from the section owning its first line. Absent, FOP chooses masters as
     * before (docx4j CR-031, fop/CR-017).
     */
    public static final String PAGE_MASTER_BY_CONTENT = "page-master-by-content";

    /**
     * {@code fox:page-number-restart="S"} on an FO carrying {@code fox:page-sequence-master-reference} restarts its
     * part's page numbers at S, as Word restarts a continuous section's: the page the part starts on prints its
     * owner's number, and the count begins there, or with {@code fox:page-number-restart-parity="keep"} (odd and
     * even headers) on the next page where S's parity differs. A following page-sequence continues from the
     * number the last page prints (docx4j CR-031 phase 3, fop/CR-017.2).
     */
    public static final String PAGE_NUMBER_RESTART = "page-number-restart";

    /**
     * {@code fox:extent="measured"} on {@code fo:region-before} or {@code fo:region-after}: the region is as tall
     * as its static content, measured per page-sequence and master, and the region-body's margin on that side is
     * the larger of its stated margin and that height, so the body clears the region, as a word processor places
     * it below its header. The lr-tb and rl-tb writing modes at reference-orientation 0 (docx4j CR-031 phase 5,
     * fop/CR-018).
     */
    public static final String MEASURED_REGION_EXTENTS = "measured-region-extents";

    /**
     * A side float ends at the break before the first line lying wholly below its foot, the space there counted
     * and kept, and its end never falls inside a table, where FOP threw (Enterprise CR-001 items 44 and 45,
     * fop/CR-020). Content set beside a float, as docx4j's text-box band is, depends on it.
     */
    public static final String SIDE_FLOAT_EDGES = "side-float-edges";

    private static final Set<String> CAPABILITIES;

    static {
        Set<String> caps = new LinkedHashSet<String>();
        caps.add(PAIR_TABLE);
        caps.add(LEADER_PLACEMENT);
        caps.add(INLINE_ACCESS);
        caps.add(GLYF_EMPTY_GLYPH);
        caps.add(GSUB_FEATURES);
        caps.add(LOOKUP_FALLBACK);
        caps.add(KERNING_FLAG);
        caps.add(SHARED_GLYPH_TOUNICODE);
        caps.add(RULE_STYLE_INT);
        caps.add(LETTER_SPACE_WIDTH);
        caps.add(PAGE_NUMBER_ZERO);
        caps.add(CONTINUATION_DISPLAY_ALIGN);
        caps.add(TO_UNICODE_MAP);
        caps.add(ASCENDER_DESCENDER);
        caps.add(PAGE_MASTER_BY_CONTENT);
        caps.add(PAGE_NUMBER_RESTART);
        caps.add(MEASURED_REGION_EXTENTS);
        caps.add(SIDE_FLOAT_EDGES);
        CAPABILITIES = Collections.unmodifiableSet(caps);
    }

    private Docx4jFop() {
    }

    /**
     * The renderer's version, e.g. {@code 2.11-docx4j.1}, from the jar manifest, or
     * {@code 2.11-docx4j.development} when the classes are not loaded from the built jar.
     * @return the version string
     */
    public static String version() {
        Package p = Docx4jFop.class.getPackage();
        String v = (p == null) ? null : p.getImplementationVersion();
        return (v == null) ? APACHE_FOP_LINE + "-docx4j.development" : v;
    }

    /**
     * The names of the hooks this renderer carries.
     * @return an unmodifiable set of capability names
     */
    public static Set<String> capabilities() {
        return CAPABILITIES;
    }

    /**
     * Whether this renderer carries the named hook.
     * @param capability a capability name
     * @return true if present
     */
    public static boolean has(String capability) {
        return CAPABILITIES.contains(capability);
    }
}
