package com.example.bodeul.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.bodeul.R;

final class NotificationSettingsBinder {
    interface Actions { void chat(); void system(); void unavailable(int title); }
    private final LinearLayout parent;
    private final Actions actions;
    private ImageButton chatSwitch;
    private TextView chatState;

    NotificationSettingsBinder(LinearLayout parent, Actions actions) {
        this.parent = parent;
        this.actions = actions;
        addRow(R.string.final_settings_notification_reminder, R.string.final_settings_notification_reminder_body, false);
        addRow(R.string.final_settings_notification_matching, R.string.final_settings_notification_matching_body, false);
        addRow(R.string.final_settings_notification_progress, R.string.final_settings_notification_progress_body, false);
        addRow(R.string.final_settings_notification_chat, R.string.final_settings_notification_chat_body, true);
        addRow(R.string.final_settings_notification_marketing, R.string.final_settings_notification_marketing_body, false);
        View footer = LayoutInflater.from(parent.getContext()).inflate(R.layout.include_notification_settings_footer, parent, false);
        footer.findViewById(R.id.buttonSystemNotifications).setOnClickListener(view -> actions.system());
        parent.addView(footer);
    }
    void bindChat(boolean enabled) {
        int state = enabled ? R.string.final_settings_notification_on : R.string.final_settings_notification_off;
        chatSwitch.setImageResource(enabled ? R.drawable.final_settings_switch_on : R.drawable.final_settings_switch_off);
        chatSwitch.setContentDescription(parent.getContext().getString(R.string.final_settings_notification_action,
                parent.getContext().getString(R.string.final_settings_notification_chat), parent.getContext().getString(state)));
        chatState.setText(state);
    }
    private void addRow(int title, int description, boolean chat) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_setting, parent, false);
        ((TextView) row.findViewById(R.id.notificationSettingTitle)).setText(title);
        ((TextView) row.findViewById(R.id.notificationSettingDescription)).setText(description);
        ImageButton button = row.findViewById(R.id.notificationSettingSwitch);
        TextView state = row.findViewById(R.id.notificationSettingState);
        if (chat) {
            chatSwitch = button;
            chatState = state;
            button.setOnClickListener(view -> actions.chat());
        } else {
            state.setText(R.string.final_settings_link_pending);
            button.setAlpha(0.45f);
            button.setContentDescription(parent.getContext().getString(R.string.final_settings_notification_pending_action,
                    parent.getContext().getString(title)));
            button.setOnClickListener(view -> actions.unavailable(title));
        }
        parent.addView(row);
    }
}
