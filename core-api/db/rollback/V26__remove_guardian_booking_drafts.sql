begin;
lock table bodeul.guardian_booking_drafts in access exclusive mode;
do $$
begin
    if exists (select 1 from bodeul.guardian_booking_drafts) then
        raise exception '예약 원문이 남아 있어 롤백하지 않습니다. 별도 보존·파기 절차를 먼저 확인해 주세요.';
    end if;
end;
$$;
drop function bodeul.purge_expired_guardian_booking_drafts(timestamptz, integer);
drop function bodeul.preview_expired_guardian_booking_drafts(timestamptz);
drop table bodeul.guardian_booking_drafts;
commit;
