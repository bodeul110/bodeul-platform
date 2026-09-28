package com.bodeul.core.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.appointment.AppUserProfileRepository.AppUserProfile;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentMutation;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentRecord;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentFollowUpMutation;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentFollowUpRecord;
import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope;
import com.bodeul.core.consent.GuardianSharingConsentAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAppointmentServiceTests {

    private static final UUID PATIENT_ID = UUID.fromString("db7cc9f9-4f3e-4f73-b572-cf653564e887");
    private static final UUID GUARDIAN_ID = UUID.fromString("bfbc7b03-3f42-4016-85c0-0981097bf1f2");
    private static final UUID SECOND_GUARDIAN_ID = UUID.fromString("1f80557d-bfb6-43ad-81d5-9c7532754ab8");
    private static final UUID MANAGER_ID = UUID.fromString("04e9b7fd-9727-4f81-af7b-ab3534339fd0");
    private static final UUID OTHER_PATIENT_ID = UUID.fromString("ced5cb21-c07d-4d0e-a151-2994b6d40793");
    private static final UUID APPOINTMENT_ID = UUID.fromString("27bf3a07-6605-48ab-adbf-c7b18551a639");
    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");

    private FakeAppointmentRepository appointmentRepository;
    private FakeAppUserProfileRepository profileRepository;
    private DefaultAppointmentService service;

    @BeforeEach
    void setUp() {
        appointmentRepository = new FakeAppointmentRepository();
        profileRepository = new FakeAppUserProfileRepository();
        profileRepository.add(new AppUserProfile(
                PATIENT_ID,
                AppUserRole.PATIENT,
                "환자 사용자",
                "patient@example.com",
                "010-1234-5678"));
        profileRepository.add(new AppUserProfile(
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                "보호자 사용자",
                "guardian@example.com",
                "010-9876-5432"));
        profileRepository.add(new AppUserProfile(
                SECOND_GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                "다른 보호자",
                "other-guardian@example.com",
                "010-2222-3333"));
        profileRepository.add(new AppUserProfile(
                MANAGER_ID,
                AppUserRole.MANAGER,
                "매니저 사용자",
                "manager@example.com",
                "010-5555-7777"));
        service = new DefaultAppointmentService(
                appointmentRepository,
                profileRepository,
                (appUser, appointmentId, patientUserId, guardianUserId, scope) -> true,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void patientCreatesAppointmentWithServerOwnedPriceAndLinkedGuardian() {
        var created = service.createAppointment(
                patient(),
                confirmedCreate(
                        UUID.fromString("7e80b784-1212-429e-b4ea-a5f9e8db7488"),
                        draft()));

        assertThat(created.id()).isEqualTo(APPOINTMENT_ID);
        assertThat(created.patientUserId()).isEqualTo(PATIENT_ID);
        assertThat(created.guardianUserId()).isEqualTo(GUARDIAN_ID);
        assertThat(created.patientName()).isEqualTo("환자 사용자");
        assertThat(created.guardianName()).isEqualTo("보호자 사용자");
        assertThat(created.basePrice()).isEqualTo(40_000);
        assertThat(created.optionSurchargePrice()).isZero();
        assertThat(created.couponDiscountPrice()).isZero();
        assertThat(created.finalPrice()).isEqualTo(40_000);
        assertThat(created.paymentStatusCode()).isEqualTo("PENDING");
        assertThat(created.status()).isEqualTo("REQUESTED");
        assertThat(created.publicCode()).matches("^BD-[A-Z0-9]{6}$");
    }

    @Test
    void newBookingsKeepTheSamePriceForEveryMobilityAndTripSelection() {
        for (String mobility : List.of("INDEPENDENT", "WALKING_AID", "WHEELCHAIR")) {
            for (String trip : List.of("ONE_WAY", "ROUND_TRIP")) {
                var created = service.createAppointment(patient(),
                        confirmedCreate(UUID.randomUUID(),
                                draftWithOptions(draft(), mobility, trip, "NONE")));
                assertThat(created.finalPrice()).isEqualTo(40_000);
                assertThat(created.optionSurchargePrice()).isZero();
                assertThat(created.couponDiscountPrice()).isZero();
            }
        }
    }

    @Test
    void oldAppCannotCreateWithoutPriceConfirmation() {
        assertThatThrownBy(() -> service.createAppointment(patient(),
                new AppointmentService.CreateAppointmentCommand(UUID.randomUUID(), draft())))
                .isInstanceOfSatisfying(AppointmentException.class, failure -> {
                    assertThat(failure.status().value()).isEqualTo(409);
                    assertThat(failure.error()).isEqualTo("appointment_price_confirmation_required");
                });
        assertThat(appointmentRepository.insertCount).isZero();
        assertThat(profileRepository.lookupCount).isZero();
    }

    @Test
    void wrongOrIncompletePriceConfirmationDoesNotCreateAnAppointment() {
        for (String version : new String[]{null, "", "mvp-fixed-40000-v0", "mvp-fixed-40000-v1"}) {
            for (Integer amount : new Integer[]{null, -1, 0, 39_999, 40_000, 69_000, 96_000}) {
                if ("mvp-fixed-40000-v1".equals(version) && Integer.valueOf(40_000).equals(amount)) {
                    continue;
                }
                assertThatThrownBy(() -> service.createAppointment(patient(),
                        new AppointmentService.CreateAppointmentCommand(
                                UUID.randomUUID(), draft(), version, amount)))
                        .isInstanceOfSatisfying(AppointmentException.class, failure ->
                                assertThat(failure.error()).isEqualTo("appointment_price_confirmation_required"));
            }
        }
        assertThat(appointmentRepository.insertCount).isZero();
        assertThat(profileRepository.lookupCount).isZero();
    }

    @Test
    void oldAppRetryOfAlreadySavedNewPriceDoesNotInsertAgain() {
        UUID requestId = UUID.randomUUID();
        var created = service.createAppointment(patient(), confirmedCreate(requestId, draft()));

        var retried = service.createAppointment(patient(),
                new AppointmentService.CreateAppointmentCommand(requestId, draft()));

        assertThat(retried.id()).isEqualTo(created.id());
        assertThat(retried.finalPrice()).isEqualTo(40_000);
        assertThat(appointmentRepository.insertCount).isEqualTo(1);
    }

    @Test
    void newBookingRejectsLegacyCouponsInsteadOfSilentlyDiscounting() {
        for (String coupon : List.of("FIRST_VISIT", "FAMILY")) {
            assertThatThrownBy(() -> service.createAppointment(patient(),
                    confirmedCreate(UUID.randomUUID(),
                            draftWithOptions(draft(), "INDEPENDENT", "ONE_WAY", coupon))))
                    .isInstanceOf(AppointmentException.class)
                    .hasMessage("현재 신규 예약에는 쿠폰을 적용할 수 없습니다.");
        }
        assertThat(appointmentRepository.insertCount).isZero();
    }

    @Test
    void legacyAppointmentEditKeepsItsOriginalPriceAndCoupon() {
        appointmentRepository.current = Optional.of(
                existingAppointment("REQUESTED", 0, null, "CARD", "FAMILY"));
        var updated = service.updateAppointment(patient(), APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentCommand(0,
                        draftWithOptions(draftWithMeetingPlace("별관 2층"),
                                "INDEPENDENT", "ONE_WAY", "FAMILY")));
        assertThat(updated.basePrice()).isEqualTo(69_000);
        assertThat(updated.optionSurchargePrice()).isEqualTo(37_000);
        assertThat(updated.couponDiscountPrice()).isEqualTo(10_000);
        assertThat(updated.finalPrice()).isEqualTo(96_000);
        assertThat(updated.couponCode()).isEqualTo("FAMILY");
        assertThat(updated.meetingPlace()).isEqualTo("별관 2층");
    }

    @Test
    void exactCreateRetryReturnsSavedLegacyPriceInsteadOfNewTariff() {
        UUID clientRequestId = UUID.randomUUID();
        var command = confirmedCreate(clientRequestId, draft());
        service.createAppointment(patient(), command);
        appointmentRepository.putLegacyClientRequest(clientRequestId,
                existingAppointment("REQUESTED", 0));

        var retried = service.createAppointment(patient(),
                new AppointmentService.CreateAppointmentCommand(clientRequestId, draft()));

        assertThat(retried.finalPrice()).isEqualTo(69_000);
        assertThat(appointmentRepository.insertCount).isEqualTo(1);
    }

    @Test
    void legacyCouponCreateRetryRemainsValidAfterNewCouponsAreDisabled() {
        UUID clientRequestId = UUID.randomUUID();
        var legacyDraft = draftWithOptions(draft(), "WHEELCHAIR", "ROUND_TRIP", "FAMILY");
        appointmentRepository.putLegacyClientRequest(clientRequestId,
                existingAppointment("REQUESTED", 0, null, "CARD", "FAMILY"));
        // 이전 계약으로 정규화·저장된 지문을 사용한다. 신규 생성은 호출하지 않는다.
        appointmentRepository.createRequestFingerprints.put(APPOINTMENT_ID,
                AppointmentCreateFingerprint.from(new AppointmentCreateFingerprint.CreateRequest(
                        PATIENT_ID, AppUserRole.PATIENT, clientRequestId,
                        legacyDraft.linkedParticipantName(), "010-9876-5432",
                        "guardian@example.com", legacyDraft.patientConditionSummary(),
                        legacyDraft.medicationSummary(), legacyDraft.hospitalName(), legacyDraft.departmentName(),
                        legacyDraft.hospitalLatitude(), legacyDraft.hospitalLongitude(),
                        Instant.parse("2026-12-20T01:30:00Z"), legacyDraft.meetingPlace(),
                        legacyDraft.specialNotes(), "WHEELCHAIR", "ROUND_TRIP", "ANY", "CARD", "FAMILY")));

        var retried = service.createAppointment(patient(),
                new AppointmentService.CreateAppointmentCommand(clientRequestId, legacyDraft));

        assertThat(retried.finalPrice()).isEqualTo(96_000);
        assertThat(retried.couponCode()).isEqualTo("FAMILY");
        assertThat(appointmentRepository.insertCount).isZero();
    }

    private static AppointmentService.CreateAppointmentCommand confirmedCreate(
            UUID clientRequestId, AppointmentService.AppointmentDraft draft) {
        return new AppointmentService.CreateAppointmentCommand(
                clientRequestId, draft, "mvp-fixed-40000-v1", 40_000);
    }

    @Test
    void legacyBankTransferEditKeepsPriceWhileTermChangesAreRejected() {
        appointmentRepository.current = Optional.of(
                existingAppointment("REQUESTED", 0, null, "BANK_TRANSFER", "NONE"));
        var unchangedTerms = draftWithOptions(draftWithPaymentMethod("BANK_TRANSFER"),
                "INDEPENDENT", "ONE_WAY", "NONE");
        var updated = service.updateAppointment(patient(), APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentCommand(0, unchangedTerms));
        assertThat(updated.finalPrice()).isEqualTo(69_000);
        assertThat(updated.paymentMethodCode()).isEqualTo("BANK_TRANSFER");
        assertThatThrownBy(() -> service.updateAppointment(patient(), APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentCommand(updated.version(),
                        draftWithOptions(unchangedTerms, "WHEELCHAIR", "ONE_WAY", "NONE"))))
                .isInstanceOf(AppointmentException.class)
                .hasMessage("무통장입금 예약의 결제수단·이동 조건·쿠폰·입금액은 생성 후 변경할 수 없습니다.");
        assertThat(appointmentRepository.current.orElseThrow().finalPrice()).isEqualTo(69_000);
    }

    @Test
    void existingAppointmentCannotApplyAnotherCoupon() {
        appointmentRepository.current = Optional.of(existingAppointment("REQUESTED", 0));
        assertThatThrownBy(() -> service.updateAppointment(patient(), APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentCommand(0,
                        draftWithOptions(draft(), "INDEPENDENT", "ONE_WAY", "FIRST_VISIT"))))
                .isInstanceOf(AppointmentException.class)
                .hasMessage("접수된 예약의 쿠폰은 변경할 수 없습니다.");
    }

    @Test
    void publicCodeCollisionIsRetriedWithANewCode() {
        var codes = new ArrayDeque<>(List.of("BD-COLLID", "BD-UNIQUE"));
        appointmentRepository.publicCodeCollisionsRemaining = 1;
        service = new DefaultAppointmentService(
                appointmentRepository,
                profileRepository,
                (appUser, appointmentId, patientUserId, guardianUserId, scope) -> true,
                Clock.fixed(NOW, ZoneOffset.UTC),
                codes::removeFirst);

        var created = service.createAppointment(
                patient(),
                confirmedCreate(UUID.randomUUID(), draft()));

        assertThat(created.publicCode()).isEqualTo("BD-UNIQUE");
        assertThat(appointmentRepository.insertCount).isEqualTo(2);
    }

    @Test
    void repeatedClientRequestIdReturnsTheExistingAppointment() {
        UUID clientRequestId = UUID.fromString("c521b77c-2655-4604-9883-c92bc4d828f7");
        var command = confirmedCreate(clientRequestId, draft());

        var first = service.createAppointment(patient(), command);
        var second = service.createAppointment(patient(), command);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(appointmentRepository.insertCount).isEqualTo(1);
    }

    @Test
    void originalCreateRetryStillMatchesAfterAppointmentEdit() {
        UUID clientRequestId = UUID.fromString("61328893-eacb-4b0c-a716-8b95fbe253ee");
        var original = confirmedCreate(clientRequestId, draft());
        var created = service.createAppointment(patient(), original);

        service.updateAppointment(
                patient(),
                created.id(),
                new AppointmentService.UpdateAppointmentCommand(
                        created.version(),
                        draftWithMeetingPlace("별관 2층")));

        var retried = service.createAppointment(patient(), original);

        assertThat(retried.id()).isEqualTo(created.id());
        assertThat(retried.meetingPlace()).isEqualTo("별관 2층");
        assertThat(appointmentRepository.insertCount).isEqualTo(1);
    }

    @Test
    void exactCreateRetryDoesNotResolveChangedProfilesAgain() {
        UUID clientRequestId = UUID.fromString("94ecb4f3-7a6c-4458-af68-8ec78a984d45");
        var command = confirmedCreate(clientRequestId, draft());
        var created = service.createAppointment(patient(), command);
        int lookupCountAfterCreate = profileRepository.lookupCount;
        profileRepository.add(new AppUserProfile(
                PATIENT_ID,
                AppUserRole.PATIENT,
                "변경된 환자",
                "changed-patient@example.com",
                "010-0000-0001"));
        profileRepository.add(new AppUserProfile(
                GUARDIAN_ID,
                AppUserRole.GUARDIAN,
                "변경된 보호자",
                "changed-guardian@example.com",
                "010-0000-0002"));

        var retried = service.createAppointment(patient(), command);

        assertThat(retried.id()).isEqualTo(created.id());
        assertThat(retried.patientName()).isEqualTo("환자 사용자");
        assertThat(retried.guardianName()).isEqualTo("보호자 사용자");
        assertThat(profileRepository.lookupCount).isEqualTo(lookupCountAfterCreate);
        assertThat(appointmentRepository.insertCount).isEqualTo(1);
    }

    @Test
    void exactCreateRetryAfterAppointmentTimeReturnsExisting() {
        UUID clientRequestId = UUID.fromString("de0e43fb-7333-4f7c-8c05-a72eff342faf");
        var command = confirmedCreate(clientRequestId, draft());
        var created = service.createAppointment(patient(), command);
        var laterService = new DefaultAppointmentService(
                appointmentRepository,
                profileRepository,
                (appUser, appointmentId, patientUserId, guardianUserId, scope) -> true,
                Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC));

        var retried = laterService.createAppointment(patient(), command);

        assertThat(retried.id()).isEqualTo(created.id());
        assertThat(appointmentRepository.insertCount).isEqualTo(1);
    }

    @Test
    void editedValuesCannotReuseTheOriginalCreateRequestId() {
        UUID clientRequestId = UUID.fromString("81434b49-5475-4437-80bf-df60269981ef");
        var created = service.createAppointment(
                patient(),
                confirmedCreate(clientRequestId, draft()));
        AppointmentService.AppointmentDraft editedDraft = draftWithMeetingPlace("별관 2층");
        service.updateAppointment(
                patient(),
                created.id(),
                new AppointmentService.UpdateAppointmentCommand(created.version(), editedDraft));

        assertThatThrownBy(() -> service.createAppointment(
                patient(),
                confirmedCreate(clientRequestId, editedDraft)))
                .isInstanceOf(AppointmentException.class)
                .hasMessage("같은 clientRequestId를 다른 예약 내용으로 다시 사용할 수 없습니다.");
    }

    @Test
    void legacyCreateWithoutFingerprintFailsClosed() {
        UUID clientRequestId = UUID.fromString("d4c8eacf-c830-4568-aabb-4dc19696a540");
        appointmentRepository.putLegacyClientRequest(
                clientRequestId,
                existingAppointment("REQUESTED", 0));

        assertThatThrownBy(() -> service.createAppointment(
                patient(),
                confirmedCreate(clientRequestId, draft())))
                .isInstanceOf(AppointmentException.class)
                .hasMessage("같은 clientRequestId를 다른 예약 내용으로 다시 사용할 수 없습니다.");
    }

    @Test
    void bankTransferAppointmentStartsInAwaitingDeposit() {
        var created = service.createAppointment(
                patient(),
                confirmedCreate(
                        UUID.randomUUID(),
                        draftWithPaymentMethod("BANK_TRANSFER")));

        assertThat(created.paymentMethodCode()).isEqualTo("BANK_TRANSFER");
        assertThat(created.paymentStatusCode()).isEqualTo("AWAITING_DEPOSIT");
    }

    @Test
    void repeatedClientRequestIdRejectsDifferentPaymentMethod() {
        UUID clientRequestId = UUID.fromString("ecda4a53-fc5f-4128-98c6-6030acb19b08");
        service.createAppointment(
                patient(),
                confirmedCreate(clientRequestId, draft()));

        assertThatThrownBy(() -> service.createAppointment(
                patient(),
                confirmedCreate(
                        clientRequestId,
                        draftWithPaymentMethod("BANK_TRANSFER"))))
                .isInstanceOf(AppointmentException.class)
                .hasMessage("같은 clientRequestId를 다른 예약 내용으로 다시 사용할 수 없습니다.");
    }

    @Test
    void repeatedClientRequestIdRejectsDifferentResolvedGuardian() {
        UUID clientRequestId = UUID.fromString("193833c4-709a-487d-8310-32cab93888ce");
        service.createAppointment(
                patient(),
                confirmedCreate(clientRequestId, draft()));

        assertThatThrownBy(() -> service.createAppointment(
                patient(),
                confirmedCreate(
                        clientRequestId,
                        draftWithLinkedGuardian(
                                "다른 보호자",
                                "010-2222-3333",
                                "other-guardian@example.com"))))
                .isInstanceOf(AppointmentException.class)
                .hasMessage("같은 clientRequestId를 다른 예약 내용으로 다시 사용할 수 없습니다.");
    }

    @Test
    void bankTransferPaymentMethodCannotChangeDuringAppointmentEdit() {
        var created = service.createAppointment(
                patient(),
                confirmedCreate(
                        UUID.randomUUID(),
                        draftWithPaymentMethod("BANK_TRANSFER")));

        assertThatThrownBy(() -> service.updateAppointment(
                patient(),
                created.id(),
                new AppointmentService.UpdateAppointmentCommand(created.version(), draft())))
                .isInstanceOf(AppointmentException.class)
                .hasMessage("무통장입금 예약의 결제수단·이동 조건·쿠폰·입금액은 생성 후 변경할 수 없습니다.");
    }

    @Test
    void managerCanReadAssignedAppointmentList() {
        var manager = new AppUserRepository.AppUser(
                UUID.randomUUID(),
                "manager-firebase-uid",
                AppUserRole.MANAGER);

        assertThat(service.getMyAppointments(manager)).isEmpty();
    }

    @Test
    void assignedManagerProfileIsIncludedInAppointmentView() {
        appointmentRepository.current = Optional.of(existingAppointment("MATCHED", 1, MANAGER_ID));

        var appointment = service.getAppointment(patient(), APPOINTMENT_ID);

        assertThat(appointment.publicCode()).isEqualTo("BD-LEGACY");
        assertThat(appointment.managerUserId()).isEqualTo(MANAGER_ID);
        assertThat(appointment.managerName()).isEqualTo("매니저 사용자");
        assertThat(appointment.managerEmail()).isEqualTo("manager@example.com");
        assertThat(appointment.managerPhone()).isEqualTo("010-5555-7777");
    }

    @Test
    void assignedManagerCanReadCareDetailsBeforeCareEnds() {
        appointmentRepository.current = Optional.of(existingAppointment("IN_PROGRESS", 1, MANAGER_ID));

        var appointment = service.getAppointment(manager(), APPOINTMENT_ID);

        assertThat(appointment.specialNotes()).isEqualTo("진료 20분 전 도착");
        assertThat(appointment.patientConditionSummary()).isEqualTo("휠체어 이동 지원 필요");
        assertThat(appointment.medicationSummary()).isEqualTo("아침 약 복용");
        assertThat(appointment.mobilitySupportCode()).isEqualTo("INDEPENDENT");
    }

    @Test
    void assignedManagerCannotReadCareDetailsAfterCareBoundary() {
        appointmentRepository.current = Optional.of(existingAppointment("IN_PROGRESS", 1, MANAGER_ID));
        appointmentRepository.careEnded = true;

        var appointment = service.getAppointment(manager(), APPOINTMENT_ID);

        assertThat(appointment.specialNotes()).isEmpty();
        assertThat(appointment.patientConditionSummary()).isEmpty();
        assertThat(appointment.medicationSummary()).isEmpty();
        assertThat(appointment.mobilitySupportCode()).isEmpty();
        assertThat(appointment.patientName()).isEqualTo("환자 사용자");
        assertThat(appointment.hospitalName()).isEqualTo("서울대학교병원");
        assertThat(appointment.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void patientRetainsCareDetailsAfterCareBoundary() {
        appointmentRepository.current = Optional.of(existingAppointment("IN_PROGRESS", 1, MANAGER_ID));
        appointmentRepository.careEnded = true;

        var appointment = service.getAppointment(patient(), APPOINTMENT_ID);

        assertThat(appointment.specialNotes()).isEqualTo("진료 20분 전 도착");
        assertThat(appointment.patientConditionSummary()).isEqualTo("휠체어 이동 지원 필요");
        assertThat(appointment.medicationSummary()).isEqualTo("아침 약 복용");
        assertThat(appointment.mobilitySupportCode()).isEqualTo("INDEPENDENT");
    }

    @Test
    void linkedParticipantCannotReadRequesterPublicCode() {
        appointmentRepository.current = Optional.of(existingAppointment("REQUESTED", 0));

        var appointment = service.getAppointment(guardian(), APPOINTMENT_ID);
        var listedAppointment = service.getMyAppointments(guardian()).getFirst();

        assertThat(appointment.publicCode()).isEmpty();
        assertThat(listedAppointment.publicCode()).isEmpty();
    }

    @Test
    void assignedManagerCanReadPublicCode() {
        appointmentRepository.current = Optional.of(existingAppointment("MATCHED", 1, MANAGER_ID));

        var appointment = service.getAppointment(manager(), APPOINTMENT_ID);

        assertThat(appointment.publicCode()).isEqualTo("BD-LEGACY");
    }

    @Test
    void unassignedManagerCannotReadAppointmentOrPublicCode() {
        appointmentRepository.current = Optional.of(existingAppointment("MATCHED", 1, MANAGER_ID));
        var unassignedManager = new AppUserRepository.AppUser(
                UUID.randomUUID(),
                "unassigned-manager-firebase-uid",
                AppUserRole.MANAGER);

        assertThatThrownBy(() -> service.getAppointment(unassignedManager, APPOINTMENT_ID))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_permission_denied");
    }

    @Test
    void managerCannotUsePatientAppointmentWriteApi() {
        var manager = new AppUserRepository.AppUser(
                UUID.randomUUID(),
                "manager-firebase-uid",
                AppUserRole.MANAGER);

        assertThatThrownBy(() -> service.createAppointment(
                manager,
                confirmedCreate(UUID.randomUUID(), draft())))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_role_not_supported");
    }

    @Test
    void guardianCannotCreateAppointmentOrResolvePatientProfile() {
        var guardian = new AppUserRepository.AppUser(
                GUARDIAN_ID,
                "guardian-firebase-uid",
                AppUserRole.GUARDIAN);

        assertThatThrownBy(() -> service.createAppointment(
                guardian,
                confirmedCreate(UUID.randomUUID(), draft())))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("guardian_appointment_creation_not_supported");
        assertThat(appointmentRepository.insertCount).isZero();
        assertThat(profileRepository.lookupCount).isZero();
    }

    @Test
    void guardianAppointmentViewContainsOnlyScheduleAndHospitalFields() {
        appointmentRepository.current = Optional.of(existingAppointment("REQUESTED", 0));
        var guardian = new AppUserRepository.AppUser(
                GUARDIAN_ID,
                "guardian-firebase-uid",
                AppUserRole.GUARDIAN);

        var view = service.getAppointment(guardian, APPOINTMENT_ID);

        assertThat(view.hospitalName()).isEqualTo("서울대학교병원");
        assertThat(view.departmentName()).isEqualTo("내과");
        assertThat(view.appointmentAt()).isNotBlank();
        assertThat(view.meetingPlace()).isEmpty();
        assertThat(view.status()).isEqualTo("REQUESTED");
        assertThat(view.legacyFirestoreId()).isEmpty();
        assertThat(view.patientUserId()).isNull();
        assertThat(view.guardianUserId()).isNull();
        assertThat(view.managerUserId()).isNull();
        assertThat(view.patientName()).isEmpty();
        assertThat(view.patientPhone()).isEmpty();
        assertThat(view.patientEmail()).isEmpty();
        assertThat(view.guardianName()).isEmpty();
        assertThat(view.managerName()).isEmpty();
        assertThat(view.specialNotes()).isEmpty();
        assertThat(view.patientConditionSummary()).isEmpty();
        assertThat(view.medicationSummary()).isEmpty();
        assertThat(view.paymentMethodCode()).isEmpty();
        assertThat(view.couponCode()).isEmpty();
        assertThat(view.paymentApprovalCode()).isEmpty();
        assertThat(view.finalPrice()).isZero();
        assertThat(profileRepository.lookupCount).isZero();
    }

    @Test
    void guardianCannotUpdateAppointmentOrResolveProfiles() {
        var guardian = new AppUserRepository.AppUser(
                GUARDIAN_ID,
                "guardian-firebase-uid",
                AppUserRole.GUARDIAN);

        assertThatThrownBy(() -> service.updateAppointment(
                guardian,
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentCommand(0, draft())))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("guardian_appointment_mutation_not_supported");
        assertThat(profileRepository.lookupCount).isZero();
    }

    @Test
    void participantFromAnotherAppointmentIsRejected() {
        appointmentRepository.current = Optional.of(existingAppointment("REQUESTED", 0));
        var otherPatient = new AppUserRepository.AppUser(
                OTHER_PATIENT_ID,
                "other-patient",
                AppUserRole.PATIENT);

        assertThatThrownBy(() -> service.getAppointment(otherPatient, APPOINTMENT_ID))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_permission_denied");
    }

    @Test
    void guardianRelationshipWithoutExplicitConsentIsRejected() {
        appointmentRepository.current = Optional.of(existingAppointment("REQUESTED", 0));
        var guardian = new AppUserRepository.AppUser(
                GUARDIAN_ID,
                "guardian-firebase-uid",
                AppUserRole.GUARDIAN);
        var failClosedService = new DefaultAppointmentService(
                appointmentRepository,
                profileRepository,
                (appUser, appointmentId, patientUserId, guardianUserId, scope) -> false,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> failClosedService.getAppointment(guardian, APPOINTMENT_ID))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_permission_denied");
    }

    @Test
    void staleVersionIsRejectedBeforeUpdate() {
        appointmentRepository.current = Optional.of(existingAppointment("REQUESTED", 3));

        assertThatThrownBy(() -> service.updateAppointment(
                patient(),
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentCommand(2, draft())))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_version_conflict");
    }

    @Test
    void guardianUpdateIsRejectedBeforeParticipantRelinking() {
        appointmentRepository.current = Optional.of(existingAppointment("REQUESTED", 0));
        var guardian = new AppUserRepository.AppUser(
                GUARDIAN_ID,
                "guardian-firebase-uid",
                AppUserRole.GUARDIAN);
        AppointmentService.AppointmentDraft differentPatient = new AppointmentService.AppointmentDraft(
                "다른 환자",
                "010-1111-2222",
                "different@example.com",
                "보행 지원 필요",
                "",
                "서울대학교병원",
                "내과",
                37.5796,
                126.9990,
                "2026-12-20 10:30",
                "본관 1층",
                "",
                "WALKING_AID",
                "ONE_WAY",
                "ANY",
                "CARD",
                "NONE");

        assertThatThrownBy(() -> service.updateAppointment(
                guardian,
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentCommand(0, differentPatient)))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("guardian_appointment_mutation_not_supported");
    }

    @Test
    void guardianCannotCancelAppointmentEvenWithAppointmentScope() {
        appointmentRepository.current = Optional.of(existingAppointment("MATCHED", 1));
        RecordingConsentAccess consentAccess = new RecordingConsentAccess();
        var guardianService = new DefaultAppointmentService(
                appointmentRepository,
                profileRepository,
                consentAccess,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var guardian = new AppUserRepository.AppUser(
                GUARDIAN_ID,
                "guardian-firebase-uid",
                AppUserRole.GUARDIAN);

        assertThatThrownBy(() -> guardianService.cancelAppointment(guardian, APPOINTMENT_ID, 1))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("guardian_appointment_mutation_not_supported");
        assertThat(appointmentRepository.current.orElseThrow().status()).isEqualTo("MATCHED");
        assertThat(appointmentRepository.sessionCanceled).isFalse();
        assertThat(consentAccess.finalizedAppointmentId).isNull();
    }

    @Test
    void matchedAppointmentCancellationAlsoCancelsTheActiveSession() {
        appointmentRepository.current = Optional.of(existingAppointment("MATCHED", 1));

        var canceled = service.cancelAppointment(patient(), APPOINTMENT_ID, 1);

        assertThat(canceled.status()).isEqualTo("CANCELED");
        assertThat(appointmentRepository.sessionCanceled).isTrue();
    }

    @Test
    void requestedAppointmentCanBeCanceled() {
        appointmentRepository.current = Optional.of(existingAppointment("REQUESTED", 4));
        RecordingConsentAccess consentAccess = new RecordingConsentAccess();
        var cancellationService = new DefaultAppointmentService(
                appointmentRepository,
                profileRepository,
                consentAccess,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var canceled = cancellationService.cancelAppointment(patient(), APPOINTMENT_ID, 4);

        assertThat(canceled.status()).isEqualTo("CANCELED");
        assertThat(canceled.version()).isEqualTo(5);
        assertThat(consentAccess.finalizedAppointmentId).isEqualTo(APPOINTMENT_ID);
        assertThat(consentAccess.careEndedAt).isEqualTo(NOW);
    }

    @Test
    void profileBackfillIsRequiredBeforeCreatingAppointments() {
        profileRepository.profiles.put(PATIENT_ID, new AppUserProfile(
                PATIENT_ID,
                AppUserRole.PATIENT,
                "",
                "",
                ""));

        assertThatThrownBy(() -> service.createAppointment(
                patient(),
                confirmedCreate(UUID.randomUUID(), draft())))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_profile_not_ready");
    }

    @Test
    void appointmentTimeMustBeInTheFuture() {
        AppointmentService.AppointmentDraft pastDraft = new AppointmentService.AppointmentDraft(
                "보호자 사용자",
                "010-9876-5432",
                "guardian@example.com",
                "",
                "",
                "서울대학교병원",
                "내과",
                37.5796,
                126.9990,
                "2026-07-17 10:30",
                "본관 1층",
                "",
                "INDEPENDENT",
                "ONE_WAY",
                "ANY",
                "CARD",
                "NONE");

        assertThatThrownBy(() -> service.createAppointment(
                patient(),
                confirmedCreate(UUID.randomUUID(), pastDraft)))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("invalid_appointment_request");
    }

    @Test
    void participantReadsEmptyFollowUpBeforeFirstSave() {
        appointmentRepository.current = Optional.of(existingAppointment("COMPLETED", 3));

        var followUp = service.getAppointmentFollowUp(patient(), APPOINTMENT_ID);

        assertThat(followUp.appointmentRequestId()).isEqualTo(APPOINTMENT_ID);
        assertThat(followUp.reviewRatingCode()).isEmpty();
        assertThat(followUp.version()).isZero();
    }

    @Test
    void guardianNeedsReportScopeInAdditionToAppointmentForFollowUp() {
        appointmentRepository.current = Optional.of(existingAppointment("COMPLETED", 3));
        var appointmentOnlyService = new DefaultAppointmentService(
                appointmentRepository,
                profileRepository,
                (appUser, appointmentId, patientUserId, guardianUserId, scope) ->
                        scope == com.bodeul.core.consent.AdultPatientGuardianSharingPolicy
                                .InformationScope.APPOINTMENT,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var guardian = new AppUserRepository.AppUser(
                GUARDIAN_ID,
                "guardian-firebase-uid",
                AppUserRole.GUARDIAN);

        assertThatThrownBy(() -> appointmentOnlyService.getAppointmentFollowUp(
                guardian,
                APPOINTMENT_ID))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_permission_denied");
    }

    @Test
    void participantSavesFollowUpActionsWithOptimisticVersion() {
        appointmentRepository.current = Optional.of(existingAppointment("COMPLETED", 3));

        var review = service.updateAppointmentFollowUp(
                patient(),
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentFollowUpCommand(
                        0, "excellent", null, null, null));
        var settlement = service.updateAppointmentFollowUp(
                patient(),
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentFollowUpCommand(
                        review.version(), null, "NEEDS_HELP", "결제 내역 확인 요청", null));

        assertThat(settlement.reviewRatingCode()).isEqualTo("excellent");
        assertThat(settlement.settlementFollowUpStatus()).isEqualTo("NEEDS_HELP");
        assertThat(settlement.settlementFollowUpNote()).isEqualTo("결제 내역 확인 요청");
        assertThat(settlement.version()).isEqualTo(2);
    }

    @Test
    void guardianCannotWriteFollowUpEvenWithReportScope() {
        appointmentRepository.current = Optional.of(existingAppointment("COMPLETED", 3));
        var guardian = new AppUserRepository.AppUser(
                GUARDIAN_ID,
                "guardian-firebase-uid",
                AppUserRole.GUARDIAN);

        assertThatThrownBy(() -> service.updateAppointmentFollowUp(
                guardian,
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentFollowUpCommand(
                        0, "excellent", null, null, null)))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("guardian_appointment_mutation_not_supported");
        assertThat(appointmentRepository.followUp).isEmpty();
    }

    @Test
    void followUpWriteRequiresCompletedAppointment() {
        appointmentRepository.current = Optional.of(existingAppointment("IN_PROGRESS", 3));

        assertThatThrownBy(() -> service.updateAppointmentFollowUp(
                patient(),
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentFollowUpCommand(
                        0, "good", null, null, null)))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_state_conflict");
    }

    @Test
    void staleFollowUpVersionIsRejected() {
        appointmentRepository.current = Optional.of(existingAppointment("COMPLETED", 3));
        appointmentRepository.followUp = Optional.of(new AppointmentFollowUpRecord(
                APPOINTMENT_ID,
                "good",
                NOW,
                "",
                "",
                null,
                "",
                null,
                2));

        assertThatThrownBy(() -> service.updateAppointmentFollowUp(
                patient(),
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentFollowUpCommand(
                        1, "excellent", null, null, null)))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("appointment_version_conflict");
    }

    @Test
    void supportEscalationWriteIsRejectedAndLegacyValueRemainsReadable() {
        appointmentRepository.current = Optional.of(existingAppointment("COMPLETED", 3));
        appointmentRepository.followUp = Optional.of(new AppointmentFollowUpRecord(
                APPOINTMENT_ID,
                "good",
                NOW,
                "",
                "",
                null,
                "DIALED_119",
                NOW,
                2));

        assertThat(service.getAppointmentFollowUp(patient(), APPOINTMENT_ID)
                .supportEscalationStatus()).isEqualTo("DIALED_119");
        assertThatThrownBy(() -> service.updateAppointmentFollowUp(
                patient(),
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentFollowUpCommand(
                        2, "excellent", null, null, "GUIDE_VIEWED")))
                .isInstanceOf(AppointmentException.class)
                .extracting(exception -> ((AppointmentException) exception).error())
                .isEqualTo("support_escalation_not_supported");
        assertThat(appointmentRepository.followUp).get()
                .extracting(AppointmentFollowUpRecord::supportEscalationStatus)
                .isEqualTo("DIALED_119");
        assertThat(appointmentRepository.followUp).get()
                .extracting(AppointmentFollowUpRecord::version)
                .isEqualTo(2L);
    }

    @Test
    void reviewUpdatePreservesLegacySupportEscalationColumns() {
        appointmentRepository.current = Optional.of(existingAppointment("COMPLETED", 3));
        appointmentRepository.followUp = Optional.of(new AppointmentFollowUpRecord(
                APPOINTMENT_ID,
                "good",
                NOW,
                "",
                "",
                null,
                "MANAGER_CALLED",
                NOW,
                2));

        var updated = service.updateAppointmentFollowUp(
                patient(),
                APPOINTMENT_ID,
                new AppointmentService.UpdateAppointmentFollowUpCommand(
                        2, "excellent", null, null, null));

        assertThat(updated.reviewRatingCode()).isEqualTo("excellent");
        assertThat(updated.supportEscalationStatus()).isEqualTo("MANAGER_CALLED");
        assertThat(updated.supportEscalatedAt()).isNotEmpty();
        assertThat(updated.version()).isEqualTo(3L);
    }

    private AppUserRepository.AppUser patient() {
        return new AppUserRepository.AppUser(PATIENT_ID, "patient-firebase-uid", AppUserRole.PATIENT);
    }

    private AppUserRepository.AppUser guardian() {
        return new AppUserRepository.AppUser(GUARDIAN_ID, "guardian-firebase-uid", AppUserRole.GUARDIAN);
    }

    private AppUserRepository.AppUser manager() {
        return new AppUserRepository.AppUser(MANAGER_ID, "manager-firebase-uid", AppUserRole.MANAGER);
    }

    private AppointmentService.AppointmentDraft draft() {
        return new AppointmentService.AppointmentDraft(
                "입력된 보호자",
                "01098765432",
                "GUARDIAN@example.com",
                "휠체어 이동 지원 필요",
                "아침 약 복용",
                "서울대학교병원",
                "내과",
                37.5796,
                126.9990,
                "2026-12-20 10:30",
                "본관 1층",
                "진료 20분 전 도착",
                "WHEELCHAIR",
                "ROUND_TRIP",
                "ANY",
                "CARD",
                "NONE");
    }

    private AppointmentService.AppointmentDraft draftWithOptions(
            AppointmentService.AppointmentDraft source, String mobility, String trip, String coupon) {
        return new AppointmentService.AppointmentDraft(
                source.linkedParticipantName(), source.linkedParticipantPhone(), source.linkedParticipantEmail(),
                source.patientConditionSummary(), source.medicationSummary(), source.hospitalName(),
                source.departmentName(), source.hospitalLatitude(), source.hospitalLongitude(),
                source.appointmentAt(), source.meetingPlace(), source.specialNotes(), mobility, trip,
                source.managerGenderPreferenceCode(), source.paymentMethodCode(), coupon);
    }

    private AppointmentService.AppointmentDraft draftWithPaymentMethod(String paymentMethodCode) {
        AppointmentService.AppointmentDraft source = draft();
        return new AppointmentService.AppointmentDraft(
                source.linkedParticipantName(),
                source.linkedParticipantPhone(),
                source.linkedParticipantEmail(),
                source.patientConditionSummary(),
                source.medicationSummary(),
                source.hospitalName(),
                source.departmentName(),
                source.hospitalLatitude(),
                source.hospitalLongitude(),
                source.appointmentAt(),
                source.meetingPlace(),
                source.specialNotes(),
                source.mobilitySupportCode(),
                source.tripTypeCode(),
                source.managerGenderPreferenceCode(),
                paymentMethodCode,
                source.couponCode());
    }

    private AppointmentService.AppointmentDraft draftWithMeetingPlace(String meetingPlace) {
        AppointmentService.AppointmentDraft source = draft();
        return new AppointmentService.AppointmentDraft(
                source.linkedParticipantName(),
                source.linkedParticipantPhone(),
                source.linkedParticipantEmail(),
                source.patientConditionSummary(),
                source.medicationSummary(),
                source.hospitalName(),
                source.departmentName(),
                source.hospitalLatitude(),
                source.hospitalLongitude(),
                source.appointmentAt(),
                meetingPlace,
                source.specialNotes(),
                source.mobilitySupportCode(),
                source.tripTypeCode(),
                source.managerGenderPreferenceCode(),
                source.paymentMethodCode(),
                source.couponCode());
    }

    private AppointmentService.AppointmentDraft draftWithLinkedGuardian(
            String name,
            String phone,
            String email) {
        AppointmentService.AppointmentDraft source = draft();
        return new AppointmentService.AppointmentDraft(
                name,
                phone,
                email,
                source.patientConditionSummary(),
                source.medicationSummary(),
                source.hospitalName(),
                source.departmentName(),
                source.hospitalLatitude(),
                source.hospitalLongitude(),
                source.appointmentAt(),
                source.meetingPlace(),
                source.specialNotes(),
                source.mobilitySupportCode(),
                source.tripTypeCode(),
                source.managerGenderPreferenceCode(),
                source.paymentMethodCode(),
                source.couponCode());
    }

    private AppointmentRecord existingAppointment(String status, long version) {
        return existingAppointment(status, version, null);
    }

    private AppointmentRecord existingAppointment(String status, long version, UUID managerUserId) {
        return existingAppointment(status, version, managerUserId, "CARD", "NONE");
    }

    private AppointmentRecord existingAppointment(
            String status, long version, UUID managerUserId, String paymentMethod, String coupon) {
        return new AppointmentRecord(
                APPOINTMENT_ID,
                "legacy-firestore-id",
                "BD-LEGACY",
                PATIENT_ID,
                GUARDIAN_ID,
                managerUserId,
                PATIENT_ID,
                AppUserRole.PATIENT,
                new AppointmentRepository.ParticipantSnapshot(
                        "환자 사용자", "010-1234-5678", "patient@example.com"),
                new AppointmentRepository.ParticipantSnapshot(
                        "보호자 사용자", "010-9876-5432", "guardian@example.com"),
                "서울대학교병원",
                "내과",
                37.5796,
                126.9990,
                Instant.parse("2026-12-20T01:30:00Z"),
                "본관 1층",
                "진료 20분 전 도착",
                "휠체어 이동 지원 필요",
                "아침 약 복용",
                "INDEPENDENT",
                "ONE_WAY",
                "ANY",
                status,
                69_000,
                "FAMILY".equals(coupon) ? 37_000 : 0,
                "FAMILY".equals(coupon) ? 10_000 : 0,
                "FAMILY".equals(coupon) ? 96_000 : 69_000,
                paymentMethod,
                coupon,
                "PENDING",
                "",
                null,
                "",
                version);
    }

    private final class FakeAppointmentRepository implements AppointmentRepository {
        private Optional<AppointmentRecord> current = Optional.empty();
        private Optional<AppointmentFollowUpRecord> followUp = Optional.empty();
        private final Map<String, AppointmentRecord> byClientRequest = new HashMap<>();
        private final Map<UUID, String> createRequestFingerprints = new HashMap<>();
        private int insertCount;
        private int publicCodeCollisionsRemaining;
        private boolean sessionCanceled;
        private boolean careEnded;

        @Override
        public List<AppointmentRecord> findAllForParticipant(UUID userId, AppUserRole role) {
            return current.stream().toList();
        }

        @Override
        public Optional<AppointmentRecord> findById(UUID appointmentId) {
            return current.filter(appointment -> appointment.id().equals(appointmentId));
        }

        @Override
        public Optional<AppointmentRecord> findByClientRequestId(
                UUID requesterUserId,
                UUID clientRequestId) {
            return Optional.ofNullable(byClientRequest.get(requesterUserId + ":" + clientRequestId));
        }

        @Override
        public Optional<String> findCreateRequestFingerprint(UUID appointmentId) {
            return Optional.ofNullable(createRequestFingerprints.get(appointmentId));
        }

        @Override
        public boolean hasCareEnded(UUID appointmentId) {
            return careEnded;
        }

        @Override
        public Optional<AppointmentRecord> insert(
                AppointmentMutation mutation,
                String publicCode,
                String createRequestFingerprint) {
            insertCount++;
            if (publicCodeCollisionsRemaining > 0) {
                publicCodeCollisionsRemaining--;
                return Optional.empty();
            }
            AppointmentRecord inserted = fromMutation(mutation, publicCode, "REQUESTED", 0);
            current = Optional.of(inserted);
            byClientRequest.put(
                    mutation.requesterUserId() + ":" + mutation.clientRequestId(),
                    inserted);
            createRequestFingerprints.put(inserted.id(), createRequestFingerprint);
            return Optional.of(inserted);
        }

        @Override
        public Optional<AppointmentRecord> update(
                UUID appointmentId,
                long expectedVersion,
                AppointmentMutation mutation) {
            if (current.isEmpty() || current.get().version() != expectedVersion) {
                return Optional.empty();
            }
            AppointmentRecord updated = fromMutation(
                    mutation,
                    current.get().publicCode(),
                    "REQUESTED",
                    expectedVersion + 1);
            current = Optional.of(updated);
            byClientRequest.replaceAll((key, appointment) ->
                    appointment.id().equals(updated.id()) ? updated : appointment);
            return current;
        }

        void putLegacyClientRequest(UUID clientRequestId, AppointmentRecord appointment) {
            current = Optional.of(appointment);
            byClientRequest.put(PATIENT_ID + ":" + clientRequestId, appointment);
        }

        @Override
        public Optional<AppointmentRecord> cancel(UUID appointmentId, long expectedVersion) {
            if (current.isEmpty() || current.get().version() != expectedVersion) {
                return Optional.empty();
            }
            AppointmentRecord appointment = current.get();
            current = Optional.of(new AppointmentRecord(
                    appointment.id(),
                    appointment.firestoreId(),
                    appointment.publicCode(),
                    appointment.patientUserId(),
                    appointment.guardianUserId(),
                    appointment.managerUserId(),
                    appointment.requesterUserId(),
                    appointment.requesterRole(),
                    appointment.patient(),
                    appointment.guardian(),
                    appointment.hospitalName(),
                    appointment.departmentName(),
                    appointment.hospitalLatitude(),
                    appointment.hospitalLongitude(),
                    appointment.appointmentAt(),
                    appointment.meetingPlace(),
                    appointment.specialNotes(),
                    appointment.patientConditionSummary(),
                    appointment.medicationSummary(),
                    appointment.mobilitySupportCode(),
                    appointment.tripTypeCode(),
                    appointment.managerGenderPreferenceCode(),
                    "CANCELED",
                    appointment.basePrice(),
                    appointment.optionSurchargePrice(),
                    appointment.couponDiscountPrice(),
                    appointment.finalPrice(),
                    appointment.paymentMethodCode(),
                    appointment.couponCode(),
                    appointment.paymentStatusCode(),
                    appointment.paymentApprovalCode(),
                    appointment.paymentApprovedAt(),
                    appointment.paymentProviderLabel(),
                    expectedVersion + 1));
            return current;
        }

        @Override
        public boolean cancelActiveSession(UUID appointmentId) {
            sessionCanceled = true;
            return true;
        }

        @Override
        public Optional<Instant> findCancellationBoundary(UUID appointmentId) {
            return current.filter(appointment -> appointment.id().equals(appointmentId))
                    .filter(appointment -> "CANCELED".equals(appointment.status()))
                    .map(appointment -> NOW);
        }

        @Override
        public Optional<AppointmentFollowUpRecord> findFollowUpByAppointmentId(UUID appointmentId) {
            return followUp.filter(value -> value.appointmentId().equals(appointmentId));
        }

        @Override
        public Optional<AppointmentFollowUpRecord> insertFollowUp(AppointmentFollowUpMutation mutation) {
            if (followUp.isPresent() || mutation.expectedVersion() != 0) {
                return Optional.empty();
            }
            followUp = Optional.of(new AppointmentFollowUpRecord(
                    mutation.appointmentId(),
                    valueOrEmpty(mutation.reviewRatingCode()),
                    mutation.reviewRatingCode() == null ? null : NOW,
                    valueOrEmpty(mutation.settlementStatus()),
                    valueOrEmpty(mutation.settlementNote()),
                    mutation.settlementStatus() == null ? null : NOW,
                    "",
                    null,
                    1));
            return followUp;
        }

        @Override
        public Optional<AppointmentFollowUpRecord> updateFollowUp(AppointmentFollowUpMutation mutation) {
            if (followUp.isEmpty() || followUp.get().version() != mutation.expectedVersion()) {
                return Optional.empty();
            }
            AppointmentFollowUpRecord currentFollowUp = followUp.get();
            followUp = Optional.of(new AppointmentFollowUpRecord(
                    mutation.appointmentId(),
                    mutation.reviewRatingCode() == null
                            ? currentFollowUp.reviewRatingCode()
                            : mutation.reviewRatingCode(),
                    mutation.reviewRatingCode() == null ? currentFollowUp.reviewSavedAt() : NOW,
                    mutation.settlementStatus() == null
                            ? currentFollowUp.settlementStatus()
                            : mutation.settlementStatus(),
                    mutation.settlementStatus() == null
                            ? currentFollowUp.settlementNote()
                            : valueOrEmpty(mutation.settlementNote()),
                    mutation.settlementStatus() == null ? currentFollowUp.settlementSavedAt() : NOW,
                    currentFollowUp.supportEscalationStatus(),
                    currentFollowUp.supportEscalatedAt(),
                    currentFollowUp.version() + 1));
            return followUp;
        }

        private String valueOrEmpty(String value) {
            return value == null ? "" : value;
        }

        private AppointmentRecord fromMutation(
                AppointmentMutation mutation,
                String publicCode,
                String status,
                long version) {
            return new AppointmentRecord(
                    APPOINTMENT_ID,
                    null,
                    publicCode,
                    mutation.patientUserId(),
                    mutation.guardianUserId(),
                    null,
                    mutation.requesterUserId(),
                    mutation.requesterRole(),
                    mutation.patient(),
                    mutation.guardian(),
                    mutation.hospitalName(),
                    mutation.departmentName(),
                    mutation.hospitalLatitude(),
                    mutation.hospitalLongitude(),
                    mutation.appointmentAt(),
                    mutation.meetingPlace(),
                    mutation.specialNotes(),
                    mutation.patientConditionSummary(),
                    mutation.medicationSummary(),
                    mutation.mobilitySupportCode(),
                    mutation.tripTypeCode(),
                    mutation.managerGenderPreferenceCode(),
                    status,
                    mutation.basePrice(),
                    mutation.optionSurchargePrice(),
                    mutation.couponDiscountPrice(),
                    mutation.finalPrice(),
                    mutation.paymentMethodCode(),
                    mutation.couponCode(),
                    mutation.paymentStatusCode(),
                    "",
                    null,
                    "",
                    version);
        }
    }

    private static final class FakeAppUserProfileRepository implements AppUserProfileRepository {
        private final Map<UUID, AppUserProfile> profiles = new HashMap<>();
        private int lookupCount;

        void add(AppUserProfile profile) {
            profiles.put(profile.id(), profile);
        }

        @Override
        public Optional<AppUserProfile> findById(UUID userId) {
            lookupCount++;
            return Optional.ofNullable(profiles.get(userId));
        }

        @Override
        public List<AppUserProfile> findByEmail(AppUserRole role, String email) {
            lookupCount++;
            return matching(role, email, true);
        }

        @Override
        public List<AppUserProfile> findByPhone(AppUserRole role, String phone) {
            lookupCount++;
            return matching(role, phone, false);
        }

        private List<AppUserProfile> matching(AppUserRole role, String value, boolean email) {
            List<AppUserProfile> matches = new ArrayList<>();
            for (AppUserProfile profile : profiles.values()) {
                String profileValue = email ? profile.email() : profile.phone();
                if (profile.role() == role && profileValue.equals(value)) {
                    matches.add(profile);
                }
            }
            return matches;
        }
    }

    private static final class RecordingConsentAccess implements GuardianSharingConsentAccess {
        private UUID finalizedAppointmentId;
        private Instant careEndedAt;

        @Override
        public boolean isAllowed(
                AppUserRepository.AppUser appUser,
                UUID appointmentRequestId,
                UUID patientUserId,
                UUID guardianUserId,
                InformationScope scope) {
            return true;
        }

        @Override
        public void finalizeExpiryAfterCareBoundary(
                UUID appointmentRequestId,
                Instant careEndedAt) {
            this.finalizedAppointmentId = appointmentRequestId;
            this.careEndedAt = careEndedAt;
        }
    }
}
