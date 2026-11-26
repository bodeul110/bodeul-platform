"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {execFileSync} = require("node:child_process");
const {mkdtempSync, mkdirSync, writeFileSync, rmSync} = require("node:fs");
const {tmpdir} = require("node:os");
const {join} = require("node:path");
const {checkDispatch, checkEnvironment, checkFunction, checkUpload, patchBody, sourceZip} = require("../retention-preview-deploy");

const sha = "a".repeat(40);
const dispatch = {GITHUB_REPOSITORY: "bodeul110/bodeul-platform", GITHUB_REPOSITORY_ID: "1209358990",
  GITHUB_REPOSITORY_OWNER_ID: "275679915", GITHUB_EVENT_NAME: "workflow_dispatch", GITHUB_REF: "refs/heads/dev",
  GITHUB_REF_PROTECTED: "true", GITHUB_SHA: sha, CONFIRM_DEV_SHA: sha,
  GITHUB_WORKFLOW_REF: "bodeul110/bodeul-platform/.github/workflows/firebase-rules.yml@refs/heads/dev"};
const env = {name: "firebase-retention-preview", can_admins_bypass: false,
  deployment_branch_policy: {custom_branch_policies: true}, protection_rules: [{type: "required_reviewers",
    prevent_self_review: true, reviewers: [{type: "User", reviewer: {id: 275679915}}]}]};
const policies = {total_count: 1, branch_policies: [{name: "dev", type: "branch"}]};
const loc = "projects/bodeul-dev/locations/asia-northeast3";
const addr = (role) => "bodeul-retention-dev-" + role + "@bodeul-dev.iam.gserviceaccount.com";
const fn = {name: loc + "/functions/cleanupExpiredData", state: "ACTIVE", environment: "GEN_2",
  labels: {"deployment-scheduled": "true"},
  buildConfig: {runtime: "nodejs22", entryPoint: "cleanupExpiredData", dockerRepository: loc + "/repositories/bodeul-retention-dev",
    serviceAccount: "projects/bodeul-dev/serviceAccounts/" + addr("build")},
  serviceConfig: {service: loc + "/services/cleanupexpireddata", serviceAccountEmail: addr("run"),
    environmentVariables: {GCLOUD_PROJECT: "bodeul-dev", RETENTION_APPLY_ENABLED: "false", LOG_EXECUTION_ID: "true"},
    secretEnvironmentVariables: ["RETENTION_DATABASE_URL", "RETENTION_DATABASE_CA_CERT"].map((key) => ({
      key, secret: key, projectId: "bodeul-dev", version: key === "RETENTION_DATABASE_URL" ? "2" : "1"}))}};

test("지정한 보호된 dev의 최신 커밋에서 수동으로 실행해야 한다", () => {
  checkDispatch(dispatch, sha);
  for (const [k,v] of Object.entries({GITHUB_EVENT_NAME: "push", GITHUB_REF: "refs/heads/master",
    GITHUB_REF_PROTECTED: "false", GITHUB_REPOSITORY_ID: "1", GITHUB_REPOSITORY_OWNER_ID: "1",
    GITHUB_WORKFLOW_REF: "다른 워크플로", CONFIRM_DEV_SHA: "a".repeat(7)})) {
    assert.throws(() => checkDispatch({...dispatch,[k]: v}, sha));
  }
  assert.throws(() => checkDispatch(dispatch, "b".repeat(40)));
});

test("독립 승인자 한 명, 관리자 우회 금지, dev 단일 브랜치가 필수다", () => {
  checkEnvironment(env, policies);
  for (const edit of [(e) => {e.can_admins_bypass = true;}, (e) => {e.protection_rules = [];},
    (e) => {e.protection_rules[0].prevent_self_review = false;}, (e) => {e.protection_rules[0].reviewers.push({});},
    (e) => {e.protection_rules[0].reviewers[0].reviewer.id = 1;}, (e) => {e.name = "production";}]) {
    const e = structuredClone(env); edit(e); assert.throws(() => checkEnvironment(e, policies));
  }
  for (const policy of [{total_count: 2, branch_policies: policies.branch_policies},
    {total_count: 1, branch_policies: [{name: "dev", type: "tag"}]}]) {
    assert.throws(() => checkEnvironment(env, policy));
  }
});

test("다른 함수, 기본 계정, 공유 저장소, 삭제 설정, 다른 시크릿은 거부한다", () => {
  checkFunction(fn);
  for (const edit of [(f) => {f.name = loc + "/functions/reportMonthlyRetention";},
    (f) => {f.buildConfig.serviceAccount = "default";}, (f) => {f.serviceConfig.serviceAccountEmail = "default";},
    (f) => {f.buildConfig.dockerRepository = loc + "/repositories/gcf-artifacts";},
    (f) => {f.serviceConfig.environmentVariables.RETENTION_APPLY_ENABLED = "true";},
    (f) => {delete f.serviceConfig.environmentVariables.RETENTION_APPLY_ENABLED;},
    (f) => {f.serviceConfig.environmentVariables.GCLOUD_PROJECT = "bodeul-prod-110";},
    (f) => {f.serviceConfig.secretEnvironmentVariables[0].version = "latest";},
    (f) => {f.serviceConfig.secretEnvironmentVariables[0].version = "3";},
    (f) => {f.serviceConfig.secretEnvironmentVariables[1].projectId = "bodeul-prod-110";},
    (f) => {f.serviceConfig.secretVolumes = [{}];}]) {
    const f = structuredClone(fn); edit(f); assert.throws(() => checkFunction(f));
  }
});

test("PATCH에는 소스와 기존 환경변수만 포함하고 실제 삭제를 끈다", () => {
  const src = {bucket: "fixture", object: "source.zip"};
  const patch = patchBody(fn, src);
  assert.deepEqual(patch, {name: fn.name,buildConfig: {source: {storageSource: src}},
    serviceConfig: {environmentVariables: {...fn.serviceConfig.environmentVariables,
      RETENTION_GUARDIAN_BOOKING_ENABLED: "true", RETENTION_APPLY_ENABLED: "false"}}});
  assert.equal(fn.serviceConfig.environmentVariables.RETENTION_GUARDIAN_BOOKING_ENABLED, undefined);
});

test("서명된 업로드는 개발 프로젝트와 리전의 Google Storage 주소만 허용한다", () => {
  const bucket = "gcf-v2-uploads-533563500316.asia-northeast3.cloudfunctions.appspot.com";
  const ticket = {uploadUrl: "https://storage.googleapis.com/" + bucket + "/src.zip", storageSource: {bucket, object: "src.zip"}};
  checkUpload(ticket);
  checkUpload({...ticket, uploadUrl: "https://" + bucket + ".storage.googleapis.com/src.zip"});
  for (const url of ["https://example.org/" + bucket + "/src.zip", "https://storage.googleapis.com/other/src.zip",
    "http://storage.googleapis.com/" + bucket + "/src.zip", "https://storage.googleapis.com/" + bucket + "/other.zip",
    "https://" + bucket + ".storage.googleapis.com/other.zip"]) {
    assert.throws(() => checkUpload({...ticket, uploadUrl: url}));
  }
  assert.throws(() => checkUpload({...ticket, storageSource: {bucket: "production", object: "src.zip"}}));
});

test("배포 ZIP은 지정한 커밋의 함수 파일만 포함하고 심볼릭 링크를 거부한다", (t) => {
  const cwd = mkdtempSync(join(tmpdir(), "bodeul-retention-archive-test-"));
  t.after(() => rmSync(cwd, {recursive: true, force: true}));
  const git = (...args) => execFileSync("git", ["-c", "user.name=fixture", "-c", "user.email=fixture@example.invalid",
    "-c", "core.autocrlf=false", "-c", "commit.gpgSign=false", ...args], {cwd, encoding: "utf8"}).trim();
  git("init", "--quiet");
  const dir = join(cwd, "functions");
  mkdirSync(join(dir, "src"), {recursive: true});
  for (const f of ["index.js", "src/retention.js", "package.json", "package-lock.json", "ignored-fixture.txt"]) {
    writeFileSync(join(dir, f), "fixture");
  }
  git("add", "functions");
  git("commit", "--quiet", "-m", "fixture");
  const commit = git("rev-parse", "HEAD");
  writeFileSync(join(dir, "src", "untracked.js"), "fixture");
  const zip = sourceZip(commit, cwd, cwd);
  assert.equal(zip.subarray(0, 2).toString(), "PK");
  for (const name of ["index.js", "src/retention.js", "package.json", "package-lock.json"]) assert.ok(zip.includes(Buffer.from(name)));
  for (const name of ["ignored-fixture.txt", "untracked.js"]) assert.ok(!zip.includes(Buffer.from(name)));
  assert.throws(() => sourceZip("HEAD", cwd, cwd));
  const blob = git("rev-parse", "HEAD:functions/index.js");
  git("update-index", "--add", "--cacheinfo", "120000," + blob + ",functions/src/link.js");
  git("commit", "--quiet", "-m", "fixture-link");
  assert.throws(() => sourceZip(git("rev-parse", "HEAD"), cwd, cwd), /파일 유형/);
});
