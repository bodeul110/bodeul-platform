package com.example.bodeul.ui.booking;

import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import com.example.bodeul.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** 선택 창의 모서리와 최대 높이를 맞추고 작은 화면에서는 본문만 스크롤한다. */
abstract class BookingSelectorSheet extends BottomSheetDialogFragment {
    protected abstract int preferredHeightDp();

    @Override public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        sheet.setBackgroundResource(R.drawable.bg_final_department_sheet);
        sheet.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + dp(20), dp(20));
            }
        });
        sheet.setClipToOutline(true);
        int maxHeight = getResources().getDisplayMetrics().heightPixels - dp(60);
        int height = dp(Math.round(preferredHeightDp() * Math.max(1f, getResources().getConfiguration().fontScale)));
        sheet.getLayoutParams().height = Math.min(maxHeight, height);
        sheet.requestLayout();
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    protected int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
