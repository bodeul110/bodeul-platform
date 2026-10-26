package com.bodeul.core.appointment;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import com.bodeul.core.account.BookingInventoryPostgresConfiguration;
import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRepository.AppUser;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.auth.FirebaseAccountStatus;
import com.bodeul.core.consent.GuardianBookingApprovalService;
import com.bodeul.core.consent.GuardianBookingApprovalService.ApprovalView;
import com.bodeul.core.consent.GuardianBookingException;
import com.bodeul.core.consent.GuardianBookingPostgresConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** localhost의 일회용 테스트 DB에서 운영 코드와 Core role의 트랜잭션 경계를 검증한다. */
@Tag("postgres-booking-approval")
class GuardianBookingTransactionPostgresTests {
    private static final AppUser PATIENT = new AppUser(UUID.fromString("a1910000-0000-0000-0000-000000000001"), "api-test-patient", AppUserRole.PATIENT);
    private static final AppUser GUARDIAN = new AppUser(UUID.fromString("a1910000-0000-0000-0000-000000000002"), "api-test-guardian", AppUserRole.GUARDIAN);
    private JdbcTemplate owner;
    private AnnotationConfigApplicationContext context;
    private GuardianBookingService service;
    private GuardianBookingApprovalService approvals;
    private AppointmentService appointments;
    private TransactionTemplate transaction;
    private AppointmentService.CreateAppointmentCommand command;

    @BeforeEach
    void setUp() {
        assertThat(System.getenv("BOOKING_TEST_DB_URL"))
                .matches("^jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/bodeul_guardian_booking_test$");
        owner = new JdbcTemplate(dataSource(null));
        assertThat(owner.queryForObject("select current_database()", String.class)).isEqualTo("bodeul_guardian_booking_test");
        clearFixtures();
        owner.update("""
                insert into bodeul.app_users (id, firebase_uid, role, name, phone, email) values
                (?, ?, 'PATIENT', '테스트 환자', '01012345678', 'patient@example.test'),
                (?, ?, 'GUARDIAN', '테스트 보호자', '01055556666', 'guardian@example.test')
                on conflict (id) do nothing
                """, PATIENT.id(), PATIENT.firebaseUid(), GUARDIAN.id(), GUARDIAN.firebaseUid());
        DataSource runtime = dataSource("bodeul_core_runtime");
        JdbcTemplate jdbc = new JdbcTemplate(runtime);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(runtime);
        transaction = new TransactionTemplate(manager);
        transaction.setTimeout(15);
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("database");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("bodeul.guardian-booking.enabled", "true")));
        context.registerBean(DataSource.class, () -> runtime);
        context.registerBean(JdbcTemplate.class, () -> jdbc);
        context.registerBean(NamedParameterJdbcTemplate.class, () -> new NamedParameterJdbcTemplate(runtime));
        context.registerBean(JdbcClient.class, () -> JdbcClient.create(runtime));
        context.registerBean(DataSourceTransactionManager.class, () -> manager);
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(FirebaseAccountStatus.class, () -> uid -> true);
        context.registerBean(AppUserRepository.class, () -> new AppUserRepository() {
            public Optional<AppUser> findByFirebaseUid(String uid) { return Optional.empty(); }
            public Optional<AppUser> findById(UUID id) {
                return jdbc.query("select id, firebase_uid, role from bodeul.app_users where id = ?",
                        (rs, row) -> new AppUser(rs.getObject("id", UUID.class), rs.getString("firebase_uid"),
                                AppUserRole.valueOf(rs.getString("role"))), id).stream().findFirst();
            }
        });
        context.register(GuardianBookingPostgresConfiguration.class, BookingInventoryPostgresConfiguration.class,
                GuardianBookingService.class, JdbcAppointmentRepository.class, JdbcAppUserProfileRepository.class,
                AppointmentPublicCodeGenerator.class, DefaultAppointmentService.class);
        context.refresh();
        service = context.getBean(GuardianBookingService.class);
        approvals = context.getBean(GuardianBookingApprovalService.class);
        appointments = context.getBean(AppointmentService.class);
        command = GuardianBookingTestFixtures.command(UUID.randomUUID(), Instant.now().plusSeconds(172800));
    }

    @AfterEach
    void cleanUp() {
        if (context != null) context.close();
        if (owner != null) clearFixtures();
    }

    @Test
    void approvalAndCreationUseRealRuntimePrivilegesWithoutSharingAccess() {
        ApprovalView grant = grant();
        var receipt = create(grant);
        assertThat(bookingCount()).isEqualTo(1);
        assertThat(owner.queryForObject("select requester_role from bodeul.appointment_requests where id = ?", String.class, receipt.appointmentId())).isEqualTo("GUARDIAN");
        assertThat(owner.queryForObject("select count(*) from bodeul.guardian_sharing_consents where appointment_request_id = ?", Integer.class, receipt.appointmentId())).isZero();
        assertThatThrownBy(() -> appointments.getAppointment(GUARDIAN, receipt.appointmentId())).isInstanceOf(AppointmentException.class);
        assertThat(appointments.getAppointment(PATIENT, receipt.appointmentId()).id()).isEqualTo(receipt.appointmentId());
        assertThatThrownBy(() -> appointments.createAppointment(GUARDIAN, command)).isInstanceOf(AppointmentException.class);
    }

    @Test
    void identicalConcurrentCreatesReturnOneReceipt() throws Exception {
        var grant = grant();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { await(start); return create(grant); });
            var second = executor.submit(() -> { await(start); return create(grant); });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(bookingCount()).isEqualTo(1);
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void rollbackRemovesActualAppointmentAndKeepsPriorApproval() {
        var grant = grant();
        transaction.executeWithoutResult(status -> {
            create(grant);
            status.setRollbackOnly();
        });
        assertThat(bookingCount()).isZero();
        assertThat(eventCount()).isEqualTo(1);
        assertThat(approvals.get(PATIENT, PATIENT.id(), GUARDIAN.id(), command.clientRequestId()).active()).isTrue();
    }

    @Test
    void createLockLastsUntilAppointmentTransactionCommits() throws Exception {
        var grant = grant();
        var inserted = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var creating = executor.submit(() -> transaction.execute(status -> {
                var receipt = create(grant);
                inserted.countDown();
                await(commit);
                return receipt;
            }));
            try {
                assertThat(inserted.await(5, TimeUnit.SECONDS)).isTrue();
                var revoking = executor.submit(() -> approvals.revoke(PATIENT, GUARDIAN.id(), command.clientRequestId(), 1));
                awaitBlockedWriter();
                assertThat(revoking.isDone()).isFalse();
                assertThat(bookingCount()).isZero();
                commit.countDown();
                assertThat(creating.get(10, TimeUnit.SECONDS)).isNotNull();
                assertThat(revoking.get(10, TimeUnit.SECONDS).active()).isFalse();
            } finally { commit.countDown(); }
        }
        assertThat(bookingCount()).isEqualTo(1);
        assertThat(eventCount()).isEqualTo(2);
    }

    @Test
    void creationWinningAgainstReapprovalRollsBackNewApprovalAndAudit() throws Exception {
        var grant = grant();
        var inserted = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var creating = executor.submit(() -> transaction.execute(status -> {
                var receipt = create(grant);
                inserted.countDown();
                await(commit);
                return receipt;
            }));
            try {
                assertThat(inserted.await(5, TimeUnit.SECONDS)).isTrue();
                var reapproving = executor.submit(() -> {
                    assertThatThrownBy(() -> service.grant(PATIENT, GUARDIAN.id(), command, 1, true))
                            .isInstanceOf(GuardianBookingException.class);
                    return true;
                });
                awaitBlockedWriter();
                commit.countDown();
                assertThat(creating.get(10, TimeUnit.SECONDS)).isNotNull();
                assertThat(reapproving.get(10, TimeUnit.SECONDS)).isTrue();
            } finally { commit.countDown(); }
        }
        assertThat(eventCount()).isEqualTo(1);
        assertThat(bookingCount()).isEqualTo(1);
        assertThat(approvals.get(PATIENT, PATIENT.id(), GUARDIAN.id(), command.clientRequestId()).version()).isEqualTo(1);
    }

    @Test
    void revocationCommittedFirstPreventsWaitingCreation() throws Exception {
        var grant = grant();
        var revoked = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var revoking = executor.submit(() -> transaction.execute(status -> {
                var view = approvals.revoke(PATIENT, GUARDIAN.id(), command.clientRequestId(), 1);
                revoked.countDown();
                await(commit);
                return view;
            }));
            try {
                assertThat(revoked.await(5, TimeUnit.SECONDS)).isTrue();
                var creating = executor.submit(() -> {
                    assertThatThrownBy(() -> create(grant)).isInstanceOf(GuardianBookingException.class);
                    return true;
                });
                awaitBlockedWriter();
                commit.countDown();
                assertThat(revoking.get(10, TimeUnit.SECONDS).active()).isFalse();
                assertThat(creating.get(10, TimeUnit.SECONDS)).isTrue();
            } finally { commit.countDown(); }
        }
        assertThat(bookingCount()).isZero();
    }

    @Test
    void changedBodyAndSupersededGrantCannotInsert() {
        var first = grant();
        var replacement = service.grant(PATIENT, GUARDIAN.id(), command, 1, true);
        assertThatThrownBy(() -> create(first)).isInstanceOf(GuardianBookingException.class);
        var changed = new AppointmentService.CreateAppointmentCommand(command.clientRequestId(),
                GuardianBookingTestFixtures.draft(Instant.now().plusSeconds(172800), "변경 병원", "ON_SITE"), AppointmentPricePolicy.VERSION, 40000);
        assertThatThrownBy(() -> service.create(GUARDIAN, PATIENT.id(), replacement.grantId(), replacement.version(), changed))
                .isInstanceOf(GuardianBookingException.class);
        assertThat(bookingCount()).isZero();
    }

    @Test
    void retryAfterRevocationDoesNotCreateAgainOrOpenDetails() {
        var grant = grant();
        var receipt = create(grant);
        approvals.revoke(PATIENT, GUARDIAN.id(), command.clientRequestId(), 1);
        assertThat(create(grant)).isEqualTo(receipt);
        assertThat(bookingCount()).isEqualTo(1);
        assertThatThrownBy(() -> appointments.getAppointment(GUARDIAN, receipt.appointmentId())).isInstanceOf(AppointmentException.class);
    }

    @Test
    void creationGuardRequiresAmbientTransaction() {
        var grant = grant();
        assertThatThrownBy(() -> approvals.lockForCreation(GUARDIAN, PATIENT.id(), command.clientRequestId(), grant.grantId(), 1, "a".repeat(64)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void inventoryIncludesBothParticipantsAndRetainedRevocationAudit() {
        grant();
        for (var user : new AppUser[]{PATIENT, GUARDIAN}) {
            var counts = BookingInventoryPostgresConfiguration.completeCounts(context, user.id(), user.firebaseUid());
            assertThat(counts).containsEntry("guardianBookingApprovals", 1L)
                    .containsEntry("activeGuardianBookingApprovals", 1L).containsEntry("guardianBookingApprovalAudits", 1L);
        }
        approvals.revoke(PATIENT, GUARDIAN.id(), command.clientRequestId(), 1);
        var after = BookingInventoryPostgresConfiguration.completeCounts(context, PATIENT.id(), PATIENT.firebaseUid());
        assertThat(after).containsEntry("guardianBookingApprovals", 1L)
                .containsEntry("activeGuardianBookingApprovals", 0L).containsEntry("guardianBookingApprovalAudits", 2L);
        assertThat(BookingInventoryPostgresConfiguration.completeCounts(context, UUID.randomUUID(), "unrelated"))
                .containsEntry("guardianBookingApprovals", 0L).containsEntry("guardianBookingApprovalAudits", 0L);
    }

    private ApprovalView grant() { return service.grant(PATIENT, GUARDIAN.id(), command, 0, true); }
    private GuardianBookingService.Receipt create(ApprovalView grant) {
        return service.create(GUARDIAN, PATIENT.id(), grant.grantId(), grant.version(), command);
    }
    private int bookingCount() { return owner.queryForObject("select count(*) from bodeul.appointment_requests where requester_user_id = ?", Integer.class, GUARDIAN.id()); }
    private int eventCount() { return owner.queryForObject("select count(*) from bodeul.guardian_booking_approval_events where patient_user_id = ?", Integer.class, PATIENT.id()); }
    private void clearFixtures() {
        owner.update("delete from bodeul.guardian_booking_approval_events where patient_user_id = ?", PATIENT.id());
        owner.update("delete from bodeul.guardian_booking_approvals where patient_user_id = ?", PATIENT.id());
        owner.update("delete from bodeul.appointment_requests where requester_user_id = ?", GUARDIAN.id());
    }
    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("트랜잭션 검증 대기 시간이 초과되었습니다.");
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
    private void awaitBlockedWriter() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (owner.queryForObject("""
                    select count(*) from pg_stat_activity where datname = current_database()
                    and application_name = 'guardian-booking-api-test' and wait_event_type = 'Lock'
                    """, Integer.class) == 0) Thread.sleep(10);
        });
    }
    private DriverManagerDataSource dataSource(String role) {
        var dataSource = new DriverManagerDataSource(System.getenv("BOOKING_TEST_DB_URL"),
                System.getenv().getOrDefault("BOOKING_TEST_DB_USER", "postgres"), System.getenv("BOOKING_TEST_DB_PASSWORD"));
        Properties properties = new Properties();
        properties.setProperty("ApplicationName", "guardian-booking-api-test");
        properties.setProperty("options", "-c statement_timeout=15000 -c lock_timeout=10000" + (role == null ? "" : " -c role=" + role));
        dataSource.setConnectionProperties(properties);
        return dataSource;
    }
}
