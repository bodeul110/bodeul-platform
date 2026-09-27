package com.example.bodeul.ui.navigation;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.R;

/** 공통 하단 탭의 높이와 스크롤 콘텐츠의 안전영역을 유지한다. */
public final class ClientBottomNavigationInsets {
    private ClientBottomNavigationInsets() {
    }

    public static void apply(View navigation) {
        if (Boolean.TRUE.equals(navigation.getTag(R.id.client_bottom_navigation_insets_applied))) {
            return;
        }
        navigation.setTag(R.id.client_bottom_navigation_insets_applied, true);
        int initialHeight = navigation.getLayoutParams().height;
        int initialLeft = navigation.getPaddingLeft();
        int initialTop = navigation.getPaddingTop();
        int initialRight = navigation.getPaddingRight();
        int initialBottom = navigation.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(navigation, (view, windowInsets) -> {
            Insets safeInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(initialLeft + safeInsets.left, initialTop,
                    initialRight + safeInsets.right, initialBottom + safeInsets.bottom);
            // 고정 높이 안에서 padding만 늘리면 탭이 눌린다. IME 높이는 탭 자체에 더하지 않는다.
            if (initialHeight > 0) {
                ViewGroup.LayoutParams params = view.getLayoutParams();
                int height = initialHeight + safeInsets.bottom;
                if (params.height != height) {
                    params.height = height;
                    view.setLayoutParams(params);
                }
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(navigation);
    }

    public static void applyScrollableContent(View content) {
        if (Boolean.TRUE.equals(content.getTag(R.id.client_navigation_content_insets_applied))) {
            return;
        }
        content.setTag(R.id.client_navigation_content_insets_applied, true);
        int initialLeft = content.getPaddingLeft();
        int initialTop = content.getPaddingTop();
        int initialRight = content.getPaddingRight();
        int initialBottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets safeInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboardInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            // 기존 콘텐츠의 하단 탭 간격에 시스템 영역만 더해 마지막 항목이 메뉴에 가리지 않게 한다.
            view.setPadding(initialLeft + safeInsets.left, initialTop + safeInsets.top,
                    initialRight + safeInsets.right,
                    initialBottom + Math.max(safeInsets.bottom, keyboardInsets.bottom));
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
