package com.example.bodeul.ui.booking;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.bodeul.R;

/** 주소 검색을 가장하지 않고 사용자가 입력한 만남 장소를 확인해 전달한다. */
public final class BookingMeetingManualSheet extends BookingSelectorSheet {
    static final String TAG = "bookingMeetingManual";
    static final String RESULT = "bookingMeetingManualSelection";
    private EditText input;

    static BookingMeetingManualSheet create(String initial) {
        BookingMeetingManualSheet sheet = new BookingMeetingManualSheet();
        Bundle args = new Bundle();
        args.putString("meetingPlace", initial);
        sheet.setArguments(args);
        return sheet;
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.sheet_booking_meeting_manual, parent, false);
    }

    @Override public void onViewCreated(@NonNull View root, @Nullable Bundle state) {
        input = root.findViewById(R.id.inputBookingMeetingManual);
        input.setText((state == null ? requireArguments() : state).getString("meetingPlace", ""));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(root); }
            @Override public void afterTextChanged(Editable s) { }
        });
        root.findViewById(R.id.buttonClearBookingMeetingManual).setOnClickListener(v -> input.setText(""));
        root.findViewById(R.id.buttonCloseBookingMeetingManual).setOnClickListener(v -> dismiss());
        root.findViewById(R.id.buttonConfirmBookingMeetingManual).setOnClickListener(v -> confirm());
        input.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            confirm();
            return true;
        });
        render(root);
    }

    private void render(View root) {
        String value = input.getText().toString().trim();
        ((TextView) root.findViewById(R.id.textBookingMeetingManualPreview)).setText(value);
        root.findViewById(R.id.textBookingMeetingManualDescription).setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.buttonConfirmBookingMeetingManual).setEnabled(!value.isEmpty());
    }

    private void confirm() {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) return;
        Bundle result = new Bundle();
        result.putString("meetingPlace", value);
        getParentFragmentManager().setFragmentResult(RESULT, result);
        dismiss();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        if (input != null) state.putString("meetingPlace", input.getText().toString());
        super.onSaveInstanceState(state);
    }

    @Override public void onDestroyView() { input = null; super.onDestroyView(); }
    @Override protected int preferredHeightDp() { return 650; }
}
