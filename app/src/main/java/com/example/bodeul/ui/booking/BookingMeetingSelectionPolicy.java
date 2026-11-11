package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.BookingMeetingLocationSelection;
import com.example.bodeul.domain.model.BookingMeetingPointOption;
import java.util.List;

/** 저장된 장소 문구를 우선해 새 선택기를 열 때 직접 입력을 기본 지점으로 덮어쓰지 않는다. */
final class BookingMeetingSelectionPolicy {
    static final String CUSTOM_POINT_ID = "custom";
    private BookingMeetingSelectionPolicy() { }

    static BookingMeetingLocationSelection resolve(List<BookingMeetingPointOption> points,
            BookingMeetingLocationSelection current) {
        if (!current.getMeetingPlace().isEmpty()) {
            for (BookingMeetingPointOption point : points) {
                if (point.getMeetingPlace().equals(current.getMeetingPlace())) return selection(point);
            }
            return new BookingMeetingLocationSelection(CUSTOM_POINT_ID, current.getMeetingPlace());
        }
        for (BookingMeetingPointOption point : points) {
            if (point.getId().equals(current.getPointId())) return selection(point);
        }
        return points.isEmpty() ? new BookingMeetingLocationSelection("", "") : selection(points.get(0));
    }

    static BookingMeetingLocationSelection selection(BookingMeetingPointOption point) {
        return new BookingMeetingLocationSelection(point.getId(), point.getMeetingPlace());
    }
}
