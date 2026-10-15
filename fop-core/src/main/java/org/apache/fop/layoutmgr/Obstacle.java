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
 * Apache FOP 2.11: hook obstacles-in-the-line, a rectangle on the page the lines wrap around (fop/CR-024 phase A).
 * See README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr;

import org.apache.fop.fo.Constants;

/**
 * An obstacle in the line: a rectangle the lines of a page wrap around, grown by the distances the text keeps
 * from it, with a side rule (hook obstacles-in-the-line, fop/CR-024 phase A). An absolutely positioned
 * {@code fo:block-container} with {@code fox:wrap} is one; its rectangle is page-relative when the container is
 * {@code fixed}, and relative to the top of the box that holds it and the column's start edge when it is
 * {@code absolute}. The page breaker meets the box that holds the container, registers the obstacle for the page
 * that box lands on, and handles it as a side float whose area is already drawn: the lines from the first legal
 * break before the rectangle's top to the first after its foot are narrowed to the side the rule names.
 */
public final class Obstacle {

    private final LayoutManager layoutManager;
    private final boolean pageRelative;
    private final int top;
    private final int left;
    private final int width;
    private final int height;
    private final int wrap;
    private final int sides;
    private final int distanceBefore;
    private final int distanceAfter;
    private final int distanceStart;
    private final int distanceEnd;
    private boolean started;

    /**
     * @param layoutManager the layout manager of the container, by which the box holding it is recognised
     * @param pageRelative whether the rectangle is in page coordinates (a fixed container)
     * @param top the rectangle's top edge, in millipoints
     * @param left the rectangle's start edge, in millipoints
     * @param width the rectangle's inline size, in millipoints
     * @param height the rectangle's block size, in millipoints
     * @param wrap {@link Constants#EN_SQUARE} or {@link Constants#EN_TOP_AND_BOTTOM}
     * @param sides {@link Constants#EN_BOTH}, {@link Constants#EN_LEFT}, {@link Constants#EN_RIGHT} or
     * {@link Constants#EN_LARGEST}
     * @param distances the distances the lines keep from the rectangle: before, after, start, end, in millipoints
     */
    public Obstacle(LayoutManager layoutManager, boolean pageRelative, int top, int left, int width, int height,
            int wrap, int sides, int[] distances) {
        this.layoutManager = layoutManager;
        this.pageRelative = pageRelative;
        this.top = top;
        this.left = left;
        this.width = width;
        this.height = height;
        this.wrap = wrap;
        this.sides = sides;
        this.distanceBefore = distances[0];
        this.distanceAfter = distances[1];
        this.distanceStart = distances[2];
        this.distanceEnd = distances[3];
    }

    /** @return the layout manager of the container that is the obstacle */
    public LayoutManager getLayoutManager() {
        return layoutManager;
    }

    /** @return whether the rectangle is in page coordinates */
    public boolean isPageRelative() {
        return pageRelative;
    }

    /** @return the top of the band the lines keep out of: the rectangle's top less the distance before */
    public int getBandTop() {
        return top - distanceBefore;
    }

    /** @return the foot of the band the lines keep out of: the rectangle's foot plus the distance after */
    public int getBandBottom() {
        return top + height + distanceAfter;
    }

    /** The least room beside an obstacle that counts as room for the lines: 1pt, in millipoints (§11.20). */
    public static final int MIN_ROOM = 1000;

    /** @return the start edge of the room the lines keep out of: the rectangle's start less the distance start */
    public int getRoomStart() {
        return left - distanceStart;
    }

    /** @return the end edge of the room the lines keep out of: the rectangle's end plus the distance end */
    public int getRoomEnd() {
        return left + width + distanceEnd;
    }

    /** @return {@link Constants#EN_SQUARE} or {@link Constants#EN_TOP_AND_BOTTOM} */
    public int getWrap() {
        return wrap;
    }

    /** @return the side rule */
    public int getSides() {
        return sides;
    }

    /** @return whether the page breaker has taken this obstacle up */
    public boolean isStarted() {
        return started;
    }

    /** The page breaker has taken the obstacle up, or found it has no effect on the lines. */
    public void setStarted() {
        started = true;
    }

    /**
     * The narrowing of the column the obstacle asks for, by its side rule, as the start and end intrusion
     * adjustments a side float sets: for {@code top-and-bottom} the whole column; for {@code square} the room
     * from the column's start edge to the obstacle's end, or from its start to the column's end edge, the lines
     * going on the other side; the whole column where the side the rule names has no room, so that the lines go
     * below (§11.4). {@code both} is laid as {@code largest} in phase A, which lays a line in one piece.
     *
     * @param roomStart the obstacle's room, start edge, relative to the column's start edge
     * @param roomEnd the obstacle's room, end edge, relative to the column's start edge
     * @param columnWidth the column's inline size
     * @return the start and end intrusion adjustments, or null when the obstacle lies outside the column
     */
    public int[] intrusionsIn(int roomStart, int roomEnd, int columnWidth) {
        int start = Math.max(0, roomStart);
        int end = Math.min(columnWidth, roomEnd);
        if (end <= start) {
            return null;
        }
        if (wrap == Constants.EN_TOP_AND_BOTTOM) {
            return new int[] {columnWidth, 0};
        }
        int freeStart = start;
        int freeEnd = columnWidth - end;
        boolean textAtStart;
        if (sides == Constants.EN_LEFT) {
            textAtStart = true;
        } else if (sides == Constants.EN_RIGHT) {
            textAtStart = false;
        } else {
            textAtStart = freeStart >= freeEnd;
        }
        if ((textAtStart ? freeStart : freeEnd) < MIN_ROOM) {
            // no room on the side the rule names: the lines go below, as for top-and-bottom (§11.4). A sliver
            // under a point is none: a letterhead table 0.25pt short of the column's end edge left lines of that
            // width beside it, and the flow's table laid there was lost (§11.20)
            return new int[] {columnWidth, 0};
        }
        return textAtStart ? new int[] {0, columnWidth - start} : new int[] {end, 0};
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Obstacle[" + (pageRelative ? "page " : "anchor ") + left + "," + top + " " + width + "x" + height
                + " wrap=" + wrap + " sides=" + sides + (started ? " started" : "") + "]";
    }
}
