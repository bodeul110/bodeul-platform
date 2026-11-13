package com.example.bodeul.ui.booking;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 예약 확인 화면의 조작 영역을 시스템 바와 분리한다.
 */
final class BookingCompletionInsets {
    private BookingCompletionInsets() {
    }

    static void apply(View root) {
        apply(root, null);
    }

    static void applySheet(View root, View bottomAction) {
        apply(root, bottomAction);
    }

    private static void apply(View root, View bottomAction) {
        int initialLeft = root.getPaddingLeft();
        int initialTop = root.getPaddingTop();
        int initialRight = root.getPaddingRight();
        int initialBottom = root.getPaddingBottom();
        int actionBottom = bottomAction == null ? 0 : bottomAction.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    initialLeft + systemInsets.left,
                    initialTop + systemInsets.top,
                    initialRight + systemInsets.right,
                    initialBottom + (bottomAction == null ? systemInsets.bottom : 0)
            );
            // 반투명 시트는 배경을 화면 끝까지 유지하고 버튼에만 하단 안전 여백을 준다.
            if (bottomAction != null) {
                bottomAction.setPadding(bottomAction.getPaddingLeft(), bottomAction.getPaddingTop(),
                        bottomAction.getPaddingRight(), actionBottom + systemInsets.bottom);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
