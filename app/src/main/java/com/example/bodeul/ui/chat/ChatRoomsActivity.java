package com.example.bodeul.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewStub;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.bodeul.R;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.common.DetailViews;
import com.example.bodeul.ui.manager.ManagerActivity;
import com.example.bodeul.ui.navigation.ClientBottomNavigationBinder;
import com.example.bodeul.ui.navigation.ClientBottomNavigationRouter;
import com.example.bodeul.ui.navigation.ClientBottomNavigationTab;
import com.example.bodeul.ui.profile.ManagerMyPageActivity;
import com.example.bodeul.ui.profile.NotificationSettingsActivity;
import com.example.bodeul.ui.profile.SettingsDetailActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.Collections;
import java.util.List;

/** 목록은 예약 요약만 읽고, 선택한 방의 권한 검증과 읽음 처리는 기존 상세에 맡긴다. */
public class ChatRoomsActivity extends SettingsDetailActivity {
    private ChatRoomsBinder binder;
    private BottomNavigationView nav;
    private List<ChatRoomDirectory.Room> rooms = Collections.emptyList();
    private ChatRoomDirectory.Filter filter = ChatRoomDirectory.Filter.ALL;
    private String query = "", owner = "";
    private boolean keyboardVisible;
    public static Intent createIntent(Context c) { return new Intent(c, ChatRoomsActivity.class); }
    protected BookingRepository bookings() { return ServiceLocator.provideBookingRepository(this); }
    protected ManagerRepository managers() { return ServiceLocator.provideManagerRepository(this); }
    @Override protected int titleResource() { return R.string.batch_chat_title; }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        View root = findViewById(R.id.settingsDetailRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets) -> {
            int system = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
            Insets safe = insets.getInsets(system | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (nav != null) nav.setVisibility(user != null && !keyboardVisible ? View.VISIBLE : View.GONE);
            return insets;
        });
    }
    @Override protected void onPrivateStateCleared() {
        rooms = Collections.emptyList();
        binder = null;
        if (nav != null) nav.setVisibility(View.GONE);
        findViewById(R.id.buttonRoomNotifications).setVisibility(View.GONE);
    }
    @Override protected void showUser(User current, int attempt) {
        String identity = current.getRole().name() + ":" + current.getId();
        if (!identity.equals(owner)) { query = ""; filter = ChatRoomDirectory.Filter.ALL; owner = identity; }
        bindNavigation(current.getRole(), attempt);
        if (current.getRole() == UserRole.MANAGER) {
            managers().getManagerDashboard(current.getId(), new RepositoryCallback<ManagerDashboard>() {
                public void onSuccess(ManagerDashboard result) { showRooms(current, attempt, ChatRoomDirectory.manager(result)); }
                public void onError(String reason) {
                    if (ManagerRepository.MESSAGE_NO_ACTIVE_SESSION.equals(reason)) showRooms(current, attempt, Collections.emptyList());
                    else failed(attempt);
                }
            });
        } else bookings().getMyAppointmentRequests(current, new RepositoryCallback<List<AppointmentRequest>>() {
            public void onSuccess(List<AppointmentRequest> result) { showRooms(current, attempt, ChatRoomDirectory.client(current.getRole(), result)); }
            public void onError(String ignored) { failed(attempt); }
        });
    }
    private void showRooms(User current, int attempt, List<ChatRoomDirectory.Room> result) {
        if (!isCurrent(attempt)) return;
        ready();
        rooms = result;
        content.removeAllViews();
        binder = new ChatRoomsBinder(content, current.getRole(), filter, query, new ChatRoomsBinder.Listener() {
            public void select(ChatRoomDirectory.Filter f) { if (isCurrent(attempt)) { filter = f; refresh(); } }
            public void search(String q) { if (isCurrent(attempt)) { query = q; refresh(); } }
            public void open(String id) {
                if (isCurrent(attempt)) startActivity(CompanionChatActivity.createIntent(ChatRoomsActivity.this,id));
            }
        });
        refresh();
    }
    private void refresh() { if (binder != null) binder.render(rooms, filter, query); }
    private void failed(int attempt) {
        if (!isCurrent(attempt)) return;
        ready();
        rooms = Collections.emptyList();
        binder = null;
        content.removeAllViews();
        DetailViews.text(content, getString(R.string.batch_data_error), R.style.FinalProfileBody);
        DetailViews.action(content, R.string.final_profile_retry, this::reload);
    }
    private void bindNavigation(UserRole role, int attempt) {
        View bell = findViewById(R.id.buttonRoomNotifications);
        bell.setVisibility(View.VISIBLE);
        bell.setOnClickListener(v -> { if (isCurrent(attempt)) startActivity(NotificationSettingsActivity.createIntent(this)); });
        if (nav == null) nav = (BottomNavigationView) ((ViewStub)findViewById(R.id.roomsBottomStub)).inflate();
        nav.setOnItemSelectedListener(null);
        nav.setOnItemReselectedListener(null);
        nav.getMenu().clear();
        nav.inflateMenu(role == UserRole.MANAGER ? R.menu.menu_manager_final_navigation : R.menu.menu_client_bottom_navigation);
        nav.setVisibility(keyboardVisible ? View.GONE : View.VISIBLE);
        if (role == UserRole.MANAGER) {
            nav.setSelectedItemId(R.id.navManagerChat);
            nav.setOnItemSelectedListener(item -> {
                if (!isCurrent(attempt) || item.getItemId() == R.id.navManagerChat) return false;
                Intent target = item.getItemId() == R.id.navManagerProfile ? new Intent(this,ManagerMyPageActivity.class)
                        : ManagerActivity.createTabIntent(this,item.getItemId());
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
                return false;
            });
        } else ClientBottomNavigationBinder.bind(nav,ClientBottomNavigationTab.COMPANION_ROOM, tab -> {
            if (isCurrent(attempt)) ClientBottomNavigationRouter.open(this,ClientBottomNavigationTab.COMPANION_ROOM,tab);
        });
        // 루트가 이미 시스템 바 여백을 처리하므로 하단 탭이 같은 여백을 또 더하지 않는다.
        ViewCompat.setOnApplyWindowInsetsListener(nav,(v,insets) -> insets);
    }
}
