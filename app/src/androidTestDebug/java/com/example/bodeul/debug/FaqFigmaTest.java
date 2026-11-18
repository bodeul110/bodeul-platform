package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.ViewInteraction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.bodeul.R;
import com.example.bodeul.ui.support.FaqActivity;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FaqFigmaTest {
    @Test public void initialLayoutHasDesignOrderAndReadableFullSizeTargets() {
        try (ActivityScenario<FaqActivity> screen = ActivityScenario.launch(FaqActivity.class)) {
            onView(withId(R.id.faqTitle)).check(matches(isDisplayed()));
            screen.onActivity(a -> {
                ViewGroup filters = a.findViewById(R.id.faqFilters);
                int[] labels = {R.string.faq_filter_all, R.string.faq_filter_matching, R.string.faq_filter_payment,
                        R.string.faq_filter_service, R.string.faq_filter_other};
                assertEquals(labels.length, filters.getChildCount());
                for (int i = 0; i < labels.length; i++) {
                    TextView chip = (TextView) filters.getChildAt(i);
                    assertEquals(a.getString(labels[i]), chip.getText().toString());
                    assertEquals(i == 0, chip.isSelected());
                    assertTrue(chip.getHeight() >= dp(a, 48) && chip.getWidth() >= dp(a, 48));
                    assertTrue(ColorUtils.calculateContrast(chip.getCurrentTextColor(),
                            a.getColor(i == 0 ? R.color.final_brand_primary : R.color.final_input_surface)) >= 4.5);
                }
                ViewGroup list = a.findViewById(R.id.faqRows);
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    assertEquals(700, ((TextView) a.findViewById(R.id.faqTitle)).getTypeface().getWeight());
                    assertEquals(600, ((TextView) list.getChildAt(0).findViewById(R.id.faqQuestion)).getTypeface().getWeight());
                }
                int[] questions = {R.string.faq_service_question, R.string.faq_booking_question, R.string.faq_manager_question, R.string.faq_payment_question};
                assertEquals(questions.length, list.getChildCount());
                for (int i = 0; i < questions.length; i++) {
                    View row = list.getChildAt(i);
                    assertEquals(a.getString(questions[i]), ((TextView) row.findViewById(R.id.faqQuestion)).getText().toString());
                    assertEquals(View.GONE, row.findViewById(R.id.faqAnswer).getVisibility());
                    assertEquals(dp(a, 14), row.findViewById(R.id.faqChevron).getWidth());
                    assertTrue(row.findViewById(R.id.faqToggle).getHeight() >= dp(a, 48));
                }
                assertTextFits(a.findViewById(R.id.faqRoot));
            });
            capture("list");
        }
    }

    @Test public void eachFilterAndEmptyStateRemainReachable() {
        try (ActivityScenario<FaqActivity> screen = ActivityScenario.launch(FaqActivity.class)) {
            int[][] cases = {{R.string.faq_filter_matching, 2}, {R.string.faq_filter_payment, 1},
                    {R.string.faq_filter_service, 1}, {R.string.faq_filter_other, 0}, {R.string.faq_filter_all, 4}};
            for (int[] item : cases) {
                chip(item[0]).perform(scrollTo(), click()).check(matches(isSelected()));
                screen.onActivity(a -> {
                    ViewGroup rows = a.findViewById(R.id.faqRows);
                    int count = 0;
                    for (int i = 0; i < rows.getChildCount(); i++) {
                        View row = rows.getChildAt(i);
                        if (row.getVisibility() != View.VISIBLE) continue;
                        count++;
                        if (item[0] != R.string.faq_filter_all) {
                            assertEquals(a.getString(item[0]), ((TextView) row.findViewById(R.id.faqCategory)).getText().toString());
                        }
                    }
                    assertEquals(item[1], count);
                    assertEquals(count == 0 ? View.VISIBLE : View.GONE, a.findViewById(R.id.faqEmpty).getVisibility());
                });
                if (item[1] == 0) capture("empty");
            }
        }
    }

    @Test public void openingAnotherQuestionClosesThePreviousAnswer() {
        try (ActivityScenario<FaqActivity> screen = ActivityScenario.launch(FaqActivity.class)) {
            int[][] items = {{R.string.faq_service_question, R.string.faq_service_answer},
                    {R.string.faq_booking_question, R.string.faq_booking_answer}, {R.string.faq_manager_question, R.string.faq_manager_answer},
                    {R.string.faq_payment_question, R.string.faq_payment_answer}};
            for (int i = 0; i < items.length; i++) {
                toggle(items[i][0]).perform(scrollTo(), click());
                onView(withText(items[i][1])).perform(scrollTo()).check(matches(isDisplayed()));
                if (i > 0) onView(withText(items[i - 1][1])).check(matches(withEffectiveVisibility(Visibility.GONE)));
                screen.onActivity(a -> assertTextFits(a.findViewById(R.id.faqRoot)));
                if (i == 1) capture("expanded");
            }
            toggle(R.string.faq_payment_question).perform(scrollTo(), click());
            onView(withText(R.string.faq_payment_answer)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test public void recreationRestoresOnlyTheVisibleSelectedAnswer() {
        try (ActivityScenario<FaqActivity> screen = ActivityScenario.launch(FaqActivity.class)) {
            chip(R.string.faq_filter_matching).perform(scrollTo(), click());
            toggle(R.string.faq_manager_question).perform(scrollTo(), click());
            screen.recreate();
            chip(R.string.faq_filter_matching).check(matches(isSelected()));
            onView(withText(R.string.faq_manager_answer)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
            toggle(R.string.faq_manager_question).check((view, error) -> {
                if (error != null) throw error;
                assertTrue(view.isFocusable());
                assertEquals(view.getResources().getString(R.string.faq_expanded), ViewCompat.getStateDescription(view));
            });
            chip(R.string.faq_filter_other).perform(scrollTo(), click());
            screen.recreate();
            onView(withId(R.id.faqEmpty)).check(matches(isDisplayed()));
            chip(R.string.faq_filter_all).perform(scrollTo(), click());
            onView(withText(R.string.faq_manager_answer)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    private static ViewInteraction chip(int label) {
        return onView(allOf(withParent(withId(R.id.faqFilters)), withText(label)));
    }
    private static ViewInteraction toggle(int title) {
        return onView(allOf(withId(R.id.faqToggle), hasDescendant(withText(title))));
    }
    private static int dp(FaqActivity a, int value) { return Math.round(value * a.getResources().getDisplayMetrics().density); }

    private static void assertTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView text) {
            assertNotNull(text.getLayout());
            assertTrue("세로 잘림: " + text.getText(), text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
            for (int i = 0; i < text.getLineCount(); i++) {
                assertEquals(0, text.getLayout().getEllipsisCount(i));
                assertTrue("가로 잘림: " + text.getText(), text.getLayout().getLineMax(i) <= text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 1);
            }
        }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) assertTextFits(group.getChildAt(i));
    }

    private static void capture(String name) {
        String prefix = InstrumentationRegistry.getArguments().getString("captureFaq");
        if (prefix == null) return;
        var run = InstrumentationRegistry.getInstrumentation();
        run.waitForIdleSync();
        Bitmap bitmap = run.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File dir = run.getTargetContext().getFilesDir();
        try (FileOutputStream out = new FileOutputStream(new File(dir, prefix + "-" + name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } catch (Exception e) { throw new AssertionError(e); }
        finally { bitmap.recycle(); }
    }
}
