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
 * @see <a href="https://github.com/plutext/xmlgraphics-fop/tree/docx4j-2.11">the fork</a>
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

    private static final Set<String> CAPABILITIES;

    static {
        Set<String> caps = new LinkedHashSet<String>();
        caps.add(PAIR_TABLE);
        caps.add(LEADER_PLACEMENT);
        caps.add(INLINE_ACCESS);
        caps.add(GLYF_EMPTY_GLYPH);
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
