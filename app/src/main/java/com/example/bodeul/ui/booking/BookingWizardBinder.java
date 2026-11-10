package com.example.bodeul.ui.booking;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.R;

/** 단계와 표시만 관리하며 검증·저장·권한 판단은 기존 예약 계층에 맡긴다. */
final class BookingWizardBinder {
    private final View root;
    private final ScrollView scroll;
    private BookingStep step = BookingStep.PARTICIPANT;
    private boolean participantSelected;
    private boolean history;
    private boolean ready;
    private boolean loading;

    BookingWizardBinder(View root) {
        this.root = root;
        scroll = root.findViewById(R.id.scrollBooking);
        ViewCompat.setAccessibilityHeading(root.findViewById(R.id.textBookingStepTitle), true);
        render();
    }

    BookingStep step() { return step; }
    boolean hasParticipant() { return participantSelected; }
    boolean isHistory() { return history; }

    void selectPatient() {
        participantSelected = true;
        render();
    }

    void showStep(BookingStep value) {
        step = value;
        history = false;
        moveToTop();
        render();
    }

    void showHistory(boolean value) {
        history = value;
        moveToTop();
        render();
    }

    void reset() {
        participantSelected = false;
        showStep(BookingStep.PARTICIPANT);
    }

    void setAvailable(boolean ready, boolean loading) {
        this.ready = ready;
        this.loading = loading;
        render();
    }

    Bundle saveState() {
        Bundle state = new Bundle();
        state.putString("step", step.name());
        state.putBoolean("participant", participantSelected);
        state.putBoolean("history", history);
        state.putInt("scroll", scroll.getScrollY());
        return state;
    }

    void restoreState(Bundle state) {
        if (state == null) return;
        participantSelected = state.getBoolean("participant");
        step = participantSelected ? BookingStep.restore(state.getString("step")) : BookingStep.PARTICIPANT;
        history = state.getBoolean("history");
        render();
        scroll.post(() -> scroll.scrollTo(0, state.getInt("scroll")));
    }

    private void moveToTop() {
        View focused = root.findFocus();
        if (focused != null) focused.clearFocus();
        if (ViewCompat.getWindowInsetsController(root) != null) {
            ViewCompat.getWindowInsetsController(root).hide(WindowInsetsCompat.Type.ime());
        }
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void render() {
        int[] panels = {R.id.bookingStepParticipant, R.id.bookingStepVisit,
                R.id.bookingStepHealth, R.id.bookingStepPayment};
        int[] titles = {R.string.final_booking_who, R.string.final_booking_visit,
                R.string.final_booking_health, R.string.final_booking_payment};
        for (int i = 0; i < panels.length; i++) visible(panels[i], !history && step.ordinal() == i);
        visible(R.id.bookingHistoryPanel, history);
        ((TextView) root.findViewById(R.id.textBookingStepTitle)).setText(
                history ? R.string.final_booking_history : titles[step.ordinal()]);
        visible(R.id.bookingContactFields, participantSelected);
        root.findViewById(R.id.buttonBookingSelf).setSelected(participantSelected);
        ((TextView) root.findViewById(R.id.textBookingSelf)).setTextColor(root.getContext().getColor(
                participantSelected ? R.color.final_text_accent_accessible : R.color.final_text_primary));
        visible(R.id.checkBookingSelf, participantSelected);
        visible(R.id.bookingFooter, ready && !history && participantSelected);
        visible(R.id.buttonBookingNext, step != BookingStep.PAYMENT);
        visible(R.id.buttonSubmitBooking, step == BookingStep.PAYMENT);
        visible(R.id.bookingStepProgress, !history);
        ((ProgressBar) root.findViewById(R.id.bookingProgressParticipant)).setProgress(participantSelected ? 100 : 19);
        ((ProgressBar) root.findViewById(R.id.bookingProgressVisit)).setProgress(step.ordinal() >= 1 ? 100 : 0);
        ((ProgressBar) root.findViewById(R.id.bookingProgressHealth)).setProgress(
                step == BookingStep.PAYMENT ? 100 : step == BookingStep.HEALTH ? 50 : 0);
        root.findViewById(R.id.bookingStepProgress).setContentDescription(root.getContext().getString(
                R.string.final_booking_progress, Math.min(3, step.ordinal() + 1)));
        for (int id : new int[]{R.id.buttonBookingSelf, R.id.buttonBookingGuardian,
                R.id.buttonBookingNext, R.id.buttonCloseBooking}) {
            root.findViewById(id).setEnabled(ready && !loading);
        }
    }

    private void visible(int id, boolean value) {
        root.findViewById(id).setVisibility(value ? View.VISIBLE : View.GONE);
    }
}
