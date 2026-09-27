package com.example.bodeul.ui.booking;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 예약 내역의 스크롤 콘텐츠와 고정 하단 탭을 시스템 영역 안쪽에 배치한다.
 */
final class ClientBookingHistoryInsets {
    private ClientBookingHistoryInsets() {
    }

    static void apply(View content, View bottomNavigation) {
        int contentLeft = content.getPaddingLeft();
        int contentTop = content.getPaddingTop();
        int contentRight = content.getPaddingRight();
        int contentBottom = content.getPaddingBottom();
        int initialLeft = bottomNavigation.getPaddingLeft();
        int initialTop = bottomNavigation.getPaddingTop();
        int initialRight = bottomNavigation.getPaddingRight();
        int initialBottom = bottomNavigation.getPaddingBottom();
        int initialHeight = bottomNavigation.getLayoutParams().height;

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets safeInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    contentLeft + safeInsets.left,
                    contentTop + safeInsets.top,
                    contentRight + safeInsets.right,
                    contentBottom + safeInsets.bottom
            );
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation, (view, windowInsets) -> {
            Insets bottomInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    initialLeft + bottomInsets.left,
                    initialTop,
                    initialRight + bottomInsets.right,
                    initialBottom + bottomInsets.bottom
            );
            if (initialHeight > 0) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                int targetHeight = initialHeight + bottomInsets.bottom;
                if (layoutParams.height != targetHeight) {
                    layoutParams.height = targetHeight;
                    view.setLayoutParams(layoutParams);
                }
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
        ViewCompat.requestApplyInsets(bottomNavigation);
    }
}
