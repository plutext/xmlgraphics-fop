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
