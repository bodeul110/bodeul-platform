package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;

/**
 * 예약 접수 또는 수정 완료 후 Figma 기준 성공 화면을 보여준다.
 */
public class BookingCompletionActivity extends AppCompatActivity {
    private static final String EXTRA_IS_UPDATED = "isUpdated";

    public static Intent createIntent(Context context, AppointmentRequest request, boolean isUpdated) {
        Intent intent = new Intent(context, BookingCompletionActivity.class);
        BookingCompletionSnapshot.fromRequest(request).writeToIntent(intent);
        intent.putExtra(EXTRA_IS_UPDATED, isUpdated);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        setContentView(R.layout.activity_booking_completion);
        View root = findViewById(R.id.bookingCompletionRoot);
        BookingCompletionInsets.applySheet(root, findViewById(R.id.bookingCompletionBottomAction));
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightNavigationBars(true);

        BookingCompletionSnapshot snapshot = BookingCompletionSnapshot.fromIntent(getIntent());
        boolean isUpdated = getIntent().getBooleanExtra(EXTRA_IS_UPDATED, false);

        BookingCompletionCoordinator coordinator = new BookingCompletionCoordinator(this);
        BookingCompletionScreenModel model = coordinator.buildScreenModel(snapshot, isUpdated);
        setTitle(model.getScreenTitle());
        new BookingCompletionBinder(root).bind(model);

        findViewById(R.id.buttonBackBookingCompletion).setOnClickListener(view -> finish());
        findViewById(R.id.buttonBookingCompletionList).setOnClickListener(view -> openBookingHistory());
        View detail = findViewById(R.id.buttonBookingCompletionDetail);
        detail.setVisibility(snapshot.getRequestId().isEmpty() ? View.GONE : View.VISIBLE);
        detail.setOnClickListener(view -> startActivity(
                BookingStatusActivity.createIntent(this, snapshot.getRequestId())));
        View map = findViewById(R.id.buttonBookingCompletionMap);
        map.setVisibility(snapshot.getHospitalName().isEmpty() ? View.GONE : View.VISIBLE);
        map.setContentDescription(getString(R.string.final_hospital_map_description, snapshot.getHospitalName()));
        map.setOnClickListener(view -> openHospitalMap(snapshot));
    }

    private void configureSystemBars() {
        EdgeToEdge.enable(this);
    }

    private void openHospitalMap(BookingCompletionSnapshot snapshot) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="
                    + Uri.encode(snapshot.getHospitalMapQuery()))));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.final_hospital_map_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void openBookingHistory() {
        Intent intent = new Intent(this, ClientBookingHistoryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
