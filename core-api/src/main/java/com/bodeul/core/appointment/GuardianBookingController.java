package com.bodeul.core.appointment;

import java.util.UUID;
import java.util.List;

import com.bodeul.core.auth.AppUserRepository.AppUser;
import com.bodeul.core.consent.GuardianBookingApprovalService;
import com.bodeul.core.consent.GuardianBookingApprovalService.ApprovalView;
import com.bodeul.core.consent.GuardianBookingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments/guardian-booking")
@Profile({"database", "guardian-booking-test"})
class GuardianBookingController {
    private final GuardianBookingService service;
    private final GuardianBookingApprovalService approvals;
    private final GuardianBookingDraftService drafts;

    GuardianBookingController(GuardianBookingService service, GuardianBookingApprovalService approvals,
            GuardianBookingDraftService drafts) {
        this.service = service;
        this.approvals = approvals;
        this.drafts = drafts;
    }

    @PostMapping("/drafts")
    ResponseEntity<GuardianBookingDraftRepository.DraftSummary> submitDraft(@AuthenticationPrincipal AppUser actor,
            @RequestBody SubmitDraftRequest request) {
        return noStore(drafts.submit(actor, request.patientUserId(), command(request.appointment())));
    }

    @GetMapping("/drafts")
    ResponseEntity<List<GuardianBookingDraftRepository.DraftSummary>> listDrafts(@AuthenticationPrincipal AppUser actor) {
        return noStore(drafts.list(actor));
    }

    @GetMapping("/drafts/guardians/{guardianId}/requests/{requestId}")
    ResponseEntity<GuardianBookingDraftService.DraftView> getDraft(@AuthenticationPrincipal AppUser actor,
            @PathVariable UUID guardianId, @PathVariable UUID requestId) {
        return noStore(drafts.get(actor, guardianId, requestId));
    }

    @PostMapping("/drafts/guardians/{guardianId}/requests/{requestId}/approvals")
    ResponseEntity<ApprovalView> grantStoredDraft(@AuthenticationPrincipal AppUser actor,
            @PathVariable UUID guardianId, @PathVariable UUID requestId, @RequestBody StoredGrantRequest request) {
        return noStore(drafts.grant(actor, guardianId, requestId,
                version(request.expectedVersion()), confirmed(request.adultPatientConfirmed())));
    }

    @PostMapping("/drafts/guardians/{guardianId}/requests/{requestId}/create")
    ResponseEntity<GuardianBookingService.Receipt> createStoredDraft(@AuthenticationPrincipal AppUser actor,
            @PathVariable UUID guardianId, @PathVariable UUID requestId, @RequestBody StoredCreateRequest request) {
        return noStore(drafts.create(actor, guardianId, requestId, request.grantId(), version(request.approvalVersion())));
    }

    @PostMapping("/preview")
    ResponseEntity<AppointmentService.CreateAppointmentCommand> preview(@AuthenticationPrincipal AppUser actor,
            @RequestBody PreviewRequest request) {
        return noStore(service.preview(actor, request.guardianUserId(), command(request.appointment())));
    }

    @PostMapping("/approvals")
    ResponseEntity<ApprovalView> grant(@AuthenticationPrincipal AppUser actor, @RequestBody GrantRequest request) {
        return noStore(service.grant(actor, request.guardianUserId(), command(request.appointment()),
                version(request.expectedVersion()), confirmed(request.adultPatientConfirmed())));
    }

    @GetMapping("/patients/{patientId}/guardians/{guardianId}/requests/{requestId}")
    ResponseEntity<ApprovalView> get(@AuthenticationPrincipal AppUser actor, @PathVariable UUID patientId,
            @PathVariable UUID guardianId, @PathVariable UUID requestId) {
        return noStore(approvals.get(actor, patientId, guardianId, requestId));
    }

    @PostMapping("/guardians/{guardianId}/requests/{requestId}/revoke")
    ResponseEntity<ApprovalView> revoke(@AuthenticationPrincipal AppUser actor, @PathVariable UUID guardianId,
            @PathVariable UUID requestId, @RequestBody RevokeRequest request) {
        return noStore(approvals.revoke(actor, guardianId, requestId, version(request.expectedVersion())));
    }

    @PostMapping("/create")
    ResponseEntity<GuardianBookingService.Receipt> create(@AuthenticationPrincipal AppUser actor,
            @RequestBody CreateRequest request) {
        return noStore(service.create(actor, request.patientUserId(), request.grantId(),
                version(request.approvalVersion()), command(request.appointment())));
    }

    private AppointmentService.CreateAppointmentCommand command(AppointmentController.CreateAppointmentRequest value) {
        if (value == null) return null;
        return new AppointmentService.CreateAppointmentCommand(value.clientRequestId(), value.toDraft(),
                value.pricePolicyVersion(), value.confirmedFinalPrice());
    }

    private long version(JsonNode value) {
        if (value == null || value.isNull()) return -1;
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw GuardianBookingException.invalid("승인 버전은 정수로 보내 주세요.");
        }
        return value.longValue();
    }

    private boolean confirmed(JsonNode value) {
        if (value == null || value.isNull()) return false;
        if (!value.isBoolean()) throw GuardianBookingException.invalid("성인 본인 확인 값을 확인해 주세요.");
        return value.booleanValue();
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    record PreviewRequest(UUID guardianUserId, AppointmentController.CreateAppointmentRequest appointment) { }
    record GrantRequest(UUID guardianUserId, JsonNode expectedVersion, JsonNode adultPatientConfirmed,
                        AppointmentController.CreateAppointmentRequest appointment) { }
    record RevokeRequest(JsonNode expectedVersion) { }
    record CreateRequest(UUID patientUserId, UUID grantId, JsonNode approvalVersion,
                         AppointmentController.CreateAppointmentRequest appointment) { }
    record SubmitDraftRequest(UUID patientUserId, AppointmentController.CreateAppointmentRequest appointment) { }
    record StoredGrantRequest(JsonNode expectedVersion, JsonNode adultPatientConfirmed) { }
    record StoredCreateRequest(UUID grantId, JsonNode approvalVersion) { }
}
