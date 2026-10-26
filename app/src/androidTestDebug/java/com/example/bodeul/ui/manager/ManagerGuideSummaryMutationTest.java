package com.example.bodeul.ui.manager;

import android.content.Context;

import androidx.lifecycle.SavedStateHandle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.mock.MockManagerRepository;
import com.example.bodeul.data.realtime.CompanionRealtimeSubscriber;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 지연된 저장 응답과 화면 전환을 네트워크 없이 검증한다. */
@RunWith(AndroidJUnit4.class)
public class ManagerGuideSummaryMutationTest {
    @Test
    public void pendingSaveBlocksDuplicateAndAdvance_failureKeepsDraft_retryReleasesGate() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DeferredRepository repository = new DeferredRepository();
            ManagerGuideViewModel viewModel = createViewModel(repository);
            viewModel.reload();
            String sessionId = repository.sessionId();
            viewModel.saveSummaryDraft(sessionId,
                    ManagerGuideSummaryDraft.fromInput("작성 중", "기존 기록"));
            viewModel.saveConsultationSummaryNote("작성 중");
            viewModel.saveConsultationSummaryNote("중복 저장");
            viewModel.advanceStep();
            assertEquals(1, repository.saves);
            assertEquals(0, repository.advances);
            assertTrue(viewModel.getMutationInFlight().getValue());
            assertEquals(sessionId, repository.expectedSessionId);
            assertEquals("CONSULTATION_SUMMARY", repository.expectedStepCode);
            repository.pending.onError("네트워크 오류");
            assertFalse(viewModel.getMutationInFlight().getValue());
            assertEquals("작성 중", viewModel.getSummaryDraft(sessionId).note);
            viewModel.saveConsultationSummaryNote("");
            assertEquals(2, repository.saves);
            repository.completeSave();
            assertFalse(viewModel.getMutationInFlight().getValue());
            assertEquals("", viewModel.getUiState().getValue().dashboard.getSession()
                    .getFieldPhotoNote());
        });
    }

    @Test
    public void lateSaveDoesNotRestorePreviousStepAndDraftDoesNotCrossStepBoundary() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            DeferredRepository repository = new DeferredRepository();
            ManagerGuideViewModel viewModel = createViewModel(repository);
            viewModel.reload();
            String sessionId = repository.sessionId();
            ManagerDashboard stale = viewModel.getUiState().getValue().dashboard;
            viewModel.saveSummaryDraft(sessionId,
                    ManagerGuideSummaryDraft.fromInput("초안", ""));
            viewModel.saveConsultationSummaryNote("초안");
            repository.store = new MockBodeulRepository();
            repository.store.getManagerSessions("manager-1").get(0)
                    .applyServerGuideProgress("PAYMENT_EVIDENCE", true, true, "");
            viewModel.loadDashboard();
            assertNull(viewModel.getSummaryDraft(sessionId));
            repository.pending.onSuccess(stale);
            assertEquals("PAYMENT_EVIDENCE", viewModel.getUiState().getValue()
                    .dashboard.getSession().getCurrentStepCode());
            viewModel.saveConsultationSummaryNote("이전 화면에서 저장");
            assertEquals(1, repository.saves);
        });
    }

    private ManagerGuideViewModel createViewModel(DeferredRepository repository) {
        AuthRepository auth = (AuthRepository) Proxy.newProxyInstance(
                AuthRepository.class.getClassLoader(), new Class<?>[]{AuthRepository.class},
                (proxy, method, args) -> {
                    if ("getCurrentUser".equals(method.getName())) {
                        @SuppressWarnings("unchecked")
                        RepositoryCallback<User> callback = (RepositoryCallback<User>) args[0];
                        callback.onSuccess(new User("manager-1", UserRole.MANAGER,
                                "테스트 매니저", "manager@example.com", "010-0000-0000"));
                    }
                    return method.getReturnType() == boolean.class ? false : null;
                });
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return new ManagerGuideViewModel(auth, repository,
                new ManagerGuideCoordinator(context, new ManagerGuidePresentationFormatter(context)),
                new CompanionRealtimeSubscriber() {
                    @Override public void subscribe(String id, Runnable changed) { }
                    @Override public void stop() { }
                }, new SavedStateHandle(), false);
    }

    private static final class DeferredRepository extends MockManagerRepository {
        MockBodeulRepository store = new MockBodeulRepository();
        RepositoryCallback<ManagerDashboard> pending;
        String expectedSessionId;
        String expectedStepCode;
        String note;
        int saves;
        int advances;

        DeferredRepository() {
            super(new MockBodeulRepository());
            store.getManagerSessions("manager-1").get(0)
                    .applyServerGuideProgress("CONSULTATION_SUMMARY", true, true, "");
        }

        String sessionId() { return store.getManagerSessions("manager-1").get(0).getId(); }

        @Override public void getManagerDashboard(
                String managerId, RepositoryCallback<ManagerDashboard> callback) {
            callback.onSuccess(store.getManagerDashboard(managerId));
        }

        @Override public void saveConsultationSummaryNote(String managerId, String sessionId,
                String stepCode, String note, RepositoryCallback<ManagerDashboard> callback) {
            saves++;
            expectedSessionId = sessionId;
            expectedStepCode = stepCode;
            this.note = note;
            pending = callback;
        }

        @Override public void advanceCurrentStep(String managerId, String sessionId,
                String stepCode, RepositoryCallback<ManagerDashboard> callback) {
            advances++;
        }

        void completeSave() {
            assertNotNull(pending);
            store.getManagerSessions("manager-1").get(0).setFieldPhotoNote(note);
            pending.onSuccess(store.getManagerDashboard("manager-1"));
        }
    }
}
