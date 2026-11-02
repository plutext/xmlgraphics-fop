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
 * Apache FOP 2.12: Word mode's leaders, moved from docx4j-export-fo's LBP (fop/CR-031, docx4j CR-071). See
 * README.md, "Changes from Apache FOP 2.11". */

/* $Id$ */

package org.apache.fop.layoutmgr.inline;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import org.apache.fop.area.Trait;
import org.apache.fop.area.inline.AbstractTextArea;
import org.apache.fop.area.inline.FilledArea;
import org.apache.fop.area.inline.InlineArea;
import org.apache.fop.area.inline.InlineParent;
import org.apache.fop.area.inline.Space;
import org.apache.fop.area.inline.TextArea;
import org.apache.fop.fo.Constants;
import org.apache.fop.fo.flow.Leader;
import org.apache.fop.fonts.Font;
import org.apache.fop.layoutmgr.LayoutManager;
import org.apache.fop.layoutmgr.TraitSetter;
import org.apache.fop.traits.MinOptMax;

/**
 * Word mode's leaders (fop/CR-031): a tab's leader given the width and the pattern of the stop the tab reached, a
 * leader run's characters stepped on Word's 1/300 inch grid and the run begun on a whole multiple of its step from
 * the page's edge, and the blank Word writes as a space character. Moved from docx4j's {@code LBP}, where it reached
 * these areas and managers by reflection on Apache FOP and through the hooks {@code leader-placement} and
 * {@code inline-access} on the docx4j FO renderer; here it calls those accessors.
 */
public final class WordLeaders {

    /** Leader kinds, as {@code w:leader} gives them. The three character kinds are drawn as a run of that
     *  character, which is what Word's own PDF carries for them; {@link #LEADER_RULE} is the drawn line
     *  {@code w:leader="heavy"} asks for. */
    public static final int LEADER_NONE = 0;
    /** {@code w:leader="dot"} */
    public static final int LEADER_DOTS = 1;
    /** a rule */
    public static final int LEADER_RULE = 2;
    /** {@code w:leader="underscore"} or {@code "heavy"} as characters */
    public static final int LEADER_UNDERSCORE = 3;
    /** {@code w:leader="hyphen"} */
    public static final int LEADER_HYPHEN = 4;
    /** {@code w:leader="middleDot"} */
    public static final int LEADER_MIDDLE_DOT = 5;

    /** One 1/300 inch, the grid Word's layout works in, in millipoints. */
    public static final int GRID_MPT = 240;

    /** Whether leader characters step on Word's grid ({@code leaderGrid}). */
    private final boolean grid;

    /**
     * @param grid whether leader characters step on Word's 1/300 inch grid ({@code leaderGrid})
     */
    public WordLeaders(boolean grid) {
        this.grid = grid;
    }

    /** The font of an fo:inline's layout manager, or null for other managers.
     *  @param lm the manager
     *  @return its font */
    public static Font inlineFont(LayoutManager lm) {
        return lm instanceof InlineLayoutManager ? ((InlineLayoutManager) lm).getFont() : null;
    }

    private static InlineArea currentArea(LayoutManager lm) {
        return lm instanceof LeafNodeLayoutManager ? ((LeafNodeLayoutManager) lm).getCurrentArea() : null;
    }

    private static void setCurrentArea(LayoutManager lm, InlineArea area) {
        ((LeafNodeLayoutManager) lm).setCurrentArea(area);
    }

    private static Font leaderFont(LayoutManager lm) {
        return lm instanceof LeaderLayoutManager ? ((LeaderLayoutManager) lm).getFont() : null;
    }

    /**
     * Give a leaf inline manager (here: an fo:leader standing in for a tab) the width the line manager worked out.
     * Its area's IPD comes from its own stored AreaInfo at addAreas time, not from the Knuth element, so both have
     * to be set.
     * @param lm the manager
     * @param ipd the width, in millipoints
     */
    public static void setLeafIPD(LayoutManager lm, int ipd) {
        if (lm instanceof LeafNodeLayoutManager) {
            ((LeafNodeLayoutManager) lm).setAreaInfoIPD(MinOptMax.getInstance(ipd));
        }
    }

    /** The area a leaf inline manager (an fo:leader standing in for a tab) will add to the line, so that it can be
     *  found again once the line's areas exist.
     *  @param lm the manager
     *  @return its area, or null */
    public static InlineArea leafArea(LayoutManager lm) {
        return currentArea(lm);
    }

    /** The character a leader kind repeats, or 0 for a kind which is not a character. Read off the
     *  {@code tab-leader-kinds} golden, where Word draws every kind but {@code none} as characters: {@code .} and
     *  {@code ·} on their own advances, {@code -}, and {@code _} for both underscore and heavy.
     *  @param kind a leader kind
     *  @return its character, or 0 */
    public static char leaderChar(int kind) {
        switch (kind) {
        case LEADER_DOTS:
            return '.';
        case LEADER_MIDDLE_DOT:
            return '·';
        case LEADER_UNDERSCORE:
            return '_';
        case LEADER_HYPHEN:
            return '-';
        default:
            return 0;
        }
    }

    /** @param kind a leader kind
     *  @return whether it is drawn as a run of a character rather than as a rule */
    public static boolean isCharacterLeader(int kind) {
        return leaderChar(kind) != 0;
    }

    /** An advance rounded to Word's 1/300 inch grid, which is the step its leader characters take.
     *  @param mpt an advance, in millipoints
     *  @return the advance on the grid */
    public static int roundToGrid(int mpt) {
        if (mpt <= 0) {
            return mpt;
        }
        int units = (mpt + GRID_MPT / 2) / GRID_MPT;
        return units > 0 ? units * GRID_MPT : GRID_MPT;
    }

    /**
     * Put a leader run FOP built on Word's step: its characters repeat on the advance rounded to the 1/300 inch
     * grid, and the difference is written as a character spacing.
     *
     * <p>Read out of the tab-leader-kinds golden's stream, all in Calibri 11.04 - the paragraph mark's font. A full
     * stop and a middle dot advance 2.782pt and are drawn {@code 0.0979 Tc} apart, a step of 2.8816 (twelve cells);
     * a hyphen advances 3.380 and is drawn {@code -0.0182 Tc} apart, 3.3616 (fourteen - so the advance is rounded,
     * and not rounded up); an underscore advances 5.500 and is drawn {@code 0.0221 Tc} apart, 5.5222
     * (twenty-three). The spacing can be negative, which a spacer cannot be, so it goes on the character.</p>
     *
     * <p>A leader the FO gave a pattern width of its own keeps it: that width is the author's, not the
     * font's.</p>
     */
    private void setGridStep(Object area) {
        if (!grid || !(area instanceof FilledArea)) {
            return;
        }
        FilledArea run = (FilledArea) area;
        List<InlineArea> kids = run.getChildAreas();
        if (kids == null || kids.size() != 1 || !(kids.get(0) instanceof TextArea)) {
            return;         // a spacer: the FO's own width
        }
        TextArea c = (TextArea) kids.get(0);
        if (run.getUnitWidth() != c.getIPD()) {
            return;         // likewise
        }
        if (gridStep(c, c.getIPD()) > 0) {
            run.setUnitWidth(c.getIPD());
        }
    }

    /** Step this leader character by {@code advance} rounded to Word's grid, as a character spacing on the
     *  character itself; the step, or 0 where it is the advance already. */
    private static int gridStep(TextArea c, int advance) {
        int pitch = roundToGrid(advance);
        if (pitch <= 0 || pitch == advance) {
            return 0;
        }
        c.setTextLetterSpaceAdjust(pitch - advance);
        c.setIPD(pitch);
        return pitch;
    }

    /**
     * The blank Word leaves before the first character of a leader run whose step is {@code period}, where the run
     * begins {@code from} millipoints from the <b>page's</b> left edge; 0 where the run begins where it is.
     *
     * <p>Word puts the run on a whole multiple of its step measured from the page edge, and takes the tab's start
     * down to the 1/300 inch grid first. On the tab-leader-kinds golden every run does sit on such a multiple: its
     * hyphen runs open at 22, 46 and 45 steps of 3.3612, its underscore runs at 14, 23, 26, 27, 30, 32 and 59 steps
     * of 5.522, its dot and middle-dot runs at 25, 45, 47, 57 and 60 steps of 2.881. The floor is what puts the P09
     * run at 46 steps (154.62) rather than 47 (157.98) when the text before it ends at 154.776 - a fraction of a
     * cell past the 46th step - so that Word's run there opens a touch <em>behind</em> the text and carries no
     * space.</p>
     * @param from where the run begins, in millipoints from the page's left edge
     * @param period the run's step
     * @return the blank
     */
    public static int gridPhase(int from, int period) {
        if (period <= 0 || from <= 0) {
            return 0;
        }
        int step = (period + GRID_MPT / 2) / GRID_MPT;      // the step, in grid cells
        if (step <= 0) {
            return 0;
        }
        int cells = from / GRID_MPT;                         // the tab's start, floored
        int start = ((cells + step - 1) / step) * step * GRID_MPT;
        int phase = start - from;
        return phase > 0 ? phase : 0;
    }

    /**
     * Give a tab's leader the leader of the stop it turned out to reach, and where {@code spaces} is set a tab
     * which reaches a stop with no leader carries a <b>space character</b> of its whole advance, as Word's PDF
     * does, rather than a jump of the same width.
     *
     * <p>The FO cannot know which stop that will be, so it asks FOP for the paragraph's own leader and the line
     * manager settles it here: a stop with no leader blanks the area, and one whose leader is the other kind - a
     * paragraph mixing dot and rule stops - gets an area built the way
     * {@code LeaderLayoutManager.getLeaderInlineArea} builds it. The replacement is hung on the leader's own
     * alignment context, which FOP made for the pattern the FO asked for, so the characters of a replaced area sit
     * on the leader's rule thickness rather than on their own height.</p>
     *
     * <p>XSL FO offers {@code dots} and {@code rule} and no other repeating glyph, so an underscore or hyphen
     * leader asks FOP for a rule and is replaced here by a run of the character itself - which is what Word draws,
     * and the only form of it that reaches the PDF's text layer. {@code w:leader="heavy"} stays a rule.</p>
     *
     * @param lm the leader's manager
     * @param kind one of the {@code LEADER_} kinds
     * @param width the tab's advance, in millipoints
     * @param spaces whether a blank is written as a space character
     */
    public void setLeaderPattern(LayoutManager lm, int kind, int width, boolean spaces) {
        unphase(lm);
        if (kind == LEADER_NONE) {
            blankLeaderArea(lm, width, spaces);
            return;
        }
        if (!(lm.getFObj() instanceof Leader)) {
            return;
        }
        Leader fobj = (Leader) lm.getFObj();
        int pattern = fobj.getLeaderPattern();
        boolean asFopBuiltIt = (kind == LEADER_DOTS && pattern == Constants.EN_DOTS)
                || (kind == LEADER_RULE && pattern == Constants.EN_RULE);
        InlineArea area = currentArea(lm);
        if (asFopBuiltIt) {
            // FOP built the area this stop wants, and it is the one that hangs on the
            // alignment context the FO asked for; all it needs is Word's step
            if (isCharacterLeader(kind)) {
                setGridStep(area);
            }
            return;
        }
        if (area == null) {
            return;
        }
        int thickness = fobj.getRuleThickness().getValue(lm);
        char c = leaderChar(kind);
        InlineArea fresh = c == 0
                ? ruleArea(fobj, thickness, area.getBidiLevel())
                : charArea(lm, fobj, thickness, area.getBidiLevel(), c);
        if (fresh != null) {
            setCurrentArea(lm, fresh);
        }
    }

    private static InlineArea ruleArea(Leader fobj, int thickness, int level) {
        if (fobj.getRuleStyle() == Constants.EN_NONE) {
            return null;
        }
        org.apache.fop.area.inline.Leader rule = new org.apache.fop.area.inline.Leader();
        rule.setRuleStyle(fobj.getRuleStyle());
        rule.setRuleThickness(thickness);
        rule.setBPD(thickness);
        rule.addTrait(Trait.COLOR, fobj.getColor());
        if (level >= 0) {
            rule.setBidiLevel(level);
        }
        return rule;
    }

    /** The area a leader of repeated characters is drawn as, built the way
     *  {@code LeaderLayoutManager.getLeaderInlineArea} builds its dots.
     *  @param c the character the leader repeats ('.', '_' or '-') */
    private InlineArea charArea(LayoutManager lm, Leader fobj, int thickness, int level, char c) {
        Font font = leaderFont(lm);
        if (font == null || !font.hasChar(c)) {
            return null;
        }
        int width = font.getCharWidth(c);
        if (width <= 0) {
            return null;
        }
        TextArea dot = new TextArea();
        int[] levels = (level < 0) ? null : new int[] {level};
        dot.addWord(String.valueOf(c), width, null, levels, null, 0);
        dot.setIPD(width);
        dot.setBPD(width);
        // FOP would put the character's baseline at its own height; this area hangs on an
        // alignment context built for the pattern the FO asked for, whose height is the
        // leader's rule thickness, so the baseline goes there instead
        dot.setBaselineOffset(thickness);
        TraitSetter.addFontTraits(dot, font);
        dot.addTrait(Trait.COLOR, fobj.getColor());
        if (grid && gridStep(dot, width) > 0) {
            width = dot.getIPD();
        }
        Space spacer = null;
        int patternWidth = fobj.getLeaderPatternWidth().getValue(lm);
        if (patternWidth > width) {
            spacer = new Space();
            spacer.setIPD(patternWidth - width);
            if (level >= 0) {
                spacer.setBidiLevel(level);
            }
            width = patternWidth;
        }
        FilledArea filled = new FilledArea();
        filled.setUnitWidth(width);
        filled.addChildArea(dot);
        if (spacer != null) {
            filled.addChildArea(spacer);
        }
        filled.setBPD(dot.getBPD());
        if (level >= 0) {
            filled.setBidiLevel(level);
        }
        return filled;
    }

    /**
     * The area a tab's dot leader is drawn as, wrapping the blank Word's grid puts before the first dot around
     * FOP's own repeating area. A {@code FilledArea} repeats its unit from its own left edge, and
     * {@code leader-alignment="reference-area"} - which is what XSL FO offers for the grid - is inert in FOP's PDF
     * output, so the phase is a space of its own (see {@code WordLineLayoutManager.dotLeaderPhase}).
     *
     * <p>The dots, not this wrapper, are what a {@code TabPageNumberWidth} widens when the page number after the
     * tab resolves: the growth then reaches this area through {@code notifyIPDVariation} and on to the line, so
     * nothing else has to know.</p>
     */
    static final class PhasedLeaderArea extends InlineParent {
        private static final long serialVersionUID = 1L;
        private final InlineArea leader;

        PhasedLeaderArea(int phase, InlineArea leader) {
            this.leader = leader;
            Space gap = new Space();
            gap.setIPD(phase);
            gap.setBPD(leader.getBPD());
            if (leader.getBidiLevel() >= 0) {
                gap.setBidiLevel(leader.getBidiLevel());
            }
            addChildArea(gap);
            addChildArea(leader);
            setBPD(leader.getBPD());
        }

        /** The same, with the blank at each end of the run written as a space character: either end may be
         *  null. */
        PhasedLeaderArea(InlineArea lead, InlineArea leader, InlineArea tail) {
            this.leader = leader;
            if (lead != null) {
                addChildArea(lead);
            }
            addChildArea(leader);
            if (tail != null) {
                addChildArea(tail);
            }
            setBPD(leader.getBPD());
            if (leader.getBidiLevel() >= 0) {
                setBidiLevel(leader.getBidiLevel());
            }
        }

        InlineArea getLeader() {
            return leader;
        }
    }

    /** Take a leader area back out of its {@link PhasedLeaderArea}, so that a line laid out again is not wrapped
     *  twice. */
    private static void unphase(LayoutManager lm) {
        InlineArea area = currentArea(lm);
        if (area instanceof PhasedLeaderArea) {
            setCurrentArea(lm, ((PhasedLeaderArea) area).getLeader());
        }
    }

    /** The width of the unit a dot leader repeats - Word's dot grid period - or 0 where this tab's area does not
     *  repeat one.
     *  @param lm the leader's manager
     *  @return the width, in millipoints */
    public static int leaderUnitWidth(LayoutManager lm) {
        InlineArea area = currentArea(lm);
        if (area instanceof PhasedLeaderArea) {
            area = ((PhasedLeaderArea) area).getLeader();
        }
        return (area instanceof FilledArea) ? ((FilledArea) area).getUnitWidth() : 0;
    }

    /**
     * Start this tab's dot leader a blank of {@code phase} in, so that its dots fall on Word's grid; {@code phase}
     * 0 leaves the area as FOP built it. With {@code spaces} the blank at <b>each</b> end of the run is written as a
     * space character, which is what Word's own PDF carries there.
     *
     * <p>Word writes one there, in a text object of its own: a corpus table of contents whose stop has an
     * underscore leader carries {@code 1 0 0 1 154.87 609.79 Tm [( )] TJ} between the entry's text and
     * {@code 1 0 0 1 156.07 609.79 Tm [(____...)] TJ}, and another at 564.22 before the page number at 569.74. The
     * run itself opens on Word's grid, a fraction of a cell after the text
     * ({@code WordLineLayoutManager.dotLeaderPhase}), and the space fills that partial cell. (The glyph draws no
     * ink, so {@code mutool draw -F trace} does not list it; the claim is read out of the operators.) A space is
     * written only where there <em>is</em> a partial cell.</p>
     *
     * @param lm the leader's manager
     * @param phase the blank before the run
     * @param width the tab's whole width, of which the leader keeps what the blank leaves
     * @param spaces whether the blanks are written as space characters
     */
    public static void setLeaderPhase(LayoutManager lm, int phase, int width, boolean spaces) {
        unphase(lm);
        if (!spaces && (phase <= 0 || phase >= width)) {
            return;
        }
        if (phase < 0 || phase >= width) {
            phase = 0;
        }
        InlineArea area = currentArea(lm);
        if (!(area instanceof FilledArea)) {
            return;
        }
        FilledArea run = (FilledArea) area;
        if (!spaces) {
            run.setIPD(width - phase);
            setCurrentArea(lm, new PhasedLeaderArea(phase, run));
            return;
        }
        int period = run.getUnitWidth();
        if (period <= 0) {
            return;
        }
        int units = (width - phase) / period;
        if (units <= 0) {
            return;            // no room for a run: the tab stays as it is
        }
        int runWidth = units * period;
        int tail = width - phase - runWidth;
        Font font = leaderFont(lm);
        Color colour = (lm.getFObj() instanceof Leader) ? ((Leader) lm.getFObj()).getColor() : null;
        int baseline = 0;
        for (Object child : run.getChildAreas()) {
            if (child instanceof AbstractTextArea) {
                baseline = ((AbstractTextArea) child).getBaselineOffset();
                break;
            }
        }
        // only where there is a partial cell to write: Word's space is that cell, and
        // where its grid is already met it writes nothing there
        TextArea lead = phase <= 0 ? null
                : spaceArea(font, colour, phase, run.getBPD(), baseline, run.getBidiLevel());
        TextArea end = tail <= 0 ? null
                : spaceArea(font, colour, tail, run.getBPD(), baseline, run.getBidiLevel());
        if (lead == null && end == null) {
            if (phase <= 0) {
                return;
            }
            run.setIPD(width - phase);
            setCurrentArea(lm, new PhasedLeaderArea(phase, run));
            return;
        }
        run.setIPD(runWidth);
        setCurrentArea(lm, new PhasedLeaderArea(lead, run, end));
    }

    /**
     * One space character of exactly this advance, in the leader's own font: the blank Word writes where docx4j
     * jumped the pen. The glyph is blank, so the character spacing which gives it its advance (a PDF {@code Tc})
     * moves nothing; what it changes is what the text layer says.
     * @param font the font
     * @param colour the colour, or null
     * @param advance the advance, in millipoints
     * @param bpd the area's height
     * @param baselineOffset its baseline
     * @param level its bidi level
     * @return the space, or null where the font has none
     */
    public static TextArea spaceArea(Font font, Color colour, int advance, int bpd, int baselineOffset,
            int level) {
        if (font == null || !font.hasChar(' ') || advance < 0) {
            return null;
        }
        TextArea space = new TextArea();
        int[] levels = (level < 0) ? null : new int[] {level};
        space.addWord(" ", advance, null, levels, null, 0);
        space.setIPD(advance);
        space.setBPD(bpd);
        space.setBaselineOffset(baselineOffset);
        if (level >= 0) {
            space.setBidiLevel(level);
        }
        space.setTextLetterSpaceAdjust(advance - font.getCharWidth(' '));
        TraitSetter.addFontTraits(space, font);
        if (colour != null) {
            space.addTrait(Trait.COLOR, colour);
        }
        return space;
    }

    /** The area a page number's width transfer must reach (WordLineLayoutManager's TabPageNumberWidth): the dots
     *  themselves, inside their phase wrapper.
     *  @param area a leader's area
     *  @return the dots */
    public static InlineArea phasedLeader(InlineArea area) {
        return (area instanceof PhasedLeaderArea) ? ((PhasedLeaderArea) area).getLeader() : area;
    }

    /**
     * Put a leader area which is <b>already placed on a line</b> on Word's grid: its characters step on the
     * advance rounded to 1/300 inch, and the run begins on a whole multiple of that step measured from the page's
     * left edge; and with {@code lead} the blank in front of the run is written as a <b>space character</b> of
     * that advance rather than as a jump of the pen.
     *
     * <p>The manager variant ({@link #setLeaderPhase}) reaches into the leader's own layout manager, which is
     * where a <b>tab</b> is settled. A table-of-contents entry's stretching leader is not laid out by the line
     * manager at all - the justification gives it its width - and nothing positional reaches its layout manager
     * either, so it is done here, from the line, where the x is known.</p>
     *
     * <p><b>The line's width does not change.</b> The blank takes exactly what the dots give up: the run is shrunk
     * by the phase and the phase is written in front of it, so the wrapper measures what the leader measured. That
     * matters twice - the line is already justified when this runs, and this leader is the thing that absorbed the
     * slack. A space of the phase's own advance measures exactly what the blank measured
     * ({@code InlineParent.addChildArea} adds each child's allocated width to the parent's).</p>
     *
     * <p>That space is what Word's own PDF carries between a numbering label and its leader run: on the
     * {@code numbering-leader-kinds} golden every one of its five items shows the label, then a space in the
     * <b>leader's</b> face at the label's size, then the run.</p>
     *
     * @param run  the leader's filled area, as FOP built and stretched it
     * @param from where the run begins, in millipoints from the <b>page's</b> left edge
     * @param lead builds a space area of the advance it is given, or returns null; may be null
     * @return the area to put in the leader's place, or null where nothing is to change
     */
    public InlineArea gridPlacedLeader(FilledArea run, int from, IntFunction<InlineArea> lead) {
        if (!grid) {
            return null;
        }
        int period = gridPlacedStep(run);
        int width = run.getIPD();
        if (period <= 0 || width <= period) {
            return null;
        }
        int phase = gridPhase(from, period);
        if (phase <= 0 || phase >= width) {
            return null;
        }
        run.setIPD(width - phase);
        InlineArea space = (lead == null) ? null : lead.apply(phase);
        return (space == null) ? new PhasedLeaderArea(phase, run) : new PhasedLeaderArea(space, run, null);
    }

    /**
     * Word's step for a leader run which is <b>already on a line</b>, set on it; the step, or 0 where there is
     * nothing to change.
     *
     * <p>{@link #setGridStep} cannot be used on a placed run. FOP's {@code FilledArea.getChildAreas()} does not
     * return the repeating <em>unit</em> - it returns the unit repeated {@code getIPD() / unitWidth} times,
     * computed on the call - so on a leader whose width is settled it hands back fifty-odd copies and the one-child
     * test which identifies the unit fails. The unit itself is {@code FilledArea.getUnitAreas()}.</p>
     *
     * <p>Changing the unit width does not change the run's width: the run keeps the IPD it was stretched to and
     * simply holds whole cells of the new step, with the remainder at the end - which is what Word draws too.</p>
     */
    private static int gridPlacedStep(FilledArea run) {
        List<InlineArea> unit = run.getUnitAreas();
        if (unit == null || unit.size() != 1) {
            return run.getUnitWidth();
        }
        /* The unit is the character itself for a leader FOP built from its dot pattern,
         * and the inline its content was laid out by for one of leader-pattern
         * use-content - which is how a numbering tab's hyphen, underscore and middle-dot
         * leaders are written (XsltFOFunctions.numberingLeaderGlyph).  Every wrapper's own
         * width has to move with the character's, or the unit and the run's unit width
         * disagree and FOP paints the characters on the old step.  (CR-001 batch 48 item 2) */
        List<InlineParent> wrappers = new ArrayList<InlineParent>();
        InlineArea inner = unit.get(0);
        /* TextArea is itself an InlineParent - its own children are the WordAreas - so the
         * descent stops at it and not at the word inside it. */
        while (inner instanceof InlineParent && !(inner instanceof FilledArea) && !(inner instanceof TextArea)) {
            InlineParent wrapper = (InlineParent) inner;
            List<InlineArea> kids = wrapper.getChildAreas();
            if (kids == null || kids.size() != 1) {
                return run.getUnitWidth();
            }
            wrappers.add(wrapper);
            inner = kids.get(0);
        }
        if (!(inner instanceof TextArea)) {
            return run.getUnitWidth();
        }
        TextArea c = (TextArea) inner;
        if (run.getUnitWidth() != c.getIPD()) {
            return run.getUnitWidth();   // the FO's own width
        }
        int step = gridStep(c, c.getIPD());
        if (step > 0) {
            for (InlineParent wrapper : wrappers) {
                wrapper.setIPD(step);
            }
            run.setUnitWidth(step);
        }
        return run.getUnitWidth();
    }

    /** Replace a leader's area with a plain space of the same height: the tab reached a stop with no leader, but
     *  the FO could not know which stop that would be. With {@code spaces} the blank is a space <b>character</b> of
     *  the tab's whole advance, which is what Word's PDF writes for a tab.
     *  @param lm the leader's manager
     *  @param width the tab's advance, in millipoints
     *  @param spaces whether the blank is a space character */
    public static void blankLeaderArea(LayoutManager lm, int width, boolean spaces) {
        InlineArea old = currentArea(lm);
        if (old == null) {
            return;
        }
        if (spaces && width > 0) {
            Font font = leaderFont(lm);
            Color colour = (lm.getFObj() instanceof Leader) ? ((Leader) lm.getFObj()).getColor() : null;
            int baseline = (old instanceof AbstractTextArea)
                    ? ((AbstractTextArea) old).getBaselineOffset() : old.getBPD();
            TextArea space = spaceArea(font, colour, width, old.getBPD(), baseline, old.getBidiLevel());
            if (space != null) {
                setCurrentArea(lm, space);
                return;
            }
        }
        if (old instanceof Space) {
            return;
        }
        Space blank = new Space();
        blank.setBPD(old.getBPD());
        blank.setBidiLevel(old.getBidiLevel());
        setCurrentArea(lm, blank);
    }
}
