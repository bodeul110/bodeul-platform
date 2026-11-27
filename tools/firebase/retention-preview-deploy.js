"use strict";

const {execFileSync} = require("node:child_process");
const {isDeepStrictEqual} = require("node:util");
const {setTimeout: sleep} = require("node:timers/promises");

const repo = "bodeul110/bodeul-platform";
const environment = "firebase-retention-preview";
const project = "bodeul-dev";
const number = "533563500316";
const region = "asia-northeast3";
const location = "projects/" + project + "/locations/" + region;
const target = location + "/functions/cleanupExpiredData";
const service = (suffix) => "bodeul-retention-dev-" + suffix + "@" + project + ".iam.gserviceaccount.com";
const registry = location + "/repositories/bodeul-retention-dev";
const cloudApi = "https://cloudfunctions.googleapis.com/v2/";
const uploadBucket = "gcf-v2-uploads-" + number + "." + region + ".cloudfunctions.appspot.com";
const secretVersions = {RETENTION_DATABASE_CA_CERT: "1", RETENTION_DATABASE_URL: "2"};

function requireThat(value, message) {
  if (!value) throw new Error(message);
}

function checkDispatch(ctx, head) {
  requireThat(ctx.GITHUB_REPOSITORY === repo && ctx.GITHUB_REPOSITORY_ID === "1209358990" &&
      ctx.GITHUB_REPOSITORY_OWNER_ID === "275679915" && ctx.GITHUB_EVENT_NAME === "workflow_dispatch" &&
      ctx.GITHUB_REF === "refs/heads/dev" && ctx.GITHUB_REF_PROTECTED === "true" &&
      ctx.GITHUB_WORKFLOW_REF === repo + "/.github/workflows/firebase-rules.yml@refs/heads/dev" &&
      /^[a-f0-9]{40}$/.test(ctx.CONFIRM_DEV_SHA || "") &&
      head === ctx.GITHUB_SHA && head === ctx.CONFIRM_DEV_SHA, "승인 대상이 현재 보호된 dev 커밋과 다릅니다.");
}

function checkEnvironment(env, policies) {
  const required = env.protection_rules?.find((r) => r.type === "required_reviewers");
  const reviewers = required?.reviewers;
  requireThat(env.name === environment && env.can_admins_bypass === false &&
      env.deployment_branch_policy?.custom_branch_policies === true &&
      required?.prevent_self_review === true && reviewers?.length === 1 &&
      reviewers[0].type === "User" && reviewers[0].reviewer.id === 275679915 &&
      policies.total_count === 1 && policies.branch_policies?.length === 1 &&
      policies.branch_policies[0].type === "branch" && policies.branch_policies[0].name === "dev",
  "독립 승인과 dev 전용 브랜치 보호가 모두 설정되어야 합니다.");
}

function gh(path) {
  return JSON.parse(execFileSync("gh", ["api", "repos/" + repo + "/" + path], {encoding: "utf8"}));
}

function guard() {
  const head = gh("git/ref/heads/dev").object.sha;
  checkDispatch(process.env, head);
  requireThat(execFileSync("git", ["rev-parse", "HEAD"], {encoding: "utf8"}).trim() === head,
      "로컬 체크아웃이 확인한 커밋이 아닙니다.");
  checkEnvironment(gh("environments/" + environment), gh("environments/" + environment + "/deployment-branch-policies"));
  return head;
}

function checkFunction(fn) {
  const b = fn.buildConfig;
  const s = fn.serviceConfig;
  requireThat(fn.name === target && fn.state === "ACTIVE" && fn.environment === "GEN_2" &&
      b?.entryPoint === "cleanupExpiredData" && b.runtime === "nodejs22" &&
      fn.labels?.["deployment-scheduled"] === "true" && !fn.eventTrigger &&
      s?.service === location + "/services/cleanupexpireddata" &&
      s?.environmentVariables?.GCLOUD_PROJECT === project &&
      s?.environmentVariables?.RETENTION_APPLY_ENABLED === "false", "개발 함수 또는 삭제 차단 설정이 다릅니다.");
  requireThat(b.dockerRepository === registry &&
      [project, number].some((p) => b.serviceAccount === "projects/" + p + "/serviceAccounts/" + service("build")) &&
      s.serviceAccountEmail === service("run"), "전용 빌드·실행 계정과 이미지 저장소가 먼저 연결되어야 합니다.");
  const refs = s.secretEnvironmentVariables || [];
  requireThat(!s.secretVolumes?.length && isDeepStrictEqual(refs.map((r) => r.key).sort(), Object.keys(secretVersions)) &&
      refs.every((r) => r.secret === r.key && [project, number].includes(r.projectId) &&
        r.version === secretVersions[r.key]), "프로젝트 또는 고정 시크릿 버전 연결이 다릅니다.");
}

function checkUpload(ticket) {
  const url = new URL(ticket.uploadUrl);
  const s = ticket.storageSource;
  const path = decodeURIComponent(url.pathname);
  requireThat(url.protocol === "https:" && !url.username && !url.password && !url.port && !url.hash && s &&
      s.bucket === uploadBucket && typeof s.object === "string" &&
      s.object.split("/").every((part) => part && part !== "." && part !== "..") &&
      ((url.hostname === "storage.googleapis.com" && path === "/" + s.bucket + "/" + s.object) ||
       (url.hostname === s.bucket + ".storage.googleapis.com" && path === "/" + s.object)),
  "서명된 주소가 개발 프로젝트의 지정 소스 객체와 일치하지 않습니다.");
}

function patchBody(fn, storageSource) {
  checkFunction(fn);
  return {
    name: target,
    buildConfig: {source: {storageSource}},
    serviceConfig: {environmentVariables: {...fn.serviceConfig.environmentVariables,
      RETENTION_APPLY_ENABLED: "false", RETENTION_GUARDIAN_BOOKING_ENABLED: "true"}},
  };
}

function sourceZip(sha, cwd = process.cwd()) {
  requireThat(/^[a-f0-9]{40}$/.test(sha), "배포 원본은 커밋 SHA 40자로 지정해야 합니다.");
  const tree = sha + ":functions";
  const paths = ["index.js", "package.json", "package-lock.json", "src"];
  const entries = execFileSync("git", ["ls-tree", "-r", tree, "--", ...paths], {cwd, encoding: "utf8"}).trim().split("\n");
  requireThat(paths.slice(0, 3).every((p) => entries.some((e) => e.endsWith("\t" + p))) &&
      entries.some((e) => e.endsWith("\tsrc/retention.js")) &&
      entries.every((e) => /^100644 blob [a-f0-9]{40}\t(index\.js|package(?:-lock)?\.json|src\/(?:[\w-]+\/)*[\w-]+\.js)$/.test(e)),
  "배포 원본 파일 또는 파일 유형이 예상과 다릅니다.");
  // 작업 폴더나 임시 파일을 다시 읽지 않고 검토한 Git 객체에서 바로 ZIP을 받는다.
  return execFileSync("git", ["archive", "--format=zip", tree, "--", ...paths], {cwd, maxBuffer: 8 * 1024 * 1024});
}

async function deploy() {
  const sha = guard();
  const token = process.env.RETENTION_DEPLOY_ACCESS_TOKEN;
  requireThat(token, "개발 전용 WIF 인증이 없습니다.");
  const api = async (path, method = "GET", body) => {
    const res = await fetch(cloudApi + path, {method, redirect: "error",
      headers: {authorization: "Bearer " + token, "content-type": "application/json"},
      ...(body ? {body: JSON.stringify(body)} : {}), signal: AbortSignal.timeout(30000)});
    requireThat(res.ok, "개발 Functions API 요청 실패: HTTP " + res.status);
    return res.json();
  };
  const before = await api(target);
  checkFunction(before);
  const zip = sourceZip(sha);
  const ticket = await api(location + "/functions:generateUploadUrl", "POST", {environment: "GEN_2"});
  checkUpload(ticket);
  // 서명된 주소에는 Google 인증 헤더를 보내지 않으며, 주소와 응답 본문은 로그에 남기지 않는다.
  const put = await fetch(ticket.uploadUrl, {method: "PUT", headers: {"content-type": "application/zip"},
    body: zip, redirect: "error", signal: AbortSignal.timeout(60000)});
  requireThat(put.ok, "함수 소스 업로드 실패: HTTP " + put.status);
  guard();
  const current = await api(target);
  requireThat(before.updateTime && before.updateTime === current.updateTime, "검증 중 함수 설정이 변경됐습니다.");
  const update = await api(target + "?updateMask=buildConfig.source,serviceConfig.environmentVariables",
      "PATCH", patchBody(current, ticket.storageSource));
  const operation = /^projects\/(bodeul-dev|533563500316)\/locations\/asia-northeast3\/operations\/[\w.-]+$/;
  requireThat(operation.test(update.name), "개발 리전의 배포 작업을 확인하지 못했습니다.");
  let op = update;
  const deadline = Date.now() + 18 * 60 * 1000;
  while (!op.done) {
    requireThat(Date.now() < deadline, "배포 결과 대기 시간이 지났습니다. 새 실행을 시작하기 전에 개발 함수 상태를 확인하세요.");
    await sleep(8000);
    op = await api(update.name);
  }
  requireThat(!op.error, "개발 함수 배포 실패: " + (op.error?.code || ""));
  const after = await api(target);
  checkFunction(after);
  requireThat(after.serviceConfig.environmentVariables.RETENTION_GUARDIAN_BOOKING_ENABLED === "true" &&
      after.serviceConfig.revision && after.serviceConfig.revision !== before.serviceConfig.revision,
  "배포 후 새 리비전 또는 집계 설정을 확인하지 못했습니다.");
  console.log("개발 함수 배포 완료: " + after.serviceConfig.revision + " / " + sha + " / DRY_RUN");
}

module.exports = {checkDispatch, checkEnvironment, checkFunction, checkUpload, patchBody, sourceZip};
if (require.main === module) {
  Promise.resolve().then(() => {
    if (process.argv[2] === "guard") return guard();
    if (process.argv[2] === "deploy") return deploy();
    throw new Error("guard 또는 deploy만 지원합니다.");
  }).catch((e) => {
    console.error(e.message);
    process.exitCode = 1;
  });
}
