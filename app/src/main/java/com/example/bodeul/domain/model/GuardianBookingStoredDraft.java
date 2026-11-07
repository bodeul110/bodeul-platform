package com.example.bodeul.domain.model;

import java.time.Instant;
import java.util.Objects;

/** 서버가 검증한 원문과 표시 정보를 메모리에서만 전달한다. */
public final class GuardianBookingStoredDraft {
    private final Summary summary;
    private final GuardianBookingRequest request;
    private final Participant patient;
    private final Participant guardian;

    public GuardianBookingStoredDraft(Summary summary, GuardianBookingRequest request,
            Participant patient, Participant guardian) {
        this.summary = Objects.requireNonNull(summary);
        this.request = Objects.requireNonNull(request);
        this.patient = Objects.requireNonNull(patient);
        this.guardian = Objects.requireNonNull(guardian);
        if (!summary.getReference().equals(GuardianBookingDraftReference.from(request))) {
            throw new IllegalArgumentException("원문과 요청 식별자가 일치하지 않습니다.");
        }
    }

    public Summary getSummary() { return summary; }
    public GuardianBookingRequest getRequest() { return request; }
    public Participant getPatient() { return patient; }
    public Participant getGuardian() { return guardian; }

    public static final class Summary {
        private final GuardianBookingDraftReference reference;
        private final Instant createdAt;
        private final Instant expiresAt;

        public Summary(GuardianBookingDraftReference reference, Instant createdAt, Instant expiresAt) {
            this.reference = Objects.requireNonNull(reference);
            this.createdAt = Objects.requireNonNull(createdAt);
            this.expiresAt = Objects.requireNonNull(expiresAt);
            if (!expiresAt.isAfter(createdAt)) {
                throw new IllegalArgumentException("예약 원문 유효기간이 올바르지 않습니다.");
            }
        }

        public GuardianBookingDraftReference getReference() { return reference; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
    }

    public static final class Participant {
        private final String name;
        private final String maskedPhone;

        public Participant(String name, String maskedPhone) {
            if (name == null || name.trim().isEmpty() || maskedPhone == null
                    || !maskedPhone.matches("(?:\\*{3}-\\*{4}-[0-9]{4}|\\*{4})")) {
                throw new IllegalArgumentException("예약 참여자 표시 정보를 확인해 주세요.");
            }
            this.name = name;
            this.maskedPhone = maskedPhone;
        }

        public String getName() { return name; }
        public String getMaskedPhone() { return maskedPhone; }
    }
}
