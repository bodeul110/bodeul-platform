package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;

import com.example.bodeul.R;
import org.hamcrest.Matcher;

/** 실제 목록/입력 왕복을 거쳐 기존 저장·이탈 테스트를 이어간다. */
final class ManagerGuideTestNavigation {
    private ManagerGuideTestNavigation() {}

    static void openCurrentStep() {
        onView(withId(R.id.finalGuideTimelineScroll)).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.buttonFinalGuideOpenStep), withEffectiveVisibility(Visibility.VISIBLE)))
                .perform(scrollTo(), click());
    }

    static void assertDetailTitle(int title) {
        onView(withId(R.id.textFinalGuideToolbar)).check(matches(allOf(isDisplayed(), withText(title))));
        onView(withId(R.id.finalGuideDetailScroll)).check(matches(isDisplayed()));
    }

    static void requestExitFromDetails() {
        onView(withId(R.id.buttonFinalGuideBack)).perform(click());
        onView(withId(R.id.finalGuideTimelineScroll)).check(matches(isDisplayed()));
        onView(withId(R.id.buttonFinalGuideBack)).perform(click());
    }

    static ViewAction clickAfterScrollSettles() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return allOf(isDisplayed(), isEnabled()); }
            @Override public String getDescription() { return "입력 오류와 키보드의 스크롤이 멈춘 뒤 버튼 클릭"; }
            @Override public void perform(UiController controller, View view) {
                long deadline = SystemClock.uptimeMillis() + 3000;
                Rect previous = new Rect();
                int stableFrames = 0;
                while (SystemClock.uptimeMillis() < deadline) {
                    controller.loopMainThreadForAtLeast(100);
                    Rect current = new Rect();
                    boolean visible = view.getGlobalVisibleRect(current)
                            && current.height() == view.getHeight();
                    stableFrames = visible && current.equals(previous) ? stableFrames + 1 : 0;
                    previous.set(current);
                    if (stableFrames >= 3) {
                        click().perform(controller, view);
                        return;
                    }
                    if (!visible) scrollTo().perform(controller, view);
                }
                throw new AssertionError("버튼이 안정적으로 표시되지 않았습니다.");
            }
        };
    }
}
