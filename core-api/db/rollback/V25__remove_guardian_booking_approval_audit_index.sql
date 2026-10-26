-- 조회 성능만 되돌린다. 승인 상태와 추가 전용 감사 이력은 보존한다.
drop index if exists bodeul.ix_guardian_booking_approval_events_guardian;
