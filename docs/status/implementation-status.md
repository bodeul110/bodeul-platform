# 현재 구현 상태

코드·문서·GitHub 확인일: 2026-09-21

부분 갱신: 2026-09-27. 최신 Notion MVP·내부 테스트와 관련 이슈·PR을 대조했다. 아래 변경 항목 외 운영 상태를 새로 확인한 것은 아니다.

이 문서의 1~5장은 현재 기준이다. 6장 이후는 날짜별 누적 이력이며 당시 결과를 보존한다. **코드 병합, 특정 환경에서의 검증, 실제 운영 개방은 서로 다르다.**

## 1. 구현한 내용

| 영역 | 현재 코드 | 근거와 경계 |
| --- | --- | --- |
| 진입·인증 | 브랜드 시작, 권한 안내, 역할 선택, 이메일·Google·Kakao 인증 | #385·#432. Launcher는 SplashActivity이며 기존 진입 흐름으로 연결. Naver 로그인은 비활성 |
| 환자·보호자 화면 | 공통 하단 탭, 홈, 병원 검색, 날짜 선택, 건강정보, 예약 완료·상태 | #414~#417·#426 병합. 화면별 실기기·API 왕복 범위는 각 구현 기록을 따름 |
| 예약 | 환자 본인 생성·수정·취소, 공개 예약 코드, 보호자 범위별 조회 | Core API·PostgreSQL 단일 쓰기. 보호자 예약 생성은 #419 후속이며 동의만으로 쓰기를 허용하지 않음 |
| 매칭 | 관리자 서버의 제한 DB 함수로 매니저 배정 | 매니저 self-accept·선착순 공고는 MVP 제외 |
| 매니저 화면 | 홈·자격 인증, 가이드 상봉·접수/대기·문진 준비, C06 진료 보조·C10 처방 자료 화면 | #386·#404·#427·#428·#442·#443 병합. #441의 1~13단계 미리보기는 debug 전용. 화면·타이머를 실제 녹음·공급자 연동 성공으로 보지 않음 |
| 내부 테스트 개선 | 자기 위치 지도 확인, 종료 전 메모 모아보기 | #430·#431 병합. #424는 #410의 9월 22일 DEV 성공 경로 증적과 남은 실패 상태를 구분. #423은 종료·리포트 이동 확인이 남음. 서버 위치 공유·AI 요약이 아님 |
| 동행·리포트 | 현장 종료 CARE_ENDED, 일지·리포트 후 COMPLETED, 실패·재시도 분리 | V18 계약. 메모 모아보기는 300자 최종 일지를 대체하지 않음 |
| 채팅·읽음 | 직접 메시지·읽음·첨부, FCM·Supabase private Broadcast | PostgreSQL 원본. 종료 뒤 신규 입력·매니저 열람 차단, 보호자 동의 범위별 접근 |
| 위치 | legacy 매니저 위치 API·수집 차단 설정 | 기본 OFF, release·production 고정 OFF. 환자 GPS 1분 공유는 목표이며 자기 위치 확인과 별개 |
| 파일 | Core API 세션 첨부·증빙 중계, Storage 원본, PostgreSQL 메타데이터 | 업로드·과거 fixture 성공과 운영 보존·파기 검증을 구분 |
| 무통장입금 | V22 원장·감사, V23 관리자 조회, Android 입금자명, 관리자 수동 전이 | 메인 #394·#397·#405~#407, 웹 #51·#52. 실제 계좌 노출·금전 수취·환불 실행 승인은 아님 |
| 매니저 서류 | JPEG/PNG/WebP 1개·10 MiB 자격 증빙, 관리자 심사 | 메인 #383·웹 #44 병합. Firestore 심사 상태·Storage 원본 유지, legacy 정리는 #313 |
| 관리자 보안 | PostgreSQL ADMIN·세부 3역할, 감사, MFA·세션 경계 | 실제 운영 계정의 역할·MFA·업무 검증은 별도 |
| 관리자 환경 표시 | 로그인·MFA·세션 화면과 상단의 개발/운영 표시 | 웹 #64·#65. 실제 Preview·Production 표시 확인은 [9월 21일 기록](../reports/admin-web-environment-display-2026-09-21.md) |
| 삭제 준비 | PostgreSQL·일부 Firestore 영향도 읽기 전용 집계 | 실제 삭제·Storage 전체·백업·법정 보존 분리는 #348 후속 |

Firebase Auth·FCM·App Check·Storage와 인증 프로필·지원·서류 관련 Functions는 유지한다. legacy 예약 알림·Firestore 도구가 PostgreSQL 업무를 자동 처리한다고 해석하지 않는다. 대상은 [Firebase 운영 도구](../operations/firebase/tools.md)를 따른다.

## 2. 현재 구조 기준

- Android는 Java/XML, Activity·Coordinator·Binder·Repository 경계를 유지한다. Codex/CLI로 개발하며 Android Studio는 필수가 아니다.
- ServiceLocator는 실제 인증 설정이 있으면 Core API와 Firebase 잔존 기능을 조합한다. Firebase 미설정 데모는 Mock이며 API 실패를 Firestore 쓰기로 우회하지 않는다.
- 사용자·매니저는 Spring Core API, 관리자는 별도 Next.js 서버로 같은 환경의 PostgreSQL을 사용한다. 서버 사이 proxy는 없다.
- DDL은 메인 core-api의 Flyway V1~V23만 소유한다. [migration 소스 목록](../architecture/database-migration-catalog.md)과 실제 적용 버전은 별도다.
- 두 저장소 기본 브랜치는 master다. Core Preview·production 배포와 migration은 수동 workflow 경계다. 제안된 dev/main 전략이 적용됐다고 적지 않는다.

## 3. 남은 범위

| 작업 | 현재 판단 |
| --- | --- |
| Preview API #429 | 9월 24일 health·인증 요청·로그 확인 기록을 근거로 닫힘. 이번에는 종료 기록만 확인했으며 화면별 E2E 성공이나 현재 가용성을 새로 검증한 것은 아님 |
| 보호자 예약 #419 | 정책 채택, 현재 환자 전용 쓰기와의 차이 구현 필요 |
| 내부 STT #420 | Naver Cloud 연동·녹음 저장 목표. #443 화면은 병합됐지만 실제 녹음·저장·공급자 검증은 미완료. 추가 논의 메모를 기존 내부 개발 철회나 운영 승인으로 해석하지 않음 |
| 가이드 진입·번호 #422 | 신규·기존 세션, legacy 7단계·13단계 snapshot과 재진입 상태를 구분해 시작 단계·중복 번호 확인 |
| 메모·리포트 #423 | 종료 전 최신 메모 조합은 반영. 마지막 CTA가 채팅으로 연결된다는 새 보고는 역할·단계·빌드 확인 필요. 종료 후 매니저 접근 회수 유지 |
| 글자 가독성 #447·하단 메뉴 #448 | 내부테스트 2에서 보고된 메모·복약 영역 가독성과 예약 후 메뉴 잘림. 현재 master 재현은 미확인 |
| 열린 화면 PR #444·#445·#446 | 단계 전환 스크롤, C11 복약 확인, C08 수납 화면. 9월 27일 기준 미병합이며 본문에 적힌 팀원 검증과 이번 문서 대조를 구분 |
| 가이드 영상 #391 | V21 메타데이터·fallback 계약은 있음. 승인 영상·재생 UI·권리·비식별 검증은 별도 |
| 위치·파기 #222 | 환자 GPS, 동의·철회·중지·파기 종단 검증과 production fixture |
| 자격 서류 #313 | legacy 파일 정리·접근 검증. 신규 최소수집 코드와 구분 |
| App Check #190·#192 | release Play Integrity, 인증된 Web VALID, enforce/rollback |
| 탈퇴 #348·법률 #347 | 실제 삭제, 법정 보존·백업 재적용, 대리권 등 추가 자문 |
| 운영 #134·#223·결제 #27 | production DB·migration·역할별 업무, 배포·rollback·실제 수취 운영과 Go/No-Go |

카드·간편결제 PG, OCR, AI 자동 리포트, 시스템 이벤트형 채팅, SOS·자동119·사고 전용 상태는 현재 MVP의 필수 미완료 목록에 다시 넣지 않는다. 새 요구가 생기면 별도 변경으로 다룬다.

제품 범위는 최신 Notion MVP를 따르고 내부 테스트는 참고·고려 자료로 쓴다. 후기는 UI만 포함하며 실제 후기 저장·정산 제공 완료로 설명하지 않는다. 지도 추가 논의 메모만으로 기존 장소 검색·자기 위치 보기를 제거하거나 모든 예약 병원을 특정 병원으로 바꾸지 않는다.

운영 로그인 계정은 Firebase Auth 등록까지만 진행했다. 비밀번호 설정·DB 역할·MFA·실제 업무 성공은 확인하지 않았다. production DB는 9월 21일 일시정지 확인 후 재개하지 않았으며 [관리자 웹 환경 기준](../operations/admin-web-environments.md)을 따른다.

## 4. 검증 기준

- Android: `.\gradlew.bat assembleDebug --console=plain`, 영향 범위에 따라 `testDebugUnitTest`
- Core API: `.\core-api\gradlew.bat -p core-api check --console=plain`, DB 변경은 격리 migration 검증
- Functions: `npm --prefix functions test`
- Firebase Rules·도구: `npm --prefix tools/firebase run test:rules` 및 관련 toolkit/preflight
- 관리자 웹: 별도 저장소의 test·lint·Next build·Vite rollback build·CI
- 문서: UTF-8, Markdown 링크·경로·앵커, 코드·원본·GitHub와의 정합성

문서 수정만으로 앱·실기기 검증을 재수행했다고 기록하지 않는다. Mock·화면 fixture·개발 API·production 검증을 분리한다. 이번 문서 작업에서는 배포·DB 쓰기·파기를 실행하지 않았다.

## 5. 최근 세부 기록 위치

- [Notion MVP·내부테스트 2 대조 기록](../reports/notion-mvp-feedback-triage-2026-09-27.md)
- [전체 문서 최신화 기록](../reports/document-refresh-2026-09-21.md)
- [Notion 제품 기준 정합성](../planning/notion-product-alignment.md)
- [Figma MVP 화면·Android 매핑](../design/figma-mvp-implementation-map-2026-08-29.md)
- [관리자 웹 환경 표시 검증](../reports/admin-web-environment-display-2026-09-21.md)
- [목표 인프라](../architecture/target-infrastructure.md)와 [migration 목록](../architecture/database-migration-catalog.md)
- [계정 삭제 준비 상태](../operations/account-deletion-readiness.md)와 [데이터 보관 정책](../operations/data-retention-policy.md)

## 6. 누적 변경 이력

아래는 당시 날짜의 작업 기록이다. 과거의 미구현·열린 PR·배포 상태와 버전은 현재 상태를 뜻하지 않는다. 현재 기준은 위 1~5장을 따른다.

## 7. 2026-04-15 추가 업데이트

### 구현

- 매니저 홈에 `서류 검토 상태`, `관리자 메모` 표시를 추가했다.
- 매니저가 서류 요약을 다시 저장하면 상태가 `PENDING_REVIEW`로 바뀌고 기존 관리자 메모는 초기화된다.
- 관리자 화면에 `매니저 서류 검토` 섹션을 추가했다.
- 관리자 화면에서 매니저별 `서류 요약`, `가능 일정`, `검토 메모`를 보고 `승인`, `보완 요청`을 저장할 수 있다.
- 목업 저장소와 Firebase 저장소 모두 같은 상태 모델을 사용하도록 맞췄다.

### 변경 범위

- `domain/model`: `ManagerDocumentStatus`, `ManagerDocumentOverview`, `ManagerHomeProfile`, `AdminDashboard`
- `data`: `AdminRepository`, `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `ui`: `AdminActivity`, `ManagerActivity`
- `layout`: `activity_admin.xml`, `activity_manager_home.xml`, `item_admin_manager_document.xml`, `dialog_admin_document_review.xml`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 매니저 서류 `실제 파일 업로드`와 `증빙 이미지 미리보기`는 아직 없다.
- 관리자 검토 이력의 `타임라인`과 `담당자 로그`는 아직 없다.
- 날짜/시간 선택기의 `빠른 선택 UX`는 1차 반영을 마쳤고, 추가 보강 항목이 남아 있다.

## 8. 2026-04-15 추가 업데이트

### 구현

- 동행 신청 화면에 `빠른 날짜 선택`, `빠른 시간 선택` 버튼을 추가했다.
- `오늘`, `내일`, `모레`, `오전 10시`, `오후 2시`, `오후 4시`를 바로 선택할 수 있다.
- 빠른 날짜와 빠른 시간은 누적 적용되므로 날짜를 먼저 고르고 시간을 이어서 맞출 수 있다.
- 기존 `MaterialDatePicker`, `MaterialTimePicker` 흐름은 그대로 유지한다.

### 변경 범위

- `ui`: `BookingActivity`
- `layout`: `activity_booking.xml`
- `values`: `strings.xml`

### 남은 범위

- 매니저 서류 `실제 파일 업로드`와 `증빙 이미지 미리보기`
- 관리자 검토 이력의 `타임라인`, `담당자 로그`
- 빠른 선택 버튼의 `선택 상태 강조`, `주말/야간 프리셋` 같은 추가 UX

## 9. 2026-04-15 추가 업데이트

### 구현

- 관리자 서류 검토 카드에 `서류 제출 시각`, `최근 검토 시각`, `담당자`를 함께 보여주는 타임라인을 추가했다.
- 매니저 홈 프로필 모델에 서류 제출/검토 시각과 검토 담당자 이름을 저장하도록 확장했다.
- 매니저가 서류 요약을 다시 저장하면 기존 검토 이력은 초기화되고, 관리자가 승인 또는 보완 요청을 저장하면 검토 시각과 담당자 이름이 함께 기록된다.

### 변경 범위

- `domain/model`: `ManagerHomeProfile`
- `data`: `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `ui`: `AdminActivity`
- `layout`: `item_admin_manager_document.xml`
- `values`: `strings.xml`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 매니저 서류 `실제 파일 업로드`, `증빙 이미지 미리보기`
- 관리자 검토 이력의 별도 `로그/타임라인 화면`
- 빠른 선택 버튼의 `선택 상태 강조`, `주말/야간 프리셋`

## 10. 2026-04-15 추가 업데이트

### 구현

- 동행 신청의 빠른 날짜/시간 버튼에 현재 선택 상태를 바로 보여주는 강조 스타일을 추가했다.
- 날짜가 `오늘`, `내일`, `모레` 중 하나와 일치하면 해당 버튼이 선택 상태로 유지된다.
- 시간이 `오전 10시`, `오후 2시`, `오후 4시` 중 하나와 일치하면 해당 시간 버튼도 함께 강조된다.
- 달력/시간 선택기, 빠른 선택 버튼, 수정 모드 진입/해제 모두 같은 선택 상태 계산을 재사용한다.

### 변경 범위

- `ui`: `BookingActivity`
- `docs`: `implementation-status.md`

### 남은 범위

- 매니저 서류 `실제 파일 업로드`, `증빙 이미지 미리보기`
- 관리자 검토 이력의 별도 `로그/타임라인 화면`
- 빠른 선택 버튼의 `주말/야간 프리셋`

## 11. 2026-04-15 추가 업데이트

### 구현

- 관리자 서류 카드에 `검토 이력 보기` 버튼을 추가하고, 별도 다이얼로그에서 제출/승인/보완 요청 기록을 시간순으로 확인할 수 있게 했다.
- 목업 저장소와 Firebase 저장소 모두 `managerDocumentHistory` 배열을 유지하도록 확장했다.
- 매니저가 서류 요약을 저장하면 `SUBMITTED` 이력이 쌓이고, 관리자가 승인 또는 보완 요청을 저장하면 `APPROVED`, `REJECTED` 이력이 이어서 누적된다.

### 변경 범위

- `domain/model`: `ManagerDocumentHistoryEntry`, `ManagerDocumentHistoryEventType`, `ManagerDocumentOverview`
- `data`: `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `ui`: `AdminActivity`
- `layout`: `dialog_admin_document_history.xml`, `item_admin_document_history.xml`, `item_admin_manager_document.xml`
- `values`: `strings.xml`
- `test`: `MockBodeulRepositoryTest`
- `docs`: `../architecture/data-api.md`, `implementation-status.md`

### 남은 범위

- 매니저 서류 `실제 파일 업로드`, `증빙 이미지 미리보기`
- 관리자 검토 이력의 `필터/검색`, `운영 메모 고정` 같은 2차 기능
- 빠른 선택 버튼의 `주말/야간 프리셋`

## 12. 2026-04-22 추가 업데이트

### 구현

- 기능 설명서와 피그마 캡처 기준으로 전면 개편용 목표 구조 문서 `../planning/screen-restructure-target.md`를 추가했다.
- 작업 규칙에 `액티비티/프래그먼트에는 흐름 제어만 남기고 역할별 객체로 분리하는 객체지향 원칙`을 명시했다.
- 스플래시 진입 분기를 `EntryFlowCoordinator`로 분리해 `스플래시 -> 권한 안내 -> 유형 선택 -> 로그인` 흐름을 조정할 수 있게 바꿨다.
- `PermissionGuideActivity`, `PermissionGuideCatalog`, `PermissionGuideItem`, `PermissionGuideItemBinder`, `PermissionGuidePreferences`를 추가해 권한 안내 화면과 권한 요청/저장 로직을 객체로 분리했다.
- 유형 선택 화면을 피그마 카드 구조에 맞게 다시 구성하고 `RoleOptionCardBinder`로 선택 강조 로직을 분리했다.
- 로그인 화면에서는 매니저 단독 진입 시 역할 칩을 숨겨 피그마 흐름과 겹치는 중복 선택을 줄였다.

### 변경 범위

- `AGENTS.md`
- `docs`: `implementation-status.md`, `../planning/screen-restructure-target.md`
- `manifest`: `AndroidManifest.xml`
- `ui/auth`: `SplashActivity`, `RoleSelectionActivity`, `LoginActivity`, `EntryFlowCoordinator`, `PermissionGuideActivity`, `PermissionGuideCatalog`, `PermissionGuideItem`, `PermissionGuideItemBinder`, `PermissionGuidePreferences`, `RoleOptionCardBinder`
- `layout`: `activity_permission_guide.xml`, `item_permission_guide.xml`, `activity_role_selection.xml`
- `values`: `strings.xml`

### 남은 범위

- 로그인 화면의 세부 시각 요소와 회원가입 흐름을 피그마 단계별 화면에 더 가깝게 정리
- 환자/보호자 홈, 예약 신청, 결제 흐름을 기능 설명서 구조로 재분해
- 매니저 홈을 `실시간 AI 매칭`, `주변 대기 환자`, `경력/문의` 중심 구조로 전환
- 동행 중 위치/채팅/현장 사진/약국 단계와 종료 후 후기/정산/긴급 신고 화면 추가

## 13. 2026-04-22 추가 업데이트

### 구현

- 기존 `MainActivity`의 임시 허브 구조를 제거하고 환자/보호자 전용 서비스 홈으로 교체했다.
- `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`, `ClientHomeNotice`, `ClientHomeNoticeProvider`를 추가해 홈 데이터 조합, 화면 모델, 뷰 바인딩, 안내 카드 구성을 객체로 분리했다.
- 홈 화면에 `동행 신청`, `진행 현황/보호자 리포트`, `최근 접수 내역`, `서비스 안내` 섹션을 추가해 기능 설명서의 환자/보호자 메인 홈 구조를 반영했다.
- 보호자는 진행 중 요청이 있으면 `보호자 리포트` 중심으로, 환자는 `요청 현황` 중심으로 홈 액션과 히어로 카드 문구가 바뀌도록 구성했다.
- 로그인 후 관리자 계정이 더 이상 `MainActivity`로 들어오지 않도록 `AuthFlowRouter`를 수정해 `AdminActivity`로 직접 분기하도록 정리했다.
- `assembleDebug --console=plain`으로 빌드 검증을 완료했다.

### 변경 범위

- `ui/home`: `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`, `ClientHomeNotice`, `ClientHomeNoticeProvider`
- `ui/auth`: `AuthFlowRouter`
- `ui/root`: `MainActivity`
- `layout`: `activity_main.xml`, `item_client_home_notice.xml`
- `values`: `strings.xml`
- `docs`: `implementation-status.md`

### 남은 범위

- 환자 건강 프로필 등록, 병원 검색, 희망 매니저 성별, 왕복/편도, 예상 비용, 결제/쿠폰 흐름 추가
- 매니저 홈을 기능 설명서 기준의 `실시간 매칭 On/Off`, `주변 대기 환자`, `주요 메뉴 버튼` 구조로 재설계
- 실시간 위치/안심 채팅, 현장 사진 피드, 약국 동행/복약 안내, 최종 후기/정산/SOS 화면 추가
- 관리자 운영 화면을 최종 기능 설명서 기준의 승인·운영·정산 구조로 확장
## 14. 2026-04-22 추가 업데이트

### 구현

- `BookingRequestDraft`, `BookingPriceSummary`와 예약 옵션 열거형을 추가해 예약 신청 데이터를 객체 하나로 전달하도록 바꿨다.
- `AppointmentRequest`에 건강 프로필, 이동 보조, 왕복/편도, 희망 매니저 성별, 결제 수단, 쿠폰, 비용 요약 필드를 확장했다.
- `BookingCoordinator`, `BookingDashboard`, `BookingDashboardBinder`, `BookingFormBinder`, `BookingRequestCardBinder`, `BookingAppointmentSelector`, `BookingPriceEstimator`, `BookingPresentationFormatter`를 추가해 예약 화면의 데이터 조합, 폼 검증, 날짜 선택, 요청 카드 표현을 객체로 분리했다.
- `BookingActivity`는 인증 확인, 저장소 호출, 화면 전환 같은 흐름 제어만 담당하도록 다시 구성했다.
- 예약 화면을 `환자 건강 프로필 -> 연결 정보 -> 방문 일정 -> 서비스 옵션 -> 결제 및 쿠폰 -> 비용 요약` 구조로 재구성했고, 요청 카드에도 건강 프로필과 결제 요약을 함께 보여주도록 바꿨다.
- `testDebugUnitTest`, `assembleDebug --console=plain` 검증을 마쳤다.

### 변경 범위

- `domain/model`: `AppointmentRequest`, `BookingRequestDraft`, `BookingPriceSummary`, `BookingTripType`, `BookingMobilitySupport`, `BookingManagerGenderPreference`, `BookingPaymentMethod`, `BookingCouponType`
- `data`: `BookingRepository`, `MockBodeulRepository`, `MockBookingRepository`, `FirebaseBookingRepository`
- `ui/booking`: `BookingActivity`, `BookingCoordinator`, `BookingDashboard`, `BookingDashboardBinder`, `BookingFormBinder`, `BookingRequestCardBinder`, `BookingAppointmentSelector`, `BookingPriceEstimator`, `BookingPresentationFormatter`, `BookingOptionGroupBinder`
- `layout`: `activity_booking.xml`, `item_booking_request.xml`
- `values`: `strings.xml`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 지도 기반 위치 선택과 실제 결제 승인/완료 화면 연결 (검색/매핑 정책은 `docs/architecture/data-api.md`에 정의 완료)
- 환자/보호자 예약 상세의 매칭 대기 이후 단계와 보호자 메인 진행 흐름을 더 촘촘하게 정렬
- 매니저 실시간 매칭 홈, 동행 중 위치/사진/약국 단계, 종료 후 후기/정산/SOS 화면
## 15. 2026-04-23 추가 업데이트

### 구현

- `ManagerActivity`를 인증/상태 전환/저장소 호출만 담당하는 흐름 제어기로 정리하고, 매니저 홈 렌더링은 전용 객체에 위임했다.
- `ManagerHomeCoordinator`, `ManagerHomeScreenModel`, `ManagerHomeHeroModel`, `ManagerHomeActionCardModel`, `ManagerHomePromoCardModel`, `ManagerHomeLiveFeedModel`을 추가해 대기형 홈과 진행형 홈을 같은 화면 모델 체계로 분리했다.
- `ManagerHomeDashboardBinder`, `ManagerHomeActionCardBinder`, `ManagerHomePromoCardBinder`를 추가해 히어로 카드, 빠른 액션, 소개 카드, 진행 중 동행 카드를 객체 기준으로 바인딩하도록 바꿨다.
- 매니저 홈 레이아웃을 최신 피그마 기준으로 재구성해 `실시간 AI 매칭 대기`, `서류 등록`, `스케줄 등록`, `과거 경력`, `문의하기`, `오늘 연결된 동행 일정` 구조를 반영했다.
- 진행 중 동행이 있으면 히어로 버튼이 바로 가이드를 열고, 대기 상태에서는 매칭 요청을 다시 확인하는 흐름으로 분기되도록 정리했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `ui/manager`: `ManagerActivity`, `ManagerHomeCoordinator`, `ManagerHomeDashboardBinder`, `ManagerHomeActionCardBinder`, `ManagerHomePromoCardBinder`, `ManagerHomeScreenModel`, `ManagerHomeHeroModel`, `ManagerHomeActionCardModel`, `ManagerHomePromoCardModel`, `ManagerHomeLiveFeedModel`, `ManagerHomePresentationFormatter`, `ManagerHomeActionType`
- `layout`: `activity_manager_home.xml`, `item_manager_home_action_card.xml`, `item_manager_home_promo_card.xml`
- `values`: `strings.xml`

### 남은 범위

- 병원 검색, 지도 기반 위치 선택, 실제 결제 완료 화면과 예약 상세/매칭 대기 흐름 연결
- 동행 중 실시간 위치, 현장 사진, 약국/복약 단계, 종료 후 후기/정산/SOS 화면 개편
- 과거 경력, 문의하기, 내 페이지, 가이드 외 탭을 실제 화면과 데이터 흐름으로 연결
- 관리자 운영 화면을 최종 기능 설명서 기준의 배정/정산/모니터링 구조로 확장

## 16. 2026-04-23 추가 업데이트

### 구현

- `CompanionSession`에 `locationSummary`, `fieldPhotoNote` 필드를 추가해 위치 공유와 현장 사진/서류 메모를 세션 데이터로 별도 관리하도록 확장했다.
- `ManagerRepository`와 목업/Firebase 저장소에 위치 메모, 현장 메모 저장 메서드를 추가해 매니저 진행 화면의 입력이 실제 세션 데이터로 반영되도록 정리했다.
- `ManagerGuideCoordinator`, `ManagerGuideScreenModel`, `ManagerGuideFocusModel`, `ManagerGuideStageModel`, `ManagerGuideDashboardBinder`, `ManagerGuideStageItemBinder`를 추가해 동행 진행 화면의 단계 계산, 포커스 카드 조합, 레일 렌더링을 액티비티 밖으로 분리했다.
- `ManagerGuideActivity`는 인증 확인, 저장소 호출, 저장 액션만 담당하도록 재작성했고, 레이아웃도 최신 피그마 기준의 `상단 동행 요약 -> 단계 레일 + 현재 단계 포커스 -> 위치 공유/보호자 공유 -> 현장 사진/복약 -> 최종 리포트` 구조로 교체했다.
- 환자/보호자 예약 상세도 같은 세션 데이터를 보도록 `BookingStatusCoordinator`에 위치 메모와 현장 사진 메모 라인을 추가했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `domain/model`: `CompanionSession`
- `data`: `ManagerRepository`, `MockBodeulRepository`, `MockManagerRepository`, `FirebaseManagerRepository`, `FirebaseAdminRepository`, `FirebaseBookingRepository`, `FirebaseGuardianReportRepository`
- `ui/manager`: `ManagerGuideActivity`, `ManagerGuideCoordinator`, `ManagerGuideDashboardBinder`, `ManagerGuideStageItemBinder`, `ManagerGuideScreenModel`, `ManagerGuideFocusModel`, `ManagerGuideStageModel`, `ManagerGuideStageState`, `ManagerGuidePresentationFormatter`
- `ui/booking`: `BookingStatusCoordinator`
- `layout`: `activity_manager_guide.xml`, `item_manager_guide_stage.xml`
- `values`: `strings.xml`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 병원 검색, 지도 기반 위치 선택, 실제 결제 완료 화면 연결
- 환자/보호자 예약 상세의 매칭 대기 이후 단계와 보호자 리포트 화면을 최신 피그마 흐름으로 정렬
- 동행 종료 후 후기/정산/SOS, 매니저 과거 경력/문의/내 페이지, 관리자 운영 화면 개편

## 17. 2026-04-23 추가 업데이트

### 구현

- `GuardianReportCoordinator`, `GuardianReportScreenModel`, `GuardianReportHighlightModel`, `GuardianReportEntryCardModel`, `GuardianReportLineItem`, `GuardianReportDashboardBinder`, `GuardianReportEntryCardBinder`, `GuardianReportPresentationFormatter`를 추가해 보호자 진행 화면의 문자열 조합과 카드 렌더링을 액티비티 밖으로 분리했다.
- `GuardianReportActivity`는 인증 확인, 저장소 호출, 로딩/에러 상태, 예약 상세 화면 이동만 담당하도록 재작성했다.
- 보호자 화면 레이아웃을 최신 구조에 맞춰 `상단 계정 요약 -> 대표 진행 현황 -> 요청별 진행 카드`로 재구성했고, 각 카드에서 `진행 상태`, `위치 공유 메모`, `현장 사진 메모`, `현장 복약 메모`, `최종 리포트`를 함께 확인할 수 있게 바꿨다.
- 보호자 카드와 대표 진행 현황 버튼에서 바로 `BookingStatusActivity`로 이동하도록 연결해 진행 화면과 예약 상세 흐름이 자연스럽게 이어지도록 정리했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `ui/report`: `GuardianReportActivity`, `GuardianReportCoordinator`, `GuardianReportDashboardBinder`, `GuardianReportEntryCardBinder`, `GuardianReportScreenModel`, `GuardianReportHighlightModel`, `GuardianReportEntryCardModel`, `GuardianReportLineItem`, `GuardianReportPresentationFormatter`
- `layout`: `activity_guardian_report.xml`, `item_guardian_report.xml`, `item_guardian_report_line.xml`
- `values`: `strings.xml`

### 남은 범위

- 병원 검색, 지도 기반 위치 선택, 실제 결제 완료 화면 연결
- 환자/보호자 예약 상세의 매칭 대기 이후 단계와 보호자 메인 진행 흐름을 더 촘촘하게 정렬
- 동행 종료 후 후기/정산/SOS, 매니저 과거 경력/문의/내 페이지, 관리자 운영 화면 개편

## 18. 2026-04-23 추가 업데이트

### 구현

- `BookingRepository`에 병원 선택용 조회 메서드를 추가하고, 목업/Firebase 저장소 모두 `hospitalGuides`를 병원별 후보 목록으로 묶어 반환하도록 확장했다.
- `BookingHospitalSelectorActivity`, `BookingHospitalSelectorCoordinator`, `BookingHospitalCatalog`, `BookingHospitalOptionAdapter`를 추가해 병원/진료과 검색과 선택을 예약 폼 밖의 별도 흐름으로 분리했다.
- 예약 폼은 병원/진료과를 직접 입력하지 않고 선택 결과만 반영하도록 바꿨고, 선택 직후 만남 장소가 비어 있으면 기본 만남 위치 문구를 자동 제안하도록 정리했다.
- `BookingCompletionActivity`, `BookingCompletionCoordinator`, `BookingCompletionBinder`, `BookingCompletionSnapshot`, `BookingCompletionScreenModel`을 추가해 접수/수정 후 토스트 대신 완료 요약 화면으로 이어지도록 재구성했다.
- `BookingActivity`는 제출 성공 후 폼 초기화와 대시보드 갱신만 담당하고, 병원 선택과 완료 화면 이동은 전용 객체와 액티비티로 위임하도록 정리했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `data`: `BookingRepository`, `MockBookingRepository`, `FirebaseBookingRepository`
- `domain/model`: `BookingHospitalOption`, `BookingHospitalSelection`
- `ui/booking`: `BookingActivity`, `BookingFormBinder`, `BookingHospitalSelectorActivity`, `BookingHospitalSelectorCoordinator`, `BookingHospitalCatalog`, `BookingHospitalOptionAdapter`, `BookingCompletionActivity`, `BookingCompletionCoordinator`, `BookingCompletionBinder`, `BookingCompletionSnapshot`, `BookingCompletionScreenModel`
- `layout`: `activity_booking.xml`, `activity_booking_hospital_selector.xml`, `item_booking_hospital_option.xml`, `activity_booking_completion.xml`
- `values`: `strings.xml`
- `manifest`: `AndroidManifest.xml`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 지도 기반 위치 선택과 실제 결제 승인/완료 단계를 예약 흐름에 연결
- 환자/보호자 예약 상세의 매칭 대기 이후 단계와 보호자 메인 진행 흐름을 더 촘촘하게 정렬
- 동행 종료 후 후기/정산/SOS, 매니저 과거 경력/문의/내 페이지, 관리자 운영 화면 개편

## 19. 2026-04-23 추가 업데이트

### 구현

- `BookingLocationSelectorActivity`, `BookingLocationSelectorCoordinator`, `BookingLocationMapView`, `BookingLocationOptionAdapter`를 추가해 만남 장소를 자유 입력이 아니라 지도 기반 선택 흐름으로 분리했다.
- 위치 선택은 병원/진료과 선택 결과를 기준으로 `정문 안내 데스크`, `외래 대기 라운지`, `주차장 승하차 구역`, `약국 연결 지점` 후보를 객체로 조합하고, 예약 폼에는 선택 결과만 반영하도록 정리했다.
- `BookingPaymentApprovalActivity`, `BookingPaymentApprovalCoordinator`, `BookingPaymentApprovalBinder`, `BookingPaymentCheckoutSnapshot`을 추가해 예약 제출 직전에 결제 승인 또는 현장 결제 확정 단계를 별도 화면으로 분리했다.
- `BookingRequestDraft`와 `AppointmentRequest`에 결제 승인 상태, 승인 번호, 승인 시각, 승인 수단 스냅샷을 추가했고, 목업/Firebase 저장소 모두 같은 필드를 읽고 쓰도록 확장했다.
- 예약 상세와 완료 화면에도 결제 승인 상태가 보이도록 `BookingStatusCoordinator`, `BookingCompletionSnapshot`, `BookingCompletionCoordinator`, `BookingPresentationFormatter`를 함께 갱신했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `domain/model`: `BookingMeetingLocationSelection`, `BookingMeetingPointOption`, `BookingPaymentApproval`, `BookingPaymentStatus`, `BookingRequestDraft`, `AppointmentRequest`
- `data`: `MockBodeulRepository`, `FirebaseBookingRepository`, `FirebaseManagerRepository`, `FirebaseGuardianReportRepository`, `FirebaseAdminRepository`
- `ui/booking`: `BookingActivity`, `BookingFormBinder`, `BookingLocationSelectorActivity`, `BookingLocationSelectorCoordinator`, `BookingLocationSelectorScreenModel`, `BookingLocationMapView`, `BookingLocationOptionAdapter`, `BookingPaymentApprovalActivity`, `BookingPaymentApprovalCoordinator`, `BookingPaymentApprovalBinder`, `BookingPaymentCheckoutSnapshot`, `BookingPaymentApprovalScreenModel`, `BookingStatusCoordinator`, `BookingCompletionSnapshot`, `BookingCompletionCoordinator`, `BookingPresentationFormatter`
- `layout`: `activity_booking.xml`, `activity_booking_location_selector.xml`, `item_booking_location_option.xml`, `activity_booking_payment_approval.xml`
- `values`: `strings.xml`
- `manifest`: `AndroidManifest.xml`

### 남은 범위

- 환자/보호자 예약 상세의 매칭 대기 이후 단계와 보호자 메인 진행 흐름을 더 촘촘하게 정렬
- 동행 종료 후 후기/정산/SOS, 매니저 과거 경력/문의/내 페이지, 관리자 운영 화면 개편
- 관리자 운영 화면을 최종 기능 설명서 기준의 배정/정산/모니터링 구조로 확장
## 20. 2026-04-23 추가 업데이트

### 구현

- `AppointmentProgressComposer`, `AppointmentProgressOverviewModel`, `AppointmentProgressStageModel`, `AppointmentProgressStageItemBinder`를 추가해 예약 진행 상태를 공통 로드맵 객체로 해석하도록 정리했다.
- `BookingStatusCoordinator`, `BookingStatusScreenModel`, `BookingStatusBinder`, `activity_booking_status.xml`을 갱신해 예약 상세를 `현재 상태 요약 -> 진행 단계 -> 다음 안내 -> 참여자/예약 요약 -> 리포트` 구조로 확장했다.
- `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`, `MainActivity`, `activity_main.xml`을 갱신해 환자·보호자 메인 홈에서도 대표 요청의 진행 로드맵을 같은 기준으로 보여주도록 맞췄다.
- 보호자 홈은 `GuardianReportEntry`의 실시간 세션/리포트 데이터를 우선 사용하고, 환자 홈은 요청 상태만으로도 동일한 단계 해석을 유지하도록 정리했다.
- `assembleDebug --console=plain` 검증을 완료했다.

### 변경 범위

- `ui/common`: `AppointmentProgressComposer`, `AppointmentProgressOverviewModel`, `AppointmentProgressStageModel`, `AppointmentProgressStageState`, `AppointmentProgressStageItemBinder`
- `ui/booking`: `BookingStatusActivity`, `BookingStatusCoordinator`, `BookingStatusBinder`, `BookingStatusScreenModel`
- `ui/home`: `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`
- `app`: `MainActivity`
- `layout`: `activity_booking_status.xml`, `activity_main.xml`, `item_appointment_progress_stage.xml`
- `values`: `strings.xml`

### 남은 범위

- 종료 후 후기/정산/SOS 화면과 예약 완료 이후 후속 흐름 정리
- 매니저 `과거 경력`, `문의하기`, `내 페이지`를 실제 기능 화면과 데이터 흐름으로 연결
- 관리자 운영 화면을 최종 기능 설명서 기준의 배정/정산/모니터링 구조로 확장
## 21. 2026-04-23 추가 업데이트

### 구현

- `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpBinder`, `BookingFollowUpScreenModel`을 추가해 완료된 동행 기준의 `후기 -> 정산 확인 -> SOS 안내` 후속 흐름을 별도 화면으로 분리했다.
- 후기 선택은 `BookingFollowUpRating`, `BookingFollowUpPreferences`, `BookingFollowUpSavedReview`로 분리하고, 예약별 만족도와 저장 시각을 기기 로컬 저장소에 유지하도록 정리했다.
- 완료된 예약 상세의 기본 액션을 `후기·정산·SOS 보기`로 바꾸고, 보호자 계정은 보조 액션으로 기존 보호자 리포트도 계속 열 수 있게 유지했다.
- SOS 영역에는 담당 매니저 다이얼 연결, `119` 다이얼 연결, 긴급 안내 다이얼로그를 추가해 완료 후 화면에서도 바로 대응 흐름을 확인할 수 있게 했다.
- `assembleDebug --console=plain` 검증을 완료했다.

### 변경 범위

- `ui/booking`: `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpBinder`, `BookingFollowUpScreenModel`, `BookingFollowUpRating`, `BookingFollowUpRatingOptionModel`, `BookingFollowUpRatingOptionBinder`, `BookingFollowUpPreferences`, `BookingFollowUpSavedReview`, `BookingStatusActivity`, `BookingStatusCoordinator`, `BookingStatusActionType`
- `layout`: `activity_booking_follow_up.xml`, `item_booking_follow_up_rating.xml`
- `values`: `strings.xml`
- `manifest`: `AndroidManifest.xml`

### 남은 범위

- 매니저 `과거 경력`, `문의하기`, `내 페이지`를 실제 기능 화면과 데이터 흐름으로 연결
- 관리자 운영 화면을 최종 기능 설명서 기준의 배정/정산/모니터링 구조로 확장
- 후기/정산/SOS의 서버 저장 API와 관리자 후속 처리 흐름을 백엔드 모델에 연결
## 22. 2026-04-23 매니저 과거 이력 / 문의하기 / 내 페이지 반영

### 구현

- `ManagerRepository`에 `getManagerDocumentOverview`, `getManagerHistoryDetails`를 추가해 매니저 홈 외의 실제 상세 화면도 같은 저장소 계약으로 연결했다.
- `ManagerHistoryActivity`, `ManagerHistoryCoordinator`, `ManagerHistoryBinder`와 관련 카드 모델을 추가해 완료된 동행 세션과 최종 리포트를 `과거 동행 이력` 화면으로 분리했다.
- `ManagerProfileActivity`, `ManagerProfileCoordinator`, `ManagerProfileBinder`와 서류 이력 카드 바인더를 추가해 계정 정보, 서류 상태, 검토 메모, 활동 가능 일정, 서류 검토 이력을 `내 페이지` 화면으로 구성했다.
- `ManagerSupportActivity`, `ManagerSupportCoordinator`, `ManagerSupportBinder`, `ManagerSupportPreferences`를 추가해 `문의하기` 화면에서 로컬 저장 기반 문의 접수와 최근 문의 내역 확인이 가능하도록 정리했다.
- `ManagerQuickNoteDialogController`, `ManagerQuickNoteType`으로 서류 요약 / 일정 요약 수정 대화상자를 공용 객체로 분리했고, 매니저 홈 빠른 액션과 새 `내 페이지` 화면이 같은 편집 흐름을 재사용하도록 맞췄다.
- `ManagerActivity`, `activity_manager_home.xml`, `AndroidManifest.xml`, `strings.xml`을 갱신해 빠른 액션과 하단 네비게이션이 `과거 이력`, `가이드`, `내 페이지`, `문의하기` 실화면으로 연결되게 정리했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `data`: `ManagerRepository`
- `data/mock`: `MockManagerRepository`
- `data/firebase`: `FirebaseManagerRepository`
- `ui/manager`: `ManagerActivity`, `ManagerQuickNoteDialogController`, `ManagerQuickNoteType`, `ManagerInfoLineItem`, `ManagerDocumentHistoryItemModel`, `ManagerDocumentHistoryItemBinder`, `ManagerProfileActivity`, `ManagerProfileCoordinator`, `ManagerProfileBinder`, `ManagerProfileScreenModel`, `ManagerHistoryActivity`, `ManagerHistoryCoordinator`, `ManagerHistoryBinder`, `ManagerHistoryEntryCardBinder`, `ManagerHistoryEntryCardModel`, `ManagerHistoryScreenModel`, `ManagerSupportActivity`, `ManagerSupportCoordinator`, `ManagerSupportBinder`, `ManagerSupportPreferences`, `ManagerSupportCategory`, `ManagerSupportInquiry`, `ManagerSupportInquiryStatus`, `ManagerSupportInquiryCardModel`, `ManagerSupportInquiryCardBinder`, `ManagerSupportScreenModel`
- `layout`: `activity_manager_home.xml`, `activity_manager_profile.xml`, `activity_manager_history.xml`, `activity_manager_support.xml`, `item_manager_info_line.xml`, `item_manager_document_history.xml`, `item_manager_history_entry.xml`, `item_manager_support_inquiry.xml`
- `manifest`: `AndroidManifest.xml`
- `values`: `strings.xml`

### 남은 범위

- 관리자 운영 화면을 최종 기능 설명서 기준의 배정 / 정산 / 모니터링 구조로 확장
- `문의하기`의 서버 저장 API와 관리자 응답 흐름 연결
- 매니저 과거 이력에 실서비스 기준 필터 / 정산 내역 / 평가 데이터 추가
## 23. 2026-04-23 관리자 운영 화면 모니터링/정산 확장

### 구현

- `AdminRequestOverview`에 `sessionReport`를 추가하고, `MockAdminRepository`, `FirebaseAdminRepository`가 요청별 세션 리포트까지 함께 조합하도록 확장했다.
- `ui/admin` 아래에 `AdminOperationsPresentationFormatter`, `AdminOperationsCoordinator`, `AdminOperationCardBinder`와 운영 카드 모델들을 추가해 `실시간 운영 모니터링`, `정산 확인` 섹션을 액티비티 로직에서 분리했다.
- `activity_admin.xml`에 운영/정산 요약과 카드 컨테이너를 추가하고, `AdminActivity`는 새 대시보드 모델을 받아 섹션 렌더링만 담당하도록 연결했다.
- 모니터링 카드는 `담당 매니저`, `현재 단계`, `보호자 공유`, `위치 공유`, `현장 사진`, `복약 메모`를 한 번에 보여주고, 정산 카드는 `최종 금액`, `결제 수단`, `승인 상태`, `승인 번호`, `승인 시각`, `다음 방문`, `최종 리포트`를 묶어 보여준다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `domain/model`: `AdminRequestOverview`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActivity`, `AdminOperationLineItem`, `AdminOperationCardModel`, `AdminOperationsDashboardModel`, `AdminOperationsPresentationFormatter`, `AdminOperationsCoordinator`, `AdminOperationCardBinder`
- `layout`: `activity_admin.xml`, `item_admin_operation_card.xml`, `item_admin_operation_line.xml`
- `values`: `strings.xml`

### 남은 범위

- 관리자 운영 화면의 배정/가이드/관리중 요청을 같은 수준의 객체들로 추가 분리
- 관리자 화면에서 정산 후속 처리, 문의 응답, 긴급 이슈 대응까지 실제 서버 액션 연결
- 매니저 문의하기, 후기/정산/SOS의 서버 저장 API 연결
## 24. 2026-04-23 관리자 요청 카드 객체 분리

### 구현

- `ui/admin` 아래에 `AdminManagedRequestFilter`, `AdminManagedRequestDateFilter`, `AdminRequestPresentationFormatter`, `AdminRequestCoordinator`, `AdminRequestCardBinder`와 요청 카드/필터 모델들을 추가했다.
- `AdminActivity`의 `배정 대기`와 `관리 중 요청` 영역은 이제 액티비티 안에서 문자열과 상세 패널을 직접 조합하지 않고, 코디네이터가 만든 카드 모델을 받아 렌더링만 하도록 바꿨다.
- `관리 중 요청`의 상태 필터, 날짜 필터, 요약 집계도 `AdminManagedRequestSectionModel` 기준으로 분리해서 액티비티 내부 조건문을 줄였다.
- 요청 카드의 상태 배지, 참여자 표시, 상세 운영 패널, 배정 버튼 목록은 `AdminRequestCardModel`과 `AdminRequestCardBinder`를 통해 일관되게 처리한다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `ui/admin`: `AdminActivity`, `AdminManagedRequestFilter`, `AdminManagedRequestDateFilter`, `AdminManagedFilterChipModel`, `AdminManagedDateFilterChipModel`, `AdminRequestAssignActionModel`, `AdminRequestCardModel`, `AdminManagedRequestSectionModel`, `AdminRequestPresentationFormatter`, `AdminRequestCoordinator`, `AdminRequestCardBinder`

### 남은 범위

- 관리자 화면의 병원 가이드 목록/편집 영역도 같은 수준의 바인더와 코디네이터로 분리
- 관리자 화면의 정산 후속 처리, 문의 응답, 긴급 이슈 대응 액션의 실제 서버 연결
- 매니저 문의하기와 후기/정산/SOS의 서버 저장 API 연결
## 25. 2026-04-23 의존성 버전 중앙관리 + 관리자 가이드 영역 분리

### 구현

- 루트/앱 빌드 스크립트의 하드코딩된 플러그인과 라이브러리 버전을 [gradle/libs.versions.toml](../../gradle/libs.versions.toml) 기준의 version catalog로 옮겼다.
- [build.gradle.kts](../../build.gradle.kts), [app/build.gradle.kts](../../app/build.gradle.kts)는 catalog alias를 사용하도록 바꿨고, 실제 버전 값은 유지했다.
- 관리자 병원 가이드 영역은 `AdminGuideCoordinator`, `AdminGuideCardBinder`, `AdminGuideFormBinder`와 가이드 카드/폼 모델들로 분리했다.
- [AdminActivity.java](../../app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java)는 이제 병원 가이드 목록 카드와 폼 모드 문자열을 직접 조합하지 않고, 가이드 코디네이터와 바인더를 통해 렌더링한다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `gradle`: `libs.versions.toml`
- `build`: `build.gradle.kts`, `app/build.gradle.kts`
- `ui/admin`: `AdminActivity`, `AdminGuideCardModel`, `AdminGuideFormModel`, `AdminGuidePresentationFormatter`, `AdminGuideCoordinator`, `AdminGuideCardBinder`, `AdminGuideFormBinder`

### 남은 범위

- 관리자 매니저 서류 검토 영역도 같은 수준의 코디네이터/바인더 구조로 분리
- 관리자 정산 후속 처리, 문의 응답, 긴급 이슈 대응 액션의 실제 서버 연결
- 매니저 문의하기와 후기/정산/SOS의 서버 저장 API 연결
## 26. 2026-04-23 관리자 매니저 서류 검토 영역 분리

### 구현

- `ui/admin` 아래에 `AdminManagerDocumentCardModel`, `AdminManagerDocumentHistoryItemModel`, `AdminManagerDocumentPresentationFormatter`, `AdminManagerDocumentCoordinator`, `AdminManagerDocumentCardBinder`, `AdminManagerDocumentHistoryItemBinder`를 추가해서 서류 검토 카드와 검토 이력 렌더링을 객체로 분리했다.
- `AdminActivity`는 이제 서류 상태 문구, 배지 색상, 타임라인 문구, 이력 본문을 직접 조합하지 않고, 코디네이터가 만든 모델을 받아 카드 렌더링과 클릭 처리만 맡는다.
- `검토 이력 보기` 다이얼로그도 동일한 이력 모델과 바인더를 사용하도록 바꿔서 카드 화면과 이력 다이얼로그가 같은 표현 규칙을 공유하게 정리했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `ui/admin`: `AdminActivity`, `AdminManagerDocumentCardModel`, `AdminManagerDocumentHistoryItemModel`, `AdminManagerDocumentPresentationFormatter`, `AdminManagerDocumentCoordinator`, `AdminManagerDocumentCardBinder`, `AdminManagerDocumentHistoryItemBinder`

### 남은 범위

- 관리자 서류 검토의 `승인/보완 요청` 후속 액션을 서버 감사 로그나 알림 전송과 연결
- 관리자 정산 후속 처리, 문의 응답, 긴급 이슈 대응 액션의 실제 서버 연동
- 매니저 문의하기와 후기/정산/SOS 저장 API 연결
## 27. 2026-04-23 관리자 후속 처리 / 문의 저장 흐름 연결

### 구현

- `AdminRepository`, `ManagerRepository` 계약에 관리자 정산 후속 처리, 긴급 이슈 처리, 매니저 문의 응답/조회 메서드를 추가하고 목업/Firebase 구현까지 연결했다.
- 관리자 화면은 `AdminOperationsCoordinator`, `AdminSupportCoordinator` 기준으로 `정산 후속 처리`, `긴급 이슈 대응`, `문의 응답` 버튼을 실제 저장 흐름과 연결했다.
- 매니저 문의하기는 `ManagerSupportActivity`, `ManagerSupportCoordinator` 기준으로 로컬 임시 저장 대신 저장소 기반 조회/등록 구조로 전환했다.
- 더 이상 사용하지 않는 `ManagerSupportPreferences`, 구형 문의 전용 로컬 모델들을 제거해 문의 도메인을 `SupportInquiry`로 일원화했다.
- `MockBodeulRepositoryTest`에 문의 등록/답변, 관리자 액션 레코드 생성 검증을 추가했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `data`: `AdminRepository`, `ManagerRepository`, `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`, `MockManagerRepository`
- `data/firebase`: `FirebaseAdminRepository`, `FirebaseManagerRepository`
- `domain/model`: `AdminSettlementRecord`, `AdminEmergencyIssueRecord`, `AdminRequestActionOverview`, `SupportInquiry` 관련 모델
- `ui/admin`: `AdminActivity`, `AdminOperationsCoordinator`, `AdminOperationsPresentationFormatter`, `AdminSupportCoordinator`, `AdminSupportInquiryPresentationFormatter`, `AdminOperationCardBinder`, `AdminSupportInquiryCardBinder`
- `ui/manager`: `ManagerSupportActivity`, `ManagerSupportCoordinator`, `ManagerSupportBinder`, `ManagerSupportInquiryCardBinder`
- `values/layout/test`: `strings_admin_manager_extension.xml`, `activity_manager_support.xml`, `item_admin_operation_card.xml`, `item_admin_support_inquiry.xml`, `item_manager_support_inquiry.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- 후기 / 정산 / SOS 후속 데이터를 실제 서버 API와 완전히 동기화
- 관리자 화면의 후속 처리 액션에 알림, 감사 로그, 운영 이력 계층 추가
- 매니저 과거 이력에 평가 / 정산 필터와 실데이터 기준 집계 추가
## 28. 2026-04-23 예약 후속 후기 저장소 연동

### 구현

- `AppointmentFollowUpReviewRating`, `AppointmentFollowUpRecord`를 추가해 완료된 예약의 후기 값을 UI 로컬 객체가 아닌 도메인 모델로 분리했다.
- `BookingRepository`에 `getAppointmentFollowUp`, `saveAppointmentFollowUpReview` 계약을 추가하고, `MockBookingRepository`, `FirebaseBookingRepository`가 같은 방식으로 후기 조회/저장을 처리하도록 연결했다.
- `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpRatingOptionModel`, `BookingFollowUpRatingOptionBinder`는 더 이상 `BookingFollowUpPreferences`를 사용하지 않고 저장소에서 받은 후속 레코드를 기준으로 화면을 렌더링한다.
- 기존 로컬 임시 저장 전용 클래스인 `BookingFollowUpPreferences`, `BookingFollowUpSavedReview`, `BookingFollowUpRating`은 제거했다.
- `MockBodeulRepositoryTest`에 완료 예약 기준 후기 조회/저장 테스트를 추가했다.
- `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 완료했다.

### 변경 범위

- `domain/model`: `AppointmentFollowUpReviewRating`, `AppointmentFollowUpRecord`
- `data`: `BookingRepository`, `MockBodeulRepository`
- `data/mock`: `MockBookingRepository`
- `data/firebase`: `FirebaseBookingRepository`
- `ui/booking`: `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpRatingOptionModel`, `BookingFollowUpRatingOptionBinder`
- `values/test`: `strings_booking_follow_up_extension.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- SOS 접수와 정산 후속 확인도 같은 예약 후속 저장 모델로 확장
- 완료 예약 상세와 홈 요약에 저장된 후기 상태를 함께 노출
- 후기 / SOS 후속 데이터의 관리자 운영 화면 반영
## 29. 2026-04-23 예약 후속 정산/SOS 저장 확장

### 구현

- `AppointmentFollowUpRecord`를 `후기`, `정산 확인`, `SOS 기록`을 함께 담는 구조로 확장하고, `AppointmentFollowUpSettlementStatus`, `AppointmentFollowUpSupportEscalationStatus` 도메인 값을 추가했다.
- `BookingRepository`에 `saveAppointmentFollowUpSettlement`, `saveAppointmentFollowUpSupportEscalation` 계약을 추가하고, `MockBookingRepository`, `FirebaseBookingRepository`가 같은 문서에 병합 저장하도록 맞췄다.
- `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpBinder`, `BookingFollowUpScreenModel`, `activity_booking_follow_up.xml`을 갱신해 `정산 확인 저장`, `정산 문의 기록`, `SOS 기록 상태`를 실제 저장 흐름으로 연결했다.
- 완료된 예약 상세는 `BookingStatusActivity`, `BookingStatusCoordinator`에서 후속 레코드를 함께 읽어 `후기`, `정산 확인`, `SOS 기록`을 리포트 카드에 노출하도록 변경했다.
- 환자/보호자 홈은 `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder` 기준으로 완료 예약의 후속 상태를 최근 요청/히어로 요약에 함께 보이도록 정리했다.
- 관리자 운영 화면은 `AdminRequestActionOverview`, `AdminOperationsCoordinator`, `AdminOperationsPresentationFormatter`, `FirebaseAdminRepository`, `MockBodeulRepository`를 통해 완료 요청의 사용자 후속 상태를 `정산 확인` 카드에 함께 표기하도록 확장했다.
- `MockBodeulRepositoryTest`에 정산/SOS 저장 보존 테스트와 관리자 액션 오버뷰 후속 노출 테스트를 추가했고, `assembleDebug --console=plain`, `testDebugUnitTest --console=plain` 검증을 마쳤다.

### 변경 범위

- `domain/model`: `AppointmentFollowUpRecord`, `AppointmentFollowUpSettlementStatus`, `AppointmentFollowUpSupportEscalationStatus`, `AdminRequestActionOverview`
- `data`: `BookingRepository`, `MockBodeulRepository`
- `data/mock`: `MockBookingRepository`
- `data/firebase`: `FirebaseBookingRepository`, `FirebaseAdminRepository`
- `ui/booking`: `BookingFollowUpActivity`, `BookingFollowUpCoordinator`, `BookingFollowUpBinder`, `BookingFollowUpScreenModel`, `BookingStatusActivity`, `BookingStatusCoordinator`, `BookingPresentationFormatter`
- `ui/home`: `ClientHomeCoordinator`, `ClientHomeDashboard`, `ClientHomeDashboardBinder`
- `ui/admin`: `AdminOperationsCoordinator`, `AdminOperationsPresentationFormatter`
- `values/layout/test`: `activity_booking_follow_up.xml`, `strings_follow_up_status_extension.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- 예약 후속 데이터의 실제 서버 API 명세를 `review`, `settlement`, `support escalation` 단위로 분리해 백엔드 계약까지 확정
- 관리자 운영 화면에서 사용자 후속 상태를 기준으로 필터/우선순위 정렬 추가
- 후기/정산/SOS 후속 데이터에 대한 관리자 알림, 감사 로그, 운영 이력 계층 연결

## 30. 2026-04-23 관리자 운영 후속 우선순위/필터 정리

### 구현

- `AdminOperationsCoordinator`는 이제 `AdminMonitoringFilter`, `AdminSettlementFilter`를 받아 운영 대상과 정산 대상을 각각 필터링하고, 후속 상태 기준 우선순위 정렬을 먼저 적용한다.
- `AdminOperationsDashboardModel`은 `요약 -> 경고 문구 -> 필터 칩 -> 카드 목록` 구조로 확장했고, `AdminOperationCardModel`, `AdminOperationBadgeModel`, `AdminOperationBadgeTone`으로 상태 배지와 우선 확인 배지를 분리했다.
- `AdminActivity`, `activity_admin.xml`, `AdminOperationCardBinder`, `item_admin_operation_card.xml`은 실시간 운영/정산 섹션에 경고 요약, 필터 칩, 최근 처리 문구를 추가해 `긴급 대응`, `사용자 문의`, `관리자 미처리` 건을 바로 좁혀 볼 수 있게 바꿨다.
- `AdminOperationsPresentationFormatter`는 필터 라벨, 경고 요약, 최근 기록 문구를 전담하고, `strings_admin_operation_extension.xml`로 운영 확장 문구를 분리했다.
- 검증은 `assembleDebug --console=plain`, `testDebugUnitTest --console=plain`로 다시 통과했다.

### 변경 범위

- `ui/admin`: `AdminActivity`, `AdminOperationsCoordinator`, `AdminOperationsDashboardModel`, `AdminOperationsPresentationFormatter`, `AdminOperationCardModel`, `AdminOperationCardBinder`, `AdminMonitoringFilter`, `AdminSettlementFilter` 및 관련 칩/배지 모델
- `layout/values`: `activity_admin.xml`, `item_admin_operation_card.xml`, `strings_admin_operation_extension.xml`, `strings_admin_manager_extension.xml`

### 남은 범위

- 관리자 후속 처리 액션의 알림 전송, 감사 로그, 운영 이력 저장 계층 연결
- 후속 상태 기반 필터 규칙을 서버 쿼리/API 계약까지 끌어올려 데이터량이 커져도 같은 정렬 규칙을 유지
- 매니저 과거 이력 화면에 후기/정산 결과와 필터를 연결해 완료 이후 이력까지 같은 축으로 정리

## 31. 2026-04-23 매니저 과거 이력 후속 상태/필터 반영

### 구현

- `AppointmentRequestDetail`에 `followUpRecord`를 포함시키고, `MockBodeulRepository`, `MockManagerRepository`, `FirebaseManagerRepository`가 완료된 요청의 후기/정산/SOS 후속 기록을 함께 조합하도록 확장했다.
- `ManagerHistoryCoordinator`, `ManagerHistoryScreenModel`, `ManagerHistoryEntryCardModel`, `ManagerHistoryBinder`, `ManagerHistoryEntryCardBinder`를 기준으로 과거 이력 화면을 `요약 -> 필터 칩 -> 완료 카드 목록` 구조로 재정리했다.
- 완료 카드에는 `동행 완료` 배지 외에 `후속 미기록`, `후기 저장`, `정산 확인/문의`, `SOS 기록` 후속 배지를 함께 노출하고, 최근 후속 기록 시각과 상세 라인도 같이 보여주도록 구성했다.
- `strings_manager_history_extension.xml`로 관련 문구를 분리했고, `MockBodeulRepositoryTest`에 매니저 이력 조회 시 후속 기록이 포함되는 테스트를 추가했다.
- 검증은 `assembleDebug --console=plain`, `testDebugUnitTest --console=plain`로 다시 통과했다.

### 변경 범위

- `domain/model`: `AppointmentRequestDetail`
- `data`: `MockBodeulRepository`
- `data/mock`: `MockManagerRepository`
- `data/firebase`: `FirebaseManagerRepository`
- `ui/manager`: `ManagerHistoryActivity`, `ManagerHistoryCoordinator`, `ManagerHistoryBinder`, `ManagerHistoryEntryCardBinder`, `ManagerHistoryScreenModel`, `ManagerHistoryEntryCardModel`, `ManagerHistoryFilter`, `ManagerHistoryFilterChipModel`, `ManagerHistoryBadgeModel`, `ManagerHistoryBadgeTone`
- `layout/values/test`: `activity_manager_history.xml`, `item_manager_history_entry.xml`, `strings_manager_history_extension.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- 관리자 후속 처리 액션의 알림/감사 로그/운영 이력 저장 계층 연결
- 후속 상태 기반 우선순위와 필터 규칙을 서버 응답 계약으로 승격
- 매니저 과거 이력 화면에 평가 평균, 정산 금액, 기간 필터 같은 운영용 지표 확장

## 32. 2026-04-23 관리자 후속 알림/감사 로그 저장 계층 연결

### 구현

- `AdminActionNotification`, `AdminAuditLogEntry`, `AdminActionSourceType`, `AdminActionNotificationLevel` 도메인을 추가하고 `AdminDashboard`에 후속 알림/감사 로그 목록을 포함시켰다.
- `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`는 이제 `정산 후속 저장`, `긴급 대응 저장`, `문의 응답 저장` 시 알림과 감사 로그를 같이 남기고, 관리자 대시보드 조회 때도 함께 불러온다.
- 관리자 화면에는 `후속 알림 및 감사 로그` 섹션을 추가했고, `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterEntryBinder` 기준으로 알림과 감사 로그를 시간순 카드 타임라인으로 노출한다.
- `activity_admin.xml`, `item_admin_action_center_entry.xml`, `strings_admin_action_center_extension.xml`에 요약/빈 상태/카드 문구를 반영했고, `MockBodeulRepositoryTest`에 저장 직후 알림/감사 로그가 생성되는 테스트를 추가했다.
- 검증은 `assembleDebug --console=plain`, `testDebugUnitTest --console=plain`로 다시 통과했다.

### 변경 범위

- `domain/model`: `AdminDashboard`, `AdminActionNotification`, `AdminAuditLogEntry`, `AdminActionSourceType`, `AdminActionNotificationLevel`
- `data`: `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActivity`, `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterScreenModel`, `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder`, `AdminActionCenterTone`
- `layout/values/test`: `activity_admin.xml`, `item_admin_action_center_entry.xml`, `strings_admin_action_center_extension.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- 후속 상태 기반 우선순위와 필터 규칙을 서버 응답 계약으로 승격
- 관리자 후속 알림의 읽음/해결 상태와 운영 히스토리 필터 추가
- 매니저 과거 이력 화면에 평가 평균, 정산 금액, 기간 필터 같은 운영용 지표 확장

## 33. 2026-04-23 관리자 후속 알림 상태/매니저 이력 지표/의존성 점검

### 구현

- `AdminActionNotification`에 `isRead`, `readAt`, `isResolved`, `resolvedAt`, `resolvedByName` 필드를 추가하고, `AdminRepository`, `MockAdminRepository`, `FirebaseAdminRepository`, `MockBodeulRepository`에 읽음 처리와 해결 완료/재오픈 저장 흐름을 연결했다.
- 관리자 액션센터는 `AdminActionCenterActionType`, `AdminActionCenterActionModel`, 확장된 `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder` 기준으로 `미확인/읽음/해결 완료` 상태 배지와 `읽음 처리/해결 완료/다시 열기` 액션을 가진 카드 구조로 바꿨다.
- 액션센터 목록에는 `전체/미확인/해결 대기/해결 완료/감사 로그` 필터 칩을 추가해서 운영자가 필요한 후속 이력만 빠르게 추려보게 했다.
- `AdminActivity`, `item_admin_action_center_entry.xml`, `strings_admin_action_center_extension.xml`에 상태 버튼과 저장 토스트를 반영했고, 액션 수행 뒤 최신 대시보드를 다시 바인딩하도록 정리했다.
- 매니저 과거 이력은 `ManagerHistoryMetricModel`, `ManagerHistoryMetricBinder`, `item_manager_history_metric.xml`을 추가해서 `완료 동행`, `후속 기록률`, `정산 문의`, `SOS 기록` 지표를 상단에 노출하도록 확장했다.
- `MockBodeulRepositoryTest`에 관리자 후속 알림 `읽음 -> 해결 완료 -> 다시 열기` 상태 전이 테스트를 추가했고, 테스트 결과는 `20 tests, 0 failures, 0 errors`로 확인했다.
- 의존성은 version catalog 기준으로 점검해 `Android Gradle Plugin 9.2.0`, `Gradle 9.4.1`, `AppCompat 1.7.1`, `androidx.credentials 1.5.0`으로 올렸고, `sdkmanager`로 `platforms;android-35`를 설치한 뒤 `compileSdk 35`에서 빌드 통과를 확인했다.

### 변경 범위

- `domain/model`: `AdminActionNotification`
- `data`: `AdminRepository`, `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActivity`, `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder`, `AdminActionCenterActionType`, `AdminActionCenterActionModel`, `AdminActionCenterFilter`, `AdminActionCenterFilterChipModel`
- `ui/manager`: `ManagerHistoryActivity`, `ManagerHistoryBinder`, `ManagerHistoryCoordinator`, `ManagerHistoryScreenModel`, `ManagerHistoryMetricModel`, `ManagerHistoryMetricBinder`
- `layout/values/test`: `item_admin_action_center_entry.xml`, `activity_manager_history.xml`, `item_manager_history_metric.xml`, `strings_admin_action_center_extension.xml`, `strings_manager_history_extension.xml`, `MockBodeulRepositoryTest`
- `gradle`: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`

### 남은 범위

- 관리자 후속 알림의 서버 측 우선순위/필터 규칙을 API 계약으로 올리기
- 관리자 후속 알림 상태 변경 시 푸시/운영 알림 계층 연결
- 매니저 과거 이력의 기간 필터, 정산 금액, 평가 평균 같은 운영 지표 추가

## 34. 2026-04-23 관리자 후속 알림 서버 계약화

### 구현

- `AdminActionNotificationState`, `AdminActionNotificationPriority`, `AdminActionNotificationFilterKey`, `AdminActionNotificationContract`를 추가해서 관리자 후속 알림의 상태, 우선순위, 필터 태그 계산 규칙을 도메인 객체로 분리했다.
- `AdminActionNotification`은 이제 `state`, `priority`, `filterKeys`를 함께 보관하고, 기존 `isRead`, `isResolved`만 있는 데이터도 생성자에서 같은 계약으로 보정한다.
- `FirebaseAdminRepository`는 후속 알림 생성, 읽음 처리, 해결 완료/재오픈 시 `state`, `priority`, `filterKeys`를 Firestore 문서에 함께 저장하고, 대시보드 로드 시에도 이 값을 우선 사용하도록 맞췄다.
- `MockBodeulRepository`도 같은 계약값으로 정렬과 상태 전이를 맞췄고, 관리자 액션센터는 `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder` 기준으로 로컬 조건문 대신 계약값만 사용하도록 바꿨다.
- 액션센터 카드는 `상태 배지 + 우선순위 배지`를 함께 보여주고, `미확인/해결 대기/해결 완료` 필터 역시 `filterKeys` 기준으로 계산한다.
- 검증은 `assembleDebug --console=plain`, `testDebugUnitTest --console=plain --rerun-tasks`로 다시 수행했고, `MockBodeulRepositoryTest` 결과는 `20 tests, 0 failures, 0 errors`다.

### 변경 범위

- `domain/model`: `AdminActionNotification`, `AdminActionNotificationContract`, `AdminActionNotificationState`, `AdminActionNotificationPriority`, `AdminActionNotificationFilterKey`
- `data`: `MockBodeulRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActionCenterCoordinator`, `AdminActionCenterPresentationFormatter`, `AdminActionCenterEntryModel`, `AdminActionCenterEntryBinder`
- `layout/values/test`: `item_admin_action_center_entry.xml`, `strings_admin_action_center_extension.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- 관리자 후속 알림 상태 변경을 푸시/운영 알림 전송 계층과 연결
- `filterKeys` 기반 서버 쿼리와 우선순위 응답 규격을 실제 API 명세로 고정
- 매니저 과거 이력에 기간 필터, 정산 금액, 평가 평균 같은 운영 지표를 추가
## 35. 2026-04-23 관리자 후속 알림 전달 기록 연동
### 구현

- `AdminActionDeliveryChannel`, `AdminActionDeliveryStatus`, `AdminActionDeliveryTrigger`, `AdminActionDeliveryRecord`를 추가해 후속 알림 생성/읽음/해결/재오픈 시점의 전달 이력을 별도 도메인으로 분리했다.
- `AdminDashboard`가 `actionDeliveries`를 함께 보유하도록 확장하고, `MockBodeulRepository`, `MockAdminRepository`, `FirebaseAdminRepository`가 동일한 전달 기록 컬렉션을 조합하도록 맞췄다.
- 후속 알림 생성 시 `앱 푸시`, `운영 피드` 전달 기록을 같이 남기고, 읽음 처리/해결 완료/재오픈 시에는 추가 푸시 생략 여부와 운영 피드 반영 여부를 각각 기록하도록 저장소 로직을 정리했다.
- 관리자 화면에는 `AdminActionDeliveryCoordinator`, `AdminActionDeliveryPresentationFormatter`, `AdminActionDeliveryCardBinder`를 추가해 `후속 알림 전달 기록` 섹션을 별도로 렌더링하도록 구성했다.
- `activity_admin.xml`, `item_admin_action_delivery_entry.xml`, `strings_admin_action_delivery_extension.xml`을 추가/수정해 채널, 트리거, 상태, 최근 처리 시각, 전달 메모를 카드 형태로 노출했다.
- `MockBodeulRepositoryTest`에 전달 기록 생성/상태 전이 검증을 추가했고, `assembleDebug`, `testDebugUnitTest --rerun-tasks`를 모두 다시 통과시켰다.

### 변경 범위

- `domain/model`: `AdminDashboard`, `AdminActionDeliveryChannel`, `AdminActionDeliveryStatus`, `AdminActionDeliveryTrigger`, `AdminActionDeliveryRecord`
- `data`: `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActivity`, `AdminActionDeliveryCoordinator`, `AdminActionDeliveryPresentationFormatter`, `AdminActionDeliveryDashboardModel`, `AdminActionDeliveryCardModel`, `AdminActionDeliveryCardBinder`
- `layout/values/test`: `activity_admin.xml`, `item_admin_action_delivery_entry.xml`, `strings_admin_action_delivery_extension.xml`, `MockBodeulRepositoryTest`

### 남은 범위

- 전달 기록을 실제 푸시 발송 큐/운영 알림 채널과 연결
- 전달 실패 재시도, 읽음 확인, 해결 완료 후 후속 SLA 규칙을 서버 계약으로 고정
- 관리자 액션센터의 필터와 전달 기록 섹션을 동일한 서버 응답 모델로 합치기
## 36. 2026-04-23 관리자 후속 알림 전달 계약/SLA 반영
### 구현

- `AdminActionDeliveryContract`, `AdminActionDeliveryState`, `AdminActionDeliveryPriority`, `AdminActionDeliveryFilterKey`, `AdminActionDeliverySlaStatus`를 추가해 전달 기록의 상태, 우선순위, 필터 태그, SLA, 재시도 규칙을 도메인 객체로 고정했다.
- `AdminActionDeliveryRecord`는 이제 `attemptCount`, `maxAttemptCount`, `confirmedAtMillis`, `nextRetryAtMillis`, `slaDueAtMillis`와 파생 필드를 함께 보유하고, 기존 데이터는 생성자에서 같은 규칙으로 보정하도록 정리했다.
- `MockBodeulRepository`, `FirebaseAdminRepository`는 읽음 처리 시 `APP_PUSH=confirmed`, 재오픈 시 `APP_PUSH=sent`, 운영 피드 반영 시 `OPERATIONS_FEED=confirmed`를 남기도록 바꿔 실제 채널 반영 흐름과 전달 기록을 더 가깝게 맞췄다.
- Firebase 전달 문서는 이제 `state`, `priority`, `filterKeys`, `slaStatus`, `attemptCount`, `maxAttemptCount`, `confirmedAt`, `nextRetryAt`, `slaDueAt`를 함께 저장하도록 확장했다.
- 관리자 전달 기록 섹션은 `확인 대기/조치 필요/완료` 요약과 `채널 + 전달 결과 + 처리 상태` 배지를 함께 보여주고, 본문에는 읽음 확인 마감/확인 시각/재시도 예정/SLA 상태를 함께 노출하도록 정리했다.
- `MockBodeulRepositoryTest`에 SLA 초과 시 `follow_up_required`로 승격되는 규칙을 추가했고, 읽음 처리 시 앱 푸시 전달 기록이 `confirmed`로 남는 흐름도 검증했다.
- 검증은 `assembleDebug --console=plain`, `testDebugUnitTest --console=plain`로 다시 수행했다.

### 변경 범위

- `domain/model`: `AdminActionDeliveryStatus`, `AdminActionDeliveryRecord`, `AdminActionDeliveryContract`, `AdminActionDeliveryState`, `AdminActionDeliveryPriority`, `AdminActionDeliveryFilterKey`, `AdminActionDeliverySlaStatus`
- `data`: `MockBodeulRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActionDeliveryCoordinator`, `AdminActionDeliveryPresentationFormatter`, `AdminActionDeliveryCardModel`, `AdminActionDeliveryCardBinder`
- `layout/values`: `item_admin_action_delivery_entry.xml`, `strings_admin_action_delivery_extension.xml`
- `docs`: `../architecture/data-api.md`, `../operations/firebase/setup.md`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 전달 실패 재시도 큐와 실제 푸시 발송 결과를 `adminActionDeliveries`에 연결
- 관리자 액션센터와 전달 기록 섹션을 같은 필터/정렬 응답 모델로 더 통합
- 운영자 읽음 확인 주체, 재시도 소진 정책, SLA 초과 알림 정책을 서버 작업 큐 문서까지 확장
## 37. 2026-04-24 관리자 후속 알림 전달 큐 연동
### 구현

- `FirebaseAdminRepository`가 앱 푸시 채널의 `sent` 전달 기록을 만들 때 `adminActionDeliveryJobs` 큐 문서를 함께 생성하도록 바꿨다. 생성/재오픈 알림은 큐에 넣고, 읽음 확인/해결 완료처럼 즉시 종료되는 기록은 기존처럼 바로 저장한다.
- Firebase 전달 기록 로딩 시에는 문서에 저장된 파생 상태를 그대로 믿지 않고 현재 시점 기준으로 `AdminActionDeliveryRecord`를 다시 계산하게 바꿔, SLA 마감 시각이 지나면 앱에서 바로 `조치 필요`로 보이도록 맞췄다.
- `functions/index.js`에 `deliverAdminActionDeliveryJobs`, `dispatchAdminActionDeliveryJobs`를 추가하고, `adminActionDeliveryJobs`의 `PENDING/FAILED` 작업을 선점해 실제 발송 또는 시뮬레이션 처리한 뒤 결과를 `adminActionDeliveries`에 다시 반영하도록 구성했다.
- Functions는 수신 관리자 계정을 `ADMIN` 역할 사용자 기준으로 해석하고, 연동값이 없으면 `SIMULATED`, 수신자 없음이면 `SKIPPED`, 오류면 `FAILED`, 성공이면 `SENT` 작업 상태로 남긴다.
- `firestore.rules`에 `appointmentFollowUps`, `supportInquiries`, `adminSettlementRecords`, `adminEmergencyIssues`, `adminActionNotifications`, `adminAuditLogs`, `adminActionDeliveries`, `adminActionDeliveryJobs`, `appointmentReminderJobs` 접근 규칙을 추가해 Firebase 관리자 저장소와 큐 문서가 실제 권한 범위 안에서 동작하도록 정리했다.
- `../architecture/data-api.md`, `../operations/firebase/setup.md`에 `adminActionDeliveryJobs` 컬렉션, Functions 엔트리, 환경 변수, 처리 흐름을 문서화했다.

### 변경 범위

- `data/firebase`: `FirebaseAdminRepository`
- `functions`: `functions/index.js`
- `firebase`: `firestore.rules`
- `docs`: `../architecture/data-api.md`, `../operations/firebase/setup.md`

### 남은 범위

- 실제 푸시 공급자 응답 스펙에 맞춰 `ADMIN_PUSH_ENDPOINT` payload 필드를 최종 고정
- 전달 큐 결과와 관리자 액션센터 필터를 같은 서버 응답 모델로 더 통합
- 운영자 읽음 확인 주체, SLA 초과 재알림, 재시도 소진 후 수동 재발송 정책을 백오피스 작업 문서까지 확장

## 38. 2026-04-24 Gradle 성능/안정성 설정 점검
### 구현

- 루트 `gradle.properties`의 Gradle 데몬 JVM 메모리를 `-Xmx4096m`으로 상향하고 `daemon`, `parallel`, `caching`, `configuration-cache`를 명시해 Android Studio + Codex 작업 환경에서 빌드 재사용성을 높였다.
- 기존 `org.gradle.jvmargs=-Xmx2048m` 설정은 새 메모리 설정과 충돌하므로 제거하고 단일 값으로 정리했다.
- 루트 `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`을 다시 점검해 deprecated 설정, 중복 repository 선언, 불필요 의존성, 과도한 컴파일 옵션 여부를 확인했다.
- repository 선언은 `settings.gradle.kts`의 중앙 관리 한 곳으로 유지되고 있고, `app` 모듈의 `compileOptions`는 Java 17 단일 설정만 사용 중이라 추가 간소화가 필요하지 않음을 확인했다.
- `app/build.gradle.kts`의 `buildFeatures.resValues = true`는 `defaultConfig.resValue(...)` 사용 때문에 필수라 유지했다.
- 검증은 `help`, `assembleDebug`, `testDebugUnitTest`를 모두 다시 실행했고, `configuration-cache`가 저장 후 재사용되는 것까지 확인했다.

### 변경 범위

- `gradle`: `gradle.properties`
- `docs`: `implementation-status.md`

### 남은 범위

- Android Studio IDE 힙 메모리 설정이 별도로 낮게 잡혀 있다면 Gradle과 별개로 IDE 힙도 상향 검토
- CI가 있다면 CI 환경에서도 `configuration-cache` 재사용 여부를 한 번 더 확인

## 39. 2026-04-24 Gradle 병목 분석 점검
### 구현

- 루트/모듈 Gradle 스크립트와 실제 빌드 결과를 다시 점검한 결과, 현재 프로젝트에는 `kapt`, `annotationProcessor`, `ksp`, `productFlavors`가 없고 모듈도 `:app` 하나뿐이라 흔한 Gradle 병목 후보인 어노테이션 처리, flavor 조합 폭증, 모듈 간 중복 의존성 문제는 없음을 확인했다.
- `settings.gradle.kts`의 repository 선언은 `pluginManagement`와 `dependencyResolutionManagement` 두 블록으로만 유지되고 있고, 모듈별 `repositories {}` 반복 선언은 없어서 repository 중복으로 인한 sync 낭비도 없다.
- `app/build.gradle.kts`에는 `debug` 외 별도 build type 확장이나 flavor가 없고, `buildFeatures.resValues = true`만 `defaultConfig.resValue(...)` 때문에 유지되고 있어 debug 빌드에서 불필요하게 무거운 사용자 정의 설정은 없는 상태다.
- `assembleDebug --profile` 기준 주요 시간은 설정 단계가 아니라 Android 기본 태스크에 몰려 있었다. 상대적으로 크게 보인 항목은 `checkDebugDuplicateClasses`, `checkDebugAarMetadata`, `mergeDebugAssets`, `processDebugNavigationResources`, `mergeDebugNativeLibs`, `mergeDebugResources`, `processDebugResources`였고, `compileDebugKotlin`은 `NO-SOURCE` 상태로 약 `0.185s` 수준이었다.
- `help --no-configuration-cache --profile` 기준 설정 단계는 전체 `1.748s`였고, 그중 `:app` 구성 `1.140s`, 루트 프로젝트 구성 `0.608s`로 측정됐다. `configuration-cache`를 켠 상태에서는 같은 구성이 재사용되어 `help`, `assembleDebug`, `testDebugUnitTest` 모두 `Configuration cache entry reused`를 확인했다.
- `:app:properties` 기준 실제 적용값은 `org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8`, `org.gradle.daemon=true`, `org.gradle.parallel=true`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true`, `android.nonTransitiveRClass=true`였다.
- AGP 9의 내장 Kotlin 때문에 Kotlin 소스가 없어도 `compileDebugKotlin`, `compileDebugUnitTestKotlin` 태스크가 `NO-SOURCE`로 생성되는 것을 확인했다. 이는 AGP 9 기본 동작이며, 현재 프로젝트에서는 성능 영향이 작고 opt-out은 안정성 리스크가 있어 추가 변경은 하지 않았다.

### 변경 범위

- `docs`: `implementation-status.md`

### 남은 범위

- 빌드 시간이 더 문제되면 Android 기본 태스크 비중이 큰 의존성 묶음(`Firebase Auth + Credentials + Google ID`)과 리소스 수를 기능 단위로 줄이는 방향 검토
- AGP 9의 `android.enableAppCompileTimeRClass` 같은 추가 최적화 플래그는 리소스 상수 사용 방식 점검 후 별도 브랜치에서 검증

## 40. 2026-04-24 관리자 액션센터/전달 기록 공용 응답 모델 정리
### 구현

- `AdminActionOverview`, `AdminActionContract`를 추가해 액션센터와 전달 기록 섹션이 함께 참조하는 공용 요약 카운트와 정렬 규칙을 도메인 계약으로 올렸다.
- `AdminDashboard`는 이제 `actionOverview`를 함께 보유하고, `MockAdminRepository`, `FirebaseAdminRepository`가 대시보드 조합 시 `actionNotifications`, `auditLogs`, `actionDeliveries`와 함께 같은 요약 응답을 생성하도록 맞췄다.
- `MockBodeulRepository`, `FirebaseAdminRepository`의 관리자 후속 처리 목록 정렬도 `AdminActionContract`를 사용하도록 통일해 목업/실데이터 모드 모두 `알림=priority -> createdAt`, `감사 로그=createdAt`, `전달 기록=priority -> processedAt` 기준을 공유하게 했다.
- `AdminActionCenterCoordinator`, `AdminActionDeliveryCoordinator`는 더 이상 UI 안에서 요약 카운트를 다시 세지 않고, 대시보드의 `actionOverview`를 그대로 사용해 요약 문구와 필터 칩 개수를 표시하도록 정리했다.
- `MockBodeulRepositoryTest`에 관리자 대시보드가 `actionOverview`와 공용 정렬 계약을 함께 반영하는 검증을 추가했고, `assembleDebug`, `testDebugUnitTest`를 다시 통과했다.

### 변경 범위

- `domain/model`: `AdminDashboard`, `AdminActionOverview`, `AdminActionContract`
- `data`: `MockBodeulRepository`
- `data/mock`: `MockAdminRepository`
- `data/firebase`: `FirebaseAdminRepository`
- `ui/admin`: `AdminActionCenterCoordinator`, `AdminActionDeliveryCoordinator`, `AdminActivity`
- `docs`: `../architecture/data-api.md`, `implementation-status.md`
- `test`: `MockBodeulRepositoryTest`

### 남은 범위

- 실제 푸시 공급자 응답 스펙에 맞춰 `ADMIN_PUSH_ENDPOINT` payload 필드를 최종 고정
- 운영자 읽음 확인 주체, SLA 초과 재알림, 재시도 소진 후 수동 재발송 정책을 백오피스 작업 문서까지 확장
## 41. 2026-04-24 Firebase 개발용 기준선 초기화 절차 정리
### 구현

- Firestore에 누적된 테스트 데이터와 `merge` 기반 후속 문서 잔존 필드를 한 번에 정리할 수 있도록 [../operations/firebase/reset-baseline.md](../operations/firebase/reset-baseline.md)를 추가해 초기화 원칙, 삭제 대상 컬렉션, 재시드 기준선을 문서로 고정했다.
- [reset-firestore-baseline.js](../../tools/firebase/reset-firestore-baseline.js)를 추가해 `appointmentRequests`, `companionSessions`, `sessionReports`, `appointmentFollowUps`, `supportInquiries`, 관리자 후속 처리 컬렉션, `appointmentReminderJobs`까지 비우고, 기존 Auth UID 기준으로 `users`, `hospitalGuides`를 다시 만드는 개발용 절차를 자동화했다.
- 스크립트는 실제 삭제 전에 기준선 이메일 4개(`admin`, `patient`, `guardian`, `manager`)가 `Firebase Authentication`에 모두 존재하는지 확인하고, `--apply`에서는 누락된 계정도 기준선으로 자동 생성하도록 구성했다.
- [../operations/firebase/setup.md](../operations/firebase/setup.md)에도 기준선 초기화 문서와 실행 스크립트 링크를 연결했다.

### 변경 범위

- `tools/firebase`: `package.json`, `reset-firestore-baseline.js`
- `docs`: `../operations/firebase/reset-baseline.md`, `../operations/firebase/setup.md`, `implementation-status.md`

### 남은 범위

- 실제 Firebase 프로젝트에 적용하기 전 `dry-run`으로 현재 문서 수와 누락된 Auth 계정을 확인
- 기준선 초기화 후 환자/보호자/매니저/관리자 로그인과 예약 병원 선택, 관리자 가이드 목록을 실제 Firebase 모드에서 재검증
## 42. 2026-04-24 Firebase 운영 스크립트 디렉터리 분리
### 구현

- 개발용 기준선 초기화 스크립트를 배포 코드인 `functions/`에서 분리해 [tools/firebase](../../tools/firebase) 디렉터리로 옮겼다.
- `functions/package.json`에 붙어 있던 기준선 초기화 npm 스크립트는 제거하고, 운영 도구 전용 [tools/firebase/package.json](../../tools/firebase/package.json)을 추가해 `reset:baseline:dry-run`, `reset:baseline:apply`만 별도로 실행할 수 있게 정리했다.
- 새 [reset-firestore-baseline.js](../../tools/firebase/reset-firestore-baseline.js)는 `firebase-admin`이나 ADC에 기대지 않고, 로컬 `firebase login` 토큰과 REST API만으로 Auth 조회/기준선 생성, Firestore 컬렉션 초기화, `users`/`hospitalGuides` 재시드를 처리하도록 바꿨다.
- 초기화/시드/마이그레이션 같은 운영 도구는 앞으로도 `tools/firebase` 아래에 모으고, `functions/`는 실제 배포되는 백엔드 코드만 남기는 기준으로 정리했다.

### 변경 범위

- `tools/firebase`: `package.json`, `reset-firestore-baseline.js`
- `functions`: `package.json`
- `docs`: `../operations/firebase/reset-baseline.md`, `../operations/firebase/setup.md`, `implementation-status.md`

### 남은 범위

- `tools/firebase` 아래에 백업/복원, 샘플 데이터 주입, 컬렉션 상태 점검 스크립트까지 같은 규칙으로 정리
- 운영용 웹/백오피스가 분리되면 앱/Functions/운영 도구/관리자 프런트의 경계를 다시 문서화
## 43. 2026-04-24 Firebase 운영 도구 확장
### 구현

- `tools/firebase/lib` 공용 helper를 추가해 프로젝트 ID/토큰 해석, Auth 조회, Firestore 컬렉션 조회/삭제/저장 로직을 운영 스크립트들이 공통으로 쓰도록 정리했다.
- [check-firestore-state.js](../../tools/firebase/check-firestore-state.js)를 추가해 기준선 Auth 계정 존재 여부, `users` 문서 존재 여부, 관리 대상 컬렉션 문서 수를 한 번에 점검할 수 있게 했다.
- [backup-firestore-state.js](../../tools/firebase/backup-firestore-state.js)를 추가해 관리 대상 컬렉션을 JSON 백업 파일로 저장하도록 했고, 백업 파일은 `tools/firebase/backups/` 아래에 쌓이도록 정리했다.
- [restore-firestore-state.js](../../tools/firebase/restore-firestore-state.js)를 추가해 백업 파일 기준 dry-run / 실제 복원을 나눠 실행할 수 있게 했다. 복원은 Firestore 문서만 대상으로 하고 Auth 계정은 유지한다.
- [../operations/firebase/tools.md](../operations/firebase/tools.md)를 추가해 `check/reset/backup/restore` 사용법과 디렉터리 운영 기준을 문서화했다.

### 변경 범위

- `tools/firebase`: `package.json`, `check-firestore-state.js`, `backup-firestore-state.js`, `restore-firestore-state.js`, `reset-firestore-baseline.js`, `backups/.gitkeep`
- `tools/firebase/lib`: `baseline-config.js`, `firebase-toolkit.js`
- `docs`: `../operations/firebase/setup.md`, `../operations/firebase/tools.md`, `implementation-status.md`
- 루트 설정: `.gitignore`

### 남은 범위

- `tools/firebase`에 샘플 예약/세션/후속 처리 흐름을 넣는 데이터 주입 스크립트 추가
- 백업 파일 검증용 스크립트와 컬렉션 diff 도구 추가

## 44. 2026-04-24 Firebase 샘플 서비스 데이터 주입 스크립트 추가
### 구현

- [seed-sample-service-data.js](../../tools/firebase/seed-sample-service-data.js)를 추가해 기준선 Auth / `users` 문서가 준비된 상태에서 예약 대기, 진행 중 동행, 종료 후속 처리 3개 시나리오를 한 번에 Firestore에 주입할 수 있게 했다.
- 샘플 데이터는 `appointmentRequests`, `companionSessions`, `sessionReports`, `appointmentFollowUps`, `supportInquiries`, `adminSettlementRecords`, `adminEmergencyIssues`, `adminActionNotifications`, `adminAuditLogs`, `adminActionDeliveries`, `adminActionDeliveryJobs`, `appointmentReminderJobs`를 고정 ID로 upsert하도록 구성해 반복 실행 시 중복 문서가 늘어나지 않게 했다.
- 요청 문서에는 예약 확장 필드(`appointmentAtEpochMillis`, `appointmentDateKey`, 결제/옵션/연결 사용자 정보)를 함께 넣고, 완료 시나리오에는 후기/정산/SOS 후속 기록과 관리자 후속 알림/전달 기록, 푸시 큐 작업까지 같이 생성하도록 맞췄다.
- [tools/firebase/package.json](../../tools/firebase/package.json)에 `seed:sample:dry-run`, `seed:sample:apply` 실행점을 추가하고, [../operations/firebase/tools.md](../operations/firebase/tools.md), [../operations/firebase/setup.md](../operations/firebase/setup.md)에 사용 절차를 문서화했다.
- 검증은 `npm run seed:sample:dry-run`, `npm run seed:sample:apply`, `npm run check:state`, `.\gradlew.bat assembleDebug --console=plain` 순서로 다시 확인한다.

### 변경 범위

- `tools/firebase`: `package.json`, `seed-sample-service-data.js`
- `docs`: `../operations/firebase/tools.md`, `../operations/firebase/setup.md`, `implementation-status.md`

### 남은 범위

- 백업 파일 검증용 스크립트와 컬렉션 diff 도구 추가
- 샘플 데이터를 역할별 화면 진입 기준으로 스냅샷 검증하는 체크리스트 또는 자동 점검 스크립트 추가

## 45. 2026-04-24 Firebase 백업 검증 / 상태 diff 도구 추가
### 구현

- [validate-firestore-backup.js](../../tools/firebase/validate-firestore-backup.js)를 추가해 백업 파일의 `schemaVersion`, `collections`, 문서 `path`/`id`/`fields` 구조를 검사하고, 관리 대상 컬렉션 누락이나 잘못된 경로, 중복 path를 오류/경고로 알려주도록 했다.
- [diff-firestore-state.js](../../tools/firebase/diff-firestore-state.js)를 추가해 백업 파일과 현재 Firestore 상태를 비교하고, 컬렉션별 추가/삭제/변경 문서를 요약할 수 있게 했다.
- [tools/firebase/package.json](../../tools/firebase/package.json)에 `validate:backup`, `diff:state` 실행점을 추가하고, [../operations/firebase/tools.md](../operations/firebase/tools.md), [../operations/firebase/setup.md](../operations/firebase/setup.md)에 사용 방법을 반영했다.
- 검증은 `node --check`로 스크립트 문법을 확인한 뒤 `npm run validate:backup -- --file ...`, `npm run diff:state -- --file ...`, `.\gradlew.bat assembleDebug --console=plain`로 다시 확인한다.

### 변경 범위

- `tools/firebase`: `package.json`, `validate-firestore-backup.js`, `diff-firestore-state.js`
- `docs`: `../operations/firebase/tools.md`, `../operations/firebase/setup.md`, `implementation-status.md`

### 남은 범위

- 샘플 데이터를 역할별 화면 진입 기준으로 스냅샷 검증하는 체크리스트 또는 자동 점검 스크립트 추가
- 운영 도구 결과를 한 번에 보는 간단한 HTML/CLI 리포트 묶음 검토

## 46. 2026-04-24 Firebase 역할별 화면 진입 점검 / 운영 리포트 추가
### 구현

- [check-role-screen-readiness.js](../../tools/firebase/check-role-screen-readiness.js)를 추가해 환자/보호자/매니저/관리자 기준선 계정이 현재 Firebase 샘플 데이터만으로 실제 화면 진입에 필요한 컬렉션을 갖췄는지 자동 점검할 수 있게 했다.
- 점검 기준은 현재 Firebase 저장소 코드가 읽는 조합에 맞춰 잡았고, `예약 대기`, `진행 중 동행`, `종료 후속 처리` 샘플 시나리오가 요청/세션/리포트/후속 처리/관리자 전달 기록까지 연결됐는지도 함께 확인하도록 구성했다.
- [generate-operations-report.js](../../tools/firebase/generate-operations-report.js)와 [operations-report.js](../../tools/firebase/lib/operations-report.js)를 추가해 현재 상태, 역할별 점검 결과, 기준선 계정 상태, 컬렉션 문서 수, 백업 대비 diff를 한 번에 담은 HTML 운영 리포트를 생성할 수 있게 했다.
- [tools/firebase/package.json](../../tools/firebase/package.json)에 `check:readiness`, `report:ops` 실행점을 추가했고, [../operations/firebase/tools.md](../operations/firebase/tools.md), [../operations/firebase/setup.md](../operations/firebase/setup.md)에 사용 방법을 반영했다.
- 생성 리포트는 `tools/firebase/reports/` 아래에 저장하고, [.gitignore](../../.gitignore)에 HTML 결과물을 Git 추적 대상에서 제외하도록 정리했다.
- 검증은 `npm run check:readiness`, `npm run report:ops -- --file backups/firestore-backup-20260424-015754.json`, `.\gradlew.bat assembleDebug --console=plain` 순서로 다시 확인한다.

### 변경 범위

- `tools/firebase`: `package.json`, `check-role-screen-readiness.js`, `generate-operations-report.js`, `reports/.gitkeep`
- `tools/firebase/lib`: `operations-report.js`
- `docs`: `../operations/firebase/tools.md`, `../operations/firebase/setup.md`, `implementation-status.md`
- 루트 설정: `.gitignore`

### 남은 범위

- 운영 리포트에 스크린샷 또는 실제 앱 네비게이션 결과를 연결하는 단계 검토
- Firebase 운영 도구를 묶어 실행하는 단일 워크플로 스크립트 또는 체크리스트 정리

## 47. 2026-04-24 Firebase 운영 워크플로 스크립트 추가
### 구현

- [backup-validator.js](../../tools/firebase/lib/backup-validator.js)로 백업 검증 로직을 공용 helper로 분리하고, [validate-firestore-backup.js](../../tools/firebase/validate-firestore-backup.js)도 같은 로직을 재사용하도록 정리했다.
- [run-operations-workflow.js](../../tools/firebase/run-operations-workflow.js)를 추가해 현재 Firebase 상태 수집, 역할별 화면 진입 점검, 백업 검증, diff 계산, HTML 리포트 생성, JSON 요약 저장을 한 번에 수행할 수 있게 했다.
- 워크플로는 [firebase-toolkit.js](../../tools/firebase/lib/firebase-toolkit.js)에서 `firebase login` 저장 토큰이 만료되면 자동으로 refresh token으로 갱신하도록 보강한 뒤 실행되도록 맞췄다. 그래서 Studio 재시작이나 시간이 지난 뒤에도 운영 스크립트가 다시 401로 끊기지 않게 했다.
- [tools/firebase/package.json](../../tools/firebase/package.json)에 `workflow:ops` 실행점을 추가했고, [../operations/firebase/tools.md](../operations/firebase/tools.md), [../operations/firebase/setup.md](../operations/firebase/setup.md)에 `--strict`, `--json` 포함 사용 절차를 반영했다.
- 워크플로 산출물인 JSON 요약도 `tools/firebase/reports/` 아래에 저장하고 [.gitignore](../../.gitignore)에 HTML/JSON 산출물을 Git 추적 대상에서 제외하도록 정리했다.
- 검증은 `npm run validate:backup -- --file backups/firestore-backup-20260424-015754.json`, `npm run workflow:ops -- --file backups/firestore-backup-20260424-015754.json`, `.\gradlew.bat assembleDebug --console=plain` 순서로 다시 확인한다.

### 변경 범위

- `tools/firebase`: `package.json`, `validate-firestore-backup.js`, `run-operations-workflow.js`
- `tools/firebase/lib`: `backup-validator.js`, `firebase-toolkit.js`
- `docs`: `../operations/firebase/tools.md`, `../operations/firebase/setup.md`, `implementation-status.md`
- 루트 설정: `.gitignore`

### 남은 범위

- 운영 리포트에 스크린샷 또는 실제 앱 네비게이션 결과를 연결하는 단계 검토
- 운영 워크플로 결과를 CI나 배포 전 점검 루틴과 연결할지 결정

## 48. 2026-04-24 로컬 프리플라이트 스크립트 추가
### 구현

- [run-local-preflight.js](../../tools/firebase/run-local-preflight.js)를 추가해 Firebase 운영 워크플로, `assembleDebug`, `testDebugUnitTest`를 한 번에 실행하는 로컬 프리플라이트 루틴을 만들었다.
- 프리플라이트는 중간 단계가 실패해도 마지막까지 실행한 뒤 전체 상태를 계산하고, 워크플로가 생성한 HTML/JSON 산출물과 함께 별도의 Markdown/JSON 요약 파일을 `tools/firebase/reports/` 아래에 남기도록 구성했다.
- 워크플로 단계는 `workflow:ops`를 내부에서 재사용하고, 백업 파일 경로가 주어지면 Firebase 점검 결과와 Gradle 빌드/테스트 결과를 한 묶음으로 기록한다.
- [tools/firebase/package.json](../../tools/firebase/package.json)에 `preflight:local` 실행점을 추가했고, [../operations/firebase/tools.md](../operations/firebase/tools.md), [../operations/firebase/setup.md](../operations/firebase/setup.md)에 사용 방법과 `--skip-workflow`, `--skip-build`, `--skip-tests` 옵션을 반영했다.
- 프리플라이트가 생성하는 Markdown 요약도 운영 리포트와 마찬가지로 [.gitignore](../../.gitignore)에 Git 추적 대상에서 제외하도록 정리했다.
- 검증은 `npm run preflight:local -- --file backups/firestore-backup-20260424-015754.json` 실행으로 완료했고, Firebase 운영 워크플로(`ready`), `assembleDebug`, `testDebugUnitTest`가 모두 통과했으며 당시 로컬 요약 파일 `local-preflight-summary-20260424-125837.md`, `local-preflight-summary-20260424-125837.json`을 생성했다.

### 변경 범위

- `tools/firebase`: `package.json`, `run-local-preflight.js`
- `docs`: `../operations/firebase/tools.md`, `../operations/firebase/setup.md`, `implementation-status.md`
- 루트 설정: `.gitignore`

### 남은 범위

- 운영 리포트에 스크린샷 또는 실제 앱 네비게이션 결과를 연결하는 단계 검토
- 운영 워크플로/프리플라이트 결과를 CI나 배포 전 점검 루틴과 연결할지 결정

## 49. 2026-04-24 앱 화면 증적 캡처 및 운영 리포트 연동
### 구현

- [capture-app-navigation-evidence.js](../../tools/firebase/capture-app-navigation-evidence.js)를 추가해 연결된 에뮬레이터/디바이스의 현재 화면을 캡처하고, `reports/screenshots/` 아래 PNG와 `app-navigation-evidence-latest.json` 증적 파일로 남기도록 구성했다.
- 공용 helper [android-toolkit.js](../../tools/firebase/lib/android-toolkit.js)에서 `adb` 경로 탐색, 디바이스 선택, 현재 화면 캡처, 디바이스 메타데이터 수집을 분리했고, [app-navigation-evidence.js](../../tools/firebase/lib/app-navigation-evidence.js)에서 증적 파일 로드/정규화/기본 경로 결정을 맡도록 나눴다.
- [operations-report.js](../../tools/firebase/lib/operations-report.js), [generate-operations-report.js](../../tools/firebase/generate-operations-report.js), [run-operations-workflow.js](../../tools/firebase/run-operations-workflow.js), [run-local-preflight.js](../../tools/firebase/run-local-preflight.js)에 `--app-evidence` 연결을 추가해, 증적 파일이 있으면 운영 리포트 HTML과 워크플로/프리플라이트 요약에 앱 화면 섹션과 통계가 함께 반영되도록 했다.
- [tools/firebase/package.json](../../tools/firebase/package.json)에 `capture:app` 실행점을 추가했고, [.gitignore](../../.gitignore)에 `reports/screenshots/*.png`를 제외하도록 정리했다.
- 증적 포맷 예시는 [app-navigation-evidence.sample.json](../../tools/firebase/templates/app-navigation-evidence.sample.json)에 남겨 두었다.
- 검증은 `node --check tools/firebase/capture-app-navigation-evidence.js`, `node --check tools/firebase/generate-operations-report.js`, `node --check tools/firebase/run-operations-workflow.js`, `node --check tools/firebase/run-local-preflight.js`로 문법을 확인했고, `npm run report:ops -- --file backups/firestore-backup-20260424-015754.json --app-evidence templates/app-navigation-evidence.sample.json`, `npm run workflow:ops -- --file backups/firestore-backup-20260424-015754.json --app-evidence templates/app-navigation-evidence.sample.json`, `npm run preflight:local -- --file backups/firestore-backup-20260424-015754.json --app-evidence templates/app-navigation-evidence.sample.json` 실행으로 당시 로컬 리포트 `firestore-operations-report-20260424-131150.html`, 요약 `firestore-operations-summary-20260424-131150.json`, 프리플라이트 `local-preflight-summary-20260424-131152.md`를 생성했다. 실제 `adb` 캡처는 연결된 디바이스가 없어 도움말 확인까지만 수행했다.

### 변경 범위

- `tools/firebase`: `capture-app-navigation-evidence.js`, `generate-operations-report.js`, `run-operations-workflow.js`, `run-local-preflight.js`, `package.json`
- `tools/firebase/lib`: `android-toolkit.js`, `app-navigation-evidence.js`, `operations-report.js`
- `tools/firebase/templates`: `app-navigation-evidence.sample.json`
- `tools/firebase/reports/screenshots`: `.gitkeep`
- `docs`: `../operations/firebase/tools.md`, `../operations/firebase/setup.md`, `implementation-status.md`
- 루트 설정: `.gitignore`

### 남은 범위

- 실제 에뮬레이터/디바이스가 연결된 상태에서 역할별 화면 이동을 어디까지 자동화할지 결정
- 운영 워크플로/프리플라이트 결과를 CI나 배포 전 점검 루틴과 연결할지 결정

## 50. 2026-04-24 CI 프리플라이트 및 GitHub Actions 연동
### 구현

- [run-ci-preflight.js](../../tools/firebase/run-ci-preflight.js)를 추가해 CI 환경에서 Firebase 입력이 준비되면 전체 프리플라이트를, 준비되지 않았으면 `--skip-workflow` 모드로 빌드/테스트만 수행하도록 분기했다.
- CI 실행점은 [run-local-preflight.js](../../tools/firebase/run-local-preflight.js)를 그대로 재사용하고, `--require-firebase`가 들어오면 `FIREBASE_TOKEN` 또는 프로젝트 식별 정보가 없을 때 실패로 종료하도록 했다.
- [tools/firebase/package.json](../../tools/firebase/package.json)에 `preflight:ci` 스크립트를 추가했다.
- [.github/workflows/android-preflight.yml](../../.github/workflows/android-preflight.yml)을 추가해 `pull_request`, `workflow_dispatch`에서 JDK 17/Node 22를 설정한 뒤 CI 프리플라이트를 실행하고, `tools/firebase/reports/` 산출물을 아티팩트로 업로드하도록 구성했다.
- 워크플로는 `secrets.GOOGLE_SERVICES_JSON`, `secrets.FIREBASERC_JSON`, `secrets.FIREBASE_TOKEN`, `vars.FIREBASE_PROJECT_ID`가 있으면 Firebase 운영 점검까지 포함하고, 없으면 자동으로 Android 빌드/테스트만 수행한다.
- 사용 방법과 필요한 시크릿 이름은 [../operations/firebase/tools.md](../operations/firebase/tools.md), [../operations/firebase/setup.md](../operations/firebase/setup.md)에 반영했다.
- 검증은 `node --check tools/firebase/run-ci-preflight.js`로 문법을 확인했고, `npm run preflight:ci -- --app-evidence templates/app-navigation-evidence.sample.json` 실행으로 Firebase 운영 워크플로(`ready`), `assembleDebug`, `testDebugUnitTest`가 모두 통과했으며 당시 로컬 산출물 `firestore-operations-report-20260424-131815.html`, `firestore-operations-summary-20260424-131815.json`, `local-preflight-summary-20260424-131817.md`를 생성했다.

### 변경 범위

- `tools/firebase`: `run-ci-preflight.js`, `package.json`
- `.github/workflows`: `android-preflight.yml`
- `docs`: `../operations/firebase/tools.md`, `../operations/firebase/setup.md`, `implementation-status.md`

### 남은 범위

- 실제 에뮬레이터/디바이스가 연결된 상태에서 역할별 화면 이동을 어디까지 자동화할지 결정
- GitHub Actions에서 Firebase 시크릿을 실제로 연결한 뒤 운영 워크플로 포함 모드까지 검증

## 51. 2026-04-24 debug 자동 진입 액티비티 및 프리셋 캡처 연동
### 구현

- [AutomationEntryActivity.java](../../app/src/debug/java/com/example/bodeul/debug/AutomationEntryActivity.java)와 [app/src/debug/AndroidManifest.xml](../../app/src/debug/AndroidManifest.xml)을 추가해 debug 빌드에서만 `adb`가 직접 열 수 있는 자동 진입 액티비티를 만들었다.
- 자동 진입 액티비티는 `role`, `screen`, `requestId`, `forceSignIn` extra를 받아 기준선 계정(`admin@bodeul.app`, `manager@bodeul.app`, `patient@bodeul.app`, `guardian@bodeul.app`)으로 로그인한 뒤 홈, 예약 상세, 후속 처리, 보호자 리포트, 매니저 홈/과거 이력/가이드/문의/내 페이지, 관리자 대시보드로 라우팅한다.
- [app-navigation-routes.js](../../tools/firebase/lib/app-navigation-routes.js)에 역할별 프리셋과 기대 액티비티를 정리했고, [android-toolkit.js](../../tools/firebase/lib/android-toolkit.js)에 debug 자동 진입 실행과 포커스 대기 helper를 추가했다.
- [capture-app-navigation-evidence.js](../../tools/firebase/capture-app-navigation-evidence.js)는 `--preset` 기반 자동 진입, 포커스 확인, 상태 자동 판정(`passed`/`failed`)을 지원하도록 확장했다.
- 문서에는 프리셋 목록과 예시 명령을 반영했다.
- 검증은 `node --check tools/firebase/capture-app-navigation-evidence.js`, `node --check tools/firebase/lib/android-toolkit.js`, `node --check tools/firebase/lib/app-navigation-routes.js`, `.\gradlew.bat assembleDebug --console=plain`, `node tools/firebase/capture-app-navigation-evidence.js --help`로 확인했다.

### 변경 범위

- `app/src/debug`: `AndroidManifest.xml`, `java/com/example/bodeul/debug/AutomationEntryActivity.java`
- `tools/firebase`: `capture-app-navigation-evidence.js`
- `tools/firebase/lib`: `android-toolkit.js`, `app-navigation-routes.js`
- `docs`: `../operations/firebase/tools.md`, `../operations/firebase/setup.md`, `implementation-status.md`

### 남은 범위

- 실제 에뮬레이터/디바이스 연결 상태에서 프리셋별 자동 진입과 캡처를 한 번씩 실측 검증
- GitHub Actions에서 Firebase 시크릿을 실제로 연결한 뒤 운영 워크플로 포함 모드까지 검증

## 52. 2026-04-24 실기기 프리셋 자동 진입 및 화면 증적 실측
### 구현

- 연결된 실기기 `SM-S921N (Android 16)`에 `installDebug`로 당시 로컬 산출물 `app/build/outputs/apk/debug/app-debug.apk`를 다시 설치한 뒤 프리셋 전체를 실측했다.
- 자동 진입 실측 과정에서 `adb shell am start`만으로는 현재 태스크에 인텐트가 재전달되며 포커스 검증이 흔들리는 문제가 있어, [android-toolkit.js](../../tools/firebase/lib/android-toolkit.js)에서 프리셋 자동 진입 시 `-S` 강제 재시작을 붙이도록 수정했다.
- [app-navigation-routes.js](../../tools/firebase/lib/app-navigation-routes.js)의 기본 대기 시간을 10초로 늘렸고, [capture-app-navigation-evidence.js](../../tools/firebase/capture-app-navigation-evidence.js)에서는 `com.example.bodeul/.MainActivity`처럼 축약된 액티비티 표기도 정상 비교하도록 포커스 판정을 보강했다.
- 프리셋 `patient-home`, `guardian-home`, `patient-booking`, `guardian-booking-status`, `patient-booking-follow-up`, `guardian-report`, `manager-home`, `manager-history`, `manager-guide`, `manager-support`, `manager-profile`, `admin-dashboard`를 모두 실행했고, 당시 로컬 `app-navigation-evidence-latest.json`에 `통과 12 / 경고 0 / 실패 0`으로 기록했다.
- 실기기 증적을 반영한 당시 로컬 운영 리포트 `firestore-operations-report-20260424-133404.html`, 요약 `firestore-operations-summary-20260424-133404.json`, 프리플라이트 `local-preflight-summary-20260424-133408.md`를 다시 생성했다.
- 검증은 `.\gradlew.bat installDebug --console=plain`, 프리셋 전체 `node tools/firebase/capture-app-navigation-evidence.js --preset ...`, `npm run workflow:ops -- --file backups/firestore-backup-20260424-015754.json --app-evidence reports/app-navigation-evidence-latest.json`, `npm run preflight:local -- --file backups/firestore-backup-20260424-015754.json --app-evidence reports/app-navigation-evidence-latest.json` 순서로 수행했다.

### 변경 범위

- `tools/firebase`: `capture-app-navigation-evidence.js`
- `tools/firebase/lib`: `android-toolkit.js`, `app-navigation-routes.js`
- `docs`: `../operations/firebase/tools.md`, `implementation-status.md`

### 남은 범위

- GitHub Actions에서 Firebase 시크릿을 실제로 연결한 뒤 운영 워크플로 포함 모드까지 검증

## 53. 2026-04-24 GitHub Actions Firebase 시크릿 반영 준비 및 토큰 호환 보강
### 구현

- GitHub 원격과 CLI 인증 상태를 점검한 결과, 원격은 `git@github.com:bodeul110/Bodeul.git`이고 SSH 키는 `bodeul110` 계정으로 인증되지만, 현재 `gh` 로그인 계정은 `21017053`이라 `repos/bodeul110/Bodeul` API 접근이 `404`로 막혀 있는 상태를 확인했다.
- Firebase 공식 문서 기준 `FIREBASE_TOKEN`은 `firebase login:ci`가 발급하는 refresh token인데, 기존 [firebase-toolkit.js](../../tools/firebase/lib/firebase-toolkit.js)는 이를 단순 access token처럼 사용하고 있었다. 이를 보강해 `FIREBASE_TOKEN`이 refresh token이면 access token으로 자동 교환하고, 기존 access token 입력도 그대로 허용하도록 수정했다.
- 같은 파일에 `resolveFirebaseCiToken()`과 `resolveProjectId()` export를 추가해, GitHub 시크릿 반영 스크립트가 로컬 Firebase 로그인 상태나 `.firebaserc` / `app/google-services.json` 값을 그대로 재사용할 수 있게 했다.
- [github-toolkit.js](../../tools/github/lib/github-toolkit.js)를 추가해 origin 원격 해석, `gh api` 기반 저장소 접근 점검, GitHub Actions secret/variable 반영, `workflow_dispatch` 실행을 공용 helper로 분리했다.
- [configure-actions-firebase.js](../../tools/github/configure-actions-firebase.js)는 `secrets.FIREBASE_TOKEN`, `secrets.GOOGLE_SERVICES_JSON`, `secrets.FIREBASERC_JSON`, `vars.FIREBASE_PROJECT_ID`를 한 번에 반영하고, `--dispatch`가 있으면 `android-preflight.yml`까지 바로 실행하도록 구성했다.
- 문서 [../operations/firebase/tools.md](../operations/firebase/tools.md), [../operations/firebase/setup.md](../operations/firebase/setup.md)에는 `FIREBASE_TOKEN`의 refresh token 기준과 GitHub CLI 계정 권한 전제조건을 반영했다.
- 검증은 `node --check tools/github/configure-actions-firebase.js`, `node --check tools/github/lib/github-toolkit.js`, `node --check tools/firebase/lib/firebase-toolkit.js`, `node tools/github/configure-actions-firebase.js --repo bodeul110/Bodeul --dry-run --skip-access-check`, `node tools/github/configure-actions-firebase.js --repo bodeul110/Bodeul --dry-run`, `npm run preflight:ci -- --app-evidence templates/app-navigation-evidence.sample.json`, `.\gradlew.bat assembleDebug --console=plain`로 진행했고, 실제 접근 점검 모드에서는 현재 `gh` 계정이 `21017053`라 저장소 API 권한 부족으로 중단되는 것을 확인했다.

### 변경 범위

- `tools/firebase/lib`: `firebase-toolkit.js`
- `tools/github`: `configure-actions-firebase.js`
- `tools/github/lib`: `github-toolkit.js`
- `docs`: `../operations/firebase/tools.md`, `../operations/firebase/setup.md`, `implementation-status.md`

### 남은 범위

- `gh`를 `bodeul110/Bodeul` 저장소 권한이 있는 계정으로 다시 로그인한 뒤 `configure-actions-firebase.js`로 시크릿/변수 반영
- GitHub Actions에서 `android-preflight.yml`을 `require_firebase_ops=true`로 실제 실행해 전체 모드 검증

## 54. 2026-04-24 GitHub Actions 시크릿 반영 완료 및 원격 워크플로 부재 확인
### 구현

- `gh` 로그인 계정을 `bodeul110`으로 다시 맞춘 뒤 `gh api repos/bodeul110/Bodeul`로 저장소 관리자 권한을 확인했다.
- [configure-actions-firebase.js](../../tools/github/configure-actions-firebase.js)를 실제 실행해 GitHub Actions 시크릿과 변수를 반영했다.
  - `secrets.FIREBASE_TOKEN`
  - `secrets.GOOGLE_SERVICES_JSON`
  - `secrets.FIREBASERC_JSON`
  - `vars.FIREBASE_PROJECT_ID=bodeul-dev`
- 검증은 `gh api repos/bodeul110/Bodeul/actions/secrets`, `gh api repos/bodeul110/Bodeul/actions/variables`로 다시 조회해 시크릿 3개와 변수 1개가 생성된 것을 확인했다.
- 이어서 `gh workflow run android-preflight.yml --repo bodeul110/Bodeul --ref master`를 시도했지만, 원격 기본 브랜치에 `.github/workflows/android-preflight.yml`이 아직 없어 `workflow ... not found on the default branch`로 중단되는 것을 확인했다.
- 즉, GitHub Actions 전체 모드의 마지막 차단점은 시크릿이 아니라 워크플로 파일이 아직 원격 저장소에 push되지 않은 상태라는 점이다.

### 변경 범위

- `docs`: `../operations/firebase/tools.md`, `../operations/firebase/setup.md`, `implementation-status.md`
- 외부 상태: `bodeul110/Bodeul` 저장소 GitHub Actions 시크릿 3개, 변수 1개

### 남은 범위

- 로컬의 `.github/workflows/android-preflight.yml`을 원격 기본 브랜치에 반영
- 반영 후 `gh workflow run android-preflight.yml --repo bodeul110/Bodeul --ref master --field require_firebase_ops=true`로 실제 전체 모드 실행 검증

## 55. 2026-04-24 GitHub Actions `app_evidence` 경로 보정
### 구현

- 첫 번째 GitHub Actions 전체 모드 실행에서 `CI 프리플라이트 실행` 단계가 `tools/firebase/tools/firebase/templates/app-navigation-evidence.sample.json`를 찾다가 실패하는 것을 확인했다.
- 원인은 [app-navigation-evidence.js](../../tools/firebase/lib/app-navigation-evidence.js)가 `--app-evidence` 입력을 현재 작업 디렉터리 기준으로만 해석해서, `tools/firebase` 내부에서 실행될 때 repo 루트 기준 경로를 중복으로 붙이던 점이었다.
- 이를 수정해 `--app-evidence`가 들어오면 현재 작업 디렉터리 기준 경로와 repo 루트 기준 경로를 모두 검사하고, 실제 존재하는 파일을 우선 사용하도록 보정했다.
- 검증은 `node --check tools/firebase/lib/app-navigation-evidence.js`, `node tools/firebase/run-ci-preflight.js --require-firebase --app-evidence tools/firebase/templates/app-navigation-evidence.sample.json`, `.\gradlew.bat assembleDebug --console=plain`로 진행했고 모두 통과했다.

### 변경 범위

- `tools/firebase/lib`: `app-navigation-evidence.js`
- `docs`: `../operations/firebase/tools.md`, `implementation-status.md`

### 남은 범위

- 경로 보정 커밋을 원격에 반영
- GitHub Actions `android-preflight.yml` 전체 모드를 재실행해 성공 여부 확인

## 56. 2026-04-24 GitHub Actions 전체 모드 실검증 성공
### 구현

- 경로 보정 커밋 `340a109`를 원격 `master`에 push한 뒤 `gh workflow run android-preflight.yml --repo bodeul110/Bodeul --ref master --field require_firebase_ops=true --field app_evidence_path=tools/firebase/templates/app-navigation-evidence.sample.json`로 GitHub Actions 전체 모드를 다시 실행했다.
- 실행 런은 [24873140407](https://github.com/bodeul110/Bodeul/actions/runs/24873140407)이며, `preflight` 잡이 `2026-04-24T05:02:31Z`에 시작해 `2026-04-24T05:07:14Z`에 성공으로 종료된 것을 확인했다.
- 이 런에서 `google-services.json` 복원, `.firebaserc` 복원, `CI 프리플라이트 실행`, `운영 리포트 아티팩트 업로드`까지 모두 성공했고, Firebase 운영 워크플로 포함 모드가 GitHub Actions에서도 실제로 동작하는 것을 검증했다.
- 당시 남은 경고는 GitHub-hosted runner의 JavaScript action 런타임이 표시한 Node 20 deprecation 경고였다. 이 경고는 2026-07-16에 Node 24 기반 Action으로 전환한 PR #161, #172, #179, #180 반영 후 최종 Core API 배포에서 annotation 0건으로 해소됐다.

### 변경 범위

- `docs`: `../operations/firebase/tools.md`, `implementation-status.md`
- 외부 상태: GitHub Actions run `24873140407` 성공

### 남은 범위

- GitHub Actions 주요 버전 변경 시 Node 런타임과 라이선스 조건을 함께 점검

## 57. 2026-04-24 다중 작업자 협업 규칙 문서화
### 구현

- 여러 작업자가 동시에 들어와도 충돌을 줄일 수 있도록 [../operations/collaboration-rules.md](../operations/collaboration-rules.md)를 새로 추가했다.
- 문서에는 시작 전 확인 순서, 충돌 위험이 큰 파일, 담당 범위 권장안, `implementation-status.md` 갱신 규칙, Firebase 운영 작업 단일 담당 원칙, 종료 전 체크리스트를 정리했다.
- [README.md](../../README.md) 문서 목록과 협업 설정 섹션에도 협업 규칙 문서 링크를 추가해 처음 들어오는 사람이 바로 찾을 수 있게 했다.

### 변경 범위

- `docs`: `../operations/collaboration-rules.md`, `implementation-status.md`
- 루트 문서: `README.md`

### 남은 범위

- 팀 내 실제 담당 방식에 맞춰 역할 구분이나 브랜치 규칙을 필요 시 더 구체화

## 58. 2026-04-24 협업 규칙에 작업 전 확인 절차 구체화
### 구현

- [../operations/collaboration-rules.md](../operations/collaboration-rules.md)에 `누가 최근에 작업했는지`, `로컬과 원격 중 어느 쪽이 최신인지`, `안전하게 pull --rebase 하는 방법`을 구체적인 명령과 판별 기준까지 포함해 추가했다.
- `git log --format="%h %an %ad %s" --date=short -10`, `git rev-list --left-right --count HEAD...origin/master`, `git diff --stat HEAD..origin/master`, `git stash push -u` 같은 실사용 명령을 그대로 넣어 처음 보는 작업자도 바로 따라 할 수 있게 정리했다.
- [README.md](../../README.md) 협업 절차에도 시작 전에 최근 작업자와 로컬/원격 최신 여부를 먼저 확인하라는 안내와 핵심 명령을 추가했다.

### 변경 범위

- `docs`: `../operations/collaboration-rules.md`, `implementation-status.md`
- 루트 문서: `README.md`

### 남은 범위

- 팀에서 실제 사용하는 공유 채널 기준으로 `현재 작업 선언` 위치만 필요 시 문서에 추가

## 59. 2026-04-25 README 관리자 데모 계정 표기 보완
### 구현

- [README.md](../../README.md)의 `데모 로그인` 섹션에 빠져 있던 관리자 계정 `admin@bodeul.app / bodeul1234`를 추가했다.
- 기존 기준선 문서에는 관리자 계정이 있었지만, 저장소 첫 진입 문서인 README에는 누락돼 있어 팀원이 바로 확인할 수 있게 맞췄다.

### 변경 범위

- 루트 문서: `README.md`
- `docs`: `implementation-status.md`

### 남은 범위

- 없음

## 60. 2026-04-25 관리자 로그인 역할 선택 경로 보완
### 구현

- 다른 팀원이 관리자 계정 로그인 시 `선택한 사용자 유형과 계정 유형이 일치하지 않습니다.` 오류를 재현했고, 원인이 로그인 검증이 아니라 인증 UI에 관리자 역할 선택 경로가 없던 점임을 확인했다.
- [RoleSelectionActivity](../../app/src/main/java/com/example/bodeul/ui/auth/RoleSelectionActivity.java)와 [activity_role_selection.xml](../../app/src/main/res/layout/activity_role_selection.xml)에 관리자 카드와 선택 상태 바인딩을 추가해 관리자도 역할 힌트를 `ADMIN`으로 넘길 수 있게 정리했다.
- [LoginActivity](../../app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java)에서 관리자 역할을 고정 로그인 역할로 처리하고, 관리자 진입에서는 회원가입 전환과 소셜 로그인 버튼을 숨기며 이메일 로그인만 허용하도록 보완했다.
- [activity_login.xml](../../app/src/main/res/layout/activity_login.xml), [strings.xml](../../app/src/main/res/values/strings.xml)에 관리자 로그인 전용 문구와 관리자 역할용 뷰 ID를 추가했다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/ui/auth`: `RoleSelectionActivity.java`, `LoginActivity.java`
- `app/src/main/res/layout`: `activity_role_selection.xml`, `activity_login.xml`
- `app/src/main/res/values`: `strings.xml`
- `docs`: `implementation-status.md`

### 남은 범위

- 현재 작업 환경에는 연결된 `adb` 디바이스가 없어 실제 단말 수동 로그인까지는 이번 턴에서 재검증하지 못했다.
- 팀원은 최신 `master`를 당겨서 관리자 카드 선택 후 `admin@bodeul.app / bodeul1234`로 다시 확인하면 된다.

## 61. 2026-04-25 관리자 로그인 숨김 진입 전환
### 구현

- 공개 역할 선택 화면에 노출했던 관리자 카드는 일반 사용자 관점에서 불필요하게 관리자 진입 경로를 드러내므로 제거했다.
- [RoleSelectionActivity](../../app/src/main/java/com/example/bodeul/ui/auth/RoleSelectionActivity.java)와 [activity_role_selection.xml](../../app/src/main/res/layout/activity_role_selection.xml)을 정리해 역할 선택 화면에는 다시 `매니저`, `환자/보호자` 카드만 남겼다.
- 대신 역할 선택 화면 상단 로고를 `1.5초 안에 5회 탭`하면 관리자 로그인 화면으로 이동하는 숨김 진입을 추가했다.
- 관리자 로그인 화면 자체는 계속 [LoginActivity](../../app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java)의 `ADMIN` 고정 모드를 사용하며, 이메일 로그인만 허용하고 회원가입/소셜 로그인 노출은 막아 일반 사용자 플로우와 분리했다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/ui/auth`: `RoleSelectionActivity.java`
- `app/src/main/res/layout`: `activity_role_selection.xml`
- `app/src/main/res/values`: `strings.xml`
- `docs`: `implementation-status.md`

### 남은 범위

- 실제 단말에서는 역할 선택 화면 로고를 5회 탭해 관리자 로그인으로 들어가는 동작만 한 번 더 눌러서 확인하면 된다.
- 관리자 권한 자체의 보안 판단은 숨김 진입이 아니라 기존 `Auth + users/{uid}.role == ADMIN + Firebase 권한 규칙`이 계속 담당한다.

## 62. 2026-05-04 관리자 웹 인증/심사 계약 정리
### 구현

- `admin-web` 브랜치의 관리자 웹이 `localStorage` 플래그만으로 로그인 상태를 유지하고 실제 Firebase 세션을 종료하지 않던 문제를 정리했다.
- [admin-web/firebase.ts](https://github.com/bodeul110/bodeul-admin-web/blob/master/firebase.ts)에서 `auth` 인스턴스를 함께 내보내고, [admin-web/src/App.tsx](https://github.com/bodeul110/bodeul-admin-web/blob/master/src/App.tsx)는 `onAuthStateChanged`로 실제 관리자 세션을 검증하도록 바꿨다.
- 로그인 후에는 `users/{uid}.role == ADMIN`을 다시 확인하고, 관리자가 아니면 즉시 `signOut()` 처리하도록 보강했다.
- 로그아웃도 `localStorage` 대신 실제 Firebase Auth `signOut()`을 호출하도록 수정했다.
- 매니저 승인/반려 저장은 기존 앱 계약에 맞춰 `managerDocumentStatus`, `managerDocumentReviewNote`, `managerDocumentReviewedAt`, `managerDocumentReviewedByName`, `managerDocumentHistory`를 함께 저장하도록 맞췄다.
- 아직 Firebase Storage가 연결되지 않았으므로, 관리자 웹에서는 서류 원본 미리보기 대신 `제출 요약 + 체크리스트` 기준 심사임을 명시했다.

### 변경 범위

- `admin-web`: `firebase.ts`, `src/App.tsx`
- `docs`: `implementation-status.md`

### 남은 범위

- Firebase Storage 연결 후 서류 원본 미리보기와 체크리스트를 실제 업로드 파일 기준으로 묶어야 한다.
- 웹 번들 크기가 `500kB` 경고를 넘기므로, 관리자 웹을 실제 배포 단계로 가져갈 때는 코드 스플리팅을 검토해야 한다.

## 63. 2026-05-04 관리자 웹 서류 Storage 미리보기 연동
### 구현

- [admin-web/firebase.ts](https://github.com/bodeul110/bodeul-admin-web/blob/master/firebase.ts)에 `storage` 인스턴스를 추가하고, [admin-web/src/App.tsx](https://github.com/bodeul110/bodeul-admin-web/blob/master/src/App.tsx)는 매니저 심사 모달에서 `Storage` 원본을 직접 읽어 미리보기 하도록 확장했다.
- 관리자 웹은 `users/{uid}.managerDocumentFiles` 메타데이터가 있으면 해당 `fullPath`를 우선 사용하고, 없으면 `manager-documents/{managerUserId}/{documentKey}/파일명` 폴더 규약을 기준으로 최신 파일을 탐색한다.
- 이미지 파일은 인라인 미리보기, PDF는 `iframe` 미리보기, 그 외 형식은 `원본 열기` 링크로 정리해 운영자가 서류 원본을 바로 검토할 수 있게 맞췄다.
- `ManagerApproval`은 더 이상 별도 Firestore 리스너를 만들지 않고, 상위 `App`이 구독한 매니저 목록과 파일 메타데이터를 그대로 받아 사용하도록 정리했다.
- [storage.rules](../../storage.rules), [firebase.json](../../firebase.json)에 `manager-documents/{managerUserId}/{documentKey}/{fileName}` 경로 규칙을 추가해, 관리자 읽기 / 본인 매니저 쓰기 정책을 저장소 설정으로 버전 관리하게 바꿨다.

### 변경 범위

- `admin-web`: `firebase.ts`, `src/App.tsx`
- Firebase 설정: `firebase.json`, `storage.rules`
- `docs`: `implementation-status.md`, `../architecture/data-api.md`, `../operations/firebase/setup.md`

### 남은 범위

- Android 매니저 앱에는 아직 실제 파일 업로드 UI와 `managerDocumentFiles` 메타데이터 저장이 없다. 현재 관리자 웹은 메타데이터가 없을 때 폴더 규약만으로 파일을 찾는다.
- `storage.rules`는 저장소 파일로 정리만 된 상태이므로, 실제 Firebase 프로젝트에는 별도 배포가 필요하다.
- 관리자 웹 번들 크기 `500kB` 경고는 그대로 남아 있어, 배포 단계로 가져갈 때는 코드 스플리팅을 검토해야 한다.

## 64. 2026-05-04 storage.rules 실제 배포
### 구현

- [storage.rules](../../storage.rules)를 Firebase 프로젝트 `bodeul-dev`에 실제 배포했다.
- `firebase deploy --only storage --project bodeul-dev --non-interactive` 명령으로 Storage Rules 컴파일과 릴리스를 확인했다.
- 관리자 웹이 사용하는 `manager-documents/{managerUserId}/{documentKey}/{fileName}` 경로 규칙이 이제 콘솔 설정이 아니라 배포된 Storage Rules 기준으로 적용된다.

### 변경 범위

- Firebase Storage 프로젝트 설정
- `docs`: `implementation-status.md`

### 남은 범위

- 매니저 앱에 실제 파일 업로드와 `managerDocumentFiles` 메타데이터 저장을 붙여야 관리자 웹이 폴더 탐색 대신 명시 경로를 우선 사용할 수 있다.
- 필요하면 Storage에 기준선 테스트 파일을 올려 관리자 웹 미리보기를 실데이터로 한 번 더 검증해야 한다.

## 65. 2026-05-04 storage.rules 권한 범위 축소
### 구현

- Firebase Rules API로 `projects/bodeul-dev/releases/firebase.storage/bodeul-dev.firebasestorage.app` 릴리스를 직접 조회해, 원격 Storage 규칙이 로컬과 동일한 상태로 배포돼 있음을 먼저 확인했다.
- [storage.rules](../../storage.rules)에 `currentUserExists()`, `isManager()`, `isAllowedDocumentKey()`를 추가해 권한 범위를 좁혔다.
- 이제 `manager-documents/{managerUserId}/{documentKey}/{fileName}` 경로는 아래 조건으로만 접근된다.
  - 읽기: 관리자 전체 또는 본인 매니저
  - 쓰기: 본인 매니저 + 허용된 `documentKey(idCard, license, criminalRecord)`만 가능
- 수정 후 `firebase deploy --only storage --project bodeul-dev --non-interactive`로 다시 배포했고, Firebase Rules API로 새 ruleset 반영까지 재확인했다.

### 변경 범위

- Firebase 설정: `storage.rules`
- `docs`: `implementation-status.md`

### 남은 범위

- Android 매니저 앱에 실제 업로드를 붙일 때, Storage 경로와 `managerDocumentFiles` 메타데이터 저장 규약을 같은 기준으로 맞춰야 한다.
- 필요하면 실제 매니저 계정으로 업로드/관리자 계정으로 읽기까지 권한 시나리오를 한 번 더 실측 검증하면 된다.
## 66. 2026-05-04 매니저 앱 원본 서류 업로드 연동
### 구현

- [ManagerProfileActivity](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileActivity.java)에서 `원본 파일 업로드` 버튼과 SAF 문서 선택 흐름을 추가했다.
- 업로드 대상은 `신분증`, `자격증`, `범죄경력 조회서` 3종으로 제한하고, 현재 선택 가능한 MIME은 `image/jpeg`, `image/png`, `image/webp`로 제한한다.
- [FirebaseManagerDocumentStorageUploader](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentStorageUploader.java), [MockManagerDocumentStorageUploader](../../app/src/main/java/com/example/bodeul/data/mock/MockManagerDocumentStorageUploader.java)를 추가해 Storage 업로드와 목업 메타데이터 생성을 분리했다.
- [FirebaseManagerRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java), [MockManagerRepository](../../app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java), [MockBodeulRepository](../../app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java)에 `managerDocumentFiles` 메타데이터 저장 흐름을 추가했다.
- Firestore 저장 형식은 `managerDocumentFiles.{documentKey}`, `managerDocumentFilePaths.{documentKey}`, 레거시 경로 필드(`managerIdCardStoragePath` 등)를 함께 갱신하도록 맞췄다.
- 매니저 내 페이지 문서 카드에는 원본 파일 요약 라인을 추가해서 업로드 여부와 최신 파일명을 바로 볼 수 있게 했다.
- [MockBodeulRepositoryTest](../../app/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java)에 업로드 메타데이터 저장 후 심사 상태 초기화와 파일명 반영을 검증하는 테스트를 추가했다.

### 변경 범위

- `app`
  - `data`: `ManagerRepository`, `ManagerDocumentStorageUploader`, `ServiceLocator`, `MockBodeulRepository`
  - `data/firebase`: `FirebaseManagerRepository`, `FirebaseManagerDocumentStorageUploader`, `FirebaseAdminRepository`
  - `data/mock`: `MockManagerRepository`, `MockManagerDocumentStorageUploader`
  - `domain/model`: `ManagerHomeProfile`, `ManagerDocumentFileType`, `ManagerDocumentFileMetadata`
  - `ui/manager`: `ManagerProfileActivity`, `ManagerProfileCoordinator`, `ManagerHomePresentationFormatter`
  - `res`: `activity_manager_profile.xml`, `strings.xml`
  - `test`: `MockBodeulRepositoryTest`
- `docs`: `implementation-status.md`, `../architecture/data-api.md`, `../operations/firebase/setup.md`

### 검증

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`

### 남은 범위

- 실제 매니저 계정으로 파일 업로드 후 관리자 웹 미리보기와 같은 경로를 읽는지 실데이터 시나리오를 한 번 더 확인해야 한다.
- Storage 업로드 성공 후 Firestore 저장이 실패했을 때 정리 정책(재시도 또는 고아 파일 정리)은 아직 운영 도구로 자동화하지 않았다.
## 67. 2026-05-04 매니저 서류 Storage 감사 도구 추가
### 구현

- [check-manager-document-storage.js](../../tools/firebase/check-manager-document-storage.js)를 추가해 `users/{uid}.managerDocumentFiles`, `managerDocumentFilePaths`, 레거시 경로 필드와 `manager-documents/` 실제 Storage 객체를 비교하도록 했다.
- [seed-manager-document-storage-sample.js](../../tools/firebase/seed-manager-document-storage-sample.js)를 추가해 `manager@bodeul.app` 기준 샘플 PNG 3종을 업로드하고 같은 경로를 Firestore 메타데이터에 반영하도록 했다.
- 공용 도구 [firebase-toolkit.js](../../tools/firebase/lib/firebase-toolkit.js)에 Storage 조회/목록/업로드 API와 Firestore `updateMask.fieldPaths` 기반 부분 업데이트를 추가했다.
- 샘플 업로드 직후 매니저 사용자 문서 일부 필드가 누락되는 문제가 확인돼, `patchDocumentFields()`를 부분 업데이트로 고친 뒤 `manager@bodeul.app` 사용자 문서의 `name/email/phone/role/provider/providerUserId`를 복구했다.
- 실제 Firebase 검증 결과 `manager@bodeul.app` 기준 참조 파일 3건, 일치 객체 3건, 누락 0건, 경로 불일치 0건으로 확인했다.

### 변경 범위

- `tools/firebase`
  - `check-manager-document-storage.js`
  - `seed-manager-document-storage-sample.js`
  - `lib/firebase-toolkit.js`
  - `package.json`
- `docs`
  - `implementation-status.md`
  - `../operations/firebase/setup.md`
  - `../operations/firebase/tools.md`

### 검증

- `node --check tools/firebase/lib/firebase-toolkit.js`
- `node --check tools/firebase/check-manager-document-storage.js`
- `node --check tools/firebase/seed-manager-document-storage-sample.js`
- `npm run seed:manager-docs:dry-run`
- `npm run seed:manager-docs:apply`
- `npm run check:manager-storage -- --json`

### 남은 범위

- 실제 Android 매니저 앱에서 업로드한 파일을 관리자 웹에서 직접 열어보는 UI 시나리오는 아직 수동 확인이 남아 있다.
- 고아 파일 삭제는 도구 옵션으로만 열어두었고, 정식 운영 절차나 CI 자동 삭제로는 아직 연결하지 않았다.

## 68. 2026-05-04 디버그 자동 업로드로 매니저 원본 파일 실기기 검증
### 구현

- [AutomationEntryActivity](../../app/src/debug/java/com/example/bodeul/debug/AutomationEntryActivity.java)에 `uploadDocumentType`, `uploadDocumentPath` extra를 추가해 디버그 자동 진입에서 매니저 원본 파일 업로드와 Firestore 메타데이터 저장까지 같은 앱 코드 경로로 실행할 수 있게 했다.
- 디바이스 파일 경로가 없거나 접근이 막히는 경우를 대비해 디버그 캐시에 1x1 PNG 샘플 파일을 생성해 업로드하도록 보강했다.
- 실기기에서 `MANAGER / MANAGER_PROFILE / idCard` 자동 업로드를 실행한 뒤 매니저 프로필 화면으로 복귀하는 것까지 확인했다.
- 실기기 화면 덤프 기준 `원본 파일` 항목이 `신분증: automation-idCard.png (2026-05-04 17:10)`으로 갱신된 것을 확인했다.

### 변경 범위

- `app/src/debug/java/com/example/bodeul/debug/AutomationEntryActivity.java`
- `implementation-status.md`

### 검증

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat installDebug --console=plain`
- `adb shell am start -S -W -n com.example.bodeul/com.example.bodeul.debug.AutomationEntryActivity --es role MANAGER --es screen MANAGER_PROFILE --ez forceSignIn true --es uploadDocumentType idCard --es uploadDocumentPath /no-such-file.png`
- `npm run check:manager-storage -- --json`

### 남은 범위

- 관리자 웹에서 같은 매니저 계정의 원본 파일 미리보기가 `automation-idCard.png` 기준으로 열리는지 수동 확인이 남아 있다.

## 69. 2026-05-04 관리자 웹 승인/미리보기 안정화
### 구현

- [App.tsx](https://github.com/bodeul110/bodeul-admin-web/blob/master/src/App.tsx)에서 Storage 메타데이터 경로가 끊긴 경우 폴더의 다른 파일로 자동 대체하지 않고 오류 상태로 멈추도록 수정했다.
- 문서 미리보기 로딩을 `Promise.allSettled` 기반으로 바꿔 일부 문서 미리보기 실패가 전체 모달 무한 로딩으로 이어지지 않도록 보강했다.
- 반려 버튼의 가짜 2단계 동작을 제거하고 즉시 반려 저장 로직만 타도록 정리했다.
- 매니저 Firestore 구독에 에러 콜백과 상단 오류 배너를 추가해 권한/네트워크 실패를 화면에서 바로 확인할 수 있게 했다.
- Firebase Console Storage 링크가 프로젝트/버킷 하드코딩 문자열에 의존하지 않도록 [firebase.ts](https://github.com/bodeul110/bodeul-admin-web/blob/master/firebase.ts) 설정값을 사용하게 바꿨다.

### 변경 범위

- `admin-web/src/App.tsx`
- `admin-web/firebase.ts`
- `implementation-status.md`

### 검증

- `npm --prefix admin-web run lint`
- `npm --prefix admin-web run build`
- `.\gradlew.bat assembleDebug --console=plain`

### 남은 범위

- 관리자 웹 번들 크기 경고(`>500kB`)는 그대로 남아 있어, 이후 코드 스플리팅이나 메뉴 단위 lazy loading 검토가 필요하다.

## 70. 2026-05-04 관리자 웹 번들 청크 분리
### 구현

- [vite.config.ts](https://github.com/bodeul110/bodeul-admin-web/blob/master/vite.config.ts)에 `manualChunks`를 추가해 `firebase`와 `react` 계열 의존성을 별도 vendor 청크로 분리했다.
- 관리자 웹 메인 청크를 줄여 초기 로드 파일을 가볍게 하고, 빌드 시 `500kB` 초과 경고가 다시 뜨지 않도록 정리했다.

### 변경 범위

- `admin-web/vite.config.ts`
- `implementation-status.md`

### 검증

- `npm --prefix admin-web run build`
- `.\gradlew.bat assembleDebug --console=plain`

### 남은 범위

- 현재는 vendor 분리까지 반영한 상태고, 이후 화면 수가 더 늘어나면 메뉴 단위 lazy loading까지 검토할 수 있다.

## 71. 2026-05-04 users 공개 검색 제거 1차
### 구현

- [FirebaseBookingRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java)에서 예약 연결 참여자 탐색을 직접 `users` 쿼리 대신 Firebase callable `resolveLinkedParticipant`로 전환했다.
- [FirebaseAuthRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java)에서 소셜 로그인 첫 가입 시 중복 이메일 확인을 callable `findSocialDuplicateEmailProvider`로 옮겼다.
- [functions/index.js](../../functions/index.js)에 두 callable 함수를 추가해 예약 연결 탐색과 소셜 중복 이메일 판별을 관리자 SDK 쿼리로 중계하도록 구현했다.
- [firestore.rules](../../firestore.rules)에서 `users` 컬렉션 규칙을 `get`/`list`로 분리해 비관리자 클라이언트의 `users` 목록 조회를 차단했다.
- 보안 작업 정리는 [../security/firestore-hardening.md](../security/firestore-hardening.md)에 이어서 기록했다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
- `functions/index.js`
- `firestore.rules`
- `../security/firestore-hardening.md`
- `implementation-status.md`

### 검증

- `node --check functions/index.js`
- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`
- `firebase deploy --only functions:resolveLinkedParticipant,functions:findSocialDuplicateEmailProvider,firestore:rules --project bodeul-dev --non-interactive`
- `guardian@bodeul.app` 기준 `resolveLinkedParticipant` 호출 성공
- `guardian@bodeul.app` 기준 `users` 이메일 쿼리 `PERMISSION_DENIED`
- `guardian@bodeul.app` 기준 `findSocialDuplicateEmailProvider` 호출 `PERMISSION_DENIED`

### 남은 범위

- 비관리자 사용자의 `users/{uid}` 직접 조회는 아직 허용하므로, 화면 요구사항이 정리되면 self/admin/참여 관계 기준으로 한 번 더 줄일 수 있다.

## 72. 2026-05-04 네이버 로그인 앱 시크릿 제거
### 구현

- [app/build.gradle.kts](../../app/build.gradle.kts)에서 `naver_client_secret` 리소스 주입을 제거했다.
- 네이버 로그인은 서버 중계 플로우가 준비될 때까지 비활성화하도록 `naver_login_enabled=false` 빌드 리소스를 추가했다.
- [BodeulApplication.java](../../app/src/main/java/com/example/bodeul/BodeulApplication.java)에서 네이버 SDK 초기화를 제거했다.
- [FirebaseAuthRepository.java](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java)에서 네이버 로그인 가능 여부 판단을 `R.bool.naver_login_enabled` 기준으로 바꾸고, 비활성화 상태 안내 메시지를 정리했다.
- [LoginActivity.java](../../app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java)에서 네이버 로그인 버튼을 숨기고, 코드 경로로 호출되더라도 안내 토스트만 표시하도록 막았다.

### 변경 범위

- `app/build.gradle.kts`
- `app/src/main/java/com/example/bodeul/BodeulApplication.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
- `app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java`
- `app/src/main/res/values/strings.xml`
- `implementation-status.md`

### 검증

- `.\gradlew.bat assembleDebug --console=plain`

### 남은 범위

- 네이버 로그인은 현재 앱에서 비활성화된 상태다. 다시 열려면 클라이언트 시크릿을 앱에 넣지 않는 서버 중계형 OAuth 흐름을 별도로 설계해야 한다.

## 73. 2026-05-04 users 직접 조회 self/admin 제한
### 구현

- [FirebaseBookingRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java)에서 예약 상세의 환자/보호자 프로필을 `appointmentRequests` 문서 스냅샷으로 복원하도록 바꾸고, 배정 매니저 정보는 callable `resolveAssignedManagerProfile`로 가져오게 정리했다.
- [FirebaseGuardianReportRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java)에서도 보호자 리포트의 매니저 프로필을 직접 `users/{uid}` 읽기 대신 callable로 전환했다.
- [FirebaseManagerRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java)에서 매니저 대시보드와 과거 이력의 환자/보호자 프로필을 요청 문서 스냅샷으로 구성하도록 바꿨다.
- [firestore.rules](../../firestore.rules)에서 `users` 직접 읽기 권한을 본인과 관리자만 허용하도록 축소했다.
- 보안 작업 정리는 [../security/firestore-hardening.md](../security/firestore-hardening.md)에 이어서 기록했다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
- `firestore.rules`
- `../security/firestore-hardening.md`
- `implementation-status.md`

### 검증

- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat testDebugUnitTest --console=plain`
- `firebase deploy --only functions:resolveAssignedManagerProfile,firestore:rules --project bodeul-dev --non-interactive`
- Firebase Web SDK 실계정 검증
  - `guardian@bodeul.app` 기준 `resolveAssignedManagerProfile(request-seed-progress)` 호출 성공
  - `guardian@bodeul.app` 기준 본인 `users/{uid}` 문서 읽기 성공
  - `guardian@bodeul.app` 기준 매니저 `users/{uid}` 문서 직접 읽기 `permission-denied`
  - `patient@bodeul.app` 기준 보호자 `users/{uid}` 문서 직접 읽기 `permission-denied`

### 남은 범위

- 타 사용자 프로필이 더 필요한 화면은 Firestore 규칙을 다시 넓히지 말고, 요청 문서 스냅샷이나 Functions 중계로 풀어야 한다.

## 74. 2026-05-04 매니저 서류 Storage 고아 파일 정리 흐름 보강
### 구현

- [check-manager-document-storage.js](../../tools/firebase/check-manager-document-storage.js)에 고아 파일 정리용 `dry-run -> apply` 흐름을 추가했다.
- `--delete-orphans`만으로는 삭제를 수행하지 않고, `--apply`가 함께 있을 때만 실제 삭제를 수행하도록 바꿨다.
- 누락 객체나 경로 불일치가 있으면 기본적으로 삭제를 차단하고, 예외 상황에서만 `--force`로 우회할 수 있게 했다.
- 대량 삭제 방지를 위해 기본 최대 삭제 수 20건 제한을 추가하고, `--max-delete`로만 조정하게 했다.
- [tools/firebase/package.json](../../tools/firebase/package.json)에 `cleanup:manager-storage:dry-run`, `cleanup:manager-storage:apply` 실행점을 추가했다.
- 운영 절차는 [../operations/firebase/tools.md](../operations/firebase/tools.md), [../operations/firebase/setup.md](../operations/firebase/setup.md)에 반영했다.

### 변경 범위

- `tools/firebase/check-manager-document-storage.js`
- `tools/firebase/package.json`
- `../operations/firebase/tools.md`
- `../operations/firebase/setup.md`
- `implementation-status.md`

### 검증

- `node tools/firebase/check-manager-document-storage.js --help`
- `npm run check:manager-storage -- --json`
- `npm run cleanup:manager-storage:dry-run`
- `npm run cleanup:manager-storage:apply`

### 남은 범위

- 실제 운영 기준으로는 정리 전 마지막 백업 생성 여부와 리포트 보관 기간만 팀 규칙으로 정하면 된다.

## 75. 2026-05-04 관리자 권한 QA 체크리스트 정리
### 구현

- [../operations/admin-access-qa-checklist.md](../operations/admin-access-qa-checklist.md)를 추가해 관리자 앱 숨김 진입, 관리자 웹 로그인, 매니저 서류 검토, 권한 실패 시나리오를 한 문서에서 점검할 수 있게 정리했다.
- [README.md](../../README.md) 문서 목록에 관리자 권한 QA 체크리스트 링크를 추가했다.
- [../operations/firebase/tools.md](../operations/firebase/tools.md)에 관리자 권한 검증 기준 문서 연결을 추가했다.

### 변경 범위

- `../operations/admin-access-qa-checklist.md`
- `README.md`
- `../operations/firebase/tools.md`
- `implementation-status.md`

### 검증

- 문서 정리 작업이라 별도 빌드 없이 내용과 링크 연결만 점검했다.

### 남은 범위

- 팀이 실제 QA를 돌리면서 실패 사례가 쌓이면 `실패 시 기록 항목` 아래에 반복되는 유형을 추가하면 된다.

## 76. 2026-05-04 보안 리뷰 최신화와 Storage 업로드 제약 강화
### 구현

- [../security/review-2026-04-29.md](../security/review-2026-04-29.md)를 현재 코드 기준으로 전면 최신화했다.
- 기존 지적 사항을 `해결`, `부분 해결`, `미해결`로 다시 분류하고, 런타임 앱 / 관리자 웹 / Firebase 운영 도구 기준 남은 위험을 재정리했다.
- [storage.rules](../../storage.rules)에 매니저 서류 업로드 제약을 추가했다.
  - 허용 MIME: `image/jpeg`, `image/png`, `image/webp`
  - 최대 크기: `10MB`
- [../operations/firebase/setup.md](../operations/firebase/setup.md)에 Storage 업로드 제약을 문서화했다.

### 변경 범위

- `../security/review-2026-04-29.md`
- `storage.rules`
- `../operations/firebase/setup.md`
- `implementation-status.md`

### 검증

- `firebase deploy --only storage --project bodeul-dev --non-interactive`
- `npm --prefix tools/firebase run check:manager-storage -- --strict`

### 남은 범위

- 현재 가장 큰 남은 위험은 `App Check 미도입`, `평문 필드 저장`, `운영 도구 토큰 처리`, `권한 최소화 검토`다.

## 77. 2026-05-04 AES 적용 범위 판단 정리
### 구현

- [../security/aes-scope-assessment.md](../security/aes-scope-assessment.md)를 추가해 `AES-256 이상의 보안` 요구를 현재 프로젝트 구조 기준으로 다시 해석했다.
- 실제 코드 기준으로 로컬 영속 저장 지점을 다시 확인했다.
  - [PermissionGuidePreferences.java](../../app/src/main/java/com/example/bodeul/ui/auth/PermissionGuidePreferences.java): 권한 안내 완료 여부만 저장
  - [ServiceLocator.java](../../app/src/main/java/com/example/bodeul/data/ServiceLocator.java): Firestore 디스크 캐시 비활성화
  - [FirebaseManagerDocumentStorageUploader.java](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentStorageUploader.java): 원본 서류를 로컬 복사 없이 바로 Storage 업로드
  - [admin-web/firebase.ts](https://github.com/bodeul110/bodeul-admin-web/blob/master/firebase.ts), [App.tsx](https://github.com/bodeul110/bodeul-admin-web/blob/master/src/App.tsx): 관리자 웹은 Firebase Auth 세션만 사용
- 결론은 `지금 릴리스 경로에는 앱이 직접 영속 저장하는 민감 비즈니스 데이터가 거의 없으므로, 전면 AES 도입보다 로컬 저장 금지 원칙과 App Check가 우선`이라는 점으로 정리했다.
- [../security/review-2026-04-29.md](../security/review-2026-04-29.md)에 AES 적용 범위 판단 링크를 추가했다.

### 변경 범위

- `../security/aes-scope-assessment.md`
- `../security/review-2026-04-29.md`
- `implementation-status.md`

### 검증

- 문서 정리 작업이라 별도 빌드 없이 코드 참조 경로와 판단 기준만 교차 확인했다.

### 남은 범위

- `오프라인 저장`, `문서 다운로드`, `자동저장` 기능이 추가되면 이 문서를 기준으로 `AES-256-GCM + Android Keystore` 적용 범위를 바로 구체화해야 한다.

## 78. 2026-05-04 App Check 1단계 적용 시작
### 구현

- Android 앱에 App Check 초기화 경로를 추가했다.
  - [BodeulApplication.java](../../app/src/main/java/com/example/bodeul/BodeulApplication.java)에서 시작 시 App Check를 설치한다.
  - `debug` 변형은 [app/src/debug/java/com/example/bodeul/firebase/AppCheckInstaller.java](../../app/src/debug/java/com/example/bodeul/firebase/AppCheckInstaller.java)에서 Debug provider를 사용한다.
  - `release` 변형은 [app/src/release/java/com/example/bodeul/firebase/AppCheckInstaller.java](../../app/src/release/java/com/example/bodeul/firebase/AppCheckInstaller.java)에서 Play Integrity provider를 사용한다.
- [app/build.gradle.kts](../../app/build.gradle.kts), [libs.versions.toml](../../gradle/libs.versions.toml)에 App Check 의존성을 추가했다.
- 관리자 웹에 선택적 App Check 초기화 경로를 추가했다.
  - [admin-web/src/appCheck.ts](https://github.com/bodeul110/bodeul-admin-web/blob/master/src/appCheck.ts)
  - [admin-web/src/main.tsx](https://github.com/bodeul110/bodeul-admin-web/blob/master/src/main.tsx)
  - [admin-web/firebase.ts](https://github.com/bodeul110/bodeul-admin-web/blob/master/firebase.ts)
  - `VITE_FIREBASE_APPCHECK_SITE_KEY`가 있을 때만 reCAPTCHA 기반 App Check를 활성화하고, 로컬 개발에서는 디버그 토큰을 허용한다.
- [functions/index.js](../../functions/index.js)에 callable 공통 옵션 `CALLABLE_FUNCTIONS_OPTIONS`를 추가하고, `ENABLE_APPCHECK_ENFORCEMENT=true`일 때만 `enforceAppCheck`를 켜게 정리했다.
- [../operations/firebase/setup.md](../operations/firebase/setup.md), [../security/review-2026-04-29.md](../security/review-2026-04-29.md)에 App Check 1단계 메모를 반영했다.

### 변경 범위

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/bodeul/BodeulApplication.java`
- `app/src/debug/java/com/example/bodeul/firebase/AppCheckInstaller.java`
- `app/src/release/java/com/example/bodeul/firebase/AppCheckInstaller.java`
- `admin-web/firebase.ts`
- `admin-web/src/appCheck.ts`
- `admin-web/src/main.tsx`
- `functions/index.js`
- `../operations/firebase/setup.md`
- `../security/review-2026-04-29.md`
- `implementation-status.md`

### 남은 범위

- Firebase Console에서 Android 앱 App Check 등록, 디버그 토큰 allowlist, 관리자 웹용 reCAPTCHA 사이트 키 등록이 필요하다.
- Firestore / Storage / Functions enforcement는 클라이언트 토큰이 안정화된 뒤 단계적으로 켜야 한다.

## 79. 2026-05-04 Firebase 운영 도구 OAuth secret 분리
### 구현

- [firebase-toolkit.js](../../tools/firebase/lib/firebase-toolkit.js)에서 refresh token 교환용 OAuth client secret 하드코딩을 제거했다.
- 이제 Firebase 운영 도구는 아래 우선순위로 OAuth client secret을 읽는다.
  - `FIREBASE_OAUTH_CLIENT_SECRET` 환경 변수
  - `local.properties`의 `firebaseOauthClientSecret`
- OAuth client id는 비밀값이 아니므로 기본값을 코드에 두고, 필요하면 `FIREBASE_OAUTH_CLIENT_ID` 또는 `local.properties`의 `firebaseOauthClientId`로 덮어쓸 수 있게 했다.
- [configure-actions-firebase.js](../../tools/github/configure-actions-firebase.js)는 `FIREBASE_TOKEN`이 refresh token일 때 `FIREBASE_OAUTH_CLIENT_SECRET`도 함께 GitHub Actions secret으로 반영하게 바꿨다.
- [.github/workflows/android-preflight.yml](../../.github/workflows/android-preflight.yml)에 `secrets.FIREBASE_OAUTH_CLIENT_SECRET` 환경 변수를 추가했다.
- [../operations/firebase/tools.md](../operations/firebase/tools.md), [../operations/firebase/setup.md](../operations/firebase/setup.md), [../security/review-2026-04-29.md](../security/review-2026-04-29.md)에 운영 도구 secret 분리 기준을 반영했다.

### 변경 범위

- `tools/firebase/lib/firebase-toolkit.js`
- `tools/github/configure-actions-firebase.js`
- `.github/workflows/android-preflight.yml`
- `../operations/firebase/tools.md`
- `../operations/firebase/setup.md`
- `../security/review-2026-04-29.md`
- `implementation-status.md`

### 남은 범위

- GitHub Actions 저장소 시크릿에 `FIREBASE_OAUTH_CLIENT_SECRET` 실제 값을 넣어 기존 refresh token 기반 운영 워크플로가 계속 동작하도록 맞춰야 한다.
- 장기적으로는 Firebase CLI refresh token 의존 자체를 줄이거나, 서비스 계정 기반 운영 경로를 분리하는 쪽이 더 낫다.

## 80. 2026-05-04 Android 권한 표면 최소화
### 구현

- [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml)에서 현재 기능이 실제로 쓰지 않는 위험 권한을 제거했다.
  - 제거 대상: 위치, 카메라, 외부 저장소 읽기, 블루투스, 전화, 연락처
- [PermissionGuideCatalog.java](../../app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideCatalog.java)를 현재 버전 안내 기준으로 바꿨다.
  - 시스템 권한을 실제로 요청하지 않음
  - 서버 중심 데이터 처리, 시스템 문서 선택기 사용, 추후 기능 추가 시 재요청 원칙만 안내
- [PermissionGuideActivity.java](../../app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideActivity.java) 주석과 [strings.xml](../../app/src/main/res/values/strings.xml) 문구를 현재 구조에 맞게 정리했다.
- [../security/review-2026-04-29.md](../security/review-2026-04-29.md)에 권한 표면 이슈 최신 상태를 반영했다.

### 변경 범위

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideCatalog.java`
- `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideActivity.java`
- `app/src/main/res/values/strings.xml`
- `../security/review-2026-04-29.md`
- `implementation-status.md`

### 남은 범위

- 추후 실제 카메라/위치/연락처 기능을 추가하면 그 시점에만 권한을 다시 선언하고, 기능/문구/검증 절차를 함께 추가해야 한다.

## 81. 2026-05-04 매니저 서류 업로드 사전 검증 보강
### 구현

- [ManagerDocumentUploadPolicy.java](../../app/src/main/java/com/example/bodeul/data/ManagerDocumentUploadPolicy.java)를 추가해 매니저 원본 서류 업로드 전에 파일 형식과 용량을 먼저 검사하도록 정리했다.
- [FirebaseManagerDocumentStorageUploader.java](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentStorageUploader.java), [MockManagerDocumentStorageUploader.java](../../app/src/main/java/com/example/bodeul/data/mock/MockManagerDocumentStorageUploader.java)에서 공통 정책을 사용해 JPEG, PNG, WebP 이미지만 허용하고, `10MB` 초과 파일은 업로드 전에 바로 차단한다. 일반 채팅 첨부의 PDF 정책은 이 변경과 분리해 유지한다.
- 서버 규칙에서 막히기 전에 앱에서 같은 기준으로 먼저 안내해, 매니저가 업로드 실패 이유를 바로 이해할 수 있게 맞췄다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/data/ManagerDocumentUploadPolicy.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentStorageUploader.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockManagerDocumentStorageUploader.java`
- `implementation-status.md`

### 남은 범위

- 현재는 파일 형식과 용량만 사전 검증한다. 추후 서류 종류별 추가 제약이 필요하면 같은 정책 객체에 확장하는 쪽이 맞다.

## 82. 2026-05-04 미사용 placeholder 경로 정리
### 구현

- 더 이상 어떤 화면에서도 쓰지 않는 `FeaturePlaceholderActivity`와 전용 레이아웃을 제거했다.
- 함께 남아 있던 미사용 안내 문구 `social_login_pending`, `toast_placeholder`도 정리해 현재 로그인/예외 흐름과 맞지 않는 dead resource를 줄였다.
- 실제 사용자 경로에는 이미 구체적인 상태 화면과 오류 메시지가 들어가 있으므로, 공용 placeholder를 유지할 이유가 없다고 판단했다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/ui/common/FeaturePlaceholderActivity.java`
- `app/src/main/res/layout/activity_feature_placeholder.xml`
- `app/src/main/res/values/strings.xml`
- `implementation-status.md`

### 남은 범위

- 이후 새 기능을 추가할 때는 공용 placeholder로 우회하지 말고, 실제 상태 패널 또는 역할별 오류 흐름 안에서 닫는 쪽이 맞다.

## 83. 2026-05-04 인증 예외 문구와 미사용 문의 문구 정리
### 구현

- [FirebaseAuthRepository.java](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java)에서 카카오/네이버/Firebase Functions 로그인 예외를 원문 메시지 그대로 노출하지 않고, 코드 기준 사용자 안내 문구로 정리했다.
- Functions가 직접 내려주는 `details.message`는 계속 우선 사용하되, 그 외에는 `INVALID_ARGUMENT`, `PERMISSION_DENIED`, `UNAUTHENTICATED`, `UNAVAILABLE` 기준으로 한국어 문구를 고정했다.
- 네이버 SDK 요청 실패도 내부 `errorDesc`를 그대로 붙이지 않고, 취소/네트워크/일반 실패로 나눠 안내한다.
- 더 이상 쓰지 않는 `manager_support_hero_body` 문구를 제거해 문의 화면 문자열도 현재 구조에 맞췄다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
- `app/src/main/res/values/strings.xml`
- `implementation-status.md`

### 남은 범위

- 실제 운영 시 소셜 로그인 공급자별 상세 실패 사유가 더 필요하면 서버 `details.message` 사전만 늘리고, SDK 원문 메시지를 그대로 노출하는 방향으로는 돌아가지 않는 편이 맞다.

## 84. 2026-05-04 네이버 로그인 deprecated SDK 경로 정리
### 구현

- [FirebaseAuthRepository.java](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java)에서 `NaverIdLoginSDK` 사용 경로를 `NidOAuth` 기준으로 교체했다.
- 네이버 로그인 요청, 토큰 조회, 로그아웃 모두 동일한 동작을 유지하면서 deprecated SDK 래퍼 의존만 제거했다.
- 이 변경으로 빌드 로그에 남던 `FirebaseAuthRepository` 경고 원인 하나를 현재 SDK 권장 경로로 맞췄다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAuthRepository.java`
- `implementation-status.md`

### 남은 범위

- 인증 영역의 나머지 경고는 없었고, 추후 SDK 버전 정책이 바뀌면 네이버 로그인 경로도 같은 방식으로 공급자 권장 API를 우선 적용하는 쪽이 맞다.

## 85. 2026-05-05 관리자 웹 민감정보 마스킹과 유휴 세션 종료
### 구현

- [admin-web/src/App.tsx](https://github.com/bodeul110/bodeul-admin-web/blob/master/src/App.tsx)에서 매니저 승인 목록의 이메일과 전화번호를 기본 마스킹 형태로 바꿨다.
- 상세 심사 모달에서는 기존처럼 원문을 유지해 실제 검토 업무는 그대로 가능하게 두고, 목록 화면의 기본 노출 범위만 줄였다.
- 관리자 웹은 15분 동안 입력이나 스크롤 등 활동이 없으면 자동으로 로그아웃되도록 세션 타이머를 추가했다.
- [../security/review-2026-04-29.md](../security/review-2026-04-29.md)에 관리자 웹 세션/마스킹 최신 상태를 반영했다.

### 변경 범위

- `admin-web/src/App.tsx`
- `../security/review-2026-04-29.md`
- `implementation-status.md`

### 남은 범위

- 현재 마스킹은 목록 화면에만 적용했다. 추후 관리자 상세 화면, 리포트 내보내기, 감사 로그까지 같은 기준을 확장할지 결정하면 된다.

## 86. 2026-05-05 프로젝트 문서 인덱스와 오래된 설명 정리
### 구현

- [../README.md](../README.md)를 추가해 문서 우선순위, 시작 순서, 문서 분류, 현재 저장소 구성 요약을 한 곳에 모았다.
- [README.md](../../README.md)의 문서 목록을 현재 구조에 맞게 다시 정리하고, 새로 들어온 작업자가 먼저 볼 순서를 명시했다.
- [admin-web/README.md](https://github.com/bodeul110/bodeul-admin-web/blob/master/README.md)를 실제 관리자 웹 구조와 현재 기능 기준으로 다시 작성했다.
- [../architecture/data-api.md](../architecture/data-api.md)에서 이미 구현된 후기 저장소 흐름과 매니저 원본 서류 업로드를 아직 미래 계획처럼 적어둔 문장을 현재형으로 수정했다.
- [../operations/firebase/tools.md](../operations/firebase/tools.md)에 현재 운영 도구 범위와 시작 흐름을 추가해 문서 역할을 더 분명하게 맞췄다.

### 변경 범위

- `../README.md`
- `README.md`
- `admin-web/README.md`
- `../architecture/data-api.md`
- `../operations/firebase/tools.md`
- `implementation-status.md`

### 남은 범위

- 문서 구조는 현재 기준으로 정리됐고, 이후에는 새 기능을 추가할 때 `implementation-status`와 관련 상세 문서를 같은 턴에 함께 갱신하는 규칙만 유지하면 된다.

## 87. 2026-05-05 최신 디자인 레퍼런스 검토 메모 정리
### 구현

- `design_refs/보들 가이드.zip`의 합본 이미지와 개별 화면 PNG를 현재 실기기 캡처, 관리자 웹 구현과 대조해 [design-reference-review-2026-05-05.md](../archive/design-reference-review-2026-05-05.md)로 정리했다.
- 이 문서에는 `확정 명세가 아닌 참고 디자인`이라는 전제를 두고, 현재 구현과 맞는 축, 바로 반영 가치가 있는 차이, 지금은 유지하는 것이 맞는 차이를 구분해 적었다.
- [README.md](../../README.md) 문서 목록에도 디자인 검토 메모를 추가해 나중에 화면 polish 작업을 할 때 바로 찾을 수 있게 맞췄다.

### 변경 범위

- `docs/archive/design-reference-review-2026-05-05.md`
- `README.md`
- `implementation-status.md`

### 남은 범위

- 이 검토는 화면 완성도 보강 우선순위를 정리한 단계다.
- 실제 적용은 `Firebase 연동 모드` 표시 축소, 권한 안내 polish, 매니저 서류 업로드 카드 정리 같은 항목부터 순차적으로 들어가면 된다.

## 88. 2026-05-05 사용자·매니저 환경 배지 숨김 정리
### 구현

- 사용자와 매니저 경로에서는 `Firebase 연동 모드`/`데모 데이터 기반` 배지를 기본으로 숨기고, 관리자 화면만 유지하도록 [EnvironmentModeBadgeHelper.java](../../app/src/main/java/com/example/bodeul/util/EnvironmentModeBadgeHelper.java)를 추가했다.
- [MainActivity.java](../../app/src/main/java/com/example/bodeul/MainActivity.java), [BookingActivity.java](../../app/src/main/java/com/example/bodeul/ui/booking/BookingActivity.java)에서 상단 모드 배지를 공통 helper 기준으로 바꿨다.
- 예약 상세/후속, 보호자 리포트, 매니저 홈/가이드/이력/내 페이지/문의 화면의 binder와 coordinator가 빈 모드 라벨을 받으면 배지를 자동으로 숨기도록 정리했다.
- 관리자 화면은 내부 운영 성격이 강하므로 기존 환경 배지를 그대로 유지했다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/util/EnvironmentModeBadgeHelper.java`
- `app/src/main/java/com/example/bodeul/MainActivity.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingActivity.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusBinder.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingFollowUpCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingFollowUpBinder.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportDashboardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerHomeCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerHomeDashboardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerHistoryCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerHistoryBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerSupportCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerSupportBinder.java`
- `implementation-status.md`

### 남은 범위

- 내부 테스트에서 환경 배지가 다시 필요해지면 관리자 화면과 같은 별도 조건부 노출 스위치를 추가하면 된다.
- 다음 화면 polish는 권한 안내 화면과 매니저 서류 업로드 카드 위계 정리 쪽이 우선이다.

## 89. 2026-05-05 권한 안내 화면 위계 polish
### 구현

- [activity_permission_guide.xml](../../app/src/main/res/layout/activity_permission_guide.xml)에 상단 요약 카드를 추가해 `필요한 시점에만 최소 권한 요청` 원칙을 먼저 보여주도록 정리했다.
- [item_permission_guide.xml](../../app/src/main/res/layout/item_permission_guide.xml)에 카드 주제 배지를 추가해 `데이터 보호`, `문서 업로드`, `추후 확장` 구분이 바로 보이게 바꿨다.
- [PermissionGuideItem.java](../../app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideItem.java), [PermissionGuideCatalog.java](../../app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideCatalog.java), [PermissionGuideItemBinder.java](../../app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideItemBinder.java)를 배지 텍스트까지 다루도록 확장했다.
- [strings.xml](../../app/src/main/res/values/strings.xml)에 권한 안내 요약 카드와 배지 문구를 추가했다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideCatalog.java`
- `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideItem.java`
- `app/src/main/java/com/example/bodeul/ui/auth/PermissionGuideItemBinder.java`
- `app/src/main/res/layout/activity_permission_guide.xml`
- `app/src/main/res/layout/item_permission_guide.xml`
- `app/src/main/res/values/strings.xml`
- `implementation-status.md`

### 남은 범위

- 권한 안내 화면은 현재 정책과 더 잘 맞게 정리됐고, 이후에는 실제 위치/카메라/연락처 기능이 들어올 때 권한 설명 카드만 같은 구조로 확장하면 된다.

## 90. 2026-05-05 매니저 내 페이지 서류 업로드 위계 polish
### 구현

- [activity_manager_profile.xml](../../app/src/main/res/layout/activity_manager_profile.xml)의 서류 영역을 `업로드 준비 안내 -> 원본 파일 현황 -> 요약/타임라인/검토 메모` 순서로 재구성했다.
- 업로드 CTA를 상단 강조 블록으로 올리고, 신분증/자격증/범죄경력 조회서를 각각 상태 카드로 분리해 현재 업로드 여부와 최근 업로드 시각이 바로 보이게 바꿨다.
- [ManagerProfileScreenModel](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileScreenModel.java), [ManagerProfileCoordinator](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileCoordinator.java), [ManagerProfileBinder](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileBinder.java)에 업로드 강조 문구와 원본 파일 카드 모델을 추가했다.
- [ManagerDocumentFileCardModel](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentFileCardModel.java), [ManagerDocumentFileCardBinder](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentFileCardBinder.java), [item_manager_document_file_status.xml](../../app/src/main/res/layout/item_manager_document_file_status.xml)로 파일 상태 표시를 분리했다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentFileCardModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentFileCardBinder.java`
- `app/src/main/res/layout/activity_manager_profile.xml`
- `app/src/main/res/layout/item_manager_document_file_status.xml`
- `app/src/main/res/values/strings.xml`
- `implementation-status.md`

### 남은 범위

- 매니저 내 페이지는 기본 위계 정리가 끝났고, 이후 남은 polish는 카드 간격과 문구 미세 조정 정도다.
- 실제 시연 전에는 관리자 웹에서 보이는 문서 상태와 매니저 앱 업로드 카드 문구가 과하게 어긋나지 않는지만 한 번 더 같이 확인하면 된다.

## 91. 2026-05-05 관리자 웹 심사 목록과 상세 모달 위계 polish
### 구현

- [admin-web/src/App.tsx](https://github.com/bodeul110/bodeul-admin-web/blob/master/src/App.tsx)의 매니저 서류 승인 화면 상단에 `전체 대상`, `요약 제출`, `원본 3종 완료`, `검토 메모 있음` 요약 카드를 추가했다.
- 심사 목록 표는 `매니저 / 연락처 / 서류 요약 / 원본 파일 / 상태 / 관리` 기준으로 재배치하고, 각 행에서 원본 파일 업로드 수와 최근 보완 메모 여부가 바로 보이도록 정리했다.
- 상세 모달은 상단 요약 띠를 추가해 현재 상태, 원본 파일 수, 체크리스트 진행, 요약 제출 상태를 먼저 보여주도록 바꿨다.
- 문서 탭은 파일명과 상태가 같이 보이는 카드형 선택 UI로 바꾸고, 우측 검토 패널은 진행 수치와 액션 버튼을 묶어 스크롤 중에도 판단 기준이 유지되게 정리했다.

### 변경 범위

- `admin-web/src/App.tsx`
- `implementation-status.md`

### 남은 범위

- 관리자 웹은 기본 심사 위계 정리가 끝났고, 이후 남은 polish는 테이블 반응형 처리나 검색/필터 추가 같은 확장 작업이다.
- 지금 단계에서는 실데이터 기준으로 목록 길이가 길어졌을 때도 스캔성이 유지되는지만 추가 QA 하면 된다.
## 92. 2026-05-05 내부 테스트 가이드와 운영 문서 연결 정리
### 구현

- [../operations/internal-test-guide.md](../operations/internal-test-guide.md)를 추가해 기획/내부 QA가 바로 사용할 테스트 계정, 더미 데이터, 역할별 테스트 순서를 한 문서에 정리했습니다.
- 샘플 예약 시나리오 `request-seed-requested`, `request-seed-progress`, `request-seed-completed`와 매니저 서류 샘플 상태를 내부 테스트 기준선으로 명시했습니다.
- 관리자 앱 숨김 진입 방식(역할 선택 화면 로고 1.5초 안에 5회 탭)과 관리자 웹 로컬 실행 주소를 내부 테스트 가이드에 함께 적었습니다.
- [README.md](../../README.md), [../README.md](../README.md), [../operations/firebase/setup.md](../operations/firebase/setup.md), [../operations/firebase/tools.md](../operations/firebase/tools.md)에 내부 테스트 가이드 링크와 운영 도구 실행 전제 조건을 반영했습니다.
- `check:state`, `check:readiness`, `preflight:local` 같은 운영 명령이 `firebaseOauthClientSecret` 또는 `FIREBASE_OAUTH_CLIENT_SECRET` 설정이 없으면 실행되지 않는 점을 문서에 분리해 적었습니다.

### 변경 범위

- `README.md`
- `../README.md`
- `../operations/firebase/setup.md`
- `../operations/firebase/tools.md`
- `implementation-status.md`
- `../operations/internal-test-guide.md`

### 남은 범위

- 기획측 내부 테스트가 실제로 시작되면 자주 나온 질문이나 실패 사례를 `../operations/internal-test-guide.md`에 FAQ 형태로 계속 누적하면 됩니다.
- Firebase 운영 도구를 직접 돌릴 개발자 PC에는 `local.properties`의 `firebaseOauthClientSecret` 또는 `FIREBASE_OAUTH_CLIENT_SECRET` 환경 변수를 별도로 맞춰야 합니다.

## 93. 2026-05-05 매니저 서류 등록 간호사/요양보호사 자격증 통합

### 구현

- 서류 등록 페이지에서 `간호사 자격증`과 `요양보호사 자격증`을 하나의 `간호사/요양보호사 자격증` 항목으로 통합했다.
- 필수 서류 체크 로직을 수정하여 두 자격증 중 하나만 업로드해도 `검토 요청`이 가능하도록 변경했다.
- 통합된 항목의 업로드 버튼 클릭 시, 업로드할 자격증 종류를 선택할 수 있는 다이얼로그를 추가했다.
- 매니저 홈의 서류 요약 정보에서도 통합된 항목명으로 표시되도록 `ManagerHomePresentationFormatter`를 업데이트했다.
- `ManagerDocumentRegistrationItemModel`의 필드명과 Getter 메서드 불일치로 인한 컴파일 에러를 수정했다.

### 변경 범위

- `ui/manager`: `ManagerDocumentRegistrationActivity`, `ManagerDocumentRegistrationCoordinator`, `ManagerDocumentRegistrationBinder`, `ManagerDocumentRegistrationItemModel`, `ManagerHomePresentationFormatter`
- `values`: `strings.xml`
- `docs`: `implementation-status.md`

### 남은 범위

- 없음

## 94. 2026-05-05 간호사 자격증 Storage 규칙과 관리자 웹 연동 보정

### 구현

- [storage.rules](../../storage.rules)에 `healthCertificate` 키를 추가해 간호사 자격증 파일도 실제 Firebase Storage 업로드 허용 대상에 포함했습니다.
- [admin-web/src/App.tsx](https://github.com/bodeul110/bodeul-admin-web/blob/master/src/App.tsx)에서 관리자 웹의 `자격증` 슬롯이 `license`뿐 아니라 `healthCertificate` 메타데이터와 Storage 폴더도 함께 읽도록 보정했습니다.
- 관리자 웹의 Storage 콘솔 링크와 경로 안내도 실제 메타데이터 경로나 `license / healthCertificate` 대체 경로를 기준으로 보이게 맞췄습니다.
- [../operations/firebase/setup.md](../operations/firebase/setup.md)와 [../features/manager-document-registration-2026-05-05.md](../features/manager-document-registration-2026-05-05.md)에 연동 상태를 반영했습니다.

### 변경 범위

- `admin-web/src/App.tsx`
- `storage.rules`
- `../operations/firebase/setup.md`
- `implementation-status.md`
- `../features/manager-document-registration-2026-05-05.md`

### 남은 범위

- 관리자 웹은 여전히 `자격증`을 단일 카드로 보여주므로, 간호사/요양보호사 자격증을 동시에 올렸을 때 두 파일을 모두 노출하는 UI는 아직 없습니다.

## 95. 2026-05-05 건강 자격증 점검과 매니저 내 페이지 업로드 통합

### 구현

- [check-manager-document-storage.js](../../tools/firebase/check-manager-document-storage.js)에서 `healthCertificate`를 점검 대상 문서 키에 포함해, 간호사 자격증 파일이 고아 파일로 잘못 분류되지 않도록 보정했다.
- [ManagerProfileActivity.java](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileActivity.java)에서 기존 내 페이지 업로드 경로를 새 서류 등록 규칙과 맞췄다. `자격증`을 누르면 `간호사 자격증`과 `요양보호사 자격증`을 한 번 더 고르게 하고, 선택 결과에 따라 `HEALTH_CERTIFICATE` 또는 `LICENSE`로 업로드한다.
- [ManagerProfileCoordinator.java](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileCoordinator.java)에서 원본 파일 카드도 새 규칙을 반영해 `간호사 자격증` 또는 `요양보호사 자격증` 중 실제 업로드된 파일을 우선 보여주도록 정리했다.
- [../architecture/data-api.md](../architecture/data-api.md), [../security/review-2026-04-29.md](../security/review-2026-04-29.md)에 `healthCertificate`를 현재 운영 기준 문서 키로 반영했다.

### 변경 범위

- `tools/firebase/check-manager-document-storage.js`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerProfileCoordinator.java`
- `../architecture/data-api.md`
- `../security/review-2026-04-29.md`
- `implementation-status.md`

### 남은 범위

- 관리자 웹은 여전히 `자격증`을 단일 슬롯으로 보여준다. 간호사 자격증과 요양보호사 자격증을 동시에 보관할 때 둘을 별도 카드로 분리해 보여줄지는 이후 운영 UX 결정이 필요하다.
- `tools/firebase` 점검 명령은 계속 `firebaseOauthClientSecret` 또는 `FIREBASE_OAUTH_CLIENT_SECRET` 설정이 있어야 실계정 검증까지 수행할 수 있다.

## 96. 2026-05-22 최신 기능설명서 기준 문서 재정렬

### 구현

- `./local/보들_플랫폼_기능설명서.pdf`를 기준으로 문서 우선순위를 다시 맞췄다.
- [README.md](../../README.md), [../README.md](../README.md)에 최신 기능설명서를 최상위 기준으로 명시하고 문서 진입 순서를 정리했다.
- [../planning/screen-restructure-target.md](../planning/screen-restructure-target.md)를 최신 기능설명서의 20개 항목 기준으로 다시 정리하고, 현재 구현 상태를 `구현 완료`, `부분 구현`, `후속 설계`로 구분했다.
- [../planning/mvp-scope.md](../planning/mvp-scope.md)를 최신 기능설명서 기준 MVP 범위와 후속 범위로 다시 정리했다.
- [../architecture/overview.md](../architecture/overview.md)에 Android 앱, 관리자 웹, Firebase 운영 도구를 포함한 현재 아키텍처 경계를 최신 기능 축 기준으로 다시 정리했다.

### 변경 범위

- `README.md`
- `../README.md`
- `../planning/screen-restructure-target.md`
- `../planning/mvp-scope.md`
- `../architecture/overview.md`
- `implementation-status.md`

### 남은 범위

- 최신 기능설명서의 추가 메모인 AI 음성 정리, OCR 복약 비교, 건강정보 화면, 초과 시간 자동 정산은 아직 `후속 설계` 범위다.
- 지도 API, 실결제, 실시간 GPS/채팅, 약국 동행 상세 흐름은 계속 `부분 구현` 상태로 관리해야 한다.

## 97. 2026-05-22 최신 기능설명서와 피그마 전체 재점검

### 구현

- `docs/local/보들_플랫폼_기능설명서.pdf`와 당시 피그마 ZIP을 각각 다시 확인해 기준을 분리했다.
- 새 문서 [../design/feature-spec-figma-audit-2026-05-22.md](../design/feature-spec-figma-audit-2026-05-22.md)를 추가해 기능설명서의 20개 항목, 추가 기획 메모, GPS/지도 요구와 피그마 화면 보드 범위를 따로 정리했다.
- [README.md](../../README.md)와 [../README.md](../README.md)에 새 점검 문서 링크를 추가해, 기능 기준과 디자인 기준을 혼동하지 않게 진입 경로를 정리했다.

### 변경 범위

- `README.md`
- `../README.md`
- `implementation-status.md`
- `../design/feature-spec-figma-audit-2026-05-22.md`

### 남은 범위

- 기능 기준은 계속 기능설명서를 우선하고, 디자인 ZIP은 화면 polish 참고본으로 유지한다.
- 실시간 GPS/지도/안심 채팅/약국 지도/건강정보/AI-OCR 계열은 문서상 계속 `부분 구현 또는 후속 설계` 범위로 관리해야 한다.

## 98. 2026-05-22 기능설명서 항목별 구현 체크리스트 정리

### 구현

- 새 문서 [../design/feature-spec-gap-checklist-2026-05-22.md](../design/feature-spec-gap-checklist-2026-05-22.md)를 추가해 기능설명서의 20개 항목을 `완료`, `부분 완료`, `미구현`, `후속 설계`로 다시 잘랐다.
- 이 체크리스트는 [../planning/screen-restructure-target.md](../planning/screen-restructure-target.md)의 구조 정리와 [../design/feature-spec-figma-audit-2026-05-22.md](../design/feature-spec-figma-audit-2026-05-22.md)의 원문 재점검 결과를 바탕으로 작성했다.
- [README.md](../../README.md)와 [../README.md](../README.md)에 새 체크리스트 링크를 추가해, 기능 기준 gap을 바로 확인할 수 있게 정리했다.

### 변경 범위

- `README.md`
- `../README.md`
- `implementation-status.md`
- `../design/feature-spec-gap-checklist-2026-05-22.md`

### 남은 범위

- 기능설명서 기준 남은 큰 gap은 `미구현 화면`보다 `부분 완료 항목의 실제 연동`이다.
- 특히 지도 API, 실시간 GPS/채팅, 결제/PG, 약국 상세, 정산 규칙은 계속 별도 우선순위로 관리해야 한다.

## 99. 2026-05-22 동행 가이드 병원 지도 fallback 추가

### 구현

- [ManagerGuideMapActionModel](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapActionModel.java), [ManagerGuideMapActionBinder](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapActionBinder.java), [ManagerGuideMapFallbackLauncher](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapFallbackLauncher.java)를 추가해 동행 가이드의 외부 지도 fallback 액션을 화면 모델과 런처로 분리했다.
- [ManagerGuideCoordinator](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java)에서 병원명, 진료과, 만남 위치를 기준으로 `병원 안내도`, `만남 위치`, `인근 약국` 3개 fallback 액션을 조합하도록 확장했다.
- [activity_manager_guide.xml](../../app/src/main/res/layout/activity_manager_guide.xml)에 `병원 지도 fallback` 섹션을 추가했고, [ManagerGuideDashboardBinder](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java)가 동적으로 액션 카드를 렌더링하도록 바꿨다.
- [ManagerGuideActivity](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java)는 액션 클릭 시 외부 지도 앱, Google Maps 검색, 일반 웹 검색 순서로 실행만 담당하도록 정리했다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapActionModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapActionBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapFallbackLauncher.java`
- `app/src/main/res/layout/activity_manager_guide.xml`
- `app/src/main/res/layout/item_manager_guide_map_action.xml`
- `app/src/main/res/values/strings.xml`
- `implementation-status.md`

### 남은 범위

- 현재는 지도 API 대신 외부 앱/검색 기반 fallback만 제공한다.
- 보호자 예약 상세나 환자 화면까지 같은 fallback을 확장할지는 다음 단계에서 별도로 결정해야 한다.

## 100. 2026-05-22 약국 진행 요약과 완료 상태 보강

### 구현

- [CompanionSession](../../app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java)에 `pharmacySummary`, `pharmacyCompleted` 필드를 추가해 약국 단계 진행 메모와 완료 여부를 세션 상태로 같이 관리하도록 확장했다.
- [ManagerRepository](../../app/src/main/java/com/example/bodeul/data/ManagerRepository.java), [FirebaseManagerRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java), [MockManagerRepository](../../app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java), [MockBodeulRepository](../../app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java)에 약국 진행 저장/완료 토글 경로를 추가했다.
- [ManagerGuideActivity](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java), [ManagerGuideDashboardBinder](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java), [ManagerGuideCoordinator](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java), [activity_manager_guide.xml](../../app/src/main/res/layout/activity_manager_guide.xml)에 `약국 진행 요약` 입력과 `약국 단계 완료` 토글을 추가했다.
- [BookingStatusCoordinator](../../app/src/main/java/com/example/bodeul/ui/booking/BookingStatusCoordinator.java), [GuardianReportCoordinator](../../app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java)에 현장 약국 진행 요약과 단계 상태를 함께 노출하도록 반영했다.
- Firebase 세션 읽기/생성 경로([FirebaseBookingRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java), [FirebaseGuardianReportRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java), [FirebaseAdminRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java))도 같은 필드를 읽도록 맞췄다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java`
- `app/src/main/java/com/example/bodeul/data/ManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuidePresentationFormatter.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java`
- `app/src/main/res/layout/activity_manager_guide.xml`
- `app/src/main/res/values/strings.xml`
- `implementation-status.md`

### 남은 범위

- 현재는 약국 진행 상태를 텍스트 요약과 완료 여부로만 관리한다.
- 약국 지도 API, 복약 입력 구조화, 영수증/약품 목록 OCR은 계속 후속 설계 범위다.

## 101. 2026-05-22 환자 건강정보 화면 추가

### 구현

- [HealthInfoActivity](../../app/src/main/java/com/example/bodeul/ui/health/HealthInfoActivity.java), [HealthInfoCoordinator](../../app/src/main/java/com/example/bodeul/ui/health/HealthInfoCoordinator.java), [HealthInfoBinder](../../app/src/main/java/com/example/bodeul/ui/health/HealthInfoBinder.java), [HealthInfoScreenModel](../../app/src/main/java/com/example/bodeul/ui/health/HealthInfoScreenModel.java)를 추가해 환자/보호자가 최근 또는 진행 중인 예약 기준 건강 프로필을 읽는 전용 화면을 구현했다.
- [MainActivity](../../app/src/main/java/com/example/bodeul/MainActivity.java)에서 환자 홈의 보조 액션을 `건강정보` 화면으로 연결했고, 보호자는 기존처럼 리포트 동선을 유지했다.
- 화면은 예약에 이미 저장된 `건강 메모`, `복약 정보`, `이동 보조`, `동행 유형`, `선호 매니저`, `예약 연결 정보`를 읽기 전용으로 보여준다.
- [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml), [activity_health_info.xml](../../app/src/main/res/layout/activity_health_info.xml), [strings.xml](../../app/src/main/res/values/strings.xml)에 건강정보 화면 진입과 한국어 문구를 반영했다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/MainActivity.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoActivity.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoBinder.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/health/HealthInfoLineItem.java`
- `app/src/main/res/layout/activity_health_info.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- `../design/feature-spec-gap-checklist-2026-05-22.md`
- `../planning/screen-restructure-target.md`
- `implementation-status.md`

### 남은 범위

- 현재 건강정보 화면은 최근 또는 진행 중인 예약 문서를 기준으로 읽는다.
- 사용자 프로필에 독립된 건강정보 영속 저장, 병력/알레르기 구조화, 건강정보 편집 전용 화면은 후속 범위다.

## 102. 2026-05-22 예약 후속 정산 상태 확장

### 구현

- [AppointmentFollowUpSettlementStatus](../../app/src/main/java/com/example/bodeul/domain/model/AppointmentFollowUpSettlementStatus.java)에 `OVERTIME_REVIEW`, `REFUND_REVIEW`를 추가하고, 정산 후속 처리 상태를 더 세분화했다.
- [BookingFollowUpActivity](../../app/src/main/java/com/example/bodeul/ui/booking/BookingFollowUpActivity.java), [BookingFollowUpCoordinator](../../app/src/main/java/com/example/bodeul/ui/booking/BookingFollowUpCoordinator.java), [BookingPresentationFormatter](../../app/src/main/java/com/example/bodeul/ui/booking/BookingPresentationFormatter.java)에서 환자 후속 화면의 정산 선택, 저장, 요약 문구를 확장했다.
- [AdminOperationsCoordinator](../../app/src/main/java/com/example/bodeul/ui/admin/AdminOperationsCoordinator.java), [ManagerHistoryCoordinator](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerHistoryCoordinator.java)에서 같은 정산 상태를 운영 화면과 매니저 과거 이력에도 같은 기준으로 반영했다.
- 관련 문자열 리소스([strings_follow_up_status_extension.xml](../../app/src/main/res/values/strings_follow_up_status_extension.xml), [strings_admin_operation_extension.xml](../../app/src/main/res/values/strings_admin_operation_extension.xml), [strings_manager_history_extension.xml](../../app/src/main/res/values/strings_manager_history_extension.xml))를 추가해 화면별 문구를 분리했다.

### 변경된 범위

- `app/src/main/java/com/example/bodeul/domain/model/AppointmentFollowUpSettlementStatus.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingFollowUpActivity.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingFollowUpCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingPresentationFormatter.java`
- `app/src/main/java/com/example/bodeul/ui/admin/AdminOperationsCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerHistoryCoordinator.java`
- `app/src/main/res/values/strings_follow_up_status_extension.xml`
- `app/src/main/res/values/strings_admin_operation_extension.xml`
- `app/src/main/res/values/strings_manager_history_extension.xml`
- `app/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java`
- `../design/feature-spec-gap-checklist-2026-05-22.md`
- `implementation-status.md`

### 남은 범위

- 다음 묶음은 `예약 후속 정산 상태 확장`에 대한 실기기 검증과 운영 정책 정리다.
- 추가 정산 필요, 환불 검토, PG 처리 상태 같은 후속 운영 규칙은 계속 보강해야 한다.

## 103. 2026-05-22 실시간 위치 확인 화면과 예약 상태 액션 보강

### 구현

- [BookingStatusCoordinator](../../app/src/main/java/com/example/bodeul/ui/booking/BookingStatusCoordinator.java), [BookingStatusActionType](../../app/src/main/java/com/example/bodeul/ui/booking/BookingStatusActionType.java), [BookingStatusActivity](../../app/src/main/java/com/example/bodeul/ui/booking/BookingStatusActivity.java)에서 예약 상태 화면에 실시간 위치 확인 진입 액션을 추가했다.
- [BookingLiveLocationActivity](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java), [BookingLiveLocationCoordinator](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java), [BookingLiveLocationBinder](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationBinder.java), [BookingLiveLocationScreenModel](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationScreenModel.java)을 추가해 보호자용 위치 확인 화면을 만들었다.
- 동행 세션 모델과 화면 모델에 현재 위치, 마지막 공유 좌표, 위치 요약 정보를 연결해 예약 상태 화면과 위치 확인 화면이 같은 기준 데이터를 읽도록 맞췄다.
- 외부 지도 예외에 대비한 [BookingLiveLocationMapFallbackLauncher](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationMapFallbackLauncher.java)를 추가했다.
- 위치 화면 레이아웃과 액션 아이템([activity_booking_live_location.xml](../../app/src/main/res/layout/activity_booking_live_location.xml), [item_booking_live_location_map_action.xml](../../app/src/main/res/layout/item_booking_live_location_map_action.xml))을 함께 정리했다.

### 변경된 범위

- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusActionType.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingStatusActivity.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationBinder.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationMapActionModel.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationMapActionBinder.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationMapFallbackLauncher.java`
- `app/src/main/res/layout/activity_booking_live_location.xml`
- `app/src/main/res/layout/item_booking_live_location_map_action.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- `implementation-status.md`

### 남은 범위

- 다음 묶음은 `실시간 위치 확인 화면 + 외부 지도 fallback` 실기기 점검이다.
- 실시간 GPS 좌표 동기화 안정화, 지도 SDK 예외 처리, 위치 권한과 배터리 절감 정책은 별도 보강이 필요하다.

## 104. 2026-05-22 안심 채팅 기본 흐름 추가

### 구현

- [CompanionChatActivity](../../app/src/main/java/com/example/bodeul/ui/chat/CompanionChatActivity.java), [CompanionChatCoordinator](../../app/src/main/java/com/example/bodeul/ui/chat/CompanionChatCoordinator.java), [CompanionChatBinder](../../app/src/main/java/com/example/bodeul/ui/chat/CompanionChatBinder.java), [CompanionChatScreenModel](../../app/src/main/java/com/example/bodeul/ui/chat/CompanionChatScreenModel.java), [CompanionChatMessageItemModel](../../app/src/main/java/com/example/bodeul/ui/chat/CompanionChatMessageItemModel.java)을 추가해 안심 채팅 화면의 기본 구조를 만들었다.
- [CompanionSession](../../app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java), [CompanionChatMessage](../../app/src/main/java/com/example/bodeul/domain/model/CompanionChatMessage.java), [BookingRepository](../../app/src/main/java/com/example/bodeul/data/BookingRepository.java), [ManagerRepository](../../app/src/main/java/com/example/bodeul/data/ManagerRepository.java)에 채팅 메시지 저장/조회 모델을 추가했다.
- Firebase/Mock 저장소에 `chatMessages` 조회와 저장 흐름을 연결하고, [BookingLiveLocationActivity](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java)와 [ManagerGuideActivity](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java)에서 [CompanionChatActivity](../../app/src/main/java/com/example/bodeul/ui/chat/CompanionChatActivity.java)로 바로 이동할 수 있게 연결했다.
- [MockBodeulRepositoryTest](../../app/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java)로 기본 송수신 흐름을 검증했다.

### 변경된 범위

- `app/src/main/java/com/example/bodeul/domain/model/CompanionChatMessage.java`
- `app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java`
- `app/src/main/java/com/example/bodeul/data/BookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/ManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockBookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
- `app/src/main/java/com/example/bodeul/ui/chat/CompanionChatActivity.java`
- `app/src/main/java/com/example/bodeul/ui/chat/CompanionChatCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/chat/CompanionChatBinder.java`
- `app/src/main/java/com/example/bodeul/ui/chat/CompanionChatScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/chat/CompanionChatMessageItemModel.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`
- `app/src/main/res/layout/activity_companion_chat.xml`
- `app/src/main/res/layout/item_companion_chat_message.xml`
- `app/src/main/res/layout/activity_booking_live_location.xml`
- `app/src/main/res/layout/activity_manager_guide.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java`
- `implementation-status.md`

### 남은 범위

- 읽음 상태와 푸시 알림은 후속 범위로 남겨뒀다.
- 파일 첨부, 이미지/PDF 미리보기, GPS 위치 공유와의 추가 연계는 별도 묶음으로 진행한다.
## 105. 2026-05-22 카카오 지도 fallback 우선화

### 구현

- [KakaoMapExternalLauncher](../../app/src/main/java/com/example/bodeul/util/KakaoMapExternalLauncher.java)를 추가해 외부 지도 fallback 검색을 카카오맵 앱 -> 카카오맵 모바일웹 -> 카카오 지도 웹 링크 -> 일반 지도 검색 순서로 통일했다.
- [ManagerGuideMapFallbackLauncher](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapFallbackLauncher.java)와 [BookingLiveLocationMapFallbackLauncher](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationMapFallbackLauncher.java)가 공용 카카오 지도 런처를 사용하도록 바꿨다.
- [strings.xml](../../app/src/main/res/values/strings.xml)과 [../design/feature-spec-gap-checklist-2026-05-22.md](../design/feature-spec-gap-checklist-2026-05-22.md)에 지도 fallback이 카카오 기준이라는 점을 반영했다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/util/KakaoMapExternalLauncher.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideMapFallbackLauncher.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationMapFallbackLauncher.java`
- `app/src/main/res/values/strings.xml`
- `docs/design/feature-spec-gap-checklist-2026-05-22.md`
- `docs/status/implementation-status.md`

### 남은 범위

- 이번 단계는 외부 지도 fallback 우선순위만 카카오 기준으로 맞춘 것이다.
- 실제 카카오 지도 API 내장 지도, 좌표 기반 마커, 실시간 GPS 스트림 표시는 아직 후속 범위다.

## 106. 2026-05-22 매니저 현재 위치 공유와 카카오 좌표 열기 연결

### 구현

- [CompanionSession](../../app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java)에 sharedLatitude, sharedLongitude, sharedLocationUpdatedAtMillis를 추가해 세션이 실제 위치 좌표와 갱신 시각을 같이 보관하도록 확장했다.
- [ManagerCurrentLocationSharer](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerCurrentLocationSharer.java)를 추가해 매니저 가이드 화면에서 기기 현재 위치를 한 번 읽어 위치 요약과 좌표를 함께 저장하도록 만들었다.
- [ManagerGuideActivity](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java), [activity_manager_guide.xml](../../app/src/main/res/layout/activity_manager_guide.xml), [ManagerGuideDashboardBinder](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java)에 현재 위치 공유 버튼과 위치 권한 요청 흐름을 추가했다.
- [FirebaseManagerRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java), [FirebaseBookingRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java), [FirebaseGuardianReportRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java), [FirebaseAdminRepository](../../app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java), [MockBodeulRepository](../../app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java), [MockManagerRepository](../../app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java)가 공유 좌표를 저장하고 읽도록 맞췄다.
- [BookingLiveLocationCoordinator](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java)가 좌표가 있을 때 kakaomap://look 링크를 우선 열어 보호자/환자 화면에서 공유 위치를 바로 카카오맵으로 보낼 수 있게 했다.
- [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml)에 ACCESS_FINE_LOCATION을 추가했고, [strings.xml](../../app/src/main/res/values/strings.xml), [../design/feature-spec-gap-checklist-2026-05-22.md](../design/feature-spec-gap-checklist-2026-05-22.md)에 현재 수준을 반영했다.

### 변경 범위

- app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java
- app/src/main/java/com/example/bodeul/data/ManagerRepository.java
- app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java
- app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerCurrentLocationSharer.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java
- app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java
- app/src/main/res/layout/activity_manager_guide.xml
- app/src/main/res/values/strings.xml
- app/src/main/AndroidManifest.xml
- ../design/feature-spec-gap-checklist-2026-05-22.md
- implementation-status.md

### 남은 범위

- 이번 단계는 매니저 1회 위치 공유 + 카카오 좌표 열기까지다.
- 연속 GPS 스트림 추적, 좌표 변경 이력, 푸시형 위치 알림, 카카오 지도 SDK 내장 마커 표시는 여전히 후속 범위다.
## 107. 2026-05-22 공유 위치 시각 노출과 가이드 직행 링크 보강

### 구현

- [ManagerGuideCoordinator](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java)가 공유 위치 메모나 좌표가 있을 때 현재 공유 위치 바로 열기 카드를 먼저 노출하고, 좌표가 있으면 카카오맵 look 링크를 직접 열도록 보강했다.
- [BookingLiveLocationCoordinator](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java)에 최근 위치 공유 시각 상태 줄을 추가해 보호자/환자 화면에서 마지막 위치 공유 시각을 바로 확인할 수 있게 했다.
- [GuardianReportCoordinator](../../app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java)에도 같은 시각 정보를 노출해 보호자 리포트 카드에서 위치 공유 최신성을 바로 판단할 수 있게 했다.
- [strings.xml](../../app/src/main/res/values/strings.xml)에 공유 위치 직행 카드와 위치 공유 시각 문구를 추가했다.

### 변경 범위

- app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java
- app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java
- app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java
- app/src/main/res/values/strings.xml
- implementation-status.md

### 남은 범위

- 현재는 최근 1회 좌표 공유 + 시각 노출 단계다.
- 연속 위치 스트림, 백그라운드 추적, 카카오 지도 SDK 내장 화면은 여전히 후속 범위다.
## 108. 2026-05-22 실시간 위치 수동 새로고침 보강

### 구현

- [BookingLiveLocationActivity](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java)와 [activity_booking_live_location.xml](../../app/src/main/res/layout/activity_booking_live_location.xml)에 `현재 위치 다시 불러오기` 버튼을 추가해 보호자/환자가 실시간 위치 확인 화면에서 수동 재조회할 수 있게 했다.
- [BookingLiveLocationBinder](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationBinder.java), [BookingLiveLocationScreenModel](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationScreenModel.java), [BookingLiveLocationCoordinator](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java)를 확장해 새로고침 버튼 문구를 화면 모델에서 같이 관리하도록 맞췄다.
- [GuardianReportActivity](../../app/src/main/java/com/example/bodeul/ui/report/GuardianReportActivity.java)와 [activity_guardian_report.xml](../../app/src/main/res/layout/activity_guardian_report.xml)에 `진행 현황 다시 불러오기` 버튼을 추가해 보호자 리포트도 같은 기준으로 재조회할 수 있게 했다.
- [strings.xml](../../app/src/main/res/values/strings.xml)에 위치 확인/보호자 리포트 새로고침 문구를 추가했다.

### 변경 범위

- app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java
- app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationBinder.java
- app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java
- app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationScreenModel.java
- app/src/main/java/com/example/bodeul/ui/report/GuardianReportActivity.java
- app/src/main/res/layout/activity_booking_live_location.xml
- app/src/main/res/layout/activity_guardian_report.xml
- app/src/main/res/values/strings.xml
- implementation-status.md

### 남은 범위

- 연속 GPS 좌표 스트림과 위치 변경 이력은 아직 없다.
- 카카오 지도 SDK 내장 지도와 백그라운드 위치 추적은 후속 범위다.

## 109. 2026-05-22 최신 디자인 세트 기준 문서와 UI 우선순위 재정립

### 구현

- `design_refs/local/`에 정리한 `bodeul_original_resolution_screens.zip`, `bodeul_split_screens/`, `index.csv`, `contact_sheet.png`를 기준으로 디자인 참조 구조를 다시 정리했다.

## 117. 2026-06-19 로컬 참조 자산 위치 정리

### 구현

- Git에 올리지 않는 로컬 전용 참조 자산 위치를 정리했다.
- 최신 기능설명서 PDF는 `docs/local/`로, 최신 디자인 원본과 분할 화면 세트는 `design_refs/local/`로 모았다.
- `.gitignore`에 `docs/local/*`, `design_refs/local/*`, 루트 `package-lock.json` 제외 규칙을 추가했다.
- 관련 문서 링크도 새 로컬 기준 경로에 맞게 정리했다.

### 변경 범위

- `.gitignore`
- `README.md`
- `design_refs/README.md`
- `docs/local/README.md`
- `design_refs/local/README.md`
- `../architecture/overview.md`
- `../README.md`
- `../design/reference-review-2026-05-22.md`
- `../design/feature-spec-figma-audit-2026-05-22.md`
- `../design/feature-spec-gap-checklist-2026-05-22.md`
- `../architecture/infrastructure.md`
- `../planning/mvp-scope.md`
- `../planning/screen-restructure-target.md`
- `docs/planning/README.md`
- `docs/archive/design-reference-review-2026-05-05.md`

### 남은 범위

- 로컬 참조 자산은 위치만 정리했고, 저장소에는 계속 포함하지 않는다.
- 팀 공용으로 공유해야 할 원본은 별도 링크나 버전 관리 가능한 추출본 정책을 정한 뒤 반영한다.

## 118. 2026-06-19 구형 디자인 ZIP 추적 제거

### 구현

- 더 이상 최신 기준으로 쓰지 않는 `design_refs/보들 가이드.zip`을 저장소 추적 대상에서 제거했다.
- 최신 디자인 기준은 계속 `design_refs/local/bodeul_original_resolution_screens.zip`과 `design_refs/local/bodeul_split_screens/`로 유지한다.
- `design_refs/README.md`도 현재 기준 자산과 구형 ZIP 처리 원칙에 맞게 다시 정리했다.

### 변경 범위

- `design_refs/보들 가이드.zip`
- `design_refs/README.md`
- `implementation-status.md`

### 남은 범위

- 이후 디자인 원본은 `design_refs/local/` 아래만 추가한다.
- 구형 자산은 archive 문서에서만 이력으로 남기고, 저장소 기준 자산 목록에서는 제외한다.
- [design_refs/README.md](../../design_refs/README.md)를 최신 원본/분할 화면/보조 참조/비사용 파일 기준으로 전면 정리했다.
- [../design/reference-review-2026-05-22.md](../design/reference-review-2026-05-22.md)를 새로 추가해 인증/공통, 환자 홈, 환자 진행 화면, 매니저 홈/가이드, 서류 등록 기준의 UI polish 우선순위를 다시 정했다.
- [../design/feature-spec-figma-audit-2026-05-22.md](../design/feature-spec-figma-audit-2026-05-22.md)를 최신 분할 화면 세트 기준으로 다시 작성해 기능 기준과 디자인 기준을 명확히 분리했다.
- [README.md](../../README.md), [../README.md](../README.md)의 디자인 문서 링크를 최신 메모 기준으로 교체했다.

### 변경 범위

- README.md
- design_refs/README.md
- ../design/reference-review-2026-05-22.md
- ../design/feature-spec-figma-audit-2026-05-22.md
- ../README.md
- implementation-status.md

### 남은 범위

- 기능 우선순위 자체는 바꾸지 않았다.
- 다음 UI polish는 `인증/공통 -> 환자/보호자 홈/진행 -> 매니저 홈/가이드 -> 서류/내 페이지 -> 설정` 순서로 진행한다.
- GPS, 결제, 정산, OCR/AI는 디자인보다 실제 연동 범위를 먼저 확정하는 축으로 유지한다.

## 110. 2026-05-22 서류 파일 미리보기와 재열기 연결

### 구현

- `ManagerDocumentFileMetadata`에 `previewUri`를 추가하고 `ManagerDocumentPreviewResolver` 계층을 도입해, 저장된 서류 메타데이터만으로 다시 열 수 있는 미리보기 URI를 해석하도록 정리했다.
- 매니저 서류 등록 화면에 `파일 열기` 버튼을 추가해 업로드된 PDF/이미지 원본을 바로 다시 확인할 수 있게 했다.
- 관리자 서류 검토 카드에 `제출 파일 보기`를 추가해 매니저가 올린 신분증/자격증/범죄경력 조회서를 목록에서 선택해 바로 열 수 있게 했다.
- 목업 모드에서는 SAF `content://` URI를 함께 저장하고, Firebase 모드에서는 Storage 경로를 다운로드 URI로 해석해 같은 화면 흐름으로 미리보기를 열도록 맞췄다.
- 관련 문자열과 서류 등록 문서를 업데이트했다.

### 변경 범위

- app/src/main/java/com/example/bodeul/domain/model/ManagerDocumentFileMetadata.java
- app/src/main/java/com/example/bodeul/data/ManagerDocumentPreviewResolver.java
- app/src/main/java/com/example/bodeul/data/ServiceLocator.java
- app/src/main/java/com/example/bodeul/data/mock/MockManagerDocumentStorageUploader.java
- app/src/main/java/com/example/bodeul/data/mock/MockManagerDocumentPreviewResolver.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerDocumentPreviewResolver.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java
- app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationActivity.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationBinder.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationCoordinator.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationItemBinder.java
- app/src/main/java/com/example/bodeul/ui/manager/ManagerDocumentRegistrationItemModel.java
- app/src/main/java/com/example/bodeul/ui/admin/AdminActivity.java
- app/src/main/java/com/example/bodeul/ui/admin/AdminManagerDocumentCoordinator.java
- app/src/main/java/com/example/bodeul/ui/admin/AdminManagerDocumentCardBinder.java
- app/src/main/java/com/example/bodeul/ui/admin/AdminManagerDocumentCardModel.java
- app/src/main/java/com/example/bodeul/util/DocumentPreviewLauncher.java
- app/src/main/res/layout/activity_manager_document_registration.xml
- app/src/main/res/layout/item_manager_document_registration.xml
- app/src/main/res/layout/item_admin_manager_document.xml
- app/src/main/res/values/strings.xml
- app/src/test/java/com/example/bodeul/MockBodeulRepositoryTest.java
- ../features/manager-document-registration-2026-05-05.md
- implementation-status.md

### 남은 범위

- 업로드한 파일 삭제와 Storage 정리 정책은 아직 없다.
- 과거 목업 데이터처럼 `previewUri`가 없는 기존 샘플 파일은 목업 모드에서 다시 열 수 없다.

# 2026-05-22 연속 위치 공유와 좌표 변경 이력 구현

## 구현

- `CompanionSession`, `CompanionLocationHistoryEntry`, `FirebaseCompanionSessionMapper`에 실시간 공유 상태와 좌표 변경 이력을 추가해 목업/Firebase가 같은 구조로 읽고 저장하도록 맞췄다.
- `ManagerLiveLocationTracker`, `ManagerLocationSupport`, `ManagerGuideActivity`를 추가·확장해 가이드 화면에서 실시간 위치 공유 시작/중지와 연속 GPS 업데이트 전송을 처리한다.
- `ManagerGuideCoordinator`, `BookingLiveLocationCoordinator`, `GuardianReportCoordinator`, `AdminOperationsCoordinator`에 실시간 공유 상태와 최근 좌표 이력 표시를 추가했다.
- `activity_manager_guide.xml`, `strings.xml`에 실시간 공유 제어 UI와 안내 문구를 반영했다.

## 변경 범위

- `app/src/main/java/com/example/bodeul/domain/model/CompanionSession.java`
- `app/src/main/java/com/example/bodeul/domain/model/CompanionLocationHistoryEntry.java`
- `app/src/main/java/com/example/bodeul/data/ManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/MockBodeulRepository.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseCompanionSessionMapper.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseManagerRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseGuardianReportRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseAdminRepository.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerLocationSupport.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerLiveLocationTracker.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerCurrentLocationSharer.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideDashboardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideScreenModel.java`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/report/GuardianReportCoordinator.java`
- `app/src/main/java/com/example/bodeul/ui/admin/AdminOperationsCoordinator.java`
- `app/src/main/java/com/example/bodeul/util/CompanionLocationDisplayHelper.java`
- `app/src/main/res/layout/activity_manager_guide.xml`
- `app/src/main/res/values/strings.xml`

## 남은 범위

- 카카오 지도 SDK 내장 지도/마커 연동은 아직 남아 있다.
- 백그라운드 위치 추적과 푸시형 위치 알림은 이번 범위에 포함하지 않았다.
- 위치 이력은 세션 문서 기준 최근 10건만 유지하며, 장기 보관 정책은 별도 설계가 필요하다.

## 111. 2026-06-05 프로젝트 아키텍처 개선 및 보안 픽스

## 구현

- **아키텍처 개선**: 기존 액티비티에 밀집되어 있던 비즈니스 로직(특히 실시간 위치 공유 등)을 `ViewModel` 기반 AAC 패턴으로 마이그레이션하여 화면 회전 시 상태 소실 및 크래시 문제를 해결했다.
- **백그라운드 위치 추적**: `ManagerLocationService` 포그라운드 서비스를 도입하여, 매니저가 화면을 끄거나 다른 앱을 사용할 때도 위치 추적이 중단되지 않도록 개선했다. `AndroidManifest.xml`에 `POST_NOTIFICATIONS` 권한을 추가하여 Android 13 이상에서 알림을 지원했다.
- **실시간 리스너 전환**: 수동 새로고침에 의존하던 동행 현황 조회를 Firestore `addSnapshotListener` 기반으로 변경하여 실시간 데이터 동기화를 구현했다 (`BookingRepository` 및 `FirebaseBookingRepository`).
- **보안 및 인증 규칙 강화**: `firestore.rules`를 수정하여 매니저 본인이 직접 승인 상태(`managerDocumentStatus`)를 조작하는 Self-Approval 어뷰징을 원천 차단했다. 또한 환자/보호자가 동행 세션에서 메시지를 보낼 때 발생하던 `PERMISSION_DENIED` 에러를 해결하기 위해 `isAppointmentParticipant` 권한을 추가했다.
- **버그 픽스**: 백그라운드 서비스 시작 시 `null` 콜백으로 인한 `NullPointerException` 등 크래시 문제를 해결했다.

## 변경 범위

- `app/src/main/AndroidManifest.xml`
- `firestore.rules`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideViewModel.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerLocationService.java`
- `app/src/main/java/com/example/bodeul/data/BookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/mock/MockBookingRepository.java`
- `app/src/main/java/com/example/bodeul/data/firebase/FirebaseBookingRepository.java`

## 남은 범위

- 리포트에서 지적받은 주요 아키텍처 및 보안 결함 수정 완료.

## 112. 2026-06-05 추가 업데이트 (카카오 맵 SDK 네이티브 연동)

### 구현

- 환자 실시간 동행 화면(`BookingLiveLocationActivity`)과 매니저 가이드 화면(`ManagerGuideActivity`)에 **카카오 네이티브 맵 SDK (v2.13.2)**를 삽입하여 앱 외부 이동 없이 지도를 볼 수 있게 개선했다.
- 백그라운드 위치 서비스나 실시간 동기화로 갱신되는 `sharedLatitude`, `sharedLongitude` 정보를 기반으로 카카오맵 마커 위치와 카메라 중심이 동적으로 변경되게 연동했다.
- 카카오맵 라이프사이클에 맞춰 Activity의 `onResume()`, `onPause()` 시점에 지도를 재개/정지 하도록 처리해 메모리 누수를 방지했다.

### 변경 범위

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/bodeul/BodeulApplication.java`
- `app/src/main/res/layout/activity_booking_live_location.xml`
- `app/src/main/res/layout/activity_manager_guide.xml`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java`
### 남은 범위

- 앱 해시 키가 카카오 플랫폼에 미등록되었을 때의 예외 처리 (현재는 미등록 시 지도가 안 나타날 수 있음)
## 113. 2026-06-05 추가 업데이트 (카카오맵 마커 및 추적 개선)

### 구현

- 카카오맵 SDK가 XML 벡터 드로어블을 직접 지원하지 않아 로고 마커가 비정상적으로 크거나 보이지 않던 문제를 수정했다.
- `ic_map_marker`(빨간 핀)와 `ic_tracking_dot`(파란 점) 벡터를 런타임에 `Bitmap`으로 변환해 카카오맵 `LabelStyle`에 적용하도록 로직을 개선했다.
- 위치 권한 요청 프로세스를 추가하고 권한이 허용되면 카카오맵의 `TrackingManager`를 활성화하여 실시간 내 위치(파란 점)를 표시하도록 기능을 완성했다.
- 외부 지도 앱 폴백 버튼을 유지하여 네이티브 지도에 문제가 있을 때도 기존 기능 사용에 지장이 없도록 안전장치를 마련했다.

### 변경 범위

- `app/src/main/res/drawable/ic_map_marker.xml`
- `app/src/main/res/drawable/ic_tracking_dot.xml`
- `app/src/main/java/com/example/bodeul/ui/booking/BookingLiveLocationActivity.java`
- `app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java`

### 남은 범위

- 없음

## 114. 2026-06-19 인프라 개요 문서 추가

### 구현

- 현재 런타임 기준 인프라 구성을 별도 문서로 정리했다.
- Android 앱, 관리자 웹, Firebase Auth/Firestore/Storage/Functions, `tools/firebase`, GitHub Actions의 역할과 경계를 한 번에 볼 수 있게 정리했다.
- Mock/Firebase 분기, Firestore 메모리 캐시 정책, App Check 준비 상태, 위치 공유/안심 채팅/카카오 지도 연동 위치도 문서에 포함했다.

### 변경 범위

- `../architecture/infrastructure.md`
- `README.md`
- `../README.md`

### 남은 범위

- 이 문서는 현재 `master` 기준 인프라 요약이다.
- App Check 실제 강제 적용, 운영 환경 분리, 배포 절차 변경이 생기면 같은 문서를 기준으로 갱신한다.

## 115. 2026-06-19 테스트 검증 및 개발환경 파일 정리

### 구현

- 최신 `master` 기준으로 `testDebugUnitTest`를 다시 실행해 단위 테스트까지 통과를 확인했다.
- 저장소에 잘못 포함된 `temp.txt`를 제거 대상으로 정리했다.
- 팀 공용으로 유지할 필요가 없는 개인 개발환경 파일을 `.gitignore` 기준으로 분리했다.
  - `.vscode/settings.json`
  - `.idea/deploymentTargetSelector.xml`
  - `.idea/deviceManager.xml`
  - `.idea/appInsightsSettings.xml`
  - `.idea/git_toolbox_prj.xml`
  - `.idea/easycode.ignore`
  - `.idea/easycode/`

### 변경 범위

- `.gitignore`
- `implementation-status.md`

### 남은 범위

- `.idea` 아래 다른 파일들은 현재 프로젝트 공용 설정인지 여부를 확인한 뒤 필요할 때만 추가 정리한다.
- 이번 정리는 장치 선택, 플러그인 상태, 개인 에디터 설정처럼 사용자별 편차가 큰 파일만 대상으로 했다.

## 116. 2026-06-19 문서 색인 구조 정리

### 구현

- 문서 경로를 한 번에 크게 옮기지 않고, 카테고리 디렉터리와 색인 `README.md`를 추가해 문서 진입 구조를 정리했다.
- `docs/architecture`, `docs/planning`, `docs/operations`, `docs/security`, `docs/design`, `docs/archive` 디렉터리를 추가했다.
- 루트 `README.md`와 `../README.md`를 카테고리 진입 기준으로 다시 정리했다.
- 구버전 디자인 검토 메모, 팀 작업 분해 초안, 기능설명서 추출본은 `docs/archive/`로 이동했다.

### 변경 범위

- `README.md`
- `../README.md`
- `docs/architecture/README.md`
- `docs/planning/README.md`
- `docs/operations/README.md`
- `docs/security/README.md`
- `docs/design/README.md`
- `docs/archive/README.md`
- `docs/archive/design-reference-review-2026-05-05.md`
- `docs/archive/team-task-breakdown.md`
- `docs/archive/보들_플랫폼_기능설명서.md`

### 남은 범위

- 링크 파급이 큰 본문 문서는 현재 경로를 유지했다.
- 앞으로 새 문서를 추가할 때는 먼저 카테고리 색인부터 맞추고, 실제 파일 이동은 링크 파급을 검토한 뒤 단계적으로 한다.

## 117. 2026-06-19 최신 피그마 ZIP 기준선 갱신

### 구현

- 새로 받은 `design_refs/보들 가이드.zip`을 `design_refs/local/보들 가이드.zip`으로 옮기고 `design_refs/local/latest_figma_2026-06-19/`에 해제했다.
- 최신 디자인 기준을 기존 `bodeul_original_resolution_screens.zip` 중심에서 `보들 가이드.zip` 해제본 중심으로 갱신했다.
- 기존 `bodeul_original_resolution_screens.zip`, `bodeul_split_screens/`는 보조 비교 세트로 격하했다.
- 디자인 비교 문서와 기준 문서 링크를 새 자산 기준으로 다시 맞췄다.

### 변경 범위

- `design_refs/README.md`
- `design_refs/local/README.md`
- `../design/reference-review-2026-05-22.md`
- `../design/feature-spec-figma-audit-2026-05-22.md`
- `implementation-status.md`

### 남은 범위

- 새 ZIP 기준 분할 화면 세트가 따로 필요하면 `latest_figma_2026-06-19/`를 기준으로 다시 생성한다.
- 현재는 보드형 PNG 세트만으로도 UI polish 우선순위 판단은 가능하다.

## 118. 2026-06-19 인증/공통 상단 요약 카드 추가

### 구현

- 역할 선택 화면 상단에 선택 경로와 다음 단계를 먼저 보여주는 요약 카드를 추가했다.
- 로그인 화면 상단에 현재 역할과 로그인/회원가입 모드에 따라 바뀌는 요약 카드를 추가했다.
- 역할 선택과 로그인이 같은 카드 레이아웃을 쓰도록 공용 요약 카드 뷰와 바인더/포매터를 분리했다.
- 권한 안내 화면은 현재 구조가 최신 피그마와 크게 어긋나지 않아 이번 묶음에서는 유지했다.

### 변경 범위

- `app/src/main/java/com/example/bodeul/ui/auth/AuthSummaryCardModel.java`
- `app/src/main/java/com/example/bodeul/ui/auth/AuthSummaryCardBinder.java`
- `app/src/main/java/com/example/bodeul/ui/auth/RoleSelectionSummaryFormatter.java`
- `app/src/main/java/com/example/bodeul/ui/auth/LoginSummaryFormatter.java`
- `app/src/main/java/com/example/bodeul/ui/auth/RoleSelectionActivity.java`
- `app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java`
- `app/src/main/res/layout/view_auth_summary_card.xml`
- `app/src/main/res/layout/activity_role_selection.xml`
- `app/src/main/res/layout/activity_login.xml`
- `app/src/main/res/values/strings.xml`
- `implementation-status.md`

### 남은 범위

- 인증/공통 polish 다음 순서는 환자/보호자 홈과 진행 화면 위계 정리다.
- 관리자 웹과 매니저 홈/가이드는 최신 피그마 기준으로 별도 묶음에서 이어서 조정한다.

## 119. 2026-06-19 환자/보호자 홈 액션 구획 정리

### 구현

- 환자/보호자 홈에서 실제 동작이 없던 두 번째 액션 줄을 제거했다.
- 홈 화면 섹션 순서를 `상단 상태 카드 -> 빠른 실행 -> 진행 로드맵 -> 최근 접수 -> 서비스 안내`로 다시 정리했다.
- 기존 기능 진입 경로는 유지하고, 실제로 누를 수 있는 카드만 남겨 화면 밀도를 줄였다.

### 변경 범위

- `app/src/main/res/layout/activity_main.xml`
- `implementation-status.md`

### 남은 범위

- 환자/보호자 진행 상세와 리포트 화면은 카드 위계와 정보 묶음을 더 다듬을 여지가 있다.
- 매니저 홈/가이드 polish는 별도 묶음으로 이어서 진행한다.

## 120. 2026-06-19 최신 피그마 누락 보드 반영

### 구현

- 최신 로컬 피그마 세트를 다시 확인해 문서에서 빠진 보드를 반영했다.
- `환자 홈 화면 (예약 완료 후)-1`, `Main`, `Body`, `보들 가이드.pdf`를 최신 디자인 기준 설명에 추가했다.
- `동행가이드1`~`12`를 단순 묶음이 아니라 연속 시퀀스 기준으로 다시 명시했다.

### 변경 범위

- `design_refs/README.md`
- `../design/reference-review-2026-05-22.md`
- `../design/feature-spec-figma-audit-2026-05-22.md`
- `implementation-status.md`

### 남은 범위

- 최신 피그마 기준에서 빠진 기능 축은 현재 문서보다 구현 쪽에 더 많지 않다.
- 다음 확인 포인트는 개별 보드 기준 UI polish 우선순위를 실제 화면에 얼마나 반영했는지다.

## 121. 2026-06-20 최근 상세 기록 분리

### 구현

- `implementation-status.md`는 손상 없이 복구 가능한 마지막 저손상 이력(섹션 120까지)을 기준으로 정리했다.
- 2026-06-19 이후 세부 배치 기록은 성격별 후속 문서로 분리해 참조하도록 정리했다.
  - `../reports/project-check-followup-2026-06-20.md`
  - `../reports/app-test-ready-2026-06-20.md`
  - `../reports/device-test-report-2026-06-20.md`
  - `../reports/admin-action-test-report-2026-06-20.md`
  - `../reports/encoding-cleanup-followup-2026-06-20.md`
  - `docs/planning/refactoring-roadmap-2026-06-20.md`
- 이후 완료 기록은 이 문서에 요약만 남기고, 화면/테스트/운영 상세는 개별 보고서 문서에 누적한다.

### 변경된 범위

- `implementation-status.md`

### 남은 범위

- 신규 작업이 생기면 `implementation-status.md`에는 완료 기준 요약만 추가한다.
- 상세 단계 로그와 점검 증적은 성격별 문서에서 계속 관리한다.

## 122. 2026-06-23 문서 디렉터리 구조 정리

### 구현

- `docs/README.md`를 문서 홈으로 만들고 기존 `document-guide.md` 역할을 병합했다.
- `docs/` 루트에는 색인만 남기고 실제 문서는 주제별 디렉터리로 이동했다.
- Firebase 운영 문서는 `../operations/firebase/`로 묶고, 날짜별 점검/테스트 기록은 `../reports/`로 분리했다.
- 기능 단위 메모는 `../features/`, 현재 구현 상태는 `../status/`, 보안/디자인/기획/아키텍처 문서는 각 전용 디렉터리로 정리했다.
- 깨진 README 추출 임시 파일 `temp.txt`를 제거했다.
- 이동된 문서의 Markdown 링크를 새 상대 경로 기준으로 갱신했다.

### 변경 범위

- `../../README.md`
- `../README.md`
- `../status/README.md`
- `../planning/README.md`
- `../architecture/README.md`
- `../operations/README.md`
- `../operations/firebase/README.md`
- `../security/README.md`
- `../design/README.md`
- `../features/README.md`
- `../reports/README.md`
- `../archive/README.md`
- `../status/implementation-status.md`
- `docs/` 하위 기존 루트 문서의 주제별 이동

### 검증

- Markdown 링크 존재 검사: `checked=543 broken=0`
- `git diff --check` 통과
- 문서 전용 변경이므로 `assembleDebug`는 실행하지 않았다.

### 남은 범위

- 현재 열린 문서 정리 잔여 범위는 없다.
- 새 문서가 생기면 `docs/README.md`와 해당 주제 디렉터리 `README.md`에 함께 연결한다.

## 123. 2026-06-23 문서 정합성 점검

### 구현

- 문서 홈, 루트 README, 상태/기획/아키텍처/운영/디자인/관리자 웹 문서를 현재 코드 구조와 다시 대조했다.
- 최신 구현 요약을 문서 상단에 모으고, 날짜별 이력은 당시 기록으로만 보도록 기준을 명확히 했다.
- 네이버 로그인 비활성 상태, 안심 채팅 첨부/푸시/읽음 구현 상태, Functions/관리자 앱/관리자 웹 분리 완료 상태를 최신 기준으로 맞췄다.
- 프로젝트 기준 문서가 아닌 로컬 보조 산출물과 손상된 기능설명서 Markdown 추출본을 기준 문서에서 제외했다.

### 변경 범위

- `../../README.md`
- `../README.md`
- `../status/README.md`
- `../status/implementation-status.md`
- `../architecture/overview.md`
- `../architecture/infrastructure.md`
- `../planning/mvp-scope.md`
- `../planning/screen-restructure-target.md`
- `../planning/refactoring-roadmap-2026-06-20.md`
- `../operations/firebase/setup.md`
- `../archive/README.md`
- `../reports/README.md`
- `../reports/document-alignment-2026-06-23.md`
- `../../design_refs/local/README.md`
- `../../admin-web/README.md`

### 검증

- Markdown 링크 존재 검사: `checked=48 broken=0`
- `.\gradlew.bat assembleDebug --console=plain`: 성공

### 남은 범위

- 날짜별 보고서와 `tools/firebase/reports/`의 생성 시점 로그는 이력 증적이므로 본문 정리 대상에서 제외했다.
- 이후 기능 변경이 생기면 이 문서 상단 최신 요약과 해당 상세 문서를 함께 갱신한다.

## 124. 2026-07-02 Issue 113 관리자 웹 병원 가이드 API 연결

### 구현

- API 서버에 관리자 웹 브라우저 호출용 CORS origin 설정과 `OPTIONS` preflight 응답을 추가했다.
- 관리자 웹에 `VITE_BODEUL_DATA_BACKEND=firebase|api`, `VITE_BODEUL_API_BASE_URL` 기준의 `bodeul-api` client를 추가했다.
- 관리자 웹 메뉴에 `병원 가이드` 검증 화면을 추가해 `GET /admin/hospital-guides?limit=50` 응답을 표시하도록 했다.
- 기본값은 `firebase` 모드로 유지해 기존 매니저 심사 화면의 Firestore 직접 조회와 승인 저장 흐름은 변경하지 않았다.
- Issue 113 구현 기록을 `../reports/issue-113-admin-web-api-connection-2026-07-02.md`에 남겼다.

### 변경 범위

- `../../api/src/config.ts`
- `../../api/src/server.ts`
- `../../api/src/server.test.ts`
- `../../api/README.md`
- `../../admin-web/.env.example`
- `../../admin-web/README.md`
- `../../admin-web/src/App.tsx`
- `../../admin-web/src/bodeulApi.ts`
- `../../admin-web/src/components/AdminShell.tsx`
- `../../admin-web/src/components/HospitalGuideApiPanel.tsx`
- `../../admin-web/src/vite-env.d.ts`
- `../architecture/admin-api-contract.md`
- `../reports/README.md`
- `../reports/issue-113-admin-web-api-connection-2026-07-02.md`

### 검증

- API 서버 CORS와 병원 가이드 계약 테스트를 추가했다.
- `npm --prefix admin-web run lint` 통과.
- `npm --prefix admin-web run build` 통과.
- `npm --prefix api run check` 통과.
- PR #121에서 `API Build`, `Admin Web Build`, `Android Preflight`, `CodeQL / analyze-javascript-typescript`가 통과했고 리뷰 승인 후 squash merge됐다.

### 남은 범위

- 운영/preview API 배포 URL이 정해지면 `BODEUL_API_ALLOWED_ORIGINS`에 관리자 웹 origin을 추가한다. 이 범위는 Issue #122에서 추적한다.
- Firestore 병원 가이드 결과와 PostgreSQL/API 응답 비교 기록은 Issue #123에서 추적한다.
- 매니저 서류 심사, 문의 조회 등 추가 read API 전환은 별도 이슈에서 다룬다.

## 125. 2026-07-02 인프라 문서 현재 상태 갱신

### 구현

- 인프라 문서를 `Firebase 인프라 유지 + Supabase PostgreSQL 전환 준비 + bodeul-api 서버 경계` 기준으로 갱신했다.
- `bodeul-api`의 Firebase ID token 검증, PostgreSQL client, 관리자 role 인가, 병원 가이드 read API, 관리자 웹 1차 연결 상태를 문서에 반영했다.
- #88과 #113은 완료 처리했고, 남은 환경 설정과 응답 비교 범위는 #122, #123으로 분리했다.
- API 배포 환경, App Check, 백업/복원, 비용 모니터링, Kakao Local REST API key, 관리자 웹 레포 분리 같은 남은 운영 이슈를 최신 목록으로 정리했다.

### 변경 범위

- `../architecture/README.md`
- `../architecture/infra-overview.md`
- `../architecture/infrastructure.md`
- `../architecture/postgres-api-boundary.md`
- `../architecture/postgres-operational-transition.md`
- `../operations/infrastructure-operations-baseline.md`
- `../status/implementation-status.md`

### 검증

- 문서 전용 변경이므로 Android, 관리자 웹, API 빌드는 실행하지 않았다.
- GitHub 이슈 상태 기준으로 #88, #113 종료와 #122, #123 생성 상태를 확인했다.
- `git diff --check` 검증 대상이다.

### 남은 범위

- #122에서 관리자 웹 API 환경변수와 CORS origin을 환경별로 확정한다.
- #123에서 병원 가이드 Firestore/API 응답 비교 기록을 남긴다.
- API 배포 실행 환경은 Oracle VM 또는 동등 실행 환경 기준으로 별도 결정이 필요하다.

## 126. 2026-07-04 Issue 122 관리자 웹 API 환경변수와 CORS 기준 정리

### 구현

- 관리자 웹 API 전환 환경값과 CORS allow-list 기준을 `../operations/admin-api-environments.md`에 정리했다.
- local, preview, production별 `VITE_BODEUL_DATA_BACKEND`, `VITE_BODEUL_API_BASE_URL`, `BODEUL_API_ALLOWED_ORIGINS` 기준을 표로 고정했다.
- GitHub Environment에 둘 관리자 웹 값과 API 서버 환경변수에 둘 값을 분리했다.
- CORS preflight 확인 명령, 실패 증상, rollback 기준을 문서화했다.
- 기존 관리자 API 계약, 관리자 웹 README, API README에서 새 운영 기준 문서로 연결했다.

### 변경 범위

- `../operations/admin-api-environments.md`
- `../operations/README.md`
- `../architecture/admin-api-contract.md`
- `../../admin-web/README.md`
- `../../api/README.md`
- `../reports/README.md`
- `../reports/issue-122-admin-api-environments-2026-07-04.md`
- `../status/implementation-status.md`

### 검증

- 문서 전용 변경이지만, API CORS preflight 계약이 유지되는지 `npm --prefix api run check`로 확인했다. API 테스트 50개가 모두 통과했다.
- 로컬 API 서버를 `127.0.0.1:18080`으로 임시 실행하고 `OPTIONS /admin/hospital-guides?limit=50` preflight가 HTTP `204`와 CORS 허용 헤더를 반환하는지 확인했다.
- `git diff --cached --check`로 문서 공백 오류가 없음을 확인했다.

### 남은 범위

- preview API URL이 확정되면 `admin-web-preview`와 API 서버 환경변수에 실제 값을 설정한다.
- production API URL이 확정되면 `admin-web-production`과 운영 API 서버 환경변수에 실제 값을 설정한다.
- 병원 가이드 Firestore/API 응답 비교는 Issue #123에서 진행한다.

## 127. 2026-07-10 관리자 웹 분리/API 실연동 상태 최신화

### 구현

- #140/#123 댓글 기준으로 Oracle `bodeul-api`, Supabase PostgreSQL, Firebase Admin 인증, 로컬 관리자 웹 API 모드, 실제 병원 가이드 API 응답 비교 통과 상태를 문서에 반영했다.
- Vercel preview는 #140 직접 완료 범위에서 제외됐고, production 전환이 아닌 팀 공유 화면 검증 후속 작업으로 분리한다고 정리했다.
- 실제 `bodeul-admin-web` 저장소 분리는 여전히 보류하고, #134 production 기준과 #135 실행 준비 이슈가 선행 조건임을 유지했다.
- #123 실제 배포 API 응답 비교 기록을 `../reports/issue-123-live-api-comparison-2026-07-08.md`에 추가했다.

### 변경 범위

- `../architecture/infra-overview.md`
- `../architecture/infrastructure.md`
- `../architecture/postgres-api-boundary.md`
- `../architecture/postgres-operational-transition.md`
- `../operations/admin-api-environments.md`
- `../operations/admin-web-repository-split.md`
- `../operations/infrastructure-operations-baseline.md`
- `../operations/postgres-operational-transition-runbook.md`
- `../reports/issue-123-live-api-comparison-2026-07-08.md`
- `../../admin-web/README.md`
- `../../api/README.md`

### 검증

- 문서 전용 변경이므로 Android, 관리자 웹, API 빌드는 실행하지 않는다.
- GitHub 이슈 댓글로 #123, #140의 2026-07-08 실연동 기록을 확인했다.
- `git diff --check`와 문서 링크 확인으로 검증한다.

### 남은 범위

- #123은 실제 배포 API 응답 비교 `passed` 반영 후 종료 또는 후속 분리 여부를 판단해야 한다.
- #140은 Vercel 또는 Firebase Hosting preview URL 기반 팀 공유 화면 검증을 후속 작업으로 분리해야 한다.
- #134에서 production Firebase project, Hosting site, Auth domain, App Check, live WIF 조건을 확정해야 한다.
- #135에서 실제 `bodeul-admin-web` 저장소 분리 실행 여부와 체크리스트를 이어간다.

## 128. 2026-07-10 관리자 웹 분리 저장소 bootstrap

### 구현

- `bodeul110/bodeul-admin-web` 저장소를 생성하고 `admin-web` 히스토리를 보존해 `master`에 push했다.
- 새 저장소 root 기준 README, AGENTS, Firebase Hosting 전용 `firebase.json`, build/preview deploy/CodeQL workflow, CODEOWNERS, Dependabot, PR 템플릿을 추가했다.
- 새 저장소 `admin-web-preview` GitHub Environment를 생성하고 원 저장소의 공개 variables를 복사했다.
- 새 저장소의 Environment secret은 GitHub에서 값을 읽을 수 없으므로 자동 복사하지 않고, 후속 이슈로 분리했다.
- 원 저장소 `admin-web/`은 아직 삭제하거나 freeze하지 않는다. source-of-truth 전환과 production live 기준은 #134와 #135 후속 판단에 따른다.

### 변경 범위

- `../operations/admin-web-repository-split.md`
- `../operations/admin-web-environments.md`
- `../status/implementation-status.md`
- `../../admin-web/README.md`

### 검증

- 새 저장소 로컬 검증: `npm ci`, `npm run lint`, CI placeholder 환경값 기반 `npm run build`
- 새 저장소 YAML 검증: `yq e '.' .github/workflows/admin-web.yml`, `yq e '.' .github/workflows/admin-web-preview-deploy.yml`, `yq e '.' .github/workflows/codeql.yml`
- 새 저장소 공백 검증: `git diff --check`
- 새 저장소 GitHub Actions: `Admin Web Build` workflow 성공

### 남은 범위

- 새 저장소 `admin-web-preview` Environment secret 등록
- GCP Workload Identity Provider가 `bodeul110/bodeul-admin-web` 저장소를 허용하는지 확인
- 새 저장소 `Admin Web Preview Deploy` 수동 실행 및 URL 확인
- production Firebase project, Hosting site, Auth domain, App Check, live WIF 조건 확정
- preview 검증 완료 전까지 원 저장소 `admin-web/` 삭제 또는 freeze 보류

## 129. 2026-07-16 Spring Core API App Check 관찰 경계 추가

### 구현

- Android Core API client가 발급 가능한 App Check token을 `X-Firebase-AppCheck` 헤더로 전달하도록 변경했다.
- Spring Core API에 `off`, `observe`, `enforce` 모드와 Firebase App Check JWT/JWKS 검증기를 추가했다.
- Cloud Run preview workflow는 `FIREBASE_PROJECT_NUMBER`를 확인하고 `BODEUL_APP_CHECK_MODE=observe`를 주입한다.
- token 원문 없이 판정, 검증된 app ID, 요청 경로만 기록한다.

### 선택 근거

- Java Admin SDK 9.10.0에는 App Check 검증 API가 없으므로 별도 proxy 대신 Spring Security JWT decoder를 사용한다.
- 구현 시점에는 `VALID` 요청이 0건이었으므로 enforce하지 않고 observe에서 정상 token을 먼저 확인하기로 했다.
- Firebase ID token과 PostgreSQL role 인가는 App Check와 독립적으로 유지한다.

### 검증

- `core-api/gradlew.bat check --console=plain`, 53개 테스트 실패 0
- `yq e '.' .github/workflows/core-api-preview-deploy.yml`
- Android `assembleDebug`, `testDebugUnitTest`, 43개 테스트 실패 0
- PR #193의 Android preflight, Core API CI, Android CodeQL 성공
- [Core API Preview Deploy #29518038972](https://github.com/bodeul110/Bodeul/actions/runs/29518038972) 성공
- Cloud Run `asia-northeast1` 리비전 `bodeul-core-api-preview-00007-8hk`, 트래픽 100%, observe 설정 확인
- health 200과 무인증 auth/place search 401 smoke test 통과

### 남은 범위

- Issue #190 Android debug/Play Integrity 실기기 `valid` 확인
- Issue #192 custom backend enforce 전환과 즉시 observe 롤백
- 관리자 Next.js 서버의 reCAPTCHA Enterprise와 App Check 검증은 관리자 웹 Issue #16에서 별도 진행
- 인증된 요청이 들어온 뒤 Cloud Logging에서 `valid`, `missing`, `invalid` 실제 판정 로그 확인

## 130. 2026-07-17 Android App Check debug 실검증

### 구현과 운영 설정

- API 34 x86_64 에뮬레이터에서 Kakao Map ARM 네이티브 라이브러리 부재로 앱이 시작 전에 종료되는 원인을 확인했다.
- PR #195에서 debuggable 빌드만 지도 SDK 초기화를 건너뛰고, release 빌드는 오류를 유지하도록 보완했다.
- Android debug provider가 발급한 token을 Firebase App Check allowlist에 비공개 등록했다.
- token 원문은 출력하거나 저장소에 기록하지 않았고 등록 후 로컬 임시 파일을 삭제했다.

### 검증

- `gradlew.bat assembleDebug testDebugUnitTest --console=plain --no-daemon --max-workers=1` 성공
- 에뮬레이터 앱 프로세스 생존, AndroidRuntime fatal 예외 없음, debug 지도 SDK 건너뛰기 경고 확인
- debug token 교환 성공, App Check token TTL 3,600초 확인
- 존재하지 않는 Firestore 문서 읽기 probe는 Rules에서 403, 데이터 쓰기 없음
- Cloud Monitoring에서 Android 앱 ID, `firestore.googleapis.com`, `ALLOW`, `VALID` 1건 확인
- Android provider와 debug allowlist 준비 게이트 통과
- Firebase 등록 SHA-256 1개가 local debug keystore와 일치하고 별도 release 후보가 0개임을 지문 원문 출력 없이 확인
- `app/build.gradle.kts`에 release signing 설정이 없음을 확인

### 남은 범위

- 팀 소유 release keystore와 Gradle signing 설정, Firebase release SHA-256 등록
- ARM 실기기에서 release Play Integrity token 발급 확인
- 로그인, 예약, 세션, 채팅 첨부, Core API 장소 검색 흐름 검증
- Core API Cloud Logging의 `app_check_verdict=valid` 확인
- Next.js 관리자 웹 provider와 debug token, preview `VALID` 요청 준비

## 131. 2026-07-17 ARM 실기기 주요 흐름 및 Core API App Check 검증

### 구현과 운영 설정

- Storage Rules의 Firestore 문서 접근 한도를 넘던 채팅 첨부 참여자 판정을 세션 직접 참여자 ID 기준으로 변경했다.
- 관리자 세션 생성은 `getAfter()` 예약 요청과 참여자 ID 일치를 검증하고, 생성 후 참여자 ID 변경을 금지했다.
- 기존 개발 세션 2건을 백업 후 backfill하고 Firestore/Storage Rules를 `bodeul-dev`에 배포했다.
- Firestore 세션 쿼리에 환자 또는 보호자 UID 조건을 추가해 Rules가 쿼리 전체의 참여자 범위를 증명할 수 있게 했다.
- Kakao Android 플랫폼 전용 Native App Key를 Git 제외 로컬 설정으로 이동하고 추적 파일의 실제 키를 제거했다.
- ARM 실기기 App Check debug token을 공식 REST API로 비공개 등록했다.

### 검증

- Samsung SM-S921N, Android 16 ARM64 debug APK 설치 성공
- 역할별 주요 화면과 채팅 첨부 결과 15건 통과, 경고 0건, 실패 0건
- 보호자 채팅 이미지 첨부의 Storage 업로드, Firestore 메시지 저장, 첨부 열기 확인
- Kakao Map 인증 HTTP 200, `onRenderViewSuccess`, 지도 타일과 위치 마커 렌더링 확인
- Core API 장소 검색 3건 모두 `app_check_verdict=valid`, HTTP 200 확인
- `npm --prefix tools/firebase run test:toolkit` 21건 통과
- `npm --prefix tools/firebase run test:rules` 7건 통과
- `gradlew.bat testDebugUnitTest assembleDebug --console=plain` 성공
- SM-S921N에서 `connectedDebugAndroidTest` 1건 통과
- 상세 결과는 [Issue 190 ARM 실기기 검증 기록](../reports/issue-190-arm-device-validation-2026-07-17.md)에 정리했다.

### 남은 범위

- 팀 소유 release keystore, Gradle signing, Firebase와 Kakao의 release SHA-256 등록
- ARM release 후보의 Play Integrity token 검증
- #192 Core API enforce 전환과 observe 롤백 재현
- Next.js 관리자 웹 reCAPTCHA Enterprise와 App Check custom backend 검증

## 132. 2026-07-17 관리자 서버 전환과 Node API 종료

### 구현과 운영 설정

- 관리자 웹 source of truth를 별도 `bodeul-admin-web` 저장소의 Next.js로 확정했다.
- Vercel Preview 전용 `bodeul_admin_service`를 활성화하고 SELECT 권한과 connection limit 5를 적용했다.
- Supabase Root CA를 명시해 TLS 인증서 검증을 유지했다.
- 메인 저장소의 `api/`, `admin-web/`, 관리자 전용 workflow와 Firebase Hosting 설정을 제거했다.
- Dependabot, CodeQL, Android Preflight와 인프라 문서를 현재 저장소 경계에 맞췄다.
- 기존 관리자 Hosting용 GitHub Environment, Firebase Hosting site, WIF provider, 배포 서비스 계정과 IAM binding을 제거했다.

### 검증

- 관리자 Preview 무인증 401, 비관리자 403, 관리자 200과 병원 가이드 조회 확인
- 임시 Firebase 사용자와 PostgreSQL row 삭제 후 잔여 0건 확인
- 별도 관리자 저장소의 test, lint, Next.js build, Vite rollback build, CodeQL과 Vercel checks 통과
- production Vercel environment에 관리자 DB 자격 증명이 없음을 확인
- Firebase Hosting 비활성화 후 기존 `bodeul-dev.web.app` 응답이 404인지 확인
- Core API Preview용 WIF provider만 남고 관리자 Hosting용 provider와 서비스 계정이 삭제됐는지 확인
- Vercel 최신 master deployment의 Ready, 기본 alias 루트 200, 무인증 관리자 API 401, `live=false`, custom domain 0개 확인
- Supabase runtime role의 세 도메인 테이블 SELECT 전용, 공개 role 권한 0건과 Security Advisor 경고 0건 확인
- 신규 예약 조회·Flyway 내부 인덱스의 미사용 Performance INFO 2건은 실제 쿼리 통계가 쌓일 때까지 유지

### 당시 남은 범위

- production Firebase·Supabase·Cloud Run 기반과 DB 자격 증명 분리는 아래 133번에서 완료
- Vercel Production 관리자 DB 자격 증명과 실제 운영 도메인은 미연결
- 관리자 웹 reCAPTCHA Enterprise와 App Check custom backend 검증
- 도메인별 PostgreSQL 쓰기 source of truth 전환
- production backup/restore와 rollback 리허설

## 133. 2026-07-17 production 인프라 최초 구축

### 구현과 운영 설정

- Google Cloud/Firebase `bodeul-prod-110`과 월 30,000 KRW budget을 만들고 50%·80%·100% 알림을 설정했다.
- Tokyo Firestore, 기본 Storage bucket, Email/Password Auth, Android·Web 앱과 저장소 Rules release를 준비했다.
- Cloud Run production Artifact Registry, WIF provider, deploy/runtime 서비스 계정과 DB Secret Manager version을 만들었다.
- Tokyo Supabase `bodeul-prod`에 서버 전용 role·schema bootstrap과 Flyway V1~V3를 적용했다.
- `bodeul-prod` Free project는 2026-07-25 비활성으로 자동 일시 중지됐다. 원래 소유 조직에서 재개하고 읽기 전용 점검과 최신 백업·격리 복원을 다시 통과하기 전까지 production migration과 배포를 차단한다.
- GitHub production 배포·migration Environment를 실제 식별자와 분리된 secret으로 연결했다.
- Windows에서 `gcloud.ps1`이 실행 파일로 선택되던 secret 입력 스크립트를 `gcloud.cmd` 우선으로 수정했다.

### 검증

- Firestore Native mode와 삭제 방지, Storage Tokyo bucket, Firebase Rules release 2개 확인
- WIF가 저장소, `master`, `core-api-production` Environment로 제한되고 사용자 관리 서비스 계정 key가 없는지 확인
- 보호된 migration run `29570950189`에서 Core API 검사와 Flyway V1~V3 성공
- 업무 테이블 owner `bodeul_migration`, RLS 3개, 정책 6개, 공개 role table grant 0건 확인
- production 업무 데이터 row 0건과 Supabase Security Advisor lint 0건 확인
- pre-migration dump를 공개 접근 차단·28일 retention GCS bucket에 저장하고 SHA-256 기록

### 남은 범위

- Kakao production key와 첫 Cloud Run revision 배포·rollback
- Vercel Production Firebase·SELECT-only 관리자 DB 연결
- release Google 로그인, App Check provider/enforcement와 custom domain
- production DB restore 리허설, 유료 backup 정책과 운영자·출시 일정 확정

## 134. 2026-07-18 production PostgreSQL 복원 검증

### 구현과 운영 설정

- production migration 자격 증명으로 custom-format dump를 생성하되 GitHub Artifact에는 올리지 않는 workflow를 구성했다.
- 별도 PostgreSQL 17 컨테이너에 dump를 복원한 뒤 검증에 성공한 산출물만 제한된 GCS 경로에 저장하도록 했다.
- 전용 WIF provider와 `bodeul-db-backup` 서비스 계정은 object 생성·조회만 가능하고 삭제 권한은 갖지 않는다.

### 검증

- run `29633892075`에서 4개 테이블 row 수, owner, ACL, RLS 정책 6개, 인덱스 14개, 제약 31개와 Flyway V3 일치 확인
- GCS 재다운로드 SHA-256 일치 확인
- production Supabase Security Advisor 오류 0건 확인

### 남은 범위

- Supabase Pro 일일 7일 백업 활성화
- Cloud Run과 Vercel deployment rollback 리허설
- 실제 업무 데이터가 쌓인 뒤 분기 복원 반복

## 135. 2026-07-18 연말 운영 전환 기준 확정

### 확정한 기준

- 목표 운영 전환일을 2026-12-15 10:00 KST로 정했다.
- 월 반복 비용 승인 한도는 150,000 KRW, 정상 목표는 100,000~130,000 KRW로 정했다.
- 실제 사용자 데이터 투입 전 Supabase Pro, 실제 운영 전 Vercel Pro 개발자 좌석 2개를 사용한다.
- 위치 정밀 좌표는 종료 후 24시간, 채팅 본문은 180일, 채팅 첨부와 매니저 증빙 원본은 30일을 기본 보관 기간으로 정했다.
- 업무 데이터는 PostgreSQL을 단일 source of truth로 두고 Supabase Realtime private Broadcast는 커밋 결과 알림에만 사용한다.

### 실행 순서

- 8월까지 예약 API와 Android repository를 PostgreSQL 경로로 준비한다.
- 9월 예약 단일 쓰기, 10월 매칭·동행·리포트, 11월 채팅·위치와 자동 파기를 개발 환경에서 완료한다.
- 12월에는 release 보안, backup/restore, rollback과 Go/No-Go 검증만 수행한다.
- 전환 뒤 30일은 Firestore를 읽기 전용 rollback 자료로 유지하고 2027-01-15부터 legacy 경로를 제거한다.

### 남은 범위

- 예약 도메인 PostgreSQL source of truth 전환 구현
- 도메인별 Flyway migration과 Core/Admin 최소 권한 적용
- 보관 기간을 집행하는 일일 정리 job과 legal hold 구현
- 기준 도메인, 실명 운영자 2명과 사용자 공지 확정

## 136. 2026-07-18 매칭·동행·리포트 PostgreSQL 스키마 1단계

### 구현과 운영 설정

- 예약과 1:1인 동행 세션, 세션 리포트, 예약 후속 처리와 관리자 배정 감사 테이블을 Flyway V5로 추가했다.
- 관리자 runtime에는 광범위한 테이블 쓰기 대신 role·상태·version을 검증하는 배정 함수만 허용했다.
- 최신 Firestore 백업을 검증하고 migration role로만 upsert하는 세션 전용 seed·rollback 도구를 추가했다.
- 채팅과 고빈도 위치는 #221 범위로 분리했다.

### 검증

- 최신 백업 기준 동행 세션 2건, 리포트 2건, 후속 처리 1건과 FK·상태 오류 0건 확인
- Firebase 도구 테스트 29건과 V5 계약 테스트 통과
- PostgreSQL 17에서 V1~V5, 버전 충돌 거부, 관리자 배정, Core 권한 거부와 V5 rollback 통과
- 개발 DB migration run `29638503856`에서 V5 적용, owner·RLS·정책·함수 ACL 확인

### 남은 범위

- 개발 DB 세션·리포트·후속 처리 백필과 FK·advisor 실검증
- Core API 매니저 세션·리포트·매칭 후 취소 트랜잭션
- 관리자 서버 배정 API와 Android repository 전환
- 실기기·관리자 Preview 검증 후 Firestore 해당 도메인 쓰기 중지

## 137. 2026-07-18 동행 세션 백필과 Core API 쓰기 경계

### 구현과 운영 설정

- 보호된 migration workflow로 개발 DB에 세션 2건, 리포트 2건, 후속 처리 1건을 백필했다.
- 백필 SQL은 일회성 Environment secret과 SHA-256으로 고정하고 실행 직후 secret을 삭제했다.
- Core API에 참여자·배정 매니저 세션 조회, 매니저 현장 메모·단계 전환·리포트 endpoint를 추가했다.
- 단계 수는 클라이언트 값이 아니라 PostgreSQL 병원 가이드에서 계산하도록 했다.
- 매칭 후 취소, 단계 전환, 리포트 완료는 예약과 세션을 같은 Spring 트랜잭션으로 처리한다.
- V6에서 Core runtime에 필요한 세션 UPDATE와 리포트 INSERT·UPDATE 컬럼만 허용하고 DELETE는 차단했다.
- Supabase Advisor가 지적한 외래키 7개에 covering index를 추가했다.

### 검증

- migration run `29638905550` attempt 2 성공, 일회성 secret 삭제 확인
- V6 migration run `29639792606` 성공, 기존 백필 2/2/1건 보존 확인
- FK와 `imported_at` 누락 0건, 예약·세션 상태 조합 2건 일치
- 개발 Supabase Security Advisor lint 0건
- Core API 전체 검사 통과
- PostgreSQL 17 V1~V6 적용에서 세션 UPDATE와 리포트 INSERT 허용, DELETE 거부, 쓰기 정책 3개 확인
- Core runtime DML에서 완료·취소 시 예약과 세션 상태 일치, 리포트 저장과 병원 가이드 단계 조회 확인
- V6 rollback 뒤 쓰기 권한·정책·추가 인덱스 0건 확인
- 개발 DB에서 Core 쓰기 허용·DELETE 차단, Admin·Supabase client 쓰기 차단, RLS 정책 3개와 covering index 7개 확인
- 외래키 미인덱스 INFO는 해소됐고 트래픽 전 미사용 인덱스 INFO만 유지
- Core API Preview deploy run `29639915209` 성공, commit `9d08c1be` 이미지와 리비전 `bodeul-core-api-preview-00010-pd9` 트래픽 100% 확인
- Preview `/health` 200 `UP`, `/api/companion-sessions` 무인증 401 `missing_authorization` 확인

### 당시 남은 범위

- Core API Preview 실제 Firebase token 역할·충돌 검증(무인증 401 경계는 완료)
- 관리자 서버 배정 API와 Android repository 전환
- 실기기·관리자 Preview 통합 검증 후 Firestore 쓰기 종료

## 138. 2026-07-18 Android 동행 세션 Core API 전환과 실기기 검증

### 구현과 운영 설정

- Firebase ID token과 App Check token을 공통으로 처리하는 Android Core API 인증 클라이언트를 분리했다.
- 예약 상세, 매니저 홈·가이드·이력과 보호자 리포트의 세션 진행·리포트 원본을 Core API로 전환했다.
- 매니저의 단계 전환, 현장 메모, 약국 상태와 리포트 제출은 PostgreSQL version 조건부 API를 사용한다.
- 채팅·첨부·좌표 이력·읽음 시각·실시간 위치 상태는 #221까지 Firestore에 남긴다.
- 예약 상세는 Firebase 보조 listener와 10초 Core API 갱신을 함께 사용한다.
- 보호자 세션 보조 쿼리에 `guardianUserId` 조건을 추가해 Rules 거부를 수정했다.

### 검증

- 실제 Firebase token으로 환자·보호자·매니저 세션 목록 200과 각 2건, 관리자 403 확인
- 환자 세션 수정 403, 매니저 잘못된 version 수정 409와 데이터 무변경 확인
- `testDebugUnitTest`, `assembleDebug`, SM-S921N `connectedDebugAndroidTest` 1건 통과
- 매니저 홈 활성 세션 `IN_TREATMENT`·`4/7`, 과거 이력 완료 1건 표시
- 보호자 리포트 4건과 예약 상세 `IN_TREATMENT`·`4/7` 표시
- 예약 상세 25초 유지 중 10초 갱신 이후에도 동일 상태, 관련 오류 로그 없음
- 계측 테스트의 앱 데이터 제거 후 새 debug token을 비공개 재등록하고 예약 목록·상세·세션 요청 3건 `app_check_verdict=valid` 확인

### 남은 범위

- 후속 처리 API와 Core API 단독 생성 예약·배정의 Firebase 보조 데이터 의존 제거
- 관리자 Preview가 만든 Core-only 예약·배정을 Android가 Firestore 보조 문서 없이 조회하는 실기기 검증
- 통합 검증 후 세션·리포트·후속 처리 Firestore 쓰기 중지

## 139. 2026-07-18 관리자 전용 매니저 배정 API 연결과 Preview 검증

### 구현과 운영 설정

- 별도 `bodeul-admin-web` 저장소의 Next.js 서버에 `POST /admin/companion-assignments`를 추가했다.
- Firebase ID token과 PostgreSQL `ADMIN` 역할을 확인하고 DB의 `assign_companion_session` 함수만 실행한다.
- 관리자 runtime에는 테이블 쓰기 권한을 추가하지 않았고 UUID, 예약 version, 배정 사유를 서버에서 검증한다.
- DB SQLSTATE는 공개 API의 400·403·404·409·503으로 변환하고 내부 오류 문구를 노출하지 않는다.

### 검증

- 관리자 웹 테스트 19건, lint, Next.js build, Vite rollback build, CodeQL 통과
- Vercel Preview 무인증 401, 환자 403, 관리자 입력 오류 400, 취소 예약 상태 충돌 409 확인
- 임시 `REQUESTED` 예약 성공 201, 예약 `MATCHED`·version 1, 세션 `READY`, 감사 1건 확인
- 임시 예약·세션·감사는 검증 직후 삭제하고 잔여 0건 확인
- 배정 함수 `security definer`, `search_path=bodeul, pg_temp`, Admin만 실행 가능, Security Advisor 경고 0건 확인
- 관리자 웹 PR [#23](https://github.com/bodeul110/bodeul-admin-web/pull/23) squash merge 완료

### 남은 범위

- Core-only 예약·배정을 Android가 Firebase 보조 문서 없이 조회하도록 repository 시작점을 전환
- 관리자 웹 App Check Issue #16과 production V5~V7 migration 승인
- 통합 검증 후 세션·리포트·후속 처리 Firestore 쓰기 중지

## 140. 2026-07-18 예약 후속 처리 Core API 전환

### 구현과 운영 설정

- Flyway V7에서 `bodeul_core_runtime`에 `appointment_follow_ups` 지정 열 INSERT·UPDATE 권한과 RLS 쓰기 정책을 추가했다.
- `GET/PATCH /api/appointments/{id}/follow-up`을 추가해 참여자 조회, 완료 예약의 환자·보호자 부분 저장과 version 충돌 검사를 구현했다.
- 저장 전 Firebase ID token, App Check, PostgreSQL role과 예약 참여 관계를 검증한다.
- Android Core API 예약 저장소의 후기·정산 확인·긴급 지원 조회와 저장을 새 endpoint로 전환했다.
- 채팅·첨부·위치 공유는 #221까지 Firestore에 유지한다.

### 검증

- Core API 단위·통합 테스트와 전체 `check` 통과
- Android `testDebugUnitTest`, `assembleDebug`, `lintDebug` 통과
- PostgreSQL 17 임시 인스턴스에 V1~V7 연속 migration 적용 성공
- Core runtime의 후속 처리 생성 version 1, 정산 부분 수정 version 2, 기존 후기 유지 확인
- 오래된 version 수정 0건, `anon`·`authenticated`·`service_role` 권한 0건 확인
- 임시 DB 트랜잭션 rollback과 컨테이너 삭제 완료
- 개발 DB migration run `29642658596` 성공, V7 이력과 기존 후속 1건 보존 확인
- Cloud Run Preview run `29642778613`, 리비전 `00011-tp4`, 트래픽 100%와 무인증 401 확인
- SM-S921N 환자 계정의 GET·PATCH 7건 200, App Check `valid`, 후기·정산·긴급 지원과 actor 일치 확인
- 실기기 검증용 임시 예약·후속 기록 삭제 후 잔여 0건 확인

### 남은 범위

- Core-only 예약·배정의 Firebase 보조 문서 의존 제거와 실기기 검증
- 통합 검증 후 `appointmentFollowUps` Firestore 운영 쓰기 중지

## 141. 2026-07-18 Core-only 예약·배정 화면 시작점 전환

### 구현과 운영 설정

- Core API 예약 응답에 PostgreSQL `app_users` 기준 배정 매니저 이름·전화번호·이메일을 추가했다.
- Android 매니저 홈은 본인 Core API 세션 목록에서 최신 활성 세션을 선택하고 예약·참여자·매니저 정보를 Core API 응답으로 조합한다.
- 매니저 과거 이력은 완료된 Core API 세션과 리포트만 사용하며 Firestore 세션·예약·리포트 목록을 시작점으로 사용하지 않는다.
- 보호자 진행 현황은 본인 Core API 예약 목록과 세션 목록을 예약 UUID로 연결한다. 미배정 예약도 세션 없이 표시한다.
- 환자·보호자 예약 상세의 Core-only 모델에도 예약 응답의 배정 매니저 프로필을 포함한다.
- 채팅·첨부·위치 공유·매니저 서류·문의는 #221과 별도 후속 이슈까지 기존 Firebase 경계를 유지한다.

### 검증

- Core API 단위·통합 테스트와 전체 `check` 통과
- 배정 매니저 프로필이 예약 API JSON에 포함되는 통합 테스트 추가
- Android `testDebugUnitTest`, `assembleDebug`, `lintDebug` 통과
- Cloud Run Preview run `29643728174`, 리비전 `00012-tqv`, 트래픽 100% 확인
- Firestore 예약·연결 세션 문서 0건인 임시 PostgreSQL 배정을 SM-S921N 매니저 홈·보호자 리포트·환자 예약 상세에서 확인
- 첫 환자 상세의 담당 매니저 누락을 실기기에서 발견하고 Core-only 모델 조합을 보완한 뒤 재설치 검증
- 임시 예약 상세 GET 3건과 역할별 목록 요청 200, App Check `valid` 확인
- 임시 배정 감사·세션·예약을 각각 1건 삭제하고 세 테이블 잔여 0건 확인

### 남은 범위

- 검증 완료 후 세션·리포트·후속 처리 Firestore 클라이언트 쓰기 제한

## 142. 2026-07-18 채팅·위치 PostgreSQL 스키마 1단계

### 구현과 운영 설정

- Flyway V8에 채팅 메시지, 첨부 메타데이터, 세션별 읽음 위치와 위치 이력 테이블을 추가했다.
- 채팅은 클라이언트 메시지 UUID로 중복을 막고 위치는 배정 매니저와 진행 상태를 확인하는 서버 전용 함수로만 기록한다.
- 위치 이력은 세션별 최근 10건만 유지하고, 세션 종료 시 채팅 180일·첨부 30일·위치 24시간 만료 시각을 예약한다.
- 관리자 runtime은 조회만 허용하고 Core runtime에는 endpoint 구현에 필요한 지정 컬럼 쓰기와 위치 함수 실행만 허용했다.
- bootstrap과 rollback 파일을 명시적 트랜잭션으로 감싸 `SET LOCAL ROLE`이 실행 도구와 무관하게 같은 트랜잭션에서 적용되도록 했다.

### 검증

- V8 migration과 rollback 계약 테스트 통과
- PostgreSQL 17 `postgres:17` 임시 인스턴스에서 bootstrap과 V1~V8 연속 적용 성공
- Core 채팅·읽음·위치 쓰기 허용, `authenticated` 읽기와 관리자 채팅 쓰기 거부 확인
- 위치 11건 입력 후 최근 10건 유지, 같은 클라이언트 UUID 재전송 시 중복 없음 확인
- 세션 완료 후 채팅 180일·위치 24시간 만료 예약 확인
- V8 rollback 후 실시간 테이블 4개 잔여 0건 확인
- 개발 DB migration run `29645323232`에서 V8 적용, owner·RLS·정책·함수·trigger와 신규 행 0건 확인
- Security Advisor 0건 확인, Performance Advisor의 읽음 복합 외래키 미인덱스 1건을 V9으로 보완
- PostgreSQL 17에서 V1~V9 적용과 V9 rollback 확인
- 개발 DB migration run `29645575612`에서 V9 적용, 복합 인덱스 열 순서와 외래키 미인덱스 경고 해소 확인

### 후속 상태

- Core API endpoint와 private Broadcast 발행은 2단계에서 완료했다.
- Firebase JWT private 채널 인가는 3단계에서 완료했고 Android 재연결·API 재조회 전환이 남았다.
- #222 일일 파기 job과 Storage 첨부 삭제 구현이 남았다.

## 143. 2026-07-18 채팅·위치 Core API와 Broadcast 2단계

### 구현과 운영 설정

- 참여자용 Realtime snapshot과 메시지·읽음·위치 Core API를 추가했다.
- 메시지·위치는 클라이언트 UUID로 중복을 막고, 읽음 위치는 같은 세션에서 앞으로만 이동한다.
- 첨부는 기존 Firebase Storage 세션 prefix, JPEG·PNG·PDF, 파일당 10 MiB와 메시지당 3개를 서버에서 검증한다.
- 완료·취소 세션은 새 메시지·위치를 거부하고 snapshot에서 정밀 위치를 반환하지 않는다.
- V10 DB trigger는 채팅·읽음·위치 커밋 신호를 private 주제로 만들고 V11은 managed Realtime 권한을 migration role에 주지 않는 privileged publisher로 발행한다.
- publisher는 세션·리소스·레코드 ID 외 payload를 거부하므로 본문·좌표·Storage 경로를 전달할 수 없다.

### 검증

- Core API 서비스 단위 테스트, HTTP 통합 테스트, V10·V11 migration 계약 테스트와 전체 `check` 통과
- PostgreSQL 17 임시 인스턴스에서 bootstrap과 V1~V11 연속 적용 성공
- 메시지 재시도 중복 0건, 읽음 위치 역행 갱신 0건, 위치 12건 기록 뒤 최근 10건 유지 확인
- `anon`·`authenticated` 조회, Admin 채팅 쓰기, Core 위치 table 직접 쓰기 거부 확인
- Realtime 확장 부재 시 publisher false 반환과 본 쓰기 유지, V11 rollback·재적용 확인

### 남은 범위

- Android API·private 채널·재연결·FCM 전환 뒤 Firestore 채팅·위치 쓰기 중지
- #222 일일 파기 job과 Storage 첨부 삭제

## 144. 2026-07-18 Firebase JWT와 private Realtime 인가 3단계

### 구현과 운영 설정

- 개발 Supabase에 privileged publisher·V11과 실제 private Broadcast 3종을 검증했다.
- Firebase `bodeul-dev`를 Third-Party Auth로 등록하고 Realtime을 private-only로 전환했다.
- Firebase ID token의 `role: authenticated` 신규 사용자 함수와 기존 사용자 백필 도구를 추가했다.
- Realtime RLS는 Firebase 발급 프로젝트, private topic UUID와 환자·보호자·배정 매니저 참여 관계를 함께 확인한다.

### 검증

- 개발 Supabase의 역할별 허용 3건과 거부 4건 시나리오 통과, Security Advisor 0건
- 신규 사용자 함수 배포와 기존 사용자 9명 백필 완료, 재실행 대상 0명
- 실제 Firebase JWT로 참여 private WebSocket join 성공, 비참여 세션 join 거부 확인
- 종단 검증용 Firebase 사용자와 PostgreSQL 데이터 잔여 0건 확인

### 남은 범위

- Android Core API 쓰기·private channel 구독·재연결 snapshot 복구와 Firestore 쓰기 차단 코드는 4단계에서 완료
- 개발 DB·Cloud Run·Firestore Rules 적용과 실기기 FCM 종단 검증
- production Firebase Third-Party Auth와 production RLS 적용
- #222 일일 파기 job과 Storage 첨부 삭제

## 145. 2026-07-18 Android Realtime 전환 4단계

### 구현과 운영 설정

- Android 채팅·읽음·위치와 실시간 공유 상태 쓰기를 Core API로 전환했다.
- Firebase ID token private WebSocket 구독, 25초 heartbeat, 45분 token 재연결, 최대 30초 지수 backoff와 snapshot 재조회를 추가했다.
- Flyway V12에 실시간 위치 공유와 자동 위치 알림 운영 상태를 추가하고 Core runtime의 지정 컬럼 쓰기만 허용했다.
- Core API는 PostgreSQL `AFTER_COMMIT` 이후 같은 요청 안에서 민감 본문·좌표가 없는 FCM 보조 알림을 전송한다.
- Firestore `companionSessions`는 참여자 읽기만 유지하고 client 쓰기를 모두 거부하도록 Rules와 emulator 검증을 바꿨다.

### 검증

- Core API 전체 `check` 통과
- Android `assembleDebug` 통과
- Firestore·Storage Rules emulator 7개 시나리오 통과
- 개발 Supabase transaction 안에서 V12 적용·rollback과 잔여 컬럼 0개 확인
- 개발 DB migration run `29650223504`와 Cloud Run Preview deploy run `29651623086` 통과
- 최신 리비전 `bodeul-core-api-preview-00014-wnr`에서 health 200, 무인증 auth/place 401 확인
- 실제 세션 채팅·읽음·위치·재연결 통과, FCM 1/1 성공과 SM-S921N 알림 생성 확인
- private Realtime 10/10 join, Core API 메시지 200·436ms, Broadcast 10/10 수신 확인
- Supabase Security Advisor 0건, 운영 목표 Pro 포함량 안의 개발 부하임을 확인

### 남은 범위

- #251 Core-only 세션 첨부 서버 중계, 인증된 실기기 업로드·다운로드, DB 충돌 보상 삭제와 개발 만료·삭제 종단 검증 완료. production 적용은 #134 출시 게이트에서 진행
- FCM 재시도·실패율 관측, release App Check enforce 검증
- production 적용과 #222 일일 파기

## 146. 2026-07-28 Firebase Preflight WIF 전환

### 선택 근거

- GitHub Actions에 저장한 Firebase 사용자 refresh token은 계정 비밀번호·세션 변경에 영향을 받고 장기 자격 증명으로 남는다.
- 공식 Firebase CLI와 Google Cloud 지침은 CI에서 Application Default Credentials와 Workload Identity Federation 사용을 권장한다.
- 기존 `github-actions` WIF 풀을 재사용하되 Android Preflight 전용 provider와 서비스 계정을 분리해 다른 배포 자격과 권한을 공유하지 않는다.

### 구현과 운영 설정

- `bodeul-firebase-preflight` provider는 저장소·소유자 ID, `master`, workflow 경로와 `workflow_dispatch`를 모두 검사한다.
- 같은 이름의 전용 서비스 계정에는 Firestore·Firebase Auth 읽기와 API 사용 권한만 부여했다.
- 조직 정책상 운영 계정에 프로젝트 Owner를 둘 수 없어 기존 WIF provider API 권한만 포함한 프로젝트 custom role `bodeulWifProviderAdmin`을 운영 계정에 부여했다.
- custom role 생성과 서비스 계정 정책 연결에 사용한 임시 Role Admin·Service Account Admin 권한은 작업 직후 회수했다.
- Android Preflight는 WIF로 30분짜리 access token을 발급하고 Firebase 도구는 `GOOGLE_OAUTH_ACCESS_TOKEN`을 사용자 token보다 우선한다.
- Actions 설정 도구는 더 이상 `FIREBASE_TOKEN`이나 OAuth client secret을 GitHub에 올리지 않는다.

### 검증 기준

- Firebase 도구 단위 테스트와 CI preflight 통과
- WIF provider 조건, 서비스 계정 IAM, 사용자 관리형 key 0개 확인
- `master`의 수동 Android Preflight에서 Firebase 운영 점검 성공
- 성공 확인 뒤 `FIREBASE_TOKEN`, `FIREBASE_OAUTH_CLIENT_SECRET` 저장소 secret 삭제

## 147. 2026-07-28 Core-only 세션 첨부 권한 전환

### 구현과 운영 설정

- Android 채팅 첨부 업로드·다운로드를 Spring Core API 경로로 전환하고 기존 Firebase Storage 경로는 구버전 첨부 조회 호환용으로만 유지했다.
- Core API는 PostgreSQL 참여 관계와 세션 활성 상태를 Storage 쓰기 전에 확인하고, 메시지 저장 직전에도 다시 확인한다.
- 첨부 원본은 Firebase Storage에 두고 PostgreSQL에는 참여자, 경로, 해시, 크기와 30일 만료 metadata를 기록한다.
- 개발·production 기본 버킷에 각 Core runtime service account의 버킷 단위 `roles/storage.objectUser`만 부여했다.
- 개발 Storage Rules는 Firestore 보조 문서가 없는 경로의 클라이언트 직접 접근을 거부하도록 배포했다.

### 검증

- Core API 전체 `check`, Android `testDebugUnitTest`·`assembleDebug`, Rules emulator 7개 시나리오 통과
- PR #273과 권한 선확인 보완 PR #274의 필수 CI 통과 후 squash 병합
- Core API Preview deploy run `30355824697`, merge SHA `2a3b01f6083dbafd51b8e6b8075bbe36334acebc`, 리비전 `bodeul-core-api-preview-00016-v94` 트래픽 100% 확인
- 배포 리비전에서 health 200, 무인증 multipart 첨부 메시지와 첨부 다운로드 각각 401 `missing_authorization` 확인
- 개발·production 기본 버킷 IAM에서 의도한 runtime service account 외 별도 `roles/storage.objectUser` 구성원이 없음을 재확인
- 실기기 첫 인증 업로드의 HTTP 503을 버킷 메타데이터 권한 불일치로 좁히고, PR #280에서 IAM 추가 없이 객체 API 직접 접근으로 수정
- Preview deploy run `30364434679`, merge SHA `e30d87e75cb93da7a8223956ac6e682e227cd8dc`, 리비전 `bodeul-core-api-preview-00017-x9r` 트래픽 100% 확인
- SM-S921N(Android 16)에서 인증된 첨부 메시지 POST 200, 69바이트 PNG 객체 생성, 첨부 GET 200, 채팅 표시와 Android 파일 열기 선택 화면 진입 확인
- 자동화 원본은 전송 완료 후 삭제됐고 인증 다운로드 미리보기 캐시가 생성됨을 확인
- 같은 `clientMessageId`에 다른 첨부를 보내 실제 PostgreSQL idempotency 충돌 409를 유도하고, 충돌 객체 경로가 실행 전·후 모두 없으며 기존 원본 한 개만 유지됨을 확인

### 후속 운영 범위

- #134 production 출시 게이트 이후 production Rules와 Core API 적용

## 148. 2026-07-28 Core 첨부 자동 파기 경로 보완과 개발 리허설

### 구현과 운영 설정

- 자동 파기 Storage 허용 목록에 `세션 ID/클라이언트 메시지 UUID/파일` Core 첨부 경로를 추가했다.
- 기존 `세션 ID/파일` 경로는 호환용으로 유지하고 임의 디렉터리와 추가 중첩은 거부한다.
- Android Preflight가 Functions 변경 시 Node 22 `npm test`를 실행하도록 CI 누락을 보완했다.
- 개발 `cleanupExpiredData`, `reportMonthlyRetention` 함수를 새 소스로 제한 재배포했다.

### 검증

- 로컬 Functions 테스트 16개, workflow YAML 파싱과 diff 검사 통과
- PR #276의 Node 22 Functions 테스트와 JavaScript CodeQL 통과 후 squash 병합
- 개발 리비전 `cleanupexpireddata-00007-jad`, `reportmonthlyretention-00005-jun` ACTIVE 확인
- `RETENTION_APPLY_ENABLED=false`, DB URL Secret v2, CA Secret v1 유지 확인
- Scheduler 수동 실행 HTTP 200, `DRY_RUN`, 후보와 삭제 실패 각각 0건 확인
- preview 전용 고정 fixture 실행 경로 PR #278 CI 통과 후 merge commit `96e9b3a135e2731186806679a016fc4504df4271` 반영
- DB setup run `30360287210`: fixture 7행, 첨부 후보 1건, legal hold skip 2건
- 리비전 `cleanupexpireddata-00008-bek` 단일 APPLY: Scheduler HTTP 200, 첨부 삭제 1건, legal hold skip 2건, 실패 0건
- DB status run `30361215537`: 만료 첨부 `DELETED`와 메타데이터 비식별화, legal hold 첨부 `ACTIVE`와 53B Storage 객체 보존 확인
- cleanup run `30361372405`: fixture 7행과 고정 Storage prefix 객체 모두 정리
- 리비전 `cleanupexpireddata-00009-cil`에서 `RETENTION_APPLY_ENABLED=false`, 최종 dry-run 모든 후보와 실패 0건 확인

### 남은 범위

- Firestore 전환 fixture APPLY와 개인정보 처리방침·위치기반서비스 이용약관 대조
- production 출시 게이트 이후 production 파기 로그인 secret, 함수 dry-run과 fixture APPLY

## 149. 2026-08-22 Notion·Figma 기준 문서 정렬

### 정리한 기준

- Notion은 제품 의도와 미결 정책, Figma는 화면 구조와 시각 위계, 저장소는 현재 구현과 기술 계약을 소유하도록 책임을 분리했다.
- Figma `보들 가이드`의 현재 페이지 `460:2`, 최상위 화면 38개, 가이드 1~13과 미연결 채팅 시안 6개를 현행 화면 지도로 정리했다.
- Notion의 Firestore 단일 원본, 채팅 제외와 AI/STT 처리 문구는 현재 구현 근거가 아니라고 명시했다.
- 제품 목표인 매니저 self-accept와 시스템 이벤트 동행방이 현재 관리자 배정·직접 채팅 구현과 다르다는 점을 분리했다.
- 로컬 기능설명서 PDF와 Figma export를 현재 원본이 아닌 과거·임시 스냅샷으로 낮췄다.

### 문서

- [기획·디자인·구현 기준](../planning/source-of-truth.md)
- [Notion 제품 기준 정합성](../planning/notion-product-alignment.md)
- [Figma 현행 화면 지도](../design/figma-current-screen-map.md)
- [Notion·Figma 문서 정합성 점검](../reports/notion-figma-document-alignment-2026-08-22.md)

### 검증

- 문서 내부 상대 링크 검사
- `git diff --check`
- 변경 파일의 Notion 비공개 URL·페이지 ID와 계정 정보 미포함 확인
- 문서 전용 변경이므로 Android·Core API 빌드는 수행하지 않음

## 150. 2026-08-25 Android 릴리스 서명 fail-closed 경계

### 구현과 운영 설정

- 팀 소유 릴리스 키가 확정되기 전에도 unsigned 릴리스 산출물이 만들어지지 않도록 Gradle 검증을 추가했다.
- 키 저장소 경로와 alias는 추적되지 않는 로컬 설정·Gradle 속성·환경변수로 받고, 두 암호는 환경변수로만 받는다.
- 릴리스 산출물 작업은 `validateReleaseSigning`을 선행하고 누락·부분 입력·키 저장소 파일 부재 시 실패한다.
- 비밀값을 Gradle 구성 캐시에 남기지 않도록 릴리스 작업에는 `--no-configuration-cache`를 요구한다.
- Android 키 저장소 확장자를 Git 추적에서 제외하고 [릴리스 서명 운영 계약](../operations/android-release-signing.md)을 추가했다.

### 검증

- `assembleDebug` 성공과 구성 캐시 저장 확인
- 구성 캐시 활성 릴리스 작업 차단
- 릴리스 서명 전체 누락·부분 입력·키 저장소 파일 부재 차단
- 축약 릴리스 작업명과 `-x vRS` 축약 제외 시도에서도 unsigned 산출물 생성 차단
- 기존 로컬 debug keystore를 비배포용으로만 사용해 서명 구성 성공 경로 확인
- 실패 출력에 테스트 입력값과 암호 sentinel이 포함되지 않음을 확인
- `assembleRelease --no-configuration-cache` 실패 후 신규 APK·AAB가 생성되지 않음을 확인

### 남은 범위

- 팀 소유 release keystore, alias, 암호 보관 주체와 오프라인 백업 위치 확정
- Firebase와 Kakao에 release SHA-256 등록
- Google Play Console과 Firebase Play Integrity 연결
- ARM release 후보의 App Check `VALID`와 rollback 검증

## 151. 2026-08-26 Production 인프라 감사와 운영 WIF 강화

### 구현과 운영 설정

- metadata 전용 감사 서비스 계정과 exact-subject WIF provider를 분리하고, GitHub Environment 승인 뒤에만 수동 감사가 실행되도록 구성했다.
- Core API 배포와 DB 백업 provider를 불변 저장소·소유자 ID, `master`, 정확한 Environment·workflow·`workflow_dispatch` 조건으로 제한했다.
- 배포·백업 서비스 계정 impersonation을 Environment 전체 principalSet에서 각 exact subject 하나로 축소했다.
- Firebase Storage Public Access Prevention을 bucket 수준으로 강제하고, Storage Rules를 현재 저장소 ruleset과 일치시켰다.
- Firestore PITR을 활성화하고 version 보존 기간을 7일로 확정했다.
- Firebase Storage UBLA는 개발 버킷 canary 전까지 `deferred`로 유지했다.

### 검증

- 감사 도구 단위 테스트 11개, PowerShell 구문, GitHub Actions YAML 검사 통과
- 관리자 단기 토큰 기반 Production metadata baseline 33개 전체 통과
- [Production Infrastructure Audit run 32971183897](https://github.com/bodeul110/Bodeul/actions/runs/32971183897)에서 exact WIF 인증, PITR 상태와 baseline 감사 성공
- 감사·배포·런타임·백업·보존 서비스 계정의 사용자 관리 key 0개 확인
- 적용에 사용한 임시 project binding 3개 회수와 권한 하나짜리 임시 custom role 삭제 확인
- 관리자 권한 없이 PITR 스크립트를 재실행해 활성 상태와 `604800s` 보존을 다시 확인

### 남은 범위

- Kakao REST API production Secret version과 첫 Cloud Run production revision
- Android·Web App Check provider와 enforcement 실검증
- 개발 버킷 canary 후 Firebase Storage UBLA

## 152. 2026-08-26 Production PostgreSQL V15 migration·복원

### 구현과 운영 설정

- 자동 일시 중지됐던 `bodeul-prod` Supabase project를 재개하고 `ACTIVE_HEALTHY` 상태를 확인했다.
- 보호된 production workflow가 project ref, JDBC 최종 대상, migration login과 `master` commit을 다시 확인하도록 유지했다.
- Flyway V14 guide snapshot 고정과 V15 계정 삭제 영향도 DB 계약을 적용했다.
- migration 전후 logical dump를 각각 격리 PostgreSQL 17에 복원한 경우에만 비공개 GCS에 업로드했다.

### 검증

- migration 전 readiness run `32980526711`: Flyway V13, 실패 이력과 V14 backfill 후보 0건
- migration 전 backup·restore run `32980749558`: 격리 복원과 manifest 일치
- migration run `32981200371`: V14·V15 적용과 `verifyAccountDeletionInventory` 통과
- migration 후 readiness run `32981484159`: Flyway V15, 실패 이력과 업무 데이터 0건
- migration 후 backup·restore run `32981633994`: 최신 V15 복구 지점 검증·외부 보관
- Supabase Security Advisor 경고 0건. 미사용 인덱스 INFO는 실제 쿼리 통계가 쌓일 때까지 유지

### 남은 범위

- 실제 사용자 데이터 투입 전 Supabase Pro, 일일 백업과 spend cap 확인
- Kakao production Secret과 첫 Cloud Run revision, smoke·rollback 검증
- 정책·약관 승인 뒤 production 자동 파기 fixture와 쓰기 전환 검증

## 153. 2026-08-26 관리자 웹 Production App Check 관찰 배포

### 구현과 운영 설정

- 별도 `bodeul-admin-web` 저장소 PR #37에서 reCAPTCHA Enterprise provider와 `X-Firebase-AppCheck` 전달을 반영했다.
- Next.js 서버에 `off`, `observe`, `enforce` 모드와 허용 Web App ID 검증을 추가했다.
- Vercel Production은 client 활성화와 서버 `observe`로 배포해 App Check 실패를 기록하되 아직 요청을 차단하지 않는다.

### 검증

- 관리자 웹 test 32개, lint, TypeScript 검사, Next.js·Vite rollback build 통과
- 병합 commit의 Build·CodeQL과 Vercel Production 배포 성공
- canonical 운영 주소 루트 200, 무인증 관리자 API 401, Tokyo 함수 배치 확인

### 남은 범위

- 인증된 관리자 계정으로 주요 흐름과 App Check `VALID` 요청 확인
- 관찰 결과 확인 뒤 `enforce` 전환과 rollback 실검증
- 개발 Web provider와 Preview debug token 분리

## 154. 2026-08-29 정상 동행 종료·완료 경계 구현

### 구현과 운영 설정

- 가이드 12 실제 동행 종료 `CARE_ENDED`와 가이드 13 업무 완료 `COMPLETED`를 분리하고 최초 서버 종료 시각을 보존했다.
- 선택 매니저 일지를 최대 300자로 제한하고 세션 완료와 리포트 `PENDING`·`READY`·`FAILED` 상태를 분리해 실패 세션 재진입과 재시도를 지원했다.
- 최초 `care_ended_at`부터 새 채팅·첨부·위치 쓰기와 배정 매니저의 기존 원문 조회를 닫았다. 보호자의 범위별 인가는 `CHAT`=채팅 본문·읽음 상태, `CHAT+ATTACHMENT`=채팅 첨부, `ATTACHMENT`=가이드 첨부, `REPORT`=리포트·건강정보로 분리했다.
- 채팅 180일·첨부 30일·위치 24시간 TTL과 보호자 동의 만료를 `CARE_ENDED`에서 시작하고, DB 세션 행 잠금과 trigger로 종료 동시 쓰기 우회를 차단했다.
- 종료 뒤 동의 재부여와 매니저 리포트·가이드 첨부 원문 조회를 차단했다. Android는 `careEndedAt`이 있으면 Realtime 보강을 생략하고 구독을 닫으며, 매니저 완료 이력은 리포트 대신 본인 `managerJournal`만 표시한다. DB 신호 발행 중단과 privileged bootstrap 006으로 신규·재인가 연결도 막는다.
- Firestore의 전환된 예약·세션·리포트·후속 처리 비교 문서와 Storage 채팅 첨부는 매니저·보호자 직접 경로를 닫고 Core API 인가·마스킹을 거치게 했다. 환자 보관 조회와 관리자 운영 조회만 유지하고 채팅 첨부의 모든 클라이언트 쓰기를 거부한다.
- migration 전 실시간 원문과 동의 만료값을 비공개 ledger에 보존해 V18 rollback 때 원래 값을 복원하고, 대상이 이미 파기됐으면 중단하도록 했다.
- 가이드 8 선택 결제 증빙 0~1개와 가이드 10 선택 처방 이미지 0~3개를 Core API 서버 중계로 추가했다.
- 완료 강제 설정은 Preview·Production 워크플로가 전달할 수 있게 준비했지만 기본값 `false`를 유지했고 실제 Environment 값 변경이나 배포는 하지 않았다.

### 검증

- 최신 변경을 포함한 Core API 전체 `check`, Android 전체 `testDebugUnitTest`와 `assembleDebug`: 성공
- Firebase Rules 에뮬레이터 테스트: 7/7 성공
- V18 migration·rollback 계약과 `CARE_ENDED` 역할별 접근 테스트: Core API 전체 검사와 disposable PostgreSQL 17 `migration-contract`에서 성공
- Core API·Preview·Production workflow YAML 파싱, 전체 diff와 migration 검증 셸 문법 검사: 성공

### 남은 범위

- maintenance 상태에서 V18 schema rollback과 bootstrap 006 rollback을 연속 실행하는 운영 절차 확인
- Preview Core API·Android 배포 후 실기기 중복 종료, 재진입, 첨부와 리포트 실패 재시도 검증
- #222 가이드 첨부 원본 만료·orphan 정리 연결
- 새 앱 보급과 별도 승인 뒤 Preview 완료 강제 설정 검증. Production 활성화는 출시 게이트에서 별도 결정

상세 근거는 [이슈 307 정상 동행 종료·완료 구현 기록](../reports/issue-307-companion-completion-2026-08-29.md)에 정리했다.

## 155. 2026-08-29 예약 공개 코드 계약

- Flyway V19에 `appointment_requests.public_code` 형식·unique·변경 금지 제약과 기존 예약 backfill을 추가했다.
- Core API가 `SecureRandom` 기반 `BD-` + 6자리 코드를 생성하고 unique 충돌을 최대 5회 재시도하도록 구현했다.
- 신청자와 배정 매니저의 기존 예약 인가 응답에만 `publicCode`를 포함하고 Android 신청자 예약 상세·매니저 가이드에 표시했다. 신청자가 아닌 연결 참여자의 응답에서는 빈 값으로 가린다.
- PostgreSQL에는 관리자별 분당 10회 제한과 평문을 남기지 않는 감사 기록을 포함한 정확 검색 함수를 준비했다. 관리자 웹 route와 화면을 구현한 별도 저장소 PR #43도 2026-08-31 병합됐다. 메인 PR #380과 관리자 웹 PR #43의 코드 반영 및 역할별 Preview 검증 뒤 이슈 #315를 완료 처리했다.
- Firestore `reservationCodes`는 추가하지 않았으며 PostgreSQL `appointment_requests`를 계속 예약 업무 원본으로 유지한다.
- production DB migration과 실제 배포는 수행하지 않았다.

## 156. 2026-08-30 매니저 자격 증빙 최소수집 전환

### 구현과 운영 경계

- 신규 매니저 심사 원본을 `license` 또는 `nursingLicense` 중 정확히 1종으로 줄이고, 신분증·범죄경력 원본과 legacy `healthCertificate`의 신규 클라이언트 쓰기를 차단했다.
- Android는 UID·문서 키·nested metadata·path map·legacy alias가 일치하는 canonical 이미지 1종만 제출 가능으로 표시한다. 오염된 참조, legacy key와 PDF는 `교체 필요`로 안내한다.
- `healthCertificate` 객체를 `nursingLicense`로 복사·무결성 검증·Firestore transaction·generation 조건부 원본 삭제 순서로 이관하는 기본 dry-run 도구를 추가했다.
- 순수 서버 이관에는 클라이언트가 변경할 수 없는 표식을 남겨 사용자 재제출 이력과 구분한다.
- Functions는 자격 종류 교체 뒤 이전 canonical·legacy 객체를 정리한다. Firestore transaction에서 서버 전용 삭제 claim과 최신 역할·참조·legal hold를 함께 고정하고, claim 중 클라이언트 참조 변경과 Storage 재업로드를 막은 뒤 객체 generation 조건부 삭제와 참조 정리를 완료한다. 불완전하거나 충돌한 상태에서는 fail-closed한다.
- 실제 데이터 이관 apply, Rules·Functions 배포와 production 변경은 수행하지 않았다.

### 검증

- Android 전체 단위 테스트와 debug APK 빌드: 성공
- Core API 전체 `check`와 V19 뒤 V20 migration 순서 계약: 성공
- Functions 테스트: 73개 중 70개 성공, 기존 Emulator 전용 3개 제외, 실패 0개
- Firebase 운영 도구 테스트: 62/62 성공
- Firestore·Storage Rules 에뮬레이터: 7/7 성공
- CI 프리플라이트의 로컬 빌드·테스트: 성공. 전용 작업공간에 Firebase 프로젝트 입력이 없어 운영 워크플로는 건너뜀
- 전체 diff 형식과 migration 검증 셸 문법 검사: 성공

### 남은 범위

- 개발 Firebase에서 이관 dry-run 대상·차단 건과 백업 증적 대조 후 승인된 apply 수행
- 관리자 웹 PR `bodeul110/bodeul-admin-web#44`는 같은 canonical 계약과 자동 검사를 반영한 열린 상태다. 현재 HEAD에 대한 사람 승인과 Preview 역할별 심사·거부·파기 흐름 검증은 남아 있다.
- 새 Android 앱과 Rules·Functions를 같은 출시 창에 배포한 뒤 구버전 legacy 쓰기가 남지 않았는지 확인

상세 근거는 [이슈 349 매니저 자격 증빙 최소수집 전환 기록](../reports/issue-349-manager-document-minimization-2026-08-30.md)에 정리했다.

## 157. 2026-09-01 Figma 인증·매니저 화면 반영 상태

### 반영 상태

- Android 인증 화면을 최신 Figma 기준으로 정리한 메인 PR #385가 2026-08-31 병합됐다.
- 매니저 홈·자격인증 화면을 최신 Figma 기준으로 정리한 메인 PR #386이 2026-09-01 병합됐다.
- 2026-09-01 확인 시점에 메인 저장소의 열린 PR은 0개다.
- 관리자 웹 저장소에서는 자격 증빙 계약을 반영하는 PR #44만 열려 있다. 자동 검사는 통과했지만 현재 HEAD에 대한 사람 승인과 역할별 Preview 검증은 완료되지 않았다.

### 검증 경계

- 이 항목은 병합 상태와 저장소 자동 검사 결과만 기록한다.
- 실제 기기 화면 검증과 관리자·비관리자·거부·파기 Preview 검증은 완료 증거가 생기기 전까지 운영 완료로 간주하지 않는다.

## 158. 2026-09-03 MVP SOS·사고 경로 비활성화

### 구현 경계

- 완료 예약 후속 화면의 SOS 카드, 매니저·119 전화, 긴급 안내와 신규 상태 기록을 제거했다.
- 홈·예약 상세·후기 요약·매니저 이력의 SOS 필터·배지·지표·상세와 관리자 운영 화면의 긴급 이슈 보고·해결 쓰기 경로를 제거했다.
- Android Core API·Firestore·Mock 저장소의 신규 SOS writer를 제거했다.
- Core API는 값이 있는 신규 `supportEscalationStatus` 요청을 `409 support_escalation_not_supported`로 거부하고, JDBC 후기·정산 갱신은 기존 legacy SOS 열을 덮어쓰지 않는다.
- 기존 PostgreSQL·Firestore 필드, 응답 DTO와 파서는 삭제하지 않아 과거 데이터와 구버전 응답을 계속 읽을 수 있다.
- 일반 `REQUESTED`·`MATCHED` 예약 취소와 정상 동행 `CARE_ENDED`·`COMPLETED` 흐름은 변경하지 않았다. 진행 중 사고 전용 중단 상태나 자동 연락은 추가하지 않았다.

### 검증

- Android legacy SOS 단독 레코드가 후속 완료 항목으로 노출되지 않고 후기·정산은 계속 노출되는 정책 테스트를 추가했다.
- Core API 신규 SOS 쓰기 거부와 후기 갱신 시 legacy 열 보존 테스트를 추가했다.

상세 판단은 [MVP SOS·사고 대응 비활성 경계](../architecture/mvp-accident-response-boundary.md)를 따른다.
