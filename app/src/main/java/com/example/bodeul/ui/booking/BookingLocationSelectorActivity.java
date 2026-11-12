package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.domain.model.BookingMeetingLocationSelection;
import com.example.bodeul.domain.model.BookingMeetingPointOption;
import java.util.ArrayList;
import java.util.List;

/** 만남 장소의 확인·취소와 기존 예약 결과 계약을 연결한다. */
public class BookingLocationSelectorActivity extends AppCompatActivity {
    private static final String EXTRA_HOSPITAL_NAME = "hospitalName";
    private static final String EXTRA_DEPARTMENT_NAME = "departmentName";
    private static final String EXTRA_POINT_ID = "pointId";
    private static final String EXTRA_MEETING_PLACE = "meetingPlace";
    private BookingLocationOptionAdapter adapter;
    private List<BookingMeetingPointOption> points;
    private BookingMeetingLocationSelection selection;
    private String customPlace = "";

    public static Intent createIntent(Context context, BookingHospitalSelection hospital, BookingMeetingLocationSelection current) {
        return new Intent(context, BookingLocationSelectorActivity.class)
                .putExtra(EXTRA_HOSPITAL_NAME, hospital.getHospitalName())
                .putExtra(EXTRA_DEPARTMENT_NAME, hospital.getDepartmentName())
                .putExtra(EXTRA_POINT_ID, current.getPointId())
                .putExtra(EXTRA_MEETING_PLACE, current.getMeetingPlace());
    }

    public static BookingMeetingLocationSelection parseResult(@Nullable Intent data) {
        return new BookingMeetingLocationSelection(data == null ? "" : data.getStringExtra(EXTRA_POINT_ID),
                data == null ? "" : data.getStringExtra(EXTRA_MEETING_PLACE));
    }

    @Override protected void onCreate(@Nullable Bundle state) {
        setTheme(R.style.Theme_Bodeul_FinalBooking);
        super.onCreate(state);
        setContentView(R.layout.activity_booking_location_selector);
        BookingSelectorInsets.apply(this, findViewById(R.id.bookingLocationSelectorRoot));
        BookingHospitalSelection hospital = new BookingHospitalSelection(
                getIntent().getStringExtra(EXTRA_HOSPITAL_NAME), getIntent().getStringExtra(EXTRA_DEPARTMENT_NAME));
        points = BookingMeetingPointCatalog.createPointOptions(this, hospital.getHospitalName(), hospital.getDepartmentName());
        selection = BookingMeetingSelectionPolicy.resolve(points, state == null ? parseResult(getIntent())
                : new BookingMeetingLocationSelection(state.getString(EXTRA_POINT_ID), state.getString(EXTRA_MEETING_PLACE)));
        customPlace = state == null ? "" : state.getString("customPlace", "");
        if (BookingMeetingSelectionPolicy.CUSTOM_POINT_ID.equals(selection.getPointId())) customPlace = selection.getMeetingPlace();
        ListView list = findViewById(R.id.listBookingLocationOptions);
        View header = getLayoutInflater().inflate(R.layout.header_booking_location_selector, list, false);
        list.addHeaderView(header, null, false);
        ((TextView) header.findViewById(R.id.textBookingLocationTitle)).setText(hospital.getHospitalName());
        ((TextView) header.findViewById(R.id.textBookingLocationBody)).setText(hospital.getDepartmentName());
        TextView manual = header.findViewById(R.id.buttonBookingLocationManual);
        manual.setPaintFlags(manual.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        if (getResources().getConfiguration().fontScale > 1.3f) {
            LinearLayout row = header.findViewById(R.id.bookingLocationHeadingRow);
            row.setOrientation(LinearLayout.VERTICAL);
            row.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            manual.setPadding(0, 0, 0, 0);
        }
        manual.setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag(BookingMeetingManualSheet.TAG) == null) {
                BookingMeetingManualSheet.create(customPlace).show(getSupportFragmentManager(), BookingMeetingManualSheet.TAG);
            }
        });
        header.findViewById(R.id.buttonBookingLocationDiagram).setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag(BookingMeetingDiagramSheet.TAG) == null) {
                BookingMeetingDiagramSheet.create(hospital, selection.getPointId())
                        .show(getSupportFragmentManager(), BookingMeetingDiagramSheet.TAG);
            }
        });
        adapter = new BookingLocationOptionAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            int index = position - list.getHeaderViewsCount();
            if (index >= 0 && index < adapter.getCount()) {
                selection = BookingMeetingSelectionPolicy.selection(adapter.getItem(index));
                render();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(BookingMeetingManualSheet.RESULT, this, (key, result) -> {
            String value = result.getString(EXTRA_MEETING_PLACE, "").trim();
            if (value.isEmpty()) return;
            customPlace = value;
            selection = new BookingMeetingLocationSelection(BookingMeetingSelectionPolicy.CUSTOM_POINT_ID, value);
            render();
            list.setSelection(0);
        });
        getSupportFragmentManager().setFragmentResultListener(BookingMeetingDiagramSheet.RESULT, this, (key, result) -> {
            for (BookingMeetingPointOption point : points) {
                if (point.getId().equals(result.getString(EXTRA_POINT_ID))) {
                    selection = BookingMeetingSelectionPolicy.selection(point);
                    render();
                    break;
                }
            }
        });
        findViewById(R.id.buttonBackBookingLocation).setOnClickListener(v -> finish());
        findViewById(R.id.buttonBookingLocationConfirm).setOnClickListener(v -> {
            if (!selection.isComplete()) return;
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_POINT_ID, selection.getPointId())
                    .putExtra(EXTRA_MEETING_PLACE, selection.getMeetingPlace()));
            finish();
        });
        render();
    }

    private void render() {
        List<BookingMeetingPointOption> visible = new ArrayList<>(points);
        if (!customPlace.isEmpty()) visible.add(0, new BookingMeetingPointOption(
                BookingMeetingSelectionPolicy.CUSTOM_POINT_ID, customPlace,
                getString(R.string.final_location_manual_description), customPlace, 0, 0));
        adapter.submitList(visible);
        adapter.setSelectedPointId(selection.getPointId());
        findViewById(R.id.buttonBookingLocationConfirm).setEnabled(selection.isComplete());
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(EXTRA_POINT_ID, selection.getPointId());
        state.putString(EXTRA_MEETING_PLACE, selection.getMeetingPlace());
        state.putString("customPlace", customPlace);
        super.onSaveInstanceState(state);
    }
}
