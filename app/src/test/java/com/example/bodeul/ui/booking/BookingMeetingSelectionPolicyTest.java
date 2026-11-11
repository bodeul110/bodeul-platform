package com.example.bodeul.ui.booking;

import static org.junit.Assert.*;
import com.example.bodeul.domain.model.BookingMeetingLocationSelection;
import com.example.bodeul.domain.model.BookingMeetingPointOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class BookingMeetingSelectionPolicyTest {
    private final List<BookingMeetingPointOption> options = Arrays.asList(
            new BookingMeetingPointOption("main", "정문", "", "검증 병원 정문", .2f, .3f),
            new BookingMeetingPointOption("pharmacy", "약국", "", "검증 병원 약국", .4f, .5f));

    @Test public void newSelectionKeepsExistingDefault() {
        assertEquals("main", resolve("", "").getPointId());
    }
    @Test public void exactPlaceRestoresItsPointEvenWithoutId() {
        assertEquals("pharmacy", resolve("", "검증 병원 약국").getPointId());
    }
    @Test public void freeTextWithOldPointIdIsNeverReplaced() {
        BookingMeetingLocationSelection result = resolve("main", " 정문 옆 파란 의자 ");
        assertEquals("custom", result.getPointId());
        assertEquals("정문 옆 파란 의자", result.getMeetingPlace());
    }
    @Test public void unknownSavedPointKeepsPlace() {
        assertEquals("폐기된 별관 안내 데스크", resolve("old", "폐기된 별관 안내 데스크").getMeetingPlace());
    }
    @Test public void emptyPlaceCanRestoreExistingPoint() {
        assertEquals("검증 병원 약국", resolve("pharmacy", "").getMeetingPlace());
    }
    @Test public void emptyOptionsNeverInventALocation() {
        assertFalse(BookingMeetingSelectionPolicy.resolve(Collections.emptyList(), new BookingMeetingLocationSelection("", "")).isComplete());
    }
    @Test public void emptyOptionsStillPreserveTypedPlace() {
        assertEquals("입력 장소", BookingMeetingSelectionPolicy.resolve(Collections.emptyList(),
                new BookingMeetingLocationSelection("", "입력 장소")).getMeetingPlace());
    }

    private BookingMeetingLocationSelection resolve(String id, String text) {
        return BookingMeetingSelectionPolicy.resolve(options, new BookingMeetingLocationSelection(id, text));
    }
}
