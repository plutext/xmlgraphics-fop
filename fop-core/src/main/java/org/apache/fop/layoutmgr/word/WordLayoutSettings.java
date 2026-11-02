/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Added by Plutext Pty Ltd for the docx4j FO renderer (docx4j-fo-renderer), a modified distribution derived from
 * Apache FOP 2.12: Word mode's settings (fop/CR-031, docx4j CR-071). See README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr.word;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.fop.layoutmgr.KnuthElement;

/**
 * The settings of Word mode (fop/CR-031, docx4j CR-071): Word's greedy line breaking and the rules around it, in the
 * fork's own layout managers. An instance exists only when Word mode is on
 * ({@code FopFactoryBuilder.setWordLayout}); the managers read it as {@code fobj.getUserAgent().getWordLayout()},
 * and {@code null} there means Apache FOP's layout.
 *
 * <p>The settings are keyed by docx4j's property names ({@code docx4j.convert.out.fo.wordLayout.*}), whose values
 * docx4j resolves (a system property first, then {@code docx4j.properties}) and passes as strings. An absent key
 * takes docx4j's default, and a value is parsed as docx4j parsed it: {@code Boolean.parseBoolean} and
 * {@code Double.valueOf} on the trimmed string, a number that does not parse counting as absent. Keys the managers
 * do not read are ignored, so docx4j may pass every property it has.</p>
 */
public final class WordLayoutSettings {

    /** The prefix of every key. */
    public static final String PREFIX = "docx4j.convert.out.fo.wordLayout.";

    /** The space compression of a justified line where the document does not state one: Word 2013's 0.245. */
    public static final double DEFAULT_MAX_SPACE_SHRINK = 0.245;

    /** The space compression of a line ending in a hyphen. */
    public static final double DEFAULT_MAX_HYPHEN_SPACE_SHRINK = 0.10;

    /** How much stretch a line must have before its spaces are compressed instead. */
    public static final double DEFAULT_MIN_STRETCH_TO_COMPRESS = 0;

    /** How compression is weighed against stretch. */
    public static final double DEFAULT_COMPRESSION_TO_STRETCH = 0.5;

    /** The penalty an infeasible keep chain's keeps are reduced to. */
    public static final int DEFAULT_KEEP_CHAIN_PENALTY = 900;

    /** How many pages a keep chain may span before it is bounded. */
    public static final double DEFAULT_KEEP_CHAIN_TOLERANCE = 3.0;

    /** How many pages a chain of a table's row keeps may span before it is bounded. */
    public static final double DEFAULT_KEEP_CHAIN_ROW_TOLERANCE = 1.0;

    private final Map<String, String> values;

    private final BiFunction<String, String, Double> ascentShare;

    /**
     * The settings from docx4j's resolved properties.
     * @param settings the values by property name; {@code null} is taken as empty
     */
    public WordLayoutSettings(Map<String, String> settings) {
        this(settings, null);
    }

    /**
     * The settings from docx4j's resolved properties, and docx4j's font metrics.
     * @param settings the values by property name; {@code null} is taken as empty
     * @param ascentShare see {@link #ascentShare(String, String)}; may be {@code null}
     */
    public WordLayoutSettings(Map<String, String> settings, BiFunction<String, String, Double> ascentShare) {
        this.ascentShare = ascentShare;
        Map<String, String> copy = new HashMap<String, String>();
        if (settings != null) {
            for (Map.Entry<String, String> e : settings.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    copy.put(e.getKey(), e.getValue().trim());
                }
            }
        }
        values = Collections.unmodifiableMap(copy);
    }

    /**
     * Word's share of a run's line pitch above its baseline, {@code (usWinAscent + external leading) / line
     * height} of the font Word lays the run out in. The answer is docx4j's: it knows the document's fonts, which FOP
     * may render in a substitute, and their metrics ({@code org.docx4j.fonts.WordLineMetrics}), so docx4j passes the
     * lookup ({@code FopFactoryBuilder.setWordLayoutFontMetrics}).
     * @param documentFont the document font the run names ({@code docx4j:font}), or {@code null}
     * @param physicalFont the full name of the font FOP renders the run in
     * @return the share, or {@code null} where it is not known (the block font's share is then used)
     */
    public Double ascentShare(String documentFont, String physicalFont) {
        return ascentShare == null ? null : ascentShare.apply(documentFont, physicalFont);
    }

    private String value(String name) {
        return values.get(PREFIX + name);
    }

    private boolean flag(String name, boolean dflt) {
        String v = value(name);
        return v == null ? dflt : Boolean.parseBoolean(v);
    }

    private Double number(String name) {
        String v = value(name);
        if (v == null) {
            return null;
        }
        try {
            return Double.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double number(String name, double dflt) {
        Double v = number(name);
        return v == null ? dflt : v;
    }

    /**
     * {@code maxSpaceShrink}, where it is set: how far the spaces of a justified line may be compressed, overriding
     * the document's {@code docx4j:space-shrink}.
     * @return the configured value, or {@code null} where it is not set
     */
    public Double configuredMaxSpaceShrink() {
        return number("maxSpaceShrink");
    }

    /** @return {@code maxSpaceShrink}, default {@link #DEFAULT_MAX_SPACE_SHRINK} */
    public double maxSpaceShrink() {
        return number("maxSpaceShrink", DEFAULT_MAX_SPACE_SHRINK);
    }

    /** @return {@code maxHyphenSpaceShrink}, default {@link #DEFAULT_MAX_HYPHEN_SPACE_SHRINK} */
    public double maxHyphenSpaceShrink() {
        return number("maxHyphenSpaceShrink", DEFAULT_MAX_HYPHEN_SPACE_SHRINK);
    }

    /** @return {@code minStretchToCompress}, default {@link #DEFAULT_MIN_STRETCH_TO_COMPRESS} */
    public double minStretchToCompress() {
        return number("minStretchToCompress", DEFAULT_MIN_STRETCH_TO_COMPRESS);
    }

    /** @return {@code compressionToStretch}, default {@link #DEFAULT_COMPRESSION_TO_STRETCH} */
    public double compressionToStretch() {
        return number("compressionToStretch", DEFAULT_COMPRESSION_TO_STRETCH);
    }

    /** @return {@code hyphenationZone}: whether the document's hyphenation zone is enforced, default false */
    public boolean enforceHyphenationZone() {
        return flag("hyphenationZone", false);
    }

    /** @return {@code labelAscentAgainstBaseline}, default true */
    public boolean labelAscentAgainstBaseline() {
        return flag("labelAscentAgainstBaseline", true);
    }

    /** @return {@code lineBaselineFromBlock}, default true */
    public boolean lineBaselineFromBlock() {
        return flag("lineBaselineFromBlock", true);
    }

    /** @return {@code leaderGrid}, default true */
    public boolean leaderGrid() {
        return flag("leaderGrid", true);
    }

    /** @return {@code widowsAcrossSoftReturns}, default true */
    public boolean widowsAcrossSoftReturns() {
        return flag("widowsAcrossSoftReturns", true);
    }

    /** @return {@code tabSpaces}, default true */
    public boolean tabSpaces() {
        return flag("tabSpaces", true);
    }

    /** @return {@code leaderCharacters}, default true */
    public boolean leaderCharacters() {
        return flag("leaderCharacters", true);
    }

    /** @return {@code labelSuffixSpace}, default true */
    public boolean labelSuffixSpace() {
        return flag("labelSuffixSpace", true);
    }

    /** @return {@code emergencyBreak}, default true */
    public boolean emergencyBreak() {
        return flag("emergencyBreak", true);
    }

    /**
     * @param dflt the value where it is not set
     * @return {@code emergencyBreakTolerance}
     */
    public double emergencyBreakTolerance(double dflt) {
        return number("emergencyBreakTolerance", dflt);
    }

    /**
     * @param dflt the value where it is not set
     * @return {@code cellEmergencyBreakTolerance}
     */
    public double cellEmergencyBreakTolerance(double dflt) {
        return number("cellEmergencyBreakTolerance", dflt);
    }

    /**
     * @param dflt the value where it is not set
     * @return {@code cellEmergencyBreakToleranceRatio}
     */
    public double cellEmergencyBreakToleranceRatio(double dflt) {
        return number("cellEmergencyBreakToleranceRatio", dflt);
    }

    /**
     * @param dflt the value where it is not set
     * @return {@code pageNumberPlaceholder}
     */
    public String pageNumberPlaceholder(String dflt) {
        String v = value("pageNumberPlaceholder");
        return v == null ? dflt : v;
    }

    /** @return {@code seamBreak}, default true */
    public boolean seamBreak() {
        return flag("seamBreak", true);
    }

    /** @return {@code breakOpportunities}, default true */
    public boolean breakOpportunities() {
        return flag("breakOpportunities", true);
    }

    /** @return {@code boundKeepChains}, default true */
    public boolean boundKeepChains() {
        return flag("boundKeepChains", true);
    }

    /**
     * @return {@code keepChainPenalty}, default {@link #DEFAULT_KEEP_CHAIN_PENALTY}, an integer, and less than
     *     {@link KnuthElement#INFINITE}
     */
    public int keepChainPenalty() {
        int reduced;
        String v = value("keepChainPenalty");
        try {
            reduced = v == null ? DEFAULT_KEEP_CHAIN_PENALTY : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            reduced = DEFAULT_KEEP_CHAIN_PENALTY;
        }
        return reduced >= KnuthElement.INFINITE ? KnuthElement.INFINITE - 1 : reduced;
    }

    /** @return {@code keepChainTolerance}, default {@link #DEFAULT_KEEP_CHAIN_TOLERANCE}, at least 1 */
    public double keepChainTolerance() {
        return Math.max(1.0, number("keepChainTolerance", DEFAULT_KEEP_CHAIN_TOLERANCE));
    }

    /** @return {@code keepChainRowTolerance}, default {@link #DEFAULT_KEEP_CHAIN_ROW_TOLERANCE}, at least 1 */
    public double keepChainRowTolerance() {
        return Math.max(1.0, number("keepChainRowTolerance", DEFAULT_KEEP_CHAIN_ROW_TOLERANCE));
    }
}
