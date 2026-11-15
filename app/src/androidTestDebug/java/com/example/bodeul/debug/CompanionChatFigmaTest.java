package com.example.bodeul.debug;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;
import com.example.bodeul.data.*;
import com.example.bodeul.data.realtime.CompanionRealtimeSubscriber;
import com.example.bodeul.domain.model.*;
import com.example.bodeul.firebase.CompanionChatPushContract;
import com.example.bodeul.ui.chat.*;

import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CompanionChatFigmaTest {
    private User patient, manager, guardian, user;
    private AppointmentRequest request;
    private CompanionSession session;
    private boolean failLoad, delayLoad, failSend, delaySend;
    private int reads, marked, sends, uploads, subscribed, stops;
    private String sentBody, sentRequest, sentSession, markedRequest, markedSession;
    private List<CompanionChatAttachment> sentAttachments;
    private final List<RepositoryCallback<AppointmentRequestDetail>> pendingLoads = new ArrayList<>();
    private Runnable completeSend;
    private Runnable changed;
    private static final long TIME = 1794718800000L;

    @Before public void setup() {
        patient = new User("patient", UserRole.PATIENT, "김현숙", "", "01000000000");
        manager = new User("manager", UserRole.MANAGER, "이아연", "", "01000000000");
        guardian = new User("guardian", UserRole.GUARDIAN, "김보호", "", "01000000000");
        user = patient;
        request = new AppointmentRequest("request", "patient", "guardian", "서울대학교병원", "신경과",
                "2026-11-15 10:30", "1층 입구", "", AppointmentStatus.IN_PROGRESS, "manager");
        session = new CompanionSession("session", "request", "manager", 1, SessionStatus.MEETING, "", "", "", "", "", false);
        session.addChatMessage(new CompanionChatMessage(UserRole.MANAGER, "안녕하세요. 오늘 동행을 담당할 매니저입니다.", TIME));
        session.addChatMessage(new CompanionChatMessage(UserRole.PATIENT, "네 안녕하세요.", TIME + 60000));
        session.markChatRead(UserRole.MANAGER, TIME + 90000);
        CompanionChatPreviewActivity.auth = proxy(AuthRepository.class, (m, a) -> {
            if (!m.equals("getCurrentUser")) throw new AssertionError(m);
            RepositoryCallback<User> callback = callback(a);
            if (user == null) callback.onError("로그인 필요"); else callback.onSuccess(user);
            return null;
        });
        CompanionChatPreviewActivity.bookings = proxy(BookingRepository.class, (m, a) -> {
            if (m.equals("isFirebaseBacked")) return false;
            if (m.equals("getMyAppointmentRequests")) { CompanionChatFigmaTest.<List<AppointmentRequest>>callback(a).onSuccess(List.of(request)); return null; }
            if (m.equals("getAppointmentRequestDetail")) {
                reads++;
                RepositoryCallback<AppointmentRequestDetail> callback = callback(a);
                if (delayLoad) pendingLoads.add(callback);
                else if (failLoad) callback.onError("조회할 수 없습니다.");
                else callback.onSuccess(detail());
                return null;
            }
            if (m.equals("markCompanionChatRead")) { marked++; return null; }
            if (m.equals("sendCompanionChatMessage")) {
                sends++; sentRequest = (String) a[1]; sentBody = (String) a[2];
                sentAttachments = (List<CompanionChatAttachment>) a[3];
                RepositoryCallback<AppointmentRequestDetail> callback = callback(a);
                User sender = (User) a[0];
                completeSend = () -> {
                    if (failSend) callback.onError("전송 실패");
                    else {
                        session.addChatMessage(new CompanionChatMessage(sender.getRole(), sentBody, TIME + 120000, sentAttachments));
                        callback.onSuccess(detail());
                    }
                };
                if (!delaySend) completeSend.run();
                return null;
            }
            throw new AssertionError(m);
        });
        CompanionChatPreviewActivity.managers = proxy(ManagerRepository.class, (m, a) -> {
            if (m.equals("isFirebaseBacked")) return false;
            if (m.equals("getManagerDashboard")) {
                reads++;
                CompanionChatFigmaTest.<ManagerDashboard>callback(a).onSuccess(dashboard()); return null;
            }
            if (m.equals("markCompanionChatRead")) {
                marked++; markedRequest = (String) a[1]; markedSession = (String) a[2]; return null;
            }
            if (m.equals("sendCompanionChatMessage")) {
                sentRequest = (String) a[1]; sentSession = (String) a[2];
                RepositoryCallback<ManagerDashboard> callback = callback(a);
                if (!ManagerRepository.matchesChatExpectation(session, sentRequest, sentSession)) {
                    callback.onError(ManagerRepository.MESSAGE_STALE_CHAT_SESSION); return null;
                }
                sends++; sentBody = (String) a[3]; sentAttachments = (List<CompanionChatAttachment>) a[4];
                session.addChatMessage(new CompanionChatMessage(UserRole.MANAGER, sentBody, TIME + 120000, sentAttachments));
                CompanionChatFigmaTest.<ManagerDashboard>callback(a).onSuccess(dashboard()); return null;
            }
            throw new AssertionError(m);
        });
        CompanionChatPreviewActivity.uploader = new CompanionChatAttachmentUploader() {
            @Override public boolean isFirebaseBacked() { return false; }
            @Override public void uploadAttachment(String id, Uri uri, RepositoryCallback<CompanionChatAttachment> callback) {
                uploads++;
                assertEquals("session", id);
                callback.onSuccess(new CompanionChatAttachment("test/" + uploads, "안내.pdf", "application/pdf", TIME));
            }
        };
        CompanionChatPreviewActivity.resolver = (attachment, callback) -> callback.onError("테스트 미리보기");
        CompanionChatPreviewActivity.realtime = new CompanionRealtimeSubscriber() {
            @Override public void subscribe(String id, Runnable callback) { subscribed++; changed = callback; }
            @Override public void stop() { stops++; changed = null; }
        };
        CompanionChatPreviewActivity.lastNavigation = null;
    }

    @After public void cleanup() {
        CompanionChatPreviewActivity.auth = null;
        CompanionChatPreviewActivity.bookings = null;
        CompanionChatPreviewActivity.managers = null;
        CompanionChatPreviewActivity.uploader = null;
        CompanionChatPreviewActivity.resolver = null;
        CompanionChatPreviewActivity.realtime = null;
    }

    @Test public void realParticipantsBubblesDatesAndReadReceiptRender() {
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            onView(withId(R.id.textCompanionChatTitle)).check(matches(withText("이아연 매니저")));
            scenario.onActivity(a -> {
                String text = text(a.findViewById(R.id.layoutCompanionChatMessages));
                assertTrue(text.contains("안녕하세요. 오늘 동행"));
                assertTrue(text.contains(a.getString(R.string.companion_chat_read_confirmed)));
                assertFalse(text.contains("동행이 시작되었습니다"));
                assertEquals(1, visibleCount(a.findViewById(R.id.layoutCompanionChatMessages), R.id.textCompanionChatDate));
            });
            assertEquals(1, marked);
            capture(scenario, "normal");
        }
    }

    @Test public void sendTrimsUsesCurrentRequestAndEmptySendDoesNothing() {
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            onView(withId(R.id.buttonCompanionChatSend)).perform(click());
            assertEquals(0, sends);
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.inputCompanionChatMessage)).setText("  도착했습니다.  "));
            onView(withId(R.id.buttonCompanionChatSend)).perform(click());
            assertEquals(1, sends);
            assertEquals("request", sentRequest);
            assertEquals("도착했습니다.", sentBody);
            onView(withId(R.id.inputCompanionChatMessage)).check(matches(withText("")));
            assertTrue(marked >= 2);
        }
    }

    @Test public void failureRetainsDraftAndDuplicateSendIsBlockedAcrossRecreation() {
        delaySend = true;
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.inputCompanionChatMessage)).setText("전송 중 보존"));
            onView(withId(R.id.buttonCompanionChatSend)).perform(click());
            scenario.onActivity(a -> a.findViewById(R.id.buttonCompanionChatSend).performClick());
            scenario.recreate();
            assertEquals(1, sends);
            scenario.onActivity(a -> assertFalse(a.findViewById(R.id.buttonCompanionChatSend).isEnabled()));
            failSend = true;
            scenario.onActivity(a -> completeSend.run());
            onView(withId(R.id.inputCompanionChatMessage)).check(matches(withText("전송 중 보존")));
            failSend = false;
            onView(withId(R.id.buttonCompanionChatSend)).perform(click());
            scenario.onActivity(a -> completeSend.run());
            assertEquals(2, sends);
            onView(withId(R.id.inputCompanionChatMessage)).check(matches(withText("")));
        }
    }

    @Test public void attachmentDraftSurvivesRecreationCancelRemoveAndAttachmentOnlySend() {
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> addPending(a, 3));
            scenario.recreate();
            scenario.onActivity(a -> {
                assertEquals(3, pending(a).size());
                try {
                    Method picked = CompanionChatActivity.class.getDeclaredMethod("handleAttachmentsPicked", List.class);
                    picked.setAccessible(true);
                    picked.invoke(a, Collections.emptyList());
                } catch (Exception e) { throw new AssertionError(e); }
                assertEquals(3, pending(a).size());
                a.findViewById(R.id.buttonCompanionChatAttachmentClear).performClick();
                assertEquals(2, pending(a).size());
            });
            capture(scenario, "attachments");
            onView(withId(R.id.buttonCompanionChatSend)).perform(click());
            assertEquals(2, uploads);
            assertEquals(2, sentAttachments.size());
            assertEquals("", sentBody);
            scenario.onActivity(a -> assertTrue(pending(a).isEmpty()));
        }
    }

    @Test public void localImageThumbnailPendingPreviewAndAuthorizedOpenRemainAvailable() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getCacheDir(), "companion-chat-attachments");
        assertTrue(directory.exists() || directory.mkdirs());
        File file = new File(directory, "chat-test-image.png");
        Bitmap bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(android.graphics.Color.rgb(1, 161, 255));
        try (FileOutputStream stream = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        bitmap.recycle();
        Uri uri = androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        int[] resolved = {0};
        CompanionChatPreviewActivity.resolver = (attachment, callback) -> {
            resolved[0]++;
            callback.onSuccess(uri);
        };
        session.addChatMessage(new CompanionChatMessage(UserRole.MANAGER, "사진 안내", TIME + 120000,
                new CompanionChatAttachment("test/photo", "안내.png", "image/png", TIME)));
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                pending(a).add(new CompanionChatPendingAttachment(uri, "안내.png", "image/png"));
                refreshPending(a);
            });
            boolean[] decoded = {false};
            for (int i = 0; i < 50 && !decoded[0]; i++) {
                scenario.onActivity(a -> decoded[0] = ((android.widget.ImageView) a.findViewById(R.id.imageCompanionChatAttachmentPreview)).getDrawable() != null
                        && ((android.widget.ImageView) a.findViewById(R.id.imageCompanionChatPendingAttachmentPreview)).getDrawable() != null);
                if (!decoded[0]) android.os.SystemClock.sleep(100);
            }
            assertTrue("받은 사진과 첨부 대기 사진이 표시되지 않음", decoded[0]);
            capture(scenario, "image");
            int before = resolved[0];
            scenario.onActivity(a -> a.findViewById(R.id.buttonCompanionChatAttachmentOpen).performClick());
            assertEquals(before + 1, resolved[0]);
            delaySend = true;
            onView(withId(R.id.buttonCompanionChatSend)).perform(click());
            scenario.onActivity(a -> assertFalse(a.findViewById(R.id.buttonCompanionChatAttachmentClear).isEnabled()));
            failSend = true;
            scenario.onActivity(a -> completeSend.run());
            scenario.onActivity(a -> {
                assertEquals(1, pending(a).size());
                assertTrue(a.findViewById(R.id.buttonCompanionChatAttachmentClear).isEnabled());
            });
        } finally {
            assertTrue(file.delete());
        }
    }

    @Test public void realtimeRefreshIgnoresOlderResponseAndStopsInBackground() {
        session.setRealtimeSessionId("realtime-session");
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            assertEquals(1, subscribed);
            delayLoad = true;
            scenario.onActivity(a -> { changed.run(); changed.run(); });
            assertEquals(2, pendingLoads.size());
            scenario.onActivity(a -> {
                pendingLoads.get(1).onSuccess(detail());
                pendingLoads.get(0).onError("오래된 오류");
                assertEquals(View.VISIBLE, a.findViewById(R.id.companionChatContentContainer).getVisibility());
            });
            Runnable lateUpdate = changed;
            scenario.moveToState(Lifecycle.State.CREATED);
            int before = reads;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(lateUpdate);
            assertEquals(before, reads);
        }
    }

    @Test public void managerHasOwnSendPathAndCannotSilentlySwitchAssignments() {
        user = manager;
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            onView(withId(R.id.textCompanionChatTitle)).check(matches(withText("김현숙 환자")));
            scenario.onActivity(a -> ((EditText) a.findViewById(R.id.inputCompanionChatMessage)).setText("담당 안내"));
            onView(withId(R.id.buttonCompanionChatSend)).perform(click());
            assertEquals(1, sends);
            assertEquals("request", sentRequest);
            assertEquals("session", sentSession);
            assertEquals("request", markedRequest);
            assertEquals("session", markedSession);
            onView(withId(R.id.buttonCompanionChatProgress)).perform(click());
            assertTrue(CompanionChatPreviewActivity.lastNavigation.getComponent().getClassName().endsWith("ManagerGuideActivity"));
            scenario.moveToState(Lifecycle.State.CREATED);
            request = new AppointmentRequest("new-request", "patient", "guardian", "다른 병원", "신경과", "", "", "", AppointmentStatus.IN_PROGRESS, "manager");
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> assertEquals(View.GONE, a.findViewById(R.id.companionChatContentContainer).getVisibility()));
        }
    }

    @Test public void uploadAndManualRefreshSendOnceAfterRecreation() {
        List<RepositoryCallback<CompanionChatAttachment>> completions = delayUploads();
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                addPending(a, 1);
                ((EditText) a.findViewById(R.id.inputCompanionChatMessage)).setText("첨부 안내");
                a.findViewById(R.id.buttonCompanionChatSend).performClick();
            });
            int before = reads;
            delayLoad = true;
            onView(withId(R.id.buttonCompanionChatMore)).perform(click());
            onView(withText(R.string.final_chat_refresh)).perform(click());
            scenario.recreate();
            assertEquals(before, reads);
            assertTrue(pendingLoads.isEmpty());
            scenario.onActivity(a -> {
                assertFalse(a.findViewById(R.id.buttonCompanionChatSend).isEnabled());
                completeUpload(completions.get(0));
            });
            assertEquals(1, sends);
            assertEquals("request", sentRequest);
            assertEquals("첨부 안내", sentBody);
            assertEquals(1, sentAttachments.size());
            assertEquals(before + 1, reads);
            scenario.onActivity(a -> pendingLoads.get(0).onSuccess(detail()));
            onView(withId(R.id.inputCompanionChatMessage)).check(matches(withText("")));
            scenario.onActivity(a -> assertTrue(pending(a).isEmpty()));
        }
    }

    @Test public void uploadAndRoomPushCoalesceWithoutCancelingSend() {
        List<RepositoryCallback<CompanionChatAttachment>> completions = delayUploads();
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                addPending(a, 1);
                a.findViewById(R.id.buttonCompanionChatSend).performClick();
            });
            int before = reads;
            sendPush("request");
            assertEquals(before, reads);
            scenario.onActivity(a -> completeUpload(completions.get(0)));
            assertEquals(1, sends);
            assertEquals(before + 1, reads);
        }
    }

    @Test public void staleRefreshCannotClearIdentityDuringUpload() {
        session.setRealtimeSessionId("realtime-session");
        List<RepositoryCallback<CompanionChatAttachment>> completions = delayUploads();
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            delayLoad = true;
            scenario.onActivity(a -> {
                changed.run();
                addPending(a, 1);
                a.findViewById(R.id.buttonCompanionChatSend).performClick();
                pendingLoads.get(0).onError("전송 전에 시작했던 조회 실패");
                completeUpload(completions.get(0));
            });
            assertEquals(1, sends);
            assertEquals(2, pendingLoads.size());
            scenario.onActivity(a -> pendingLoads.get(1).onSuccess(detail()));
            scenario.onActivity(a -> assertTrue(pending(a).isEmpty()));
        }
    }

    @Test public void failedUploadKeepsDraftAndReportsFailureAfterDeferredRefresh() {
        List<RepositoryCallback<CompanionChatAttachment>> completions = delayUploads();
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            CompanionChatViewModel[] model = {null};
            List<String> errors = new ArrayList<>();
            androidx.lifecycle.Observer<String> observer = message -> { if (message != null) errors.add(message); };
            scenario.onActivity(a -> {
                model[0] = new androidx.lifecycle.ViewModelProvider(a).get(CompanionChatViewModel.class);
                model[0].getToastMessage().removeObservers(a);
                model[0].getToastMessage().observeForever(observer);
                addPending(a, 1);
                ((EditText) a.findViewById(R.id.inputCompanionChatMessage)).setText("다시 보낼 안내");
                a.findViewById(R.id.buttonCompanionChatSend).performClick();
                model[0].reload();
                completions.get(0).onError("첨부 업로드 실패");
            });
            assertEquals(0, sends);
            assertTrue(errors.contains("첨부 업로드 실패"));
            onView(withId(R.id.inputCompanionChatMessage)).check(matches(withText("다시 보낼 안내")));
            scenario.onActivity(a -> {
                assertEquals(1, pending(a).size());
                assertTrue(a.findViewById(R.id.buttonCompanionChatSend).isEnabled());
                model[0].getToastMessage().removeObserver(observer);
            });
        }
    }

    @Test public void managerReassignmentDuringUploadNeverWritesToNewSession() {
        user = manager;
        List<RepositoryCallback<CompanionChatAttachment>> completions = delayUploads();
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                addPending(a, 1);
                ((EditText) a.findViewById(R.id.inputCompanionChatMessage)).setText("이전 동행 안내");
                a.findViewById(R.id.buttonCompanionChatSend).performClick();
                session = new CompanionSession("new-session", "new-request", "manager", 1,
                        SessionStatus.MEETING, "", "", "", "", "", false);
                request = new AppointmentRequest("new-request", "patient", "guardian", "다른 병원", "신경과",
                        "", "", "", AppointmentStatus.IN_PROGRESS, "manager");
                completeUpload(completions.get(0));
                assertEquals(View.GONE, a.findViewById(R.id.companionChatContentContainer).getVisibility());
                assertEquals(1, pending(a).size());
                assertEquals("이전 동행 안내", ((EditText) a.findViewById(R.id.inputCompanionChatMessage)).getText().toString());
            });
            assertEquals("request", sentRequest);
            assertEquals("session", sentSession);
            assertEquals(0, sends);
            assertTrue(session.getChatMessages().isEmpty());
            assertEquals("session", markedSession);
        }
    }

    private List<RepositoryCallback<CompanionChatAttachment>> delayUploads() {
        List<RepositoryCallback<CompanionChatAttachment>> completions = new ArrayList<>();
        CompanionChatPreviewActivity.uploader = new CompanionChatAttachmentUploader() {
            @Override public boolean isFirebaseBacked() { return false; }
            @Override public void uploadAttachment(String id, Uri uri, RepositoryCallback<CompanionChatAttachment> callback) {
                assertEquals("session", id);
                uploads++;
                completions.add(callback);
            }
        };
        return completions;
    }

    @Test public void accountChangeDuringUploadDoesNotSendPreviousDraft() {
        List<RepositoryCallback<CompanionChatAttachment>> completions = delayUploads();
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                addPending(a, 1);
                ((EditText) a.findViewById(R.id.inputCompanionChatMessage)).setText("이전 계정 초안");
                a.findViewById(R.id.buttonCompanionChatSend).performClick();
            });
            scenario.moveToState(Lifecycle.State.CREATED);
            user = guardian;
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> completeUpload(completions.get(0)));
            assertEquals(0, sends);
            onView(withId(R.id.inputCompanionChatMessage)).check(matches(withText("")));
            scenario.onActivity(a -> assertTrue(pending(a).isEmpty()));
        }
    }

    private void completeUpload(RepositoryCallback<CompanionChatAttachment> callback) {
        callback.onSuccess(new CompanionChatAttachment("test/upload", "안내.pdf", "application/pdf", TIME));
    }

    @Test public void guardianUsesReadReceiptAndNoPrivateRealtimeSubscription() {
        user = guardian;
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            assertEquals(1, marked);
            assertEquals(0, subscribed);
            int before = stops;
            scenario.moveToState(Lifecycle.State.CREATED);
            assertTrue(stops > before);
            scenario.moveToState(Lifecycle.State.RESUMED);
            assertEquals(2, reads);
        }
    }

    @Test public void lateResponseIsIgnoredAfterStopAndDifferentAccountClearsDraft() {
        delayLoad = true;
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> {
                pendingLoads.get(0).onSuccess(detail());
                assertEquals(View.GONE, a.findViewById(R.id.companionChatContentContainer).getVisibility());
                pendingLoads.get(1).onSuccess(detail());
                ((EditText) a.findViewById(R.id.inputCompanionChatMessage)).setText("개인 초안");
                addPending(a, 1);
            });
            delayLoad = false;
            scenario.moveToState(Lifecycle.State.CREATED);
            user = guardian;
            scenario.moveToState(Lifecycle.State.RESUMED);
            onView(withId(R.id.inputCompanionChatMessage)).check(matches(withText("")));
            scenario.onActivity(a -> assertTrue(pending(a).isEmpty()));
            scenario.moveToState(Lifecycle.State.CREATED);
            user = null;
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> {
                assertEquals(View.GONE, a.findViewById(R.id.companionChatContentContainer).getVisibility());
                assertEquals("안심 채팅", ((TextView) a.findViewById(R.id.textCompanionChatTitle)).getText().toString());
            });
        }
    }

    @Test public void loadErrorRetryNullSessionAndBackAreUsable() {
        failLoad = true;
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            failLoad = false;
            capture(scenario, "error");
            boolean[] visible = {false};
            for (int i = 0; i < 8; i++) {
                scenario.onActivity(a -> {
                    View button = a.findViewById(R.id.buttonStatePanelPrimary);
                    Rect bounds = new Rect();
                    visible[0] = button.getGlobalVisibleRect(bounds) && bounds.height() == button.getHeight();
                });
                if (visible[0]) break;
                onView(withId(R.id.scrollCompanionChatState)).perform(androidx.test.espresso.action.ViewActions.swipeUp());
            }
            capture(scenario, "error-action");
            assertTrue("재시도 버튼을 스크롤해 표시할 수 없음", visible[0]);
            onView(withText(R.string.state_action_retry)).perform(click());
            onView(withId(R.id.textCompanionChatTitle)).check(matches(withText("이아연 매니저")));
            scenario.moveToState(Lifecycle.State.CREATED);
            session = null;
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> assertEquals(View.GONE, a.findViewById(R.id.companionChatContentContainer).getVisibility()));
            onView(withId(R.id.buttonBackCompanionChat)).perform(click());
            assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
        }
    }

    @Test public void closedSessionKeepsHistoryAndBlocksWrites() {
        session = new CompanionSession("session", "request", "manager", 13, SessionStatus.COMPLETED, "", "", "", "", "", true);
        session.addChatMessage(new CompanionChatMessage(UserRole.MANAGER, "마지막 안내", TIME));
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                assertEquals(View.GONE, a.findViewById(R.id.layoutCompanionChatInput).getVisibility());
                assertTrue(text(a.findViewById(R.id.layoutCompanionChatMessages)).contains("마지막 안내"));
                a.findViewById(R.id.buttonCompanionChatSend).performClick();
            });
            assertEquals(0, sends);
            capture(scenario, "closed");
        }
    }

    @Test public void detailsAndProgressKeepRequestAndMoreHasSessionInformation() {
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            onView(withId(R.id.buttonCompanionChatDetails)).perform(click());
            assertEquals("request", CompanionChatPreviewActivity.lastNavigation.getStringExtra("requestId"));
            onView(withId(R.id.buttonCompanionChatProgress)).perform(click());
            assertEquals("request", CompanionChatPreviewActivity.lastNavigation.getStringExtra("requestId"));
            onView(withId(R.id.buttonCompanionChatMore)).perform(click());
            onView(withText(R.string.final_chat_info)).perform(click());
            onView(withText(android.R.string.ok)).perform(click());
        }
    }

    @Test public void pushOnlyRefreshesMatchingRoomAndClearsContentOnRevocation() {
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            int before = reads;
            sendPush("other");
            assertEquals(before, reads);
            failLoad = true;
            sendPush("request");
            assertEquals(before + 1, reads);
            scenario.onActivity(a -> {
                assertEquals(View.GONE, a.findViewById(R.id.companionChatContentContainer).getVisibility());
                assertFalse(text(a.findViewById(android.R.id.content)).contains("비공개 알림 본문"));
            });
        }
    }

    @Test public void longMessagesFilesAndComposerFitFontScaleAndIme() {
        manager = new User("manager", UserRole.MANAGER, "이름이아주긴담당매니저", "", "01000000000");
        session.addChatMessage(new CompanionChatMessage(UserRole.MANAGER,
                String.join("", Collections.nCopies(12, "긴 대화도 줄바꿈되어 끝까지 읽을 수 있어야 합니다. ")), TIME + 86400000));
        session.addChatMessage(new CompanionChatMessage(UserRole.PATIENT, "첨부 안내", TIME + 86460000,
                new CompanionChatAttachment("test/report", "글자가매우긴첨부문서이름입니다.pdf", "application/pdf", TIME)));
        try (ActivityScenario<CompanionChatPreviewActivity> scenario = launch()) {
            awaitLayout(scenario);
            capture(scenario, "long-before-scroll");
            scenario.onActivity(a -> {
                assertTextFits(a.findViewById(R.id.companionChatRoot));
                assertComposerVisible(a);
                ScrollView scroll = a.findViewById(R.id.scrollCompanionChat);
                scroll.fullScroll(View.FOCUS_DOWN);
            });
            capture(scenario, "long");
            onView(withId(R.id.inputCompanionChatMessage)).perform(click());
            waitForKeyboard(scenario);
            scenario.onActivity(CompanionChatFigmaTest::assertComposerVisible);
            capture(scenario, "keyboard");
            androidx.test.espresso.Espresso.closeSoftKeyboard();
        }
    }

    private ActivityScenario<CompanionChatPreviewActivity> launch() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return ActivityScenario.launch(new Intent(context, CompanionChatPreviewActivity.class).putExtra("requestId", "request"));
    }
    private AppointmentRequestDetail detail() { return new AppointmentRequestDetail(request, patient, guardian, manager, session, null, null); }
    private ManagerDashboard dashboard() { return new ManagerDashboard(manager, patient, guardian, request, session, null, null); }
    @SuppressWarnings("unchecked") private static <T> RepositoryCallback<T> callback(Object[] args) { return (RepositoryCallback<T>) args[args.length - 1]; }
    interface Call { Object invoke(String method, Object[] args); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> call.invoke(m.getName(), a));
    }
    @SuppressWarnings("unchecked") private static List<CompanionChatPendingAttachment> pending(CompanionChatActivity a) {
        try { Field f = CompanionChatActivity.class.getDeclaredField("pendingAttachments"); f.setAccessible(true); return (List<CompanionChatPendingAttachment>) f.get(a); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static void addPending(CompanionChatActivity a, int count) {
        for (int i = 0; i < count; i++) pending(a).add(new CompanionChatPendingAttachment(
                Uri.parse("content://test/" + i), "안내-" + i + ".pdf", "application/pdf"));
        refreshPending(a);
    }
    private static void refreshPending(CompanionChatActivity a) {
        try { Method m = CompanionChatActivity.class.getDeclaredMethod("refreshPendingAttachmentUi"); m.setAccessible(true); m.invoke(a); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private void sendPush(String requestId) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch delivered = new CountDownLatch(1);
        context.sendOrderedBroadcast(new Intent(CompanionChatPushContract.ACTION_COMPANION_CHAT_UPDATED).setPackage(context.getPackageName())
                .putExtra(CompanionChatPushContract.EXTRA_APPOINTMENT_REQUEST_ID, requestId)
                .putExtra(CompanionChatPushContract.EXTRA_BODY, "비공개 알림 본문"), null,
                new android.content.BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent i) { delivered.countDown(); }
                }, new android.os.Handler(android.os.Looper.getMainLooper()), 0, null, null);
        try { assertTrue(delivered.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { throw new AssertionError(error); }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private static String text(View view) {
        if (view.getVisibility() != View.VISIBLE) return "";
        StringBuilder result = new StringBuilder(view instanceof TextView ? ((TextView) view).getText() : "");
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) result.append(text(((ViewGroup) view).getChildAt(i)));
        return result.toString();
    }
    private static int visibleCount(View view, int id) {
        if (view.getVisibility() != View.VISIBLE) return 0;
        int count = view.getId() == id ? 1 : 0;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) count += visibleCount(((ViewGroup) view).getChildAt(i), id);
        return count;
    }
    private static void assertTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView && !(view instanceof EditText)) {
            TextView t = (TextView) view;
            int[] location = new int[2];
            t.getLocationOnScreen(location);
            android.view.ViewParent parent = t.getParent();
            while (parent instanceof View) {
                View container = (View) parent;
                int[] parentLocation = new int[2];
                container.getLocationOnScreen(parentLocation);
                assertTrue("부모 오른쪽 밖으로 넘침: " + t.getText(), location[0] + t.getWidth() <= parentLocation[0] + container.getWidth() + 1);
                assertTrue("부모 왼쪽 밖으로 넘침: " + t.getText(), location[0] >= parentLocation[0] - 1);
                parent = container.getParent();
            }
            if (t.getLayout() != null) {
                assertTrue("세로 잘림: " + t.getText(), t.getLayout().getHeight() <= t.getHeight() - t.getCompoundPaddingTop() - t.getCompoundPaddingBottom());
                for (int line = 0; line < t.getLayout().getLineCount(); line++) {
                    assertEquals(0, t.getLayout().getEllipsisCount(line));
                    assertTrue("가로 잘림: " + t.getText(), t.getLayout().getLineMax(line) <= t.getWidth() - t.getCompoundPaddingLeft() - t.getCompoundPaddingRight() + 1);
                }
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) assertTextFits(((ViewGroup) view).getChildAt(i));
    }
    private static void assertComposerVisible(CompanionChatPreviewActivity a) {
        View root = a.findViewById(R.id.companionChatRoot), composer = a.findViewById(R.id.layoutCompanionChatInput);
        Rect bounds = new Rect();
        assertTrue(composer.getGlobalVisibleRect(bounds));
        assertTrue(bounds.height() >= composer.getHeight() - 1);
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
        assertNotNull(insets);
        int bottom = Math.max(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
        assertTrue(bounds.bottom <= root.getHeight() - bottom + 1);
    }

    private static void waitForKeyboard(ActivityScenario<CompanionChatPreviewActivity> scenario) {
        boolean[] visible = {false};
        for (int i = 0; i < 30 && !visible[0]; i++) {
            scenario.onActivity(a -> visible[0] = ViewCompat.getRootWindowInsets(a.findViewById(R.id.companionChatRoot))
                    .isVisible(WindowInsetsCompat.Type.ime()));
            if (!visible[0]) android.os.SystemClock.sleep(100);
        }
        assertTrue("키보드가 표시되지 않음", visible[0]);
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(500, 5000);
        } catch (java.util.concurrent.TimeoutException error) { throw new AssertionError(error); }
    }
    private static void awaitLayout(ActivityScenario<CompanionChatPreviewActivity> scenario) {
        CountDownLatch latch = new CountDownLatch(1);
        scenario.onActivity(a -> a.getWindow().getDecorView().postOnAnimation(
                () -> a.getWindow().getDecorView().postOnAnimation(() -> a.getWindow().getDecorView().post(latch::countDown))));
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
    }
    private void capture(ActivityScenario<CompanionChatPreviewActivity> scenario, String name) {
        String prefix = InstrumentationRegistry.getArguments().getString("captureChat");
        if (prefix == null) return;
        awaitLayout(scenario);
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getExternalFilesDir(null), "chat-captures");
        directory.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(new File(directory, prefix + "-" + name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (Exception e) { throw new AssertionError(e); }
        image.recycle();
    }
}
