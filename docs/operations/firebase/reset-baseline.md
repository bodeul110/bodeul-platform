# Firebase 개발용 기준선 초기화

기준일: 2026-04-24

이 문서는 Firebase Firestore 데이터를 한 번 비우고, 보들 프로젝트가 다시 작업을 이어갈 수 있는 최소 기준선으로 되돌리는 절차를 정리한다.

## 목적

- 누적형 컬렉션에 남은 테스트 데이터가 화면과 집계에 섞이는 문제를 정리한다.
- `appointmentFollowUps`처럼 `merge`로 관리되는 문서에 남은 오래된 필드를 정리한다.
- 사용자 인증은 유지하고, Firestore만 앱이 기대하는 최소 상태로 다시 맞춘다.

## 초기화 원칙

- `Firebase Authentication`은 삭제하지 않는다.
- `Firestore`는 운영/테스트 컬렉션을 비우고, `users`, `hospitalGuides`만 기준선으로 다시 만든다.
- 기준선 `users` 문서는 기존 Auth UID에 맞춰 다시 생성한다.
- 기준선 `hospitalGuides`는 예약 화면과 관리자 화면이 바로 동작할 수 있도록 최소 1건을 재주입한다.

## 컬렉션 분류

### 비우고 다시 시작할 컬렉션

- `appointmentRequests`
- `companionSessions`
- `sessionReports`
- `appointmentFollowUps`
- `supportInquiries`
- `clientSupportRequests`
- `adminSettlementRecords`
- `adminEmergencyIssues`
- `adminActionNotifications`
- `adminAuditLogs`
- `adminActionDeliveries`
- `adminActionDeliveryJobs`
- `appointmentReminderJobs`

### 비운 뒤 기준선으로 다시 만들 컬렉션

- `users`
- `hospitalGuides`

### 건드리지 않는 대상

- `Firebase Authentication`
- `Cloud Functions`
- `Firestore Rules / Indexes`
- `google-services.json`

## 기준선 데이터

### 기준선 계정

아래 계정은 `Firebase Authentication`에 이미 존재해야 한다. 스크립트는 이 계정들의 UID를 기준으로 `users/{uid}` 문서를 다시 만든다.

- `admin@bodeul.app` / `bodeul1234`
- `patient@bodeul.app` / `bodeul1234`
- `guardian@bodeul.app` / `bodeul1234`
- `manager@bodeul.app` / `bodeul1234`

### 기준선 users 문서

- `ADMIN`: 관리자 기본 계정
- `PATIENT`: 환자 기본 계정
- `GUARDIAN`: 보호자 기본 계정
- `MANAGER`: 매니저 기본 계정
  - 서류 요약
  - 가능 시간 요약
  - 서류 검토 상태 / 검토 메모 / 검토 이력

### 기준선 hospitalGuides 문서

- `서울대학교병원 / 내과`
  - 환자 접수
  - 접수 등록
  - 진료 접수
  - 진료 완료
  - 수납 처리
  - 약국 방문
  - 환자 귀가(서비스 종료)

## 실행 스크립트

위 절차는 [reset-firestore-baseline.js](../../../tools/firebase/reset-firestore-baseline.js)로 자동화했다.

이 스크립트는 배포 대상인 `functions/`가 아니라 운영 도구 디렉터리 `tools/firebase/`에 둔다. 기준선 초기화, 시드, 마이그레이션 같은 작업은 앱 런타임 코드와 분리해 두는 편이 이후 관리가 쉽다.

실행 위치:

```powershell
cd D:\BoDeul\tools\firebase
```

dry-run:

```powershell
npm run reset:baseline:dry-run
```

실행:

```powershell
npm run reset:baseline:apply
```

## 스크립트 동작

1. `firebase login` 토큰과 프로젝트 ID를 로컬 설정에서 읽는다.
2. 기준선 이메일 4개가 `Firebase Authentication`에 있는지 확인한다.
3. `--apply`일 때 누락된 기준선 Auth 계정은 자동으로 생성한다.
4. 지정된 Firestore 컬렉션을 비운다.
5. 기존 Auth UID에 맞춰 `users/{uid}` 문서를 다시 만든다.
6. 최소 병원 가이드 1건을 `hospitalGuides`에 다시 넣는다.

## 실행 전 체크

- Firebase 프로젝트 권한이 있는 계정으로 인증되어 있어야 한다.
- 로컬에서 `firebase login`이 되어 있어야 한다.
- `tools/firebase/package.json` 기준으로 Node 22 이상에서 실행하는 것을 권장한다.

## 실행 후 확인

- 환자 / 보호자 / 매니저 / 관리자 로그인
- 예약 화면 병원 선택 목록 노출
- 관리자 화면 가이드 목록 노출
- 관리자 화면 후속 알림 / 전달 기록이 빈 상태로 시작되는지 확인
- 예약 생성 후 `appointmentRequests`만 새로 쌓이고 이전 테스트 데이터가 섞이지 않는지 확인
