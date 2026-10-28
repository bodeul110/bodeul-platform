const assert = require("node:assert/strict");
const test = require("node:test");
const postgres = require("postgres");
const {PostgresRetentionRepository, runRetentionJob} = require("../src/retention");

async function withDatabase(run) {
  const host = process.env.PGHOST;
  assert.ok(["127.0.0.1", "localhost"].includes(host), "로컬 격리 DB에서만 실행합니다.");
  const options = {
    host,
    port: Number(process.env.PGPORT),
    database: "bodeul_guardian_booking_test",
    username: process.env.PGUSER,
    password: process.env.PGPASSWORD,
    max: 1,
    prepare: false,
    ssl: false,
    connect_timeout: 5,
    connection: {statement_timeout: 15000},
  };
  const owner = postgres(options);
  const runtime = postgres({...options, connection: {...options.connection, options: "-c role=bodeul_retention_runtime"}});
  try {
    const [database] = await owner`select current_database() as name`;
    assert.equal(database.name, "bodeul_guardian_booking_test");
    const [role] = await runtime`select current_user as name`;
    assert.equal(role.name, "bodeul_retention_runtime");
    // 전용 테스트 DB의 합성 기록만 교체한다. 실제 개발·운영 DB는 위 경계에서 거부한다.
    await owner`truncate bodeul.guardian_booking_drafts, bodeul.retention_job_runs`;
    const patient = "b2900000-0000-0000-0000-000000000001";
    const guardian = "b2900000-0000-0000-0000-000000000002";
    await owner`
      insert into bodeul.app_users (id, firebase_uid, role) values
        (${patient}::uuid, 'retention-node-patient', 'PATIENT'),
        (${guardian}::uuid, 'retention-node-guardian', 'GUARDIAN')
      on conflict (id) do nothing
    `;
    const seed = async (expired = 503, active = 1) => owner`
        insert into bodeul.guardian_booking_drafts
          (guardian_user_id, client_request_id, patient_user_id, appointment_body,
           request_fingerprint, created_at, expires_at)
        select ${guardian}::uuid, request_id, ${patient}::uuid,
          jsonb_build_object('clientRequestId', request_id::text, 'memo', '합성 원문'),
          repeat('a', 64), now() - interval '2 hours',
          case when fixture_id <= ${expired} then now() - interval '1 hour' else now() + interval '1 hour' end
        from (select fixture_id, gen_random_uuid() as request_id
          from generate_series(1, ${expired + active}) fixture_id) fixtures
      `;
    const repository = Object.create(PostgresRetentionRepository.prototype);
    repository.sql = runtime;
    repository.guardianBookingEnabled = true;
    // 이 테스트는 원문과 실행 이력만 다룬다. 다른 데이터·Firebase·Storage 파기는 대체한다.
    repository.preview = async () => ({messageCandidates: 0, attachmentCandidates: 0,
      locationCandidates: 0, legalHoldSkips: 0, adminAuditCandidates: 0});
    repository.claimAttachments = async () => [];
    repository.purgeCompanionRecords = async () => ({messagesRedacted: 0, locationsDeleted: 0});
    repository.purgeAdminAudits = async () => 0;
    const dependencies = {
      database: repository,
      legacyStore: {preview: async () => ({sessions: [], messageCandidates: 0,
        attachmentCandidates: 0, locationCandidates: 0, legalHoldSkips: 0})},
      managerStore: {preview: async () => ({candidates: [], legalHoldSkips: 0})},
      storage: {},
      guardianBookingEnabled: true,
    };
    await run({owner, runtime, repository, dependencies, seed});
  } finally {
    await runtime.end({timeout: 5});
    await owner.end({timeout: 5});
  }
}

test("격리 PostgreSQL에서 원문 dry-run·파기·재실행·집계·역할 경계를 검증한다", () =>
  withDatabase(async ({owner, runtime, repository, dependencies, seed}) => {
    await seed();
    const disabled = await runRetentionJob({...dependencies, guardianBookingEnabled: false, apply: true});
    assert.equal(Object.hasOwn(disabled, "guardianDraftCandidates"), false);
    const dryRun = await runRetentionJob({...dependencies, apply: false});
    assert.equal(dryRun.guardianDraftCandidates, 503);
    assert.equal(dryRun.guardianDraftsPurged, 0);
    const [before] = await owner`select count(appointment_body)::integer as bodies from bodeul.guardian_booking_drafts`;
    assert.equal(before.bodies, 504);
    const applied = await runRetentionJob({...dependencies, apply: true});
    assert.equal(applied.guardianDraftsPurged, 503);
    assert.equal(applied.guardianDraftsRemaining, 0);
    const [after] = await owner`
      select count(*)::integer as records, count(appointment_body)::integer as bodies,
        count(purged_at)::integer as purged from bodeul.guardian_booking_drafts
    `;
    assert.deepEqual({...after}, {records: 504, bodies: 1, purged: 503});
    const retried = await runRetentionJob({...dependencies, apply: true});
    assert.equal(retried.guardianDraftCandidates, 0);
    assert.equal(retried.guardianDraftsPurged, 0);
    const summary = await repository.monthlySummary(new Date());
    assert.equal(summary.guardianDraftRunCount, 3);
    assert.equal(summary.guardianDraftCandidates, 1006);
    assert.equal(summary.guardianDraftsPurged, 503);
    assert.equal(summary.guardianDraftsRemaining, 0);
    await assert.rejects(runtime`select appointment_body from bodeul.guardian_booking_drafts`,
        (error) => error.code === "42501");
    await assert.rejects(runtime`update bodeul.guardian_booking_drafts set appointment_body = null`,
        (error) => error.code === "42501");
  }));

test("DB 커밋 후 응답 유실과 새 실행 재시도에도 파기 집계가 누락되지 않는다", () =>
  withDatabase(async ({owner, repository, dependencies, seed}) => {
    await seed();
    const purge = repository.purgeGuardianDrafts.bind(repository);
    let loseResponse = true;
    repository.purgeGuardianDrafts = async (jobId, limit) => {
      const count = await purge(jobId, limit);
      if (loseResponse) {
        loseResponse = false;
        throw Object.assign(new Error("합성 커밋 후 응답 유실"), {code: "ECONNRESET"});
      }
      return count;
    };
    await assert.rejects(runRetentionJob({...dependencies, apply: true}),
        (error) => error.retentionFailureStage === "PURGE_GUARDIAN_DRAFTS");
    const [failed] = await owner`
      select status, guardian_draft_candidates as candidates, guardian_drafts_purged as purged,
        guardian_drafts_remaining as remaining from bodeul.retention_job_runs
    `;
    assert.deepEqual({...failed}, {status: "FAILED", candidates: 503, purged: 500, remaining: 3});
    assert.equal((await repository.monthlySummary(new Date())).guardianDraftsPurged, 500);
    const retried = await runRetentionJob({...dependencies, apply: true});
    assert.equal(retried.guardianDraftsPurged, 3);
    assert.equal(retried.guardianDraftsRemaining, 0);
    assert.equal((await repository.monthlySummary(new Date())).guardianDraftsPurged, 503);
    const [after] = await owner`select count(appointment_body)::integer as bodies from bodeul.guardian_booking_drafts`;
    assert.equal(after.bodies, 1);
  }));

test("완료 기록 전 프로세스 중단과 같은 실행·새 실행 재시도에서 집계를 보존한다", () =>
  withDatabase(async ({owner, runtime, repository, dependencies, seed}) => {
    await seed();
    const now = new Date();
    const jobId = await repository.beginJob("APPLY", now, now);
    assert.equal(await repository.purgeGuardianDrafts(jobId, 500), 500);
    // 종료 처리 자체가 실행되지 않는 프로세스 중단을 재현한다.
    const [unfinished] = await owner`
      select status, guardian_drafts_purged as purged from bodeul.retention_job_runs where id = ${jobId}::uuid
    `;
    assert.deepEqual({...unfinished}, {status: "RUNNING", purged: 500});
    assert.equal((await repository.monthlySummary(now)).guardianDraftsPurged, 500);
    // 응답을 못 받은 동일 실행이 재호출돼도 이미 파기한 행을 중복 집계하지 않는다.
    assert.equal(await repository.purgeGuardianDrafts(jobId, 500), 3);
    assert.equal(await repository.purgeGuardianDrafts(jobId, 500), 0);
    const retryRepository = Object.assign(Object.create(PostgresRetentionRepository.prototype), repository);
    const retried = await runRetentionJob({...dependencies, database: retryRepository, apply: true});
    assert.equal(retried.guardianDraftsPurged, 0);
    assert.equal((await repository.monthlySummary(new Date())).guardianDraftsPurged, 503);
    await assert.rejects(runtime`select bodeul.purge_expired_guardian_booking_drafts(now(), 500)`,
        (error) => error.code === "42501");
  }));
