package com.example.bodeul.data;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import com.example.bodeul.firebase.CompanionChatPushNotifier;
import com.example.bodeul.util.NotificationPermissionSupport;

public final class DeviceNotificationSettingsRepository implements NotificationSettingsRepository {
    private final Context context;
    public DeviceNotificationSettingsRepository(Context context) { this.context = context.getApplicationContext(); }

    @Override public boolean isChatEnabled() {
        if (!NotificationPermissionSupport.canPostNotifications(context)) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        NotificationChannel channel = manager.getNotificationChannel(CompanionChatPushNotifier.CHANNEL_ID);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    @Override public Intent chatSettingsIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !NotificationPermissionSupport.canPostNotifications(context)) {
            return appSettingsIntent();
        }
        // 최초 푸시 전에도 같은 채널을 연다. 기존 채널의 사용자 설정은 덮어쓰지 않는다.
        CompanionChatPushNotifier.createChannelIfNeeded(context);
        return new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                .putExtra(Settings.EXTRA_CHANNEL_ID, CompanionChatPushNotifier.CHANNEL_ID);
    }
    @Override public Intent appSettingsIntent() { return NotificationPermissionSupport.createNotificationSettingsIntent(context); }
}
