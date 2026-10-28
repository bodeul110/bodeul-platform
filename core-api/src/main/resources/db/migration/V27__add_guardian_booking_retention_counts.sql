-- 새 파기 경로의 집계만 추가한다. 원문·계정 ID·연락처는 실행 이력에 남기지 않는다.
alter table bodeul.retention_job_runs
    add column guardian_draft_checked boolean not null default false,
    add column guardian_draft_candidates integer not null default 0,
    add column guardian_drafts_purged integer not null default 0,
    add column guardian_drafts_remaining integer not null default 0,
    add constraint ck_retention_job_runs_guardian_draft_counts check (
        guardian_draft_candidates >= 0 and guardian_drafts_purged >= 0 and guardian_drafts_remaining >= 0
    );

-- 응답 유실·프로세스 종료에도 원문 파기와 건수 이력이 함께 커밋되게 한다.
create function bodeul.purge_expired_guardian_booking_drafts(p_job_id uuid, p_limit integer)
returns bigint
language plpgsql
security definer
set search_path = pg_catalog, pg_temp
as $$
declare
    v_job bodeul.retention_job_runs%rowtype;
    v_candidates bigint;
    v_purged bigint;
    v_remaining bigint;
begin
    if p_job_id is null or p_limit is null or p_limit < 1 or p_limit > 500 then
        raise exception '파기 실행 ID와 배치 크기를 확인해 주세요.' using errcode = '22023';
    end if;
    select * into v_job from bodeul.retention_job_runs where id = p_job_id for update;
    if not found or v_job.mode <> 'APPLY' or v_job.status <> 'RUNNING' then
        raise exception '진행 중인 적용 실행에서만 원문을 파기할 수 있습니다.' using errcode = '22023';
    end if;

    v_candidates := case when v_job.guardian_draft_checked then v_job.guardian_draft_candidates
        else bodeul.preview_expired_guardian_booking_drafts(v_job.as_of) end;
    v_purged := bodeul.purge_expired_guardian_booking_drafts(v_job.as_of, p_limit);
    v_remaining := bodeul.preview_expired_guardian_booking_drafts(v_job.as_of);
    update bodeul.retention_job_runs
    set guardian_draft_checked = true,
        guardian_draft_candidates = v_candidates,
        guardian_drafts_purged = guardian_drafts_purged + v_purged,
        guardian_drafts_remaining = v_remaining
    where id = p_job_id;
    return v_purged;
end;
$$;

alter function bodeul.purge_expired_guardian_booking_drafts(uuid, integer) owner to bodeul_migration;
revoke all on function bodeul.purge_expired_guardian_booking_drafts(uuid, integer)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
grant execute on function bodeul.purge_expired_guardian_booking_drafts(uuid, integer)
    to bodeul_retention_runtime;
-- 이전 함수는 소유자 전용 내부 구현으로 남기고 집계 없는 runtime 파기를 막는다.
revoke all on function bodeul.purge_expired_guardian_booking_drafts(timestamptz, integer)
    from bodeul_retention_runtime;

create or replace function bodeul.finish_retention_job(
    p_job_id uuid,
    p_status text,
    p_finished_at timestamptz,
    p_counts jsonb,
    p_failure_stage text
) returns boolean
language plpgsql
security definer
set search_path = pg_catalog, pg_temp
as $$
declare
    v_updated_count integer;
begin
    if p_status not in ('COMPLETED', 'FAILED') then
        raise exception '지원하지 않는 파기 완료 상태입니다.' using errcode = '22023';
    end if;
    if p_failure_stage is not null and char_length(p_failure_stage) > 64 then
        raise exception '실패 단계 식별자는 64자 이하여야 합니다.' using errcode = '22023';
    end if;
    if p_counts is null or jsonb_typeof(p_counts) <> 'object'
            or not p_counts ?& array[
                'postgresMessageCandidates',
                'postgresAttachmentCandidates',
                'postgresLocationCandidates',
                'postgresLegalHoldSkips',
                'firestoreMessageCandidates',
                'firestoreAttachmentCandidates',
                'firestoreLocationCandidates',
                'firestoreLegalHoldSkips',
                'managerDocumentCandidates',
                'managerDocumentLegalHoldSkips',
                'messagesRedacted',
                'attachmentsDeleted',
                'attachmentDeleteFailures',
                'locationsDeleted',
                'firestoreMessagesRedacted',
                'firestoreAttachmentsDeleted',
                'firestoreAttachmentDeleteFailures',
                'firestoreLocationsCleared',
                'managerDocumentsDeleted',
                'managerDocumentDeleteFailures'
            ]
            or p_counts - array[
                'postgresMessageCandidates',
                'postgresAttachmentCandidates',
                'postgresLocationCandidates',
                'postgresLegalHoldSkips',
                'firestoreMessageCandidates',
                'firestoreAttachmentCandidates',
                'firestoreLocationCandidates',
                'firestoreLegalHoldSkips',
                'managerDocumentCandidates',
                'managerDocumentLegalHoldSkips',
                'adminAuditCandidates',
                'messagesRedacted',
                'attachmentsDeleted',
                'attachmentDeleteFailures',
                'locationsDeleted',
                'firestoreMessagesRedacted',
                'firestoreAttachmentsDeleted',
                'firestoreAttachmentDeleteFailures',
                'firestoreLocationsCleared',
                'managerDocumentsDeleted',
                'managerDocumentDeleteFailures',
                'adminAuditsDeleted',
                'guardianDraftCandidates',
                'guardianDraftsPurged',
                'guardianDraftsRemaining'
            ] <> '{}'::jsonb
            or (p_counts ?| array['guardianDraftCandidates', 'guardianDraftsPurged', 'guardianDraftsRemaining']
                and (not p_counts ?& array['guardianDraftCandidates', 'guardianDraftsPurged', 'guardianDraftsRemaining']
                    or exists (
                        select 1 from jsonb_each(p_counts) item
                        where item.key in ('guardianDraftCandidates', 'guardianDraftsPurged', 'guardianDraftsRemaining')
                          and jsonb_typeof(item.value) <> 'number'
                    )))
            or exists (
                select 1
                from jsonb_each_text(p_counts) item
                where item.value !~ '^[0-9]{1,9}$'
            ) then
        raise exception '파기 집계 형식이 올바르지 않습니다.' using errcode = '22023';
    end if;

    update bodeul.retention_job_runs run
    set status = p_status,
        finished_at = p_finished_at,
        postgres_message_candidates = (p_counts ->> 'postgresMessageCandidates')::integer,
        postgres_attachment_candidates = (p_counts ->> 'postgresAttachmentCandidates')::integer,
        postgres_location_candidates = (p_counts ->> 'postgresLocationCandidates')::integer,
        postgres_legal_hold_skips = (p_counts ->> 'postgresLegalHoldSkips')::integer,
        firestore_message_candidates = (p_counts ->> 'firestoreMessageCandidates')::integer,
        firestore_attachment_candidates = (p_counts ->> 'firestoreAttachmentCandidates')::integer,
        firestore_location_candidates = (p_counts ->> 'firestoreLocationCandidates')::integer,
        firestore_legal_hold_skips = (p_counts ->> 'firestoreLegalHoldSkips')::integer,
        manager_document_candidates = (p_counts ->> 'managerDocumentCandidates')::integer,
        manager_document_legal_hold_skips = (p_counts ->> 'managerDocumentLegalHoldSkips')::integer,
        admin_audit_candidates = coalesce((p_counts ->> 'adminAuditCandidates')::integer, 0),
        messages_redacted = (p_counts ->> 'messagesRedacted')::integer,
        attachments_deleted = (p_counts ->> 'attachmentsDeleted')::integer,
        attachment_delete_failures = (p_counts ->> 'attachmentDeleteFailures')::integer,
        locations_deleted = (p_counts ->> 'locationsDeleted')::integer,
        firestore_messages_redacted = (p_counts ->> 'firestoreMessagesRedacted')::integer,
        firestore_attachments_deleted = (p_counts ->> 'firestoreAttachmentsDeleted')::integer,
        firestore_attachment_delete_failures = (p_counts ->> 'firestoreAttachmentDeleteFailures')::integer,
        firestore_locations_cleared = (p_counts ->> 'firestoreLocationsCleared')::integer,
        manager_documents_deleted = (p_counts ->> 'managerDocumentsDeleted')::integer,
        manager_document_delete_failures = (p_counts ->> 'managerDocumentDeleteFailures')::integer,
        admin_audits_deleted = coalesce((p_counts ->> 'adminAuditsDeleted')::integer, 0),
        guardian_draft_checked = run.guardian_draft_checked or p_counts ? 'guardianDraftCandidates',
        guardian_draft_candidates = case when run.guardian_draft_checked then run.guardian_draft_candidates
            else coalesce((p_counts ->> 'guardianDraftCandidates')::integer, 0) end,
        guardian_drafts_purged = run.guardian_drafts_purged,
        guardian_drafts_remaining = case when run.guardian_draft_checked then run.guardian_drafts_remaining
            else coalesce((p_counts ->> 'guardianDraftsRemaining')::integer, 0) end,
        failure_stage = p_failure_stage
    where run.id = p_job_id and run.status = 'RUNNING';

    get diagnostics v_updated_count = row_count;
    return v_updated_count = 1;
end;
$$;

drop function bodeul.retention_monthly_summary(date);

create function bodeul.retention_monthly_summary(
    p_month_start date
) returns table (
    run_count bigint,
    failed_run_count bigint,
    message_candidates bigint,
    attachment_candidates bigint,
    location_candidates bigint,
    manager_document_candidates bigint,
    messages_redacted bigint,
    attachments_deleted bigint,
    attachment_delete_failures bigint,
    locations_deleted bigint,
    manager_documents_deleted bigint,
    manager_document_delete_failures bigint,
    legal_hold_skips bigint,
    admin_audit_candidates bigint,
    admin_audits_deleted bigint,
    guardian_draft_run_count bigint,
    guardian_draft_candidates bigint,
    guardian_drafts_purged bigint,
    guardian_drafts_remaining bigint
)
language sql
stable
security definer
set search_path = pg_catalog, pg_temp
as $$
    select
        count(*),
        count(*) filter (where run.status = 'FAILED'),
        coalesce(sum(run.postgres_message_candidates + run.firestore_message_candidates), 0),
        coalesce(sum(run.postgres_attachment_candidates + run.firestore_attachment_candidates), 0),
        coalesce(sum(run.postgres_location_candidates + run.firestore_location_candidates), 0),
        coalesce(sum(run.manager_document_candidates), 0),
        coalesce(sum(run.messages_redacted + run.firestore_messages_redacted), 0),
        coalesce(sum(run.attachments_deleted + run.firestore_attachments_deleted), 0),
        coalesce(sum(run.attachment_delete_failures + run.firestore_attachment_delete_failures), 0),
        coalesce(sum(run.locations_deleted + run.firestore_locations_cleared), 0),
        coalesce(sum(run.manager_documents_deleted), 0),
        coalesce(sum(run.manager_document_delete_failures), 0),
        coalesce(sum(
            run.postgres_legal_hold_skips
            + run.firestore_legal_hold_skips
            + run.manager_document_legal_hold_skips
        ), 0),
        coalesce(sum(run.admin_audit_candidates), 0),
        coalesce(sum(run.admin_audits_deleted), 0),
        count(*) filter (where run.guardian_draft_checked),
        coalesce(sum(run.guardian_draft_candidates), 0),
        coalesce(sum(run.guardian_drafts_purged), 0),
        coalesce((
            select latest.guardian_drafts_remaining::bigint
            from bodeul.retention_job_runs latest
            where latest.guardian_draft_checked
              and latest.started_at >= date_trunc('month', p_month_start::timestamp)
              and latest.started_at < date_trunc('month', p_month_start::timestamp) + interval '1 month'
            order by latest.started_at desc, latest.id desc limit 1
        ), 0)
    from bodeul.retention_job_runs run
    where run.started_at >= date_trunc('month', p_month_start::timestamp)
      and run.started_at < date_trunc('month', p_month_start::timestamp) + interval '1 month';
$$;

alter function bodeul.finish_retention_job(uuid, text, timestamptz, jsonb, text)
    owner to bodeul_migration;
alter function bodeul.retention_monthly_summary(date) owner to bodeul_migration;
revoke all on function bodeul.finish_retention_job(uuid, text, timestamptz, jsonb, text)
    from public, anon, authenticated, service_role, bodeul_core_runtime, bodeul_admin_runtime;
revoke all on function bodeul.retention_monthly_summary(date)
    from public, anon, authenticated, service_role, bodeul_core_runtime;
grant execute on function bodeul.finish_retention_job(uuid, text, timestamptz, jsonb, text)
    to bodeul_retention_runtime;
grant execute on function bodeul.retention_monthly_summary(date)
    to bodeul_retention_runtime, bodeul_admin_runtime;
