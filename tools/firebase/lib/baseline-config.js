const DAY_IN_MILLIS = 24 * 60 * 60 * 1000;
const DEFAULT_PASSWORD = "bodeul1234";
const LIST_PAGE_SIZE = 200;

const MANAGED_COLLECTIONS = Object.freeze([
  "users",
  "hospitalGuides",
  "appointmentRequests",
  "companionSessions",
  "sessionReports",
  "appointmentFollowUps",
  "supportInquiries",
  "clientSupportRequests",
  "adminSettlementRecords",
  "adminEmergencyIssues",
  "adminActionNotifications",
  "adminAuditLogs",
  "adminActionDeliveries",
  "adminActionDeliveryJobs",
  "appointmentReminderJobs",
]);

const BASELINE_USERS = Object.freeze([
  {
    role: "ADMIN",
    name: "관리자",
    email: "admin@bodeul.app",
    phone: "010-0000-0004",
  },
  {
    role: "PATIENT",
    name: "이현우",
    email: "patient@bodeul.app",
    phone: "010-0000-0001",
  },
  {
    role: "GUARDIAN",
    name: "김하나",
    email: "guardian@bodeul.app",
    phone: "010-0000-0002",
  },
  {
    role: "MANAGER",
    name: "김보들",
    email: "manager@bodeul.app",
    phone: "010-0000-0003",
    managerProfile: {
      managerDocumentSummary: "요양보호사 자격증 제출 완료",
      managerAvailabilitySummary: "평일 09:00-18:00, 주요 병원 동행 가능",
      managerDocumentStatus: "APPROVED",
      managerDocumentReviewNote: "관리자 검토를 마쳤습니다. 이번 주 일정만 최신으로 유지해 주세요.",
      managerDocumentReviewedByName: "관리자",
    },
  },
]);

const BASELINE_GUIDES = Object.freeze([
  {
    id: "guide-seed-seoul-internal-medicine",
    hospitalName: "서울대학교병원",
    departmentName: "내과",
    steps: [
      {
        order: 1,
        title: "환자 접수",
        description: "환자분의 내원 여부를 확인하고 보호자에게 출발 상황을 공유합니다.",
      },
      {
        order: 2,
        title: "접수 등록",
        description: "접수 창구에서 예약 정보와 신분증을 확인합니다.",
      },
      {
        order: 3,
        title: "진료 접수",
        description: "진료과 대기 순서를 확인하고 필요한 서류를 제출합니다.",
      },
      {
        order: 4,
        title: "진료 완료",
        description: "진료 결과와 다음 안내 사항을 메모합니다.",
      },
      {
        order: 5,
        title: "수납 처리",
        description: "수납 및 결제 예약 여부를 확인합니다.",
      },
      {
        order: 6,
        title: "약국 방문",
        description: "처방전을 수령하고 복용법을 정리합니다.",
      },
      {
        order: 7,
        title: "환자 귀가(서비스 종료)",
        description: "귀가 동선을 확인하고 보호자에게 최종 상황을 전달합니다.",
      },
    ],
  },
]);

module.exports = {
  DAY_IN_MILLIS,
  DEFAULT_PASSWORD,
  LIST_PAGE_SIZE,
  MANAGED_COLLECTIONS,
  BASELINE_USERS,
  BASELINE_GUIDES,
};
