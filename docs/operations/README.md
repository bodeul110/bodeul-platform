# 운영 문서

개발 협업, 내부 테스트, QA, Firebase 운영 절차를 모아둔다.

## 협업/테스트

- [협업 규칙](collaboration-rules.md)
  - 작업 시작 전 확인, 충돌 방지, 문서 갱신 원칙
- [내부 테스트 가이드](internal-test-guide.md)
  - 테스트 계정, 더미 데이터, 역할별 테스트 순서
- [관리자 권한 QA 체크리스트](admin-access-qa-checklist.md)
  - Firebase 연동 앱 관리자 업무 차단과 웹 세부 역할 검증
- [GitHub self-hosted runner 운영 기준](github-self-hosted-runner.md)
  - 수동 프리플라이트용 자체 실행기 등록, 라벨, 보안 기준
- [App Check 적용 로드맵](app-check-enforcement-roadmap.md)
  - Android, 관리자 웹, Functions, Firestore, Storage enforcement 전환 기준
- [관리자 웹 저장소 분리 기록](admin-web-repository-split.md)
  - 별도 저장소 source of truth와 메인 저장소 소유권 정리
- [관리자 웹 환경 기준](admin-web-environments.md)
  - Vercel Preview와 production의 Firebase·PostgreSQL 환경변수, TLS와 배포 기준
- [관리자 API 환경 문서 이전 안내](admin-api-environments.md)
  - 종료된 Node API 환경 기준과 현재 문서 위치
- [PostgreSQL 운영 전환 런북](postgres-operational-transition-runbook.md)
  - Supabase, Cloud Run, GitHub Environment, API 서버 전환 준비 절차
- [Spring Core API Cloud Run 인프라 런북](core-api-infrastructure-runbook.md)
  - Core API 컨테이너, WIF, Secret Manager, 배포와 rollback 절차
- [Production 인프라 읽기 전용 점검](production-infrastructure-audit.md)
  - metadata-only 감사와 기대 구성 대조
- [Google Cloud 접근 관리](google-cloud-access-governance.md)
  - 사람 계정·그룹과 CI·서비스 계정 경계
- [Google Cloud 공식 조직 이전](google-cloud-organization-migration.md)
  - 개발·운영 프로젝트의 공식 조직 소속, 이전 검증과 복귀 경계
- [프로젝트와 인프라 명칭](resource-naming.md)
  - 역할·환경별 표시 이름, 유지할 식별자, GitHub 이름 변경의 인증 점검 순서
- [Android 릴리스 서명](android-release-signing.md)
  - 팀 소유 서명과 release 검증 준비
- [Core API 가이드 실기기 fixture](core-api-guide-device-fixture.md)
  - preview 13단계 가이드의 setup, status, cleanup과 실기기 검증 기준
- [Preview 병원 가이드 V1 전환](core-api-preview-hospital-guide-v1-promotion.md)
  - 개발 DB의 서울내과병원 7단계 가이드를 보존형 V1 계약으로 조회·전환하는 승인 절차
- [인프라 운영 기준](infrastructure-operations-baseline.md)
  - 관리자 웹 배포 방식, 비용 리스크, App Check, 인덱스, 백업/복원, Functions, 운영 명령어
- [Production 인프라 기본값](production-infrastructure-defaults.md)
  - 운영 프로젝트·서비스 이름, Tokyo 리전, 배포·권한·백업 기준과 사람 결정 항목
- [Production DB migration 사전 점검](production-database-migration-readiness.md)
  - 운영 Supabase 연결 대상, Flyway version과 V14·V15 영향 건수의 읽기 전용 확인 절차
- [2026년 Production 운영 전환 계획](production-transition-plan-2026.md)
  - 임시 일정과 실제 전환 게이트 구분, 월 비용 한도와 환경별 준비 순서
- [비용과 쿼터 모니터링](cost-monitoring.md)
  - Google Cloud budget, Firebase/Cloud Run metric, Kakao Local 쿼터와 알림 대응
- [데이터 보관 및 파기 정책](data-retention-policy.md)
  - 위치, 채팅, 첨부와 매니저 증빙의 보관 기간, legal hold와 자동 파기 기준
- [위치 이력 보관 및 노출 정책](location-history-retention-policy.md)
  - 환자 위치 목표, legacy 공유 기본 OFF, 로컬 위치 보기와 서버 공유 구분

## Firebase 운영

- [Firebase 운영 문서](firebase/README.md)
- [Firebase 설정](firebase/setup.md)
- [Firebase 운영 도구](firebase/tools.md)
- [Firebase 기준선 초기화](firebase/reset-baseline.md)
- [개발 Firebase 자동 파기 픽스처](firebase/retention-development-fixture.md)
- [Production Firebase 자동 파기 격리 픽스처](firebase/retention-production-fixture.md)

## 관련 문서

- [FCM 토큰 수명주기 정책](../security/fcm-token-lifecycle-policy.md)
- [카카오 지도 실좌표 설정 메모](firebase/setup.md#카카오-병원약국-실좌표-검색-메모)
- [실기기 테스트 보고서](../reports/device-test-report-2026-06-20.md)
