package com.example.bodeul.ui.support;

import static org.junit.Assert.*;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import com.example.bodeul.R;
import com.example.bodeul.data.*;
import com.example.bodeul.domain.model.*;
import org.junit.Rule;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.*;

public class SupportViewModelTest {
    @Rule public final InstantTaskExecutorRule executor = new InstantTaskExecutorRule();

    @Test public void listFiltersAndDetailKeepFullMultilineText() {
        Harness h = new Harness(false);
        h.start();
        assertEquals(2, h.model.visibleTickets().size());
        h.model.filter(SupportViewModel.Filter.ANSWERED);
        assertEquals("answered", h.model.visibleTickets().get(0).id);
        h.model.open("answered");
        assertEquals("첫 줄\n둘째 줄", h.model.selectedTicket().body);
        assertEquals("답변 첫 줄\n답변 둘째 줄", h.model.selectedTicket().response);
        h.model.list(false);
        assertEquals(SupportViewModel.Filter.ANSWERED, h.model.filter());
        h.model.filter(SupportViewModel.Filter.WAITING);
        assertEquals("pending", h.model.visibleTickets().get(0).id);
    }

    @Test public void clientRowsAreOwnerAndRoleScopedAndMarkExistingUnreadContract() {
        Harness h = new Harness(false);
        h.clientRows.add(client("foreign", "other", UserRole.PATIENT, true));
        h.clientRows.add(client("wrong-role", "owner", UserRole.GUARDIAN, true));
        h.start();
        assertEquals(2, h.model.visibleTickets().size());
        assertEquals(1, h.readMarks);
    }

    @Test public void managerRowsAreOwnerScopedAndDoNotUseClientReadMark() {
        Harness h = new Harness(true);
        h.managerRows.add(manager("foreign", "other", true));
        h.start();
        assertEquals(2, h.model.visibleTickets().size());
        assertEquals(0, h.readMarks);
        assertEquals(4, h.source.categories().length);
    }

    @Test public void emptyAndFailedReadsCanRetry() {
        Harness h = new Harness(false);
        h.failRead = true;
        h.start();
        assertEquals(SupportViewModel.Gate.ERROR, h.model.gate());
        h.failRead = false;
        h.clientRows.clear();
        h.model.refresh();
        assertTrue(h.model.isReady());
        assertTrue(h.model.visibleTickets().isEmpty());
    }

    @Test public void missingOrForeignDeepLinkReturnsToList() {
        Harness h = new Harness(false);
        h.model = h.create("", "foreign");
        h.start();
        assertEquals(SupportViewModel.Mode.LIST, h.model.mode());
        assertEquals(R.string.final_support_not_found, h.model.consumeNotice());
    }

    @Test public void ownedDeepLinkAndPushOpenDetail() {
        Harness h = new Harness(false);
        h.model = h.create("", "answered");
        h.start();
        assertEquals(SupportViewModel.Mode.DETAIL, h.model.mode());
        h.model.onPush("pending");
        assertEquals("pending", h.model.selectedTicket().id);
    }

    @Test public void deepLinkSurvivesRecreationBeforeFirstAuthenticationCompletes() {
        Harness h = new Harness(false);
        h.deferAuth = true;
        h.model = h.create("", "answered");
        h.start(); h.model.stop(); h.restore();
        h.deferAuth = false; h.start();
        assertEquals(SupportViewModel.Mode.DETAIL, h.model.mode());
        assertEquals("answered", h.model.selectedTicket().id);
    }

    @Test public void pushKeepsComposeDraftAndCategory() {
        Harness h = new Harness(false); h.start(); h.draft();
        h.model.onPush("answered");
        assertEquals(SupportViewModel.Mode.COMPOSE, h.model.mode());
        assertEquals(" 제목 ", h.model.title());
        assertEquals("OTHER", h.model.category());
    }

    @Test public void requiresCategoryAndNonblankFields() {
        Harness h = new Harness(false); h.start(); h.model.compose();
        h.model.setDraft("제목", "본문");
        assertFalse(h.model.canSubmit());
        h.model.selectCategory("INVALID");
        assertFalse(h.model.canSubmit());
        h.model.selectCategory("REPORT");
        assertTrue(h.model.canSubmit());
        h.model.setDraft("  ", "본문");
        assertFalse(h.model.submit());
        assertEquals(0, h.writes);
    }

    @Test public void discardClearsDraftWhileReturnPreservesIt() {
        Harness h = new Harness(false); h.start(); h.draft();
        h.model.list(false); h.model.compose();
        assertTrue(h.model.hasDraft());
        h.model.list(true); h.model.compose();
        assertFalse(h.model.hasDraft());
    }

    @Test public void recreationRestoresDraftModeAndFilter() {
        Harness h = new Harness(false); h.start(); h.model.filter(SupportViewModel.Filter.WAITING); h.draft();
        h.model.stop(); h.restore(); h.start();
        assertEquals(SupportViewModel.Mode.COMPOSE, h.model.mode());
        assertEquals(SupportViewModel.Filter.WAITING, h.model.filter());
        assertEquals(" 제목 ", h.model.title());
        assertEquals(" 본문\n다음 줄 ", h.model.body());
        assertEquals("OTHER", h.model.category());
    }

    @Test public void validatesBookingAndSendsExistingCategoryPayloadOnlyOnce() {
        Harness h = new Harness(false);
        h.model = h.create("booking", ""); h.start(); h.draft();
        assertTrue(h.model.submit());
        assertFalse(h.model.submit());
        assertEquals("booking", h.payload[1]);
        assertEquals(ClientSupportCategory.OTHER, h.payload[2]);
        assertEquals("제목", h.payload[3]);
        assertEquals("본문\n다음 줄", h.payload[4]);
        assertEquals(1, h.writes);
        h.save.onSuccess(h.clientRows);
        assertEquals(SupportViewModel.Mode.LIST, h.model.mode());
        assertFalse(h.model.hasDraft());
    }

    @Test public void managerUsesOriginalRepositoryAndEnum() {
        Harness h = new Harness(true); h.start(); h.draft();
        h.model.selectCategory("DOCUMENT"); h.model.submit();
        assertEquals("owner", h.payload[0]);
        assertEquals(SupportInquiryCategory.DOCUMENT, h.payload[1]);
        assertEquals("제목", h.payload[2]);
        h.save.onSuccess(h.managerRows);
        assertFalse(h.model.hasDraft());
    }

    @Test public void failedBookingContextDoesNotSilentlyBecomeGenericInquiry() {
        Harness h = new Harness(false);
        h.failContext = true; h.model = h.create("booking", ""); h.start();
        assertEquals(SupportViewModel.Gate.NONE, h.model.gate());
        assertTrue(h.model.isReady());
        assertEquals(SupportViewModel.ContextState.ERROR, h.model.contextState());
        h.draft();
        assertFalse(h.model.submit());
        assertEquals(0, h.writes);
    }

    @Test public void failedBookingStillLoadsOwnedDeepLinkAndMarksAnswerRead() {
        Harness h = new Harness(false);
        h.failContext = true; h.model = h.create("booking", "answered"); h.start();
        assertEquals(1, h.reads);
        assertEquals(1, h.readMarks);
        assertEquals(SupportViewModel.Mode.DETAIL, h.model.mode());
        assertEquals("answered", h.model.selectedTicket().id);
        assertEquals("답변 첫 줄\n답변 둘째 줄", h.model.selectedTicket().response);
        h.model.list(false);
        assertEquals(2, h.model.visibleTickets().size());
        h.model.onPush("pending");
        assertEquals("pending", h.model.selectedTicket().id);
    }

    @Test public void pendingBookingAllowsReadingButCannotSubmitUntilVerified() {
        Harness h = new Harness(false);
        h.deferContext = true; h.model = h.create("booking", "answered"); h.start();
        assertTrue(h.model.isReady());
        assertEquals("answered", h.model.selectedTicket().id);
        assertEquals(SupportViewModel.ContextState.CHECKING, h.model.contextState());
        h.draft();
        assertFalse(h.model.submit());
        assertEquals(0, h.writes);
        h.context.onSuccess(h.detail("booking"));
        assertTrue(h.model.submit());
        assertEquals("booking", h.payload[1]);
    }

    @Test public void bookingRetryPreservesDraftAndNeverFallsBackToGenericInquiry() {
        Harness h = new Harness(false);
        h.failContext = true; h.model = h.create("booking", ""); h.start(); h.draft();
        h.failContext = false; h.deferContext = true; h.model.refresh();
        assertEquals(" 제목 ", h.model.title());
        assertEquals("OTHER", h.model.category());
        assertFalse(h.model.submit());
        h.context.onSuccess(h.detail("booking"));
        assertTrue(h.model.submit());
        assertEquals("booking", h.payload[1]);
        assertEquals(1, h.writes);
    }

    @Test public void staleBookingSuccessCannotUnlockFailedValidationAfterReturn() {
        Harness h = new Harness(false);
        h.deferContext = true; h.model = h.create("booking", ""); h.start(); h.draft();
        RepositoryCallback<AppointmentRequestDetail> old = h.context;
        h.model.stop(); h.deferContext = false; h.failContext = true; h.start();
        old.onSuccess(h.detail("booking"));
        assertEquals(SupportViewModel.ContextState.ERROR, h.model.contextState());
        assertFalse(h.model.submit());
        assertEquals(0, h.writes);
    }

    @Test public void staleBookingCannotAttachPreviousAccountsReservation() {
        Harness h = new Harness(false);
        h.deferContext = true; h.model = h.create("booking", ""); h.start();
        RepositoryCallback<AppointmentRequestDetail> old = h.context;
        h.model.stop(); h.user = user("other", UserRole.PATIENT); h.start();
        old.onSuccess(h.detail("booking"));
        assertEquals("", h.model.appointmentId());
        h.draft();
        assertTrue(h.model.submit());
        assertEquals("", h.payload[1]);
        assertEquals("other", ((User) h.payload[0]).getId());
    }

    @Test public void bookingSuccessCannotHideInquiryReadFailure() {
        Harness h = new Harness(false);
        h.failRead = true; h.deferContext = true; h.model = h.create("booking", "answered"); h.start();
        h.context.onSuccess(h.detail("booking"));
        assertEquals(SupportViewModel.Gate.ERROR, h.model.gate());
        assertFalse(h.model.isReady());
        assertNull(h.model.selectedTicket());
        assertFalse(h.model.submit());
    }

    @Test public void wrongRoleKeepsAuthenticatedUserForPermissionRecoveryWithoutReading() {
        for (boolean managerMode : new boolean[]{false, true}) {
            Harness h = new Harness(managerMode);
            h.user = user("owner", managerMode ? UserRole.PATIENT : UserRole.MANAGER);
            h.start();
            assertEquals(SupportViewModel.Gate.PERMISSION, h.model.gate());
            assertEquals(h.user, h.model.currentUser());
            assertEquals(0, h.reads);
            assertFalse(h.model.submit());
        }
    }

    @Test public void writeFailureKeepsDraftAndRequiresFreshListBeforeRetry() {
        Harness h = new Harness(false); h.start(); h.draft(); h.model.submit();
        h.failRead = true;
        h.save.onError("테스트 실패");
        assertFalse(h.model.isReady());
        assertEquals(" 제목 ", h.model.title());
        assertEquals(1, h.writes);
        h.failRead = false; h.model.refresh();
        assertEquals(SupportViewModel.Mode.LIST, h.model.mode());
        assertEquals(R.string.final_support_check_submission, h.model.consumeNotice());
        assertEquals(1, h.writes);
    }

    @Test public void processDeathWithPendingWriteLoadsHistoryWithoutResending() {
        Harness h = new Harness(false); h.start(); h.draft(); h.model.submit();
        h.model.stop(); h.restore(); h.start();
        assertEquals(SupportViewModel.Mode.LIST, h.model.mode());
        assertEquals(R.string.final_support_check_submission, h.model.consumeNotice());
        assertEquals(1, h.writes);
        assertTrue(h.model.hasDraft());
    }

    @Test public void inFlightWriteSurvivesStopStartWithoutDuplicateOrStaleLoading() {
        Harness h = new Harness(false); h.start(); h.draft(); h.model.submit();
        h.model.stop(); h.start();
        assertTrue(h.model.isSubmitting());
        assertFalse(h.model.submit());
        h.save.onSuccess(h.clientRows);
        assertTrue(h.model.isReady());
        assertFalse(h.model.isSubmitting());
        assertEquals(1, h.writes);
    }

    @Test public void completedWriteWhileStoppedIsLoadedOnReturn() {
        Harness h = new Harness(false); h.start(); h.draft(); h.model.submit();
        h.model.stop(); h.save.onSuccess(h.clientRows);
        assertFalse(h.model.isReady());
        h.start();
        assertTrue(h.model.isReady());
        assertFalse(h.model.hasDraft());
    }

    @Test public void authFailureNullWrongRoleAndIncompleteProfileNeverLoadOrWrite() {
        for (User user : new User[]{null, user("owner", UserRole.MANAGER), new User("owner", UserRole.PATIENT, "", "", "")}) {
            Harness h = new Harness(false); h.user = user; h.start();
            assertFalse(h.model.isReady());
            assertFalse(h.model.submit());
            assertEquals(0, h.reads);
            assertEquals(0, h.writes);
        }
    }

    @Test public void changedAccountOrRoleClearsDraftSelectionAndBookingContext() {
        for (User user : new User[]{user("other", UserRole.PATIENT), user("owner", UserRole.GUARDIAN)}) {
            Harness h = new Harness(false); h.model = h.create("booking", ""); h.start(); h.draft();
            h.model.stop(); h.user = user; h.start();
            assertFalse(h.model.hasDraft());
            assertEquals("", h.model.appointmentId());
            assertEquals(SupportViewModel.Mode.LIST, h.model.mode());
            assertTrue(h.model.visibleTickets().isEmpty());
        }
    }

    @Test public void changedIdentityDuringSubmitReauthenticationCannotWrite() {
        Harness h = new Harness(false); h.start(); h.draft();
        h.deferAuth = true; h.model.submit();
        h.authCallback.onSuccess(user("other", UserRole.PATIENT));
        assertEquals(0, h.writes);
        assertEquals(SupportViewModel.Gate.AUTH, h.model.gate());
    }

    @Test public void stoppedAuthCallbackCannotStartWrite() {
        Harness h = new Harness(false); h.start(); h.draft();
        h.deferAuth = true; h.model.submit(); h.model.stop();
        h.authCallback.onSuccess(h.user);
        assertEquals(0, h.writes);
        assertFalse(h.model.isSubmitting());
    }

    @Test public void staleReadCannotReplaceNewAccountData() {
        Harness h = new Harness(false); h.deferRead = true; h.start();
        RepositoryCallback<List<ClientSupportRequest>> old = h.read;
        h.model.stop(); h.user = user("other", UserRole.PATIENT); h.deferRead = false; h.start();
        old.onSuccess(h.clientRows);
        assertTrue(h.model.visibleTickets().isEmpty());
    }

    @Test public void staleWriteCannotClearNewAccountsDraft() {
        Harness h = new Harness(false); h.start(); h.draft(); h.model.submit();
        RepositoryCallback<Object> old = h.save;
        h.model.stop(); h.user = user("other", UserRole.PATIENT); h.start();
        h.model.compose(); h.model.setDraft("새 계정 초안", "새 내용");
        old.onSuccess(h.clientRows);
        assertEquals("새 계정 초안", h.model.title());
        assertEquals(SupportViewModel.Mode.COMPOSE, h.model.mode());
    }

    private static User user(String id, UserRole role) { return new User(id, role, "테스트 사용자", "test@example.com", "01000000000"); }
    private static ClientSupportRequest client(String id, String owner, UserRole role, boolean answered) {
        return new ClientSupportRequest(id, owner, "테스트", role, "", ClientSupportCategory.RESERVATION, "문의 제목", "첫 줄\n둘째 줄",
                answered ? ClientSupportStatus.ANSWERED : ClientSupportStatus.RECEIVED, answered ? 200 : 100,
                answered ? "답변 첫 줄\n답변 둘째 줄" : "", answered ? 300 : 0, "담당자", false, 0, 0, 0);
    }
    private static SupportInquiry manager(String id, String owner, boolean answered) {
        return new SupportInquiry(id, owner, "테스트", SupportInquiryCategory.MATCHING, "문의 제목", "첫 줄\n둘째 줄",
                answered ? SupportInquiryStatus.ANSWERED : SupportInquiryStatus.RECEIVED, answered ? 200 : 100,
                answered ? "답변 첫 줄\n답변 둘째 줄" : "", answered ? 300 : 0, "담당자");
    }

    private static final class Harness {
        User user;
        boolean failRead, failContext, deferRead, deferAuth, deferContext;
        int reads, writes, readMarks;
        Object[] payload;
        RepositoryCallback<User> authCallback;
        RepositoryCallback<AppointmentRequestDetail> context;
        RepositoryCallback<List<ClientSupportRequest>> read;
        RepositoryCallback<Object> save;
        final List<ClientSupportRequest> clientRows = new ArrayList<>(Arrays.asList(client("pending", "owner", UserRole.PATIENT, false), client("answered", "owner", UserRole.PATIENT, true)));
        final List<SupportInquiry> managerRows = new ArrayList<>(Arrays.asList(manager("pending", "owner", false), manager("answered", "owner", true)));
        SavedStateHandle state = new SavedStateHandle();
        final AuthRepository auth;
        final SupportSource source;
        SupportViewModel model;

        @SuppressWarnings("unchecked") Harness(boolean managerMode) {
            user = user("owner", managerMode ? UserRole.MANAGER : UserRole.PATIENT);
            auth = (AuthRepository) Proxy.newProxyInstance(AuthRepository.class.getClassLoader(), new Class[]{AuthRepository.class}, (p, m, a) -> {
                if (m.getName().equals("getCurrentUser")) {
                    authCallback = (RepositoryCallback<User>) a[0];
                    if (!deferAuth) authCallback.onSuccess(user);
                }
                return null;
            });
            Class<?> type = managerMode ? ManagerRepository.class : ClientSupportRepository.class;
            Object repo = Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (p, m, a) -> {
                if (m.getName().equals("getClientSupportRequests") || m.getName().equals("getSupportInquiries")) {
                    reads++;
                    RepositoryCallback callback = (RepositoryCallback) a[1];
                    if (deferRead) read = callback;
                    else if (failRead) callback.onError("테스트 조회 실패");
                    else callback.onSuccess(managerMode ? managerRows : clientRows);
                } else if (m.getName().startsWith("submit")) {
                    writes++; payload = a; save = (RepositoryCallback<Object>) a[a.length - 1];
                } else if (m.getName().equals("markClientSupportResponsesRead")) {
                    readMarks++; ((RepositoryCallback<Void>) a[1]).onSuccess(null);
                } else if (m.getReturnType() == boolean.class) return false;
                return null;
            });
            BookingRepository booking = (BookingRepository) Proxy.newProxyInstance(BookingRepository.class.getClassLoader(), new Class[]{BookingRepository.class}, (p, m, a) -> {
                if (m.getName().equals("getAppointmentRequestDetail")) {
                    RepositoryCallback<AppointmentRequestDetail> callback = (RepositoryCallback<AppointmentRequestDetail>) a[2];
                    if (deferContext) context = callback;
                    else if (failContext) callback.onError("예약 확인 실패");
                    else callback.onSuccess(detail((String) a[1]));
                }
                return null;
            });
            source = managerMode ? new SupportSource((ManagerRepository) repo) : new SupportSource((ClientSupportRepository) repo, booking);
            model = create("", "");
        }
        SupportViewModel create(String booking, String focus) { state = new SavedStateHandle(); return new SupportViewModel(auth, source, state, booking, focus); }
        AppointmentRequestDetail detail(String id) {
            return new AppointmentRequestDetail(new AppointmentRequest(id, user.getId(), "", "테스트 병원", "내과", "일시", "정문", "", AppointmentStatus.REQUESTED, ""), user, null, null, null, null, null);
        }
        void start() { model.start(); }
        void draft() { model.compose(); model.selectCategory("OTHER"); model.setDraft(" 제목 ", " 본문\n다음 줄 "); }
        void restore() {
            Map<String, Object> snapshot = new HashMap<>();
            for (String key : state.keys()) snapshot.put(key, state.get(key));
            state = new SavedStateHandle(snapshot);
            model = new SupportViewModel(auth, source, state, "", "");
        }
    }
}
