package com.example.bodeul.ui.booking;

import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** 세로 레이아웃 전체를 안전 영역에 두어 고정 버튼과 키보드가 겹치지 않게 한다. */
final class BookingMainScreenInsets {
    private BookingMainScreenInsets() { }

    static void apply(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
