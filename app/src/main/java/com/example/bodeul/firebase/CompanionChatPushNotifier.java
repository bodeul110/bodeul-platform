package com.example.bodeul.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.bodeul.R;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.util.NotificationPermissionSupport;

/**
 * 안심 채팅 새 메시지를 시스템 알림으로 노출한다.
 */
public final class CompanionChatPushNotifier {
    public static final String CHANNEL_ID = "companionChatUpdates";

    private CompanionChatPushNotifier() {}

    public static void showMessageNotification(
            @NonNull Context context,
            @NonNull CompanionChatPushPayload payload
    ) {
        createChannelIfNeeded(context);

        Intent destinationIntent = CompanionChatActivity.createIntent(
                context,
                payload.getAppointmentRequestId()
        );
        destinationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                payload.toNotificationId(),
                destinationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String body = resolveBody(context, payload);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(resolveTitle(context, payload))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationPermissionSupport.notifyIfPermitted(
                context,
                payload.toNotificationId(),
                builder.build()
        );
    }

    @NonNull
    private static String resolveTitle(@NonNull Context context, @NonNull CompanionChatPushPayload payload) {
        if (!TextUtils.isEmpty(payload.getTitle())) {
            return payload.getTitle();
        }
        return context.getString(R.string.companion_chat_push_title_fallback);
    }

    @NonNull
    private static String resolveBody(@NonNull Context context, @NonNull CompanionChatPushPayload payload) {
        if (!TextUtils.isEmpty(payload.getBody())) {
            return payload.getBody();
        }
        return context.getString(R.string.companion_chat_push_body_fallback);
    }

    public static void createChannelIfNeeded(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null || notificationManager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.companion_chat_push_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.companion_chat_push_channel_description));
        notificationManager.createNotificationChannel(channel);
    }
}
