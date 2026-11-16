package com.example.bodeul.ui.profile;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.res.ResourcesCompat;
import com.example.bodeul.R;

/** 일반·매니저 마이페이지에서 계정 표시와 메뉴 배치를 공유한다. */
public final class ClientProfileBinder {
    private final View root;
    public ClientProfileBinder(View root) { this.root = root; }

    public void bind(ClientProfileScreenModel model, boolean manager, ProfileMenuBinder.Listener listener) {
        ((TextView) root.findViewById(R.id.textClientProfileName)).setText(model.getName());
        ((TextView) root.findViewById(R.id.textClientProfileEmail)).setText(model.getEmail());
        root.findViewById(R.id.buttonProfileGeneral).setSelected(!manager);
        root.findViewById(R.id.buttonProfileManager).setSelected(manager);
        root.findViewById(R.id.profileManagerSection).setVisibility(manager ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.buttonProfileEdit).setVisibility(manager ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.buttonProfileView).setVisibility(manager ? View.VISIBLE : View.GONE);
        LinearLayout menus = root.findViewById(R.id.profileMenuContainer);
        menus.removeAllViews();
        if (manager) ProfileMenuBinder.add(menus, ProfileMenuItem.SETTLEMENT, listener);
        for (ProfileMenuItem item : new ProfileMenuItem[]{ProfileMenuItem.SUPPORT, ProfileMenuItem.NOTICE,
                ProfileMenuItem.FAQ, ProfileMenuItem.TERMS, ProfileMenuItem.VERSION}) {
            ProfileMenuBinder.add(menus, item, listener);
        }
        LinearLayout shortcuts = root.findViewById(R.id.profileManagerShortcuts);
        shortcuts.removeAllViews();
        if (manager) for (ProfileMenuItem item : new ProfileMenuItem[]{ProfileMenuItem.MATCHING,
                ProfileMenuItem.REPORT, ProfileMenuItem.DOCUMENTS}) addShortcut(shortcuts, item, listener);
    }
    public void clear() {
        ((TextView) root.findViewById(R.id.textClientProfileName)).setText("");
        ((TextView) root.findViewById(R.id.textClientProfileEmail)).setText("");
        ((LinearLayout) root.findViewById(R.id.profileMenuContainer)).removeAllViews();
        ((LinearLayout) root.findViewById(R.id.profileManagerShortcuts)).removeAllViews();
    }
    private void addShortcut(LinearLayout container, ProfileMenuItem item, ProfileMenuBinder.Listener listener) {
        LinearLayout button = new LinearLayout(container.getContext());
        button.setId(item.viewId);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(android.view.Gravity.CENTER);
        button.setFocusable(true);
        button.setBackgroundResource(R.drawable.bg_final_profile_surface);
        button.setPadding(dp(4), dp(4), dp(4), dp(4));
        button.setOnClickListener(view -> listener.onSelected(item));
        ImageView image = new ImageView(container.getContext());
        image.setImageResource(item.iconId);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (item != ProfileMenuItem.DOCUMENTS) image.setPadding(dp(5), dp(5), dp(5), dp(5));
        button.addView(image, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView label = new TextView(container.getContext());
        label.setText(item.labelId);
        label.setTextSize(13);
        label.setTextColor(root.getContext().getColor(R.color.final_text_primary));
        label.setTypeface(ResourcesCompat.getFont(root.getContext(), R.font.pretendard_medium));
        label.setIncludeFontPadding(false);
        label.setGravity(android.view.Gravity.CENTER);
        label.setPadding(0, dp(5), 0, 0);
        button.addView(label, new LinearLayout.LayoutParams(-1, -2));
        container.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
    }
    private int dp(float value) { return Math.round(value * root.getResources().getDisplayMetrics().density); }
}
