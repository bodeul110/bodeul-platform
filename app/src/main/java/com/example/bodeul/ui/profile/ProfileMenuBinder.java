package com.example.bodeul.ui.profile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.bodeul.BuildConfig;
import com.example.bodeul.R;

/** 원본 아이콘의 프레임과 48dp 터치 영역을 유지하며 큰 글자는 행 높이를 늘린다. */
public final class ProfileMenuBinder {
    public interface Listener { void onSelected(ProfileMenuItem item); }
    private ProfileMenuBinder() { }

    public static void add(LinearLayout container, ProfileMenuItem item, Listener listener) {
        Context context = container.getContext();
        View row = LayoutInflater.from(context).inflate(R.layout.item_final_profile_menu, container, false);
        row.setId(item.viewId);
        TextView label = row.findViewById(R.id.textProfileMenuLabel);
        label.setText(item == ProfileMenuItem.VERSION
                ? context.getString(item.labelId, BuildConfig.VERSION_NAME) : context.getString(item.labelId));
        ImageView icon = row.findViewById(R.id.imageProfileMenuIcon);
        icon.setImageResource(item.iconId);
        if (item == ProfileMenuItem.SUPPORT) {
            int padding = Math.round(3 * context.getResources().getDisplayMetrics().density);
            icon.setPadding(padding, padding, padding, padding);
        }
        row.setOnClickListener(view -> listener.onSelected(item));
        container.addView(row);
    }
}
