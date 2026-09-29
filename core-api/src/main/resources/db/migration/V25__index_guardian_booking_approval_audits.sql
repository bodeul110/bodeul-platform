-- 환자 조건은 기존 PK로 처리하고, 보호자 조건은 별도 인덱스로 감사 전체 순회를 피한다.
create index ix_guardian_booking_approval_events_guardian
    on bodeul.guardian_booking_approval_events (guardian_user_id);

comment on index bodeul.ix_guardian_booking_approval_events_guardian is
    '계정 삭제 영향도의 보호자별 예약 승인 감사 집계용 인덱스';
