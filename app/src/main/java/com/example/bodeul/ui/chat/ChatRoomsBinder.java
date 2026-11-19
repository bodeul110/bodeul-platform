package com.example.bodeul.ui.chat;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.common.DetailViews;
import java.util.List;

final class ChatRoomsBinder {
    interface Listener {
        void select(ChatRoomDirectory.Filter filter);
        void search(String query);
        void open(String id);
    }
    private final LinearLayout list;
    private final UserRole role;
    private final Listener listener;
    ChatRoomsBinder(LinearLayout root, UserRole role, ChatRoomDirectory.Filter filter, String query, Listener listener) {
        this.role = role;
        this.listener = listener;
        Context c = root.getContext();
        int[] labels = {R.string.batch_all, R.string.batch_chat_unread, R.string.batch_chat_active, R.string.batch_progress_done};
        DetailViews.filters(root, labels, filter.ordinal(), true, i -> listener.select(ChatRoomDirectory.Filter.values()[i]));
        DetailViews.space(root, 16);
        EditText search = new androidx.appcompat.widget.AppCompatEditText(c);
        search.setSingleLine(true);
        search.setSaveEnabled(false);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int imeOptions = EditorInfo.IME_ACTION_SEARCH;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        search.setImeOptions(imeOptions);
        search.setBackgroundResource(R.drawable.bg_final_hospital_search);
        search.setMinHeight(DetailViews.dp(c, 48));
        search.setPadding(DetailViews.dp(c, 12), 0, DetailViews.dp(c, 12), 0);
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_figma_hospital_search_search, 0, 0, 0);
        search.setCompoundDrawablePadding(DetailViews.dp(c, 7));
        search.setTextSize(15);
        search.setHint(R.string.batch_chat_search);
        search.setText(query);
        search.setId(R.id.batchRoomSearch);
        root.setFocusableInTouchMode(true);
        root.addView(search,-1,-2);
        DetailViews.space(root, 12);
        list = DetailViews.column(root, false);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void afterTextChanged(Editable s) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { listener.search(s.toString()); }
        });
    }
    void render(List<ChatRoomDirectory.Room> rooms, ChatRoomDirectory.Filter filter, String query) {
        list.removeAllViews();
        Context c = list.getContext();
        List<ChatRoomDirectory.Room> filtered = ChatRoomDirectory.filter(rooms, filter, query);
        if (filtered.isEmpty()) {
            DetailViews.space(list, 32);
            DetailViews.muted(DetailViews.text(list,c.getString(filter == ChatRoomDirectory.Filter.UNREAD
                    ? R.string.batch_chat_unread_unavailable : query.trim().isEmpty() ? R.string.batch_chat_no_rooms
                    : R.string.batch_chat_no_match), R.style.FinalProfileBody));
        }
        for (ChatRoomDirectory.Room room : filtered) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = DetailViews.dp(c, 12);
            row.setPadding(0,pad,0,pad);
            list.addView(row,-1,-2);
            DetailViews.image(row,R.drawable.bodeul_final_profile_avatar,48);
            LinearLayout body = new LinearLayout(c);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(pad,0,0,0);
            row.addView(body,new LinearLayout.LayoutParams(0,-2,1));
            String heading = room.counterpart.isEmpty() ? room.hospital : room.counterpart;
            DetailViews.text(body, heading.isEmpty() ? c.getString(R.string.batch_reservation) : heading, R.style.FinalSettingsLabel);
            if (!room.hospital.isEmpty() && !room.counterpart.isEmpty())
                DetailViews.muted(DetailViews.text(body, room.hospital, R.style.FinalSettingsValue));
            DetailViews.muted(DetailViews.text(body,c.getString(room.status == AppointmentStatus.IN_PROGRESS ? R.string.batch_progress_now :
                    room.status == AppointmentStatus.COMPLETED ? R.string.batch_progress_done : R.string.batch_chat_matched),R.style.FinalSettingsValue));
            if (role == UserRole.GUARDIAN) DetailViews.muted(DetailViews.text(body,c.getString(R.string.batch_chat_guardian),R.style.FinalSettingsValue));
            row.setFocusable(true);
            row.setOnClickListener(v -> listener.open(room.id));
            DetailViews.line(list);
        }
    }
}
