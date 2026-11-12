package com.example.bodeul.ui.booking;

import android.app.Activity;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** 선택기의 고정 헤더·하단 확인 영역을 시스템 바 밖에 배치한다. */
final class BookingSelectorInsets {
    private BookingSelectorInsets() { }

    static void apply(Activity activity, View root) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        WindowCompat.getInsetsController(activity.getWindow(), root).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(activity.getWindow(), root).setAppearanceLightNavigationBars(true);
        ViewCompat.requestApplyInsets(root);
    }
}
