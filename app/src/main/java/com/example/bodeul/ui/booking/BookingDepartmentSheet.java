package com.example.bodeul.ui.booking;

import android.os.Bundle;
import android.graphics.Outline;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingHospitalOption;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 선택 중인 값은 복원하되 확인 전에는 예약 초안으로 반환하지 않는다. */
public final class BookingDepartmentSheet extends BottomSheetDialogFragment {
    static final String TAG = "bookingDepartment";
    static final String RESULT = "bookingHospitalSelection";
    private String selected = "";
    private final List<View> choices = new ArrayList<>();

    static BookingDepartmentSheet create(BookingHospitalOption option, String initial) {
        BookingDepartmentSheet sheet = new BookingDepartmentSheet();
        Bundle args = selectionBundle(new BookingHospitalSelection(option.getHospitalName(), initial,
                option.getLatitude(), option.getLongitude()));
        args.putStringArrayList("departments", new ArrayList<>(option.getDepartmentNames()));
        sheet.setArguments(args);
        return sheet;
    }

    static Bundle selectionBundle(BookingHospitalSelection selection) {
        Bundle data = new Bundle();
        data.putString("hospital", selection.getHospitalName());
        data.putString("department", selection.getDepartmentName());
        data.putDouble("latitude", selection.getHospitalLatitude());
        data.putDouble("longitude", selection.getHospitalLongitude());
        return data;
    }

    static BookingHospitalSelection readSelection(Bundle data) {
        return new BookingHospitalSelection(data.getString("hospital", ""), data.getString("department", ""),
                data.getDouble("latitude"), data.getDouble("longitude"));
    }

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selected = (savedInstanceState == null ? requireArguments() : savedInstanceState).getString("department", "");
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_booking_department, container, false);
    }

    @Override public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        choices.clear();
        List<String> names = BookingDepartmentOptions.build(requireArguments().getStringArrayList("departments"),
                Arrays.asList(getResources().getStringArray(R.array.final_department_defaults)), selected);
        LinearLayout grid = root.findViewById(R.id.bookingDepartmentGrid);
        int columns = getResources().getConfiguration().fontScale > 1.3f ? 1 : 2;
        for (int i = 0; i < names.size(); i += columns) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(10);
            grid.addView(row, rowParams);
            for (int column = 0; column < columns; column++) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                if (column > 0) params.setMarginStart(dp(11));
                if (i + column >= names.size()) { row.addView(new View(requireContext()), params); continue; }
                String name = names.get(i + column);
                View choice = getLayoutInflater().inflate(R.layout.item_booking_department, row, false);
                row.addView(choice, params);
                choice.setTag(name);
                choice.setContentDescription(name);
                ((TextView) choice.findViewById(R.id.textBookingDepartment)).setText(name);
                ViewCompat.setAccessibilityDelegate(choice, new AccessibilityDelegateCompat() {
                    @Override public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setClassName("android.widget.RadioButton");
                        info.setCheckable(true);
                        info.setChecked(host.isSelected());
                    }
                });
                choice.setOnClickListener(v -> { selected = name; render(root); });
                choices.add(choice);
            }
        }
        root.findViewById(R.id.buttonCloseBookingDepartment).setOnClickListener(v -> dismiss());
        root.findViewById(R.id.buttonConfirmBookingDepartment).setOnClickListener(v -> {
            if (selected.isEmpty()) return;
            Bundle result = new Bundle(requireArguments());
            result.putString("department", selected);
            getParentFragmentManager().setFragmentResult(RESULT, result);
            dismiss();
        });
        root.findViewById(R.id.buttonManualBookingDepartment).setOnClickListener(v -> {
            if (getParentFragmentManager().findFragmentByTag(BookingHospitalManualDialog.TAG) != null) return;
            Bundle current = new Bundle(requireArguments());
            current.putString("department", selected);
            BookingHospitalManualDialog.create(readSelection(current), false)
                    .show(getParentFragmentManager(), BookingHospitalManualDialog.TAG);
        });
        render(root);
    }

    @Override public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundResource(R.drawable.bg_final_department_sheet);
            // 확장 상태에서도 Material 기본 동작이 원본의 위쪽 둥근 모서리를 없애지 않게 한다.
            sheet.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + dp(20), dp(20));
                }
            });
            sheet.setClipToOutline(true);
            int maxHeight = getResources().getDisplayMetrics().heightPixels - dp(80);
            sheet.getLayoutParams().height = Math.min(maxHeight,
                    dp(Math.round(500 * Math.max(1f, getResources().getConfiguration().fontScale))));
            sheet.requestLayout();
            dialog.getBehavior().setSkipCollapsed(true);
            dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void render(View root) {
        for (View choice : choices) {
            boolean checked = selected.equals(choice.getTag());
            choice.setSelected(checked);
            TextView text = choice.findViewById(R.id.textBookingDepartment);
            text.setTextColor(ContextCompat.getColor(requireContext(), checked
                    ? R.color.final_text_accent_accessible : R.color.final_text_primary));
            text.setTypeface(ResourcesCompat.getFont(requireContext(), checked
                    ? R.font.pretendard_bold : R.font.pretendard_medium));
            choice.findViewById(R.id.checkBookingDepartment).setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
        }
        root.findViewById(R.id.buttonConfirmBookingDepartment).setEnabled(!selected.isEmpty());
        root.findViewById(R.id.bookingDepartmentFooter).setVisibility(selected.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("department", selected);
        super.onSaveInstanceState(outState);
    }

    @Override public void onDestroyView() { choices.clear(); super.onDestroyView(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
