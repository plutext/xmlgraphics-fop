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
 * Apache FOP 2.11: a test that the FLOAT_RESTART flag (fop/CR-020) has a bit of its own. See README.md, "Changes
 * from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A layout context's flags are independent: a block read again after a side float's edge is not content to be
 * treated as an artifact, nor the reverse.
 */
public class LayoutContextFlagsTestCase {

    /** Setting FLOAT_RESTART does not make the content an artifact. */
    @Test
    public void testFloatRestartIsNotAnArtifact() {
        LayoutContext context = LayoutContext.newInstance();
        context.setFlags(LayoutContext.FLOAT_RESTART, true);
        assertTrue(context.isFloatRestart());
        assertFalse(context.treatAsArtifact());
    }

    /** Content treated as an artifact is not a block read again after a float's edge. */
    @Test
    public void testArtifactIsNotAFloatRestart() {
        LayoutContext context = LayoutContext.newInstance();
        context.setTreatAsArtifact(true);
        assertTrue(context.treatAsArtifact());
        assertFalse(context.isFloatRestart());
    }
}
