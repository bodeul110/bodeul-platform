package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.booking.BookingActivity;
import com.example.bodeul.ui.booking.BookingFormBinder;

import org.junit.Test;
import org.junit.runner.RunWith;

/** 로컬 데이터 예약 미리보기가 Figma 단계 구조를 실제 BookingActivity에 표시하는지 확인한다. */
@RunWith(AndroidJUnit4.class)
public class BookingFigmaPreviewTest {
    @Test
    public void editingLegacyPrices_keepsSnapshotUntilCreateModeIsRestored() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = ActivityScenario.launch(
                BookingFigmaPreviewActivity.createIntent(context))) {
            scenario.onActivity(activity -> {
                // 테스트 전용 공개 API를 제품 코드에 추가하지 않고 기존 폼의 편집 상태를 검증한다.
                BookingFormBinder form;
                try {
                    java.lang.reflect.Field field = BookingActivity.class.getDeclaredField("formBinder");
                    field.setAccessible(true);
                    form = (BookingFormBinder) field.get(activity);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
                User patient = new User("patient-1", UserRole.PATIENT,
                        "합성 환자", "patient@example.com", "010-0000-0001");
                for (String method : new String[]{"CARD", "BANK_TRANSFER"}) {
                    AppointmentRequest legacy = new AppointmentRequest(
                            "legacy-price", "patient-1", "guardian-1", "검증 병원", "내과",
                            "2026-12-20 10:30", "1층", "합성 기록", AppointmentStatus.REQUESTED,
                            null, "합성 환자", "010-0000-0001", "patient@example.com",
                            "합성 보호자", "010-0000-0002", "guardian@example.com",
                            "이동 지원 요청", "", "WHEELCHAIR", "ROUND_TRIP", "ANY", method,
                            "FAMILY", 69_000, 37_000, 10_000, 96_000);
                    form.bindEditMode(patient, legacy);
                    assertEquals("예상 결제 금액 · 96,000원",
                            ((TextView) activity.findViewById(R.id.textBookingEstimateFinal)).getText().toString());
                    form.setLoading(true);
                    form.setLoading(false);
                    assertFalse(activity.findViewById(R.id.buttonBookingCouponFamily).isEnabled());
                    if ("BANK_TRANSFER".equals(method)) {
                        assertFalse(activity.findViewById(R.id.buttonBookingTripOneWay).isEnabled());
                    }
                }
                form.bindCreateMode(patient);
                assertEquals("예상 결제 금액 · 40,000원",
                        ((TextView) activity.findViewById(R.id.textBookingEstimateFinal)).getText().toString());
            });
        }
    }

    @Test
    public void newQuote_usesMvpPriceAndDoesNotOfferLegacyCoupons() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<BookingFigmaPreviewActivity> ignored = ActivityScenario.launch(
                BookingFigmaPreviewActivity.createIntent(context))) {
            onView(withId(R.id.textBookingPricePolicy)).perform(scrollTo())
                    .check(matches(withText(R.string.booking_price_mvp_policy)));
            onView(withId(R.id.textBookingEstimateFinal))
                    .check(matches(withText("예상 결제 금액 · 40,000원")));
            onView(withId(R.id.buttonBookingTripRoundTrip)).perform(scrollTo(), click());
            onView(withId(R.id.textBookingEstimateFinal)).perform(scrollTo())
                    .check(matches(withText("예상 결제 금액 · 40,000원")));
            onView(withId(R.id.buttonBookingCouponFamily))
                    .check(matches(withEffectiveVisibility(GONE)));
        }
    }

    @Test
    public void preview_recreationKeepsNavigationAboveRealSystemInsets() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = ActivityScenario.launch(
                BookingFigmaPreviewActivity.createIntent(context))) {
            assertNavigationHeight(scenario);
            scenario.recreate();
            assertNavigationHeight(scenario);
        }
    }

    private static void assertNavigationHeight(ActivityScenario<BookingFigmaPreviewActivity> scenario) {
        onView(withId(R.id.clientBottomNavigation)).check(matches(isCompletelyDisplayed()));
        scenario.onActivity(activity -> {
            View navigation = activity.findViewById(R.id.clientBottomNavigation);
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(navigation);
            assertNotNull(insets);
            int baseHeight = Math.round(84 * activity.getResources().getDisplayMetrics().density);
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()
                    | WindowInsetsCompat.Type.displayCutout()).bottom;
            assertEquals(baseHeight + bottom, navigation.getHeight());
        });
    }

    @Test
    public void preview_showsFigmaBookingHierarchyWithoutServer() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<BookingFigmaPreviewActivity> ignored = ActivityScenario.launch(
                BookingFigmaPreviewActivity.createIntent(context))) {
            onView(withText(R.string.debug_manager_guide_preview_banner))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.booking_main_heading)).check(matches(isDisplayed()));
            onView(withText(R.string.booking_main_step_visit)).check(matches(isDisplayed()));
            onView(withId(R.id.layoutBookingHospitalMapPlaceholder))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withText(R.string.booking_main_step_health))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withText(R.string.booking_main_step_options))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
        }
    }
}
