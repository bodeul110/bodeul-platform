const assert = require("node:assert/strict");
const test = require("node:test");

const {
  FirebaseLegacyCompanionStore,
  FirebaseManagerDocumentStore,
  FirebaseStorageGateway,
  PostgresRetentionRepository,
  evaluateManagerDocument,
  evaluateLegacyCompanionSession,
  isAllowedChatAttachmentPath,
  isAllowedManagerDocumentPath,
  previousMonthStart,
  postgresConnectionOptions,
  retentionApplyEnabled,
  retentionGuardianBookingEnabled,
  retentionCounts,
  runRetentionJob,
} = require("../src/retention");
const {
  MANAGER_DOCUMENT_DELETION_CLAIM_FIELD,
  buildManagerDocumentDeletionClaim,
} = require("../src/manager-document-deletion");
const {
  createTransactionalDocument,
} = require("./manager-document-deletion-test-helper");

test("PostgreSQL 연결은 Supabase CA 검증을 강제한다", () => {
  const ca = "-----BEGIN CERTIFICATE-----\nfixture\n-----END CERTIFICATE-----";
  const options = postgresConnectionOptions(ca);

  assert.equal(options.ssl.ca, ca);
  assert.equal(options.ssl.rejectUnauthorized, true);
  assert.equal(options.prepare, false);
  assert.equal(options.max, 1);
});

test("Supabase CA가 없거나 PEM 형식이 아니면 연결 구성을 거부한다", () => {
  assert.throws(
      () => postgresConnectionOptions(""),
      (error) => error.code === "DATABASE_CA_CONFIG_INVALID",
  );
  assert.throws(
      () => postgresConnectionOptions("not-a-certificate"),
      (error) => error.code === "DATABASE_CA_CONFIG_INVALID",
  );
});

test("DB 집계 payload는 계약 키만 남기고 안전한 정수로 정규화한다", () => {
  const counts = retentionCounts({
    mode: "DRY_RUN",
    asOf: "2026-07-19T00:00:00.000Z",
    postgresMessageCandidates: "3",
    adminAuditCandidates: "5",
    adminAuditsDeleted: "2",
    attachmentsDeleted: -1,
    unexpected: 100,
  });

  assert.equal(counts.postgresMessageCandidates, 3);
  assert.equal(counts.attachmentsDeleted, 0);
  assert.equal(counts.managerDocumentDeleteFailures, 0);
  assert.equal(counts.adminAuditCandidates, 5);
  assert.equal(counts.adminAuditsDeleted, 2);
  assert.equal(Object.hasOwn(counts, "mode"), false);
  assert.equal(Object.hasOwn(counts, "unexpected"), false);
  assert.equal(Object.keys(counts).length, 22);
});

test("월간 보고는 관리자 감사 후보·삭제 집계를 함께 반환한다", async () => {
  const repository = Object.create(PostgresRetentionRepository.prototype);
  repository.sql = async () => [{
    run_count: "2",
    failed_run_count: "1",
    admin_audit_candidates: "15",
    admin_audits_deleted: "12",
  }];

  const summary = await repository.monthlySummary(new Date("2026-07-01T00:00:00.000Z"));

  assert.equal(summary.month, "2026-07");
  assert.equal(summary.runCount, 2);
  assert.equal(summary.failedRunCount, 1);
  assert.equal(summary.adminAuditCandidates, 15);
  assert.equal(summary.adminAuditsDeleted, 12);
});

test("정기 파기는 true를 명시한 환경에서만 활성화한다", () => {
  assert.equal(retentionApplyEnabled("true"), true);
  assert.equal(retentionApplyEnabled(" TRUE "), true);
  assert.equal(retentionApplyEnabled("false"), false);
  assert.equal(retentionApplyEnabled("1"), false);
  assert.equal(retentionApplyEnabled(undefined), false);
});

function createDatabase(overrides = {}) {
  const calls = [];
  return {
    calls,
    async beginJob() {
      calls.push("begin");
      return "5a246e0e-e543-4fd4-936e-58e07b478247";
    },
    async preview() {
      calls.push("preview");
      return {
        messageCandidates: 2,
        attachmentCandidates: 1,
        locationCandidates: 3,
        legalHoldSkips: 4,
        adminAuditCandidates: 5,
      };
    },
    async claimAttachments() {
      calls.push("claimAttachments");
      return [];
    },
    async markAttachmentDeleted() {
      calls.push("markAttachmentDeleted");
      return true;
    },
    async purgeCompanionRecords() {
      calls.push("purgeCompanionRecords");
      return {messagesRedacted: 2, locationsDeleted: 3};
    },
    async purgeAdminAudits() {
      calls.push("purgeAdminAudits");
      return 5;
    },
    async finishJob() {
      calls.push("finish");
      return true;
    },
    ...overrides,
  };
}

function createManagerStore(overrides = {}) {
  const calls = [];
  return {
    calls,
    async preview() {
      calls.push("preview");
      return {candidates: [], legalHoldSkips: 0};
    },
    async deleteCandidate(candidate, _asOf, storage) {
      calls.push("deleteCandidate");
      await storage.deleteManagerDocument(
          candidate.storagePath,
          candidate.managerId,
          candidate.documentKey,
          "1",
      );
      return {status: "COMPLETED"};
    },
    ...overrides,
  };
}

function createLegacyStore(overrides = {}) {
  const calls = [];
  return {
    calls,
    async preview() {
      calls.push("preview");
      return {
        sessions: [],
        messageCandidates: 0,
        attachmentCandidates: 0,
        locationCandidates: 0,
        legalHoldSkips: 0,
      };
    },
    async applySession() {
      calls.push("applySession");
      return {
        messagesRedacted: 0,
        attachmentsDeleted: 0,
        attachmentDeleteFailures: 0,
        locationsCleared: 0,
      };
    },
    ...overrides,
  };
}

test("dry-run은 후보 수만 기록하고 삭제 함수를 호출하지 않는다", async () => {
  const database = createDatabase();
  const legacyStore = createLegacyStore();
  const managerStore = createManagerStore();
  const storage = {
    async deleteChatAttachment() {
      assert.fail("dry-run에서 Storage를 삭제하면 안 됩니다.");
    },
    async deleteManagerDocument() {
      assert.fail("dry-run에서 Storage를 삭제하면 안 됩니다.");
    },
  };

  const summary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: false,
    now: new Date("2026-07-18T00:00:00.000Z"),
  });

  assert.equal(summary.mode, "DRY_RUN");
  assert.equal(summary.postgresMessageCandidates, 2);
  assert.equal(summary.postgresAttachmentCandidates, 1);
  assert.equal(summary.postgresLocationCandidates, 3);
  assert.equal(summary.postgresLegalHoldSkips, 4);
  assert.equal(summary.adminAuditCandidates, 5);
  assert.equal(summary.adminAuditsDeleted, 0);
  assert.deepEqual(database.calls, ["begin", "preview", "finish"]);
});

test("관리자 감사기록은 1년 경과 후보만 500건 단위로 끝까지 파기한다", async () => {
  const batches = [500, 2];
  const database = createDatabase({
    async preview() {
      return {
        messageCandidates: 0,
        attachmentCandidates: 0,
        locationCandidates: 0,
        legalHoldSkips: 0,
        adminAuditCandidates: 502,
      };
    },
    async purgeAdminAudits() {
      return batches.shift() ?? 0;
    },
  });

  const summary = await runRetentionJob({
    database,
    legacyStore: createLegacyStore(),
    managerStore: createManagerStore(),
    storage: {
      async deleteChatAttachment() {},
      async deleteManagerDocument() {},
    },
    apply: true,
    now: new Date("2026-07-18T00:00:00.000Z"),
  });

  assert.equal(summary.adminAuditCandidates, 502);
  assert.equal(summary.adminAuditsDeleted, 502);
  assert.equal(batches.length, 0);
});

test("보호자 원문 파기는 별도 true 플래그가 있어야 활성화한다", () => {
  for (const value of ["", "false", "1", true, null]) {
    assert.equal(retentionGuardianBookingEnabled(value), false);
  }
  assert.equal(retentionGuardianBookingEnabled(" TRUE "), true);
});

function guardianRetentionDependencies(overrides = {}) {
  return {
    database: createDatabase(overrides),
    legacyStore: createLegacyStore(),
    managerStore: createManagerStore(),
    storage: {},
    apply: false,
    guardianBookingEnabled: true,
    now: new Date("2026-10-28T01:00:00.000Z"),
  };
}

test("새 경로 OFF는 APPLY에서도 V26 조회·파기와 새 집계 키를 요구하지 않는다", async () => {
  const dependencies = guardianRetentionDependencies({
    async previewGuardianDrafts() { assert.fail("비활성 경로 조회"); },
    async purgeGuardianDrafts() { assert.fail("비활성 경로 파기"); },
  });
  for (const enabled of [false, "true", null]) {
    const summary = await runRetentionJob({...dependencies, apply: true, guardianBookingEnabled: enabled});
    assert.equal(Object.keys(retentionCounts(summary)).length, 22);
    assert.equal(Object.hasOwn(summary, "guardianDraftCandidates"), false);
  }
});

test("보호자 원문 dry-run은 후보만 보고하고 원문과 Storage를 변경하지 않는다", async () => {
  const dependencies = guardianRetentionDependencies({
    async previewGuardianDrafts(asOf) {
      assert.equal(asOf.toISOString(), "2026-10-28T01:00:00.000Z");
      return 7;
    },
    async purgeGuardianDrafts() { assert.fail("dry-run 파기"); },
  });
  for (const apply of [false, "false", "true"]) {
    const summary = await runRetentionJob({...dependencies, apply});
    assert.equal(summary.mode, "DRY_RUN");
    assert.equal(summary.guardianDraftCandidates, 7);
    assert.equal(summary.guardianDraftsPurged, 0);
    assert.equal(summary.guardianDraftsRemaining, 7);
    const counts = retentionCounts({...summary, patientId: "민감정보", appointmentBody: {memo: "원문"}});
    assert.equal(Object.keys(counts).length, 25);
    assert.equal(Object.hasOwn(counts, "patientId"), false);
    assert.equal(Object.hasOwn(counts, "appointmentBody"), false);
  }
});

test("보호자 원문은 기준 시각을 가진 같은 실행 ID의 500건 배치로 처리한다", async () => {
  const batches = [500, 2];
  let previews = 0;
  const dependencies = guardianRetentionDependencies({
    async previewGuardianDrafts() { return previews++ === 0 ? 502 : 0; },
    async beginJob(mode, asOf) {
      assert.equal(mode, "APPLY");
      assert.equal(asOf.toISOString(), "2026-10-28T01:00:00.000Z");
      return "5a246e0e-e543-4fd4-936e-58e07b478247";
    },
    async purgeGuardianDrafts(jobId, limit) {
      assert.equal(jobId, "5a246e0e-e543-4fd4-936e-58e07b478247");
      assert.equal(limit, 500);
      return batches.shift();
    },
  });
  const summary = await runRetentionJob({...dependencies, apply: true});
  assert.equal(summary.guardianDraftCandidates, 502);
  assert.equal(summary.guardianDraftsPurged, 502);
  assert.equal(summary.guardianDraftsRemaining, 0);
  assert.equal(previews, 2);
  assert.equal(batches.length, 0);
});

test("보호자 원문은 실행당 10배치 상한을 지키고 미처리 건수를 숨기지 않는다", async () => {
  let calls = 0;
  const dependencies = guardianRetentionDependencies({
    async previewGuardianDrafts() { return 5500 - calls * 500; },
    async purgeGuardianDrafts() { calls += 1; return 500; },
  });
  const summary = await runRetentionJob({...dependencies, apply: true});
  assert.equal(calls, 10);
  assert.equal(summary.guardianDraftsPurged, 5000);
  assert.equal(summary.guardianDraftsRemaining, 500);
});

test("잠긴 원문 때문에 0건을 처리해도 남은 후보를 다시 보고한다", async () => {
  let calls = 0;
  const summary = await runRetentionJob({...guardianRetentionDependencies({
    async previewGuardianDrafts() { return 3; },
    async purgeGuardianDrafts() { calls += 1; return 0; },
  }), apply: true});
  assert.equal(calls, 1);
  assert.equal(summary.guardianDraftsRemaining, 3);
});

test("V27 사전 조회 실패는 삭제 전 중단하고 기존 집계 계약으로 실패를 남긴다", async () => {
  const records = [];
  const dependencies = guardianRetentionDependencies({
    async previewGuardianDrafts() { throw Object.assign(new Error("fixture"), {code: "42703"}); },
    async purgeGuardianDrafts() { assert.fail("미적용 DB 파기"); },
    async finishJob(_id, status, _at, summary, stage) {
      records.push({status, stage, keys: Object.keys(retentionCounts(summary)).length});
      return true;
    },
  });
  await assert.rejects(runRetentionJob({...dependencies, apply: true}),
      (error) => error.retentionFailureStage === "PREVIEW_GUARDIAN_DRAFTS");
  assert.deepEqual(records, [{status: "FAILED", stage: "PREVIEW_GUARDIAN_DRAFTS", keys: 22}]);
  assert.deepEqual(dependencies.database.calls, ["begin", "preview"]);
});

test("중간 배치 실패는 처리 건수를 보존하고 다음 실행에서 남은 원문만 재시도한다", async () => {
  let remaining = 502;
  let failOnce = true;
  const records = [];
  const dependencies = guardianRetentionDependencies({
    async previewGuardianDrafts() { return remaining; },
    async purgeGuardianDrafts() {
      if (remaining === 2 && failOnce) {
        failOnce = false;
        throw new Error("fixture");
      }
      const purged = Math.min(remaining, 500);
      remaining -= purged;
      return purged;
    },
    async finishJob(_id, status, _at, summary, stage) {
      records.push({status, stage, purged: summary.guardianDraftsPurged});
      return true;
    },
  });
  await assert.rejects(runRetentionJob({...dependencies, apply: true}),
      (error) => error.retentionFailureStage === "PURGE_GUARDIAN_DRAFTS");
  assert.equal(remaining, 2);
  const second = await runRetentionJob({...dependencies, apply: true});
  assert.equal(second.guardianDraftsPurged, 2);
  assert.equal(second.guardianDraftsRemaining, 0);
  assert.deepEqual(records, [
    {status: "FAILED", stage: "PURGE_GUARDIAN_DRAFTS", purged: 500},
    {status: "COMPLETED", stage: undefined, purged: 2},
  ]);
});

test("마지막 후보 재조회 실패는 전체 파기 완료로 기록하지 않는다", async () => {
  let calls = 0;
  let status;
  const dependencies = guardianRetentionDependencies({
    async previewGuardianDrafts() { if (calls++ > 0) throw new Error("fixture"); return 2; },
    async purgeGuardianDrafts() { return 2; },
    async finishJob(_id, value) { status = value; return true; },
  });
  await assert.rejects(runRetentionJob({...dependencies, apply: true}),
      (error) => error.retentionFailureStage === "RECHECK_GUARDIAN_DRAFTS");
  assert.equal(status, "FAILED");
});

test("보호자 파기 SQL은 V27 집계 계약을 먼저 검사하고 값은 바인딩한다", async () => {
  const repository = Object.create(PostgresRetentionRepository.prototype);
  const queries = [];
  repository.sql = async (parts, ...values) => {
    const sql = parts.join("?");
    queries.push({sql, values});
    return sql.includes("candidate_count") ? [{candidate_count: "7"}] : [{purged_count: "2"}];
  };
  const asOf = new Date("2026-10-28T01:00:00.000Z");
  const jobId = "5a246e0e-e543-4fd4-936e-58e07b478247";
  assert.equal(await repository.previewGuardianDrafts(asOf), 7);
  assert.equal(await repository.purgeGuardianDrafts(jobId, 500), 2);
  assert.match(queries[0].sql, /guardian_drafts_remaining[\s\S]*limit 0/);
  assert.match(queries[1].sql, /preview_expired_guardian_booking_drafts/);
  assert.deepEqual(queries[2].values, [jobId, 500]);
  assert.match(queries[2].sql, /\?::uuid/);
  assert.equal(queries.some(({sql}) => sql.includes("appointment_body")), false);
});

test("누락·음수·비정상 파기 건수는 0건 성공으로 숨기지 않는다", async () => {
  const repository = Object.create(PostgresRetentionRepository.prototype);
  for (const value of [undefined, null, -1, 1.5, "", "NaN", "1.2", 501]) {
    repository.sql = async () => [{purged_count: value}];
    await assert.rejects(repository.purgeGuardianDrafts("5a246e0e-e543-4fd4-936e-58e07b478247", 500),
        (error) => error.code === "GUARDIAN_DRAFT_COUNT_INVALID");
  }
});

test("보호자 월간 보고는 활성 실행 수와 마지막 잔여 건수를 제공한다", async () => {
  const repository = Object.create(PostgresRetentionRepository.prototype);
  repository.guardianBookingEnabled = true;
  repository.sql = async () => [{
    guardian_draft_run_count: "3", guardian_draft_candidates: "12",
    guardian_drafts_purged: "7", guardian_drafts_remaining: "0",
  }];
  const summary = await repository.monthlySummary(new Date("2026-10-01T00:00:00Z"));
  assert.equal(summary.guardianDraftRunCount, 3);
  assert.equal(summary.guardianDraftCandidates, 12);
  assert.equal(summary.guardianDraftsPurged, 7);
  assert.equal(summary.guardianDraftsRemaining, 0);
  repository.guardianBookingEnabled = false;
  assert.equal((await repository.monthlySummary(new Date())).guardianDraftRunCount, 3);
  repository.guardianBookingEnabled = true;
  repository.sql = async () => [{}];
  await assert.rejects(repository.monthlySummary(new Date()),
      (error) => error.code === "GUARDIAN_DRAFT_COUNT_INVALID");
});

test("PostgreSQL 첨부 일부 삭제 실패는 참조를 유지하고 다음 실행에서 재시도한다", async () => {
  const candidates = [
    {
      id: "50de4226-df48-4622-9de8-c292c3fc0ed9",
      storagePath: "companion-chat-attachments/728916a2-d57e-4e8f-bd99-c6c47498b4ba/retry.pdf",
    },
    {
      id: "04e413bf-8b76-4d1d-954e-1ca8ffb64c24",
      storagePath: "companion-chat-attachments/728916a2-d57e-4e8f-bd99-c6c47498b4ba/success.pdf",
    },
  ];
  const markedIds = new Set();
  const finishes = [];
  const database = createDatabase({
    async claimAttachments() {
      return candidates.filter((candidate) => !markedIds.has(candidate.id));
    },
    async markAttachmentDeleted(candidate) {
      markedIds.add(candidate.id);
      return true;
    },
    async finishJob(_jobId, status, _finishedAt, summary, failureStage) {
      finishes.push({
        status,
        attachmentsDeleted: summary.attachmentsDeleted,
        attachmentDeleteFailures: summary.attachmentDeleteFailures,
        failureStage: failureStage || null,
      });
      return true;
    },
  });
  const legacyStore = createLegacyStore();
  const managerStore = createManagerStore();
  const attempts = new Map();
  const storage = {
    async deleteChatAttachment(storagePath) {
      const attempt = (attempts.get(storagePath) || 0) + 1;
      attempts.set(storagePath, attempt);
      if (storagePath.endsWith("retry.pdf") && attempt === 1) {
        throw new Error("storage unavailable");
      }
    },
    async deleteManagerDocument() {},
  };

  const firstSummary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: true,
    now: new Date("2026-07-18T00:00:00.000Z"),
  });

  assert.equal(firstSummary.attachmentsDeleted, 1);
  assert.equal(firstSummary.attachmentDeleteFailures, 1);
  assert.deepEqual([...markedIds], [candidates[1].id]);

  const secondSummary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: true,
    now: new Date("2026-07-19T00:00:00.000Z"),
  });

  assert.equal(secondSummary.attachmentsDeleted, 1);
  assert.equal(secondSummary.attachmentDeleteFailures, 0);
  assert.equal(markedIds.size, 2);
  assert.equal(attempts.get(candidates[0].storagePath), 2);
  assert.equal(attempts.get(candidates[1].storagePath), 1);
  assert.deepEqual(finishes, [
    {
      status: "COMPLETED",
      attachmentsDeleted: 1,
      attachmentDeleteFailures: 1,
      failureStage: null,
    },
    {
      status: "COMPLETED",
      attachmentsDeleted: 1,
      attachmentDeleteFailures: 0,
      failureStage: null,
    },
  ]);
});

test("관리자 증빙은 심사 후 30일이 지나고 법적 보존이 없을 때만 후보가 된다", () => {
  const now = new Date("2026-07-18T00:00:00.000Z");
  const baseData = {
    role: "MANAGER",
    managerDocumentStatus: "APPROVED",
    managerDocumentReviewedAt: Date.parse("2026-06-01T00:00:00.000Z"),
    managerDocumentUpdatedAt: Date.parse("2026-05-31T00:00:00.000Z"),
    managerDocumentFiles: {
      license: {
        fullPath: "manager-documents/manager-1/license/license.pdf",
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {
      license: "manager-documents/manager-1/license/license.pdf",
    },
    managerLicenseStoragePath: "manager-documents/manager-1/license/license.pdf",
  };

  const eligible = evaluateManagerDocument("manager-1", baseData, now);
  assert.equal(eligible.candidates.length, 1);
  assert.equal(eligible.legalHoldSkips, 0);

  const reviewedAt = Date.parse("2026-06-01T00:00:00.000Z");
  assert.equal(evaluateManagerDocument(
      "manager-1",
      baseData,
      new Date(reviewedAt + (30 * 24 * 60 * 60 * 1000) - 1),
  ).candidates.length, 0);
  assert.equal(evaluateManagerDocument(
      "manager-1",
      baseData,
      new Date(reviewedAt + (30 * 24 * 60 * 60 * 1000)),
  ).candidates.length, 1);

  const held = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentLegalHoldUntil: Date.parse("2026-08-01T00:00:00.000Z"),
    managerDocumentLegalHoldReason: "분쟁 대응",
    managerDocumentLegalHoldByAdminUserId: "admin-1",
  }, now);
  assert.equal(held.candidates.length, 0);
  assert.equal(held.legalHoldSkips, 1);

  const incompleteHold = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentLegalHoldReason: "분쟁 대응",
  }, now);
  assert.equal(incompleteHold.candidates.length, 0);
  assert.equal(incompleteHold.legalHoldSkips, 1);

  const invalidHold = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentLegalHoldUntil: "invalid",
  }, now);
  assert.equal(invalidHold.candidates.length, 0);
  assert.equal(invalidHold.legalHoldSkips, 1);

  const expiredHold = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentLegalHoldUntil: Date.parse("2026-07-17T23:59:59.999Z"),
    managerDocumentLegalHoldReason: "만료된 보존",
    managerDocumentLegalHoldByAdminUserId: "admin-1",
  }, now);
  assert.equal(expiredHold.candidates.length, 1);
  assert.equal(expiredHold.legalHoldSkips, 0);

  const otherManagerPath = "manager-documents/manager-2/license/license.pdf";
  const crossOwner = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      license: {...baseData.managerDocumentFiles.license, fullPath: otherManagerPath},
    },
    managerDocumentFilePaths: {license: otherManagerPath},
    managerLicenseStoragePath: otherManagerPath,
  }, now);
  assert.equal(crossOwner.candidates.length, 0);

  const whitespaceOwner = evaluateManagerDocument(" manager-1 ", baseData, now);
  assert.equal(whitespaceOwner.candidates.length, 0);

  const whitespacePath = " manager-documents/manager-1/license/license.pdf ";
  const whitespaceAliases = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      license: {...baseData.managerDocumentFiles.license, fullPath: whitespacePath},
    },
    managerDocumentFilePaths: {license: whitespacePath},
    managerLicenseStoragePath: whitespacePath,
  }, now);
  assert.equal(whitespaceAliases.candidates.length, 0);

  const wrongDocumentKeyPath = "manager-documents/manager-1/idCard/license.pdf";
  const crossDocumentKey = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      license: {
        ...baseData.managerDocumentFiles.license,
        fullPath: wrongDocumentKeyPath,
      },
    },
    managerDocumentFilePaths: {license: wrongDocumentKeyPath},
    managerLicenseStoragePath: wrongDocumentKeyPath,
  }, now);
  assert.equal(crossDocumentKey.candidates.length, 0);

  const mismatchedAliases = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFilePaths: {
      license: "manager-documents/manager-1/license/path-map-mismatch.pdf",
    },
  }, now);
  assert.equal(mismatchedAliases.candidates.length, 0);

  const missingLegacyAlias = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerLicenseStoragePath: "",
  }, now);
  assert.equal(missingLegacyAlias.candidates.length, 0);

  const missingPathMapAlias = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFilePaths: {},
  }, now);
  assert.equal(missingPathMapAlias.candidates.length, 0);

  const nursingLicensePath =
    "manager-documents/manager-1/nursingLicense/nursing-license.jpg";
  const nursingLicense = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      nursingLicense: {
        fullPath: nursingLicensePath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {nursingLicense: nursingLicensePath},
    managerLicenseStoragePath: "",
  }, now);
  assert.deepEqual(
      nursingLicense.candidates.map(({documentKey}) => documentKey),
      ["nursingLicense"],
  );

  const healthCertificatePath =
    "manager-documents/manager-1/healthCertificate/health.jpg";
  const healthCertificate = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      healthCertificate: {
        fullPath: healthCertificatePath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {healthCertificate: healthCertificatePath},
    managerLicenseStoragePath: "",
  }, now);
  assert.equal(healthCertificate.candidates.length, 1);

  const mismatchedHealthCertificateAlias = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      healthCertificate: {
        fullPath: healthCertificatePath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {healthCertificate: healthCertificatePath},
    managerHealthCertificateStoragePath:
      "manager-documents/manager-1/healthCertificate/mismatch.jpg",
    managerLicenseStoragePath: "",
  }, now);
  assert.equal(mismatchedHealthCertificateAlias.candidates.length, 0);

  const incompleteHealthCertificate = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      healthCertificate: {
        fullPath: healthCertificatePath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {},
    managerHealthCertificateStoragePath: healthCertificatePath,
    managerLicenseStoragePath: "",
  }, now);
  assert.equal(incompleteHealthCertificate.candidates.length, 0);

  const idCardPath = "manager-documents/manager-1/idCard/legacy-id.jpg";
  const idCard = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      idCard: {
        fullPath: idCardPath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {idCard: idCardPath},
    managerIdCardStoragePath: idCardPath,
    managerLicenseStoragePath: "",
  }, now);
  assert.deepEqual(idCard.candidates.map(({documentKey}) => documentKey), ["idCard"]);

  const criminalRecordPath =
    "manager-documents/manager-1/criminalRecord/legacy-record.jpg";
  const criminalRecord = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      criminalRecord: {
        fullPath: criminalRecordPath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {criminalRecord: criminalRecordPath},
    managerCriminalRecordStoragePath: criminalRecordPath,
    managerLicenseStoragePath: "",
  }, now);
  assert.deepEqual(
      criminalRecord.candidates.map(({documentKey}) => documentKey),
      ["criminalRecord"],
  );

  const unsupportedPath = "manager-documents/manager-1/passport/unsupported.jpg";
  const unsupported = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      passport: {
        fullPath: unsupportedPath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {passport: unsupportedPath},
    managerLicenseStoragePath: "",
  }, now);
  assert.equal(unsupported.candidates.length, 0);
});

test("기존 retention claim 대상은 다음 실행의 첫 삭제 후보가 된다", () => {
  const now = new Date("2026-07-18T00:00:00.000Z");
  const licensePath = "manager-documents/manager-1/license/license.pdf";
  const idCardPath = "manager-documents/manager-1/idCard/id-card.pdf";
  const claimed = buildManagerDocumentDeletionClaim({
    candidate: {
      managerId: "manager-1",
      documentKey: "idCard",
      storagePath: idCardPath,
    },
    operation: "RETENTION",
    claimedAt: now,
  });
  const evaluation = evaluateManagerDocument("manager-1", {
    role: "MANAGER",
    managerDocumentStatus: "APPROVED",
    managerDocumentReviewedAt: Date.parse("2026-06-01T00:00:00.000Z"),
    managerDocumentUpdatedAt: Date.parse("2026-05-31T00:00:00.000Z"),
    managerDocumentFiles: {
      license: {fullPath: licensePath},
      idCard: {fullPath: idCardPath},
    },
    managerDocumentFilePaths: {
      license: licensePath,
      idCard: idCardPath,
    },
    managerLicenseStoragePath: licensePath,
    managerIdCardStoragePath: idCardPath,
    [MANAGER_DOCUMENT_DELETION_CLAIM_FIELD]: {
      ...claimed,
      state: "READY",
      objectGeneration: "17",
    },
  }, now);

  assert.deepEqual(
      evaluation.candidates.map((candidate) => candidate.documentKey),
      ["idCard", "license"],
  );
});

test("관리자 증빙 Storage 삭제도 사용자와 문서 키를 다시 확인한다", async () => {
  const deletedPaths = [];
  const gateway = new FirebaseStorageGateway({
    file(storagePath, options) {
      return {
        async getMetadata() {
          return [{generation: "19"}];
        },
        async delete(deleteOptions) {
          deletedPaths.push({storagePath, options, deleteOptions});
        },
      };
    },
  });

  assert.deepEqual(
      await gateway.inspectManagerDocument(
          "manager-documents/manager-1/license/license.jpg",
          "manager-1",
          "license",
      ),
      {objectGeneration: "19"},
  );

  await assert.rejects(
      gateway.deleteManagerDocument(
          "manager-documents/manager-2/idCard/id.jpg",
          "manager-1",
          "idCard",
          "20",
      ),
      (error) => error.code === "MANAGER_STORAGE_PATH_INVALID",
  );
  await assert.rejects(
      gateway.deleteManagerDocument(
          "manager-documents/manager-1/license/id.jpg",
          "manager-1",
          "idCard",
          "21",
      ),
      (error) => error.code === "MANAGER_STORAGE_PATH_INVALID",
  );
  await gateway.deleteManagerDocument(
      "manager-documents/manager-1/idCard/id.jpg",
      "manager-1",
      "idCard",
      "22",
  );
  await gateway.deleteManagerDocument(
      "manager-documents/manager-1/nursingLicense/license.jpg",
      "manager-1",
      "nursingLicense",
      "23",
  );
  await gateway.deleteManagerDocument(
      "manager-documents/manager-1/healthCertificate/legacy.jpg",
      "manager-1",
      "healthCertificate",
      "24",
  );
  await assert.rejects(
      gateway.deleteManagerDocument(
          "manager-documents/manager-1/passport/unsupported.jpg",
          "manager-1",
          "passport",
          "25",
      ),
      (error) => error.code === "MANAGER_STORAGE_PATH_INVALID",
  );

  assert.deepEqual(deletedPaths, [
    {
      storagePath: "manager-documents/manager-1/idCard/id.jpg",
      options: undefined,
      deleteOptions: {ignoreNotFound: true, ifGenerationMatch: "22"},
    },
    {
      storagePath: "manager-documents/manager-1/nursingLicense/license.jpg",
      options: undefined,
      deleteOptions: {ignoreNotFound: true, ifGenerationMatch: "23"},
    },
    {
      storagePath: "manager-documents/manager-1/healthCertificate/legacy.jpg",
      options: undefined,
      deleteOptions: {ignoreNotFound: true, ifGenerationMatch: "24"},
    },
  ]);
});

test("관리자 증빙 보존 파기는 exact claim과 참조·별칭을 한 transaction에서 정리한다", async () => {
  const asOf = new Date("2026-07-18T00:00:00.000Z");
  const storagePath = "manager-documents/manager-1/license/license.jpg";
  const fixture = createTransactionalDocument({
    role: "MANAGER",
    managerDocumentStatus: "APPROVED",
    managerDocumentReviewedAt: Date.parse("2026-06-01T00:00:00.000Z"),
    managerDocumentUpdatedAt: Date.parse("2026-05-31T00:00:00.000Z"),
    managerDocumentFiles: {
      license: {
        fullPath: storagePath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {license: storagePath},
    managerLicenseStoragePath: storagePath,
  });
  const firestore = {
    ...fixture.firestore,
    collection(name) {
      assert.equal(name, "users");
      return {
        doc(managerId) {
          assert.equal(managerId, "manager-1");
          return fixture.reference;
        },
      };
    },
  };
  const store = new FirebaseManagerDocumentStore(firestore);
  const deleteCalls = [];

  const result = await store.deleteCandidate({
    managerId: "manager-1",
    documentKey: "license",
    storagePath,
  }, asOf, {
    async inspectManagerDocument() {
      return {objectGeneration: "37"};
    },
    async deleteManagerDocument(path, managerId, documentKey, generation) {
      deleteCalls.push({path, managerId, documentKey, generation});
    },
  });

  assert.equal(result.status, "COMPLETED");
  assert.deepEqual(deleteCalls, [{
    path: storagePath,
    managerId: "manager-1",
    documentKey: "license",
    generation: "37",
  }]);
  const after = fixture.getData();
  assert.equal(after.managerDocumentFiles.license, undefined);
  assert.equal(after.managerDocumentFilePaths.license, undefined);
  assert.equal(after.managerLicenseStoragePath, undefined);
  assert.equal(Object.hasOwn(after, MANAGER_DOCUMENT_DELETION_CLAIM_FIELD), false);
  assert.equal(after.managerDocumentOriginalsDeletedAt instanceof Date, true);
});

test("관리자 증빙 삭제 실패는 참조를 유지하고 다음 실행에서 재시도한다", async () => {
  const candidate = {
    managerId: "manager-1",
    documentKey: "license",
    storagePath: "manager-documents/manager-1/license/license.pdf",
  };
  const order = [];
  const database = createDatabase();
  const legacyStore = createLegacyStore();
  let referenceExists = true;
  let deleteAttempt = 0;
  const managerStore = createManagerStore({
    async preview() {
      return {
        candidates: referenceExists ? [candidate] : [],
        legalHoldSkips: 0,
      };
    },
    async deleteCandidate(candidateToDelete, _asOf, storageToUse) {
      order.push("claim");
      await storageToUse.deleteManagerDocument(
          candidateToDelete.storagePath,
          candidateToDelete.managerId,
          candidateToDelete.documentKey,
          "31",
      );
      order.push("finalize");
      referenceExists = false;
      return {status: "COMPLETED"};
    },
  });
  const storage = {
    async deleteChatAttachment() {},
    async deleteManagerDocument() {
      order.push("delete");
      deleteAttempt += 1;
      if (deleteAttempt === 1) {
        throw new Error("storage unavailable");
      }
    },
  };

  const firstSummary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: true,
    now: new Date("2026-07-18T00:00:00.000Z"),
  });

  assert.deepEqual(order, ["claim", "delete"]);
  assert.equal(referenceExists, true);
  assert.equal(firstSummary.managerDocumentsDeleted, 0);
  assert.equal(firstSummary.managerDocumentDeleteFailures, 1);

  const secondSummary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: true,
    now: new Date("2026-07-19T00:00:00.000Z"),
  });

  assert.deepEqual(order, ["claim", "delete", "claim", "delete", "finalize"]);
  assert.equal(referenceExists, false);
  assert.equal(secondSummary.managerDocumentsDeleted, 1);
  assert.equal(secondSummary.managerDocumentDeleteFailures, 0);
});

test("Firestore 전환 첨부 삭제 실패는 참조를 보존하고 다음 실행에서 제거한다", async () => {
  const successPath = "companion-chat-attachments/session-legacy/success.pdf";
  const retryPath = "companion-chat-attachments/session-legacy/retry.pdf";
  let sessionData = {
    currentStatus: "COMPLETED",
    completedAt: Date.parse("2026-06-01T00:00:00.000Z"),
    chatMessages: [{
      body: "민감한 대화",
      attachments: [
        {fullPath: successPath},
        {fullPath: retryPath},
      ],
    }],
  };
  const snapshot = () => ({
    exists: true,
    id: "session-legacy",
    data: () => structuredClone(sessionData),
  });
  const reference = {id: "session-legacy"};
  const firestore = {
    collection(name) {
      assert.equal(name, "companionSessions");
      return {
        doc(sessionId) {
          assert.equal(sessionId, "session-legacy");
          return {
            ...reference,
            async get() {
              return snapshot();
            },
          };
        },
      };
    },
    async runTransaction(callback) {
      return callback({
        async get() {
          return snapshot();
        },
        update(_reference, updates) {
          if (updates.chatMessages) {
            sessionData = {...sessionData, chatMessages: updates.chatMessages};
          }
        },
      });
    },
  };
  const legacyStore = new FirebaseLegacyCompanionStore(firestore);
  const deleteAttempts = new Map();
  const storage = {
    async deleteChatAttachment(candidatePath) {
      const attempt = (deleteAttempts.get(candidatePath) || 0) + 1;
      deleteAttempts.set(candidatePath, attempt);
      if (candidatePath === retryPath && attempt === 1) {
        throw new Error("storage unavailable");
      }
    },
  };
  const candidate = {sessionId: "session-legacy"};

  const firstResult = await legacyStore.applySession(
      candidate,
      new Date("2026-07-18T00:00:00.000Z"),
      storage,
  );

  assert.equal(firstResult.messagesRedacted, 0);
  assert.equal(firstResult.attachmentsDeleted, 1);
  assert.equal(firstResult.attachmentDeleteFailures, 1);
  assert.equal(sessionData.chatMessages[0].body, "민감한 대화");
  assert.deepEqual(sessionData.chatMessages[0].attachments, [{fullPath: retryPath}]);

  const secondResult = await legacyStore.applySession(
      candidate,
      new Date("2026-07-19T00:00:00.000Z"),
      storage,
  );

  assert.equal(secondResult.messagesRedacted, 0);
  assert.equal(secondResult.attachmentsDeleted, 1);
  assert.equal(secondResult.attachmentDeleteFailures, 0);
  assert.deepEqual(sessionData.chatMessages[0].attachments, []);
  assert.equal(deleteAttempts.get(successPath), 1);
  assert.equal(deleteAttempts.get(retryPath), 2);
});

test("부분 삭제 실패는 저장소별 성공과 실패 집계를 COMPLETED 작업에 분리 기록한다", async () => {
  const postgresAttachments = [
    {
      id: "df462d78-8a9d-4a75-8b35-fb09489a7f60",
      storagePath: "companion-chat-attachments/session-summary/postgres-success.pdf",
    },
    {
      id: "66ed98d5-f8da-4fab-9723-5f7587f5c7f7",
      storagePath: "companion-chat-attachments/session-summary/postgres-failure.pdf",
    },
  ];
  const managerCandidates = [
    {
      managerId: "manager-1",
      documentKey: "license",
      storagePath: "manager-documents/manager-1/license/success.pdf",
    },
    {
      managerId: "manager-2",
      documentKey: "license",
      storagePath: "manager-documents/manager-2/license/failure.pdf",
    },
  ];
  const finishes = [];
  const database = createDatabase({
    async claimAttachments() {
      return postgresAttachments;
    },
    async finishJob(_jobId, status, _finishedAt, summary, failureStage) {
      finishes.push({status, failureStage: failureStage || null, summary: {...summary}});
      return true;
    },
  });
  const legacyStore = createLegacyStore({
    async preview() {
      return {
        sessions: [{sessionId: "session-summary"}],
        messageCandidates: 1,
        attachmentCandidates: 3,
        locationCandidates: 1,
        legalHoldSkips: 0,
      };
    },
    async applySession() {
      return {
        messagesRedacted: 1,
        attachmentsDeleted: 2,
        attachmentDeleteFailures: 1,
        locationsCleared: 1,
      };
    },
  });
  const managerStore = createManagerStore({
    async preview() {
      return {candidates: managerCandidates, legalHoldSkips: 0};
    },
  });
  const storage = {
    async deleteChatAttachment(storagePath) {
      if (storagePath.endsWith("postgres-failure.pdf")) {
        throw new Error("postgres attachment unavailable");
      }
    },
    async deleteManagerDocument(storagePath) {
      if (storagePath.endsWith("failure.pdf")) {
        throw new Error("manager document unavailable");
      }
    },
  };

  const summary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: true,
    now: new Date("2026-07-18T00:00:00.000Z"),
  });

  assert.equal(summary.attachmentsDeleted, 1);
  assert.equal(summary.attachmentDeleteFailures, 1);
  assert.equal(summary.firestoreMessagesRedacted, 1);
  assert.equal(summary.firestoreAttachmentsDeleted, 2);
  assert.equal(summary.firestoreAttachmentDeleteFailures, 1);
  assert.equal(summary.firestoreLocationsCleared, 1);
  assert.equal(summary.managerDocumentsDeleted, 1);
  assert.equal(summary.managerDocumentDeleteFailures, 1);
  assert.equal(finishes.length, 1);
  assert.equal(finishes[0].status, "COMPLETED");
  assert.equal(finishes[0].failureStage, null);
  assert.equal(finishes[0].summary.attachmentsDeleted, 1);
  assert.equal(finishes[0].summary.attachmentDeleteFailures, 1);
  assert.equal(finishes[0].summary.firestoreMessagesRedacted, 1);
  assert.equal(finishes[0].summary.firestoreAttachmentsDeleted, 2);
  assert.equal(finishes[0].summary.firestoreAttachmentDeleteFailures, 1);
  assert.equal(finishes[0].summary.firestoreLocationsCleared, 1);
  assert.equal(finishes[0].summary.managerDocumentsDeleted, 1);
  assert.equal(finishes[0].summary.managerDocumentDeleteFailures, 1);
});

test("처리되지 않은 오류는 실패 단계와 함께 FAILED 작업으로 기록한다", async () => {
  const finishes = [];
  const database = createDatabase({
    async claimAttachments() {
      return [{
        id: "50de4226-df48-4622-9de8-c292c3fc0ed9",
        storagePath: "companion-chat-attachments/session-fatal/evidence.pdf",
      }];
    },
    async finishJob(_jobId, status, _finishedAt, summary, failureStage) {
      finishes.push({
        status,
        failureStage: failureStage || null,
        attachmentsDeleted: summary.attachmentsDeleted,
        messagesRedacted: summary.messagesRedacted,
        locationsDeleted: summary.locationsDeleted,
      });
      return true;
    },
  });
  const legacyStore = createLegacyStore({
    async preview() {
      return {
        sessions: [{sessionId: "session-fatal"}],
        messageCandidates: 1,
        attachmentCandidates: 1,
        locationCandidates: 1,
        legalHoldSkips: 0,
      };
    },
    async applySession() {
      throw new Error("firestore unavailable");
    },
  });

  await assert.rejects(
      runRetentionJob({
        database,
        legacyStore,
        managerStore: createManagerStore(),
        storage: {
          async deleteChatAttachment() {},
        },
        apply: true,
        now: new Date("2026-07-18T00:00:00.000Z"),
      }),
      (error) => error.retentionFailureStage === "PURGE_FIRESTORE",
  );

  assert.deepEqual(finishes, [{
    status: "FAILED",
    failureStage: "PURGE_FIRESTORE",
    attachmentsDeleted: 1,
    messagesRedacted: 2,
    locationsDeleted: 3,
  }]);
});

test("채팅 첨부 삭제 경로는 legacy와 Core API 구조만 허용한다", () => {
  assert.equal(isAllowedChatAttachmentPath(
      "companion-chat-attachments/728916a2-d57e-4e8f-bd99-c6c47498b4ba/a.pdf",
  ), true);
  assert.equal(isAllowedChatAttachmentPath(
      "companion-chat-attachments/728916a2-d57e-4e8f-bd99-c6c47498b4ba/"
      + "e402cf5d-8811-4dd5-83f5-58529251cc65/0-evidence.pdf",
  ), true);
  assert.equal(isAllowedChatAttachmentPath(
      "companion-chat-attachments/session/not-a-uuid/evidence.pdf",
  ), false);
  assert.equal(isAllowedChatAttachmentPath(
      "companion-chat-attachments/session/"
      + "e402cf5d-8811-4dd5-83f5-58529251cc65/nested/evidence.pdf",
  ), false);
  assert.equal(isAllowedChatAttachmentPath("manager-documents/user/idCard/a.pdf"), false);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/idCard/a.pdf",
  ), true);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/idCard/a.pdf",
      "manager-1",
      "idCard",
  ), true);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-2/idCard/a.pdf",
      "manager-1",
      "idCard",
  ), false);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/license/a.pdf",
      "manager-1",
      "idCard",
  ), false);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/idCard/a.pdf",
      " manager-1 ",
      "idCard",
  ), false);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/idCard/a.pdf",
      "manager-1",
      " idCard ",
  ), false);
  assert.equal(isAllowedManagerDocumentPath(
      " manager-documents/manager-1/idCard/a.pdf ",
      "manager-1",
      "idCard",
  ), false);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/idCard/a.pdf",
      1,
      "idCard",
  ), false);
  assert.equal(isAllowedManagerDocumentPath("manager-documents/manager-1/other/a.pdf"), false);
});

test("Firestore 전환 데이터도 세션 종료 시각 기준 보관 기간을 적용한다", () => {
  const evaluation = evaluateLegacyCompanionSession("session-legacy", {
    currentStatus: "COMPLETED",
    updatedAt: Date.parse("2025-12-01T00:00:00.000Z"),
    sharedLatitude: 37.5,
    sharedLongitude: 127.0,
    sharedLocationHistory: [{latitude: 37.5, longitude: 127.0}],
    chatMessages: [{
      senderRole: "MANAGER",
      body: "민감한 대화",
      attachments: [{
        fullPath: "companion-chat-attachments/session-legacy/evidence.pdf",
      }],
    }],
  }, new Date("2026-07-18T00:00:00.000Z"));

  assert.equal(evaluation.locationEligible, true);
  assert.equal(evaluation.messageCandidates, 1);
  assert.equal(evaluation.attachments.length, 1);
  assert.equal(evaluation.hasWork, true);
});

test("Firestore 전환 데이터는 보관 기간이 끝나기 전에는 파기하지 않는다", () => {
  const evaluation = evaluateLegacyCompanionSession("session-recent", {
    currentStatus: "COMPLETED",
    updatedAt: Date.parse("2026-07-17T12:00:00.000Z"),
    sharedLatitude: 37.5,
    chatMessages: [{
      body: "보관 중인 대화",
      attachment: {
        fullPath: "companion-chat-attachments/session-recent/evidence.pdf",
      },
    }],
  }, new Date("2026-07-18T00:00:00.000Z"));

  assert.equal(evaluation.hasWork, false);
  assert.equal(evaluation.messageCandidates, 0);
  assert.equal(evaluation.attachments.length, 0);
  assert.equal(evaluation.locationEligible, false);
});

test("Firestore 전환 데이터의 legal hold는 모든 파기 후보를 제외한다", () => {
  const evaluation = evaluateLegacyCompanionSession("session-legacy", {
    currentStatus: "CANCELED",
    updatedAt: Date.parse("2025-12-01T00:00:00.000Z"),
    legalHoldUntil: Date.parse("2026-08-01T00:00:00.000Z"),
    sharedLatitude: 37.5,
    chatMessages: [{
      body: "민감한 대화",
      attachment: {
        fullPath: "companion-chat-attachments/session-legacy/evidence.pdf",
      },
    }],
  }, new Date("2026-07-18T00:00:00.000Z"));

  assert.equal(evaluation.hasWork, false);
  assert.equal(evaluation.legalHoldSkips, 3);
});

test("해석할 수 없는 legal hold 값은 삭제하지 않는 쪽으로 처리한다", () => {
  const evaluation = evaluateLegacyCompanionSession("session-invalid-hold", {
    currentStatus: "COMPLETED",
    updatedAt: Date.parse("2025-12-01T00:00:00.000Z"),
    legalHoldUntil: "invalid",
    sharedLatitude: 37.5,
  }, new Date("2026-07-18T00:00:00.000Z"));

  assert.equal(evaluation.hasWork, false);
  assert.equal(evaluation.legalHoldSkips, 1);
});

test("월간 집계 대상은 서울 시간 기준 직전 달이다", () => {
  assert.equal(
      previousMonthStart(new Date("2026-07-31T20:15:00.000Z")).toISOString(),
      "2026-07-01T00:00:00.000Z",
  );
});
