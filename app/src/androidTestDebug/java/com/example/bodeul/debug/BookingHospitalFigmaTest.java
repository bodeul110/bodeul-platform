package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.bodeul.R;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.map.KakaoLocalPlaceSearchClient;
import com.example.bodeul.data.map.KakaoPlaceCoordinate;
import com.example.bodeul.domain.model.BookingHospitalOption;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.ui.booking.BookingHospitalSelectorActivity;
import com.example.bodeul.ui.booking.BookingHospitalSelectorCoordinator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 실제 Activity·Fragment·결과 계약을 서버 쓰기 없이 검증한다. */
@RunWith(AndroidJUnit4.class)
public class BookingHospitalFigmaTest {
    private FakeSearch search;
    private boolean catalogFails;
    private int catalogLoads;
    private List<BookingHospitalOption> options;

    @Before public void setup() {
        options = Arrays.asList(new BookingHospitalOption("검증 병원", Arrays.asList("내과", "외과", "재활의학과"), 37.5, 127.1),
                new BookingHospitalOption("검증 치과", Arrays.asList("치과"), 37.6, 127.2));
        search = new FakeSearch();
        BookingHospitalFigmaPreviewActivity.testSearch = search;
        BookingHospitalFigmaPreviewActivity.testRepository = (BookingRepository) Proxy.newProxyInstance(
                BookingRepository.class.getClassLoader(), new Class[]{BookingRepository.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getHospitalOptions")) throw new AssertionError("예약 쓰기 호출: " + method.getName());
                    catalogLoads++;
                    @SuppressWarnings("unchecked") RepositoryCallback<List<BookingHospitalOption>> callback =
                            (RepositoryCallback<List<BookingHospitalOption>>) args[0];
                    if (catalogFails) callback.onError("합성 목록 오류"); else callback.onSuccess(options);
                    return null;
                });
    }

    @After public void cleanup() {
        BookingHospitalFigmaPreviewActivity.testRepository = null;
        BookingHospitalFigmaPreviewActivity.testSearch = null;
    }

    @Test public void selectionNeedsConfirmationAndReturnsCoordinates() {
        try (ActivityScenario<BookingHospitalFigmaPreviewActivity> scenario = launch("", "")) {
            onView(withText("검증 병원")).perform(click());
            onView(withId(R.id.bookingDepartmentFooter)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            choose("외과");
            onView(withId(R.id.buttonConfirmBookingDepartment)).check(matches(isCompletelyDisplayed()));
            scenario.recreate();
            onView(withTagValue(is((Object) "외과"))).check(matches(isSelected()));
            onView(withId(R.id.buttonConfirmBookingDepartment)).perform(click());
            android.app.Instrumentation.ActivityResult activityResult = scenario.getResult();
            assertEquals(Activity.RESULT_OK, activityResult.getResultCode());
            BookingHospitalSelection result = BookingHospitalSelectorActivity.parseResult(activityResult.getResultData());
            assertEquals("검증 병원", result.getHospitalName());
            assertEquals("외과", result.getDepartmentName());
            assertEquals(37.5, result.getHospitalLatitude(), 0.00001);
            assertEquals(127.1, result.getHospitalLongitude(), 0.00001);
        }
    }

    @Test public void cancelSheetKeepsSearchAndDoesNotCommitSelection() {
        try (ActivityScenario<BookingHospitalFigmaPreviewActivity> scenario = launch("검증 병원", "내과")) {
            onView(allOf(withId(R.id.textBookingHospitalOptionName), withText("검증 병원"))).perform(click());
            choose("외과");
            onView(withId(R.id.buttonCloseBookingDepartment)).perform(click());
            onView(withId(R.id.searchBookingHospital)).check(matches(withText("검증 병원")));
            onView(allOf(withId(R.id.textBookingHospitalOptionName), withText("검증 병원"))).perform(click());
            onView(withTagValue(is((Object) "내과"))).check(matches(isSelected()));
            onView(withId(R.id.buttonCloseBookingDepartment)).perform(click());
            onView(withId(R.id.buttonBackBookingHospitalSelector)).perform(click());
            assertEquals(Activity.RESULT_CANCELED, scenario.getResult().getResultCode());
        }
    }

    @Test public void emptyCatalogStillSearchesRemoteAndOldRepliesCannotReplaceNewOnes() {
        options = Collections.emptyList();
        search.delay = true;
        try (ActivityScenario<BookingHospitalFigmaPreviewActivity> scenario = launch("", "")) {
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.searchBookingHospital)).setText("이전"));
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.searchBookingHospital)).setText("최신"));
            scenario.onActivity(a -> search.callbacks.get(1).onSuccess(Arrays.asList(new KakaoPlaceCoordinate("최신 병원", 37.7, 127.4))));
            scenario.onActivity(a -> search.callbacks.get(0).onSuccess(Arrays.asList(new KakaoPlaceCoordinate("이전 병원", 37.1, 127.0))));
            onView(withText("최신 병원")).check(matches(isDisplayed()));
            scenario.onActivity(a -> assertEquals(1, ((ListView) a.findViewById(R.id.listBookingHospitalOptions)).getCount()));
            onView(withText("최신 병원")).perform(click());
            choose("정형외과");
            onView(withId(R.id.buttonConfirmBookingDepartment)).perform(click());
            BookingHospitalSelection result = BookingHospitalSelectorActivity.parseResult(scenario.getResult().getResultData());
            assertEquals("정형외과", result.getDepartmentName());
            assertEquals(37.7, result.getHospitalLatitude(), 0.00001);
        }
    }

    @Test public void queryRestoresAndDestroyedScreenReplyIsIgnored() {
        search.delay = true;
        try (ActivityScenario<BookingHospitalFigmaPreviewActivity> scenario = launch("", "")) {
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.searchBookingHospital)).setText("보존"));
            scenario.recreate();
            onView(withId(R.id.searchBookingHospital)).check(matches(withText("보존")));
            scenario.onActivity(a -> search.callbacks.get(search.callbacks.size() - 1).onSuccess(
                    Arrays.asList(new KakaoPlaceCoordinate("복원 병원", 37.5, 127.2))));
            scenario.onActivity(a -> search.callbacks.get(0).onSuccess(Collections.emptyList()));
            onView(withText("복원 병원")).check(matches(isDisplayed()));
        }
    }

    @Test public void loadFailureAllowsRetryAndManualInput() {
        catalogFails = true;
        try (ActivityScenario<BookingHospitalFigmaPreviewActivity> scenario = launch("", "")) {
            onView(withText(R.string.booking_hospital_selector_error_title)).check(matches(isDisplayed()));
            onView(withId(R.id.buttonBookingHospitalManualInput)).check(matches(isCompletelyDisplayed()));
            catalogFails = false;
            onView(withText(R.string.state_action_retry)).perform(scrollTo(), click());
            onView(withText("검증 병원")).check(matches(isDisplayed()));
            assertEquals(2, catalogLoads);
        }
    }

    @Test public void manualInputValidatesRestoresAndDropsCoordinatesForRenamedHospital() {
        try (ActivityScenario<BookingHospitalFigmaPreviewActivity> scenario = launch("검증 병원", "내과")) {
            onView(withId(R.id.buttonBookingHospitalManualInput)).perform(click());
            onView(withId(R.id.inputManualHospital)).perform(replaceText("수정 병원"));
            onView(withId(R.id.inputManualDepartment)).perform(replaceText(""), closeSoftKeyboard());
            onView(withId(android.R.id.button1)).perform(click());
            onView(withText(R.string.error_required_field)).check(matches(isDisplayed()));
            onView(withId(R.id.inputManualDepartment)).perform(replaceText(" 특수 진료과 "), closeSoftKeyboard());
            scenario.recreate();
            onView(withId(R.id.inputManualHospital)).check(matches(withText("수정 병원")));
            onView(withId(R.id.inputManualDepartment)).check(matches(withText(" 특수 진료과 ")));
            onView(withId(android.R.id.button1)).perform(click());
            BookingHospitalSelection result = BookingHospitalSelectorActivity.parseResult(scenario.getResult().getResultData());
            assertEquals("수정 병원", result.getHospitalName());
            assertEquals("특수 진료과", result.getDepartmentName());
            assertEquals(0, result.getHospitalLatitude(), 0);
        }
    }

    @Test public void catalogFailureDoesNotCoverRemoteResultsAndRetryRemainsAvailable() {
        catalogFails = true;
        search.delay = true;
        try (ActivityScenario<BookingHospitalFigmaPreviewActivity> scenario = launch("", "")) {
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.searchBookingHospital)).setText("외부"));
            scenario.onActivity(a -> search.callbacks.get(0).onSuccess(
                    Arrays.asList(new KakaoPlaceCoordinate("외부 병원", 37.5, 127.1))));
            onView(withText("외부 병원")).check(matches(isDisplayed()));
            onView(withId(R.id.bookingHospitalError)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.buttonBookingHospitalOptions)).perform(click());
            catalogFails = false;
            search.delay = false;
            onView(withText(R.string.state_action_retry)).perform(click());
            assertEquals(2, catalogLoads);
            onView(withId(R.id.buttonClearBookingHospital)).perform(click());
            onView(withText("검증 병원")).check(matches(isDisplayed()));
        }
    }

    @Test public void customDepartmentKeepsKnownHospitalCoordinates() {
        try (ActivityScenario<BookingHospitalFigmaPreviewActivity> scenario = launch("", "")) {
            onView(withText("검증 병원")).perform(click());
            onView(withId(R.id.buttonManualBookingDepartment)).perform(click());
            onView(withId(R.id.inputManualHospital)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            onView(withId(R.id.inputManualDepartment)).perform(replaceText("특수 진료과"), closeSoftKeyboard());
            scenario.recreate();
            onView(withId(android.R.id.button1)).perform(click());
            BookingHospitalSelection result = BookingHospitalSelectorActivity.parseResult(scenario.getResult().getResultData());
            assertEquals("검증 병원", result.getHospitalName());
            assertEquals("특수 진료과", result.getDepartmentName());
            assertEquals(37.5, result.getHospitalLatitude(), 0.00001);
        }
    }

    @Test public void mapActionDoesNotSelectHospitalAndRegionShortcutsStillSearch() {
        try (ActivityScenario<BookingHospitalFigmaPreviewActivity> scenario = launch("", "")) {
            onView(withContentDescription("검증 병원 지도 보기")).perform(click());
            scenario.onActivity(a -> {
                assertEquals(Intent.ACTION_VIEW, a.mapIntent.getAction());
                assertEquals("geo:0,0?q=37.5,127.1(검증 병원)", android.net.Uri.decode(a.mapIntent.getDataString()));
            });
            onView(withId(R.id.buttonBookingHospitalOptions)).perform(click());
            onView(withText(R.string.booking_hospital_selector_region_jongno)).perform(click());
            onView(withId(R.id.searchBookingHospital)).check(matches(withText(R.string.booking_hospital_selector_region_jongno_query)));
        }
    }

    @Test public void visualStatesStayReadableAndKeyboardDoesNotCoverActions() {
        options = Arrays.asList(new BookingHospitalOption("서울대학교병원 강남센터", Collections.emptyList(), 37.5, 127.1),
                new BookingHospitalOption("한글 병원 이름이 길어지는 경우의 검증 병원", Arrays.asList("내과")));
        try (ActivityScenario<BookingHospitalFigmaPreviewActivity> scenario = launch("", "")) {
            scenario.onActivity(a -> assertTextFits(a.findViewById(R.id.bookingHospitalSelectorRoot)));
            capture("hospital-list");
            onView(withText("서울대학교병원 강남센터")).perform(click());
            capture("department-empty");
            choose("내과");
            onView(withId(R.id.buttonConfirmBookingDepartment)).check(matches(isCompletelyDisplayed()));
            onView(withId(R.id.bookingDepartmentRoot)).check((v, e) -> { if (e != null) throw e; assertTextFits(v); });
            onView(withId(R.id.buttonConfirmBookingDepartment)).check((v, e) -> {
                if (e != null) throw e;
                TextView text = (TextView) v;
                assertTrue(ColorUtils.calculateContrast(text.getCurrentTextColor(), aColor(R.color.final_brand_primary)) >= 4.5);
            });
            capture("department-selected");
            onView(withId(R.id.buttonCloseBookingDepartment)).perform(click());
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.searchBookingHospital)).setText("없는결과"));
            onView(withText(R.string.final_hospital_empty)).check(matches(isDisplayed()));
            capture("hospital-empty");
            onView(withId(R.id.searchBookingHospital)).perform(click());
            scenario.onActivity(a -> androidx.core.view.WindowCompat.getInsetsController(a.getWindow(),
                    a.findViewById(R.id.searchBookingHospital)).show(androidx.core.view.WindowInsetsCompat.Type.ime()));
            android.os.SystemClock.sleep(1000);
            scenario.onActivity(a -> assertTrue("키보드가 실제로 표시되어야 합니다.",
                    androidx.core.view.ViewCompat.getRootWindowInsets(a.findViewById(R.id.bookingHospitalSelectorRoot))
                            .isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())));
            onView(withId(R.id.buttonBookingHospitalManualInput)).check(matches(isCompletelyDisplayed()));
            capture("hospital-keyboard");
        }
    }

    private static int aColor(int resource) { return androidx.core.content.ContextCompat.getColor(context(), resource); }
    private static void choose(String name) { onView(withTagValue(is((Object) name))).perform(scrollTo(), click()); }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private ActivityScenario<BookingHospitalFigmaPreviewActivity> launch(String hospital, String department) {
        Intent intent = BookingHospitalSelectorActivity.createIntent(context(), new BookingHospitalSelection(hospital, department, 37.5, 127.1));
        intent.setClass(context(), BookingHospitalFigmaPreviewActivity.class);
        return ActivityScenario.launchActivityForResult(intent);
    }
    private static void assertTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView && !(view instanceof EditText)) {
            TextView text = (TextView) view;
            if (text.getLayout() != null && text.length() > 0) {
                assertTrue("세로 잘림: " + text.getText(), text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 2);
                for (int i = 0; i < text.getLineCount(); i++) {
                    assertEquals("말줄임: " + text.getText(), 0, text.getLayout().getEllipsisCount(i));
                    assertTrue("가로 잘림: " + text.getText(), text.getLayout().getLineMax(i) <= text.getLayout().getWidth() + 2);
                }
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) assertTextFits(((ViewGroup) view).getChildAt(i));
    }
    private static void capture(String name) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(400);
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(context().getExternalFilesDir(null), "hospital-final");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
    private static final class FakeSearch implements BookingHospitalSelectorCoordinator.PlaceSearch {
        boolean delay;
        final List<KakaoLocalPlaceSearchClient.KeywordCallback> callbacks = new ArrayList<>();
        @Override public boolean isConfigured() { return true; }
        @Override public void search(String query, KakaoLocalPlaceSearchClient.KeywordCallback callback) {
            callbacks.add(callback);
            if (!delay) callback.onSuccess(Collections.emptyList());
        }
    }
}
