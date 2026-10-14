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
 * Apache FOP 2.11: hook obstacles-in-the-line, the obstacles of a page-sequence, registered by the layout
 * managers that make them and taken up by the page breaker (fop/CR-024 phase A).
 * See README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr;

import java.util.ArrayList;
import java.util.List;

/**
 * The obstacles of a page-sequence (hook obstacles-in-the-line, fop/CR-024 phase A). A layout manager that makes
 * an obstacle registers it when it makes its elements, before the page breaker has placed the box that holds it;
 * the page breaker, meeting a box, asks for the obstacles not yet started whose layout manager lies below one in
 * the box's position chain, since the container is laid out inside its wrapper and only the wrapper's box is in
 * the page's list.
 */
public final class ObstacleRegistry {

    private final List<Obstacle> obstacles = new ArrayList<Obstacle>();

    /** @param obstacle an obstacle made by a layout manager of this page-sequence */
    public void register(Obstacle obstacle) {
        obstacles.add(obstacle);
    }

    /** @return whether any obstacle is registered and not yet started */
    public boolean hasPending() {
        for (Obstacle o : obstacles) {
            if (!o.isStarted()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The first obstacle not yet started that a box holds: one whose layout manager is, or descends from, a
     * layout manager in the box's position chain.
     * @param position the box's position
     * @return the obstacle, or null
     */
    public Obstacle firstHeldBy(Position position) {
        if (obstacles.isEmpty() || position == null) {
            return null;
        }
        for (Obstacle o : obstacles) {
            if (!o.isStarted() && heldBy(position, o.getLayoutManager())) {
                return o;
            }
        }
        return null;
    }

    private static boolean heldBy(Position position, LayoutManager obstacleLM) {
        for (Position p = position; p != null; p = next(p)) {
            LayoutManager lm = p.getLM();
            if (lm == null) {
                continue;
            }
            for (LayoutManager a = obstacleLM; a != null; a = a.getParent()) {
                if (a == lm) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The position wrapped by this one, or null at the end of the chain: a position may return itself. */
    private static Position next(Position p) {
        Position n = p.getPosition();
        return (n == p) ? null : n;
    }
}
