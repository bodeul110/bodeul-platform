package com.example.bodeul.debug;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.DeviceNotificationSettingsRepository;
import com.example.bodeul.data.NotificationSettingsRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AccountSignInInfo;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.firebase.CompanionChatPushNotifier;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.manager.ManagerSupportActivity;
import com.example.bodeul.ui.profile.AccountSettingsActivity;
import com.example.bodeul.ui.profile.NotificationSettingsActivity;
import com.example.bodeul.ui.support.ClientSupportActivity;
import com.example.bodeul.util.NotificationPermissionSupport;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;

/** 실제 서버·계정 변경 없이 운영 화면과 저장소 경계, 화면 복귀 및 재생성을 확인한다. */
@RunWith(AndroidJUnit4.class)
public class SettingsDetailFigmaTest {
    private User user;
    private String email, provider;
    private boolean failAuth, failInfo, delayInfo, chatEnabled;
    private int resets, infoReads, chatOpens, appOpens;
    private RepositoryCallback<Void> resetCallback;
    private final List<RepositoryCallback<AccountSignInInfo>> pendingInfo = new ArrayList<>();

    @Before public void setup() {
        user = new User("patient", UserRole.PATIENT, "김환자", "patient@example.test", "01000000000");
        email = "patient@example.test";
        provider = "password";
        chatEnabled = true;
        ProfilePreviewDependencies.lastNavigation = null;
        ProfilePreviewDependencies.rejectNotificationSettings = false;
        ProfilePreviewDependencies.auth = (AuthRepository) Proxy.newProxyInstance(AuthRepository.class.getClassLoader(), new Class[]{AuthRepository.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getCurrentUser":
                            RepositoryCallback<User> auth = (RepositoryCallback<User>) args[0];
                            if (failAuth) auth.onError("인증 조회 실패"); else auth.onSuccess(user);
                            return null;
                        case "getAccountSignInInfo":
                            infoReads++;
                            RepositoryCallback<AccountSignInInfo> info = (RepositoryCallback<AccountSignInInfo>) args[1];
                            if (delayInfo) pendingInfo.add(info);
                            else if (failInfo) info.onError("로그인 정보 조회 실패");
                            else info.onSuccess(account());
                            return null;
                        case "resetCurrentAccountPassword":
                            resets++;
                            resetCallback = (RepositoryCallback<Void>) args[2];
                            assertEquals("확인한 UID만 발송", "patient", args[0]);
                            assertEquals("확인한 이메일만 발송", "patient@example.test", args[1]);
                            if (user == null || !args[0].equals(user.getId()) || !email.equals(args[1]) || !provider.equals("password")) {
                                resetCallback.onError("계정 변경");
                            }
                            return null;
                        default: throw new AssertionError("허용하지 않은 인증 변경: " + method.getName());
                    }
                });
        ProfilePreviewDependencies.notifications = new NotificationSettingsRepository() {
            @Override public boolean isChatEnabled() { return chatEnabled; }
            @Override public Intent chatSettingsIntent() { chatOpens++; return new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS); }
            @Override public Intent appSettingsIntent() { appOpens++; return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS); }
        };
    }
    @After public void cleanup() {
        ProfilePreviewDependencies.auth = null;
        ProfilePreviewDependencies.notifications = null;
        ProfilePreviewDependencies.lastNavigation = null;
        ProfilePreviewDependencies.rejectNotificationSettings = false;
    }
    private AccountSignInInfo account() {
        return new AccountSignInInfo(user.getId(), email, Collections.singleton(provider),
                "custom".equals(provider) ? "KAKAO" : null);
    }
    @Test public void emailLayoutUsesActualAccountAndAllSectionsRemainReachable() {
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            layout(scenario);
            scenario.onActivity(a -> {
                assertEquals(email, text(a.findViewById(R.id.accountSettingsEmail)));
                assertEquals(View.VISIBLE, a.findViewById(R.id.accountPasswordSection).getVisibility());
                assertEquals(3, ((LinearLayout) a.findViewById(R.id.accountProviderRows)).getChildCount());
                assertFalse(text(a.findViewById(android.R.id.content)).contains("hyunaa"));
                assertTextFits(a.findViewById(android.R.id.content));
            });
            capture("account-email-top");
            onView(withId(R.id.buttonAccountWithdraw)).perform(scrollTo()).check(matches(isCompletelyDisplayed()));
            capture("account-email-bottom");
        }
        assertEquals(0, resets);
    }
    @Test public void googleLayoutDoesNotInventEmailPasswordOrExtraLinkState() {
        provider = "google.com";
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            layout(scenario);
            scenario.onActivity(a -> {
                assertEquals(a.getString(R.string.final_settings_provider_google), text(a.findViewById(R.id.accountProviderName)));
                assertEquals(View.GONE, a.findViewById(R.id.accountPasswordSection).getVisibility());
                assertEquals(View.GONE, a.findViewById(R.id.accountLinkSection).getVisibility());
                assertEquals(email, text(a.findViewById(R.id.accountSettingsEmail)));
                assertTextFits(a.findViewById(android.R.id.content));
            });
            capture("account-google");
        }
        assertEquals(0, resets);
    }
    @Test public void customKakaoAndUnknownProviderHaveNoPasswordAction() {
        for (String source : new String[]{"custom", "unrecognized"}) {
            provider = source;
            user = new User("kakao_123", UserRole.GUARDIAN, "김보호", email, "01000000000");
            try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
                scenario.onActivity(a -> {
                    assertEquals(View.GONE, a.findViewById(R.id.accountPasswordSection).getVisibility());
                    assertEquals(a.getString("custom".equals(source) ? R.string.final_settings_provider_kakao : R.string.final_settings_login_unknown),
                            text(a.findViewById(R.id.accountProviderName)));
                });
            }
        }
    }
    @Test public void resetCancellationDoesNotSendEmail() {
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            onView(withId(R.id.buttonAccountPassword)).perform(scrollTo(), click());
            onView(withText(R.string.final_settings_cancel)).inRoot(isDialog()).perform(click());
            assertEquals(0, resets);
        }
    }
    @Test public void resetSendsOnceAndRemainsBusyAcrossRecreation() {
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            confirmReset();
            scenario.onActivity(a -> {
                assertFalse(a.findViewById(R.id.buttonAccountPassword).isEnabled());
                a.findViewById(R.id.buttonAccountPassword).performClick();
            });
            scenario.recreate();
            scenario.onActivity(a -> assertFalse(a.findViewById(R.id.buttonAccountPassword).isEnabled()));
            assertEquals(1, resets);
            scenario.onActivity(a -> resetCallback.onSuccess(null));
            onView(withText(R.string.final_settings_password_sent)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.permission_guide_confirm)).perform(click());
            scenario.onActivity(a -> assertTrue(a.findViewById(R.id.buttonAccountPassword).isEnabled()));
            scenario.recreate();
            assertEquals(1, resets);
        }
    }
    @Test public void resetErrorAllowsExplicitRetryWithoutAutomaticResend() {
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            confirmReset();
            scenario.onActivity(a -> resetCallback.onError("발송 실패"));
            onView(withText(R.string.final_settings_password_error)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.permission_guide_confirm)).perform(click());
            assertEquals(1, resets);
            confirmReset();
            assertEquals(2, resets);
        }
    }
    @Test public void changedEmailAfterConfirmationCannotShowSuccessfulReset() {
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            onView(withId(R.id.buttonAccountPassword)).perform(scrollTo(), click());
            email = "changed@example.test";
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            onView(withText(R.string.final_settings_password_error)).inRoot(isDialog()).check(matches(isDisplayed()));
        }
    }
    @Test public void newAccountDiscardsPreviousResetResult() {
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            confirmReset();
            scenario.moveToState(Lifecycle.State.CREATED);
            user = new User("other", UserRole.GUARDIAN, "다른 계정", "other@example.test", "01000000000");
            email = user.getEmail();
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> {
                resetCallback.onSuccess(null);
                assertEquals(email, text(a.findViewById(R.id.accountSettingsEmail)));
                assertTrue(a.findViewById(R.id.buttonAccountPassword).isEnabled());
            });
            onView(withId(R.id.buttonAccountPassword)).check(matches(isDisplayed()));
        }
    }
    @Test public void lateAccountReadCannotReopenStoppedOrFailedScreen() {
        delayInfo = true;
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            assertEquals(2, pendingInfo.size());
            scenario.onActivity(a -> {
                pendingInfo.get(1).onError("현재 조회 실패");
                pendingInfo.get(0).onSuccess(account());
                assertEquals(0, ((LinearLayout) a.findViewById(R.id.settingsDetailContent)).getChildCount());
            });
        }
    }
    @Test public void accountReadErrorCanRetryAndDoesNotExposeCachedIdentity() {
        failInfo = true;
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            scenario.onActivity(a -> assertEquals(0, ((LinearLayout) a.findViewById(R.id.settingsDetailContent)).getChildCount()));
            failInfo = false;
            onView(withText(R.string.final_profile_retry)).perform(scrollTo(), click());
            onView(withId(R.id.accountSettingsEmail)).check(matches(withText(email)));
            assertEquals(2, infoReads);
        }
    }
    @Test public void mismatchedAccountReadDoesNotRenderAnotherUsersEmail() {
        delayInfo = true;
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            scenario.onActivity(a -> {
                pendingInfo.get(0).onSuccess(new AccountSignInInfo("other", "other@example.test", Collections.singleton("password"), null));
                assertEquals(0, ((LinearLayout) a.findViewById(R.id.settingsDetailContent)).getChildCount());
            });
        }
    }
    @Test public void failedAuthenticationOnReturnClearsOldIdentityAndActions() {
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            scenario.moveToState(Lifecycle.State.CREATED);
            failAuth = true;
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> assertEquals(0, ((LinearLayout) a.findViewById(R.id.settingsDetailContent)).getChildCount()));
        }
        assertEquals(1, infoReads);
        assertEquals(0, resets);
    }
    @Test public void unsupportedConsentAndLinkActionsDoNotPretendToSave() {
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            for (int id : new int[]{R.id.buttonAccountPrivacyConsent, R.id.buttonAccountMarketingConsent}) {
                onView(withId(id)).perform(scrollTo(), click());
                onView(withText(R.string.final_settings_consent_unavailable)).inRoot(isDialog()).check(matches(isDisplayed()));
                onView(withText(R.string.permission_guide_confirm)).perform(click());
            }
            scenario.onActivity(a -> ((LinearLayout) a.findViewById(R.id.accountProviderRows)).getChildAt(0).performClick());
            onView(withText(R.string.final_settings_link_unavailable)).inRoot(isDialog()).check(matches(isDisplayed()));
            assertEquals(0, resets);
            assertNull(ProfilePreviewDependencies.lastNavigation);
        }
    }
    @Test public void withdrawalRoutesToRoleSupportWithoutDeletingAccount() {
        for (UserRole role : new UserRole[]{UserRole.PATIENT, UserRole.GUARDIAN, UserRole.MANAGER}) {
            user = new User("patient", role, "테스트", email, "01000000000");
            try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
                onView(withId(R.id.buttonAccountWithdraw)).perform(scrollTo(), click());
                if (role == UserRole.PATIENT) capture("withdrawal-confirmation");
                onView(withText(R.string.final_settings_cancel)).inRoot(isDialog()).perform(click());
                assertNull(ProfilePreviewDependencies.lastNavigation);
                onView(withId(R.id.buttonAccountWithdraw)).perform(click());
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
                assertTarget(role == UserRole.MANAGER ? ManagerSupportActivity.class : ClientSupportActivity.class);
                assertEquals(0, resets);
            }
            ProfilePreviewDependencies.lastNavigation = null;
        }
    }
    @Test public void invalidRoleAndAuthenticationKeepBothDetailsPrivate() {
        user = new User("admin", UserRole.ADMIN, "운영자", email, "01000000000");
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            scenario.onActivity(a -> assertEquals(0, ((LinearLayout) a.findViewById(R.id.settingsDetailContent)).getChildCount()));
        }
        failAuth = true;
        try (ActivityScenario<NotificationSettingsPreviewActivity> scenario = notificationScreen()) {
            scenario.onActivity(a -> assertEquals(0, ((LinearLayout) a.findViewById(R.id.settingsDetailContent)).getChildCount()));
        }
        assertEquals(0, infoReads);
    }
    @Test public void incompleteProfileUsesExistingCompletionFlow() {
        user = new User("patient", UserRole.PATIENT, "", email, "");
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) { assertTarget(ProfileCompletionActivity.class); }
        assertEquals(0, infoReads);
    }
    @Test public void longEmailWrapsAndFooterStaysAccessible() {
        email = "very.long.account.email.address.for.layout.verification@example.test";
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            layout(scenario);
            scenario.onActivity(a -> assertTextFits(a.findViewById(android.R.id.content)));
            capture("account-long-email");
            onView(withId(R.id.buttonAccountWithdraw)).perform(scrollTo()).check(matches(isCompletelyDisplayed()));
        }
    }
    @Test public void notificationLayoutReadsRealStateAndPreservesFiveFigmaCategories() {
        try (ActivityScenario<NotificationSettingsPreviewActivity> scenario = notificationScreen()) {
            layout(scenario);
            scenario.onActivity(a -> {
                LinearLayout rows = a.findViewById(R.id.settingsDetailContent);
                assertEquals(6, rows.getChildCount());
                for (int i = 0; i < 5; i++) assertEquals(a.getString(i == 3 ? R.string.final_settings_notification_on : R.string.final_settings_link_pending),
                        text(rows.getChildAt(i).findViewById(R.id.notificationSettingState)));
                assertTextFits(a.findViewById(android.R.id.content));
                View toggle = rows.getChildAt(3).findViewById(R.id.notificationSettingSwitch);
                assertEquals(48f, toggle.getWidth() / a.getResources().getDisplayMetrics().density, 1f);
                assertEquals(48f, toggle.getHeight() / a.getResources().getDisplayMetrics().density, 1f);
            });
            capture("notifications-top");
            onView(withId(R.id.buttonSystemNotifications)).perform(scrollTo()).check(matches(isCompletelyDisplayed()));
            capture("notifications-bottom");
        }
    }
    @Test public void figmaAssetsHaveVisiblePixelsAndCorrectSlots() {
        try (ActivityScenario<AccountSettingsPreviewActivity> scenario = accountScreen()) {
            layout(scenario);
            scenario.onActivity(a -> {
                float density = a.getResources().getDisplayMetrics().density;
                ImageView back = a.findViewById(R.id.buttonSettingsDetailBack);
                assertEquals(30f, (back.getWidth() - back.getPaddingLeft() - back.getPaddingRight()) / density, 1f);
                assertPainted(back.getDrawable());
                for (Drawable arrow : ((TextView) a.findViewById(R.id.buttonAccountPassword)).getCompoundDrawablesRelative()) {
                    if (arrow != null) { assertEquals(14f, arrow.getIntrinsicWidth() / density, 1f); assertPainted(arrow); }
                }
                assertPointsRight(((TextView) a.findViewById(R.id.buttonAccountPassword)).getCompoundDrawablesRelative()[2]);
                assertPointsRight(((TextView) a.findViewById(R.id.buttonAccountPrivacyConsent)).getCompoundDrawablesRelative()[2]);
                LinearLayout rows = a.findViewById(R.id.accountProviderRows);
                for (int i = 0; i < rows.getChildCount(); i++) {
                    ImageView icon = rows.getChildAt(i).findViewById(R.id.providerRowIcon);
                    assertEquals(30f, icon.getWidth() / density, 1f);
                    assertEquals(30f, icon.getHeight() / density, 1f);
                    assertPainted(icon.getDrawable());
                    if (icon.getBackground() != null) assertPainted(icon.getBackground());
                }
            });
        }
        try (ActivityScenario<NotificationSettingsPreviewActivity> scenario = notificationScreen()) {
            layout(scenario);
            scenario.onActivity(a -> {
                float density = a.getResources().getDisplayMetrics().density;
                LinearLayout rows = a.findViewById(R.id.settingsDetailContent);
                for (int i = 0; i < 5; i++) {
                    ImageView toggle = rows.getChildAt(i).findViewById(R.id.notificationSettingSwitch);
                    assertEquals(40f, toggle.getDrawable().getIntrinsicWidth() / density, 1f);
                    assertEquals(24f, toggle.getDrawable().getIntrinsicHeight() / density, 1f);
                    assertPainted(toggle.getDrawable());
                }
            });
        }
    }
    @Test public void chatShortcutDoesNotOptimisticallyToggleAndRefreshesOnReturn() {
        try (ActivityScenario<NotificationSettingsPreviewActivity> scenario = notificationScreen()) {
            scenario.onActivity(a -> {
                View chat = ((LinearLayout) a.findViewById(R.id.settingsDetailContent)).getChildAt(3);
                chat.findViewById(R.id.notificationSettingSwitch).performClick();
                assertEquals(a.getString(R.string.final_settings_notification_on), text(chat.findViewById(R.id.notificationSettingState)));
            });
            assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, ProfilePreviewDependencies.lastNavigation.getAction());
            assertEquals(1, chatOpens);
            scenario.moveToState(Lifecycle.State.CREATED);
            chatEnabled = false;
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> assertEquals(a.getString(R.string.final_settings_notification_off), text(
                    ((LinearLayout) a.findViewById(R.id.settingsDetailContent)).getChildAt(3).findViewById(R.id.notificationSettingState))));
            scenario.recreate();
            assertEquals(1, chatOpens);
        }
    }
    @Test public void pendingNotificationsNeverOpenSystemSettingsOrStoreOptIn() {
        try (ActivityScenario<NotificationSettingsPreviewActivity> scenario = notificationScreen()) {
            for (int i : new int[]{0, 1, 2, 4}) {
                scenario.onActivity(a -> ((LinearLayout) a.findViewById(R.id.settingsDetailContent))
                        .getChildAt(i).findViewById(R.id.notificationSettingSwitch).performClick());
                onView(withText(R.string.final_settings_notification_pending)).inRoot(isDialog()).check(matches(isDisplayed()));
                onView(withText(R.string.permission_guide_confirm)).perform(click());
            }
            assertEquals(0, chatOpens + appOpens);
            assertNull(ProfilePreviewDependencies.lastNavigation);
        }
    }
    @Test public void existingAppSettingsShortcutAndUnavailableHandlerArePreserved() {
        try (ActivityScenario<NotificationSettingsPreviewActivity> scenario = notificationScreen()) {
            onView(withId(R.id.buttonSystemNotifications)).perform(scrollTo(), click());
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, ProfilePreviewDependencies.lastNavigation.getAction());
            assertEquals(1, appOpens);
            ProfilePreviewDependencies.rejectNotificationSettings = true;
            onView(withId(R.id.buttonSystemNotifications)).perform(click());
            onView(withText(R.string.final_profile_notification_unavailable)).inRoot(isDialog()).check(matches(isDisplayed()));
        }
    }
    @Test public void deviceRepositoryUsesActualPermissionAndExistingChatChannelWithoutOverwritingIt() {
        DeviceNotificationSettingsRepository repository = new DeviceNotificationSettingsRepository(context());
        NotificationManager manager = context().getSystemService(NotificationManager.class);
        NotificationChannel before = Build.VERSION.SDK_INT >= 26 ? manager.getNotificationChannel(CompanionChatPushNotifier.CHANNEL_ID) : null;
        boolean allowed = NotificationPermissionSupport.canPostNotifications(context());
        assertEquals(allowed && (before == null || before.getImportance() != NotificationManager.IMPORTANCE_NONE), repository.isChatEnabled());
        Intent target = repository.chatSettingsIntent();
        assertEquals(context().getPackageName(), target.getStringExtra(Settings.EXTRA_APP_PACKAGE));
        if (Build.VERSION.SDK_INT >= 26 && allowed) {
            assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, target.getAction());
            assertEquals(CompanionChatPushNotifier.CHANNEL_ID, target.getStringExtra(Settings.EXTRA_CHANNEL_ID));
            NotificationChannel after = manager.getNotificationChannel(CompanionChatPushNotifier.CHANNEL_ID);
            assertNotNull(after);
            if (before != null) assertEquals(before.getImportance(), after.getImportance());
        } else assertEquals(repository.appSettingsIntent().getAction(), target.getAction());
    }
    @Test public void settingsMenuRoundTripUsesAuthenticatedDetailsWithoutIdentityExtras() {
        try (ActivityScenario<ProfileSettingsPreviewActivity> scenario = ActivityScenario.launch(
                new Intent(context(), ProfileSettingsPreviewActivity.class).putExtra("openDetails", true))) {
            onView(withId(R.id.profileMenuAccount)).perform(scrollTo(), click());
            onView(withId(R.id.accountSettingsEmail)).check(matches(withText(email)));
            assertTarget(AccountSettingsActivity.class);
            assertNull(ProfilePreviewDependencies.lastNavigation.getExtras());
            onView(withId(R.id.buttonSettingsDetailBack)).perform(click());
            onView(withId(R.id.profileMenuNotifications)).perform(scrollTo(), click());
            assertTarget(NotificationSettingsActivity.class);
            onView(withText(R.string.final_settings_notifications_title)).check(matches(isDisplayed()));
            androidx.test.espresso.Espresso.pressBack();
            onView(withId(R.id.profileMenuNotifications)).check(matches(isDisplayed()));
        }
    }

    private void confirmReset() {
        onView(withId(R.id.buttonAccountPassword)).perform(scrollTo(), click());
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
    }
    private ActivityScenario<AccountSettingsPreviewActivity> accountScreen() { return ActivityScenario.launch(AccountSettingsPreviewActivity.class); }
    private ActivityScenario<NotificationSettingsPreviewActivity> notificationScreen() { return ActivityScenario.launch(NotificationSettingsPreviewActivity.class); }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static void assertTarget(Class<?> type) {
        assertNotNull(ProfilePreviewDependencies.lastNavigation);
        assertEquals(type.getName(), ProfilePreviewDependencies.lastNavigation.getComponent().getClassName());
    }
    private static String text(View view) {
        if (view.getVisibility() != View.VISIBLE) return "";
        StringBuilder out = new StringBuilder(view instanceof TextView ? ((TextView) view).getText() : "");
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) out.append(text(((ViewGroup) view).getChildAt(i)));
        return out.toString();
    }
    private static void assertPainted(Drawable drawable) {
        assertNotNull(drawable);
        assertTrue(drawable.getIntrinsicWidth() > 0 && drawable.getIntrinsicHeight() > 0);
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Rect bounds = new Rect(drawable.getBounds());
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(new Canvas(bitmap));
        drawable.setBounds(bounds);
        int painted = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            if ((bitmap.getPixel(x, y) >>> 24) != 0) painted++;
        }
        bitmap.recycle();
        assertTrue("원본 자산이 비어 있음", painted > 0);
    }
    private static void assertPointsRight(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Rect bounds = new Rect(drawable.getBounds());
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(new Canvas(bitmap));
        drawable.setBounds(bounds);
        int middleCount = 0, edgeCount = 0;
        float middleX = 0, edgeX = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            if ((bitmap.getPixel(x, y) >>> 24) <= 128) continue;
            if (y > bitmap.getHeight() / 3 && y < bitmap.getHeight() * 2 / 3) { middleX += x; middleCount++; }
            else { edgeX += x; edgeCount++; }
        }
        bitmap.recycle();
        assertTrue("이동 화살표의 방향이 오른쪽이어야 함", middleCount > 0 && edgeCount > 0 && middleX / middleCount > edgeX / edgeCount);
    }
    private static void assertTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getLayout() != null) {
                assertTrue("세로 잘림: " + text.getText(), text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
                for (int line = 0; line < text.getLineCount(); line++) {
                    assertEquals("말줄임: " + text.getText(), 0, text.getLayout().getEllipsisCount(line));
                    assertTrue("가로 잘림: " + text.getText(), text.getLayout().getLineMax(line) <= text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 1);
                }
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) assertTextFits(((ViewGroup) view).getChildAt(i));
    }
    private static <T extends android.app.Activity> void layout(ActivityScenario<T> scenario) {
        CountDownLatch done = new CountDownLatch(1);
        scenario.onActivity(a -> a.getWindow().getDecorView().postOnAnimation(() -> a.getWindow().getDecorView().postOnAnimation(done::countDown)));
        try { assertTrue(done.await(5, TimeUnit.SECONDS)); } catch (InterruptedException error) { throw new AssertionError(error); }
    }
    private static void capture(String name) {
        String prefix = InstrumentationRegistry.getArguments().getString("captureSettings");
        if (prefix == null) return;
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try { InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(250, 5000); }
        catch (java.util.concurrent.TimeoutException error) { throw new AssertionError(error); }
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(context().getFilesDir(), "settings-captures"); directory.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(new File(directory, prefix + "-" + name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
