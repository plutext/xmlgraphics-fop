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
 * Apache FOP 2.12: Word mode's leading glue, moved from docx4j-export-fo (fop/CR-031, docx4j CR-071). See
 * README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr.word;

import org.apache.fop.layoutmgr.KnuthGlue;

/**
 * The extra leading below a line in Word mode (fop/CR-031), as a Knuth glue: counted between lines, discarded when
 * a page break is taken just before it (FOP, like Knuth, treats glue after a taken break as belonging to the page
 * before it). That is Word's rule: the last line of a page keeps only its text box within the margins, and a page's
 * first line starts with its ascent. The flow manager moves the glue of a paragraph's last line behind the break
 * possibility that follows the paragraph. Moved from docx4j's
 * {@code org.docx4j.fop.wordlayout.LeadingGlue}.
 */
public class LeadingGlue extends KnuthGlue {

    /**
     * @param width the leading, in millipoints
     */
    public LeadingGlue(int width) {
        super(width, 0, 0, null, true);
    }
}
