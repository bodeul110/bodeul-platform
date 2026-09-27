import assert from "node:assert/strict";
import test from "node:test";

import {
  STATUS,
  auditAppCheck,
  auditProductionInfrastructure,
  buildReport,
  classifyAppCheckStage,
  combineAppCheckStage,
  classifyHttpStatus,
  escapeMarkdownTableCell,
  exitCodeForReport,
  hasExactProjectLocalRoles,
  isExpectedAuditProvider,
  isExpectedCloudRunImage,
  isExpectedOperationalProvider,
  isExpectedRecaptchaEnterpriseKey,
  makeCheck,
  readAppCheckDebugTokens,
  readAppCheckVerificationSeries,
  renderMarkdown,
  sanitizeText,
  summarizeValidAppCheckRequests,
  summarizeChecks,
  validAppCheckTtl,
  validPlayIntegrityPolicy,
  validRecaptchaRiskAnalysis,
} from "../check-production-infrastructure.mjs";

const check = (status, message = "검사 결과") => makeCheck({
  id: `test.${status.toLowerCase()}`,
  area: "테스트",
  status,
  message,
});

const validEnvironment = Object.freeze({
  GCP_PROJECT_ID: "bodeul-prod-110",
  GCP_PROJECT_NUMBER: "649312328770",
  GCP_REGION: "asia-northeast1",
  AUDIT_WORKLOAD_IDENTITY_PROVIDER: "projects/649312328770/locations/global/workloadIdentityPools/github-actions/providers/bodeul-infra-audit-production",
  AUDIT_SERVICE_ACCOUNT: "bodeul-infra-auditor@bodeul-prod-110.iam.gserviceaccount.com",
  CLOUD_RUN_ARTIFACT_REPOSITORY: "bodeul-core-api",
  CLOUD_RUN_SERVICE: "bodeul-core-api",
  CLOUD_RUN_DEPLOY_SERVICE_ACCOUNT: "bodeul-core-deployer@bodeul-prod-110.iam.gserviceaccount.com",
  CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT: "bodeul-core-runtime@bodeul-prod-110.iam.gserviceaccount.com",
  DB_BACKUP_SERVICE_ACCOUNT: "bodeul-db-backup@bodeul-prod-110.iam.gserviceaccount.com",
  FIREBASE_RETENTION_OPERATOR_SERVICE_ACCOUNT: "bodeul-retention-operator@bodeul-prod-110.iam.gserviceaccount.com",
  FIREBASE_PROJECT_ID: "bodeul-prod-110",
  DB_BACKUP_BUCKET: "bodeul-prod-110-db-backups",
  FIREBASE_STORAGE_BUCKET: "bodeul-prod-110.firebasestorage.app",
  CLOUD_RUN_EXPECTED_STATE: "absent",
  KAKAO_SECRET_EXPECTED_STATE: "metadata-only",
  FIRESTORE_PITR_EXPECTED_STATE: "deferred",
  FIREBASE_STORAGE_UBLA_EXPECTED_STATE: "deferred",
  APP_CHECK_EXPECTED_STATE: "unverified",
  ANDROID_RELEASE_SHA256: "",
});

test("HTTP 상태를 감사 상태로 분류한다", () => {
  assert.equal(classifyHttpStatus(401), STATUS.UNAVAILABLE);
  assert.equal(classifyHttpStatus(403), STATUS.UNAVAILABLE);
  assert.equal(classifyHttpStatus(429), STATUS.UNAVAILABLE);
  assert.equal(classifyHttpStatus(404), STATUS.DRIFT);
  assert.equal(classifyHttpStatus(404, STATUS.EXPECTED_ABSENT), STATUS.EXPECTED_ABSENT);
  assert.equal(classifyHttpStatus(500), STATUS.ERROR);
  assert.equal(classifyHttpStatus(0), STATUS.ERROR);
});

test("baseline과 release expected 상태를 분리한다", () => {
  assert.equal(summarizeChecks([check(STATUS.PASS)], "baseline"), STATUS.PASS);
  assert.equal(
    summarizeChecks([check(STATUS.PASS), check(STATUS.EXPECTED_BLOCKER)], "release"),
    STATUS.EXPECTED_BLOCKER,
  );
  assert.equal(
    summarizeChecks([check(STATUS.PASS), check(STATUS.EXPECTED_ABSENT)], "release"),
    STATUS.EXPECTED_ABSENT,
  );
  assert.equal(
    summarizeChecks([check(STATUS.EXPECTED_ABSENT), check(STATUS.DRIFT)], "release"),
    STATUS.DRIFT,
  );
});

test("민감 식별자와 자격 증명을 안전한 문구로 치환한다", () => {
  const raw = [
    "Bearer secret-token-for-test",
    "operator@example.com",
    "gs://private-bucket/path/report.json",
    "projects/p/secrets/database-password/versions/17",
  ].join(" ");
  const sanitized = sanitizeText(raw);
  assert.doesNotMatch(sanitized, /secret-token-for-test/);
  assert.doesNotMatch(sanitized, /operator@example\.com/);
  assert.doesNotMatch(sanitized, /gs:\/\//);
  assert.doesNotMatch(sanitized, /versions\/17/);
  assert.match(sanitized, /토큰 숨김/);
  assert.match(sanitized, /계정 식별자 숨김/);
  assert.match(sanitized, /버킷 경로 숨김/);
  assert.match(sanitized, /secret version 숨김/);
});

test("Markdown과 JSON 결과에 원래 민감값을 남기지 않는다", () => {
  const report = buildReport([
    check(STATUS.PASS, "svc@example.com gs://private-bucket/a projects/p/secrets/s/versions/2"),
  ], [
    check(STATUS.EXPECTED_BLOCKER, "Bearer test-token-value"),
  ], "2026-08-26T00:00:00.000Z");
  const serialized = `${renderMarkdown(report)}\n${JSON.stringify(report)}`;
  assert.doesNotMatch(serialized, /svc@example\.com/);
  assert.doesNotMatch(serialized, /private-bucket/);
  assert.doesNotMatch(serialized, /versions\/2/);
  assert.doesNotMatch(serialized, /test-token-value/);
});

test("Markdown 표 셀의 역슬래시와 구분자를 모두 이스케이프한다", () => {
  assert.equal(
    escapeMarkdownTableCell(String.raw`왼쪽\|가운데|오른쪽`),
    String.raw`왼쪽\\\|가운데\|오른쪽`,
  );
});

test("expected 상태만 있으면 성공하고 drift, unavailable, error는 실패한다", () => {
  const expectedOnly = buildReport(
    [check(STATUS.PASS)],
    [check(STATUS.EXPECTED_BLOCKER), check(STATUS.EXPECTED_ABSENT)],
  );
  assert.equal(exitCodeForReport(expectedOnly), 0);

  for (const status of [STATUS.DRIFT, STATUS.UNAVAILABLE, STATUS.ERROR]) {
    const report = buildReport([check(status)], []);
    assert.equal(exitCodeForReport(report), 1);
  }
});

test("WIF provider 조건은 허용 조건 전체가 정확히 일치해야 한다", () => {
  const condition = "assertion.repository == 'bodeul110/bodeul-platform' && assertion.repository_id == '1209358990' && assertion.repository_owner_id == '275679915' && assertion.ref == 'refs/heads/master' && assertion.environment == 'production-infrastructure-audit' && assertion.workflow_ref == 'bodeul110/bodeul-platform/.github/workflows/production-infrastructure-audit.yml@refs/heads/master' && assertion.event_name == 'workflow_dispatch'";
  const provider = {
    state: "ACTIVE",
    oidc: {issuerUri: "https://token.actions.githubusercontent.com"},
    attributeMapping: {"google.subject": "assertion.sub"},
    attributeCondition: condition,
  };
  assert.equal(isExpectedAuditProvider(provider), true);
  assert.equal(isExpectedAuditProvider({...provider, attributeCondition: `${condition} || true`}), false);
  assert.equal(isExpectedAuditProvider({...provider, attributeMapping: {...provider.attributeMapping, "attribute.ref": "assertion.ref"}}), false);
  assert.equal(isExpectedAuditProvider({...provider, oidc: {...provider.oidc, allowedAudiences: ["alternate"]}}), false);
  assert.equal(isExpectedAuditProvider({...provider, disabled: true}), false);
  assert.equal(isExpectedAuditProvider({...provider, state: "DELETED"}), false);
  assert.equal(isExpectedAuditProvider({...provider, oidc: {...provider.oidc, issuerUri: "https://example.com"}}), false);
});

test("운영 WIF provider는 정확한 workflow와 불변 저장소 식별자를 요구한다", () => {
  const provider = {
    state: "ACTIVE",
    oidc: {issuerUri: "https://token.actions.githubusercontent.com"},
    attributeMapping: {
      "google.subject": "assertion.sub",
      "attribute.repository": "assertion.repository",
      "attribute.repository_owner": "assertion.repository_owner",
      "attribute.ref": "assertion.ref",
      "attribute.environment": "assertion.environment",
      "attribute.actor": "assertion.actor",
      "attribute.workflow": "assertion.workflow",
    },
    attributeCondition: "assertion.repository == 'bodeul110/bodeul-platform' && assertion.repository_id == '1209358990' && assertion.repository_owner_id == '275679915' && assertion.ref == 'refs/heads/master' && assertion.environment == 'core-api-production' && assertion.workflow_ref == 'bodeul110/bodeul-platform/.github/workflows/core-api-production-deploy.yml@refs/heads/master' && assertion.event_name == 'workflow_dispatch'",
  };
  assert.equal(isExpectedOperationalProvider(provider, "deploy"), true);
  assert.equal(isExpectedOperationalProvider({
    ...provider,
    attributeCondition: "assertion.repository == 'bodeul110/bodeul-platform' && assertion.ref == 'refs/heads/master' && assertion.environment == 'core-api-production'",
  }, "deploy"), false);
  assert.equal(isExpectedOperationalProvider(provider, "unknown"), false);
});

test("project-local 역할은 조건 없는 정확한 집합만 허용한다", () => {
  const member = "serviceAccount:auditor@example.iam.gserviceaccount.com";
  const expectedRole = "projects/example/roles/auditor";
  const policy = {bindings: [{role: expectedRole, members: [member]}]};
  assert.equal(hasExactProjectLocalRoles(policy, member, [expectedRole]), true);
  assert.equal(hasExactProjectLocalRoles({bindings: [{...policy.bindings[0], condition: {title: "limited"}}]}, member, [expectedRole]), false);
  assert.equal(hasExactProjectLocalRoles({bindings: [...policy.bindings, {role: "roles/viewer", members: [member]}]}, member, [expectedRole]), false);
  assert.equal(hasExactProjectLocalRoles({bindings: []}, member, []), true);
});

test("운영 서비스 계정은 이름만 같은 subject가 아니라 불변 ID subject만 허용한다", async () => {
  const accounts = {
    "bodeul-infra-auditor": ["production-infrastructure-audit", "iam.audit-impersonation"],
    "bodeul-core-deployer": ["core-api-production", "iam.deploy-service-account-policy"],
    "bodeul-db-backup": ["core-api-migration-production", "iam.backup-service-account-policy"],
    "bodeul-retention-operator": ["firebase-retention-production", "iam.retention-service-account-policy"],
  };
  for (const [prefix, expected] of [
    ["repo:bodeul110@275679915/bodeul-platform@1209358990", STATUS.PASS],
    ["repo:bodeul110/bodeul-platform", STATUS.DRIFT],
    ["repo:bodeul110@275679915/bodeul-platform@9999999999", STATUS.DRIFT],
  ]) {
    const report = await auditProductionInfrastructure({
      env: validEnvironment,
      tokenResolver: async () => "test-only",
      fetchImpl: async (url) => {
        const parsed = new URL(url);
        const pathname = decodeURIComponent(parsed.pathname);
        const account = Object.keys(accounts).find((name) =>
          pathname.endsWith(`/serviceAccounts/${name}@bodeul-prod-110.iam.gserviceaccount.com:getIamPolicy`));
        if (parsed.hostname !== "iam.googleapis.com" || !account) {
          return new Response("", {status: 403});
        }
        return Response.json({bindings: [{
          role: "roles/iam.workloadIdentityUser",
          members: [`principal://iam.googleapis.com/projects/649312328770/locations/global/workloadIdentityPools/github-actions/subject/${prefix}:environment:${accounts[account][0]}`],
        }]});
      },
    });
    for (const [, id] of Object.values(accounts)) {
      assert.equal(report.baseline.checks.find((entry) => entry.id === id)?.status, expected, id);
    }
  }
});

test("Cloud Run 이미지는 production 저장소의 불변 식별자만 허용한다", () => {
  const prefix = "asia-northeast1-docker.pkg.dev/bodeul-prod-110/bodeul-core-api/bodeul-core-api";
  assert.equal(isExpectedCloudRunImage(`${prefix}:${"a".repeat(40)}`), true);
  assert.equal(isExpectedCloudRunImage(`${prefix}@sha256:${"b".repeat(64)}`), true);
  assert.equal(isExpectedCloudRunImage(`${prefix}:production`), false);
  assert.equal(isExpectedCloudRunImage(`docker.io/example/bodeul-core-api:${"a".repeat(40)}`), false);
});

test("App Check provider와 서비스 상태를 운영 단계로 분류한다", () => {
  const off = {
    "identitytoolkit.googleapis.com": "OFF",
    "firestore.googleapis.com": "OFF",
    "firebasestorage.googleapis.com": "OFF",
  };
  assert.equal(classifyAppCheckStage({
    androidProviderState: "absent",
    webProviderState: "absent",
    serviceModes: off,
  }), "unverified");
  assert.equal(classifyAppCheckStage({
    androidProviderState: "absent",
    webProviderState: "ready",
    serviceModes: off,
  }), "preparing");

  const observe = Object.fromEntries(Object.keys(off).map((service) => [service, "UNENFORCED"]));
  assert.equal(classifyAppCheckStage({
    androidProviderState: "ready",
    webProviderState: "ready",
    serviceModes: observe,
  }), "observe");

  const enforced = Object.fromEntries(Object.keys(off).map((service) => [service, "ENFORCED"]));
  assert.equal(classifyAppCheckStage({
    androidProviderState: "ready",
    webProviderState: "ready",
    serviceModes: enforced,
  }), "enforced");
  assert.equal(classifyAppCheckStage({
    androidProviderState: "ready",
    webProviderState: "ready",
    serviceModes: {...observe, "firebasestorage.googleapis.com": "ENFORCED"},
  }), "staged");
  assert.equal(classifyAppCheckStage({
    androidProviderState: "partial",
    webProviderState: "ready",
    serviceModes: observe,
  }), "invalid");
  assert.equal(classifyAppCheckStage({
    androidProviderState: "invalid",
    webProviderState: "absent",
    serviceModes: off,
  }), "invalid");
});

test("Firebase 서비스와 callable Functions 단계를 하나의 rollout 상태로 합친다", () => {
  assert.equal(combineAppCheckStage("unverified", "absent"), "unverified");
  assert.equal(combineAppCheckStage("preparing", "observe"), "preparing");
  assert.equal(combineAppCheckStage("observe", "observe"), "observe");
  assert.equal(combineAppCheckStage("observe", "enforced"), "staged");
  assert.equal(combineAppCheckStage("staged", "observe"), "staged");
  assert.equal(combineAppCheckStage("enforced", "observe"), "staged");
  assert.equal(combineAppCheckStage("enforced", "enforced"), "enforced");
  assert.equal(combineAppCheckStage("observe", "absent"), "invalid");
});

test("App Check TTL은 기본값과 공식 duration 범위만 허용한다", () => {
  assert.equal(validAppCheckTtl(undefined), true);
  assert.equal(validAppCheckTtl("3600s"), true);
  assert.equal(validAppCheckTtl("3600.123456789s"), true);
  assert.equal(validAppCheckTtl("1799.9s"), false);
  assert.equal(validAppCheckTtl("604800.1s"), false);
  assert.equal(validAppCheckTtl("3600.1234567890s"), false);
});

test("reCAPTCHA Enterprise 키는 SCORE와 단일 운영 도메인 제한을 요구한다", () => {
  const siteKey = "public-site-key";
  const hostname = "bodeul-admin-web-iota.vercel.app";
  const key = {
    name: `projects/bodeul-prod-110/keys/${siteKey}`,
    displayName: "BoDeul Admin Web Production App Check",
    webSettings: {
      integrationType: "SCORE",
      allowAllDomains: false,
      allowAmpTraffic: false,
      allowedDomains: [hostname],
    },
  };
  const displayName = "BoDeul Admin Web Production App Check";
  assert.equal(isExpectedRecaptchaEnterpriseKey(key, siteKey, hostname, displayName), true);
  assert.equal(isExpectedRecaptchaEnterpriseKey({
    ...key,
    webSettings: {...key.webSettings, allowAllDomains: true},
  }, siteKey, hostname, displayName), false);
  assert.equal(isExpectedRecaptchaEnterpriseKey({
    ...key,
    testingOptions: {testingScore: 0.9},
  }, siteKey, hostname, displayName), false);
  assert.equal(isExpectedRecaptchaEnterpriseKey({
    ...key,
    webSettings: {...key.webSettings, allowedDomains: [hostname, "vercel.app"]},
  }, siteKey, hostname, displayName), false);
});

test("reCAPTCHA Enterprise 위험 점수는 기본값 또는 0.5만 허용한다", () => {
  assert.equal(validRecaptchaRiskAnalysis({}), true);
  assert.equal(validRecaptchaRiskAnalysis({riskAnalysis: {minValidScore: 0.5}}), true);
  assert.equal(validRecaptchaRiskAnalysis({riskAnalysis: {minValidScore: 0}}), false);
  assert.equal(validRecaptchaRiskAnalysis({riskAnalysis: {minValidScore: 0.7}}), false);
});

test("Play Integrity는 배포 채널 확정 전 Firebase 기본 정책만 허용한다", () => {
  assert.equal(validPlayIntegrityPolicy({}), true);
  assert.equal(validPlayIntegrityPolicy({
    appIntegrity: {allowUnrecognizedVersion: false},
    deviceIntegrity: {minDeviceRecognitionLevel: "NO_INTEGRITY"},
    accountDetails: {requireLicensed: false},
  }), true);
  assert.equal(validPlayIntegrityPolicy({appIntegrity: {allowUnrecognizedVersion: true}}), false);
  assert.equal(validPlayIntegrityPolicy({deviceIntegrity: {minDeviceRecognitionLevel: "DEVICE_INTEGRITY"}}), false);
  assert.equal(validPlayIntegrityPolicy({accountDetails: {requireLicensed: true}}), false);
});

test("production debug token은 모든 페이지를 읽고 순환 token을 차단한다", async () => {
  const urls = [];
  const client = {
    get: async (url) => {
      urls.push(new URL(url));
      return urls.length === 1 ? {
        debugTokens: [],
        nextPageToken: "next-page",
      } : {debugTokens: [{name: "redacted"}]};
    },
  };
  const tokens = await readAppCheckDebugTokens(client, "test-app");
  assert.equal(tokens.length, 1);
  assert.equal(urls[0].searchParams.get("pageSize"), "100");
  assert.equal(urls[1].searchParams.get("pageToken"), "next-page");

  await assert.rejects(() => readAppCheckDebugTokens({
    get: async () => ({nextPageToken: "loop"}),
  }, "test-app"), /페이지 경계/);
});

test("App Check 유효 요청은 정확한 앱 ID의 ALLOW와 VALID만 합산한다", () => {
  const counts = summarizeValidAppCheckRequests([
    metricSeries("android", "ALLOW", "VALID", [2, 3]),
    metricSeries("android", "ALLOW", "CONSUMED", [7]),
    metricSeries("web", "DENY", "VALID", [11]),
    metricSeries("web", "ALLOW", "VALID", [5]),
    metricSeries("unknown", "ALLOW", "VALID", [13]),
  ], ["android", "web"]);
  assert.deepEqual(counts, {android: 5, web: 5});
});

test("App Check 메트릭 조회는 최근 7일과 모든 페이지를 사용한다", async () => {
  const urls = [];
  const client = {
    get: async (url) => {
      urls.push(new URL(url));
      return urls.length === 1 ? {
        timeSeries: [metricSeries("android", "ALLOW", "VALID", [1])],
        nextPageToken: "next-page",
      } : {timeSeries: [metricSeries("web", "ALLOW", "VALID", [1])]};
    },
  };
  const now = new Date("2026-08-26T00:00:00.000Z");
  const series = await readAppCheckVerificationSeries(client, now);
  assert.equal(series.length, 2);
  assert.equal(urls.length, 2);
  assert.equal(urls[0].searchParams.get("interval.startTime"), "2026-08-19T00:00:00.000Z");
  assert.equal(urls[0].searchParams.get("interval.endTime"), "2026-08-26T00:00:00.000Z");
  assert.equal(urls[1].searchParams.get("pageToken"), "next-page");
});

test("provider config 404와 Functions 부재는 현재 production unverified 계약으로 판정한다", async () => {
  const baseline = [];
  const release = [];
  await auditAppCheck(createAppCheckClient(), baseline, release, {
    APP_CHECK_EXPECTED_STATE: "unverified",
    ANDROID_RELEASE_SHA256: "",
  });
  assert.equal(baseline.every((item) => item.status === STATUS.PASS), true);
  assert.equal(baseline.find((item) => item.id === "firebase.app-check-stage")?.message.includes("unverified"), true);
  assert.equal(release.find((item) => item.id === "release.app-check-enforcement")?.status, STATUS.EXPECTED_BLOCKER);
  assert.equal(release.find((item) => item.id === "release.app-check-valid-requests")?.status, STATUS.EXPECTED_BLOCKER);
});

test("provider config 리소스만 남아 있어도 unverified가 아니라 preparing으로 판정한다", async () => {
  const baseline = [];
  const release = [];
  await auditAppCheck(createAppCheckClient({androidConfigResidual: true}), baseline, release, {
    APP_CHECK_EXPECTED_STATE: "preparing",
    ANDROID_RELEASE_SHA256: "",
  });
  assert.equal(baseline.every((item) => item.status === STATUS.PASS), true);
  assert.equal(baseline.find((item) => item.id === "firebase.app-check-stage")?.message.includes("preparing"), true);
});

test("production debug token이 있으면 App Check baseline을 fail-closed 처리한다", async () => {
  const baseline = [];
  const release = [];
  await auditAppCheck(createAppCheckClient({debugTokenPresent: true}), baseline, release, {
    APP_CHECK_EXPECTED_STATE: "unverified",
    ANDROID_RELEASE_SHA256: "",
  });
  assert.equal(baseline.find((item) => item.id === "firebase.app-check")?.status, STATUS.DRIFT);
  assert.equal(baseline.find((item) => item.id === "firebase.app-check-stage")?.status, STATUS.DRIFT);
});

test("Firebase 관찰 중 Functions를 먼저 강제하면 통합 staged 단계로 판정한다", async () => {
  const baseline = [];
  const release = [];
  await auditAppCheck(createAppCheckClient({providersReady: true, functionMode: "enforced"}), baseline, release, {
    APP_CHECK_EXPECTED_STATE: "staged",
    ANDROID_RELEASE_SHA256: "a".repeat(64),
  });
  assert.equal(baseline.every((item) => item.status === STATUS.PASS), true);
  assert.equal(baseline.find((item) => item.id === "firebase.app-check-stage")?.message.includes("staged"), true);
  assert.equal(release.find((item) => item.id === "release.app-check-enforcement")?.status, STATUS.EXPECTED_BLOCKER);
});

test("허용하지 않은 단계 상태는 실행 경계 drift로 기록한다", async () => {
  const report = await auditProductionInfrastructure({
    env: {...validEnvironment, CLOUD_RUN_EXPECTED_STATE: "deleted"},
    tokenResolver: async () => {
      throw new Error("테스트 인증 중단");
    },
  });
  const configuration = report.baseline.checks.find((item) => item.id === "configuration.fixed-target");
  assert.equal(configuration?.status, STATUS.DRIFT);
  assert.match(configuration?.message ?? "", /1개/);
});

test("운영 표시 이름 변경 후에도 Firebase 프로젝트 ID와 number를 함께 검증한다", async () => {
  const expected = {
    projectId: "bodeul-prod-110",
    projectNumber: "649312328770",
    displayName: "bodeul-prod",
  };
  for (const [overrides, status] of [
    [{}, STATUS.PASS],
    [{displayName: "BoDeul Production"}, STATUS.DRIFT],
    [{projectId: "bodeul-dev"}, STATUS.DRIFT],
    [{projectNumber: "533563500316"}, STATUS.DRIFT],
  ]) {
    const report = await auditProductionInfrastructure({
      env: validEnvironment,
      tokenResolver: async () => "short-lived-test-token-without-real-access",
      fetchImpl: async (url) => {
        if (String(url) === "https://firebase.googleapis.com/v1beta1/projects/bodeul-prod-110") {
          return Response.json({...expected, ...overrides});
        }
        return new Response("", {status: 403});
      },
    });
    assert.equal(report.baseline.checks.find((item) => item.id === "firebase.project")?.status, status);
  }
});

test("원격 조회가 모두 거부되면 raw 응답 없이 실패 상태를 반환한다", async () => {
  const report = await auditProductionInfrastructure({
    env: validEnvironment,
    tokenResolver: async () => "short-lived-test-token-without-real-access",
    fetchImpl: async () => new Response("operator@example.com raw denial", {status: 403}),
  });
  const serialized = `${renderMarkdown(report)}\n${JSON.stringify(report)}`;
  assert.equal(report.baseline.status, STATUS.UNAVAILABLE);
  assert.equal(exitCodeForReport(report), 1);
  assert.doesNotMatch(serialized, /operator@example\.com|raw denial/);
});

function metricSeries(appId, result, security, values) {
  return {
    metric: {labels: {app_id: appId, result, security}},
    points: values.map((value) => ({value: {int64Value: String(value)}})),
  };
}

function createAppCheckClient({
  providersReady = false,
  functionMode = "absent",
  debugTokenPresent = false,
  androidConfigResidual = false,
} = {}) {
  const androidAppId = "1:649312328770:android:b0698534ff92da7fdea1db";
  const webAppId = "1:649312328770:web:3ade1cb9e994abb3dea1db";
  const callableIds = [
    "kakaoCustomToken",
    "naverCustomToken",
    "resolveLinkedParticipant",
    "findSocialDuplicateEmailProvider",
    "resolveAssignedManagerProfile",
    "dispatchAdminActionDeliveryJobs",
    "dispatchAppointmentReminderJobs",
  ];
  const get = async (url) => {
    const parsed = new URL(url);
    const path = parsed.pathname;
    if (parsed.hostname === "monitoring.googleapis.com") return {};
    if (path.endsWith(`/androidApps/${androidAppId}/sha`)) {
      return providersReady ? {certificates: [{certType: "SHA_256", shaHash: "a".repeat(64)}]} : {};
    }
    if (path.endsWith(`/androidApps/${androidAppId}`)) {
      return {
        name: `projects/bodeul-prod-110/androidApps/${androidAppId}`,
        appId: androidAppId,
        displayName: "BoDeul Android Production",
        packageName: "com.example.bodeul",
        state: "ACTIVE",
      };
    }
    if (path.endsWith(`/webApps/${webAppId}`)) {
      return {
        name: `projects/bodeul-prod-110/webApps/${webAppId}`,
        appId: webAppId,
        displayName: "BoDeul Admin Web Production",
        state: "ACTIVE",
      };
    }
    if (path.endsWith(`/apps/${androidAppId}/debugTokens`)) {
      return debugTokenPresent ? {debugTokens: [{name: "redacted"}]} : {};
    }
    if (path.endsWith(`/apps/${webAppId}/debugTokens`)) return {};
    if (parsed.hostname === "identitytoolkit.googleapis.com") {
      return {
        authorizedDomains: providersReady ? [
          "bodeul-prod-110.firebaseapp.com",
          "bodeul-prod-110.web.app",
          "bodeul-admin-web-iota.vercel.app",
        ] : ["bodeul-prod-110.firebaseapp.com", "bodeul-prod-110.web.app"],
      };
    }
    if (parsed.hostname === "serviceusage.googleapis.com") return {state: providersReady ? "ENABLED" : "DISABLED"};
    if (parsed.hostname === "recaptchaenterprise.googleapis.com") {
      return {
        name: "projects/bodeul-prod-110/keys/public-site-key",
        displayName: "BoDeul Admin Web Production App Check",
        webSettings: {
          integrationType: "SCORE",
          allowedDomains: ["bodeul-admin-web-iota.vercel.app"],
        },
      };
    }
    if (path.includes("/services/")) return {enforcementMode: providersReady ? "UNENFORCED" : undefined};
    if (parsed.hostname === "cloudfunctions.googleapis.com") {
      if (functionMode === "absent") return {};
      return {functions: callableIds.map((id) => ({
        name: `projects/bodeul-prod-110/locations/asia-northeast3/functions/${id}`,
        state: "ACTIVE",
        serviceConfig: {environmentVariables: {
          ENABLE_APPCHECK_ENFORCEMENT: functionMode === "enforced" ? "true" : "false",
        }},
      }))};
    }
    throw new Error(`예상하지 않은 App Check 테스트 URL: ${url}`);
  };
  return {
    get,
    getOptional: async (url) => {
      if (url.endsWith("/playIntegrityConfig")) {
        if (!providersReady && !androidConfigResidual) return null;
        return {
          name: `projects/649312328770/apps/${androidAppId}/playIntegrityConfig`,
          tokenTtl: "3600s",
          appIntegrity: {allowUnrecognizedVersion: false},
          deviceIntegrity: {minDeviceRecognitionLevel: "NO_INTEGRITY"},
          accountDetails: {requireLicensed: false},
        };
      }
      if (url.endsWith("/recaptchaEnterpriseConfig")) {
        if (!providersReady) return null;
        return {
          name: `projects/649312328770/apps/${webAppId}/recaptchaEnterpriseConfig`,
          siteKey: "public-site-key",
          tokenTtl: "3600s",
        };
      }
      throw new Error(`예상하지 않은 optional App Check 테스트 URL: ${url}`);
    },
  };
}
