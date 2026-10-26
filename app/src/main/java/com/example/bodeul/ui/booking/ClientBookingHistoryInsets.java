package com.example.bodeul.ui.booking;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.ui.navigation.ClientBottomNavigationInsets;

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
        ClientBottomNavigationInsets.apply(bottomNavigation);
        ViewCompat.requestApplyInsets(content);
    }
}
