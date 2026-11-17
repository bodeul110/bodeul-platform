package com.example.bodeul.ui.support;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.Nullable;
import com.example.bodeul.data.ServiceLocator;

/** 기존 예약·답변 알림의 진입 계약을 유지한다. */
public final class ClientSupportActivity extends SupportScreenActivity {
    public static Intent createIntent(Context context) { return new Intent(context, ClientSupportActivity.class); }
    public static Intent createIntent(Context context, @Nullable String requestId) { return createIntent(context, requestId, null); }
    public static Intent createIntent(Context context, @Nullable String requestId, @Nullable String supportRequestId) {
        return createIntent(context).putExtra("requestId", requestId).putExtra("supportRequestId", supportRequestId);
    }
    @Override protected SupportSource provideSupportSource() {
        return new SupportSource(ServiceLocator.provideClientSupportRepository(this), ServiceLocator.provideBookingRepository(this));
    }
}
