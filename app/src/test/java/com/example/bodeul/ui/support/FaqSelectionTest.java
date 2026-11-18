package com.example.bodeul.ui.support;

import com.example.bodeul.ui.support.FaqSelection.Category;
import com.example.bodeul.ui.support.FaqSelection.Question;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FaqSelectionTest {
    private final FaqSelection state = new FaqSelection();

    @Test public void initialListMatchesTheFourQuestionsInDesignOrder() {
        assertEquals(Category.ALL, state.category());
        assertNull(state.expanded());
        assertEquals(Arrays.asList(Question.SERVICE, Question.BOOKING, Question.MANAGER, Question.PAYMENT), visible());
    }

    @Test public void filterShowsOnlyItsOwnQuestionsAndOtherIsEmpty() {
        state.select(Category.MATCHING);
        assertEquals(Arrays.asList(Question.BOOKING, Question.MANAGER), visible());
        state.select(Category.OTHER);
        assertTrue(visible().isEmpty());
    }

    @Test public void onlyOneAnswerOpensAndASecondTapClosesIt() {
        state.toggle(Question.SERVICE);
        state.toggle(Question.BOOKING);
        assertEquals(Question.BOOKING, state.expanded());
        state.toggle(Question.BOOKING);
        assertNull(state.expanded());
    }

    @Test public void changingFiltersKeepsOnlyAStillVisibleAnswer() {
        state.toggle(Question.BOOKING);
        state.select(Category.MATCHING);
        assertEquals(Question.BOOKING, state.expanded());
        state.select(Category.PAYMENT);
        state.toggle(Question.BOOKING);
        assertNull(state.expanded());
        state.select(Category.ALL);
        assertNull(state.expanded());
    }

    @Test public void restoreUsesNamesAndRejectsStaleOrHiddenItems() {
        assertEquals(Question.MANAGER, FaqSelection.restore("MATCHING", "MANAGER").expanded());
        assertEquals(Category.ALL, FaqSelection.restore("REMOVED", "MANAGER").category());
        assertNull(FaqSelection.restore("REMOVED", "MANAGER").expanded());
        assertNull(FaqSelection.restore("OTHER", "BOOKING").expanded());
        assertNull(FaqSelection.restore("PAYMENT", "REMOVED").expanded());
        assertNull(FaqSelection.restore(null, null).expanded());
    }

    private List<Question> visible() {
        return Arrays.stream(Question.values()).filter(state::includes).collect(Collectors.toList());
    }
}
