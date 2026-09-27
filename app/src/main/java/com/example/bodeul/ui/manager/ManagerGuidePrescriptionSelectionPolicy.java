package com.example.bodeul.ui.manager;

/** 처방 이미지 선택 결과를 서버 호출 전에 제한하는 화면 정책이다. */
final class ManagerGuidePrescriptionSelectionPolicy {
    static final int MAX_IMAGE_COUNT = 3;

    private ManagerGuidePrescriptionSelectionPolicy() {
    }

    static boolean exceedsLimit(int selectedCount) {
        return selectedCount > MAX_IMAGE_COUNT;
    }
}
