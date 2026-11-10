package com.example.bodeul.ui.booking;

import android.app.Dialog;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

/** 직접 입력 중 화면이 재생성돼도 작성 내용을 보존한다. */
public final class BookingHospitalManualDialog extends DialogFragment {
    static final String TAG = "bookingHospitalManual";
    private EditText hospital;
    private EditText department;

    static BookingHospitalManualDialog create(BookingHospitalSelection selection, boolean editHospital) {
        BookingHospitalManualDialog dialog = new BookingHospitalManualDialog();
        Bundle args = BookingDepartmentSheet.selectionBundle(selection);
        args.putBoolean("editHospital", editHospital);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View form = getLayoutInflater().inflate(R.layout.dialog_booking_hospital_manual, null);
        hospital = form.findViewById(R.id.inputManualHospital);
        department = form.findViewById(R.id.inputManualDepartment);
        Bundle values = savedInstanceState == null ? requireArguments() : savedInstanceState;
        hospital.setText(values.getString("hospital", ""));
        department.setText(values.getString("department", ""));
        hospital.setEnabled(requireArguments().getBoolean("editHospital"));
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(requireArguments().getBoolean("editHospital")
                        ? R.string.booking_hospital_selector_manual_title : R.string.final_department_manual)
                .setView(form)
                .setPositiveButton(R.string.booking_hospital_selector_manual_confirm, null)
                .setNegativeButton(R.string.booking_hospital_selector_department_cancel, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = hospital.getText().toString().trim();
            String departmentName = department.getText().toString().trim();
            ((TextInputLayout) form.findViewById(R.id.layoutManualHospital)).setError(
                    name.isEmpty() ? getString(R.string.error_required_field) : null);
            ((TextInputLayout) form.findViewById(R.id.layoutManualDepartment)).setError(
                    departmentName.isEmpty() ? getString(R.string.error_required_field) : null);
            if (name.isEmpty() || departmentName.isEmpty()) {
                TextInputLayout invalid = form.findViewById(name.isEmpty()
                        ? R.id.layoutManualHospital : R.id.layoutManualDepartment);
                // 확대 글꼴에서도 입력란 아래 오류까지 보이도록 스크롤한다.
                invalid.post(() -> invalid.requestRectangleOnScreen(
                        new Rect(0, 0, invalid.getWidth(), invalid.getHeight()), true));
                return;
            }
            BookingHospitalSelection initial = BookingDepartmentSheet.readSelection(requireArguments());
            // 병원 이름을 바꾼 경우 다른 병원의 좌표를 예약에 섞지 않는다.
            boolean sameHospital = name.equals(initial.getHospitalName());
            BookingHospitalSelection result = new BookingHospitalSelection(name, departmentName,
                    sameHospital ? initial.getHospitalLatitude() : 0,
                    sameHospital ? initial.getHospitalLongitude() : 0);
            getParentFragmentManager().setFragmentResult(BookingDepartmentSheet.RESULT, BookingDepartmentSheet.selectionBundle(result));
            dismiss();
        }));
        return dialog;
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        if (hospital != null) outState.putString("hospital", hospital.getText().toString());
        if (department != null) outState.putString("department", department.getText().toString());
        super.onSaveInstanceState(outState);
    }
}
