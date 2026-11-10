package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.example.bodeul.R;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.data.mock.MockAuthRepository;
import com.example.bodeul.data.mock.MockBookingRepository;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** 리뷰에서 확인한 조회 지연·화면 재생성 조합을 실제 Activity로 회귀 검증한다. */
@RunWith(AndroidJUnit4.class)
public class ClientBookingHistoryReviewRegressionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Map<Field, Object> originalRepositories = new LinkedHashMap<>();
    private DelayedBookingRepository bookingRepository;

    @Before
    public void useDelayedIsolatedRepositories() throws Exception {
        Context context = instrumentation.getTargetContext();
        MockBodeulRepository data = new MockBodeulRepository();
        MockAuthRepository authRepository = new MockAuthRepository(data);
        AtomicReference<User> signedInUser = new AtomicReference<>();
        authRepository.signIn(
                context.getString(R.string.demo_account_patient_email),
                context.getString(R.string.demo_account_password),
                UserRole.PATIENT,
                new RepositoryCallback<User>() {
                    @Override
                    public void onSuccess(User result) {
                        signedInUser.set(result);
                    }

                    @Override
                    public void onError(String message) {
                        throw new AssertionError("로컬 테스트 사용자 준비 실패: " + message);
                    }
                }
        );
        User patient = signedInUser.get();
        assertNotNull(patient);
        List<AppointmentRequest> requests = data.getAppointmentRequestsForUser(
                patient.getId(),
                UserRole.PATIENT
        );
        assertFalse(requests.isEmpty());
        for (AppointmentRequest request : requests) {
            request.setStatus(AppointmentStatus.MATCHED);
        }

        bookingRepository = new DelayedBookingRepository(data);
        replaceRepository("authRepository", authRepository);
        replaceRepository("bookingRepository", bookingRepository);
    }

    @After
    public void restoreRepositories() throws Exception {
        instrumentation.runOnMainSync(() -> {
            Set<Activity> activities = new HashSet<>();
            for (Stage stage : Stage.values()) {
                if (stage != Stage.DESTROYED) {
                    activities.addAll(ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(stage));
                }
            }
            for (Activity activity : activities) {
                activity.finish();
            }
        });
        instrumentation.waitForIdleSync();
        for (Map.Entry<Field, Object> entry : originalRepositories.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    @Test
    public void recreateBeforeFirstResponse_selectsFirstNonEmptyTabAfterResponse() {
        try (ActivityScenario<ClientBookingHistoryActivity> scenario =
                     ActivityScenario.launch(ClientBookingHistoryActivity.class)) {
            scenario.recreate();
            scenario.onActivity(activity -> {
                bookingRepository.deliverPending();
                assertTrue(activity.findViewById(R.id.buttonHistoryFilterProgress).isSelected());
                assertFalse(activity.findViewById(R.id.buttonHistoryFilterMatching).isSelected());
                assertTrue(((LinearLayout) activity.findViewById(
                        R.id.layoutClientBookingHistoryEntries)).getChildCount() > 0);
            });
        }
    }

    @Test
    public void recreateAfterExplicitEmptyTab_keepsUserSelection() {
        try (ActivityScenario<ClientBookingHistoryActivity> scenario =
                     ActivityScenario.launch(ClientBookingHistoryActivity.class)) {
            scenario.onActivity(activity -> {
                bookingRepository.deliverPending();
                assertTrue(activity.findViewById(R.id.buttonHistoryFilterProgress).isSelected());
                assertTrue(activity.findViewById(R.id.buttonHistoryFilterMatching).performClick());
                assertTrue(activity.findViewById(R.id.buttonHistoryFilterMatching).isSelected());
                assertTrue(activity.findViewById(
                        R.id.textClientBookingHistoryFilterEmpty).getVisibility() == View.VISIBLE);
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                bookingRepository.deliverPending();
                assertTrue(activity.findViewById(R.id.buttonHistoryFilterMatching).isSelected());
                assertTrue(activity.findViewById(
                        R.id.textClientBookingHistoryFilterEmpty).getVisibility() == View.VISIBLE);
            });
        }
    }

    private void replaceRepository(String name, Object replacement) throws Exception {
        Field field = ServiceLocator.class.getDeclaredField(name);
        field.setAccessible(true);
        originalRepositories.put(field, field.get(null));
        field.set(null, replacement);
    }

    private static final class DelayedBookingRepository extends MockBookingRepository {
        private final MockBodeulRepository data;
        private final List<PendingRead> pendingReads = new ArrayList<>();

        private DelayedBookingRepository(MockBodeulRepository data) {
            super(data);
            this.data = data;
        }

        @Override
        public void getMyAppointmentRequests(
                User currentUser,
                RepositoryCallback<List<AppointmentRequest>> callback
        ) {
            pendingReads.add(new PendingRead(currentUser, callback));
        }

        private void deliverPending() {
            List<PendingRead> deliveries = new ArrayList<>(pendingReads);
            pendingReads.clear();
            for (PendingRead delivery : deliveries) {
                delivery.callback.onSuccess(new ArrayList<>(data.getAppointmentRequestsForUser(
                        delivery.user.getId(),
                        delivery.user.getRole()
                )));
            }
        }
    }

    private static final class PendingRead {
        private final User user;
        private final RepositoryCallback<List<AppointmentRequest>> callback;

        private PendingRead(
                User user,
                RepositoryCallback<List<AppointmentRequest>> callback
        ) {
            this.user = user;
            this.callback = callback;
        }
    }
}
