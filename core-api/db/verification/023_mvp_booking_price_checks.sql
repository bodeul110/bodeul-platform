-- 기존 검증 데이터·조회 감사 개수를 유지하며 신규/과거 금액의 공존을 확인한다.
savepoint mvp_booking_price_checks;
set local role bodeul_core_runtime;

insert into bodeul.appointment_requests (
    id, client_request_id, patient_user_id, requester_user_id, requester_role,
    patient_name, patient_phone, patient_email, hospital_name, department_name,
    hospital_latitude, hospital_longitude, appointment_at, appointment_at_epoch_millis,
    appointment_date_key, mobility_support_code, trip_type_code,
    manager_gender_preference_code, status, base_price, option_surcharge_price,
    coupon_discount_price, final_price, payment_method_code, coupon_code, payment_status_code
)
select
    '20000000-0000-0000-0000-000000000006',
    '30000000-0000-0000-0000-000000000006',
    patient_user_id, requester_user_id, requester_role,
    patient_name, patient_phone, patient_email, hospital_name, department_name,
    hospital_latitude, hospital_longitude, appointment_at, appointment_at_epoch_millis,
    appointment_date_key, 'WHEELCHAIR', 'ROUND_TRIP',
    manager_gender_preference_code, 'REQUESTED', 40000, 0,
    0, 40000, 'BANK_TRANSFER', 'NONE', 'AWAITING_DEPOSIT'
from bodeul.appointment_requests
where id = '20000000-0000-0000-0000-000000000003';

update bodeul.appointment_requests
set meeting_place = '합성 예약 만남 장소 수정', version = version + 1
where id = '20000000-0000-0000-0000-000000000006';

do $$
declare
    v_amount integer;
    v_due_at timestamptz;
begin
    select expected_amount, payment_due_at into v_amount, v_due_at
    from bodeul.get_bank_transfer_payment(
        '20000000-0000-0000-0000-000000000006',
        '10000000-0000-0000-0000-000000000001');
    if v_amount is distinct from 40000 or v_due_at is not null then
        raise exception '신규 원장이 40,000원 금액을 복사하지 않았거나 개별 기한을 임의 생성했습니다.';
    end if;

    select expected_amount into v_amount
    from bodeul.get_bank_transfer_payment(
        '20000000-0000-0000-0000-000000000003',
        '10000000-0000-0000-0000-000000000001');
    if v_amount is distinct from 69000 then
        raise exception '과거 예약 원장의 69,000원 금액이 변경됐습니다.';
    end if;

    begin
        update bodeul.appointment_requests set final_price = 69000
        where id = '20000000-0000-0000-0000-000000000006';
        raise exception '신규 무통장입금 금액 변경이 허용됐습니다.' using errcode = 'P0004';
    exception when sqlstate '22023' then null;
    end;

    begin
        update bodeul.appointment_requests set final_price = 40000
        where id = '20000000-0000-0000-0000-000000000003';
        raise exception '과거 무통장입금을 신규 요금으로 변경할 수 있습니다.' using errcode = 'P0004';
    exception when sqlstate '22023' then null;
    end;
end;
$$;

set local role bodeul_admin_runtime;
do $$
declare v_payment jsonb;
begin
    v_payment := bodeul.get_admin_bank_transfer_payment(
        '10000000-0000-0000-0000-000000000004',
        '20000000-0000-0000-0000-000000000006');
    if (v_payment ->> 'expectedAmount')::integer is distinct from 40000
            or v_payment ->> 'paymentStatusCode' is distinct from 'AWAITING_DEPOSIT' then
        raise exception '관리자 조회가 신규 40,000원 원장과 일치하지 않습니다.';
    end if;

    v_payment := bodeul.get_admin_bank_transfer_payment(
        '10000000-0000-0000-0000-000000000004',
        '20000000-0000-0000-0000-000000000003');
    if (v_payment ->> 'expectedAmount')::integer is distinct from 69000 then
        raise exception '관리자 조회가 기존 69,000원 원장과 일치하지 않습니다.';
    end if;
end;
$$;

set local role bodeul_migration;
do $$
begin
    if (select count(*) from bodeul.appointment_payment_events
        where appointment_request_id = '20000000-0000-0000-0000-000000000006') <> 1 then
        raise exception '일반 정보 수정·금액 변경 거부 과정에서 결제 이벤트가 추가되거나 누락됐습니다.';
    end if;
end;
$$;

rollback to savepoint mvp_booking_price_checks;
release savepoint mvp_booking_price_checks;
