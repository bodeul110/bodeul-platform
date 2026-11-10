package com.example.bodeul.ui.booking;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 실제 가이드 목록과 사용자가 이미 입력한 값을 삭제하지 않는 선택 목록이다. */
final class BookingDepartmentOptions {
    private BookingDepartmentOptions() { }

    static List<String> build(List<String> available, List<String> fallback, String initial) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addAll(result, available);
        if (result.isEmpty()) addAll(result, fallback);
        if (initial != null && !initial.trim().isEmpty()) result.add(initial.trim());
        return new ArrayList<>(result);
    }

    private static void addAll(LinkedHashSet<String> target, List<String> values) {
        if (values == null) return;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) target.add(value.trim());
        }
    }
}
