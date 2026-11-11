package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.action.GeneralClickAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Tap;
import androidx.core.graphics.ColorUtils;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.domain.model.BookingMeetingLocationSelection;
import com.example.bodeul.ui.booking.BookingAppointmentSelectorActivity;
import com.example.bodeul.ui.booking.BookingLocationSelectorActivity;
import com.example.bodeul.ui.booking.BookingMeetingPointCatalog;
import com.example.bodeul.domain.model.BookingMeetingPointOption;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** 서버·DB·실기기 상태를 건드리지 않고 실제 선택기와 결과 계약을 검증한다. */
@RunWith(AndroidJUnit4.class)
public class BookingScheduleLocationFigmaTest {
    @Test public void existingArbitraryMinuteAndLeapDaySurviveRecreation() {
        try (ActivityScenario<BookingAppointmentSelectorActivity> scenario = schedule("2028-02-29 10:17")) {
            onView(withTagValue(is((Object) "2028-02-29 00:00"))).check(matches(isSelected()));
            onView(withId(R.id.buttonBookingAppointmentCustomTime)).perform(scrollTo(), click());
            onView(withId(R.id.pickerBookingMinute)).check((v,e) -> { if(e!=null) throw e; assertEquals(17, ((NumberPicker)v).getValue()); });
            scenario.recreate();
            onView(withId(R.id.pickerBookingMinute)).check((v,e) -> { if(e!=null) throw e; assertEquals(17, ((NumberPicker)v).getValue()); });
            onView(withId(R.id.buttonConfirmBookingTime)).perform(click());
            onView(withId(R.id.buttonBookingAppointmentComplete)).perform(click());
            assertEquals("2028-02-29 10:17", BookingAppointmentSelectorActivity.parseResult(scenario.getResult().getResultData()));
            assertEquals(Activity.RESULT_OK, scenario.getResult().getResultCode());
        }
    }

    @Test public void calendarChangesYearWithoutChangingTime() {
        try (ActivityScenario<BookingAppointmentSelectorActivity> scenario = schedule("2027-12-31 23:59")) {
            onView(withId(R.id.buttonBookingAppointmentNextMonth)).perform(scrollTo(), click());
            onView(withTagValue(is((Object) "2028-01-01 00:00"))).perform(scrollTo(), click());
            scenario.recreate();
            onView(withTagValue(is((Object) "2028-01-01 00:00"))).check(matches(isSelected()));
            onView(withId(R.id.buttonBookingAppointmentComplete)).perform(click());
            assertEquals("2028-01-01 23:59", BookingAppointmentSelectorActivity.parseResult(scenario.getResult().getResultData()));
        }
    }

    @Test public void pastDaysAndPreviousMonthAreDisabled() {
        Calendar today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"), Locale.KOREA);
        String value = String.format(Locale.KOREA, "%04d-%02d-%02d 10:00", today.get(Calendar.YEAR),
                today.get(Calendar.MONTH)+1, today.get(Calendar.DAY_OF_MONTH));
        try (ActivityScenario<BookingAppointmentSelectorActivity> scenario = schedule(value)) {
            onView(withId(R.id.buttonBookingAppointmentPreviousMonth)).check(matches(not(isEnabled())));
            scenario.onActivity(a -> {
                GridLayout grid = a.findViewById(R.id.gridBookingAppointmentCalendar);
                for (int i=0; i<grid.getChildCount(); i++) {
                    View cell = grid.getChildAt(i);
                    if (cell.getTag() instanceof String && ((String)cell.getTag()).compareTo(value.substring(0,10)+" 00:00") < 0)
                        assertFalse(cell.isEnabled());
                }
            });
        }
    }

    @Test public void newScheduleNeedsTimeConfirmationAndCancelDoesNotSave() {
        try (ActivityScenario<BookingAppointmentSelectorActivity> scenario = schedule("")) {
            onView(withId(R.id.layoutBookingAppointmentBottomAction)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.buttonBookingAppointmentCustomTime)).perform(scrollTo(), click());
            onView(withId(R.id.buttonCloseBookingTime)).perform(click());
            onView(withId(R.id.layoutBookingAppointmentBottomAction)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.buttonBookingAppointmentBack)).perform(click());
            assertEquals(Activity.RESULT_CANCELED, scenario.getResult().getResultCode());
        }
    }

    @Test public void wheelChangeSurvivesRecreationAndConfirm() {
        try (ActivityScenario<BookingAppointmentSelectorActivity> scenario = schedule("2028-03-01 00:17")) {
            onView(withId(R.id.buttonBookingAppointmentCustomTime)).perform(scrollTo(), click());
            onView(withId(R.id.pickerBookingMinute)).perform(scrollTo(), swipeUp());
            int[] changed = {17};
            onView(withId(R.id.pickerBookingMinute)).check((v,e) -> { if(e!=null) throw e; changed[0]=((NumberPicker)v).getValue(); });
            assertNotEquals(17, changed[0]);
            scenario.recreate();
            onView(withId(R.id.pickerBookingMinute)).check((v,e) -> { if(e!=null) throw e; assertEquals(changed[0],((NumberPicker)v).getValue()); });
            onView(withId(R.id.buttonConfirmBookingTime)).perform(click());
            onView(withId(R.id.buttonBookingAppointmentComplete)).perform(click());
            assertEquals(String.format(Locale.KOREA,"2028-03-01 00:%02d",changed[0]),
                    BookingAppointmentSelectorActivity.parseResult(scenario.getResult().getResultData()));
        }
    }

    @Test public void cancelWheelDoesNotChangeOriginalTime() {
        try (ActivityScenario<BookingAppointmentSelectorActivity> scenario = schedule("2028-03-01 12:00")) {
            onView(withId(R.id.buttonBookingAppointmentCustomTime)).perform(scrollTo(), click());
            onView(withId(R.id.pickerBookingHour)).perform(scrollTo(), swipeUp());
            onView(withId(R.id.buttonCloseBookingTime)).perform(click());
            onView(withId(R.id.buttonBookingAppointmentComplete)).perform(click());
            assertEquals("2028-03-01 12:00", BookingAppointmentSelectorActivity.parseResult(scenario.getResult().getResultData()));
        }
    }

    @Test public void presetPlaceSelectionReturnsExistingContract() {
        try (ActivityScenario<BookingLocationSelectorActivity> scenario = location("main","")) {
            // 병원 요약 헤더 뒤 네 번째 장소 행을 실제로 스크롤하고 누른다.
            onData(anything()).inAdapterView(withId(R.id.listBookingLocationOptions)).atPosition(4).perform(click());
            scenario.recreate();
            onView(withId(R.id.buttonBookingLocationConfirm)).perform(click());
            BookingMeetingLocationSelection value = BookingLocationSelectorActivity.parseResult(scenario.getResult().getResultData());
            assertEquals(Activity.RESULT_OK, scenario.getResult().getResultCode());
            assertEquals("pharmacy", value.getPointId());
            assertTrue(value.getMeetingPlace().contains("약국"));
        }
    }

    @Test public void unknownSavedPlaceIsPreservedWithoutChoosingAnything() {
        try (ActivityScenario<BookingLocationSelectorActivity> scenario = location("old-point","별관 3층 파란 의자 앞")) {
            scenario.recreate();
            onView(withId(R.id.buttonBookingLocationConfirm)).perform(click());
            assertEquals("별관 3층 파란 의자 앞",
                    BookingLocationSelectorActivity.parseResult(scenario.getResult().getResultData()).getMeetingPlace());
        }
    }

    @Test public void manualInputRejectsBlankRestoresAndReturnsOnlyOnConfirm() {
        try (ActivityScenario<BookingLocationSelectorActivity> scenario = location("", "")) {
            onView(withId(R.id.buttonBookingLocationManual)).perform(click());
            onView(withId(R.id.buttonConfirmBookingMeetingManual)).check(matches(not(isEnabled())));
            onView(withId(R.id.inputBookingMeetingManual)).perform(replaceText("   "), closeSoftKeyboard());
            onView(withId(R.id.buttonConfirmBookingMeetingManual)).check(matches(not(isEnabled())));
            onView(withId(R.id.inputBookingMeetingManual)).perform(replaceText(" 서울 종로구 검증 장소 1층 안내 데스크 "), closeSoftKeyboard());
            scenario.recreate();
            onView(withId(R.id.inputBookingMeetingManual)).check(matches(withText(" 서울 종로구 검증 장소 1층 안내 데스크 ")));
            onView(withId(R.id.buttonConfirmBookingMeetingManual)).perform(click());
            scenario.recreate();
            onView(withId(R.id.buttonBookingLocationConfirm)).perform(click());
            BookingMeetingLocationSelection result = BookingLocationSelectorActivity.parseResult(scenario.getResult().getResultData());
            assertEquals("custom", result.getPointId());
            assertEquals("서울 종로구 검증 장소 1층 안내 데스크", result.getMeetingPlace());
        }
    }

    @Test public void cancelledManualEntryKeepsPreviousPlace() {
        try (ActivityScenario<BookingLocationSelectorActivity> scenario = location("", "기존 직접 입력")) {
            onView(withId(R.id.buttonBookingLocationManual)).perform(click());
            onView(withId(R.id.inputBookingMeetingManual)).perform(replaceText("취소할 입력"), closeSoftKeyboard());
            scenario.recreate();
            onView(withId(R.id.buttonCloseBookingMeetingManual)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.buttonBookingLocationConfirm)).perform(click());
            assertEquals("기존 직접 입력", BookingLocationSelectorActivity.parseResult(scenario.getResult().getResultData()).getMeetingPlace());
        }
    }

    @Test public void diagramCancelAndConfirmAreSeparateFromBookingResult() {
        try (ActivityScenario<BookingLocationSelectorActivity> scenario = location("", "직접 장소")) {
            onView(withId(R.id.buttonBookingLocationDiagram)).perform(click());
            onView(withId(R.id.buttonConfirmBookingMeetingDiagram)).check(matches(not(isEnabled())));
            onView(withId(R.id.viewBookingLocationMap)).perform(scrollTo(), tapDiagramPoint("parking"));
            scenario.recreate();
            onView(allOf(withTagValue(is((Object)"parking")), isAssignableFrom(android.widget.RadioButton.class)))
                    .inRoot(isDialog()).check(matches(isChecked()));
            onView(withId(R.id.bookingMeetingDiagramRoot)).check((v,e) -> {if(e!=null)throw e;assertTextFits(v);});
            capture("location-diagram");
            onView(withId(R.id.buttonCloseBookingMeetingDiagram)).perform(click());
            onView(withId(R.id.buttonBookingLocationDiagram)).perform(click());
            onView(withId(R.id.buttonConfirmBookingMeetingDiagram)).check(matches(not(isEnabled())));
            onView(allOf(withTagValue(is((Object)"outpatient")), isAssignableFrom(android.widget.RadioButton.class))).perform(scrollTo(), click());
            onView(withId(R.id.buttonConfirmBookingMeetingDiagram)).perform(click());
            onView(withId(R.id.buttonBookingLocationConfirm)).perform(click());
            assertEquals("outpatient", BookingLocationSelectorActivity.parseResult(scenario.getResult().getResultData()).getPointId());
        }
    }

    @Test public void closingLocationScreenReturnsCancelled() {
        try (ActivityScenario<BookingLocationSelectorActivity> scenario = location("main","")) {
            onView(withId(R.id.buttonBackBookingLocation)).perform(click());
            assertEquals(Activity.RESULT_CANCELED, scenario.getResult().getResultCode());
        }
    }

    @Test public void scheduleVisualStatesRemainReadable() {
        try (ActivityScenario<BookingAppointmentSelectorActivity> scenario = schedule("2028-09-16 14:17")) {
            scenario.onActivity(a -> assertTextFits(a.findViewById(R.id.bookingAppointmentSelectorRoot)));
            onView(withId(R.id.buttonBookingAppointmentComplete)).check(matches(isCompletelyDisplayed()));
            onView(withId(R.id.buttonBookingAppointmentComplete)).check((v,e) -> {
                if(e!=null) throw e;
                assertTrue(ColorUtils.calculateContrast(((TextView)v).getCurrentTextColor(),color(R.color.final_brand_primary))>=4.5);
            });
            capture("schedule");
            onView(withId(R.id.buttonBookingAppointmentCustomTime)).perform(scrollTo(), click());
            onView(withId(R.id.bookingTimeRoot)).check((v,e) -> {if(e!=null)throw e;assertTextFits(v);});
            onView(withId(R.id.bookingTimeWheels)).perform(scrollTo()).check(matches(isCompletelyDisplayed()));
            onView(withId(R.id.buttonConfirmBookingTime)).check(matches(isCompletelyDisplayed()));
            capture("time");
        }
    }

    @Test public void locationVisualStatesAndKeyboardKeepActionsVisible() {
        try (ActivityScenario<BookingLocationSelectorActivity> scenario = location("", "서울 종로구 검증 병원의 별관 3층 안내 데스크 앞")) {
            scenario.onActivity(a -> assertTextFits(a.findViewById(R.id.bookingLocationSelectorRoot)));
            onView(withId(R.id.buttonBookingLocationConfirm)).check(matches(isCompletelyDisplayed()));
            capture("location");
            onView(withId(R.id.buttonBookingLocationManual)).perform(click());
            onView(withId(R.id.bookingMeetingManualRoot)).check((v,e) -> {if(e!=null)throw e;assertTextFits(v);});
            capture("location-manual");
            onView(withId(R.id.inputBookingMeetingManual)).perform(click());
            android.os.SystemClock.sleep(700);
            onView(withId(R.id.buttonConfirmBookingMeetingManual)).check(matches(isCompletelyDisplayed()));
            capture("location-keyboard");
            onView(withId(R.id.inputBookingMeetingManual)).perform(closeSoftKeyboard());
        }
    }

    private static ViewAction tapDiagramPoint(String id) {
        return new GeneralClickAction(Tap.SINGLE, view -> {
            BookingMeetingPointOption point = BookingMeetingPointCatalog.createPointOptions(
                    view.getContext(), "검증 병원", "검증 진료과").stream()
                    .filter(option -> id.equals(option.getId())).findFirst().orElseThrow(AssertionError::new);
            int[] position = new int[2];
            view.getLocationOnScreen(position);
            float inset = 8 * view.getResources().getDisplayMetrics().density;
            float width = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight() - 2 * inset;
            float height = view.getHeight() - view.getPaddingTop() - view.getPaddingBottom() - 2 * inset;
            return new float[]{position[0] + view.getPaddingLeft() + inset + width * point.getRelativeX(),
                    position[1] + view.getPaddingTop() + inset + height * point.getRelativeY()};
        }, Press.FINGER);
    }

    private static Context context() {return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static int color(int id) {return androidx.core.content.ContextCompat.getColor(context(),id);}
    private static ActivityScenario<BookingAppointmentSelectorActivity> schedule(String value) {
        return ActivityScenario.launchActivityForResult(BookingAppointmentSelectorActivity.createIntent(context(),value));
    }
    private static ActivityScenario<BookingLocationSelectorActivity> location(String id, String value) {
        return ActivityScenario.launchActivityForResult(BookingLocationSelectorActivity.createIntent(context(),
                new BookingHospitalSelection("서울대학교병원 강남센터 검증", "소화기내과"),
                new BookingMeetingLocationSelection(id,value)));
    }
    private static void assertTextFits(View view) {
        if(view.getVisibility()!=View.VISIBLE || view instanceof NumberPicker)return;
        if(view instanceof TextView && !(view instanceof EditText)){
            TextView text=(TextView)view;
            if(text.getLayout()!=null && text.length()>0){
                assertTrue("세로 잘림: "+text.getText(),text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()+2);
                for(int i=0;i<text.getLineCount();i++){
                    assertEquals("말줄임: "+text.getText(),0,text.getLayout().getEllipsisCount(i));
                    assertTrue("가로 잘림: "+text.getText(),text.getLayout().getLineMax(i)<=text.getLayout().getWidth()+2);
                }
            }
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)assertTextFits(((ViewGroup)view).getChildAt(i));
    }
    private static void capture(String name){
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(400);
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory=new File(context().getExternalFilesDir(null),"schedule-location-final");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try(FileOutputStream output=new FileOutputStream(new File(directory,name+".png"))){
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));
        }catch(Exception error){throw new AssertionError(error);}
        finally{bitmap.recycle();}
    }
}
