package com.example.bodeul.ui.booking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.domain.model.BookingMeetingPointOption;
import java.util.List;

/** 이전 안내도의 지점 선택을 유지하며 직접 입력한 주소에는 가짜 좌표를 붙이지 않는다. */
public final class BookingMeetingDiagramSheet extends BookingSelectorSheet {
    static final String TAG = "bookingMeetingDiagram";
    static final String RESULT = "bookingMeetingDiagramSelection";
    private String selected = "";

    static BookingMeetingDiagramSheet create(BookingHospitalSelection hospital, String pointId) {
        BookingMeetingDiagramSheet sheet = new BookingMeetingDiagramSheet();
        Bundle args = new Bundle();
        args.putString("hospital", hospital.getHospitalName());
        args.putString("department", hospital.getDepartmentName());
        args.putString("pointId", pointId);
        sheet.setArguments(args);
        return sheet;
    }

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        selected = (state == null ? requireArguments() : state).getString("pointId", "");
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.sheet_booking_meeting_diagram, parent, false);
    }

    @Override public void onViewCreated(@NonNull View root, @Nullable Bundle state) {
        List<BookingMeetingPointOption> options = BookingMeetingPointCatalog.createPointOptions(requireContext(),
                requireArguments().getString("hospital"), requireArguments().getString("department"));
        BookingLocationMapView map = root.findViewById(R.id.viewBookingLocationMap);
        RadioGroup group = root.findViewById(R.id.bookingMeetingDiagramOptions);
        map.setPointOptions(options);
        boolean found = false;
        for (BookingMeetingPointOption point : options) {
            RadioButton radio = new RadioButton(requireContext());
            radio.setId(View.generateViewId());
            radio.setTag(point.getId());
            radio.setText(point.getTitle());
            radio.setTextSize(14);
            radio.setTextColor(ContextCompat.getColor(requireContext(), R.color.final_text_primary));
            radio.setTypeface(ResourcesCompat.getFont(requireContext(), R.font.pretendard_medium));
            radio.setMinHeight(dp(48));
            group.addView(radio, new RadioGroup.LayoutParams(-1, -2));
            if (point.getId().equals(selected)) { group.check(radio.getId()); found = true; }
        }
        if (!found) selected = "";
        map.setSelectedPointId(selected);
        View confirm = root.findViewById(R.id.buttonConfirmBookingMeetingDiagram);
        confirm.setEnabled(!selected.isEmpty());
        group.setOnCheckedChangeListener((parent, id) -> {
            View radio = group.findViewById(id);
            if (radio == null) return;
            selected = (String) radio.getTag();
            map.setSelectedPointId(selected);
            confirm.setEnabled(true);
        });
        map.setOnPointSelectedListener(point -> {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (point.getId().equals(group.getChildAt(i).getTag())) group.check(group.getChildAt(i).getId());
            }
        });
        root.findViewById(R.id.buttonCloseBookingMeetingDiagram).setOnClickListener(v -> dismiss());
        confirm.setOnClickListener(v -> {
            if (selected.isEmpty()) return;
            Bundle result = new Bundle();
            result.putString("pointId", selected);
            getParentFragmentManager().setFragmentResult(RESULT, result);
            dismiss();
        });
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        state.putString("pointId", selected);
        super.onSaveInstanceState(state);
    }
    @Override protected int preferredHeightDp() { return 600; }
}
