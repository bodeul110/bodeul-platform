import assert from "node:assert/strict";
import {spawnSync} from "node:child_process";
import {fileURLToPath} from "node:url";

const root = fileURLToPath(new URL("../../", import.meta.url));
const checks = [
  {
    name: "Debug에 운영 Core API 주입 차단",
    task: ":app:verifyDebugEnvironment",
    args: ["-PbodeulCoreApiBaseUrl=https://bodeul-core-api-649312328770.asia-northeast1.run.app"],
    error: "Core API 주소가 빌드 환경과 일치하지 않습니다.",
  },
  {
    name: "Debug에 운영 Realtime 주입 차단",
    task: ":app:verifyDebugEnvironment",
    args: ["-PbodeulCoreApiBaseUrl=https://bodeul-core-api-preview-cyvvxy3kia-an.a.run.app",
      "-PbodeulSupabaseDebugUrl=https://aoijbzgozbopsxzrasbb.supabase.co",
      "-PbodeulSupabaseDebugPublishableKey=sb_publishable_test"],
    error: "Supabase URL과 publishable key가 빌드 환경과 일치하지 않습니다.",
  },
  {
    name: "Release에 개발 Core API 주입 차단",
    task: ":app:verifyReleaseEnvironment",
    args: ["-PbodeulCoreApiReleaseBaseUrl=https://bodeul-core-api-preview-cyvvxy3kia-an.a.run.app"],
    error: "Core API 주소가 빌드 환경과 일치하지 않습니다.",
  },
  {
    name: "Release에 개발 Realtime 주입 차단",
    task: ":app:verifyReleaseEnvironment",
    args: ["-PbodeulCoreApiReleaseBaseUrl=https://bodeul-core-api-649312328770.asia-northeast1.run.app",
      "-PbodeulSupabaseReleaseUrl=https://parpdzttloacinyvhwmx.supabase.co",
      "-PbodeulSupabaseReleasePublishableKey=sb_publishable_test"],
    error: "Supabase URL과 publishable key가 빌드 환경과 일치하지 않습니다.",
  },
];

// 로컬 설정을 덮어쓰지 않으므로 CI 또는 별도 검증 checkout에서 실행한다.
for (const check of checks) {
  const args = [check.task, ...check.args, "--console=plain", "--no-configuration-cache"];
  const windows = process.platform === "win32";
  const result = spawnSync(windows ? "cmd.exe" : "./gradlew",
    windows ? ["/d", "/c", "gradlew.bat", ...args] : args,
    {cwd: root, encoding: "utf8", timeout: 120_000, windowsHide: true});
  assert.ifError(result.error);
  assert.notEqual(result.status, 0, `${check.name}: 잘못된 환경을 허용했습니다.`);
  assert.ok(`${result.stdout}${result.stderr}`.includes(check.error),
    `${check.name}: 예상한 환경 오류가 아닙니다. 로컬 설정 override 여부를 확인하세요.`);
  process.stdout.write(`통과: ${check.name}\n`);
}
