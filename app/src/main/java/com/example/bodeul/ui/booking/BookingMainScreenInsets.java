package com.example.bodeul.ui.booking;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 예약 메인 화면의 고정 앱바·하단 탭과 시스템 영역을 함께 보정한다.
 */
final class BookingMainScreenInsets {
    private BookingMainScreenInsets() {
    }

    static void apply(
            View content,
            View topBar,
            View bottomNavigation,
            int topBarExtraHeightPx
    ) {
        int contentLeft = content.getPaddingLeft();
        int contentTop = content.getPaddingTop() + Math.max(0, topBarExtraHeightPx);
        int contentRight = content.getPaddingRight();
        int contentBottom = content.getPaddingBottom();
        int topBarTop = topBar.getPaddingTop();
        int bottomNavigationLeft = bottomNavigation.getPaddingLeft();
        int bottomNavigationTop = bottomNavigation.getPaddingTop();
        int bottomNavigationRight = bottomNavigation.getPaddingRight();
        int bottomNavigationBottom = bottomNavigation.getPaddingBottom();
        int bottomNavigationHeight = bottomNavigation.getLayoutParams().height;

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(
                    contentLeft + systemInsets.left,
                    contentTop + systemInsets.top,
                    contentRight + systemInsets.right,
                    contentBottom + Math.max(systemInsets.bottom, imeInsets.bottom)
            );
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(topBar, (view, windowInsets) -> {
            Insets topInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(topInsets.left, topBarTop + topInsets.top, topInsets.right, 0);
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation, (view, windowInsets) -> {
            Insets bottomInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            int targetHeight = bottomNavigationHeight + bottomInsets.bottom;
            if (layoutParams.height != targetHeight) {
                layoutParams.height = targetHeight;
                view.setLayoutParams(layoutParams);
            }
            view.setPadding(
                    bottomNavigationLeft + bottomInsets.left,
                    bottomNavigationTop,
                    bottomNavigationRight + bottomInsets.right,
                    bottomNavigationBottom + bottomInsets.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
        ViewCompat.requestApplyInsets(topBar);
        ViewCompat.requestApplyInsets(bottomNavigation);
    }
}
