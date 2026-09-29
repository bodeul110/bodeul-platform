-- 예약 전 생성 승인이다. V17의 예약 이후 정보공유 동의와 권한을 섞지 않는다.
create table bodeul.guardian_booking_approvals (
    patient_user_id uuid not null references bodeul.app_users (id),
    guardian_user_id uuid not null references bodeul.app_users (id),
    client_request_id uuid not null,
    version bigint not null,
    grant_id uuid not null,
    request_fingerprint text not null,
    policy_version text not null,
    granted_by_user_id uuid not null references bodeul.app_users (id),
    granted_at timestamptz not null,
    expires_at timestamptz not null,
    revoked_by_user_id uuid references bodeul.app_users (id),
    revoked_at timestamptz,
    primary key (patient_user_id, guardian_user_id, client_request_id),
    constraint ck_guardian_booking_approvals_users check (patient_user_id <> guardian_user_id),
    constraint ck_guardian_booking_approvals_version check (version >= 1),
    constraint ck_guardian_booking_approvals_fingerprint check (request_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint ck_guardian_booking_approvals_policy
        check (policy_version = btrim(policy_version) and policy_version <> ''),
    constraint ck_guardian_booking_approvals_patient_grant check (granted_by_user_id = patient_user_id),
    constraint ck_guardian_booking_approvals_expiry check (expires_at > granted_at),
    constraint ck_guardian_booking_approvals_revocation check (
        (revoked_by_user_id is null and revoked_at is null)
        or (revoked_by_user_id is not null and revoked_at is not null
            and revoked_by_user_id = patient_user_id and revoked_at >= granted_at)
    )
);

-- 승인 ID는 재승인마다 교체되므로 과거 이벤트는 현재 grant_id가 아닌 요청 키를 참조한다.
create table bodeul.guardian_booking_approval_events (
    patient_user_id uuid not null,
    guardian_user_id uuid not null,
    client_request_id uuid not null,
    version bigint not null,
    grant_id uuid not null,
    request_fingerprint text not null,
    policy_version text not null,
    granted_by_user_id uuid not null,
    granted_at timestamptz not null,
    expires_at timestamptz not null,
    revoked_by_user_id uuid,
    revoked_at timestamptz,
    action text not null,
    actor_user_id uuid not null,
    occurred_at timestamptz not null,
    primary key (patient_user_id, guardian_user_id, client_request_id, version),
    foreign key (patient_user_id, guardian_user_id, client_request_id)
        references bodeul.guardian_booking_approvals (patient_user_id, guardian_user_id, client_request_id),
    constraint ck_guardian_booking_events_version check (version >= 1),
    constraint ck_guardian_booking_events_fingerprint check (request_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint ck_guardian_booking_events_policy
        check (policy_version = btrim(policy_version) and policy_version <> ''),
    constraint ck_guardian_booking_events_actor
        check (actor_user_id = patient_user_id and granted_by_user_id = patient_user_id),
    constraint ck_guardian_booking_events_expiry check (expires_at > granted_at),
    constraint ck_guardian_booking_events_action check (
        (action = 'GRANTED' and revoked_at is null and revoked_by_user_id is null
            and occurred_at = granted_at)
        or (action = 'REVOKED' and revoked_at is not null and revoked_by_user_id is not null
            and revoked_by_user_id = patient_user_id and revoked_at >= granted_at
            and occurred_at = revoked_at)
    )
);

comment on table bodeul.guardian_booking_approvals is
    '성인 환자 본인의 보호자 예약 생성 승인 최신 상태. 정보공유 동의나 예약 변경 권한이 아니다.';
comment on table bodeul.guardian_booking_approval_events is
    '예약 생성 승인·재승인·철회의 추가 전용 감사 snapshot. Core runtime은 수정·삭제할 수 없다.';
comment on column bodeul.guardian_booking_approvals.request_fingerprint is
    '서버가 정규화된 예약 생성 본문으로 계산한 SHA-256. 원문을 저장하거나 클라이언트 값을 신뢰하지 않는다.';
comment on column bodeul.guardian_booking_approvals.granted_at is
    '정책 판정에서 성인 환자 본인 확인과 예약 본문 승인을 함께 확인한 시각';

-- 사용자 FK의 조회·향후 계정 영향도 집계를 위한 인덱스다. 환자 키는 PK 첫 열이 덮는다.
create index ix_guardian_booking_approvals_guardian
    on bodeul.guardian_booking_approvals (guardian_user_id);
create index ix_guardian_booking_approvals_granted_by
    on bodeul.guardian_booking_approvals (granted_by_user_id);
create index ix_guardian_booking_approvals_revoked_by
    on bodeul.guardian_booking_approvals (revoked_by_user_id) where revoked_by_user_id is not null;

revoke all on table bodeul.guardian_booking_approvals, bodeul.guardian_booking_approval_events
    from public, anon, authenticated, service_role, bodeul_admin_runtime;
grant select, insert, update on table bodeul.guardian_booking_approvals to bodeul_core_runtime;
grant select, insert on table bodeul.guardian_booking_approval_events to bodeul_core_runtime;

alter table bodeul.guardian_booking_approvals enable row level security;
alter table bodeul.guardian_booking_approval_events enable row level security;
create policy guardian_booking_approvals_core_select on bodeul.guardian_booking_approvals
    for select to bodeul_core_runtime using (true);
create policy guardian_booking_approvals_core_insert on bodeul.guardian_booking_approvals
    for insert to bodeul_core_runtime with check (true);
create policy guardian_booking_approvals_core_update on bodeul.guardian_booking_approvals
    for update to bodeul_core_runtime using (true) with check (true);
create policy guardian_booking_events_core_select on bodeul.guardian_booking_approval_events
    for select to bodeul_core_runtime using (true);
create policy guardian_booking_events_core_insert on bodeul.guardian_booking_approval_events
    for insert to bodeul_core_runtime with check (true);
