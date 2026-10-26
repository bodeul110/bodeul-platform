package com.example.bodeul.ui.report;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.ui.navigation.ClientBottomNavigationInsets;

/**
 * 보호자 리포트의 스크롤 콘텐츠와 고정 하단 탭을 시스템 바 안쪽에 배치한다.
 */
final class GuardianReportInsets {
    private GuardianReportInsets() {
    }

    static void apply(View content, View topBar, View bottomNavigation) {
        int contentLeft = content.getPaddingLeft();
        int contentTop = content.getPaddingTop();
        int contentRight = content.getPaddingRight();
        int contentBottom = content.getPaddingBottom();
        int topLeft = topBar.getPaddingLeft();
        int topTop = topBar.getPaddingTop();
        int topRight = topBar.getPaddingRight();
        int topBottom = topBar.getPaddingBottom();
        int topHeight = topBar.getLayoutParams().height;

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets safeInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    contentLeft + safeInsets.left,
                    contentTop,
                    contentRight + safeInsets.right,
                    contentBottom + safeInsets.bottom
            );
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(topBar, (view, windowInsets) -> {
            Insets topInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(topLeft, topTop + topInsets.top, topRight, topBottom);
            if (topHeight > 0) {
                ViewGroup.LayoutParams params = view.getLayoutParams();
                int targetHeight = topHeight + topInsets.top;
                if (params.height != targetHeight) {
                    params.height = targetHeight;
                    view.setLayoutParams(params);
                }
            }
            return windowInsets;
        });
        ClientBottomNavigationInsets.apply(bottomNavigation);
        ViewCompat.requestApplyInsets(content);
        ViewCompat.requestApplyInsets(topBar);
    }
}
