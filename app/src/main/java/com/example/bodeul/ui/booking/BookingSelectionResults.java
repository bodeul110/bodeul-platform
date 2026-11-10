package com.example.bodeul.ui.booking;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.domain.model.BookingMeetingLocationSelection;
import com.example.bodeul.domain.model.User;

import java.util.Objects;

/** 선택기 실행의 소유자를 보존하고 폼 복원 뒤에 결과를 한 번만 적용한다. */
final class BookingSelectionResults {
    enum Kind { HOSPITAL, APPOINTMENT, HEALTH, LOCATION }

    @Nullable private Bundle pending;

    BookingSelectionResults(@Nullable Bundle saved) {
        pending = saved == null ? null : new Bundle(saved);
    }

    void begin(Kind kind, User owner, @Nullable String editId) {
        pending = new Bundle();
        pending.putString("kind", kind.name());
        pending.putString("owner", owner.getId());
        pending.putString("role", owner.getRole().name());
        pending.putString("edit", editId);
    }

    void accept(Kind kind, int resultCode, @Nullable Intent data) {
        if (pending == null || !kind.name().equals(pending.getString("kind"))) return;
        if (resultCode != Activity.RESULT_OK || data == null) {
            clear();
            return;
        }
        pending.putParcelable("result", new Intent(data));
    }

    void applyTo(BookingFormBinder form, User owner, @Nullable String editId) {
        if (pending == null || !pending.containsKey("result")) return;
        Bundle result = pending;
        clear();
        if (!Objects.equals(owner.getId(), result.getString("owner"))
                || !owner.getRole().name().equals(result.getString("role"))
                || !Objects.equals(editId, result.getString("edit"))) return;
        Intent data = result.getParcelable("result");
        if (data == null) return;
        switch (Kind.valueOf(result.getString("kind"))) {
            case HOSPITAL:
                BookingHospitalSelection hospital = BookingHospitalSelectorActivity.parseResult(data);
                if (hospital.isComplete()) form.applyHospitalSelection(hospital);
                break;
            case APPOINTMENT:
                String appointment = BookingAppointmentSelectorActivity.parseResult(data);
                if (BookingAppointmentDateTime.parse(appointment) != null) form.applyAppointmentAt(appointment);
                break;
            case HEALTH:
                BookingHealthProfileSelection health = BookingHealthProfileActivity.parseResult(data);
                if (health.hasRequiredCondition()) form.applyHealthProfileSelection(health);
                break;
            case LOCATION:
                BookingMeetingLocationSelection location = BookingLocationSelectorActivity.parseResult(data);
                if (location.isComplete()) form.applyMeetingLocationSelection(location);
                break;
        }
    }

    @Nullable Bundle saveState() { return pending == null ? null : new Bundle(pending); }
    void clear() { pending = null; }
}
