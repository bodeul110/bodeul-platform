-- 승인 감사와 분리된 단기 예약 원문이다. Data API·관리자 웹에는 공개하지 않는다.
create table bodeul.guardian_booking_drafts (
    guardian_user_id uuid not null references bodeul.app_users (id),
    client_request_id uuid not null,
    patient_user_id uuid not null references bodeul.app_users (id),
    appointment_body jsonb,
    request_fingerprint text not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    purged_at timestamptz,
    primary key (guardian_user_id, client_request_id),
    constraint ck_guardian_booking_drafts_users check (patient_user_id <> guardian_user_id),
    constraint ck_guardian_booking_drafts_body check (
        (appointment_body is not null and purged_at is null
         and jsonb_typeof(appointment_body) = 'object' and octet_length(appointment_body::text) <= 65536)
        or (appointment_body is null and purged_at is not null and purged_at >= expires_at)),
    constraint ck_guardian_booking_drafts_request check (
        appointment_body ? 'clientRequestId'
        and jsonb_typeof(appointment_body -> 'clientRequestId') = 'string'
        and appointment_body ->> 'clientRequestId' = client_request_id::text),
    constraint ck_guardian_booking_drafts_fingerprint check (request_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint ck_guardian_booking_drafts_expiry check (
        expires_at > created_at and expires_at <= created_at + interval '24 hours')
);

comment on table bodeul.guardian_booking_drafts is
    '지정 환자 확인용 불변 예약 원문. 조회 만료와 파기 작업은 분리하며 승인 감사로 사용하지 않는다.';
comment on column bodeul.guardian_booking_drafts.purged_at is
    '원문 파기 시각. 요청 ID 재사용을 막는 참여자·해시·시각만 남기며 연락처·건강 원문은 NULL로 제거한다.';

create index ix_guardian_booking_drafts_patient on bodeul.guardian_booking_drafts (patient_user_id, created_at desc);
create index ix_guardian_booking_drafts_guardian_expiry on bodeul.guardian_booking_drafts (guardian_user_id, expires_at);
create index ix_guardian_booking_drafts_expiry on bodeul.guardian_booking_drafts (expires_at);

revoke all on table bodeul.guardian_booking_drafts
    from public, anon, authenticated, service_role, bodeul_admin_runtime, bodeul_retention_runtime;
grant select, insert on table bodeul.guardian_booking_drafts to bodeul_core_runtime;
alter table bodeul.guardian_booking_drafts enable row level security;
create policy guardian_booking_drafts_core_select on bodeul.guardian_booking_drafts
    for select to bodeul_core_runtime using (true);
create policy guardian_booking_drafts_core_insert on bodeul.guardian_booking_drafts
    for insert to bodeul_core_runtime with check (true);

create function bodeul.preview_expired_guardian_booking_drafts(p_as_of timestamptz)
returns bigint language sql stable security definer set search_path = pg_catalog, bodeul as $$
    select count(*) from bodeul.guardian_booking_drafts
    where appointment_body is not null and expires_at <= least(p_as_of, now()) and p_as_of is not null;
$$;

create function bodeul.purge_expired_guardian_booking_drafts(p_as_of timestamptz, p_limit integer)
returns bigint language plpgsql security definer set search_path = pg_catalog, bodeul as $$
declare
    v_deleted bigint;
begin
    if p_as_of is null or p_limit is null or p_limit < 1 or p_limit > 500 then
        raise exception '원문 파기 기준 시각과 배치 크기를 확인해 주세요.';
    end if;
    with candidates as (
        select guardian_user_id, client_request_id from bodeul.guardian_booking_drafts
        where appointment_body is not null and expires_at <= least(p_as_of, now())
        order by expires_at, guardian_user_id, client_request_id
        limit p_limit for update skip locked
    ), removed as (
        update bodeul.guardian_booking_drafts draft set appointment_body = null, purged_at = now()
        from candidates candidate
        where draft.guardian_user_id = candidate.guardian_user_id
          and draft.client_request_id = candidate.client_request_id returning 1
    ) select count(*) into v_deleted from removed;
    return v_deleted;
end;
$$;

alter function bodeul.preview_expired_guardian_booking_drafts(timestamptz) owner to bodeul_migration;
alter function bodeul.purge_expired_guardian_booking_drafts(timestamptz, integer) owner to bodeul_migration;
revoke all on function bodeul.preview_expired_guardian_booking_drafts(timestamptz)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
revoke all on function bodeul.purge_expired_guardian_booking_drafts(timestamptz, integer)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
grant execute on function bodeul.preview_expired_guardian_booking_drafts(timestamptz) to bodeul_retention_runtime;
grant execute on function bodeul.purge_expired_guardian_booking_drafts(timestamptz, integer) to bodeul_retention_runtime;
