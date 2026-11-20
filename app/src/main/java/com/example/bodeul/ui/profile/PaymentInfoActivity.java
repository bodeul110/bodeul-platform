package com.example.bodeul.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.bodeul.R;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPaymentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.booking.BookingPresentationFormatter;
import com.example.bodeul.ui.booking.BookingStatusActivity;
import com.example.bodeul.ui.common.DetailViews;
import java.util.List;

public class PaymentInfoActivity extends SettingsDetailActivity {
    public static Intent createIntent(Context c) { return new Intent(c, PaymentInfoActivity.class); }
    protected BookingRepository bookings() { return ServiceLocator.provideBookingRepository(this); }
    @Override protected int titleResource() { return R.string.final_profile_payment; }
    @Override protected void showUser(User current, int attempt) {
        ready();
        content.setGravity(Gravity.TOP);
        LinearLayout card = DetailViews.column(content, true);
        DetailViews.space(card, 20);
        DetailViews.image(card, R.drawable.bodeul_final_empty_card, 80);
        TextView title = DetailViews.text(card, getString(R.string.batch_payment_methods), R.style.FinalHomeTitle);
        title.setGravity(Gravity.CENTER);
        TextView subtitle = DetailViews.text(card, getString(R.string.batch_payment_card_unavailable), R.style.FinalSettingsValue);
        subtitle.setGravity(Gravity.CENTER);
        DetailViews.muted(subtitle);
        DetailViews.space(card, 12);
        DetailViews.action(card, R.string.batch_payment_card, () -> message(R.string.batch_payment_methods, R.string.batch_payment_card_unavailable));
        DetailViews.space(content, 32);
        DetailViews.text(content, getString(R.string.batch_payment_history), R.style.FinalHomeTitle);
        LinearLayout history = DetailViews.column(content, false);
        if (current.getRole() != UserRole.PATIENT) {
            DetailViews.text(history, getString(R.string.batch_payment_patient_only), R.style.FinalProfileBody);
            return;
        }
        android.widget.ProgressBar spinner = new android.widget.ProgressBar(this);
        history.addView(spinner, DetailViews.dp(this, 36), DetailViews.dp(this, 36));
        bookings().getMyAppointmentRequests(current, new RepositoryCallback<List<AppointmentRequest>>() {
            @Override public void onSuccess(List<AppointmentRequest> rows) {
                if (!isCurrent(attempt)) return;
                history.removeAllViews();
                BookingPresentationFormatter format = new BookingPresentationFormatter(PaymentInfoActivity.this);
                if (rows != null) for (AppointmentRequest r : rows) {
                    if (r == null || r.getId() == null || r.getId().trim().isEmpty()
                            || (BookingPaymentStatus.fromValue(r.getPaymentStatusCode()) == BookingPaymentStatus.UNKNOWN
                            && BookingPaymentMethod.fromValue(r.getPaymentMethodCode()) == BookingPaymentMethod.UNKNOWN)) continue;
                    LinearLayout row = DetailViews.column(history, false);
                    row.setPadding(0, DetailViews.dp(PaymentInfoActivity.this, 16), 0, DetailViews.dp(PaymentInfoActivity.this, 16));
                    DetailViews.muted(DetailViews.text(row, format.toPaymentMethodLabel(r.getPaymentMethodCode()), R.style.FinalSettingsLabel));
                    DetailViews.text(row, r.getHospitalName() == null || r.getHospitalName().isEmpty()
                            ? getString(R.string.batch_reservation) : r.getHospitalName(), R.style.FinalProfileBody);
                    DetailViews.text(row, format.toPaymentStatusLabel(r.getPaymentStatusCode()), R.style.FinalSettingsValue);
                    if (r.getFinalPrice() > 0) DetailViews.text(row, format.formatPrice(r.getFinalPrice()), R.style.FinalSettingsValue);
                    row.setFocusable(true);
                    row.setOnClickListener(v -> {
                        if (isCurrent(attempt)) startActivity(BookingStatusActivity.createIntent(PaymentInfoActivity.this, r.getId()));
                    });
                    DetailViews.line(history);
                }
                if (history.getChildCount() == 0) {
                    content.setGravity(Gravity.CENTER_VERTICAL);
                    DetailViews.text(history, getString(R.string.batch_payment_none), R.style.FinalProfileBody);
                }
            }
            @Override public void onError(String ignored) {
                if (!isCurrent(attempt)) return;
                history.removeAllViews();
                DetailViews.text(history, getString(R.string.batch_data_error), R.style.FinalProfileBody);
                DetailViews.action(history, R.string.final_profile_retry, PaymentInfoActivity.this::reload);
            }
        });
    }
}
