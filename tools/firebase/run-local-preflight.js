#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const {spawn} = require("child_process");
const {main: runOperationsWorkflow} = require("./run-operations-workflow");

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const repoRoot = path.resolve(__dirname, "..", "..");
  const toolsRoot = path.resolve(__dirname);
  const reportsRoot = path.join(toolsRoot, "reports");
  const preflightStartedAt = Date.now();
  const steps = [];

  let workflowArtifacts = {
    reportPath: "",
    summaryPath: "",
    workflowSummary: null,
  };

  if (!options.skipWorkflow) {
    const workflowStartedAt = Date.now();
    const workflowEnv = {};
    if (options.filePath) {
      workflowEnv.BODEUL_WORKFLOW_FILE_PATH = options.filePath;
    }
    if (options.appEvidencePath) {
      workflowEnv.BODEUL_WORKFLOW_APP_EVIDENCE_PATH = options.appEvidencePath;
    }

    const workflowStep = await runWorkflow({
      cwd: toolsRoot,
      env: workflowEnv,
      label: "Firebase 운영 워크플로",
    });
    steps.push(workflowStep);
    workflowArtifacts = resolveWorkflowArtifacts(reportsRoot, workflowStartedAt);
  }

  const androidArgs = [];
  if (!options.skipBuild) {
    androidArgs.push("assembleDebug");
  }
  if (!options.skipTests) {
    androidArgs.push("testDebugUnitTest", "assembleDebugAndroidTest");
  }
  if (androidArgs.length > 0) {
    steps.push(await runCommand({
      command: resolveGradleCommand(),
      args: androidArgs.concat("--console=plain"),
      cwd: repoRoot,
      label: buildAndroidStepLabel({skipBuild: options.skipBuild, skipTests: options.skipTests}),
    }));
  }

  const summary = buildPreflightSummary({
    startedAt: preflightStartedAt,
    finishedAt: Date.now(),
    repoRoot,
    backupPath: options.filePath ? path.resolve(toolsRoot, options.filePath) : "",
    steps,
    workflowArtifacts,
  });

  const summaryPaths = writePreflightSummary(summary, reportsRoot, options.summaryPath);

  if (options.json) {
    console.log(JSON.stringify(summary, null, 2));
  } else {
    printSummary(summary, summaryPaths);
  }

  if (summary.overallStatus !== "passed") {
    process.exitCode = 1;
  }
}

function parseOptions(args) {
  const appEvidenceIndex = args.indexOf("--app-evidence");
  const fileIndex = args.indexOf("--file");
  const summaryIndex = args.indexOf("--summary");
  return {
    appEvidencePath: appEvidenceIndex >= 0 ? args[appEvidenceIndex + 1] : "",
    filePath: fileIndex >= 0 ? args[fileIndex + 1] : "",
    help: args.includes("--help") || args.includes("-h"),
    json: args.includes("--json"),
    skipBuild: args.includes("--skip-build"),
    skipTests: args.includes("--skip-tests"),
    skipWorkflow: args.includes("--skip-workflow"),
    summaryPath: summaryIndex >= 0 ? args[summaryIndex + 1] : "",
  };
}

function printHelp() {
  console.log("보들 로컬 프리플라이트 스크립트");
  console.log("");
  console.log("사용법");
  console.log("  node run-local-preflight.js");
  console.log("  node run-local-preflight.js --file backups/firestore-backup.json");
  console.log("  node run-local-preflight.js --app-evidence reports/app-navigation-evidence-latest.json");
  console.log("  node run-local-preflight.js --skip-tests");
  console.log("");
  console.log("기본 동작");
  console.log("- Firebase 운영 워크플로를 실행합니다.");
  console.log("- Android assembleDebug, testDebugUnitTest, assembleDebugAndroidTest를 실행합니다.");
  console.log("- 최종 결과를 reports 폴더의 Markdown/JSON 요약으로 남깁니다.");
}

function resolveGradleCommand() {
  return process.platform === "win32" ? "gradlew.bat" : "./gradlew";
}

function buildAndroidStepLabel({skipBuild, skipTests}) {
  if (!skipBuild && !skipTests) {
    return "Android assembleDebug, testDebugUnitTest 및 assembleDebugAndroidTest";
  }
  if (!skipBuild) {
    return "Android assembleDebug";
  }
  return "Android testDebugUnitTest 및 assembleDebugAndroidTest";
}

async function runWorkflow({cwd, env = {}, label}) {
  const startedAt = Date.now();
  const previousCwd = process.cwd();
  const commandText = "node run-operations-workflow.js";

  try {
    process.chdir(cwd);
    await runOperationsWorkflow([], {
      ...process.env,
      ...env,
    });
    return {
      label,
      command: commandText,
      cwd,
      startedAt: new Date(startedAt).toISOString(),
      finishedAt: new Date().toISOString(),
      durationMillis: Date.now() - startedAt,
      exitCode: 0,
      success: true,
      errorMessage: "",
    };
  } catch (error) {
    return {
      label,
      command: commandText,
      cwd,
      startedAt: new Date(startedAt).toISOString(),
      finishedAt: new Date().toISOString(),
      durationMillis: Date.now() - startedAt,
      exitCode: -1,
      success: false,
      errorMessage: `${error.message}`,
    };
  } finally {
    process.chdir(previousCwd);
  }
}

async function runCommand({command, args, cwd, label}) {
  const startedAt = Date.now();
  const safeCommand = resolveAllowedCommand(command);
  const safeArgs = args.map(validateSpawnArgument);
  const commandText = [safeCommand].concat(safeArgs).join(" ");
  const spawnConfig = resolveSpawnConfig(safeCommand, safeArgs);

  return new Promise((resolve) => {
    const child = spawn(spawnConfig.command, spawnConfig.args, {
      cwd,
      stdio: "inherit",
      shell: false,
    });

    child.on("error", (error) => {
      resolve({
        label,
        command: commandText,
        cwd,
        startedAt: new Date(startedAt).toISOString(),
        finishedAt: new Date().toISOString(),
        durationMillis: Date.now() - startedAt,
        exitCode: -1,
        success: false,
        errorMessage: `${error.message}`,
      });
    });

    child.on("exit", (exitCode) => {
      resolve({
        label,
        command: commandText,
        cwd,
        startedAt: new Date(startedAt).toISOString(),
        finishedAt: new Date().toISOString(),
        durationMillis: Date.now() - startedAt,
        exitCode: typeof exitCode === "number" ? exitCode : -1,
        success: exitCode === 0,
        errorMessage: "",
      });
    });
  });
}

function resolveSpawnConfig(command, args) {
  if (process.platform === "win32" && /\.(cmd|bat)$/i.test(command)) {
    return {
      command: resolveWindowsCommandProcessor(),
      args: ["/d", "/s", "/c", formatWindowsCommand(command, args)],
    };
  }

  return {command, args};
}

function resolveWindowsCommandProcessor() {
  const defaultCommandProcessor = "C:\\Windows\\System32\\cmd.exe";
  return fs.existsSync(defaultCommandProcessor) ? defaultCommandProcessor : "cmd.exe";
}

function resolveAllowedCommand(command) {
  const allowedCommands = new Set([
    resolveGradleCommand(),
  ]);
  if (!allowedCommands.has(command)) {
    throw new Error(`허용되지 않은 로컬 preflight 명령입니다: ${command}`);
  }
  return command;
}

function validateSpawnArgument(value) {
  const argument = String(value);
  if (/[\0\r\n]/.test(argument)) {
    throw new Error("로컬 preflight 명령 인자에 사용할 수 없는 제어문자가 포함되어 있습니다.");
  }
  return argument;
}

function formatWindowsCommand(command, args) {
  return [command].concat(args).map(escapeWindowsArgument).join(" ");
}

function escapeWindowsArgument(value) {
  if (value === "") {
    return "\"\"";
  }

  if (!/[\s"&()^<>|]/.test(value)) {
    return value;
  }

  const escapedValue = value
      .replace(/(\\*)"/g, "$1$1\\\"")
      .replace(/(\\+)$/g, "$1$1");
  return `"${escapedValue}"`;
}

function resolveWorkflowArtifacts(reportsRoot, startedAt) {
  const workflowSummaryPath = findLatestArtifact(
      reportsRoot,
      "firestore-operations-summary-",
      ".json",
      startedAt,
  );
  const workflowReportPath = findLatestArtifact(
      reportsRoot,
      "firestore-operations-report-",
      ".html",
      startedAt,
  );

  let workflowSummary = null;
  if (workflowSummaryPath && fs.existsSync(workflowSummaryPath)) {
    workflowSummary = JSON.parse(fs.readFileSync(workflowSummaryPath, "utf8"));
  }

  return {
    reportPath: workflowReportPath,
    summaryPath: workflowSummaryPath,
    workflowSummary,
  };
}

function findLatestArtifact(directoryPath, prefix, extension, startedAt) {
  if (!fs.existsSync(directoryPath)) {
    return "";
  }

  let latestMatch = null;
  for (const item of fs.readdirSync(directoryPath)) {
    if (!item.startsWith(prefix) || !item.endsWith(extension)) {
      continue;
    }
    const fullPath = path.join(directoryPath, item);
    const stat = fs.statSync(fullPath);
    if (stat.mtimeMs < startedAt) {
      continue;
    }
    if (!latestMatch || stat.mtimeMs > latestMatch.mtimeMs) {
      latestMatch = {
        fullPath,
        mtimeMs: stat.mtimeMs,
      };
    }
  }

  return latestMatch ? latestMatch.fullPath : "";
}

function buildPreflightSummary({
  startedAt,
  finishedAt,
  repoRoot,
  backupPath,
  steps,
  workflowArtifacts,
}) {
  const workflowSummary = workflowArtifacts.workflowSummary;
  const workflowPassed = !workflowSummary || workflowSummary.overallStatus === "ready";
  const stepsPassed = steps.every((step) => step.success);
  const overallStatus = stepsPassed && workflowPassed ? "passed" : "failed";

  return {
    generatedAt: new Date(finishedAt).toISOString(),
    startedAt: new Date(startedAt).toISOString(),
    finishedAt: new Date(finishedAt).toISOString(),
    durationMillis: finishedAt - startedAt,
    repoRoot,
    backupPath,
    overallStatus,
    workflow: workflowSummary ? {
      status: workflowSummary.overallStatus,
      reportPath: workflowArtifacts.reportPath,
      summaryPath: workflowArtifacts.summaryPath,
      readyRoleCount: workflowSummary.readiness.readyRoleCount,
      totalRoles: workflowSummary.readiness.totalRoles,
      passedScenarioCount: workflowSummary.readiness.passedScenarioCount,
      totalScenarios: workflowSummary.readiness.totalScenarios,
      backupValidation: workflowSummary.backupValidation,
      diffSummary: workflowSummary.diffSummary,
      appEvidence: workflowSummary.appEvidence || null,
    } : null,
    steps,
  };
}

function writePreflightSummary(summary, reportsRoot, summaryPath) {
  fs.mkdirSync(reportsRoot, {recursive: true});
  const token = buildTimestampToken(new Date(summary.finishedAt));
  const basePath = summaryPath ?
    path.resolve(process.cwd(), summaryPath) :
    path.join(reportsRoot, `local-preflight-summary-${token}`);

  const markdownPath = basePath.endsWith(".md") || basePath.endsWith(".json") ?
    basePath.replace(/\.(md|json)$/i, ".md") :
    `${basePath}.md`;
  const jsonPath = basePath.endsWith(".md") || basePath.endsWith(".json") ?
    basePath.replace(/\.(md|json)$/i, ".json") :
    `${basePath}.json`;

  fs.writeFileSync(markdownPath, buildMarkdownSummary(summary), "utf8");
  fs.writeFileSync(jsonPath, `${JSON.stringify(summary, null, 2)}\n`, "utf8");

  return {markdownPath, jsonPath};
}

function buildMarkdownSummary(summary) {
  const lines = [
    "# 보들 로컬 프리플라이트",
    "",
    `- 상태: ${summary.overallStatus === "passed" ? "통과" : "실패"}`,
    `- 시작: ${summary.startedAt}`,
    `- 종료: ${summary.finishedAt}`,
    `- 소요 시간(ms): ${summary.durationMillis}`,
  ];

  if (summary.backupPath) {
    lines.push(`- 기준 백업: ${summary.backupPath}`);
  }

  if (summary.workflow) {
    lines.push(`- Firebase 운영 워크플로 상태: ${summary.workflow.status}`);
    lines.push(`- 역할 준비도: ${summary.workflow.readyRoleCount}/${summary.workflow.totalRoles}`);
    lines.push(
        `- 샘플 시나리오: ${summary.workflow.passedScenarioCount}/${summary.workflow.totalScenarios}`,
    );
    if (summary.workflow.appEvidence) {
      lines.push(
          `- 앱 화면 증적: 통과 ${summary.workflow.appEvidence.summary.passedCount} / 경고 ${summary.workflow.appEvidence.summary.warningCount} / 실패 ${summary.workflow.appEvidence.summary.failedCount}`,
      );
      lines.push(`- 앱 화면 증적 파일: ${summary.workflow.appEvidence.filePath}`);
    }
    if (summary.workflow.reportPath) {
      lines.push(`- HTML 리포트: ${summary.workflow.reportPath}`);
    }
    if (summary.workflow.summaryPath) {
      lines.push(`- 워크플로 JSON 요약: ${summary.workflow.summaryPath}`);
    }
  }

  lines.push("");
  lines.push("## 단계 결과");
  lines.push("");
  for (const step of summary.steps) {
    lines.push(`- ${step.label}: ${step.success ? "통과" : "실패"}`);
    lines.push(`  - 명령: \`${step.command}\``);
    lines.push(`  - 작업 디렉터리: \`${step.cwd}\``);
    lines.push(`  - 종료 코드: ${step.exitCode}`);
    lines.push(`  - 소요 시간(ms): ${step.durationMillis}`);
    if (step.errorMessage) {
      lines.push(`  - 오류: ${step.errorMessage}`);
    }
  }

  return `${lines.join("\n")}\n`;
}

function buildTimestampToken(date) {
  return [
    String(date.getFullYear()),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
    "-",
    String(date.getHours()).padStart(2, "0"),
    String(date.getMinutes()).padStart(2, "0"),
    String(date.getSeconds()).padStart(2, "0"),
  ].join("");
}

function printSummary(summary, summaryPaths) {
  console.log("보들 로컬 프리플라이트");
  console.log(`- 상태: ${summary.overallStatus === "passed" ? "통과" : "실패"}`);
  if (summary.workflow) {
    console.log(`- Firebase 운영 워크플로: ${summary.workflow.status}`);
    console.log(`- 역할 준비도: ${summary.workflow.readyRoleCount}/${summary.workflow.totalRoles}`);
    console.log(
        `- 샘플 시나리오: ${summary.workflow.passedScenarioCount}/${summary.workflow.totalScenarios}`,
    );
    if (summary.workflow.appEvidence) {
      console.log(
          `- 앱 화면 증적: 통과 ${summary.workflow.appEvidence.summary.passedCount} / 경고 ${summary.workflow.appEvidence.summary.warningCount} / 실패 ${summary.workflow.appEvidence.summary.failedCount}`,
      );
    }
  }
  console.log(`- Markdown 요약: ${summaryPaths.markdownPath}`);
  console.log(`- JSON 요약: ${summaryPaths.jsonPath}`);
}

main().catch((error) => {
  console.error("로컬 프리플라이트 스크립트 실행 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
