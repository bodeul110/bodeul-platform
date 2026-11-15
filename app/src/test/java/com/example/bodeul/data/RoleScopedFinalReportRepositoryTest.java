package com.example.bodeul.data;

import static org.junit.Assert.*;

import com.example.bodeul.domain.model.*;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoleScopedFinalReportRepositoryTest {
    private final List<String> calls = new ArrayList<>();
    private final SessionReport report = new SessionReport("report", "session", "진료 요약", "진료 기록",
            "복약 안내", "약 이름", "변경", "일정", MedicationComparisonDecision.CHANGED, "확인", "다음 방문");
    private final AppointmentRequest request = new AppointmentRequest("request", "patient", "guardian",
            "병원", "내과", "2026-11-15 10:00", "입구", "", AppointmentStatus.COMPLETED, "manager");
    private final AppointmentRequestDetail detail = new AppointmentRequestDetail(request, null, null,
            null, null, report, null);
    private final GuardianReportEntry entry = new GuardianReportEntry(request, null, null, null, report, null);
    private GuardianReportEntry result;
    private String error;

    @Test public void patientUsesOwnDetailContractAndPreservesReport() {
        repository(detail, null, null).getReport(user(UserRole.PATIENT), "request", callback());
        assertSame(report, result.getSessionReport());
        assertEquals(Collections.singletonList("getAppointmentRequestDetail"), calls);
        assertNull(error);
    }

    @Test public void guardianUsesOnlyGrantedDashboard() {
        GuardianReportDashboard dashboard = new GuardianReportDashboard(user(UserRole.GUARDIAN), List.of(entry));
        repository(null, dashboard, null).getReport(user(UserRole.GUARDIAN), "request", callback());
        assertSame(entry, result);
        assertEquals(Collections.singletonList("getGuardianDashboard"), calls);
    }

    @Test public void guardianMissingEntryNeverFallsBackToBooking() {
        repository(detail, new GuardianReportDashboard(user(UserRole.GUARDIAN), List.of()), null)
                .getReport(user(UserRole.GUARDIAN), "request", callback());
        assertNull(result);
        assertNotNull(error);
        assertEquals(Collections.singletonList("getGuardianDashboard"), calls);
    }

    @Test public void managerHistoryNeverExposesMedicalReportEvenFromLegacyStore() {
        repository(null, null, List.of(detail)).getReport(user(UserRole.MANAGER), "request", callback());
        assertNotNull(result);
        assertNull(result.getSessionReport());
        assertEquals(Collections.singletonList("getManagerHistoryDetails"), calls);
    }

    @Test public void unfinishedAppointmentCannotBeShownAsFinalReport() {
        request.setStatus(AppointmentStatus.IN_PROGRESS);
        repository(detail, null, null).getReport(user(UserRole.PATIENT), "request", callback());
        assertNull(result);
        assertNotNull(error);
    }

    @Test public void mismatchedResponseIdIsRejected() {
        repository(detail, null, List.of(detail)).getReport(user(UserRole.PATIENT), "different", callback());
        assertNull(result);
        assertNotNull(error);
    }

    @Test public void managerCannotOpenAnotherHistoryEntry() {
        repository(null, null, List.of(detail)).getReport(user(UserRole.MANAGER), "different", callback());
        assertNull(result);
        assertNotNull(error);
    }

    @Test public void unsupportedRoleAndMissingInputDoNotReadAnyStore() {
        FinalReportRepository repository = repository(detail, null, null);
        repository.getReport(user(UserRole.ADMIN), "request", callback());
        repository.getReport(null, "request", callback());
        repository.getReport(user(UserRole.PATIENT), "  ", callback());
        assertTrue(calls.isEmpty());
        assertNull(result);
        assertNotNull(error);
    }

    @Test public void emptyReportRemainsEmptyWithoutGeneratedAdvice() {
        AppointmentRequestDetail empty = new AppointmentRequestDetail(request, null, null, null, null, null, null);
        repository(empty, null, null).getReport(user(UserRole.PATIENT), "request", callback());
        assertNotNull(result);
        assertNull(result.getSessionReport());
    }

    @Test public void repositoryErrorIsNotRetriedThroughAnotherRole() {
        BookingRepository failed = (BookingRepository) Proxy.newProxyInstance(BookingRepository.class.getClassLoader(),
                new Class<?>[]{BookingRepository.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    ((RepositoryCallback<?>) args[args.length - 1]).onError("접근 제한");
                    return null;
                });
        new RoleScopedFinalReportRepository(failed, null, null).getReport(user(UserRole.PATIENT), "request", callback());
        assertNull(result);
        assertEquals("접근 제한", error);
        assertEquals(1, calls.size());
    }

    private FinalReportRepository repository(AppointmentRequestDetail patient,
            GuardianReportDashboard guardian, List<AppointmentRequestDetail> manager) {
        return new RoleScopedFinalReportRepository(stub(BookingRepository.class, patient),
                stub(GuardianReportRepository.class, guardian), stub(ManagerRepository.class, manager));
    }

    @SuppressWarnings("unchecked")
    private <T> T stub(Class<T> type, Object response) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            calls.add(method.getName());
            ((RepositoryCallback<Object>) args[args.length - 1]).onSuccess(response);
            return null;
        });
    }

    private User user(UserRole role) { return new User("user", role, "사용자", "", ""); }

    private RepositoryCallback<GuardianReportEntry> callback() {
        return new RepositoryCallback<GuardianReportEntry>() {
            @Override public void onSuccess(GuardianReportEntry value) { result = value; }
            @Override public void onError(String message) { error = message; }
        };
    }
}
