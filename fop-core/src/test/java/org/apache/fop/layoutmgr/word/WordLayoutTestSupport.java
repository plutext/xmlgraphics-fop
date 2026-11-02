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
 * Apache FOP 2.12: Word mode's tests' settings (fop/CR-031, docx4j CR-071). See README.md, "Changes from Apache FOP
 * 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr.word;

import java.util.HashMap;
import java.util.Map;

/**
 * What Word mode's tests set: docx4j's property names, as its {@code WordLayoutCustomizer} declared them, and the
 * settings map built from the system properties of that prefix, which is docx4j's resolution (a system property
 * first) without its {@code docx4j.properties} file. The tests moved from docx4j set a property, build a factory,
 * and clear it.
 */
public final class WordLayoutTestSupport {

    public static final String MAX_SPACE_SHRINK = WordLayoutSettings.PREFIX + "maxSpaceShrink";
    public static final String MAX_HYPHEN_SPACE_SHRINK = WordLayoutSettings.PREFIX + "maxHyphenSpaceShrink";
    public static final String MIN_STRETCH_TO_COMPRESS = WordLayoutSettings.PREFIX + "minStretchToCompress";
    public static final String COMPRESSION_TO_STRETCH = WordLayoutSettings.PREFIX + "compressionToStretch";
    public static final String ENFORCE_HYPHENATION_ZONE = WordLayoutSettings.PREFIX + "hyphenationZone";
    public static final String LABEL_ASCENT_AGAINST_BASELINE = WordLayoutSettings.PREFIX + "labelAscentAgainstBaseline";
    public static final String LINE_BASELINE_FROM_BLOCK = WordLayoutSettings.PREFIX + "lineBaselineFromBlock";
    public static final String LEADER_GRID = WordLayoutSettings.PREFIX + "leaderGrid";
    public static final String WIDOWS_ACROSS_SOFT_RETURNS = WordLayoutSettings.PREFIX + "widowsAcrossSoftReturns";
    public static final String TAB_SPACES = WordLayoutSettings.PREFIX + "tabSpaces";
    public static final String LEADER_CHARACTERS = WordLayoutSettings.PREFIX + "leaderCharacters";
    public static final String LABEL_SUFFIX_SPACE = WordLayoutSettings.PREFIX + "labelSuffixSpace";
    public static final String EMERGENCY_BREAK = WordLayoutSettings.PREFIX + "emergencyBreak";
    public static final String EMERGENCY_BREAK_TOLERANCE = WordLayoutSettings.PREFIX + "emergencyBreakTolerance";
    public static final String CELL_EMERGENCY_BREAK_TOLERANCE = WordLayoutSettings.PREFIX
            + "cellEmergencyBreakTolerance";
    public static final String CELL_EMERGENCY_BREAK_TOLERANCE_RATIO
            = WordLayoutSettings.PREFIX + "cellEmergencyBreakToleranceRatio";
    public static final String PAGE_NUMBER_PLACEHOLDER = WordLayoutSettings.PREFIX + "pageNumberPlaceholder";
    public static final String SEAM_BREAK = WordLayoutSettings.PREFIX + "seamBreak";
    public static final String BREAK_OPPORTUNITIES = WordLayoutSettings.PREFIX + "breakOpportunities";

    private WordLayoutTestSupport() {
    }

    /** @return the settings map: every system property under {@link WordLayoutSettings#PREFIX} */
    public static Map<String, String> settings() {
        Map<String, String> settings = new HashMap<String, String>();
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith(WordLayoutSettings.PREFIX)) {
                settings.put(name, System.getProperty(name));
            }
        }
        return settings;
    }

    /** FOP's own UAX #14 pair table back, which a Word-mode factory changed process-wide: called after each Word-mode
     *  test class, so that FOP's own tests later in the JVM see FOP's table ({@code LineBreakStatusTestCase}). */
    public static void restoreFopPairTable() {
        WordBreakOpportunities.restoreFopPairTable();
    }

    /** @return the settings as the managers read them */
    public static WordLayoutSettings wordLayoutSettings() {
        return new WordLayoutSettings(settings());
    }

    /** @return {@code tabSpaces} as the managers read it */
    public static boolean tabSpaces() {
        return wordLayoutSettings().tabSpaces();
    }

    /** @return {@code leaderCharacters} as the managers read it */
    public static boolean leaderCharacters() {
        return wordLayoutSettings().leaderCharacters();
    }

    /** @param dflt the value where it is not set
     *  @return {@code emergencyBreakTolerance} as the managers read it */
    public static double emergencyBreakTolerance(double dflt) {
        return wordLayoutSettings().emergencyBreakTolerance(dflt);
    }
}
