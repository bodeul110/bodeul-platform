package com.example.bodeul.debug;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.bodeul.BuildConfig;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.health.HealthInfoActivity;
import com.example.bodeul.ui.manager.*;
import com.example.bodeul.ui.profile.*;
import com.example.bodeul.ui.support.ClientSupportActivity;
import com.example.bodeul.ui.support.FaqActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 실서버 없이 운영 Activity의 표시·이동 요청·인증 수명주기를 검증한다. */
@RunWith(AndroidJUnit4.class)
public class ProfileFigmaTest {
    private User user;
    private boolean fail, delay;
    private int loads, signsOut;
    private final List<RepositoryCallback<User>> pending = new ArrayList<>();
    @Before public void setup() {
        user = new User("patient", UserRole.PATIENT, "김환자", "patient@example.test", "01000000000");
        ProfilePreviewDependencies.lastNavigation = null;
        ProfilePreviewDependencies.auth = (AuthRepository) Proxy.newProxyInstance(AuthRepository.class.getClassLoader(), new Class[]{AuthRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getCurrentUser")) {
                        loads++;
                        RepositoryCallback<User> callback = (RepositoryCallback<User>) args[0];
                        if (delay) pending.add(callback);
                        else if (fail) callback.onError("계정 조회 실패");
                        else callback.onSuccess(user);
                        return null;
                    }
                    if (method.getName().equals("signOut")) { signsOut++; user = null; return null; }
                    throw new AssertionError("허용하지 않은 인증 변경: " + method.getName());
                });
    }
    @After public void cleanup() { ProfilePreviewDependencies.auth = null; ProfilePreviewDependencies.lastNavigation = null; }

    @Test public void patientUsesActualIdentityVersionFiveMenusAndReadableGeometry() {
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(false)) {
            layout(scenario);
            scenario.onActivity(a -> {
                assertEquals("김환자", ((TextView) a.findViewById(R.id.textClientProfileName)).getText().toString());
                assertEquals(5, ((LinearLayout) a.findViewById(R.id.profileMenuContainer)).getChildCount());
                assertTrue(a.findViewById(R.id.buttonProfileGeneral).isSelected());
                assertFalse(a.findViewById(R.id.buttonProfileManager).isSelected());
                assertEquals(1, ((TextView) a.findViewById(R.id.buttonProfileManager)).getLineCount());
                assertEquals(View.GONE, a.findViewById(R.id.profileManagerSection).getVisibility());
                assertEquals(R.id.clientNavProfile, ((BottomNavigationView) a.findViewById(R.id.clientBottomNavigation)).getSelectedItemId());
                assertTrue(text(a.findViewById(R.id.profileMenuVersion)).contains(BuildConfig.VERSION_NAME));
                assertTextFits(a.findViewById(android.R.id.content));
                assertVisible(a.findViewById(R.id.clientBottomNavigation));
                TextView name = a.findViewById(R.id.textClientProfileName);
                assertTrue(ColorUtils.calculateContrast(name.getCurrentTextColor(), a.getColor(R.color.final_brand_primary)) >= 4.5);
            });
            capture("patient");
            onView(withId(R.id.profileMenuVersion)).perform(scrollTo()).check(matches(isCompletelyDisplayed()));
        }
    }
    @Test public void guardianKeepsAccountAndHealthInformationRoute() {
        user = new User("guardian", UserRole.GUARDIAN, "김보호", "", "01000000000");
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(false)) {
            scenario.onActivity(a -> {
                assertEquals(a.getString(R.string.client_profile_value_missing), ((TextView) a.findViewById(R.id.textClientProfileEmail)).getText().toString());
            });
            onView(withId(R.id.buttonProfileEdit)).perform(click());
            onView(withText(R.string.final_profile_health)).inRoot(isDialog()).perform(click());
            assertTarget(HealthInfoActivity.class);
            assertEquals(0, signsOut);
        }
    }
    @Test public void profileEditDoesNotBypassIdentityVerification() {
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(false)) {
            onView(withId(R.id.buttonProfileEdit)).perform(click());
            onView(withId(android.R.id.message)).check(matches(withText(org.hamcrest.Matchers.containsString(
                    InstrumentationRegistry.getInstrumentation().getTargetContext().getString(R.string.final_profile_edit_unavailable)))));
            assertEquals(1, loads);
            assertNull(ProfilePreviewDependencies.lastNavigation);
        }
    }
    @Test public void supportRoutesByAuthenticatedRoleAndDoesNotMutateAccount() {
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(false)) {
            scenario.onActivity(a -> a.findViewById(R.id.profileMenuSupport).performClick());
            assertTarget(ClientSupportActivity.class);
        }
        user = manager();
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(true)) {
            scenario.onActivity(a -> a.findViewById(R.id.profileMenuSupport).performClick());
            assertTarget(ManagerSupportActivity.class);
        }
        assertEquals(0, signsOut);
    }
    @Test public void managerKeepsDocumentsHistoryAndReportRoutesWithoutFakeEarnings() {
        user = manager();
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(true)) {
            layout(scenario);
            scenario.onActivity(a -> {
                assertTrue(a.findViewById(R.id.buttonProfileManager).isSelected());
                assertEquals(6, ((LinearLayout) a.findViewById(R.id.profileMenuContainer)).getChildCount());
                assertFalse(text(a.findViewById(android.R.id.content)).contains("325,000"));
                assertFalse(text(a.findViewById(android.R.id.content)).contains("36.5"));
                assertTextFits(a.findViewById(android.R.id.content));
                a.findViewById(R.id.profileMenuDocuments).performClick(); assertTarget(ManagerDocumentRegistrationActivity.class);
                a.findViewById(R.id.profileMenuMatching).performClick(); assertTarget(ManagerHistoryActivity.class);
                a.findViewById(R.id.profileMenuReport).performClick(); assertTarget(ManagerHistoryActivity.class);
            });
            capture("manager");
            onView(withId(R.id.profileMenuVersion)).perform(scrollTo()).check(matches(isCompletelyDisplayed()));
            capture("manager-bottom");
        }
    }
    @Test public void otherRoleButtonOnlyExplainsAndKeepsSelection() {
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(false)) {
            onView(withId(R.id.buttonProfileManager)).perform(click());
            onView(withText(R.string.permission_guide_confirm)).perform(click());
            scenario.onActivity(a -> {
                assertTrue(a.findViewById(R.id.buttonProfileGeneral).isSelected());
                assertFalse(a.findViewById(R.id.buttonProfileManager).isSelected());
            });
            assertEquals(UserRole.PATIENT, user.getRole());
            assertEquals(0, signsOut);
        }
    }
    @Test public void authFailureClearsIdentityAndRetryRestoresIt() {
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(false)) {
            scenario.moveToState(Lifecycle.State.CREATED);
            fail = true;
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> {
                assertEquals("", ((TextView) a.findViewById(R.id.textClientProfileName)).getText().toString());
                assertEquals(View.GONE, a.findViewById(R.id.clientProfileContentContainer).getVisibility());
                assertFalse(a.findViewById(R.id.buttonProfileSettings).isEnabled());
                assertEquals(View.GONE, a.findViewById(R.id.clientBottomNavigation).getVisibility());
            });
            fail = false;
            onView(withText(R.string.final_profile_retry)).perform(scrollTo(), click());
            onView(withId(R.id.textClientProfileName)).check(matches(withText("김환자")));
        }
    }
    @Test public void latePreviousAccountResponseCannotReopenPrivateContent() {
        delay = true;
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(false)) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            assertEquals(2, pending.size());
            scenario.onActivity(a -> {
                pending.get(1).onError("현재 계정 실패");
                pending.get(0).onSuccess(user);
                assertEquals(View.GONE, a.findViewById(R.id.clientProfileContentContainer).getVisibility());
                assertEquals("", ((TextView) a.findViewById(R.id.textClientProfileName)).getText().toString());
            });
        }
    }
    @Test public void recreationReloadsCurrentIdentityWithoutDuplicatingMenus() {
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(false)) {
            user = new User("guardian", UserRole.GUARDIAN, "새 보호자", "guardian@example.test", "01000000000");
            scenario.recreate();
            scenario.onActivity(a -> {
                assertEquals("새 보호자", ((TextView) a.findViewById(R.id.textClientProfileName)).getText().toString());
                assertEquals(5, ((LinearLayout) a.findViewById(R.id.profileMenuContainer)).getChildCount());
            });
        }
    }
    @Test public void managerRouteRejectsPatientAndGeneralRouteRejectsAdmin() {
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(true)) {
            scenario.onActivity(a -> assertEquals(View.GONE, a.findViewById(R.id.clientProfileContentContainer).getVisibility()));
        }
        user = new User("admin", UserRole.ADMIN, "운영자", "admin@example.test", "01000000000");
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(false)) {
            scenario.onActivity(a -> assertEquals(View.GONE, a.findViewById(R.id.clientProfileContentContainer).getVisibility()));
        }
    }
    @Test public void longIdentityWrapsAndLastMenuRemainsReachable() {
        user = new User("patient", UserRole.PATIENT, "아주 긴 이름의 사용자입니다", "long.patient.email.address.for.layout@example.test", "01000000000");
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(false)) {
            layout(scenario);
            scenario.onActivity(a -> assertTextFits(a.findViewById(android.R.id.content)));
            capture("long-identity");
            onView(withId(R.id.profileMenuVersion)).perform(scrollTo()).check(matches(isCompletelyDisplayed()));
        }
    }
    @Test public void settingsAndSystemNotificationRoutesUseExistingDestinations() {
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(false)) {
            onView(withId(R.id.buttonProfileSettings)).perform(click()); assertTarget(ProfileSettingsActivity.class);
            onView(withId(R.id.buttonProfileNotifications)).perform(click());
            assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, ProfilePreviewDependencies.lastNavigation.getAction());
            assertEquals(BuildConfig.APPLICATION_ID, ProfilePreviewDependencies.lastNavigation.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE));
        }
    }
    @Test public void managerBottomNavigationReturnsToRequestedHomeTab() {
        user = manager();
        for (int tab : new int[]{R.id.navManagerHome, R.id.navManagerCalendar, R.id.navManagerMatching, R.id.navManagerChat}) {
            try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(true)) {
                onView(withId(tab)).perform(click());
                assertTarget(tab == R.id.navManagerChat ? com.example.bodeul.ui.chat.CompanionChatActivity.class : ManagerActivity.class);
                if (tab != R.id.navManagerChat) {
                    Intent expected = ManagerActivity.createTabIntent(context(), tab);
                    for (String key : expected.getExtras().keySet()) {
                        assertEquals(expected.getIntExtra(key, -1), ProfilePreviewDependencies.lastNavigation.getIntExtra(key, -2));
                    }
                    assertTrue((ProfilePreviewDependencies.lastNavigation.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
                }
            }
        }
    }
    @Test public void managerAccountViewShowsActualInformationWithoutEditing() {
        user = manager();
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(true)) {
            onView(withId(R.id.buttonProfileView)).perform(click());
            onView(withId(android.R.id.message)).inRoot(isDialog()).check(matches(withText(
                    org.hamcrest.Matchers.containsString("manager@example.test"))));
            assertEquals(1, loads);
            assertNull(ProfilePreviewDependencies.lastNavigation);
        }
    }
    @Test public void settingsLayoutMenusLogoutAndInsetsAreReadable() {
        try (ActivityScenario<ProfileSettingsPreviewActivity> scenario = settings()) {
            layout(scenario);
            scenario.onActivity(a -> {
                assertEquals(3, ((LinearLayout) a.findViewById(R.id.profileSettingsMenu)).getChildCount());
                assertTextFits(a.findViewById(android.R.id.content));
                assertVisible(a.findViewById(R.id.buttonProfileSettingsSignOut));
                assertSafeBottom(a.findViewById(R.id.profileSettingsRoot), a.findViewById(R.id.buttonProfileSettingsSignOut));
            });
            capture("settings");
            scenario.recreate();
            scenario.onActivity(a -> assertEquals(3, ((LinearLayout) a.findViewById(R.id.profileSettingsMenu)).getChildCount()));
        }
    }
    @Test public void signOutCancellationKeepsSessionAndConfirmationClearsTask() {
        try (ActivityScenario<ProfileSettingsPreviewActivity> scenario = settings()) {
            onView(withId(R.id.buttonProfileSettingsSignOut)).perform(click());
            onView(withText(android.R.string.cancel)).perform(click());
            assertEquals(0, signsOut);
            onView(withId(R.id.buttonProfileSettingsSignOut)).perform(click());
            onView(withId(android.R.id.button1)).perform(click());
            assertEquals(1, signsOut);
            assertTarget(RoleSelectionActivity.class);
            assertTrue((ProfilePreviewDependencies.lastNavigation.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
        }
    }
    @Test public void settingsAuthFailureAndLateResponseKeepActionsHidden() {
        delay = true;
        try (ActivityScenario<ProfileSettingsPreviewActivity> scenario = settings()) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> {
                pending.get(1).onError("조회 실패");
                pending.get(0).onSuccess(user);
                assertEquals(0, ((LinearLayout) a.findViewById(R.id.profileSettingsMenu)).getChildCount());
                assertEquals(View.GONE, a.findViewById(R.id.buttonProfileSettingsSignOut).getVisibility());
            });
        }
    }
    @Test public void faqOpensFromEachRoleWithoutPassingAccountData() {
        for (UserRole role : new UserRole[]{UserRole.PATIENT, UserRole.GUARDIAN, UserRole.MANAGER}) {
            user = new User("profile", role, "테스트 회원", "profile@example.test", "01000000000");
            ProfilePreviewDependencies.lastNavigation = null;
            try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = ActivityScenario.launch(
                    new Intent(context(), ProfileFigmaPreviewActivity.class).putExtra("openFaq", true)
                            .putExtra("manager", role == UserRole.MANAGER))) {
                onView(withId(R.id.profileMenuFaq)).perform(scrollTo(), click());
                onView(withId(R.id.faqTitle)).check(matches(isDisplayed()));
                assertTarget(FaqActivity.class);
                assertNull(ProfilePreviewDependencies.lastNavigation.getExtras());
                if (role == UserRole.GUARDIAN) androidx.test.espresso.Espresso.pressBack();
                else onView(withId(R.id.faqBack)).perform(click());
                onView(withId(R.id.profileMenuFaq)).perform(scrollTo()).check(matches(isDisplayed()));
            }
        }
        assertEquals(0, signsOut);
    }
    @Test public void unconnectedMenusShowHonestMessagesAndDoNotWriteOrNavigate() {
        try (ActivityScenario<ProfileFigmaPreviewActivity> scenario = home(false)) {
            int[] ids = {R.id.profileMenuNotice, R.id.profileMenuTerms};
            int[] messages = {R.string.final_profile_notice_unavailable, R.string.final_profile_terms_unavailable};
            for (int i = 0; i < ids.length; i++) {
                onView(withId(ids[i])).perform(scrollTo(), click());
                onView(withText(messages[i])).inRoot(isDialog()).check(matches(isDisplayed()));
                onView(withText(R.string.permission_guide_confirm)).inRoot(isDialog()).perform(click());
            }
            assertNull(ProfilePreviewDependencies.lastNavigation);
            assertEquals(0, signsOut);
        }
    }
    private User manager() { return new User("manager", UserRole.MANAGER, "이매니저", "manager@example.test", "01000000000"); }
    private ActivityScenario<ProfileFigmaPreviewActivity> home(boolean manager) {
        return ActivityScenario.launch(new Intent(context(), ProfileFigmaPreviewActivity.class).putExtra("manager", manager));
    }
    private ActivityScenario<ProfileSettingsPreviewActivity> settings() { return ActivityScenario.launch(ProfileSettingsPreviewActivity.class); }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static void assertTarget(Class<?> type) {
        assertNotNull(ProfilePreviewDependencies.lastNavigation);
        assertEquals(type.getName(), ProfilePreviewDependencies.lastNavigation.getComponent().getClassName());
    }
    private static void assertVisible(View view) {
        Rect rect = new Rect(); assertTrue(view.getGlobalVisibleRect(rect)); assertEquals(view.getHeight(), rect.height());
    }
    private static void assertSafeBottom(View root, View control) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root); assertNotNull(insets);
        int[] point = new int[2]; control.getLocationOnScreen(point);
        assertTrue(point[1] + control.getHeight() <= root.getHeight() - insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
    }
    private static String text(View view) {
        if (view.getVisibility() != View.VISIBLE) return "";
        StringBuilder out = new StringBuilder(view instanceof TextView ? ((TextView) view).getText() : "");
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) out.append(text(((ViewGroup) view).getChildAt(i)));
        return out.toString();
    }
    private static void assertTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView t = (TextView) view;
            if (t.getLayout() != null) {
                assertTrue("세로 잘림: " + t.getText(), t.getLayout().getHeight() <= t.getHeight() - t.getCompoundPaddingTop() - t.getCompoundPaddingBottom());
                for (int line = 0; line < t.getLineCount(); line++) {
                    assertEquals("말줄임: " + t.getText(), 0, t.getLayout().getEllipsisCount(line));
                    assertTrue("가로 잘림: " + t.getText(), t.getLayout().getLineMax(line) <= t.getWidth() - t.getCompoundPaddingLeft() - t.getCompoundPaddingRight() + 1);
                }
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) assertTextFits(((ViewGroup) view).getChildAt(i));
    }
    private static <T extends android.app.Activity> void layout(ActivityScenario<T> scenario) {
        CountDownLatch done = new CountDownLatch(1);
        scenario.onActivity(a -> a.getWindow().getDecorView().postOnAnimation(() -> a.getWindow().getDecorView().postOnAnimation(done::countDown)));
        try { assertTrue(done.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
    }
    private static void capture(String name) {
        String prefix = InstrumentationRegistry.getArguments().getString("captureProfile");
        if (prefix == null) return;
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // 스크롤 직후의 부분 갱신 프레임 대신 전체 화면이 그려진 뒤 캡처한다.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (android.app.Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
                    .getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                activity.getWindow().getDecorView().invalidate();
            }
        });
        try { InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(250, 5000); }
        catch (java.util.concurrent.TimeoutException error) { throw new AssertionError(error); }
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(context().getFilesDir(), "profile-captures"); directory.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(new File(directory, prefix + "-" + name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
