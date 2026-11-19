package com.example.bodeul.ui.profile;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import com.example.bodeul.R;
import com.example.bodeul.data.DeviceNotificationSettingsRepository;
import com.example.bodeul.data.NotificationSettingsRepository;
import com.example.bodeul.domain.model.User;

public class NotificationSettingsActivity extends SettingsDetailActivity {
    private NotificationSettingsRepository notifications;
    private NotificationSettingsBinder binder;
    public static Intent createIntent(Context context) { return new Intent(context, NotificationSettingsActivity.class); }
    protected NotificationSettingsRepository provideNotificationSettings() { return new DeviceNotificationSettingsRepository(this); }
    @Override protected int titleResource() { return R.string.final_settings_notifications_title; }
    @Override protected void showUser(User user, int attempt) {
        notifications = provideNotificationSettings();
        ready();
        binder = new NotificationSettingsBinder(content, new NotificationSettingsBinder.Actions() {
            @Override public void chat() { openSystem(true); }
            @Override public void system() { openSystem(false); }
            @Override public void unavailable(int title) { message(title, R.string.final_settings_notification_pending); }
        });
        refreshChat();
    }
    @Override protected void onResume() { super.onResume(); refreshChat(); }
    @Override protected void onPrivateStateCleared() { binder = null; notifications = null; }
    private void refreshChat() {
        if (binder != null && notifications != null && user != null) binder.bindChat(notifications.isChatEnabled());
    }
    private void openSystem(boolean chat) {
        if (notifications == null || user == null) return;
        try { startActivity(chat ? notifications.chatSettingsIntent() : notifications.appSettingsIntent()); }
        catch (ActivityNotFoundException | SecurityException error) {
            message(R.string.final_settings_notifications_title, R.string.final_profile_notification_unavailable);
        }
    }
}
