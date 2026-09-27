package com.example.bodeul.ui.home;

import android.view.View;
import android.widget.ScrollView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.ui.navigation.ClientBottomNavigationInsets;

/**
 * 환자 홈의 고정 상단·하단 영역과 스크롤 콘텐츠를 시스템 영역 안쪽에 배치한다.
 */
public final class ClientHomeInsets {
    private ClientHomeInsets() {
    }

    public static void apply(ScrollView content, View topBar, View bottomBar) {
        int contentLeft = content.getPaddingLeft();
        int contentTop = content.getPaddingTop();
        int contentRight = content.getPaddingRight();
        int contentBottom = content.getPaddingBottom();
        int topLeft = topBar.getPaddingLeft();
        int topTop = topBar.getPaddingTop();
        int topRight = topBar.getPaddingRight();
        int topBottom = topBar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    contentLeft + systemInsets.left,
                    contentTop + systemInsets.top,
                    contentRight + systemInsets.right,
                    contentBottom + systemInsets.bottom
            );
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(topBar, (view, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    topLeft + systemInsets.left,
                    topTop + systemInsets.top,
                    topRight + systemInsets.right,
                    topBottom
            );
            return windowInsets;
        });
        ClientBottomNavigationInsets.apply(bottomBar);
        ViewCompat.requestApplyInsets(content);
        ViewCompat.requestApplyInsets(topBar);
    }
}
