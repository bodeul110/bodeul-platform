begin;
set local role bodeul_migration;

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conrelid = 'bodeul.appointment_requests'::regclass
          and conname = 'ck_appointment_requests_create_request_fingerprint'
    ) or not exists (
        select 1 from pg_trigger
        where tgrelid = 'bodeul.appointment_requests'::regclass
          and tgname = 'appointment_create_request_fingerprint_guard'
          and not tgisinternal
    ) then
        raise exception '예약 생성 요청 지문 제약 또는 불변 guard가 없습니다.';
    end if;

    if exists (
        select 1
        from (values ('bodeul_core_runtime'), ('bodeul_admin_runtime')) runtime(role_name)
        cross join (values
            ('bodeul.appointment_bank_transfer_payments'),
            ('bodeul.appointment_payment_events')
        ) ledger(table_name)
        cross join (values ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE')) access(privilege_name)
        where has_table_privilege(runtime.role_name, ledger.table_name, access.privilege_name)
    ) then
        raise exception '결제 상세 또는 감사 테이블에 direct DML 권한이 노출됐습니다.';
    end if;

    if not has_function_privilege(
        'bodeul_core_runtime',
        'bodeul.get_bank_transfer_payment(uuid,uuid)',
        'EXECUTE'
    ) or not has_function_privilege(
        'bodeul_core_runtime',
        'bodeul.set_bank_transfer_depositor(uuid,uuid,uuid,bigint,text)',
        'EXECUTE'
    ) then
        raise exception 'Core runtime의 사용자 결제 함수 권한이 없습니다.';
    end if;

    if has_function_privilege(
        'bodeul_core_runtime',
        'bodeul.transition_appointment_bank_transfer_payment(uuid,uuid,uuid,bigint,text,integer,text)',
        'EXECUTE'
    ) or not has_function_privilege(
        'bodeul_admin_runtime',
        'bodeul.transition_appointment_bank_transfer_payment(uuid,uuid,uuid,bigint,text,integer,text)',
        'EXECUTE'
    ) then
        raise exception 'Core와 관리자 결제 상태 변경 권한이 분리되지 않았습니다.';
    end if;

    if has_function_privilege(
        'bodeul_admin_runtime',
        'bodeul.get_bank_transfer_payment(uuid,uuid)',
        'EXECUTE'
    ) or has_function_privilege(
        'bodeul_admin_runtime',
        'bodeul.set_bank_transfer_depositor(uuid,uuid,uuid,bigint,text)',
        'EXECUTE'
    ) then
        raise exception '관리자 runtime에 환자용 결제 함수가 노출됐습니다.';
    end if;
end;
$$;

insert into bodeul.app_users (id, firebase_uid, role, name, email, phone) values
    ('10000000-0000-0000-0000-000000000001', 'bank-patient', 'PATIENT', '환자', 'patient@example.com', '010-1111-1111'),
    ('10000000-0000-0000-0000-000000000002', 'bank-other-patient', 'PATIENT', '다른 환자', 'other@example.com', '010-2222-2222'),
    ('10000000-0000-0000-0000-000000000003', 'bank-manager', 'MANAGER', '매니저', 'manager@example.com', '010-3333-3333'),
    ('10000000-0000-0000-0000-000000000004', 'bank-operations', 'ADMIN', '운영 관리자', 'ops@example.com', '010-4444-4444'),
    ('10000000-0000-0000-0000-000000000005', 'bank-developer', 'ADMIN', '개발 관리자', 'dev@example.com', '010-5555-5555');

insert into bodeul.admin_role_assignments (
    admin_user_id,
    admin_role,
    grant_reason
) values
    ('10000000-0000-0000-0000-000000000004', 'OPERATIONS', '결제 검증 운영 역할'),
    ('10000000-0000-0000-0000-000000000005', 'DEVELOPER', '결제 검증 개발 역할');

insert into bodeul.appointment_requests (
    id,
    client_request_id,
    patient_user_id,
    requester_user_id,
    requester_role,
    patient_name,
    patient_phone,
    patient_email,
    hospital_name,
    department_name,
    hospital_latitude,
    hospital_longitude,
    appointment_at,
    appointment_at_epoch_millis,
    appointment_date_key,
    mobility_support_code,
    trip_type_code,
    manager_gender_preference_code,
    status,
    base_price,
    option_surcharge_price,
    coupon_discount_price,
    final_price,
    payment_method_code,
    coupon_code,
    payment_status_code
) values
    (
        '20000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        'PATIENT', '환자', '010-1111-1111', 'patient@example.com',
        '검증병원', '내과', 37.5, 127.0,
        now() + interval '7 days',
        (extract(epoch from now() + interval '7 days') * 1000)::bigint,
        to_char(current_date + 7, 'YYYY-MM-DD'),
        'INDEPENDENT', 'ONE_WAY', 'ANY', 'REQUESTED',
        90000, 10000, 0, 100000,
        'BANK_TRANSFER', 'NONE', 'AWAITING_DEPOSIT'
    ),
    (
        '20000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        'PATIENT', '환자', '010-1111-1111', 'patient@example.com',
        '검증병원', '외과', 37.5, 127.0,
        now() + interval '8 days',
        (extract(epoch from now() + interval '8 days') * 1000)::bigint,
        to_char(current_date + 8, 'YYYY-MM-DD'),
        'INDEPENDENT', 'ONE_WAY', 'ANY', 'REQUESTED',
        70000, 0, 0, 70000,
        'BANK_TRANSFER', 'NONE', 'AWAITING_DEPOSIT'
    ),
    (
        '20000000-0000-0000-0000-000000000003',
        '30000000-0000-0000-0000-000000000003',
        '10000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        'PATIENT', '환자', '010-1111-1111', 'patient@example.com',
        '검증병원', '정형외과', 37.5, 127.0,
        now() + interval '9 days',
        (extract(epoch from now() + interval '9 days') * 1000)::bigint,
        to_char(current_date + 9, 'YYYY-MM-DD'),
        'INDEPENDENT', 'ONE_WAY', 'ANY', 'REQUESTED',
        69000, 0, 0, 69000,
        'BANK_TRANSFER', 'NONE', 'AWAITING_DEPOSIT'
    ),
    (
        '20000000-0000-0000-0000-000000000004',
        '30000000-0000-0000-0000-000000000004',
        '10000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        'PATIENT', '환자', '010-1111-1111', 'patient@example.com',
        '검증병원', '신경외과', 37.5, 127.0,
        now() + interval '10 days',
        (extract(epoch from now() + interval '10 days') * 1000)::bigint,
        to_char(current_date + 10, 'YYYY-MM-DD'),
        'INDEPENDENT', 'ONE_WAY', 'ANY', 'REQUESTED',
        69000, 0, 0, 69000,
        'BANK_TRANSFER', 'NONE', 'AWAITING_DEPOSIT'
    ),
    (
        '20000000-0000-0000-0000-000000000005',
        '30000000-0000-0000-0000-000000000005',
        '10000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        'PATIENT', '환자', '010-1111-1111', 'patient@example.com',
        '검증병원', '재활의학과', 37.5, 127.0,
        now() + interval '11 days',
        (extract(epoch from now() + interval '11 days') * 1000)::bigint,
        to_char(current_date + 11, 'YYYY-MM-DD'),
        'INDEPENDENT', 'ONE_WAY', 'ANY', 'REQUESTED',
        71000, 0, 0, 71000,
        'BANK_TRANSFER', 'NONE', 'AWAITING_DEPOSIT'
    );

do $$
declare
    v_bank_transfer_payment_count bigint;
    v_payment_event_count bigint;
begin
    if (select count(*) from bodeul.appointment_bank_transfer_payments) <> 5
            or (select count(*) from bodeul.appointment_payment_events where event_type = 'CREATED') <> 5 then
        raise exception '무통장입금 예약 생성 trigger가 상세 원장과 생성 이벤트를 만들지 않았습니다.';
    end if;

    select inventory.bank_transfer_payment_count, inventory.payment_event_count
    into v_bank_transfer_payment_count, v_payment_event_count
    from bodeul.account_deletion_postgres_inventory(
        '10000000-0000-0000-0000-000000000001'
    ) inventory;
    if v_bank_transfer_payment_count <> 5 or v_payment_event_count <> 5 then
        raise exception '환자 계정 삭제 영향도에 무통장입금 상세와 이벤트가 모두 반영되지 않았습니다.';
    end if;
end;
$$;

delete from bodeul.appointment_payment_events
where appointment_request_id = '20000000-0000-0000-0000-000000000004';
delete from bodeul.appointment_bank_transfer_payments
where appointment_request_id = '20000000-0000-0000-0000-000000000004';

set local role bodeul_core_runtime;

do $$
begin
    begin
        perform 1 from bodeul.appointment_bank_transfer_payments;
        raise exception 'Core runtime이 결제 상세 테이블을 직접 조회했습니다.';
    exception
        when insufficient_privilege then null;
    end;

    begin
        update bodeul.appointment_bank_transfer_payments
        set depositor_name = '권한 우회'
        where appointment_request_id = '20000000-0000-0000-0000-000000000001';
        raise exception 'Core runtime이 결제 상세 테이블을 직접 변경했습니다.';
    exception
        when insufficient_privilege then null;
    end;

    begin
        update bodeul.appointment_requests
        set create_request_fingerprint = repeat('a', 64)
        where id = '20000000-0000-0000-0000-000000000001';
        raise exception 'Core runtime이 예약 생성 요청 지문을 변경했습니다.';
    exception
        when insufficient_privilege then null;
    end;

    begin
        update bodeul.appointment_requests
        set status = 'CANCELED',
            version = version + 1
        where id = '20000000-0000-0000-0000-000000000004';
        raise exception '결제 상세 원장이 없는 예약 취소가 허용됐습니다.';
    exception
        when sqlstate 'P0002' then null;
    end;

    begin
        perform * from bodeul.get_bank_transfer_payment(
            '20000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000002'
        );
        raise exception '다른 환자가 결제 정보를 조회했습니다.';
    exception
        when insufficient_privilege then null;
    end;
end;
$$;

select * from bodeul.set_bank_transfer_depositor(
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    0,
    '  홍   길동  '
);

select * from bodeul.set_bank_transfer_depositor(
    '20000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000005',
    0,
    '검토 입금자'
);

select * from bodeul.set_bank_transfer_depositor(
    '20000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000008',
    0,
    '이상 입금자'
);

select * from bodeul.set_bank_transfer_depositor(
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    0,
    '홍 길동'
);

do $$
begin
    begin
        perform * from bodeul.set_bank_transfer_depositor(
            '20000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000001',
            '40000000-0000-0000-0000-000000000001',
            0,
            '다른 이름'
        );
        raise exception '같은 operation_id의 다른 입금자명이 허용됐습니다.';
    exception
        when sqlstate 'P0003' then null;
    end;
end;
$$;

set local role bodeul_admin_runtime;

do $$
begin
    begin
        update bodeul.appointment_bank_transfer_payments
        set received_amount = 1
        where appointment_request_id = '20000000-0000-0000-0000-000000000001';
        raise exception 'Admin runtime이 결제 상세 테이블을 직접 변경했습니다.';
    exception
        when insufficient_privilege then null;
    end;

    begin
        perform bodeul.assign_companion_session(
            '20000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000003',
            '10000000-0000-0000-0000-000000000004',
            0,
            '입금 전 매칭 차단 검증'
        );
        -- 검증 실패를 예상한 도메인 오류(P0001)로 삼키지 않는다.
        raise exception '입금 확인 전 무통장입금 예약이 매칭됐습니다.'
            using errcode = 'P0004';
    exception
        when sqlstate 'P0001' then null;
    end;

    begin
        perform * from bodeul.transition_appointment_bank_transfer_payment(
            '20000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000005',
            '40000000-0000-0000-0000-000000000002',
            1,
            'DEPOSIT_CONFIRMED',
            100000,
            '개발 관리자 권한 거부 검증'
        );
        raise exception 'DEVELOPER 역할이 입금 상태를 변경했습니다.';
    exception
        when insufficient_privilege then null;
    end;

    begin
        perform * from bodeul.transition_appointment_bank_transfer_payment(
            '20000000-0000-0000-0000-000000000003',
            '10000000-0000-0000-0000-000000000004',
            '40000000-0000-0000-0000-000000000007',
            0,
            'CANCELED',
            null,
            '관리자 결제 함수의 예약 취소 차단'
        );
        raise exception '관리자 결제 함수가 예약 취소 상태를 직접 변경했습니다.';
    exception
        when sqlstate '22023' then null;
    end;
end;
$$;

select * from bodeul.transition_appointment_bank_transfer_payment(
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000004',
    '40000000-0000-0000-0000-000000000003',
    1,
    'DEPOSIT_CONFIRMED',
    100000,
    '예약 금액과 입금액 일치 확인'
);

select * from bodeul.transition_appointment_bank_transfer_payment(
    '20000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000004',
    '40000000-0000-0000-0000-000000000006',
    1,
    'REVIEW_REQUIRED',
    60000,
    '예약 금액과 다른 입금 운영 검토'
);

select * from bodeul.transition_appointment_bank_transfer_payment(
    '20000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000004',
    '40000000-0000-0000-0000-000000000009',
    1,
    'REVIEW_REQUIRED',
    71000,
    '정확한 금액이지만 입금자 또는 시점 이상 검토'
);

select * from bodeul.transition_appointment_bank_transfer_payment(
    '20000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000004',
    '40000000-0000-0000-0000-000000000010',
    2,
    'REFUND_REQUESTED',
    null,
    '이상 입금 운영 검토 후 환불 요청'
);

select * from bodeul.transition_appointment_bank_transfer_payment(
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000004',
    '40000000-0000-0000-0000-000000000003',
    1,
    'DEPOSIT_CONFIRMED',
    100000,
    '예약 금액과 입금액 일치 확인'
);

do $$
declare
    v_bank_transfer_payment_count bigint;
    v_payment_event_count bigint;
begin
    select inventory.bank_transfer_payment_count, inventory.payment_event_count
    into v_bank_transfer_payment_count, v_payment_event_count
    from bodeul.account_deletion_postgres_inventory(
        '10000000-0000-0000-0000-000000000004'
    ) inventory;
    if v_bank_transfer_payment_count < 1 or v_payment_event_count < 1 then
        raise exception '운영 관리자 계정 삭제 영향도에 결제 확인 상세와 감사 이벤트가 반영되지 않았습니다.';
    end if;
end;
$$;

set local role bodeul_migration;

do $$
begin
    if (select count(*) from bodeul.appointment_payment_events
        where operation_id = '40000000-0000-0000-0000-000000000003') <> 1
            or (select count(*) from bodeul.admin_access_audits
                where operation_id = '40000000-0000-0000-0000-000000000003') <> 1 then
        raise exception '관리자 성공 재시도에서 결제 또는 관리자 감사가 중복됐습니다.';
    end if;

    if (select payment_status_code from bodeul.appointment_requests
        where id = '20000000-0000-0000-0000-000000000002') <> 'REVIEW_REQUIRED' then
        raise exception '금액 불일치 입금이 REVIEW_REQUIRED 상태로 전이되지 않았습니다.';
    end if;

    if (select payment_status_code from bodeul.appointment_requests
        where id = '20000000-0000-0000-0000-000000000005') <> 'REFUND_REQUESTED' then
        raise exception '정확한 금액의 이상 입금이 검토 뒤 환불 요청으로 전이되지 않았습니다.';
    end if;

    if exists (
        select 1 from bodeul.admin_access_audits
        where operation_id = '40000000-0000-0000-0000-000000000003'
          and metadata::text like '%홍%'
    ) then
        raise exception '관리자 감사 metadata에 입금자명 평문이 저장됐습니다.';
    end if;
end;
$$;

set local role bodeul_admin_runtime;

do $$
begin
    begin
        perform * from bodeul.transition_appointment_bank_transfer_payment(
            '20000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000004',
            '40000000-0000-0000-0000-000000000004',
            2,
            'REFUNDED',
            null,
            '환불 요청 이전 완료 차단 검증'
        );
        raise exception '환불 요청 이전 REFUNDED 전이가 허용됐습니다.'
            using errcode = 'P0004';
    exception
        when sqlstate 'P0001' then null;
    end;

    begin
        perform * from bodeul.transition_appointment_bank_transfer_payment(
            '20000000-0000-0000-0000-000000000001',
            '10000000-0000-0000-0000-000000000004',
            '40000000-0000-0000-0000-000000000003',
            1,
            'DEPOSIT_CONFIRMED',
            100000,
            '동일 작업 ID의 다른 사유 거부'
        );
        raise exception '관리자 operation_id의 다른 payload 재사용이 허용됐습니다.';
    exception
        when sqlstate 'P0003' then null;
    end;
end;
$$;

select bodeul.assign_companion_session(
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000004',
    1,
    '입금 확인 뒤 매칭 허용 검증'
);

set local role bodeul_core_runtime;
update bodeul.appointment_requests
set status = 'CANCELED',
    updated_at = now(),
    version = version + 1
where id = '20000000-0000-0000-0000-000000000002';

update bodeul.appointment_requests
set status = 'CANCELED',
    updated_at = now(),
    version = version + 1
where id = '20000000-0000-0000-0000-000000000003';

set local role bodeul_admin_runtime;

select * from bodeul.transition_appointment_bank_transfer_payment(
    '20000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000004',
    '40000000-0000-0000-0000-000000000011',
    1,
    'REVIEW_REQUIRED',
    69000,
    '예약 취소 뒤 확인된 지연 입금 검토'
);

do $$
begin
    begin
        perform * from bodeul.transition_appointment_bank_transfer_payment(
            '20000000-0000-0000-0000-000000000003',
            '10000000-0000-0000-0000-000000000004',
            '40000000-0000-0000-0000-000000000012',
            2,
            'DEPOSIT_CONFIRMED',
            69000,
            '취소 예약의 지연 입금 확인 완료 차단'
        );
        raise exception '취소된 예약의 지연 입금이 확인 완료 상태로 변경됐습니다.'
            using errcode = 'P0004';
    exception
        when sqlstate 'P0001' then null;
    end;
end;
$$;

select * from bodeul.transition_appointment_bank_transfer_payment(
    '20000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000004',
    '40000000-0000-0000-0000-000000000013',
    2,
    'REFUND_REQUESTED',
    null,
    '예약 취소 뒤 지연 입금 환불 요청'
);

set local role bodeul_migration;
do $$
begin
    if (select payment_status_code from bodeul.appointment_requests
        where id = '20000000-0000-0000-0000-000000000002') <> 'REFUND_REQUESTED' then
        raise exception '금액 검토 중 예약 취소가 REFUND_REQUESTED 상태로 전이되지 않았습니다.';
    end if;
    if (select payment_status_code from bodeul.appointment_requests
        where id = '20000000-0000-0000-0000-000000000003') <> 'REFUND_REQUESTED' then
        raise exception '취소 뒤 지연 입금이 검토를 거쳐 REFUND_REQUESTED 상태로 전이되지 않았습니다.';
    end if;
    if (select payment_version from bodeul.appointment_bank_transfer_payments
        where appointment_request_id = '20000000-0000-0000-0000-000000000003') <> 3 then
        raise exception '예약 취소, 지연 입금 검토, 환불 요청이 결제 상세 버전에 반영되지 않았습니다.';
    end if;
    if (select status from bodeul.appointment_requests
        where id = '20000000-0000-0000-0000-000000000004') <> 'REQUESTED' then
        raise exception '원장 누락으로 실패한 예약 취소가 일부 반영됐습니다.';
    end if;
end;
$$;

\ir 023_mvp_booking_price_checks.sql
\ir 022_admin_bank_transfer_payment_read_checks.sql

rollback;
