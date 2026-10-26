const assert = require("node:assert/strict");
const test = require("node:test");

const {BASELINE_GUIDES, BASELINE_USERS} = require("../lib/baseline-config");
const {
  SAMPLE_COLLECTIONS,
  buildSampleState,
} = require("../seed-sample-service-data");

test("샘플 서비스 데이터는 신규 SOS와 긴급 이슈를 만들지 않는다", () => {
  const baselineUsers = BASELINE_USERS.map((user) => ({
    ...user,
    uid: `uid-${user.role.toLowerCase()}`,
  }));
  const sampleState = buildSampleState(baselineUsers);
  const followUp = sampleState.documents.find((document) =>
    document.path === "appointmentFollowUps/request-seed-completed",
  );
  const completedScenario = sampleState.scenarios.find((scenario) =>
    scenario.requestId === "request-seed-completed",
  );

  assert.equal(SAMPLE_COLLECTIONS.includes("adminEmergencyIssues"), false);
  assert.equal(
      sampleState.documents.some((document) =>
        document.collection === "adminEmergencyIssues",
      ),
      false,
  );
  assert.equal(Object.hasOwn(followUp.data, "supportEscalationStatus"), false);
  assert.equal(Object.hasOwn(followUp.data, "supportEscalatedAt"), false);
  assert.doesNotMatch(completedScenario.summary, /SOS|긴급/);
});

test("기준선과 예약 샘플은 서울대학교병원 이름을 일관되게 사용한다", () => {
  const baselineUsers = BASELINE_USERS.map((user) => ({
    ...user,
    uid: `uid-${user.role.toLowerCase()}`,
  }));
  const sampleState = buildSampleState(baselineUsers);
  const appointmentDocuments = sampleState.documents.filter((document) =>
    document.collection === "appointmentRequests",
  );

  assert.equal(BASELINE_GUIDES[0].hospitalName, "서울대학교병원");
  assert.ok(appointmentDocuments.length > 0);
  for (const appointment of appointmentDocuments) {
    assert.equal(appointment.data.hospitalName, "서울대학교병원");
  }
});
