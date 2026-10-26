package com.example.bodeul.ui.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.LayoutInflater;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.bodeul.R;
import com.example.bodeul.debug.FigmaScreenPreviewSelectorActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ClientBottomNavigationSelectionTest {
    @Test
    public void leavingTab_keepsSourceSelection_andRepeatedDestinationStillNavigates() {
        withNavigation(navigation -> {
            List<ClientBottomNavigationTab> destinations = new ArrayList<>();
            ClientBottomNavigationBinder.bind(
                    navigation, ClientBottomNavigationTab.HOME, destinations::add);
            navigation.findViewById(R.id.clientNavScheduleHistory).performClick();
            assertEquals(R.id.clientNavHome, navigation.getSelectedItemId());
            navigation.findViewById(R.id.clientNavScheduleHistory).performClick();
            assertEquals(Arrays.asList(ClientBottomNavigationTab.SCHEDULE_HISTORY,
                    ClientBottomNavigationTab.SCHEDULE_HISTORY), destinations);
            navigation.findViewById(R.id.clientNavHome).performClick();
            assertEquals("현재 화면의 탭은 중복 이동하지 않는다", 2, destinations.size());
        });
    }

    @Test
    public void rebind_resetsSelection_withoutInvokingPreviousListener() {
        withNavigation(navigation -> {
            List<ClientBottomNavigationTab> previous = new ArrayList<>();
            List<ClientBottomNavigationTab> current = new ArrayList<>();
            ClientBottomNavigationBinder.bind(
                    navigation, ClientBottomNavigationTab.HOME, previous::add);
            ClientBottomNavigationBinder.bind(
                    navigation, ClientBottomNavigationTab.PROFILE, current::add);
            assertTrue("재연결 중 이전 화면으로 이동하면 안 된다", previous.isEmpty());
            assertTrue(current.isEmpty());
            assertEquals(R.id.clientNavProfile, navigation.getSelectedItemId());
        });
    }

    @Test
    public void restoredForeignSelection_reselectsDestination_insteadOfIgnoringTap() {
        withNavigation(navigation -> {
            List<ClientBottomNavigationTab> destinations = new ArrayList<>();
            ClientBottomNavigationBinder.bind(
                    navigation, ClientBottomNavigationTab.HOME, destinations::add);
            // 이전 버전의 저장 상태처럼 실제 화면과 선택 표시가 다른 경우를 재현한다.
            navigation.getMenu().findItem(R.id.clientNavScheduleHistory).setChecked(true);
            assertEquals(R.id.clientNavScheduleHistory, navigation.getSelectedItemId());
            navigation.findViewById(R.id.clientNavScheduleHistory).performClick();
            assertEquals(Arrays.asList(ClientBottomNavigationTab.SCHEDULE_HISTORY), destinations);
            assertEquals(R.id.clientNavHome, navigation.getSelectedItemId());
        });
    }

    private void withNavigation(NavigationAssertion assertion) {
        try (ActivityScenario<FigmaScreenPreviewSelectorActivity> scenario =
                     ActivityScenario.launch(FigmaScreenPreviewSelectorActivity.class)) {
            scenario.onActivity(activity -> {
                View screen = LayoutInflater.from(activity).inflate(
                        R.layout.activity_client_home_figma, null, false);
                assertion.run(screen.findViewById(R.id.clientBottomNavigation));
            });
        }
    }

    private interface NavigationAssertion {
        void run(BottomNavigationView navigation);
    }
}
