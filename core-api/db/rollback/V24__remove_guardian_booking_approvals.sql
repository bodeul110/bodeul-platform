-- 쓰기 중단·백업과 별도 승인된 데이터 정리가 끝나 빈 테이블일 때만 실행한다.
-- 실제 승인·철회 이력을 자동 삭제하거나 Flyway history를 조작하지 않는다.
begin;
set local role bodeul_migration;
lock table bodeul.guardian_booking_approvals, bodeul.guardian_booking_approval_events
    in access exclusive mode;
do $$
begin
    if exists (select 1 from bodeul.guardian_booking_approvals)
            or exists (select 1 from bodeul.guardian_booking_approval_events) then
        raise exception '예약 생성 승인 이력을 백업·정리하기 전에는 V24를 되돌릴 수 없습니다.'
            using errcode = '55000';
    end if;
end;
$$;
drop table bodeul.guardian_booking_approval_events;
drop table bodeul.guardian_booking_approvals;
commit;
