package com.example.bodeul.ui.booking;

import static org.junit.Assert.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class BookingSubmissionViewModelTest {
    @Rule public final InstantTaskExecutorRule executor = new InstantTaskExecutorRule();

    private static final User PATIENT = user("patient", UserRole.PATIENT);
    private static final BookingRequestDraft DRAFT = BookingRequestDraft.builder().build();
    private static final AppointmentRequest REQUEST = new AppointmentRequest("request", "patient", "",
            "합성 병원", "내과", "2026-12-20 10:30", "정문", "", AppointmentStatus.REQUESTED, "");

    @Test public void blocksDuplicateDuringAuthenticationStorageAndPendingCompletion() {
        Harness h = new Harness();
        assertTrue(h.model.submit(PATIENT, null, DRAFT));
        assertTrue(h.model.isBusy());
        assertFalse(h.model.submit(PATIENT, null, DRAFT));
        assertEquals(1, h.authCalls);
        h.authResult.onSuccess(PATIENT);
        assertEquals("createAppointmentRequest", h.saveMethod);
        assertFalse(h.model.submit(PATIENT, null, DRAFT));
        h.saveResult.onSuccess(REQUEST);
        assertTrue(h.model.isBusy());
        assertFalse(h.model.submit(PATIENT, null, DRAFT));
        BookingSubmissionViewModel.Result result = h.model.consumeResult(PATIENT);
        assertSame(REQUEST, result.request);
        assertFalse(result.updated);
        assertFalse(result.authFailed);
        assertFalse(h.model.isBusy());
        assertNull(h.model.consumeResult(PATIENT));
        assertEquals(1, h.saveCalls);
        assertEquals(Boolean.FALSE, h.state.get(BookingSubmissionViewModel.PENDING));
    }

    @Test public void retainsUpdateTargetAndConsumesFailureOnlyOnce() {
        Harness h = new Harness();
        h.model.submit(PATIENT, "edit-request", DRAFT);
        h.authResult.onSuccess(PATIENT);
        assertEquals("updateAppointmentRequest", h.saveMethod);
        assertEquals("edit-request", h.editId);
        h.saveResult.onError("테스트용 저장 실패");
        BookingSubmissionViewModel.Result result = h.model.consumeResult(PATIENT);
        assertTrue(result.updated);
        assertEquals("테스트용 저장 실패", result.error);
        assertFalse(result.authFailed);
        assertNull(h.model.consumeResult(PATIENT));
        assertFalse(h.model.isBusy());
        assertEquals(1, h.saveCalls);
    }

    @Test public void rejectsChangedIdentityRoleAndIncompleteProfileBeforeStorage() {
        for (User changed : new User[]{user("other", UserRole.PATIENT), user("patient", UserRole.GUARDIAN),
                new User("patient", UserRole.PATIENT, "", "patient@example.com", "01000000001")}) {
            Harness h = new Harness();
            h.model.submit(PATIENT, null, DRAFT);
            h.authResult.onSuccess(changed);
            assertEquals(0, h.saveCalls);
            assertTrue(h.model.consumeResult(PATIENT).authFailed);
            assertFalse(h.model.isBusy());
        }
    }

    @Test public void authenticationFailureNeverSendsDraft() {
        Harness h = new Harness();
        h.model.submit(PATIENT, null, DRAFT);
        h.authResult.onError("테스트용 인증 실패");
        assertTrue(h.model.consumeResult(PATIENT).authFailed);
        assertEquals(0, h.saveCalls);
    }

    @Test public void rejectsGuardianAndManagerMutations() {
        for (UserRole role : new UserRole[]{UserRole.GUARDIAN, UserRole.MANAGER}) {
            Harness h = new Harness();
            assertFalse(h.model.submit(user("other", role), null, DRAFT));
            assertFalse(h.model.submit(user("other", role), "edit", DRAFT));
            assertEquals(0, h.authCalls);
            assertEquals(0, h.saveCalls);
            assertFalse(h.model.isBusy());
        }
    }

    @Test public void completionDoesNotLeakToAnotherAccountOrRole() {
        for (User other : new User[]{user("other", UserRole.PATIENT), user("patient", UserRole.GUARDIAN)}) {
            Harness h = new Harness();
            h.model.submit(PATIENT, null, DRAFT);
            h.authResult.onSuccess(PATIENT);
            h.saveResult.onSuccess(REQUEST);
            assertNull(h.model.consumeResult(other));
            assertFalse(h.model.isBusy());
            assertNull(h.model.consumeResult(PATIENT));
        }
    }

    @Test public void restoredPendingStateBlocksAutomaticResendUntilHistoryAcknowledgement() {
        Harness h = new Harness();
        h.model.submit(PATIENT, null, DRAFT);
        h.authResult.onSuccess(PATIENT);
        BookingSubmissionViewModel restored = h.restoreProcessState();
        assertTrue(restored.requiresRecovery());
        assertTrue(restored.isBusy());
        assertFalse(restored.submit(PATIENT, null, DRAFT));
        assertNull(restored.consumeResult(PATIENT));
        restored.discardRecoveryForOtherOwner(PATIENT);
        assertTrue(restored.requiresRecovery());
        restored.acknowledgeRecovery();
        assertFalse(restored.requiresRecovery());
        assertFalse(restored.isBusy());
        assertEquals(1, h.authCalls);
        assertEquals(1, h.saveCalls);
    }

    @Test public void unconsumedCompletionAlsoRequiresRecoveryAfterProcessRecreation() {
        Harness h = new Harness();
        h.model.submit(PATIENT, null, DRAFT);
        h.authResult.onSuccess(PATIENT);
        h.saveResult.onSuccess(REQUEST);
        BookingSubmissionViewModel restored = h.restoreProcessState();
        assertTrue(restored.requiresRecovery());
        assertFalse(restored.submit(PATIENT, null, DRAFT));
        assertEquals(1, h.saveCalls);
    }

    @Test public void recoveryFromAnotherAccountIsDiscardedWithoutResend() {
        Harness h = new Harness();
        h.model.submit(PATIENT, null, DRAFT);
        BookingSubmissionViewModel restored = h.restoreProcessState();
        restored.discardRecoveryForOtherOwner(user("other", UserRole.PATIENT));
        assertFalse(restored.requiresRecovery());
        assertFalse(restored.isBusy());
        assertEquals(0, h.saveCalls);
    }

    private static User user(String id, UserRole role) {
        return new User(id, role, "합성 사용자", id + "@example.com", "01000000001");
    }

    private static final class Harness {
        final SavedStateHandle state = new SavedStateHandle();
        RepositoryCallback<User> authResult;
        RepositoryCallback<AppointmentRequest> saveResult;
        int authCalls;
        int saveCalls;
        String saveMethod;
        String editId;
        final AuthRepository auth = (AuthRepository) Proxy.newProxyInstance(AuthRepository.class.getClassLoader(),
                new Class<?>[]{AuthRepository.class}, (proxy, method, args) -> {
                    if (!"getCurrentUser".equals(method.getName())) throw new AssertionError(method.getName());
                    authCalls++;
                    authResult = callback(args[0]);
                    return null;
                });
        final BookingRepository booking = (BookingRepository) Proxy.newProxyInstance(BookingRepository.class.getClassLoader(),
                new Class<?>[]{BookingRepository.class}, (proxy, method, args) -> {
                    saveMethod = method.getName();
                    if (!"createAppointmentRequest".equals(saveMethod) && !"updateAppointmentRequest".equals(saveMethod)) {
                        throw new AssertionError(saveMethod);
                    }
                    saveCalls++;
                    editId = args.length == 4 ? (String) args[1] : null;
                    saveResult = callback(args[args.length - 1]);
                    return null;
                });
        final BookingSubmissionViewModel model = new BookingSubmissionViewModel(auth, booking, state);

        BookingSubmissionViewModel restoreProcessState() {
            Map<String, Object> saved = new HashMap<>();
            for (String key : state.keys()) saved.put(key, state.get(key));
            return new BookingSubmissionViewModel(auth, booking, new SavedStateHandle(saved));
        }
        @SuppressWarnings("unchecked") private static <T> RepositoryCallback<T> callback(Object value) {
            return (RepositoryCallback<T>) value;
        }
    }
}
