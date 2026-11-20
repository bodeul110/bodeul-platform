package com.example.bodeul.ui.booking;

import android.content.Context;
import android.os.Build;
import androidx.annotation.ChecksSdkIntAtLeast;
import com.example.bodeul.R;

final class GuardianBookingAvailability {
    private GuardianBookingAvailability() { }

    @ChecksSdkIntAtLeast(api = 26)
    static boolean enabled(Context context) {
        return supports(Build.VERSION.SDK_INT, context.getResources().getBoolean(R.bool.bodeul_guardian_booking_inbox_enabled));
    }

    static boolean supports(int sdk, boolean featureEnabled) { return sdk >= 26 && featureEnabled; }
}
