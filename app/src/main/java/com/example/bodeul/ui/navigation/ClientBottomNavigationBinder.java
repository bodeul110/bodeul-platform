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
        navigationView.setSelectedItemId(resolveMenuItemId(selectedTab));
        navigationView.setOnItemReselectedListener(item -> {
            // 이미 선택한 최상위 화면에서는 중복 Activity를 만들지 않는다.
        });
        navigationView.setOnItemSelectedListener(item -> {
            ClientBottomNavigationTab tab = resolveTab(item.getItemId());
            if (tab == null) {
                return false;
            }
            if (tab != selectedTab) {
                listener.onTabSelected(tab);
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
