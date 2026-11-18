package com.example.bodeul.debug;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.not;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.bodeul.R;
import com.example.bodeul.data.*;
import com.example.bodeul.domain.model.*;
import com.example.bodeul.ui.manager.*;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;

/** 실제 등록·관리 화면의 파일 계약과 화면 재생성을 합성 저장소로 검증한다. */
@RunWith(AndroidJUnit4.class)
public class ManagerDocumentsFigmaTest {
    private User user;
    private ManagerHomeProfile profile;
    private boolean failRead, delayRead, failDraft;
    private int uploads, drafts, submissions, previews, reads;
    private ManagerDocumentFileType uploadedType;
    private RepositoryCallback<ManagerDocumentFileMetadata> upload;
    private RepositoryCallback<ManagerHomeProfile> submit;
    private final List<RepositoryCallback<ManagerDocumentOverview>> pendingReads = new ArrayList<>();

    @Before @SuppressWarnings("unchecked") public void setup() {
        user = new User("owner", UserRole.MANAGER, "검증 매니저", "test@example.test", "01000000000");
        profile = profile(ManagerDocumentStatus.NOT_SUBMITTED);
        ManagerDocumentsFigmaPreviewActivity.pickerCount = 0;
        ManagerDocumentsFigmaPreviewActivity.openedPreviews = 0;
        ManagerDocumentsFigmaPreviewActivity.pickerMimeTypes = null;
        ManagerDocumentsFigmaPreviewActivity.lastNavigation = null;
        ManagerDocumentsFigmaPreviewActivity.auth = (AuthRepository) Proxy.newProxyInstance(AuthRepository.class.getClassLoader(), new Class[]{AuthRepository.class}, (p,m,a) -> {
            if (m.getName().equals("getCurrentUser")) ((RepositoryCallback<User>) a[0]).onSuccess(user);
            else throw new AssertionError("인증 변경 금지: " + m.getName());
            return null;
        });
        ManagerDocumentsFigmaPreviewActivity.repository = (ManagerRepository) Proxy.newProxyInstance(ManagerRepository.class.getClassLoader(), new Class[]{ManagerRepository.class}, (p,m,a) -> {
            switch (m.getName()) {
                case "isFirebaseBacked": return false;
                case "getManagerDocumentOverview":
                    reads++;
                    RepositoryCallback<ManagerDocumentOverview> callback = (RepositoryCallback<ManagerDocumentOverview>) a[1];
                    if (delayRead) pendingReads.add(callback);
                    else if (failRead) callback.onError("테스트 조회 실패");
                    else callback.onSuccess(new ManagerDocumentOverview(user, profile));
                    return null;
                case "saveManagerDocumentDraftFileMetadata":
                    drafts++;
                    RepositoryCallback<ManagerHomeProfile> draft = (RepositoryCallback<ManagerHomeProfile>) a[2];
                    if (failDraft) draft.onError("테스트 초안 저장 실패");
                    else {
                        profile = profile(ManagerDocumentStatus.NOT_SUBMITTED, (ManagerDocumentFileMetadata) a[1]);
                        draft.onSuccess(profile);
                    }
                    return null;
                case "saveManagerDocumentSummary":
                    submissions++; submit = (RepositoryCallback<ManagerHomeProfile>) a[2]; return null;
                default: throw new AssertionError("허용하지 않은 서류 호출: " + m.getName());
            }
        });
        ManagerDocumentsFigmaPreviewActivity.uploader = new ManagerDocumentStorageUploader() {
            @Override public boolean isFirebaseBacked() { return false; }
            @Override public void uploadDocument(String id, ManagerDocumentFileType type, Uri uri, RepositoryCallback<ManagerDocumentFileMetadata> callback) {
                assertEquals("owner", id); uploads++; uploadedType = type; upload = callback;
            }
        };
        ManagerDocumentsFigmaPreviewActivity.resolver = (metadata, callback) -> {
            previews++; callback.onSuccess(Uri.parse("content://test/preview.png"));
        };
    }
    @After public void cleanup() {
        ManagerDocumentsFigmaPreviewActivity.auth = null;
        ManagerDocumentsFigmaPreviewActivity.repository = null;
        ManagerDocumentsFigmaPreviewActivity.uploader = null;
        ManagerDocumentsFigmaPreviewActivity.resolver = null;
    }

    @Test public void registrationShowsOnlySupportedQualificationAndDisabledSubmit() {
        try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(false)) {
            geometry(scenario); capture("registration");
            onView(withId(R.id.buttonManagerDocumentRequest)).check(matches(not(isEnabled())));
            onView(withId(R.id.buttonManagerDocumentPrimaryUpload)).perform(scrollTo(), click());
            onView(withText(R.string.manager_document_registration_document_nursing_license)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.manager_document_registration_document_elderly_care_license)).inRoot(isDialog()).check(matches(isDisplayed()));
            androidx.test.espresso.Espresso.pressBack();
            assertEquals(0, uploads); assertEquals(0, submissions);
        }
    }
    @Test public void pickerCancellationAndRecreationKeepTypeWithoutUploadingTwice() {
        try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(false)) {
            chooseType(true);
            assertArrayEquals(new String[]{"image/jpeg", "image/png", "image/webp"}, ManagerDocumentsFigmaPreviewActivity.pickerMimeTypes);
            scenario.recreate();
            scenario.onActivity(a -> a.deliverFile(null));
            assertEquals(0, uploads);
            chooseType(false);
            scenario.recreate();
            scenario.onActivity(a -> { a.deliverFile(Uri.parse("content://test/qualification.png")); a.deliverFile(Uri.parse("content://test/duplicate.png")); });
            assertEquals(1, uploads); assertEquals(ManagerDocumentFileType.LICENSE, uploadedType);
            onView(withId(R.id.buttonManagerDocumentRequest)).check(matches(not(isEnabled())));
            scenario.recreate();
            scenario.onActivity(a -> upload.onSuccess(file(ManagerDocumentFileType.LICENSE)));
            onView(withId(R.id.buttonManagerDocumentRequest)).check(matches(isEnabled()));
            assertEquals(1, drafts); assertEquals(0, submissions);
        }
    }
    @Test public void submissionIsSingleFlightAndCompletionSurvivesRecreation() {
        profile = profile(ManagerDocumentStatus.NOT_SUBMITTED, file(ManagerDocumentFileType.LICENSE));
        try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(false)) {
            onView(withId(R.id.buttonManagerDocumentRequest)).perform(click());
            scenario.onActivity(a -> a.findViewById(R.id.buttonManagerDocumentRequest).performClick());
            scenario.recreate();
            scenario.onActivity(a -> { profile = profile(ManagerDocumentStatus.PENDING_REVIEW, file(ManagerDocumentFileType.LICENSE)); submit.onSuccess(profile); });
            onView(withId(R.id.buttonManagerQualificationCompleteConfirm)).inRoot(isDialog()).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.buttonManagerQualificationCompleteConfirm)).inRoot(isDialog()).check((view, error) -> {
                if (error != null) throw error;
                assertTextFits(view.getRootView());
            });
            scenario.recreate();
            onView(withId(R.id.buttonManagerQualificationCompleteConfirm)).inRoot(isDialog()).perform(scrollTo(), click());
            onView(withId(R.id.buttonManagerDocumentRequest)).check(matches(not(isEnabled())));
            assertEquals(1, submissions);
        }
    }
    @Test public void managementStatesAndDetailsMatchActualStatus() {
        for (ManagerDocumentStatus status : ManagerDocumentStatus.values()) {
            profile = profile(status, file(ManagerDocumentFileType.NURSING_LICENSE));
            try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(true)) {
                int body = switch (status) {
                    case NOT_SUBMITTED -> R.string.manager_document_registration_status_body_ready;
                    case PENDING_REVIEW -> R.string.manager_document_registration_status_body_pending;
                    case APPROVED -> R.string.manager_document_registration_status_body_approved;
                    case REJECTED -> R.string.manager_document_registration_status_body_rejected_ready;
                };
                onView(withId(R.id.textManagerDocumentStatusBody)).check(matches(withText(body)));
                geometry(scenario); capture("list-" + status.name().toLowerCase(Locale.ROOT));
                onView(withId(R.id.cardManagerDocumentSummary)).perform(click());
                onView(withId(R.id.documentDetailTitle)).inRoot(isDialog()).check(matches(withText(R.string.manager_document_registration_document_nursing_license)));
                scenario.recreate();
                onView(withId(R.id.documentDetailPreview)).inRoot(isDialog()).check(matches(isEnabled()));
                onView(withId(R.id.documentDetailPreview)).inRoot(isDialog()).perform(scrollTo(), click());
                layout(scenario); capture("detail-" + status.name().toLowerCase(Locale.ROOT));
                onView(withId(R.id.documentDetailClose)).inRoot(isDialog()).perform(click());
                assertEquals(0, uploads); assertEquals(0, submissions);
            }
        }
        assertEquals(4, previews); assertEquals(4, ManagerDocumentsFigmaPreviewActivity.openedPreviews);
    }
    @Test public void emptyManagementRegistrationAndExistingHistoryStayReachable() {
        try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(true)) {
            geometry(scenario); capture("empty");
            onView(withId(R.id.cardManagerDocumentSummary)).perform(click());
            onView(withText(R.string.manager_document_registration_document_nursing_license)).inRoot(isDialog()).perform(click());
            assertEquals(1, ManagerDocumentsFigmaPreviewActivity.pickerCount);
            scenario.onActivity(a -> a.deliverFile(null));
            onView(withId(R.id.buttonManagerDocumentHistory)).perform(scrollTo(), click());
            assertEquals(ManagerProfileActivity.class.getName(), ManagerDocumentsFigmaPreviewActivity.lastNavigation.getComponent().getClassName());
        }
    }
    @Test public void uploadAndDraftFailureDoNotCreateSubmittedState() {
        try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(false)) {
            chooseType(false);
            scenario.onActivity(a -> { a.deliverFile(Uri.parse("content://test/file.png")); upload.onError("테스트 업로드 실패"); });
            onView(withId(R.id.buttonManagerDocumentRequest)).check(matches(not(isEnabled())));
            chooseType(false); failDraft = true;
            scenario.onActivity(a -> { a.deliverFile(Uri.parse("content://test/file.png")); upload.onSuccess(file(ManagerDocumentFileType.LICENSE)); });
            onView(withId(R.id.buttonManagerDocumentRequest)).check(matches(not(isEnabled())));
            assertEquals(2, uploads); assertEquals(1, drafts); assertEquals(0, submissions);
        }
    }
    @Test public void rejectionAllowsRetryButFailureKeepsReviewNote() {
        profile = profile(ManagerDocumentStatus.REJECTED, file(ManagerDocumentFileType.LICENSE));
        try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(true)) {
            onView(withId(R.id.buttonManagerDocumentRequest)).perform(click());
            scenario.onActivity(a -> submit.onError("테스트 검토 요청 실패"));
            onView(withId(R.id.buttonManagerDocumentRequest)).check(matches(isEnabled()));
            onView(withId(R.id.textManagerDocumentReviewBody)).check(matches(withText(org.hamcrest.Matchers.containsString("다시 확인"))));
            assertEquals(1, submissions);
        }
    }
    @Test public void legacyFileIsReadableButRequiresReplacementBeforeReview() {
        profile = profile(ManagerDocumentStatus.APPROVED, file(ManagerDocumentFileType.HEALTH_CERTIFICATE));
        try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(true)) {
            onView(withId(R.id.textManagerDocumentSummaryStatus)).check(matches(withText(R.string.final_documents_replacement)));
            onView(withId(R.id.buttonManagerDocumentRequest)).check(matches(not(isEnabled())));
            onView(withId(R.id.cardManagerDocumentSummary)).perform(click());
            onView(withId(R.id.documentDetailPreview)).perform(scrollTo(), click());
            assertEquals(1, previews); assertEquals(0, submissions);
        }
    }
    @Test public void invalidFilesOverrideStaleReviewStatusOnBothScreens() {
        for (ManagerDocumentStatus status : Arrays.asList(ManagerDocumentStatus.APPROVED, ManagerDocumentStatus.PENDING_REVIEW)) {
            for (boolean management : new boolean[]{false, true}) {
                assertInvalidFileNotice(management, profile(status), R.string.final_documents_add,
                        R.string.manager_document_registration_status_body_not_submitted);
                assertInvalidFileNotice(management, profile(status, file(ManagerDocumentFileType.HEALTH_CERTIFICATE)),
                        R.string.final_documents_replacement, R.string.manager_document_registration_status_body_replacement);
                assertInvalidFileNotice(management, profile(status, file(ManagerDocumentFileType.NURSING_LICENSE), file(ManagerDocumentFileType.LICENSE)),
                        R.string.final_documents_replacement, R.string.manager_document_registration_status_body_replacement);
            }
        }
    }
    private void assertInvalidFileNotice(boolean management, ManagerHomeProfile invalid, int title, int body) {
        profile = invalid;
        try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(management)) {
            onView(withId(R.id.textManagerDocumentStatusTitle)).check(matches(withText(title)));
            onView(withId(R.id.textManagerDocumentStatusBody)).check(matches(withText(body)));
            onView(withId(R.id.buttonManagerDocumentRequest)).check(matches(not(isEnabled())));
        }
    }
    @Test public void changedAccountWhilePickingCannotUpload() {
        try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(false)) {
            chooseType(false);
            user = new User("another", UserRole.MANAGER, "다른 매니저", "another@example.test", "01000000000");
            scenario.onActivity(a -> a.deliverFile(Uri.parse("content://test/file.png")));
            assertEquals(0, uploads); assertEquals(0, drafts);
        }
    }
    @Test public void nonManagerCannotReadOrUploadDocuments() {
        user = new User("patient", UserRole.PATIENT, "환자", "patient@example.test", "01000000000");
        try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(true)) {
            onView(withId(R.id.managerDocumentStatePanel)).check(matches(isDisplayed()));
            scenario.onActivity(a -> a.onDocumentUploadRequested(ManagerDocumentFileType.LICENSE));
            assertEquals(0, reads); assertEquals(0, uploads);
            capture("permission");
        }
    }
    @Test public void retryAndLateReadNeverExposeStoppedAccount() {
        failRead = true;
        try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(true)) {
            onView(withText(R.string.state_action_retry)).perform(scrollTo(), click());
            assertEquals(2, reads);
            failRead = false; delayRead = true;
            onView(withText(R.string.state_action_retry)).perform(scrollTo(), click());
            scenario.moveToState(Lifecycle.State.CREATED);
            user = new User("patient", UserRole.PATIENT, "환자", "patient@example.test", "01000000000");
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> pendingReads.get(0).onSuccess(new ManagerDocumentOverview(user, profile(ManagerDocumentStatus.APPROVED, file(ManagerDocumentFileType.LICENSE)))));
            onView(withId(R.id.managerDocumentContentContainer)).check(matches(not(isDisplayed())));
            onView(withId(R.id.managerDocumentStatePanel)).check(matches(isDisplayed()));
            assertEquals(0, uploads); assertEquals(0, submissions);
        }
    }
    @Test public void longFilenameAndNoteRemainReadableInDetail() {
        ManagerDocumentFileMetadata file = new ManagerDocumentFileMetadata(ManagerDocumentFileType.LICENSE,
                "manager-documents/owner/license/sample.png", "매우긴자격증파일이름과추가확인내용을포함한서류이미지.png", "image/png", 100, "");
        profile = new ManagerHomeProfile("", "", ManagerDocumentStatus.REJECTED,
                "서류의 전체 내용이 보이도록 다시 확인해주세요.\n자격증 번호와 이름이 잘리지 않아야 합니다.\n주민등록번호는 가려주세요.", 100, 100, "검토 담당자", Collections.singletonList(file));
        try (ActivityScenario<ManagerDocumentsFigmaPreviewActivity> scenario = launch(true)) {
            geometry(scenario);
            onView(withId(R.id.cardManagerDocumentSummary)).perform(click());
            onView(withId(R.id.documentDetailReplace)).perform(scrollTo());
            onView(withId(R.id.documentDetailContent)).check((view, error) -> { if (error != null) throw error; assertTextFits(view); });
            capture("detail-long");
        }
    }
    private void chooseType(boolean nursing) {
        onView(withId(R.id.buttonManagerDocumentPrimaryUpload)).perform(scrollTo(), click());
        onView(withText(nursing ? R.string.manager_document_registration_document_nursing_license : R.string.manager_document_registration_document_elderly_care_license)).inRoot(isDialog()).perform(click());
    }
    private ActivityScenario<ManagerDocumentsFigmaPreviewActivity> launch(boolean management) {
        Intent intent = management ? ManagerDocumentRegistrationActivity.createManagementIntent(context()) : ManagerDocumentRegistrationActivity.createIntent(context());
        return ActivityScenario.launch(intent.setClass(context(), ManagerDocumentsFigmaPreviewActivity.class));
    }
    private static ManagerHomeProfile profile(ManagerDocumentStatus status, ManagerDocumentFileMetadata... files) {
        return new ManagerHomeProfile("자격 증빙", "", status, status == ManagerDocumentStatus.REJECTED ? "서류 내용을 다시 확인해주세요." : "", 100, 100, "검토 담당자", Arrays.asList(files));
    }
    private static ManagerDocumentFileMetadata file(ManagerDocumentFileType type) {
        return new ManagerDocumentFileMetadata(type, "manager-documents/owner/" + type.getStorageKey() + "/sample.png", "자격증.png", "image/png", 100, "");
    }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static void geometry(ActivityScenario<?> scenario) {
        layout(scenario);
        scenario.onActivity(a -> {
            assertTextFits(a.findViewById(R.id.managerDocumentRoot));
            View button = a.findViewById(R.id.buttonManagerDocumentRequest);
            Rect bounds = new Rect(); assertTrue(button.getGlobalVisibleRect(bounds)); assertEquals(button.getHeight(), bounds.height());
            int background = button.isEnabled() ? R.color.final_brand_primary : R.color.final_input_surface;
            assertTrue(ColorUtils.calculateContrast(((TextView) button).getCurrentTextColor(), a.getColor(background)) >= 4.5);
        });
    }
    private static void assertTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getLayout() != null) {
                assertTrue("세로 잘림: " + text.getText(), text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
                for (int i = 0; i < text.getLineCount(); i++) {
                    assertEquals("말줄임: " + text.getText(), 0, text.getLayout().getEllipsisCount(i));
                    assertTrue("가로 잘림: " + text.getText(), text.getLayout().getLineMax(i) <= text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 1);
                }
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) assertTextFits(((ViewGroup) view).getChildAt(i));
    }
    private static void layout(ActivityScenario<?> scenario) {
        CountDownLatch latch = new CountDownLatch(1);
        scenario.onActivity(a -> a.getWindow().getDecorView().postOnAnimation(() -> a.getWindow().getDecorView().postOnAnimation(latch::countDown)));
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
    }
    private static void capture(String name) {
        String prefix = InstrumentationRegistry.getArguments().getString("captureDocuments");
        if (prefix == null) return;
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try { InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(250, 5000); }
        catch (java.util.concurrent.TimeoutException error) { throw new AssertionError(error); }
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File dir = new File(context().getFilesDir(), "documents-captures"); dir.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(new File(dir, prefix + "-" + name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
