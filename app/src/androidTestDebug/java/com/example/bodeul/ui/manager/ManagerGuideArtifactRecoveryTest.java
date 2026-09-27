package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.SavedStateHandle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.CompanionSessionArtifactUploadPolicy;
import com.example.bodeul.data.ManagerRepository;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 외부 파일 선택 중 프로세스가 재생성되는 경계 조건을 검증한다. */
@RunWith(AndroidJUnit4.class)
public class ManagerGuideArtifactRecoveryTest {

    @Test
    public void pickerResult_waitsForAuthenticationAndKeepsOriginalExpectation() {
        AtomicReference<RepositoryCallback<User>> authCallback = new AtomicReference<>();
        AtomicReference<Object[]> replaceArguments = new AtomicReference<>();
        ManagerGuideViewModel viewModel = createViewModel(
                authRepository(authCallback, false),
                managerRepository(replaceArguments, null));
        Uri receipt = Uri.parse("content://bodeul-test/payment/receipt.pdf");

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            viewModel.reload();
            viewModel.replaceSessionArtifacts(
                    "session-step-8",
                    "PAYMENT_EVIDENCE",
                    CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE,
                    Collections.singletonList(receipt));
        });

        assertNull(replaceArguments.get());
        assertNotNull(authCallback.get());

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                authCallback.get().onSuccess(managerUser()));

        Object[] arguments = replaceArguments.get();
        assertNotNull(arguments);
        assertEquals("manager-1", arguments[0]);
        assertEquals("session-step-8", arguments[1]);
        assertEquals("PAYMENT_EVIDENCE", arguments[2]);
        assertEquals(CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE, arguments[3]);
        @SuppressWarnings("unchecked")
        List<Uri> uploadedUris = (List<Uri>) arguments[5];
        assertEquals(Collections.singletonList(receipt), uploadedUris);
    }

    @Test
    public void pickerResult_isDiscardedWhenAuthenticationRecoveryFails() {
        AtomicReference<RepositoryCallback<User>> authCallback = new AtomicReference<>();
        AtomicReference<Object[]> replaceArguments = new AtomicReference<>();
        ManagerGuideViewModel viewModel = createViewModel(
                authRepository(authCallback, false),
                managerRepository(replaceArguments, null));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            viewModel.reload();
            viewModel.replaceSessionArtifacts(
                    "session-step-8",
                    "PAYMENT_EVIDENCE",
                    CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE,
                    Collections.singletonList(
                            Uri.parse("content://bodeul-test/payment/rejected.pdf")));
            authCallback.get().onError("로그인 세션이 만료되었습니다.");
        });

        assertNull(replaceArguments.get());
    }

    @Test
    public void artifactConflict_refreshesDashboardEvenWhenServerMessageIsNotNormalized() {
        AtomicInteger dashboardLoads = new AtomicInteger();
        ManagerGuideViewModel viewModel = createViewModel(
                authRepository(new AtomicReference<>(), true),
                managerRepository(new AtomicReference<>(), dashboardLoads));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            viewModel.reload();
            viewModel.replaceSessionArtifacts(
                    "session-step-8",
                    "PAYMENT_EVIDENCE",
                    CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE,
                    Collections.singletonList(
                            Uri.parse("content://bodeul-test/payment/conflict.pdf")));
        });

        assertEquals(2, dashboardLoads.get());
    }

    @Test
    public void lateRefreshFailure_keepsDashboardRenderedByConcurrentLoad() {
        List<RepositoryCallback<ManagerDashboard>> dashboardCallbacks = new ArrayList<>();
        AtomicReference<RepositoryCallback<ManagerDashboard>> replaceCallback =
                new AtomicReference<>();
        ManagerGuideViewModel viewModel = createViewModel(
                authRepository(new AtomicReference<>(), true),
                dashboardRaceRepository(dashboardCallbacks, replaceCallback),
                coordinator());
        ManagerDashboard dashboard = mockDashboard();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            viewModel.reload();
            viewModel.replaceSessionArtifacts(
                    "session-step-8",
                    "PAYMENT_EVIDENCE",
                    CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE,
                    Collections.singletonList(
                            Uri.parse("content://bodeul-test/payment/race.pdf")));
            replaceCallback.get().onError("동행 세션 상태가 변경되었습니다.");

            assertEquals(2, dashboardCallbacks.size());
            dashboardCallbacks.get(0).onSuccess(dashboard);
            dashboardCallbacks.get(1).onError("네트워크 연결을 확인해 주세요.");
        });

        ManagerGuideViewModel.UiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertSame(dashboard, state.dashboard);
        assertEquals(ManagerGuideViewModel.StatePanelType.NONE, state.statePanelType);
    }

    @Test
    public void refreshFailure_restoresScreenWhileConcurrentReloadIsLoading() {
        List<RepositoryCallback<ManagerDashboard>> dashboardCallbacks = new ArrayList<>();
        AtomicReference<RepositoryCallback<ManagerDashboard>> replaceCallback =
                new AtomicReference<>();
        ManagerGuideViewModel viewModel = createViewModel(
                authRepository(new AtomicReference<>(), true),
                dashboardRaceRepository(dashboardCallbacks, replaceCallback),
                coordinator());
        ManagerDashboard dashboard = mockDashboard();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            viewModel.reload();
            dashboardCallbacks.get(0).onSuccess(dashboard);
            viewModel.replaceSessionArtifacts(
                    "session-step-8",
                    "PAYMENT_EVIDENCE",
                    CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE,
                    Collections.singletonList(
                            Uri.parse("content://bodeul-test/payment/restore.pdf")));
            replaceCallback.get().onError("동행 세션 상태가 변경되었습니다.");
            viewModel.reload();

            assertEquals(3, dashboardCallbacks.size());
            dashboardCallbacks.get(1).onError("네트워크 연결을 확인해 주세요.");
        });

        ManagerGuideViewModel.UiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertSame(dashboard, state.dashboard);
        assertEquals(ManagerGuideViewModel.StatePanelType.NONE, state.statePanelType);
    }

    @Test
    public void refreshNoActiveSession_mapsToEmptyPanel() {
        List<RepositoryCallback<ManagerDashboard>> dashboardCallbacks = new ArrayList<>();
        AtomicReference<RepositoryCallback<ManagerDashboard>> replaceCallback =
                new AtomicReference<>();
        ManagerGuideViewModel viewModel = createViewModel(
                authRepository(new AtomicReference<>(), true),
                dashboardRaceRepository(dashboardCallbacks, replaceCallback));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            viewModel.reload();
            viewModel.replaceSessionArtifacts(
                    "session-step-8",
                    "PAYMENT_EVIDENCE",
                    CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE,
                    Collections.singletonList(
                            Uri.parse("content://bodeul-test/payment/ended.pdf")));
            replaceCallback.get().onError("동행 세션 상태가 변경되었습니다.");

            assertEquals(2, dashboardCallbacks.size());
            dashboardCallbacks.get(1).onError(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
        });

        ManagerGuideViewModel.UiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(ManagerGuideViewModel.StatePanelType.EMPTY, state.statePanelType);
    }

    private static ManagerGuideViewModel createViewModel(
            AuthRepository authRepository,
            ManagerRepository managerRepository
    ) {
        return createViewModel(authRepository, managerRepository, null);
    }

    private static ManagerGuideViewModel createViewModel(
            AuthRepository authRepository,
            ManagerRepository managerRepository,
            ManagerGuideCoordinator coordinator
    ) {
        CompanionRealtimeSubscriber realtimeSubscriber = new CompanionRealtimeSubscriber() {
            @Override
            public void subscribe(String companionSessionId, Runnable changedCallback) {
            }

            @Override
            public void stop() {
            }
        };
        return new ManagerGuideViewModel(
                authRepository,
                managerRepository,
                coordinator,
                realtimeSubscriber,
                new SavedStateHandle(),
                false);
    }

    private static ManagerGuideCoordinator coordinator() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return new ManagerGuideCoordinator(
                context,
                new ManagerGuidePresentationFormatter(context));
    }

    private static ManagerDashboard mockDashboard() {
        AtomicReference<ManagerDashboard> dashboard = new AtomicReference<>();
        new MockManagerRepository(new MockBodeulRepository()).getManagerDashboard(
                "manager-1",
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        dashboard.set(result);
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
        assertNotNull(dashboard.get());
        return dashboard.get();
    }

    private static AuthRepository authRepository(
            AtomicReference<RepositoryCallback<User>> callbackReference,
            boolean completeImmediately
    ) {
        return (AuthRepository) Proxy.newProxyInstance(
                AuthRepository.class.getClassLoader(),
                new Class<?>[]{AuthRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getCurrentUser")) {
                        @SuppressWarnings("unchecked")
                        RepositoryCallback<User> callback =
                                (RepositoryCallback<User>) arguments[0];
                        callbackReference.set(callback);
                        if (completeImmediately) {
                            callback.onSuccess(managerUser());
                        }
                        return null;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return null;
                });
    }

    private static ManagerRepository managerRepository(
            AtomicReference<Object[]> replaceArguments,
            AtomicInteger dashboardLoads
    ) {
        return (ManagerRepository) Proxy.newProxyInstance(
                ManagerRepository.class.getClassLoader(),
                new Class<?>[]{ManagerRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getManagerDashboard")) {
                        if (dashboardLoads != null) {
                            dashboardLoads.incrementAndGet();
                        }
                        return null;
                    }
                    if (method.getName().equals("replaceSessionArtifacts")) {
                        replaceArguments.set(arguments.clone());
                        if (dashboardLoads != null) {
                            @SuppressWarnings("unchecked")
                            RepositoryCallback<Object> callback =
                                    (RepositoryCallback<Object>) arguments[6];
                            callback.onError("동행 세션 상태가 변경되었습니다.");
                        }
                        return null;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return null;
                });
    }

    private static ManagerRepository dashboardRaceRepository(
            List<RepositoryCallback<ManagerDashboard>> dashboardCallbacks,
            AtomicReference<RepositoryCallback<ManagerDashboard>> replaceCallback
    ) {
        return (ManagerRepository) Proxy.newProxyInstance(
                ManagerRepository.class.getClassLoader(),
                new Class<?>[]{ManagerRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getManagerDashboard")) {
                        @SuppressWarnings("unchecked")
                        RepositoryCallback<ManagerDashboard> callback =
                                (RepositoryCallback<ManagerDashboard>) arguments[1];
                        dashboardCallbacks.add(callback);
                        return null;
                    }
                    if (method.getName().equals("replaceSessionArtifacts")) {
                        @SuppressWarnings("unchecked")
                        RepositoryCallback<ManagerDashboard> callback =
                                (RepositoryCallback<ManagerDashboard>) arguments[6];
                        replaceCallback.set(callback);
                        return null;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return null;
                });
    }

    private static User managerUser() {
        return new User(
                "manager-1",
                UserRole.MANAGER,
                "테스트 매니저",
                "manager@example.com",
                "010-0000-0000");
    }
}
