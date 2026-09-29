package com.example.bodeul.ui.navigation;

import androidx.annotation.NonNull;

import com.example.bodeul.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 공통 하단 내비게이션의 선택 상태와 탭 이벤트를 화면에 연결한다.
 */
public final class ClientBottomNavigationBinder {
    public interface Listener {
        void onTabSelected(ClientBottomNavigationTab tab);
    }

    private ClientBottomNavigationBinder() {
    }

    public static void bind(
            @NonNull BottomNavigationView navigationView,
            @NonNull ClientBottomNavigationTab selectedTab,
            @NonNull Listener listener
    ) {
        ClientBottomNavigationInsets.apply(navigationView);
        navigationView.setOnItemSelectedListener(null);
        navigationView.setOnItemReselectedListener(null);
        navigationView.setSelectedItemId(resolveMenuItemId(selectedTab));
        navigationView.setOnItemReselectedListener(item -> {
            ClientBottomNavigationTab tab = resolveTab(item.getItemId());
            // 복원된 표시가 다른 탭이면 재선택도 이동으로 처리한다.
            if (tab != null && tab != selectedTab) {
                navigationView.setSelectedItemId(resolveMenuItemId(selectedTab));
                listener.onTabSelected(tab);
            }
        });
        navigationView.setOnItemSelectedListener(item -> {
            ClientBottomNavigationTab tab = resolveTab(item.getItemId());
            if (tab == null) {
                return false;
            }
            if (tab != selectedTab) {
                // 목적지는 별도 Activity다. 복귀할 원래 화면의 선택 표시는 유지한다.
                navigationView.setSelectedItemId(resolveMenuItemId(selectedTab));
                listener.onTabSelected(tab);
                return false;
            }
            return true;
        });
    }

    private static int resolveMenuItemId(ClientBottomNavigationTab tab) {
        switch (tab) {
            case SCHEDULE_HISTORY:
                return R.id.clientNavScheduleHistory;
            case COMPANION_ROOM:
                return R.id.clientNavCompanionRoom;
            case PROFILE:
                return R.id.clientNavProfile;
            case HOME:
            default:
                return R.id.clientNavHome;
        }
    }

    private static ClientBottomNavigationTab resolveTab(int itemId) {
        if (itemId == R.id.clientNavHome) {
            return ClientBottomNavigationTab.HOME;
        }
        if (itemId == R.id.clientNavScheduleHistory) {
            return ClientBottomNavigationTab.SCHEDULE_HISTORY;
        }
        if (itemId == R.id.clientNavCompanionRoom) {
            return ClientBottomNavigationTab.COMPANION_ROOM;
        }
        if (itemId == R.id.clientNavProfile) {
            return ClientBottomNavigationTab.PROFILE;
        }
        return null;
    }
}
