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

/* Modified by Plutext Pty Ltd for the docx4j FO renderer (docx4j-fo-renderer), a modified distribution derived
 * from Apache FOP 2.11: hook gsub-features, a default performSubstitution overload taking the delta. See
 * README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.complexscripts.fonts;

import java.util.List;

// CSOFF: LineLengthCheck

/**
 * <p>Optional interface which indicates that glyph substitution is supported and, if supported,
 * can perform substitution.</p>
 *
 * <p>This work was originally authored by Glenn Adams (gadams@apache.org).</p>
 */
public interface Substitutable {

    /**
     * Determines if font performs glyph substitution.
     * @return true if performs substitution.
     */
    boolean performsSubstitution();

    /**
     * Perform substitutions on characters to effect glyph substitution. If some substitution is performed, it
     * entails mapping from one or more input characters denoting textual character information to one or more
     * output character codes denoting glyphs in this font, where the output character codes may make use of
     * private character code values that have significance only for this font.
     * @param cs character sequence to map to output font encoding character sequence
     * @param script a script identifier
     * @param language a language identifier
     * @param associations optional list to receive list of character associations
     * @param retainControls if true, then retain control characters and their glyph mappings, otherwise remove
     * @return output sequence (represented as a character sequence, where each character in the returned sequence
     * denotes "font characters", i.e., character codes that map directly (1-1) to their associated glyphs
     */
    CharSequence performSubstitution(CharSequence cs, String script, String language, List associations, boolean retainControls);

    /**
     * As above, with a delta over the GSUB features the script's processor would apply.
     * An implementor that cannot honour it should ignore it and substitute as before.
     *
     * <p>Declared abstract rather than as a Java 8 default method because the checkstyle
     * this build pins, 2.14, cannot parse a default method and fails the whole file.
     * Upstream raised its checkstyle in FOP-3281, so this can become a default method at
     * the merge of Apache's main.</p>
     *
     * @param cs character sequence
     * @param script a script identifier
     * @param language a language identifier
     * @param associations optional list to be populated with association objects
     * @param retainControls if true, retain control characters
     * @param gsubFeatures delta tokens like -liga and +clig, or null
     * @return the substituted sequence
     */
    CharSequence performSubstitution(CharSequence cs, String script, String language, List associations,
                                     boolean retainControls, String[] gsubFeatures);

    /**
     * Reorder combining marks in character sequence so that they precede (within the sequence) the base
     * character to which they are applied. N.B. In the case of LTR segments, marks are not reordered by this,
     * method since when the segment is reversed by BIDI processing, marks are automatically reordered to precede
     * their base character.
     * @param cs character sequence within which combining marks to be reordered
     * @param gpa associated glyph position adjustments (also reordered)
     * @param script a script identifier
     * @param language a language identifier
     * @param associations optional list of associations to be reordered
     * @return output sequence containing reordered "font characters"
     */
    CharSequence reorderCombiningMarks(CharSequence cs, int[][] gpa, String script, String language, List associations);

}
