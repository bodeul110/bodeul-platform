package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.UserRole;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.hamcrest.Matcher;

/** 피그마 화면과 기존 인증 위임을 합성 데이터로 검증하며 외부 계정에 접속하지 않는다. */
@RunWith(AndroidJUnit4.class)
public class LoginFigmaPreviewTest {
    @Test public void initialScreenMatchesFigmaGeometryAndHasNoLegacySummary() throws Exception {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.MANAGER)) {
            onView(withId(R.id.textLoginTitle)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.roleChipGroup)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.textResendVerification)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.inputEmail)).check(matches(withHint(R.string.final_login_email_hint)));
            onView(withId(R.id.buttonSocialApple)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                float density = activity.getResources().getDisplayMetrics().density;
                View content = activity.findViewById(R.id.layoutLoginContent);
                View email = activity.findViewById(R.id.layoutEmail);
                View password = activity.findViewById(R.id.layoutPassword);
                View submit = activity.findViewById(R.id.buttonSubmitAuth);
                assertEquals(15, content.getPaddingLeft() / density, 1);
                assertEquals(10, (top(password) - bottom(email)) / density, 1);
                assertEquals(20, (top(submit) - bottom(password)) / density, 1);
                assertTrue(email.getHeight() / density >= 50);
                assertTrue(submit.getHeight() / density >= 56);
                assertEquals(0, ((TextView) submit).getLetterSpacing(), 0.001f);
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    assertEquals(700, ((TextView) submit).getTypeface().getWeight());
                    assertEquals(500, ((TextView) activity.findViewById(R.id.inputEmail)).getTypeface().getWeight());
                }
                assertEquals(50, activity.findViewById(R.id.buttonSocialGoogle).getWidth() / density, 1);
                assertEquals(0, activity.repository.calls);
            });
            capture("initial");
        }
    }

    @Test public void invalidEmailAndMissingPasswordNeverCallRepository() {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.MANAGER)) {
            onView(withId(R.id.buttonSubmitAuth)).perform(click());
            onView(withId(R.id.textAuthMessage)).check(matches(withText(R.string.error_email_required)));
            onView(withId(R.id.inputEmail)).perform(replaceText("login@example.invalid"), closeSoftKeyboard());
            onView(withId(R.id.buttonSubmitAuth)).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.error_password_required),
                        ((TextView) activity.findViewById(R.id.textAuthMessage)).getText().toString());
                assertEquals(0, activity.repository.calls);
            });
        }
    }

    @Test public void invalidEmailIsRedWithoutMovingLoginControls() throws Exception {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.MANAGER)) {
            onView(withId(R.id.inputEmail)).perform(replaceText("invalid-email"), closeSoftKeyboard());
            int[] originalTop = new int[1];
            scenario.onActivity(activity -> originalTop[0] = top(activity.findViewById(R.id.buttonSubmitAuth)));
            onView(withId(R.id.buttonSubmitAuth)).perform(click());
            onView(withId(R.id.textAuthMessage)).check(matches(withText(R.string.error_email_invalid)));
            scenario.onActivity(activity -> {
                TextView email = activity.findViewById(R.id.inputEmail);
                TextInputLayout layout = activity.findViewById(R.id.layoutEmail);
                assertEquals(activity.getColor(R.color.final_text_error), email.getCurrentTextColor());
                assertContrast("로그인 오류 글자", email.getCurrentTextColor(), layout.getBoxBackgroundColor());
                TextView message = activity.findViewById(R.id.textAuthMessage);
                assertContrast("로그인 오류 안내", message.getCurrentTextColor(), backgroundColor(message));
                assertEquals(originalTop[0], top(activity.findViewById(R.id.buttonSubmitAuth)));
                assertEquals(0, activity.repository.calls);
            });
            capture("invalid-email");
            fillCredentials();
            onView(withId(R.id.buttonSubmitAuth)).perform(click());
            scenario.onActivity(activity -> assertEquals(activity.getColor(R.color.final_text_primary),
                    ((TextView) activity.findViewById(R.id.inputEmail)).getCurrentTextColor()));
        }
    }

    @Test public void emailLoginDisablesDuplicateActionsAndRecoversAfterError() {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.MANAGER)) {
            fillCredentials();
            onView(withId(R.id.buttonSubmitAuth)).perform(scrollTo(), click());
            onView(withId(R.id.buttonSubmitAuth)).check(matches(not(isEnabled())));
            onView(withId(R.id.inputEmail)).check(matches(not(isEnabled())));
            scenario.onActivity(activity -> {
                assertEquals("email", activity.repository.action);
                assertEquals("login@example.invalid", activity.repository.email);
                assertEquals("synthetic-password", activity.repository.password);
                assertEquals(UserRole.MANAGER, activity.repository.role);
                activity.repository.fail();
            });
            onView(withId(R.id.buttonSubmitAuth)).check(matches(isEnabled()));
            onView(withId(R.id.buttonSubmitAuth)).perform(click());
            scenario.onActivity(activity -> {
                activity.repository.succeed();
                assertEquals(UserRole.MANAGER, activity.authenticatedUser.getRole());
            });
        }
    }

    @Test public void generalLoginChoosesGuardianOnlyAtSubmissionAndCanCancel() {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.PATIENT)) {
            fillCredentials();
            onView(withId(R.id.buttonSubmitAuth)).perform(scrollTo(), click());
            assertDialogButtonContrast(android.R.string.cancel);
            onView(withText(android.R.string.cancel)).perform(click());
            scenario.onActivity(activity -> assertEquals(0, activity.repository.calls));
            onView(withId(R.id.buttonSubmitAuth)).perform(click());
            onView(withText(R.string.login_role_guardian)).perform(click());
            scenario.onActivity(activity -> assertEquals(UserRole.GUARDIAN, activity.repository.role));
        }
    }

    @Test public void existingSocialProvidersKeepTheirRoleAndRepositoryCallbacks() {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.MANAGER)) {
            onView(withId(R.id.buttonSocialKakao)).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                assertEquals("kakao", activity.repository.action);
                assertEquals(UserRole.MANAGER, activity.repository.role);
                activity.repository.fail();
            });
            onView(withId(R.id.buttonSocialGoogle)).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                assertEquals("google", activity.repository.action);
                assertEquals(UserRole.MANAGER, activity.repository.role);
                activity.repository.fail();
            });
        }
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.PATIENT)) {
            onView(withId(R.id.buttonSocialKakao)).perform(scrollTo(), click());
            onView(withText(R.string.login_role_patient)).perform(click());
            scenario.onActivity(activity -> assertEquals(UserRole.PATIENT, activity.repository.role));
        }
    }

    @Test public void registrationSurvivesRecreationAndKeepsVerificationFlow() {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.PATIENT)) {
            onView(withId(R.id.textSwitchMode)).perform(scrollTo(), click());
            onView(withId(R.id.chipRoleGuardian)).perform(scrollTo(), click());
            onView(withId(R.id.inputName)).perform(scrollTo(), replaceText("합성 보호자"));
            onView(withId(R.id.inputPhone)).perform(scrollTo(), replaceText("01012345678"), closeSoftKeyboard());
            scenario.recreate();
            onView(withId(R.id.chipRoleGuardian)).check(matches(isChecked()));
            onView(withId(R.id.inputName)).check(matches(withText("합성 보호자")));
            fillCredentials();
            onView(withId(R.id.buttonSubmitAuth)).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                assertEquals("register", activity.repository.action);
                assertEquals(UserRole.GUARDIAN, activity.repository.role);
                assertEquals("합성 보호자", activity.repository.name);
                assertEquals("010-1234-5678", activity.repository.phone);
                activity.repository.succeed();
                assertTrue(activity.repository.signedOut);
            });
            onView(withId(R.id.inputPassword)).check(matches(withText("")));
            onView(withId(R.id.layoutName)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.textResendVerification)).perform(scrollTo()).check(matches(not(isEnabled())));
        }
    }

    @Test public void recoveryKeepsResetAndResendCooldownIncludingRecreation() {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.MANAGER)) {
            fillCredentials();
            openRecovery(0);
            scenario.onActivity(activity -> assertEquals("reset", activity.repository.action));
            openRecovery(1);
            scenario.onActivity(activity -> assertEquals("resend", activity.repository.action));
            onView(withId(R.id.textResendVerification)).perform(scrollTo()).check(matches(not(isEnabled())));
            openRecovery(1);
            scenario.onActivity(activity -> assertEquals(2, activity.repository.calls));
            scenario.recreate();
            onView(withId(R.id.textResendVerification)).perform(scrollTo()).check(matches(not(isEnabled())));
            openRecovery(1);
            scenario.onActivity(activity -> assertEquals(0, activity.repository.calls));
        }
    }

    @Test public void adminStillHasNoRegistrationOrSocialEntry() {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.ADMIN)) {
            onView(withId(R.id.textSwitchMode)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.layoutSocialButtons)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            fillCredentials();
            onView(withId(R.id.buttonSubmitAuth)).perform(scrollTo(), click());
            scenario.onActivity(activity -> assertEquals(UserRole.ADMIN, activity.repository.role));
        }
    }

    @Test public void appleClearlyExplainsUnavailableProviderAndDoesNotFakeLogin() {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.MANAGER)) {
            onView(withId(R.id.buttonSocialApple)).perform(scrollTo(), click());
            onView(withText(R.string.final_login_apple_unavailable)).check(matches(isDisplayed()));
            assertDialogButtonContrast(android.R.string.ok);
            onView(withText(android.R.string.ok)).perform(click());
            scenario.onActivity(activity -> assertEquals(0, activity.repository.calls));
        }
    }

    @Test public void inputAndSubmissionStayReachableWithKeyboard() throws Exception {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.PATIENT)) {
            onView(withId(R.id.textSwitchMode)).perform(scrollTo(), click());
            onView(withId(R.id.inputPassword)).perform(scrollTo(), click(), waitForKeyboard(), replaceText("synthetic-password"));
            onView(withId(R.id.inputPassword)).check(matches(withText("synthetic-password")));
            scenario.onActivity(activity -> {
                View input = activity.findViewById(R.id.inputPassword);
                Rect visible = new Rect();
                input.getWindowVisibleDisplayFrame(visible);
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(input);
                assertNotNull(insets);
                assertTrue("검증 중 실제 키보드가 열려 있어야 한다.", insets.isVisible(WindowInsetsCompat.Type.ime()));
                assertTrue(bottom(input) <= visible.bottom);
            });
            capture("register-keyboard");
            onView(withId(R.id.buttonSubmitAuth)).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }

    @Test public void essentialTextMeetsContrastWithoutChangingFigmaBackgrounds() {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.PATIENT)) {
            scenario.onActivity(activity -> {
                assertInputContrast(activity, R.id.inputEmail, R.id.layoutEmail);
                assertInputContrast(activity, R.id.inputPassword, R.id.layoutPassword);
                TextView submit = activity.findViewById(R.id.buttonSubmitAuth);
                assertEquals(Color.rgb(1, 161, 255), backgroundColor(submit));
                assertContrast("로그인 버튼", submit.getCurrentTextColor(), backgroundColor(submit));
            });
            onView(withId(R.id.textSwitchMode)).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                assertInputContrast(activity, R.id.inputName, R.id.layoutName);
                assertInputContrast(activity, R.id.inputPhone, R.id.layoutPhone);
                assertInputContrast(activity, R.id.inputEmail, R.id.layoutEmail);
                assertInputContrast(activity, R.id.inputPassword, R.id.layoutPassword);
                TextView submit = activity.findViewById(R.id.buttonSubmitAuth);
                assertContrast("회원가입 버튼", submit.getCurrentTextColor(), backgroundColor(submit));
            });
        }
    }

    @Test public void registrationFieldAndInlineErrorTextMeetContrast() throws Exception {
        try (ActivityScenario<LoginFigmaPreviewActivity> scenario = launch(UserRole.MANAGER)) {
            onView(withId(R.id.textSwitchMode)).perform(scrollTo(), click());
            onView(withId(R.id.inputEmail)).perform(scrollTo(), replaceText("invalid-email"), closeSoftKeyboard());
            onView(withId(R.id.buttonSubmitAuth)).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                TextView input = activity.findViewById(R.id.inputEmail);
                TextInputLayout layout = activity.findViewById(R.id.layoutEmail);
                assertEquals(activity.getString(R.string.error_email_invalid), layout.getError());
                assertContrast("회원가입 오류 글자", input.getCurrentTextColor(), layout.getBoxBackgroundColor());
                assertContrast("회원가입 오류 안내", layout.getErrorCurrentTextColors(),
                        activity.getColor(R.color.final_surface));
                assertEquals(0, activity.repository.calls);
            });
            capture("register-error");
        }
    }

    private static void assertInputContrast(LoginFigmaPreviewActivity activity, int inputId, int layoutId) {
        TextView input = activity.findViewById(inputId);
        TextInputLayout layout = activity.findViewById(layoutId);
        int background = ColorUtils.compositeColors(layout.getBoxBackgroundColor(), Color.WHITE);
        assertEquals(Color.rgb(242, 245, 248), background);
        assertContrast("입력 힌트 " + inputId, input.getCurrentHintTextColor(), background);
        assertContrast("입력 본문 " + inputId, input.getCurrentTextColor(), background);
    }

    private static void assertDialogButtonContrast(int textRes) {
        onView(withText(textRes)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .check((view, missing) -> {
                    if (missing != null) { throw missing; }
                    TextView button = (TextView) view;
                    android.util.TypedValue surface = new android.util.TypedValue();
                    assertTrue(button.getContext().getTheme().resolveAttribute(
                            com.google.android.material.R.attr.colorSurface, surface, true));
                    assertContrast("대화상자 " + button.getText(), button.getCurrentTextColor(), surface.data);
                });
    }

    private static int backgroundColor(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            view.getBackground().draw(canvas);
            return bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
        } finally { bitmap.recycle(); }
    }

    private static void assertContrast(String name, int foreground, int background) {
        double ratio = ColorUtils.calculateContrast(ColorUtils.compositeColors(foreground, background), background);
        assertTrue(name + " 대비 부족: " + ratio, ratio >= 4.5);
    }

    private static void openRecovery(int item) {
        onView(withId(R.id.textForgotPassword)).perform(scrollTo(), click());
        onView(withText(item == 0 ? R.string.forgot_password : R.string.resend_verification_email))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
    }
    private static void fillCredentials() {
        onView(withId(R.id.inputEmail)).perform(scrollTo(), replaceText("login@example.invalid"));
        onView(withId(R.id.inputPassword)).perform(scrollTo(), replaceText("synthetic-password"), closeSoftKeyboard());
    }
    private static ActivityScenario<LoginFigmaPreviewActivity> launch(UserRole role) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return ActivityScenario.launch(new Intent(context, LoginFigmaPreviewActivity.class)
                .putExtra("role_hint", role.name()));
    }
    private static int top(View view) {
        int[] position = new int[2];
        view.getLocationOnScreen(position);
        return position[1];
    }
    private static int bottom(View view) { return top(view) + view.getHeight(); }
    private static ViewAction waitForKeyboard() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isDisplayed(); }
            @Override public String getDescription() { return "실제 IME inset이 적용될 때까지 대기"; }
            @Override public void perform(UiController controller, View view) {
                long deadline = SystemClock.uptimeMillis() + 5_000;
                do {
                    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(view);
                    if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) {
                        controller.loopMainThreadUntilIdle();
                        return;
                    }
                    controller.loopMainThreadForAtLeast(50);
                } while (SystemClock.uptimeMillis() < deadline);
                fail("키보드가 5초 안에 열리지 않았습니다.");
            }
        };
    }
    private static void capture(String name) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(100, 3_000);
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(context.getFilesDir(), "login-figma");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
}
